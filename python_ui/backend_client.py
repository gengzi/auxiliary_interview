import base64
import json
import urllib.request


TEXT_TIMEOUT = 180
IMAGE_TIMEOUT = 240


class BackendClient:
    def __init__(self, base_url):
        self._base_url = base_url.rstrip("/")

    def solve(self, text):
        payload = {"text": text}
        return self._post_json("/api/solve", payload, TEXT_TIMEOUT).get("answer", "")

    def solve_stream(self, text, chunk_consumer):
        payload = {"text": text}
        self._post_sse("/api/solve-stream", payload, TEXT_TIMEOUT, chunk_consumer)

    def solve_with_image(self, image_bytes, question):
        payload = {
            "question": question,
            "image": base64.b64encode(image_bytes).decode("utf-8"),
        }
        return self._post_json("/api/solve-image", payload, IMAGE_TIMEOUT).get("answer", "")

    def solve_with_image_stream(self, image_bytes, question, chunk_consumer):
        payload = {
            "question": question,
            "image": base64.b64encode(image_bytes).decode("utf-8"),
        }
        self._post_sse("/api/solve-image-stream", payload, IMAGE_TIMEOUT, chunk_consumer)

    def _post_json(self, path, payload, timeout):
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            self._base_url + path,
            data=data,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8")
            if resp.status < 200 or resp.status >= 300:
                raise RuntimeError(f"Backend error: {resp.status} {body}")
            return json.loads(body)

    def _post_sse(self, path, payload, timeout, chunk_consumer):
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            self._base_url + path,
            data=data,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            if resp.status < 200 or resp.status >= 300:
                raise RuntimeError(f"Backend error: {resp.status}")
            for raw in resp:
                if not raw:
                    continue
                line = raw.decode("utf-8", errors="ignore").strip()
                if not line.startswith("data:"):
                    continue
                data_line = line[5:].strip()
                if not data_line:
                    continue
                try:
                    chunk_consumer(data_line)
                except Exception:
                    pass
