package com.tripplanner.poc.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.poc.jev.JevAnswer;
import com.tripplanner.poc.jev.JevQuestion;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Evaluates the labelled request set against the four yes/no questions used by
 * {@code DecisionRouterPlanner}. Each route activates when its probability meets the tested
 * threshold. If no route activates, the application falls back to general.
 *
 * Opt-in, because it calls a live model:
 * <ul>
 *   <li>Jev (paid API): {@code TYPESAFE_API_KEY=... ./mvnw test -Dtest=RoutingEvalTest -Drouting.eval=true}</li>
 *   <li>Kev (local server): add {@code -Drouting.eval.backend=kev}, and
 *       {@code -Drouting.eval.kev-url=...} if it is not on {@code http://localhost:8009}</li>
 *   <li>Laya (sidecar in {@code laya-sidecar/}): add {@code -Drouting.eval.backend=laya}, and
 *       {@code -Drouting.eval.laya-url=...} if it is not on {@code http://localhost:8100}</li>
 * </ul>
 * The report is written to {@code target/routing-eval-<backend>.md}.
 */
@EnabledIfSystemProperty(named = "routing.eval", matches = "true")
class RoutingEvalTest {

    private static final List<String> ROUTES = List.of("reservation", "weather", "cost", "general");
    private static final double[] THRESHOLDS = {0.3, 0.4, 0.5, 0.6, 0.7};
    private static final double REPORT_THRESHOLD = 0.5;

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
    // HTTP/1.1 avoids an h2c upgrade that can cause the uvicorn sidecar to receive no request body.
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String backend = System.getProperty("routing.eval.backend", "jev");

    @Test
    void evaluateThresholdRouting() throws Exception {
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
                .append("median ").append(backend).append(" latency ").append(medianMillis(observations)).append(" ms.\n\n")
                .append("Each route activates when its yes/no probability is greater than or equal to the threshold. ")
                .append("When none activates, the application routes to `general` as its fallback.\n\n");

        report.append("## Threshold accuracy\n\n")
                .append("| Threshold | single | indirect | multi | general | total | fallbacks |\n")
                .append("|---:|---:|---:|---:|---:|---:|---:|\n");
        for (double threshold : THRESHOLDS) {
            report.append(row(threshold, observations,
                    o -> routes(o, threshold)));
        }

        report.append("\n## Per request (threshold ≥ ")
                .append(String.format(Locale.ROOT, "%.1f", REPORT_THRESHOLD))
                .append(")\n\n| Request | Expected | Reservation | Weather | Cost | General | Routed | OK |\n")
                .append("|---|---|---:|---:|---:|---:|---|---|\n");
        for (Observation observation : observations) {
            List<String> routed = routes(observation, REPORT_THRESHOLD);
            boolean fallback = ROUTES.stream().allMatch(route -> !activates(observation, route, REPORT_THRESHOLD));
            report.append(String.format(Locale.ROOT,
                    "| %s | %s | %s | %s | %s | %s | %s%s | %s |%n",
                    observation.item().request(), String.join("+", observation.item().expected()),
                    probabilityText(observation, "reservation"), probabilityText(observation, "weather"),
                    probabilityText(observation, "cost"), probabilityText(observation, "general"),
                    String.join("+", routed), fallback ? " (fallback)" : "",
                    observation.item().accepts(routed) ? "yes" : "**no**"));
        }
        long fallbacks = observations.stream()
                .filter(o -> ROUTES.stream().noneMatch(route -> activates(o, route, REPORT_THRESHOLD)))
                .count();
        report.append("\nGeneral fallbacks at threshold ")
                .append(String.format(Locale.ROOT, "%.1f", REPORT_THRESHOLD))
                .append(": ").append(fallbacks).append(" of ").append(observations.size()).append(" requests.\n");

        Path out = Path.of("target", "routing-eval-" + backend + ".md");
        Files.writeString(out, report);
        System.out.println(report);
        System.out.println("Report written to " + out.toAbsolutePath());
    }

    private JevResponse call(String apiKey, String state) throws Exception {
        String model = switch (backend) {
            case "jev" -> "jev-latest";
            case "kev" -> "kev-latest";
            case "laya" -> "laya";
            default -> throw new IllegalArgumentException("Unsupported eval backend: " + backend);
        };
        String baseUrl = switch (backend) {
            case "jev" -> "https://api.typesafe.ai";
            case "kev" -> System.getProperty("routing.eval.kev-url", "http://localhost:8009");
            case "laya" -> System.getProperty("routing.eval.laya-url", "http://localhost:8100");
            default -> throw new IllegalArgumentException("Unsupported eval backend: " + backend);
        };
        JevRequest body = new JevRequest(state, model, questions());
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/systemone"));
        if (backend.equals("jev")) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response = http.send(request
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(backend + " returned " + response.statusCode() + ": " + response.body());
        }
        return mapper.readValue(response.body(), JevResponse.class);
    }

    private static Map<String, JevQuestion> questions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("reservation", "Handles booking, modifying, cancelling, or questions about a reservation");
        descriptions.put("weather", "Answers weather and forecast questions for the trip or destination");
        descriptions.put("cost", "Answers pricing, total cost, budget, or fee questions");
        descriptions.put("general", "Handles only greetings and questions unrelated to reservations, weather, or pricing");

        Map<String, JevQuestion> questions = new LinkedHashMap<>();
        descriptions.forEach((route, description) -> questions.put(route, JevQuestion.noul(
                "Should the agent '" + route + "' (" + description + ") handle this request?")));
        return questions;
    }

    private static boolean activates(Observation observation, String route, double threshold) {
        JevAnswer answer = observation.answers().get(route);
        return answer != null && answer.noul() != null && answer.noul() >= threshold;
    }

    private static List<String> routes(Observation observation, double threshold) {
        List<String> activated = ROUTES.stream()
                .filter(route -> activates(observation, route, threshold))
                .toList();
        return activated.isEmpty() ? List.of("general") : activated;
    }

    private static String probabilityText(Observation observation, String route) {
        JevAnswer answer = observation.answers().get(route);
        return answer == null || answer.noul() == null
                ? "—" : String.format(Locale.ROOT, "%.2f", answer.noul());
    }

    private static String row(double threshold, List<Observation> observations,
                              Function<Observation, List<String>> router) {
        StringBuilder row = new StringBuilder("| ")
                .append(String.format(Locale.ROOT, "%.1f", threshold)).append(" |");
        int total = 0;
        for (String group : List.of("single", "indirect", "multi", "general")) {
            List<Observation> inGroup = observations.stream().filter(o -> o.item().group().equals(group)).toList();
            long ok = inGroup.stream().filter(o -> o.item().accepts(router.apply(o))).count();
            total += (int) ok;
            row.append(' ').append(ok).append('/').append(inGroup.size()).append(" |");
        }
        long fallbacks = observations.stream()
                .filter(o -> ROUTES.stream().noneMatch(route -> activates(o, route, threshold)))
                .count();
        return row.append(' ').append(total).append('/').append(observations.size()).append(" | ")
                .append(fallbacks).append(" |").append('\n').toString();
    }

    private static long medianMillis(List<Observation> observations) {
        List<Long> sorted = observations.stream().map(Observation::millis).sorted().toList();
        return sorted.get(sorted.size() / 2);
    }
}
