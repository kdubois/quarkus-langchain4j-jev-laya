# Jev × Laya × Quarkus LangChain4j Agentic — Proof of Concept

A standalone proof of concept showing how to use a **System One decision model** together with
**Quarkus LangChain4j Agentic**.

A decision model is not an LLM. You send it a `state` plus a set of typed *questions*, and it returns
typed *answers* — each with a probability distribution and a confidence value — that your code can
branch on directly. No text generation, no parsing. This PoC wires a decision model into three
different points of an agentic system, while the LLM (via LangChain4j) still does the natural-language
work it is good at.

The decision layer is backend-agnostic. The same request/answer shape is served by any of three
backends, selected with one property (`decision.backend`):

| Backend | What it is | Needs |
|---------|-----------|-------|
| `jev` (default) | the hosted **TypeSafe Jev** endpoint over HTTP | a `TYPESAFE_API_KEY` |
| `laya` | a self-hosted **Laya** sidecar over HTTP (open source, Apache 2.0) | the sidecar running |
| `stub` | a deterministic offline stand-in | nothing |

Jev and Laya speak the same three primitives (Choice / Score / Noul) with an almost identical
response schema, so the agentic system is written once and can be pointed at either model. When the
chosen backend is unavailable (no key, sidecar down), the app falls back to the stub and keeps
answering, rather than failing the request.

The system is a car-rental "trip advisor": a customer request is routed to the right specialist
(or to several, when it mixes intents), the specialists answer it, and the answer is validated
before it is returned.

## What it demonstrates

1. **A decision model as an agentic Planner (router).** The top-level `@PlannerAgent` uses a custom
   [`JevRoutingPlanner`](src/main/java/com/tripplanner/poc/agentic/JevRoutingPlanner.java)
   instead of the framework's LLM-driven supervisor. On each request it makes one decision call that
   asks a **Choice** ("which specialist?") plus one **Noul** per specialist ("does this request need
   weather / cost / reservation input?"). See [Routing](#routing) for the policy and the eval behind it.
2. **Fan-out for multi-intent requests.** When several specialists are needed, the planner calls them
   one after the other, a non-AI
   [`SpecialistRepliesCollector`](src/main/java/com/tripplanner/poc/agentic/SpecialistRepliesCollector.java)
   gathers their replies, and a [`MergeAgent`](src/main/java/com/tripplanner/poc/agentic/MergeAgent.java)
   combines them into one answer.
3. **A decision model as an output guardrail.** Replies are checked by an `OutputGuardrail` driven by
   a **Noul** (yes/no) probability: pass when it is clearly above 0.5, retry when clearly below, and
   pass-and-flag when it is within the margin of 0.5 (or missing). There are two:
   [`JevReplyGuardrail`](src/main/java/com/tripplanner/poc/guardrails/JevReplyGuardrail.java) on each
   specialist checks that the reply answers at least one thing the customer asked or said (a fan-out
   specialist only answers its part, and a greeting has no request), and
   [`JevCompleteReplyGuardrail`](src/main/java/com/tripplanner/poc/guardrails/JevCompleteReplyGuardrail.java)
   on the merged reply checks that it covers the whole request.
4. **Backend choice + graceful fallback.** The decision backend is a property
   (`decision.backend` = `jev` | `laya` | `stub`). Every backend degrades to a deterministic
   [`StubDecisionClient`](src/main/java/com/tripplanner/poc/jev/StubDecisionClient.java) when the model
   is unavailable, so the whole agentic flow — routing + guardrail — always runs and stays testable
   without a key, a network, or a downloaded model.
5. **Swapping the model without touching the agents.** Because the router, guardrail, and REST layer
   all depend on the [`DecisionClient`](src/main/java/com/tripplanner/poc/jev/DecisionClient.java)
   interface (resolved by [`ActiveDecisionClient`](src/main/java/com/tripplanner/poc/jev/ActiveDecisionClient.java)),
   pointing the PoC at Laya instead of Jev is a configuration change, not a code change.

The specialist agents themselves (`WeatherAgent`, `ReservationAgent`, `CostAgent`, `GeneralAgent`)
are ordinary LangChain4j agentic `@Agent`s backed by the configured LLM, so the answers are real.

## Layout

| Path | Role |
|------|------|
| `jev/DecisionClient` | The backend-agnostic decision interface the agents depend on |
| `jev/ActiveDecisionClient` | CDI bean that picks the backend from `decision.backend` |
| `jev/JevApiClient` | Hosted TypeSafe Jev backend (HTTP) |
| `jev/LayaDecisionClient` | Self-hosted Laya sidecar backend (HTTP) |
| `jev/StubDecisionClient` | Deterministic offline fallback |
| `jev/JevRequest`, `JevQuestion`, `JevResponse`, `JevAnswer` | Request/response records (shared by all backends) |
| `agentic/JevRoutingPlanner`, `JevRouter` | Decision-model-driven planner / router and routing policy |
| `agentic/TripAdvisorSystem` | Top-level `@PlannerAgent` wiring the sub-agents |
| `agentic/*Agent` | The four specialist LLM agents, plus `MergeAgent` for fan-outs |
| `agentic/SpecialistRepliesCollector` | Non-AI agent that collects specialist replies during a fan-out |
| `guardrails/JevReplyGuardrail`, `JevCompleteReplyGuardrail` | Decision output guardrails (relevance / completeness) |
| `guardrails/RouteAudit` | Decision history |
| `src/test/resources/routing-eval.json`, `eval/RoutingEvalTest` | Labelled routing requests and the live eval |
| `docs/eval/` | Eval reports from live Jev runs |
| `resource/TripResource` | REST entry point |
| `laya-sidecar/` | The Python FastAPI service that hosts Laya |

## Requirements

- Java 25+ (`maven.compiler.release` is 25), Maven 3.8+
- An `OPENAI_API_KEY` (or any OpenAI-compatible key) for the specialist agents' answers
- For the **`jev`** backend: a `TYPESAFE_API_KEY` (`JEV_API_KEY` also works) (see https://docs.typesafe.ai/). Without it, requests fall
  back to the stub.
- For the **`laya`** backend: Python 3.10+ and the sidecar running (see [laya-sidecar/](laya-sidecar/)).
  First run downloads the Laya checkpoint from Hugging Face.

## Run it

```bash
export OPENAI_API_KEY=sk-...          # required for the LLM answers

# Backend 1: hosted Jev (set the key to use the live endpoint, otherwise it stubs)
export TYPESAFE_API_KEY=ts-...
./mvnw quarkus:dev

# Backend 2: Laya (in a second terminal, start the sidecar, then point the app at it)
#   ./mvnw "-Ddecision.backend=laya" quarkus:dev

# Backend 3: stub only (no model, no key)
#   ./mvnw "-Ddecision.backend=stub" quarkus:dev
```

Then:

```bash
# Routing + answer (the active backend routes, the LLM answers)
curl -s -X POST http://localhost:8083/trip \
  -H 'Content-Type: application/json' \
  -d '{"request":"Will it rain in Lisbon next Tuesday?"}'

curl -s -X POST http://localhost:8083/trip -H 'Content-Type: application/json' \
  -d '{"request":"How much does it cost to rent an SUV for 5 days?"}'

curl -s -X POST http://localhost:8083/trip -H 'Content-Type: application/json' \
  -d '{"request":"Please reserve a car for next week"}'

# Which backend + model is active
curl -s http://localhost:8083/trip/backend
```

A response looks like:

```json
{
  "request": "Book me a car for Saturday and tell me what it'll cost with full insurance.",
  "reply": "...",
  "route": "cost",
  "routes": ["cost", "reservation"],
  "routingMode": "fan-out",
  "rawChoice": "reservation",
  "confidence": 0.96,
  "backend": "jev",
  "model": "jev-latest",
  "live": true
}
```

`routes` are the specialists that answered, in order, and `route` is the first of them.
`routingMode` says which part of the policy decided: `needs` (one specialist's Noul passed),
`fan-out` (several did), `choice` (none did, so the Choice decided), or `fallback` (no usable answer).
`rawChoice` and `confidence` are the Choice answer, reported even when the Nouls decided. `backend`/`model`/`live` report the backend that **actually answered** the request, so a
fallback to the stub shows up as `backend: "stub"`, `live: false`. When a configured model is
unreachable on a request, the app still answers via the stub and logs the fallback, e.g. `Laya
sidecar call failed ...; falling back to stub`. The `GET /trip/backend` endpoint reports the
**configured** backend instead, since it is an identity endpoint.

## Test it

The test suite runs **without** a Jev key and without any LLM. It verifies the routing policy
(using answer values captured from live Jev), the planner's sub-agent selection, the stub client,
response deserialization, and all three guardrail branches (pass / retry / uncertain):

```bash
./mvnw test
```

The routing eval is opt-in, because it calls a live model. It sends each request in
`src/test/resources/routing-eval.json` to the model once and scores several policies on the answers,
writing the report to `target/routing-eval-<backend>.md`:

```bash
./mvnw test -Dtest=RoutingEvalTest -Drouting.eval=true                              # Jev, needs TYPESAFE_API_KEY
./mvnw test -Dtest=RoutingEvalTest -Drouting.eval=true -Drouting.eval.backend=laya  # Laya sidecar on :8100
```

## Routing

Each request is one decision call with four questions: the routing Choice and a Noul per specialist.
[`JevRouter.decide`](src/main/java/com/tripplanner/poc/agentic/JevRouter.java) then:

1. calls every specialist whose Noul is above `routing.fan-out-threshold` (0.5), most likely first;
2. when none is, uses the Choice (this is where greetings and general questions end up).

The Choice is not used as a fast path. On the 28 labelled requests in the eval (Jev `jev-1.13.0`,
median 278 ms per call; Laya `laya-typed-decisions` on the sidecar, median 76 ms on a laptop):

| Policy | Jev multi-intent (8) | Jev total | Laya multi-intent (8) | Laya total |
|---|---|---|---|---|
| Choice only | 0 | 20/28 | 0 | 18/28 |
| Choice decides alone when confidence ≥ 0.8, else Nouls | 5 | 25/28 | 1 | 19/28 |
| Nouls first, then Choice (current) | 8 | 26/28 | 1 | 19/28 |

The Choice was confident (0.90 to 0.99) about a single specialist for requests that clearly needed
two, such as "Book me a car for Saturday and tell me what it'll cost", and since the Nouls come back
in the same call, skipping them saves nothing. The two misses of the current policy are single-intent
cost questions where the reservation Noul also passes, so the reply merges in a reservation answer
that was not needed.

The per-specialist Nouls only help when the model separates "needed" from "not needed". With Jev,
the second intent of a two-part request scored 0.54 to 0.92, and unrelated weather and cost Nouls
stayed at 0.30 or below. The exception is the reservation Noul, which reached 0.84 on cost questions
about a booking. Laya's Nouls for these questions are compressed: the second intent scored 0.19 to
0.47 and unrelated specialists up to 0.38, so no threshold separates them and Laya mostly falls back
to the Choice. Full reports:
[`docs/eval/`](docs/eval/).

Specialists in a fan-out run sequentially, so a fan-out takes about one extra LLM call per
specialist plus the merge (6 to 12 s against 2 to 4 s for a single route). The agentic module can run
agents in parallel, but in this version a custom planner cannot safely schedule a follow-up step
(the merge) after parallel agents.

## The Laya sidecar

Laya ([github.com/NandhaKishorM/laya](https://github.com/NandhaKishorM/laya), Apache 2.0) is a
multilingual, non-autoregressive System One decision model. It is a Python library with no server of
its own, so the PoC runs it behind a ~60-line FastAPI service in `laya-sidecar/`. That service POSTs
straight into Laya's `predict()` and returns Laya's output verbatim, because Laya's answer schema
matches the Jev one the Java side already deserializes.

```bash
cd laya-sidecar
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt            # pulls laya + torch + transformers
uvicorn main:app --host 0.0.0.0 --port 8100
```

First load downloads the checkpoint (~1 GB) from Hugging Face. To change the checkpoint or force a
device, set `LAYA_MODEL` / `LAYA_DEVICE` before starting. Then point the Quarkus app at it with
`decision.backend=laya`.

## Configuration

| Property | Default | Meaning |
|----------|---------|---------|
| `decision.backend` | `jev` | `jev` \| `laya` \| `stub` — which decision model to use. |
| `jev.api-key` | `${TYPESAFE_API_KEY}`, then `${JEV_API_KEY}` | Bearer key for the Jev API. Empty → deterministic stub. |
| `jev.endpoint` | `https://api.typesafe.ai/v1/systemone` | Jev System One endpoint. |
| `jev.model` | `jev-latest` | Jev model alias. |
| `laya.endpoint` | `http://localhost:8100/v1/decision` | Laya sidecar endpoint. |
| `laya.model` | `laya` | Label reported for the Laya backend. |
| `routing.fan-out-threshold` | `0.5` | Noul probability above which a specialist is called. |
| `quarkus.rest-client.jev.read-timeout` | `5000` | Jev read timeout in ms; a timed-out call falls back to the stub. |
| `quarkus.langchain4j.openai.api-key` | — | LLM key for the specialist agents. |
| `quarkus.langchain4j.openai.chat-model.model-name` | `gpt-4o` | LLM used by the specialists. |

## How a decision is called

A decision request is one `POST` (to Jev's `/v1/systemone` or the Laya sidecar's `/v1/decision`) with
a `state`, a `model`, and a `questions` map. Each question is one of the three primitives and answers
are returned under the same ids:

- **Choice** — pick an option. Returns `choice`, `probabilities`, `confidence`. (Used for routing
  when no specialist's Noul passes.)
- **Score** — rate on a rubric. Returns `score`, `legend`, `probabilities`, `confidence`.
- **Noul** — yes/no probability in `[0, 1]`. (Used for the per-specialist routing questions and the
  reply guardrails.)

Jev reference: https://docs.typesafe.ai/api. Laya: https://github.com/NandhaKishorM/laya.



