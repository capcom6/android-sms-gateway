# MessageGate Tiny Test Kit

Small cross-platform utility for validating MessageGate locally before integrating into your own code.

- Works on macOS, Linux, and Windows
- No external Python packages required
- Supports SMS, MMS, message polling, webhook setup, and webhook listener
- Includes both CLI and tiny GUI modes

## Requirements

- Python 3.9+
- MessageGate app running on phone in `ONLINE` local mode
- Local API credentials and URL

## Quick start

Set environment variables once:

```bash
export SMSGATE_URL="http://192.168.0.37:8080"
export SMSGATE_USER="sms"
export SMSGATE_PASS="your-password"
```

Windows PowerShell:

```powershell
$env:SMSGATE_URL = "http://192.168.0.37:8080"
$env:SMSGATE_USER = "sms"
$env:SMSGATE_PASS = "your-password"
```

Run commands:

```bash
python3 tools/testkit/smsgate_testkit.py health
python3 tools/testkit/smsgate_testkit.py send-sms --to +4790214465 --text "hello" --poll
python3 tools/testkit/smsgate_testkit.py send-mms --to +4790214465 --file ./photo.jpg --text "hello mms" --poll
```

Or via wrappers:

- macOS/Linux: `tools/testkit/run-testkit.sh ...`
- Windows: `tools/testkit/run-testkit.bat ...`

## GUI mode (easy/manual testing)

Launch:

- macOS/Linux: `tools/testkit/run-testkit-gui.sh`
- Windows: `tools/testkit/run-testkit-gui.bat`
- Direct: `python3 tools/testkit/smsgate_testkit_gui.py`

If your Python does not include Tkinter (for example some Homebrew/system builds),
the launcher automatically falls back to a browser-based GUI at `http://127.0.0.1:8765`.

GUI includes:

- Connection fields (URL, username, password, timeout)
- Send SMS (with state polling)
- Send MMS (file picker + state polling)
- Poll existing message ID
- Live JSON output panel
- Automatic fallback to browser GUI when Tkinter is unavailable

## Most useful commands

### 1) Health

```bash
python3 tools/testkit/smsgate_testkit.py health
```

### 2) One-shot smoke test

```bash
python3 tools/testkit/smsgate_testkit.py smoke --to +4790214465 --mms-file ./photo.jpg
```

### 3) Poll message state

```bash
python3 tools/testkit/smsgate_testkit.py message --id test-sms-123
```

### 4) Webhook listener (for local validation)

```bash
python3 tools/testkit/smsgate_testkit.py listen-webhooks --port 8787
```

Then register webhook in MessageGate:

```bash
python3 tools/testkit/smsgate_testkit.py webhooks-register \
  --id test-sms-inbound \
  --event sms:received \
  --url http://<your-computer-ip>:8787/inbound
```

List/delete hooks:

```bash
python3 tools/testkit/smsgate_testkit.py webhooks-list
python3 tools/testkit/smsgate_testkit.py webhooks-delete --id test-sms-inbound
```

## Notes

- MMS final state often stops at `Sent` (carrier-dependent for delivery receipts).
- Use real E.164 phone numbers for live testing.
- For Phonero/Telenor MMS reliability, ensure mobile data is enabled on sending SIM and APN is correct.
- GUI requires Tkinter, which is bundled with standard Python installers on most platforms.
