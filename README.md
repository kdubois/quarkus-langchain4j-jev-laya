# Decision-model routing with Quarkus LangChain4j

This proof of concept uses a small decision model to route car-rental questions, then uses chat
models for the specialist answers and final merge. It can run the same typed decisions against
hosted Jev, local Kev, the included Laya sidecar, or a deterministic stub.

The project uses Quarkus 4.0.0.Beta1, Quarkus LangChain4j 2.0.0.Beta1, and the decision-model and
agentic APIs in LangChain4j 1.21.

## What the application does

`SpecialistRouter` uses LangChain4j's `DecisionRouterPlanner` to ask whether the request needs each
of these specialists:

- `reservation` handles bookings, changes, cancellations, and pickup questions.
- `weather` handles forecasts and conditions at the destination.
- `cost` handles prices, fees, budgets, and comparisons.
- `general` handles greetings and unrelated questions.

Each specialist gets an independent yes/no probability. `DecisionRouterPlanner` calls every agent
at or above `routing.fan-out-threshold` in parallel and returns their answers in a map. If
reservation and cost both pass for “Book an SUV and tell me the price with insurance,” both agents
run. `MergeAgent` then turns their answers into one reply.

When no agent reaches the threshold, `GeneralFallback` invokes the general agent and puts its answer
in the same map shape. This fallback is a separate workflow step, so it does not change the decision
model's answers or duplicate the planner's routing policy.

The specialist and merged replies pass through decision-model output guardrails. A clearly relevant
reply passes, a clearly irrelevant reply is retried, and a probability close to 0.5 passes with an
audit flag.

## Decision backends

Set `decision.backend` to one of these values:

- `jev` uses TypeSafe's hosted Jev API. It reads `TYPESAFE_API_KEY`, with `JEV_API_KEY` as a fallback.
- `kev` uses a local or remote [Kev](https://github.com/jaredpalmer/kev) server. The default base URL
  is `http://localhost:8009`.
- `laya` uses the included [Laya sidecar](laya-sidecar/README.md). The default base URL is
  `http://localhost:8100`.
- `stub` uses a deterministic local implementation for development and tests.

Jev, Kev, and the Laya sidecar expose the same `/v1/systemone` request shape. The
`quarkus-langchain4j-typesafe` extension creates a named `DecisionModel` for each backend.
`ActiveDecisionClient` selects the configured model and falls back to the stub when a call fails.

## Run with Jev

The specialist agents still need an OpenAI-compatible chat model key.

```bash
export OPENAI_API_KEY=sk-...
export TYPESAFE_API_KEY=ts-...
./mvnw quarkus:dev
```

## Run with Kev

Kev requires Python 3.12 or 3.13 and `uv`. The Kev project recommends its 4B model as the starting
point, and documents it for Macs with at least 32 GB of memory. MLX is selected automatically on
Apple Silicon. Start it from a Kev checkout:

```bash
git clone https://github.com/jaredpalmer/kev.git
cd kev
uv sync --extra serve
uv run --extra serve python -m kev.serve --run jaredpalmer/kev-4b@v1.0 --port 8009
```

Use `jaredpalmer/kev-0.8b@v1.0` for a smaller smoke-test model. Its base model downloads about 1.8
GB, but Kev describes this variant as the size-first option with lower accuracy. Later starts reuse
the model files from the Hugging Face cache. In this application, 0.8B routed requests correctly but
gave false rejections on the merged-reply guardrail, so use 4B or hosted Jev when testing guardrails.

Then start this application in another terminal:

```bash
export OPENAI_API_KEY=sk-...
./mvnw "-Ddecision.backend=kev" quarkus:dev
```

Set `KEV_BASE_URL` when the server is elsewhere and `KEV_MODEL` when it reports a model name other
than `kev-latest`.

## Run with Laya

```bash
cd laya-sidecar
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8100
```

Then start the application in another terminal:

```bash
export OPENAI_API_KEY=sk-...
./mvnw "-Ddecision.backend=laya" quarkus:dev
```

Set `LAYA_BASE_URL` to use another address. See the [sidecar guide](laya-sidecar/README.md) for model
and device options.

## Call the API

Swagger UI is available at `http://localhost:8083/q/swagger-ui/` in dev mode. You can also call the
endpoint directly:

```bash
curl -s -X POST http://localhost:8083/trip \
  -H 'Content-Type: application/json' \
  -d '{"request":"Book an SUV for Saturday and tell me the price with full insurance."}'
```

The response includes the selected routes, the routing mode, and the backend that made the decision:

```json
{
  "request": "Book an SUV for Saturday and tell me the price with full insurance.",
  "reply": "...",
  "route": "reservation",
  "routes": ["reservation", "cost"],
  "routingMode": "fan-out",
  "backend": "kev",
  "model": "kev-latest",
  "live": true
}
```

`GET /trip/backend` reports the configured backend and model. A request that falls back to the stub
reports `backend: "stub"` and `live: false` in its response.

## Configuration

- `routing.fan-out-threshold`, default `0.5`, controls specialist activation.
- `KEV_BASE_URL`, default `http://localhost:8009`, points to Kev.
- `KEV_MODEL`, default `kev-latest`, selects Kev's reported model.
- `LAYA_BASE_URL`, default `http://localhost:8100`, points to the Laya sidecar.
- `LAYA_MODEL`, default `laya`, selects Laya's reported model.
- `quarkus.langchain4j.openai.chat-model.model-name`, default `gpt-4o`, selects the specialist chat
  model.

See [application.properties](src/main/resources/application.properties) for timeouts and the named
decision-model configuration.

## Test it

The normal suite uses the stub and does not need API keys or local models:

```bash
./mvnw test
```

The routing evaluation is opt-in. It sends the labelled cases from
`src/test/resources/routing-eval.json` to a live backend and writes a report under `target/`:

```bash
./mvnw test -Dtest=RoutingEvalTest -Drouting.eval=true
./mvnw test -Dtest=RoutingEvalTest -Drouting.eval=true -Drouting.eval.backend=kev
./mvnw test -Dtest=RoutingEvalTest -Drouting.eval=true -Drouting.eval.backend=laya
```

## Main files

- [`SpecialistRouter`](src/main/java/com/tripplanner/poc/agentic/SpecialistRouter.java) configures the
  core `DecisionRouterPlanner` with the active decision model and threshold.
- [`GeneralFallback`](src/main/java/com/tripplanner/poc/agentic/GeneralFallback.java) invokes the
  general agent when no route reaches the threshold.
- [`TripAdvisorSystem`](src/main/java/com/tripplanner/poc/agentic/TripAdvisorSystem.java) sequences the
  router, fallback, and merge agent.
- [`ActiveDecisionClient`](src/main/java/com/tripplanner/poc/jev/ActiveDecisionClient.java) selects
  Jev, Kev, Laya, or the stub.
- [`RoutingAuditListener`](src/main/java/com/tripplanner/poc/agentic/RoutingAuditListener.java)
  records the planner's decision responses without changing them.
- [`RouteAudit`](src/main/java/com/tripplanner/poc/guardrails/RouteAudit.java) keeps recent routing
  decisions in memory.
