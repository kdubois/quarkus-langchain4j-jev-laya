# Laya decision sidecar

A minimal FastAPI service that hosts the [Laya](https://github.com/NandhaKishorM/laya) System One
decision model and exposes it with the request/response shape the Quarkus app already speaks.

Laya is a Python library with no server of its own, so to use it from the Java app you run this sidecar
and point the app at it (`decision.backend=laya`, `laya.endpoint=...`). The sidecar forwards each
request to `Agent.predict()` and returns Laya's output verbatim, because Laya's answer schema matches
the Jev one (`choice`/`score`/`noul` + `probabilities` + `confidence`) that the Java side
deserializes into `JevResponse`/`JevAnswer`.

## Run the real sidecar

Requires Python 3.10+. First run downloads the checkpoint (~1 GB) from Hugging Face.

```bash
cd laya-sidecar
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8100
```

Then start the Quarkus app against it:

```bash
./mvnw "-Ddecision.backend=laya" quarkus:dev
```

### Environment variables

| Variable | Default | Meaning |
|----------|---------|---------|
| `LAYA_MODEL` | `convaiinnovations/laya-typed-decisions` | Checkpoint to load (or a local path). |
| `LAYA_DEVICE` | *(auto)* | Force a device: `cpu`, `mps`, or `cuda`. |
| `LAYA_WARM` | `1` | Set `0` to skip loading at startup (load on first request instead). |

## Endpoints

- `POST /v1/decision` — the decision endpoint the app calls. Body is `{ state, model, questions }`.
- `GET /health` — `{"status":"ok","model":...,"loaded":true|false}`.

## Mock sidecar (offline, no model download)

`mock_server.py` is a ~50-line stand-in that returns a fixed, valid Laya-shaped response. Use it to
exercise the Java Laya client path without installing `laya` or downloading the checkpoint:

```bash
python3 mock_server.py            # serves on 127.0.0.1:8110
# then:
./mvnw "-Ddecision.backend=laya" "-Dlaya.endpoint=http://localhost:8110/v1/decision" quarkus:dev
```

It always routes to `weather` for routing questions, so it is only for verifying the HTTP round-trip
and deserialization, not for real decisions.

## Notes

- Laya is Apache 2.0, but it is a young project. The checkpoint used by default
  (`laya-typed-decisions`) is the RLCD-trained model tuned for typed decisions; the base checkpoints
  score near chance zero-shot, so keep the typed-decisions checkpoint for this use case.
- On a CPU, expect on the order of a few hundred ms per decision (Laya is ~35 ms on a GPU).
- If the sidecar is down, the Quarkus app does not fail: it falls back to the offline stub and logs
  the event.
