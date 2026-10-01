package com.tripplanner.poc.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.poc.agentic.JevRouter;
import com.tripplanner.poc.agentic.JevRouter.RouteDecision;
import com.tripplanner.poc.agentic.JevRouter.RoutingPolicy;
import com.tripplanner.poc.jev.JevAnswer;
import com.tripplanner.poc.jev.JevRequest;
import com.tripplanner.poc.jev.JevResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Runs the labelled request set in {@code routing-eval.json} against a live decision model and
 * compares three routing policies: Choice only, a Choice fast path (the Choice decides alone when
 * its confidence is high, the Nouls otherwise), and the policy {@link JevRouter} uses (Nouls first,
 * Choice when no Noul passes), over a range of thresholds. Each request is sent to the model once,
 * with the same questions {@link JevRouter} asks; the policies are then applied offline to those
 * answers.
 *
 * Opt-in, because it calls a live model:
 * <ul>
 *   <li>Jev (paid API): {@code TYPESAFE_API_KEY=... ./mvnw test -Dtest=RoutingEvalTest -Drouting.eval=true}</li>
 *   <li>Laya (sidecar in {@code laya-sidecar/}): add {@code -Drouting.eval.backend=laya}, and
 *       {@code -Drouting.eval.laya-url=...} if it is not on {@code http://localhost:8100}</li>
 * </ul>
 * The report is written to {@code target/routing-eval-<backend>.md}.
 */
@EnabledIfSystemProperty(named = "routing.eval", matches = "true")
class RoutingEvalTest {

    record Item(String group, String request, List<String> expected, List<List<String>> acceptable) {

        boolean accepts(List<String> routes) {
            Set<String> actual = new HashSet<>(routes);
            if (actual.equals(new HashSet<>(expected))) {
                return true;
            }
            return acceptable != null && acceptable.stream().anyMatch(a -> actual.equals(new HashSet<>(a)));
        }
    }

    record Observation(Item item, Map<String, JevAnswer> answers, long millis) {}

    private final ObjectMapper mapper = new ObjectMapper();
    // HTTP/1.1: over plain http the default HTTP/2 client attempts an h2c upgrade, and the uvicorn
    // sidecar then receives the request without its body.
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private final String backend = System.getProperty("routing.eval.backend", "jev");

    @Test
    void evaluateRoutingPolicies() throws Exception {
        String apiKey = System.getenv("TYPESAFE_API_KEY");
        if (backend.equals("jev")) {
            assertFalse(apiKey == null || apiKey.isBlank(), "TYPESAFE_API_KEY must be set");
        }

        List<Item> items;
        try (InputStream in = getClass().getResourceAsStream("/routing-eval.json")) {
            items = mapper.readValue(in, new TypeReference<>() {});
        }

        List<Observation> observations = new ArrayList<>();
        String servedModel = null;
        for (Item item : items) {
            long start = System.nanoTime();
            JevResponse response = call(apiKey, item.request());
            observations.add(new Observation(item, response.answers(), (System.nanoTime() - start) / 1_000_000));
            servedModel = response.model();
        }

        StringBuilder report = new StringBuilder();
        report.append("# Routing eval\n\n")
                .append("Model: `").append(servedModel).append("`, ").append(items.size()).append(" requests, ")
                .append("median ").append(backend).append(" latency ").append(medianMillis(observations)).append(" ms.\n\n");

        report.append("## Policies\n\n| Policy | single | indirect | multi | general | total |\n|---|---|---|---|---|---|\n");
        report.append(row("Choice only", observations, o -> List.of(JevRouter.normalize(choice(o).choice()))));
        for (double fast : new double[] {0.7, 0.8, 0.9}) {
            String label = String.format(Locale.ROOT, "Choice fast path (confidence ≥ %.1f), then Noul > 0.5", fast);
            report.append(row(label, observations, o -> fastPath(o, fast, 0.5)));
        }
        for (double threshold : new double[] {0.3, 0.4, 0.5, 0.6, 0.7}) {
            RoutingPolicy policy = new RoutingPolicy(threshold);
            String label = String.format(Locale.ROOT, "Nouls first (Noul > %.1f), then Choice", threshold);
            report.append(row(label, observations, o -> decide(o, policy).routes()));
        }

        report.append("\n## Per request (Nouls first, Noul > 0.5)\n\n")
                .append("| Request | Expected | Choice (confidence) | Noul res / wea / cost | Routed | OK |\n|---|---|---|---|---|---|\n");
        for (Observation o : observations) {
            Map<String, Double> needs = JevRouter.needs(o.answers());
            RouteDecision decision = decide(o, RoutingPolicy.DEFAULT);
            report.append(String.format(Locale.ROOT, "| %s | %s | %s (%.2f) | %.2f / %.2f / %.2f | %s | %s |%n",
                    o.item().request(), String.join("+", o.item().expected()),
                    choice(o).choice(), choice(o).confidenceOrZero(),
                    needs.getOrDefault("reservation", -1.0), needs.getOrDefault("weather", -1.0), needs.getOrDefault("cost", -1.0),
                    String.join("+", decision.routes()), o.item().accepts(decision.routes()) ? "yes" : "**no**"));
        }
        long fanOuts = observations.stream().filter(o -> decide(o, RoutingPolicy.DEFAULT).isFanOut()).count();
        report.append("\nFan-outs: ").append(fanOuts).append(" of ").append(observations.size()).append(" requests.\n");

        Path out = Path.of("target", "routing-eval-" + backend + ".md");
        Files.writeString(out, report);
        System.out.println(report);
        System.out.println("Report written to " + out.toAbsolutePath());
    }

    private JevResponse call(String apiKey, String state) throws Exception {
        JevRequest body = new JevRequest(state, backend.equals("jev") ? "jev-latest" : "laya", JevRouter.questions());
        HttpRequest.Builder request = backend.equals("jev")
                ? HttpRequest.newBuilder(URI.create("https://api.typesafe.ai/v1/systemone"))
                        .header("Authorization", "Bearer " + apiKey)
                : HttpRequest.newBuilder(URI.create(
                        System.getProperty("routing.eval.laya-url", "http://localhost:8100") + "/v1/decision"));
        HttpResponse<String> response = http.send(request
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(backend + " returned " + response.statusCode() + ": " + response.body());
        }
        return mapper.readValue(response.body(), JevResponse.class);
    }

    private static JevAnswer choice(Observation o) {
        return o.answers().get("route");
    }

    private static RouteDecision decide(Observation o, RoutingPolicy policy) {
        return JevRouter.decide(choice(o), JevRouter.needs(o.answers()), policy);
    }

    /** The earlier policy, kept here for comparison: a confident Choice decides alone. */
    private static List<String> fastPath(Observation o, double fastPathConfidence, double threshold) {
        JevAnswer choice = choice(o);
        if (choice.confidenceOrZero() >= fastPathConfidence) {
            return List.of(JevRouter.normalize(choice.choice()));
        }
        return decide(o, new RoutingPolicy(threshold)).routes();
    }

    private static String row(String label, List<Observation> observations,
                              java.util.function.Function<Observation, List<String>> router) {
        StringBuilder row = new StringBuilder("| ").append(label).append(" |");
        int total = 0;
        for (String group : List.of("single", "indirect", "multi", "general")) {
            List<Observation> inGroup = observations.stream().filter(o -> o.item().group().equals(group)).toList();
            long ok = inGroup.stream().filter(o -> o.item().accepts(router.apply(o))).count();
            total += (int) ok;
            row.append(' ').append(ok).append('/').append(inGroup.size()).append(" |");
        }
        return row.append(' ').append(total).append('/').append(observations.size()).append(" |\n").toString();
    }

    private static long medianMillis(List<Observation> observations) {
        List<Long> sorted = observations.stream().map(Observation::millis).sorted().toList();
        return sorted.get(sorted.size() / 2);
    }
}
