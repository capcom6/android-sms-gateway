# MessageGate -> Ticketing Integration Guide

This guide explains how to connect your ticketing backend to MessageGate for:

- inbound SMS/MMS -> ticket events
- outbound replies from tickets -> SMS/MMS sends
- delivery status sync back into tickets

All examples here are for **Local Server mode**.

## 1) Prerequisites

- MessageGate app installed and set to `ONLINE`
- Local server enabled in app
- Basic auth credentials from the app (`username` / `password`)
- Device IP and port (default `8080`)

Example base URL:

`http://<device-ip>:8080`

## 2) API auth model

- `/health` is public (no auth required)
- All other local API routes require **Basic Auth**

Example:

```bash
curl -u "<username>:<password>" http://<device-ip>:8080/
```

## 3) Integration architecture (recommended)

Use your ticketing backend as the source of truth:

1. Register webhooks in MessageGate pointing to your backend receiver.
2. On inbound events (`sms:received`, `mms:received`), create/update tickets.
3. On outbound request from ticket UI, call MessageGate `/message`.
4. Track message delivery via status webhooks (`sms:sent`, `sms:delivered`, `sms:failed`) and/or polling `/messages/{id}`.

Use `message.id` as correlation ID with your ticket side.

## 4) Register webhooks

Endpoint:

- `POST /webhooks`

Payload:

```json
{
  "id": "ticketing-sms-received",
  "deviceId": null,
  "url": "https://your-backend.example.com/integrations/smsgate/webhook",
  "event": "sms:received",
  "source": "Local"
}
```

Register each event you need:

- `sms:received`
- `mms:received`
- `sms:sent`
- `sms:delivered`
- `sms:failed`

List existing hooks:

```bash
curl -u "<username>:<password>" http://<device-ip>:8080/webhooks
```

Delete:

```bash
curl -X DELETE -u "<username>:<password>" http://<device-ip>:8080/webhooks/<id>
```

## 5) Webhook payload contract

MessageGate posts a JSON envelope:

```json
{
  "id": "evt_xxx",
  "webhookId": "ticketing-sms-received",
  "event": "sms:received",
  "deviceId": "<device-id>",
  "payload": {
    "messageId": "...",
    "phoneNumber": "+47...",
    "simNumber": 1
  }
}
```

### Event-specific payload fields

- `sms:received`: `message`, `receivedAt`, plus common `messageId`, `phoneNumber`, `simNumber`
- `mms:received`: `transactionId`, `subject`, `size`, `contentClass`, `attachments[]`, `receivedAt`
- `sms:sent`: `partsCount`, `sentAt`
- `sms:delivered`: `deliveredAt`
- `sms:failed`: `failedAt`, `reason`

### MMS attachment metadata

Each item in `attachments[]`:

- `id`
- `mimeType`
- `filename`
- `size`
- `width` (optional)
- `height` (optional)
- `durationMs` (optional)
- `sha256` (optional)
- `downloadUrl` (signed path, optional)

Example `downloadUrl`:

`/media/<id>?expires=<timestamp>&token=<signature>`

## 6) Webhook signature verification

MessageGate signs webhook payloads with:

- `X-Timestamp`: unix seconds
- `X-Signature`: lowercase hex HMAC-SHA256
- message to sign: `raw_request_body + X-Timestamp`

Use `settings.webhooks.signing_key` as the secret.

Minimal verifier logic:

1. Read raw request body as exact bytes/string.
2. Read `X-Timestamp` and reject too old/skewed requests.
3. Compute `HMAC_SHA256(secret, rawBody + timestamp)`.
4. Constant-time compare with `X-Signature`.

## 7) Outbound SMS from tickets

Endpoint:

- `POST /message`

```json
{
  "id": "ticket-123-msg-001",
  "phoneNumbers": ["+4790214465"],
  "textMessage": { "text": "Hello from support" },
  "withDeliveryReport": true,
  "simNumber": 1
}
```

Response returns message state object immediately (`Pending`).

Poll:

- `GET /messages/{id}`

Final states typically move through:

- `Pending` -> `Processed` -> `Sent` -> `Delivered`
- or `Failed` with `recipients[].error`

## 8) Outbound MMS from tickets

### JSON mode (base64 in payload)

```json
{
  "id": "ticket-123-mms-001",
  "phoneNumbers": ["+4790214465"],
  "mmsMessage": {
    "text": "See attached",
    "attachments": [
      {
        "mimeType": "image/jpeg",
        "filename": "photo.jpg",
        "data": "<base64>"
      }
    ]
  }
}
```

### Multipart mode (recommended for binary files)

```bash
curl -X POST -u "<username>:<password>" \
  -F "id=ticket-123-mms-002" \
  -F "phoneNumbers=+4790214465" \
  -F "text=See attached" \
  -F "file=@/path/to/photo.jpg;type=image/jpeg" \
  http://<device-ip>:8080/message
```

## 9) Download MMS media from webhooks

When webhook attachment contains `downloadUrl`, fetch with Basic Auth:

```bash
curl -u "<username>:<password>" \
  "http://<device-ip>:8080/media/<id>?expires=<ts>&token=<sig>" \
  --output attachment.bin
```

Security notes:

- token must be valid and unexpired
- URL is scoped to that media ID and TTL
- do not persist signed URLs long-term; store media in your backend

## 10) Suggested ticket mapping

Use this mapping in your backend:

- Ticket key: sender phone number + optional channel/tenant
- External message ID: MessageGate `message.id` (outbound) or webhook `payload.messageId` (inbound)
- Event idempotency key: webhook envelope `id`
- Attachment key: `attachments[].id` + `sha256`

Recommended dedupe rules:

- Ignore webhook if envelope `id` already processed
- For inbound SMS, dedupe by `(phoneNumber, message, receivedAt)` within a short window
- For inbound MMS, dedupe by `(transactionId, phoneNumber)` within a short window

## 11) Local test plan (before git push)

1. `GET /health` returns `status: pass`
2. Send outbound SMS to test number and confirm `Delivered`
3. Send outbound MMS (image) and confirm state reaches `Sent` or `Delivered` depending carrier/device
4. Trigger reply SMS and MMS from test number
5. Confirm webhook receiver gets:
   - `sms:received`
   - `mms:received`
6. If `attachments[]` present, fetch at least one `/media/...` URL and store in your backend

## 12) Carrier/device notes (Phonero + Pixel)

- MMS requires mobile data enabled on the sending SIM.
- If MMS fails intermittently, reset APN to defaults and reboot device.
- Verify APN/MMSC values with Phonero support (Telenor infrastructure).
- OEM/carrier behavior can affect MMS delivery timing and delivery receipts.
