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
| `jev` (default) | the hosted **TypeSafe Jev** endpoint over HTTP | a `JEV_API_KEY` |
| `laya` | a self-hosted **Laya** sidecar over HTTP (open source, Apache 2.0) | the sidecar running |
| `stub` | a deterministic offline stand-in | nothing |

Jev and Laya speak the same three primitives (Choice / Score / Noul) with an almost identical
response schema, so the agentic system is written once and can be pointed at either model. When the
chosen backend is unavailable (no key, sidecar down), the app falls back to the stub and keeps
answering, rather than failing the request.

The system is a car-rental "trip advisor": a customer request is routed to the right specialist,
which answers it, and the answer is validated before it is returned.

## What it demonstrates

1. **A decision model as an agentic Planner (router).** The top-level `@PlannerAgent` uses a custom
   [`JevRoutingPlanner`](src/main/java/com/tripplanner/poc/agentic/JevRoutingPlanner.java)
   instead of the framework's LLM-driven supervisor. On each request it reads the customer message,
   asks the model a single **Choice** question ("which specialist?"), and dispatches exactly the
   matching sub-agent. This mirrors the built-in `SupervisorPlanner`, but the routing intelligence is a
   cheap, calibrated decision rather than a chat call.
2. **A decision model as an output guardrail.** Each specialist's reply is checked by
   [`JevReplyGuardrail`](src/main/java/com/tripplanner/poc/guardrails/JevReplyGuardrail.java), an
   `OutputGuardrail` driven by a **Noul** (yes/no) probability: pass when it is clearly above 0.5,
   retry when clearly below, and pass-and-flag when it is within the margin of 0.5 (or missing).
3. **Backend choice + graceful fallback.** The decision backend is a property
   (`decision.backend` = `jev` | `laya` | `stub`). Every backend degrades to a deterministic
   [`StubDecisionClient`](src/main/java/com/tripplanner/poc/jev/StubDecisionClient.java) when the model
   is unavailable, so the whole agentic flow — routing + guardrail — always runs and stays testable
   without a key, a network, or a downloaded model.
4. **Swapping the model without touching the agents.** Because the router, guardrail, and REST layer
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
| `agentic/JevRoutingPlanner`, `JevRouter` | Decision-model-driven planner / router |
| `agentic/TripAdvisorSystem` | Top-level `@PlannerAgent` wiring the sub-agents |
| `agentic/*Agent` | The four specialist LLM agents |
| `guardrails/JevReplyGuardrail`, `RouteAudit` | Decision output guardrail + decision history |
| `resource/TripResource` | REST entry point |
| `laya-sidecar/` | The Python FastAPI service that hosts Laya |

## Requirements

- Java 21+, Maven 3.8+
- An `OPENAI_API_KEY` (or any OpenAI-compatible key) for the specialist agents' answers
- For the **`jev`** backend: a `JEV_API_KEY` (see https://docs.typesafe.ai/). Without it, requests fall
  back to the stub.
- For the **`laya`** backend: Python 3.10+ and the sidecar running (see [laya-sidecar/](laya-sidecar/)).
  First run downloads the Laya checkpoint from Hugging Face.

## Run it

```bash
export OPENAI_API_KEY=sk-...          # required for the LLM answers

# Backend 1: hosted Jev (set the key to use the live endpoint, otherwise it stubs)
export JEV_API_KEY=ts-...
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
  "request": "Will it rain in Lisbon next Tuesday?",
  "reply": "...",
  "route": "weather",
  "rawChoice": "weather",
  "backend": "stub",
  "model": "stub",
  "live": false
}
```

`route` is the normalized specialist the request was sent to; `rawChoice` is the model's original
option. `backend`/`model`/`live` report the backend that **actually answered** the request, so a
fallback to the stub shows up as `backend: "stub"`, `live: false`. When a configured model is
unreachable on a request, the app still answers via the stub and logs the fallback, e.g. `Laya
sidecar call failed ...; falling back to stub`. The `GET /trip/backend` endpoint reports the
**configured** backend instead, since it is an identity endpoint.

## Test it

The test suite runs **without** a Jev key and without any LLM — it verifies the routing decision,
the planner's sub-agent selection, the stub client, and all three guardrail branches (pass / retry /
uncertain):

```bash
./mvnw test
```

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
| `jev.api-key` | *(empty)* | Bearer key for the Jev API. Empty → deterministic stub. |
| `jev.endpoint` | `https://api.typesafe.ai/v1/systemone` | Jev System One endpoint. |
| `jev.model` | `jev-latest` | Jev model alias. |
| `laya.endpoint` | `http://localhost:8100/v1/decision` | Laya sidecar endpoint. |
| `laya.model` | `laya` | Label reported for the Laya backend. |
| `quarkus.langchain4j.openai.api-key` | — | LLM key for the specialist agents. |
| `quarkus.langchain4j.openai.chat-model.model-name` | `gpt-4o` | LLM used by the specialists. |

## How a decision is called

A decision request is one `POST` (to Jev's `/v1/systemone` or the Laya sidecar's `/v1/decision`) with
a `state`, a `model`, and a `questions` map. Each question is one of the three primitives and answers
are returned under the same ids:

- **Choice** — pick an option. Returns `choice`, `probabilities`, `confidence`. (Used for routing.)
- **Score** — rate on a rubric. Returns `score`, `legend`, `probabilities`, `confidence`.
- **Noul** — yes/no probability in `[0, 1]`. (Used for the reply guardrail.)

Jev reference: https://docs.typesafe.ai/api. Laya: https://github.com/NandhaKishorM/laya.



