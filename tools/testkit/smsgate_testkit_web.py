#!/usr/bin/env python3
"""Browser-based fallback GUI for MessageGate test kit (no Tk required)."""

from __future__ import annotations

import html
import json
import os
import threading
import time
import urllib.parse
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from smsgate_testkit import FINAL_STATES, SmsGateClient


class State:
    def __init__(self) -> None:
        self.values = {
            "base_url": os.getenv("SMSGATE_URL", "http://192.168.0.37:8080"),
            "username": os.getenv("SMSGATE_USER", "sms"),
            "password": os.getenv("SMSGATE_PASS", ""),
            "timeout": "20",
            "to": "",
            "message_id": "",
            "sim_number": "",
            "sms_text": "Hello from MessageGate Web UI",
            "mms_text": "Hello MMS from MessageGate Web UI",
            "mms_file": "",
            "poll_wait": "120",
            "poll_interval": "3",
        }
        self.output = "Ready"


STATE = State()


def _safe_int(value: str, default: int) -> int:
    value = (value or "").strip()
    if not value:
        return default
    return int(value)


def _client(values: dict[str, str]) -> SmsGateClient:
    base_url = values["base_url"].strip()
    username = values["username"].strip()
    password = values["password"]
    timeout = _safe_int(values.get("timeout", "20"), 20)

    if not base_url:
        raise ValueError("Base URL is required")
    if not username:
        raise ValueError("Username is required")
    if not password:
        raise ValueError("Password is required")

    return SmsGateClient(base_url=base_url, username=username, password=password, timeout=timeout)


def _effective_id(values: dict[str, str], prefix: str) -> str:
    current = values.get("message_id", "").strip()
    if current:
        return current
    generated = f"{prefix}-{int(time.time())}"
    values["message_id"] = generated
    return generated


def _sim_number(values: dict[str, str]) -> int | None:
    raw = values.get("sim_number", "").strip()
    if not raw:
        return None
    return int(raw)


def _poll_message(
    client: SmsGateClient,
    message_id: str,
    wait_seconds: int,
    interval: int,
) -> dict:
    deadline = time.time() + wait_seconds
    last = client.get_message(message_id)
    while time.time() < deadline and last.get("state") not in FINAL_STATES:
        time.sleep(interval)
        last = client.get_message(message_id)
    return last


def run_action(values: dict[str, str], action: str) -> dict:
    client = _client(values)

    if action == "health":
        return {"health": client.get_health()}

    if action == "send_sms":
        to = values["to"].strip()
        text = values["sms_text"].strip()
        if not to:
            raise ValueError("Recipient number is required")
        if not text:
            raise ValueError("SMS text is required")

        message_id = _effective_id(values, "webui-sms")
        sent = client.send_sms(to, text, message_id, _sim_number(values))
        final = _poll_message(
            client,
            message_id,
            _safe_int(values.get("poll_wait", "120"), 120),
            _safe_int(values.get("poll_interval", "3"), 3),
        )
        return {"send": sent, "final": final}

    if action == "send_mms":
        to = values["to"].strip()
        path = values["mms_file"].strip()
        if not to:
            raise ValueError("Recipient number is required")
        if not path:
            raise ValueError("MMS file path is required")

        message_id = _effective_id(values, "webui-mms")
        sent = client.send_mms(to, values.get("mms_text") or None, path, message_id, _sim_number(values))
        final = _poll_message(
            client,
            message_id,
            _safe_int(values.get("poll_wait", "120"), 120),
            _safe_int(values.get("poll_interval", "3"), 3),
        )
        return {"send": sent, "final": final}

    if action == "poll_message":
        message_id = values.get("message_id", "").strip()
        if not message_id:
            raise ValueError("Message ID is required")
        return {
            "message": _poll_message(
                client,
                message_id,
                _safe_int(values.get("poll_wait", "120"), 120),
                _safe_int(values.get("poll_interval", "3"), 3),
            )
        }

    raise ValueError(f"Unknown action: {action}")


def _render_page() -> str:
    v = STATE.values
    out = html.escape(STATE.output)
    return f"""
<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>MessageGate Tiny Web UI</title>
  <style>
    body {{ font-family: Arial, sans-serif; margin: 18px; background: #f6f8fb; color: #1b1f24; }}
    .box {{ background: #fff; border: 1px solid #d8e0ea; border-radius: 8px; padding: 12px; margin-bottom: 12px; }}
    label {{ display:block; font-size: 12px; color: #4a5568; margin-top: 8px; }}
    input {{ width: 100%; padding: 8px; box-sizing: border-box; border: 1px solid #cbd5e0; border-radius: 6px; }}
    .row {{ display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; }}
    .row4 {{ display: grid; grid-template-columns: 2fr 1fr 1fr 1fr; gap: 10px; }}
    button {{ margin-right: 8px; margin-top: 10px; padding: 10px 14px; border: 0; border-radius: 6px; background: #0e7490; color: white; cursor: pointer; }}
    pre {{ background: #0f172a; color: #e2e8f0; padding: 12px; border-radius: 8px; max-height: 360px; overflow: auto; }}
    .hint {{ font-size: 12px; color: #64748b; }}
  </style>
</head>
<body>
  <h2>MessageGate Tiny Web UI</h2>
  <form method="post" action="/run">
    <div class="box">
      <h3>Connection</h3>
      <div class="row4">
        <div>
          <label>Base URL</label>
          <input name="base_url" value="{html.escape(v['base_url'])}" />
        </div>
        <div>
          <label>Username</label>
          <input name="username" value="{html.escape(v['username'])}" />
        </div>
        <div>
          <label>Password</label>
          <input name="password" value="{html.escape(v['password'])}" />
        </div>
        <div>
          <label>Timeout</label>
          <input name="timeout" value="{html.escape(v['timeout'])}" />
        </div>
      </div>
    </div>

    <div class="box">
      <h3>Message</h3>
      <div class="row">
        <div>
          <label>To (E.164)</label>
          <input name="to" value="{html.escape(v['to'])}" />
        </div>
        <div>
          <label>Message ID</label>
          <input name="message_id" value="{html.escape(v['message_id'])}" />
        </div>
        <div>
          <label>SIM number (optional)</label>
          <input name="sim_number" value="{html.escape(v['sim_number'])}" />
        </div>
      </div>
      <label>SMS text</label>
      <input name="sms_text" value="{html.escape(v['sms_text'])}" />
      <label>MMS text</label>
      <input name="mms_text" value="{html.escape(v['mms_text'])}" />
      <label>MMS file path (on this computer)</label>
      <input name="mms_file" value="{html.escape(v['mms_file'])}" />

      <div class="row">
        <div>
          <label>Poll wait seconds</label>
          <input name="poll_wait" value="{html.escape(v['poll_wait'])}" />
        </div>
        <div>
          <label>Poll interval seconds</label>
          <input name="poll_interval" value="{html.escape(v['poll_interval'])}" />
        </div>
      </div>

      <div>
        <button name="action" value="health">Health</button>
        <button name="action" value="send_sms">Send SMS</button>
        <button name="action" value="send_mms">Send MMS</button>
        <button name="action" value="poll_message">Poll Message</button>
      </div>
      <p class="hint">MMS in web UI uses local file path (for simplicity/no dependencies).</p>
    </div>
  </form>

  <div class="box">
    <h3>Output</h3>
    <pre>{out}</pre>
  </div>
</body>
</html>
"""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802
        if self.path != "/":
            self.send_error(404)
            return
        page = _render_page().encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(page)))
        self.end_headers()
        self.wfile.write(page)

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/run":
            self.send_error(404)
            return

        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length)
        form = urllib.parse.parse_qs(raw.decode("utf-8", errors="replace"), keep_blank_values=True)

        for key in STATE.values:
            if key in form:
                STATE.values[key] = form[key][0]

        action = form.get("action", [""])[0]
        try:
            result = run_action(dict(STATE.values), action)
            STATE.output = json.dumps(result, ensure_ascii=False, indent=2)
        except Exception as exc:  # pylint: disable=broad-except
            STATE.output = f"ERROR: {exc}"

        self.send_response(303)
        self.send_header("Location", "/")
        self.end_headers()

    def log_message(self, format: str, *args) -> None:
        return


def main() -> int:
    host = "127.0.0.1"
    port = 8765

    server = ThreadingHTTPServer((host, port), Handler)
    url = f"http://{host}:{port}/"

    print(f"MessageGate tiny web UI listening at {url}")
    threading.Timer(0.5, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
