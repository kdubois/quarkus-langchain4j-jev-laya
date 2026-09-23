import json
from http.server import BaseHTTPRequestHandler, HTTPServer


class H(BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        req = json.loads(self.rfile.read(n) or b"{}")
        answers = {}
        for qid, q in (req.get("questions") or {}).items():
            t = q.get("type")
            if t == "choice":
                # echo back the "weather" option to force a known route
                crit = q.get("criteria") or {}
                ans = "weather" if "weather" in crit else next(iter(crit))
                probs = {k: (0.9 if k == ans else 0.1) for k in crit}
                answers[qid] = {"type": "choice", "choice": ans, "probabilities": probs, "confidence": 0.9}
            elif t == "noul":
                answers[qid] = {"type": "noul", "noul": 0.9, "confidence": 0.9}
            elif t == "score":
                crit = q.get("criteria") or []
                answers[qid] = {"type": "score", "score": 0.0,
                                "legend": {str(i): c for i, c in enumerate(crit)},
                                "probabilities": {"0": 0.9, "1": 0.05, "2": 0.05}, "confidence": 0.9}
        body = json.dumps({"model": "laya-rl-agent", "answers": answers,
                           "usage": {"input_tokens": 42, "output_tokens": 0}}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    print("mock laya sidecar on :8110", flush=True)
    HTTPServer(("127.0.0.1", 8110), H).serve_forever()
