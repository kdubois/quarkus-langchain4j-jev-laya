"""Laya decision sidecar.

A tiny FastAPI service that wraps the Laya System One decision model and exposes it with the same
request/response shape the Quarkus app already speaks. The Quarkus side's LayaDecisionClient simply
POSTs a JevRequest here and reads the JevResponse back, so the agentic system is identical no
matter whether the backend is the hosted Jev API, this Laya sidecar, or the offline stub.

Request  (POST /v1/decision):
    {
      "state": "<text or JSON the model decides over>",
      "model": "<ignored, for compatibility with the Jev client>",
      "questions": { "<id>": { "type": "choice|score|noul", "instructions": "...", "criteria": ... } }
    }

Response: Laya's own output, which already matches the Jev answer schema:
    { "model": "...", "answers": { "<id>": { "type", "choice"/"score"/"noul", "probabilities", "confidence", ... } }, "usage": {...} }

Run:
    uvicorn main:app --host 0.0.0.0 --port 8100
"""
import os
import logging

from fastapi import FastAPI
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("laya-sidecar")

# Which checkpoint to load. Default to the RLCD-trained typed-decisions checkpoint, which is the
# one tuned for exactly the choice/score/noul questions this app asks. Override with LAYA_MODEL.
LAYA_MODEL = os.environ.get("LAYA_MODEL", "convaiinnovations/laya-typed-decisions")
DEVICE = os.environ.get("LAYA_DEVICE", "")  # empty = auto (cuda > mps > cpu)


class Question(BaseModel):
    type: str
    instructions: str
    criteria: object | None = None


class DecisionRequest(BaseModel):
    state: object
    model: str | None = None
    questions: dict[str, Question]


app = FastAPI(title="Laya decision sidecar", version="1.0")

_agent = None


def get_agent():
    """Load the Laya agent once, lazily. Import and checkpoint download happen on first request
    (or at startup if you call warm()), not at import time, so the app boots fast."""
    global _agent
    if _agent is None:
        from laya import Agent  # imported here so `uvicorn --reload` / health work without laya
        device = DEVICE or None
        log.info("Loading Laya model '%s' (device=%s)...", LAYA_MODEL, device or "auto")
        _agent = Agent(LAYA_MODEL, device=device)
        log.info("Laya model loaded.")
    return _agent


@app.on_event("startup")
def warm() -> None:
    # Preload at startup so the first request is fast. Disable with LAYA_WARM=0 if the checkpoint
    # is not downloaded yet and you do not want a startup failure.
    if os.environ.get("LAYA_WARM", "1") == "1":
        try:
            get_agent()
        except Exception:
            log.exception("Failed to preload the Laya model at startup; will retry on first request")


@app.get("/health")
def health():
    return {"status": "ok", "model": LAYA_MODEL, "loaded": _agent is not None}


@app.post("/v1/decision")
def decision(req: DecisionRequest):
    agent = get_agent()
    questions = {qid: q.model_dump(exclude_none=True) for qid, q in req.questions.items()}
    result = agent.predict(req.state, questions)
    # Return Laya's response verbatim; its answer schema matches JevResponse/JevAnswer.
    return result
