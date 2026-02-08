#!/usr/bin/env python3
"""Tiny cross-platform MessageGate test kit.

No third-party dependencies. Works on macOS, Linux, and Windows with Python 3.9+.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import mimetypes
import os
import sys
import time
import uuid
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib import error, parse, request


FINAL_STATES = {"Delivered", "Sent", "Failed"}


def _encode_basic_auth(username: str, password: str) -> str:
    token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def _pretty(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2)


def _safe_read_json(raw: bytes) -> Any:
    text = raw.decode("utf-8", errors="replace")
    return json.loads(text)


def _build_multipart(fields: dict[str, str], files: list[dict[str, Any]]) -> tuple[bytes, str]:
    boundary = f"----SMSGATEKIT{uuid.uuid4().hex}"
    lines: list[bytes] = []

    for name, value in fields.items():
        lines.extend(
            [
                f"--{boundary}".encode("utf-8"),
                f'Content-Disposition: form-data; name="{name}"'.encode("utf-8"),
                b"",
                str(value).encode("utf-8"),
            ]
        )

    for item in files:
        filename = item["filename"]
        mime_type = item["mime_type"]
        data = item["data"]
        lines.extend(
            [
                f"--{boundary}".encode("utf-8"),
                (
                    f'Content-Disposition: form-data; name="{item["field"]}"; '
                    f'filename="{filename}"'
                ).encode("utf-8"),
                f"Content-Type: {mime_type}".encode("utf-8"),
                b"",
                data,
            ]
        )

    lines.append(f"--{boundary}--".encode("utf-8"))
    lines.append(b"")

    body = b"\r\n".join(lines)
    return body, f"multipart/form-data; boundary={boundary}"


@dataclass
class SmsGateClient:
    base_url: str
    username: str
    password: str
    timeout: int = 20

    def __post_init__(self) -> None:
        self.base_url = self.base_url.rstrip("/")
        self._auth = _encode_basic_auth(self.username, self.password)

    def _request(
        self,
        method: str,
        path: str,
        body: bytes | None = None,
        content_type: str | None = None,
        authenticated: bool = True,
    ) -> tuple[int, bytes, dict[str, str]]:
        url = f"{self.base_url}{path}"
        headers: dict[str, str] = {}
        if authenticated:
            headers["Authorization"] = self._auth
        if content_type:
            headers["Content-Type"] = content_type

        req = request.Request(url=url, method=method, data=body, headers=headers)
        try:
            with request.urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read()
                resp_headers = {k.lower(): v for k, v in resp.headers.items()}
                return resp.status, raw, resp_headers
        except error.HTTPError as exc:
            raw = exc.read()
            resp_headers = {k.lower(): v for k, v in exc.headers.items()}
            return exc.code, raw, resp_headers

    def get_health(self) -> Any:
        status, raw, _ = self._request("GET", "/health", authenticated=False)
        if status != 200:
            raise RuntimeError(f"health failed with status {status}: {raw.decode('utf-8', 'replace')}")
        return _safe_read_json(raw)

    def send_sms(
        self,
        phone: str,
        text: str,
        message_id: str | None,
        sim_number: int | None,
    ) -> Any:
        payload: dict[str, Any] = {
            "id": message_id,
            "phoneNumbers": [phone],
            "textMessage": {"text": text},
            "withDeliveryReport": True,
        }
        if sim_number is not None:
            payload["simNumber"] = sim_number

        body = json.dumps(payload).encode("utf-8")
        status, raw, _ = self._request("POST", "/message", body, "application/json")
        if status not in (200, 202):
            raise RuntimeError(f"send sms failed with status {status}: {raw.decode('utf-8', 'replace')}")
        return _safe_read_json(raw)

    def send_mms(
        self,
        phone: str,
        text: str | None,
        file_path: str,
        message_id: str | None,
        sim_number: int | None,
    ) -> Any:
        path = os.path.abspath(file_path)
        if not os.path.isfile(path):
            raise FileNotFoundError(f"attachment not found: {path}")

        with open(path, "rb") as handle:
            data = handle.read()

        mime_type = mimetypes.guess_type(path)[0] or "application/octet-stream"
        fields: dict[str, str] = {
            "phoneNumbers": phone,
        }
        if text:
            fields["text"] = text
        if message_id:
            fields["id"] = message_id
        if sim_number is not None:
            fields["simNumber"] = str(sim_number)

        body, content_type = _build_multipart(
            fields,
            [
                {
                    "field": "file",
                    "filename": os.path.basename(path),
                    "mime_type": mime_type,
                    "data": data,
                }
            ],
        )
        status, raw, _ = self._request("POST", "/message", body, content_type)
        if status not in (200, 202):
            raise RuntimeError(f"send mms failed with status {status}: {raw.decode('utf-8', 'replace')}")
        return _safe_read_json(raw)

    def get_message(self, message_id: str) -> Any:
        status, raw, _ = self._request("GET", f"/messages/{parse.quote(message_id)}")
        if status != 200:
            raise RuntimeError(
                f"get message failed with status {status}: {raw.decode('utf-8', 'replace')}"
            )
        return _safe_read_json(raw)

    def list_webhooks(self) -> Any:
        status, raw, _ = self._request("GET", "/webhooks")
        if status != 200:
            raise RuntimeError(
                f"list webhooks failed with status {status}: {raw.decode('utf-8', 'replace')}"
            )
        return _safe_read_json(raw)

    def register_webhook(self, webhook_id: str, url: str, event: str, source: str) -> Any:
        payload = {
            "id": webhook_id,
            "deviceId": None,
            "url": url,
            "event": event,
            "source": source,
        }
        body = json.dumps(payload).encode("utf-8")
        status, raw, _ = self._request("POST", "/webhooks", body, "application/json")
        if status not in (200, 201):
            raise RuntimeError(
                f"register webhook failed with status {status}: {raw.decode('utf-8', 'replace')}"
            )
        return _safe_read_json(raw)

    def delete_webhook(self, webhook_id: str) -> int:
        status, raw, _ = self._request("DELETE", f"/webhooks/{parse.quote(webhook_id)}")
        if status not in (200, 204):
            raise RuntimeError(
                f"delete webhook failed with status {status}: {raw.decode('utf-8', 'replace')}"
            )
        return status


def _resolve_credentials(args: argparse.Namespace) -> tuple[str, str, str]:
    base_url = args.base_url or os.getenv("SMSGATE_URL")
    username = args.username or os.getenv("SMSGATE_USER")
    password = args.password or os.getenv("SMSGATE_PASS")

    if not base_url:
        raise ValueError("Missing base URL. Use --base-url or SMSGATE_URL.")
    if not username:
        raise ValueError("Missing username. Use --username or SMSGATE_USER.")
    if not password:
        raise ValueError("Missing password. Use --password or SMSGATE_PASS.")

    return base_url, username, password


def _poll_message(client: SmsGateClient, message_id: str, wait_seconds: int, interval: int) -> Any:
    deadline = time.time() + wait_seconds
    last: Any = None
    while time.time() < deadline:
        last = client.get_message(message_id)
        state = last.get("state")
        print(f"state={state}")
        if state in FINAL_STATES:
            return last
        time.sleep(interval)
    if last is None:
        raise RuntimeError("No message state available")
    return last


class WebhookHandler(BaseHTTPRequestHandler):
    signing_key: str | None = None

    def do_POST(self) -> None:  # noqa: N802
        content_length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(content_length)
        body_text = raw.decode("utf-8", errors="replace")

        timestamp = self.headers.get("X-Timestamp")
        signature = self.headers.get("X-Signature")
        signature_ok = None
        if self.signing_key and timestamp and signature:
            payload = body_text + timestamp
            digest = hmac.new(
                self.signing_key.encode("utf-8"),
                payload.encode("utf-8"),
                hashlib.sha256,
            ).hexdigest()
            signature_ok = hmac.compare_digest(digest, signature)

        print("=" * 72)
        print(f"Webhook {self.command} {self.path}")
        print(f"X-Timestamp: {timestamp}")
        print(f"X-Signature: {signature}")
        if signature_ok is not None:
            print(f"Signature valid: {signature_ok}")
        try:
            print(_pretty(json.loads(body_text)))
        except json.JSONDecodeError:
            print(body_text)

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"ok":true}')

    def log_message(self, format: str, *args: Any) -> None:
        return


def _cmd_health(args: argparse.Namespace) -> int:
    base_url, username, password = _resolve_credentials(args)
    client = SmsGateClient(base_url, username, password, timeout=args.timeout)
    print(_pretty(client.get_health()))
    return 0


def _cmd_send_sms(args: argparse.Namespace) -> int:
    base_url, username, password = _resolve_credentials(args)
    client = SmsGateClient(base_url, username, password, timeout=args.timeout)
    message_id = args.id or f"test-sms-{int(time.time())}"
    response = client.send_sms(args.to, args.text, message_id, args.sim_number)
    print("send response:")
    print(_pretty(response))
    if args.poll:
        final = _poll_message(client, message_id, args.wait_seconds, args.interval)
        print("final state:")
        print(_pretty(final))
    return 0


def _cmd_send_mms(args: argparse.Namespace) -> int:
    base_url, username, password = _resolve_credentials(args)
    client = SmsGateClient(base_url, username, password, timeout=args.timeout)
    message_id = args.id or f"test-mms-{int(time.time())}"
    response = client.send_mms(args.to, args.text, args.file, message_id, args.sim_number)
    print("send response:")
    print(_pretty(response))
    if args.poll:
        final = _poll_message(client, message_id, args.wait_seconds, args.interval)
        print("final state:")
        print(_pretty(final))
    return 0


def _cmd_get_message(args: argparse.Namespace) -> int:
    base_url, username, password = _resolve_credentials(args)
    client = SmsGateClient(base_url, username, password, timeout=args.timeout)
    print(_pretty(client.get_message(args.id)))
    return 0


def _cmd_webhooks_list(args: argparse.Namespace) -> int:
    base_url, username, password = _resolve_credentials(args)
    client = SmsGateClient(base_url, username, password, timeout=args.timeout)
    print(_pretty(client.list_webhooks()))
    return 0


def _cmd_webhooks_register(args: argparse.Namespace) -> int:
    base_url, username, password = _resolve_credentials(args)
    client = SmsGateClient(base_url, username, password, timeout=args.timeout)
    print(
        _pretty(
            client.register_webhook(
                webhook_id=args.id,
                url=args.url,
                event=args.event,
                source=args.source,
            )
        )
    )
    return 0


def _cmd_webhooks_delete(args: argparse.Namespace) -> int:
    base_url, username, password = _resolve_credentials(args)
    client = SmsGateClient(base_url, username, password, timeout=args.timeout)
    status = client.delete_webhook(args.id)
    print(f"deleted webhook {args.id}, status={status}")
    return 0


def _cmd_smoke(args: argparse.Namespace) -> int:
    base_url, username, password = _resolve_credentials(args)
    client = SmsGateClient(base_url, username, password, timeout=args.timeout)

    print("[1/4] health")
    print(_pretty(client.get_health()))

    print("[2/4] send sms")
    sms_id = f"smoke-sms-{int(time.time())}"
    print(_pretty(client.send_sms(args.to, args.sms_text, sms_id, args.sim_number)))
    sms_final = _poll_message(client, sms_id, args.wait_seconds, args.interval)
    print("sms final:")
    print(_pretty(sms_final))

    if args.mms_file:
        print("[3/4] send mms")
        mms_id = f"smoke-mms-{int(time.time())}"
        print(
            _pretty(
                client.send_mms(
                    args.to,
                    args.mms_text,
                    args.mms_file,
                    mms_id,
                    args.sim_number,
                )
            )
        )
        mms_final = _poll_message(client, mms_id, args.wait_seconds, args.interval)
        print("mms final:")
        print(_pretty(mms_final))

    print("[4/4] done")
    return 0


def _cmd_listen(args: argparse.Namespace) -> int:
    WebhookHandler.signing_key = args.signing_key
    host = args.host
    port = args.port
    print(f"Listening on http://{host}:{port} ...")
    if args.signing_key:
        print("Signature verification enabled")

    with ThreadingHTTPServer((host, port), WebhookHandler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nStopped")
    return 0


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Tiny cross-platform MessageGate test kit")
    parser.add_argument("--base-url", help="Base URL, e.g. http://192.168.0.37:8080")
    parser.add_argument("--username", help="Basic auth username")
    parser.add_argument("--password", help="Basic auth password")
    parser.add_argument("--timeout", type=int, default=20, help="HTTP timeout in seconds")

    sub = parser.add_subparsers(dest="command", required=True)

    p_health = sub.add_parser("health", help="Check /health")
    p_health.set_defaults(func=_cmd_health)

    p_sms = sub.add_parser("send-sms", help="Send SMS via /message")
    p_sms.add_argument("--to", required=True, help="Recipient phone number in E.164 format")
    p_sms.add_argument("--text", required=True, help="SMS text")
    p_sms.add_argument("--id", help="Optional message id")
    p_sms.add_argument("--sim-number", type=int, help="SIM slot (1-based)")
    p_sms.add_argument("--poll", action="store_true", help="Poll final state")
    p_sms.add_argument("--wait-seconds", type=int, default=90, help="Polling timeout")
    p_sms.add_argument("--interval", type=int, default=3, help="Polling interval")
    p_sms.set_defaults(func=_cmd_send_sms)

    p_mms = sub.add_parser("send-mms", help="Send MMS via multipart /message")
    p_mms.add_argument("--to", required=True, help="Recipient phone number in E.164 format")
    p_mms.add_argument("--file", required=True, help="Attachment file path")
    p_mms.add_argument("--text", help="Optional MMS text")
    p_mms.add_argument("--id", help="Optional message id")
    p_mms.add_argument("--sim-number", type=int, help="SIM slot (1-based)")
    p_mms.add_argument("--poll", action="store_true", help="Poll final state")
    p_mms.add_argument("--wait-seconds", type=int, default=120, help="Polling timeout")
    p_mms.add_argument("--interval", type=int, default=3, help="Polling interval")
    p_mms.set_defaults(func=_cmd_send_mms)

    p_msg = sub.add_parser("message", help="Get message state by id")
    p_msg.add_argument("--id", required=True, help="Message id")
    p_msg.set_defaults(func=_cmd_get_message)

    p_wl = sub.add_parser("webhooks-list", help="List configured webhooks")
    p_wl.set_defaults(func=_cmd_webhooks_list)

    p_wr = sub.add_parser("webhooks-register", help="Register webhook")
    p_wr.add_argument("--id", required=True, help="Webhook id")
    p_wr.add_argument("--url", required=True, help="Webhook URL")
    p_wr.add_argument(
        "--event",
        required=True,
        choices=[
            "sms:received",
            "sms:sent",
            "sms:delivered",
            "sms:failed",
            "sms:data-received",
            "mms:received",
            "system:ping",
        ],
        help="Webhook event type",
    )
    p_wr.add_argument("--source", default="Local", choices=["Local", "Cloud", "Gateway"])
    p_wr.set_defaults(func=_cmd_webhooks_register)

    p_wd = sub.add_parser("webhooks-delete", help="Delete webhook")
    p_wd.add_argument("--id", required=True, help="Webhook id")
    p_wd.set_defaults(func=_cmd_webhooks_delete)

    p_smoke = sub.add_parser("smoke", help="Health + SMS (+ optional MMS) quick smoke test")
    p_smoke.add_argument("--to", required=True, help="Recipient phone number in E.164 format")
    p_smoke.add_argument("--sms-text", default="MessageGate smoke SMS")
    p_smoke.add_argument("--mms-file", help="Optional file path for MMS test")
    p_smoke.add_argument("--mms-text", default="MessageGate smoke MMS")
    p_smoke.add_argument("--sim-number", type=int, help="SIM slot (1-based)")
    p_smoke.add_argument("--wait-seconds", type=int, default=120, help="Polling timeout")
    p_smoke.add_argument("--interval", type=int, default=3, help="Polling interval")
    p_smoke.set_defaults(func=_cmd_smoke)

    p_listen = sub.add_parser("listen-webhooks", help="Run local webhook test listener")
    p_listen.add_argument("--host", default="0.0.0.0")
    p_listen.add_argument("--port", type=int, default=8787)
    p_listen.add_argument("--signing-key", help="Optional signing key for signature verification")
    p_listen.set_defaults(func=_cmd_listen)

    return parser


def main() -> int:
    parser = _build_parser()
    args = parser.parse_args()
    try:
        return args.func(args)
    except Exception as exc:  # pylint: disable=broad-except
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
