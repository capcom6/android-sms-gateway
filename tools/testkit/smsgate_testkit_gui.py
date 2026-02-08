#!/usr/bin/env python3
"""Tiny cross-platform GUI for MessageGate test kit."""

from __future__ import annotations

import json
import os
import threading
import time
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from tkinter.scrolledtext import ScrolledText

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in os.sys.path:
    os.sys.path.insert(0, SCRIPT_DIR)

from smsgate_testkit import FINAL_STATES, SmsGateClient  # noqa: E402


class TestKitGui(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("MessageGate Tiny Test Kit")
        self.geometry("900x680")
        self.minsize(760, 560)

        self.base_url_var = tk.StringVar(value=os.getenv("SMSGATE_URL", "http://192.168.0.37:8080"))
        self.username_var = tk.StringVar(value=os.getenv("SMSGATE_USER", "sms"))
        self.password_var = tk.StringVar(value=os.getenv("SMSGATE_PASS", ""))
        self.timeout_var = tk.StringVar(value="20")

        self.to_var = tk.StringVar(value="")
        self.sms_text_var = tk.StringVar(value="Hello from MessageGate GUI")
        self.mms_text_var = tk.StringVar(value="Hello MMS from MessageGate GUI")
        self.mms_file_var = tk.StringVar(value="")
        self.message_id_var = tk.StringVar(value="")
        self.sim_number_var = tk.StringVar(value="")
        self.poll_wait_var = tk.StringVar(value="120")
        self.poll_interval_var = tk.StringVar(value="3")

        self._build_layout()

    def _build_layout(self) -> None:
        root = ttk.Frame(self, padding=12)
        root.pack(fill=tk.BOTH, expand=True)

        cred = ttk.LabelFrame(root, text="Connection", padding=10)
        cred.pack(fill=tk.X)

        ttk.Label(cred, text="Base URL").grid(row=0, column=0, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(cred, textvariable=self.base_url_var, width=52).grid(
            row=0, column=1, sticky=tk.EW, padx=4, pady=4
        )

        ttk.Label(cred, text="Username").grid(row=0, column=2, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(cred, textvariable=self.username_var, width=12).grid(
            row=0, column=3, sticky=tk.EW, padx=4, pady=4
        )

        ttk.Label(cred, text="Password").grid(row=0, column=4, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(cred, textvariable=self.password_var, width=16, show="*").grid(
            row=0, column=5, sticky=tk.EW, padx=4, pady=4
        )

        ttk.Label(cred, text="Timeout(s)").grid(row=0, column=6, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(cred, textvariable=self.timeout_var, width=8).grid(
            row=0, column=7, sticky=tk.EW, padx=4, pady=4
        )

        cred.columnconfigure(1, weight=1)

        msg = ttk.LabelFrame(root, text="Message", padding=10)
        msg.pack(fill=tk.X, pady=(10, 0))

        ttk.Label(msg, text="To (E.164)").grid(row=0, column=0, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(msg, textvariable=self.to_var, width=20).grid(row=0, column=1, sticky=tk.W, padx=4, pady=4)

        ttk.Label(msg, text="Message ID").grid(row=0, column=2, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(msg, textvariable=self.message_id_var, width=24).grid(
            row=0, column=3, sticky=tk.W, padx=4, pady=4
        )

        ttk.Label(msg, text="SIM (optional)").grid(row=0, column=4, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(msg, textvariable=self.sim_number_var, width=8).grid(
            row=0, column=5, sticky=tk.W, padx=4, pady=4
        )

        ttk.Label(msg, text="SMS Text").grid(row=1, column=0, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(msg, textvariable=self.sms_text_var).grid(
            row=1, column=1, columnspan=5, sticky=tk.EW, padx=4, pady=4
        )

        ttk.Label(msg, text="MMS Text").grid(row=2, column=0, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(msg, textvariable=self.mms_text_var).grid(
            row=2, column=1, columnspan=5, sticky=tk.EW, padx=4, pady=4
        )

        ttk.Label(msg, text="MMS File").grid(row=3, column=0, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(msg, textvariable=self.mms_file_var).grid(
            row=3, column=1, columnspan=4, sticky=tk.EW, padx=4, pady=4
        )
        ttk.Button(msg, text="Browse", command=self._browse_file).grid(
            row=3, column=5, sticky=tk.W, padx=4, pady=4
        )

        ttk.Label(msg, text="Poll wait(s)").grid(row=4, column=0, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(msg, textvariable=self.poll_wait_var, width=8).grid(
            row=4, column=1, sticky=tk.W, padx=4, pady=4
        )
        ttk.Label(msg, text="Poll interval(s)").grid(row=4, column=2, sticky=tk.W, padx=4, pady=4)
        ttk.Entry(msg, textvariable=self.poll_interval_var, width=8).grid(
            row=4, column=3, sticky=tk.W, padx=4, pady=4
        )

        msg.columnconfigure(1, weight=1)

        actions = ttk.Frame(root)
        actions.pack(fill=tk.X, pady=(10, 0))

        self.health_button = ttk.Button(actions, text="Health", command=self._on_health)
        self.health_button.pack(side=tk.LEFT, padx=(0, 6))

        self.sms_button = ttk.Button(actions, text="Send SMS", command=self._on_send_sms)
        self.sms_button.pack(side=tk.LEFT, padx=(0, 6))

        self.mms_button = ttk.Button(actions, text="Send MMS", command=self._on_send_mms)
        self.mms_button.pack(side=tk.LEFT, padx=(0, 6))

        self.poll_button = ttk.Button(actions, text="Poll Message ID", command=self._on_poll_message)
        self.poll_button.pack(side=tk.LEFT, padx=(0, 6))

        ttk.Button(actions, text="Clear Output", command=self._clear_output).pack(side=tk.RIGHT)

        output_frame = ttk.LabelFrame(root, text="Output", padding=8)
        output_frame.pack(fill=tk.BOTH, expand=True, pady=(10, 0))

        self.output = ScrolledText(output_frame, wrap=tk.WORD, height=20)
        self.output.pack(fill=tk.BOTH, expand=True)

    def _clear_output(self) -> None:
        self.output.delete("1.0", tk.END)

    def _browse_file(self) -> None:
        path = filedialog.askopenfilename(title="Select MMS Attachment")
        if path:
            self.mms_file_var.set(path)

    def _append(self, text: str) -> None:
        self.output.insert(tk.END, text + "\n")
        self.output.see(tk.END)

    def _append_json(self, data: object) -> None:
        self._append(json.dumps(data, ensure_ascii=False, indent=2))

    def _run_async(self, title: str, func) -> None:
        def worker() -> None:
            started_at = time.time()
            self.after(0, lambda: self._append(f"[{title}] started"))
            try:
                result = func()
                elapsed = time.time() - started_at
                self.after(0, lambda: self._append(f"[{title}] completed in {elapsed:.1f}s"))
                if result is not None:
                    self.after(0, lambda: self._append_json(result))
            except Exception as exc:  # pylint: disable=broad-except
                self.after(0, lambda: self._append(f"[{title}] ERROR: {exc}"))

        threading.Thread(target=worker, daemon=True).start()

    def _get_client(self) -> SmsGateClient:
        base_url = self.base_url_var.get().strip()
        username = self.username_var.get().strip()
        password = self.password_var.get()
        timeout = int(self.timeout_var.get().strip() or "20")

        if not base_url:
            raise ValueError("Base URL is required")
        if not username:
            raise ValueError("Username is required")
        if not password:
            raise ValueError("Password is required")

        return SmsGateClient(base_url=base_url, username=username, password=password, timeout=timeout)

    def _effective_message_id(self, prefix: str) -> str:
        value = self.message_id_var.get().strip()
        if value:
            return value
        generated = f"{prefix}-{int(time.time())}"
        self.message_id_var.set(generated)
        return generated

    def _effective_sim_number(self) -> int | None:
        value = self.sim_number_var.get().strip()
        if not value:
            return None
        return int(value)

    def _poll_message_state(self, client: SmsGateClient, message_id: str) -> dict:
        wait_seconds = int(self.poll_wait_var.get().strip() or "120")
        interval = int(self.poll_interval_var.get().strip() or "3")
        deadline = time.time() + wait_seconds

        last = client.get_message(message_id)
        self._append(f"poll: state={last.get('state')}")

        while time.time() < deadline and last.get("state") not in FINAL_STATES:
            time.sleep(interval)
            last = client.get_message(message_id)
            self._append(f"poll: state={last.get('state')}")

        return last

    def _on_health(self) -> None:
        self._run_async("Health", lambda: self._get_client().get_health())

    def _on_send_sms(self) -> None:
        def task() -> dict:
            client = self._get_client()
            to = self.to_var.get().strip()
            text = self.sms_text_var.get()
            if not to:
                raise ValueError("Recipient number is required")
            if not text.strip():
                raise ValueError("SMS text is required")

            message_id = self._effective_message_id("gui-sms")
            response = client.send_sms(
                phone=to,
                text=text,
                message_id=message_id,
                sim_number=self._effective_sim_number(),
            )
            final = self._poll_message_state(client, message_id)
            return {"send": response, "final": final}

        self._run_async("Send SMS", task)

    def _on_send_mms(self) -> None:
        def task() -> dict:
            client = self._get_client()
            to = self.to_var.get().strip()
            file_path = self.mms_file_var.get().strip()
            if not to:
                raise ValueError("Recipient number is required")
            if not file_path:
                raise ValueError("MMS file is required")

            message_id = self._effective_message_id("gui-mms")
            response = client.send_mms(
                phone=to,
                text=self.mms_text_var.get().strip() or None,
                file_path=file_path,
                message_id=message_id,
                sim_number=self._effective_sim_number(),
            )
            final = self._poll_message_state(client, message_id)
            return {"send": response, "final": final}

        self._run_async("Send MMS", task)

    def _on_poll_message(self) -> None:
        def task() -> dict:
            client = self._get_client()
            message_id = self.message_id_var.get().strip()
            if not message_id:
                raise ValueError("Message ID is required for polling")
            return self._poll_message_state(client, message_id)

        self._run_async("Poll Message", task)


def main() -> int:
    try:
        app = TestKitGui()
        app.mainloop()
        return 0
    except Exception as exc:  # pylint: disable=broad-except
        messagebox.showerror("MessageGate Test Kit", str(exc))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
