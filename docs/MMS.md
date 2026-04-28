# MMS support

The gateway can **send** and **receive** multimedia messages (MMS) carrying
images, audio, video, and arbitrary binary attachments, in addition to SMS
and Data SMS.

This document covers:
- Enabling MMS on the device
- The `POST /message` shape for sending MMS
- Webhook events for received MMS
- Fetching attachments from a received MMS
- Limits, gotchas, and carrier notes

## Contents

- [Requirements](#requirements)
- [Setup](#setup)
  - [Grant the default SMS app role](#grant-the-default-sms-app-role)
- [Sending MMS](#sending-mms)
  - [API](#api)
  - [Attachment sources](#attachment-sources)
  - [Example: send an image](#example-send-an-image)
  - [Example: send multiple attachments with subject](#example-send-multiple-attachments-with-subject)
  - [Example: pull attachments from a URL](#example-pull-attachments-from-a-url)
- [Receiving MMS](#receiving-mms)
  - [Flow](#flow)
  - [Webhook: `mms:received`](#webhook-mmsreceived)
  - [Webhook: `mms:downloaded`](#webhook-mmsdownloaded)
  - [Fetching attachment bytes](#fetching-attachment-bytes)
- [Inbox API](#inbox-api)
- [Cloud mode](#cloud-mode)
- [Limits and carrier behavior](#limits-and-carrier-behavior)
- [Troubleshooting](#troubleshooting)

## Requirements

- Android 5.0 (API 21) or newer. Android 10+ is recommended.
- A SIM with an MMS-capable plan and a working MMS APN.
- `SEND_SMS`, `READ_PHONE_STATE`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` permissions
  granted. These are the same permissions the app already needs for SMS.
- To actively download inbound MMS, the app must be the **default SMS app**
  (see below).

## Setup

### Grant the default SMS app role

Sending MMS from a non-default app is allowed by the platform but many US
carriers (Verizon notably) silently drop those messages. The gateway should
be the default SMS app on any device that is expected to send or receive
real MMS traffic.

From inside the app:

1. Open the **Settings** tab.
2. Tap **Default SMS app**.
3. Accept the Android prompt to change the default.

The same preference shows whether the role is currently held. To hand the
role back to another app (Google Messages, Verizon Messages, etc.) use
Android's own Default apps screen.

Under the hood the app now declares the manifest receivers, activity, and
service required to qualify for the `android.app.role.SMS` role, so the
system can grant it without further configuration.

## Sending MMS

### API

The existing `POST /message` endpoint gains a new mutually-exclusive
content field, `mmsMessage`. Exactly one of `textMessage`, `dataMessage`,
`mmsMessage`, or the deprecated `message` string must be present.

```json
{
  "phoneNumbers": ["+15551234567"],
  "mmsMessage": {
    "subject": "Optional subject line",
    "text": "Optional text body shown alongside attachments",
    "attachments": [
      {
        "contentType": "image/jpeg",
        "name": "photo.jpg",
        "data": "<base64-encoded bytes>"
      },
      {
        "contentType": "audio/amr",
        "url": "https://media.example.com/clip.amr"
      }
    ]
  },
  "simNumber": 1,
  "withDeliveryReport": true,
  "priority": 100
}
```

Field reference:

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `phoneNumbers` | `string[]` | yes | Recipient MSISDNs. E.164 format strongly recommended. |
| `mmsMessage.subject` | `string` | no | Optional MMS subject. |
| `mmsMessage.text` | `string` | no | Optional text/plain body part. Can coexist with attachments. |
| `mmsMessage.attachments[]` | `Attachment[]` | conditional | At least one of `text` or `attachments` must be present. |
| `attachments[].contentType` | `string` | yes | e.g. `image/jpeg`, `image/png`, `audio/amr`, `video/mp4`. |
| `attachments[].name` | `string` | no | Suggested filename, forwarded as Content-Location + Content-Type `name`. |
| `attachments[].data` | `string` | one of | Base64-encoded bytes. |
| `attachments[].url` | `string` | one of | URL the device fetches over HTTP(S) at send time. |

Other top-level message fields (`simNumber`, `withDeliveryReport`,
`priority`, `ttl`/`validUntil`, `isEncrypted`, `id`) behave the same way
they do for SMS.

Response is the same `Accepted` shape as SMS, with the request echoed back
including the stored `mmsMessage` content:

```json
{
  "id": "PyDmBQZZXYmyxMwED8Fzy",
  "deviceId": "...",
  "state": "Pending",
  "isEncrypted": false,
  "mmsMessage": { "...": "..." },
  "recipients": [
    { "phoneNumber": "+15551234567", "state": "Pending", "error": null }
  ],
  "states": {}
}
```

### Attachment sources

Each attachment provides **exactly one** of `data` or `url`:
- `data`: inline base64 bytes. Larger payloads bloat the request body — prefer
  `url` for anything over a few hundred KB.
- `url`: the device performs an HTTP GET at send time and embeds the
  response body as the part. Useful for sending media already hosted on
  your infrastructure without base64 overhead.

If both are provided the request is rejected with `400 Bad Request`.

### Example: send an image

```sh
B64=$(base64 -w0 < photo.jpg)
curl -u "<user>:<pass>" -H 'Content-Type: application/json' \
  -d "{
    \"phoneNumbers\":[\"+15551234567\"],
    \"mmsMessage\":{
      \"text\":\"Say hi\",
      \"attachments\":[{
        \"contentType\":\"image/jpeg\",
        \"name\":\"photo.jpg\",
        \"data\":\"$B64\"
      }]
    }
  }" \
  http://<device_ip>:8080/message
```

### Example: send multiple attachments with subject

```sh
curl -u "<user>:<pass>" -H 'Content-Type: application/json' -d '{
  "phoneNumbers":["+15551234567"],
  "mmsMessage":{
    "subject":"Weekly report",
    "text":"See attached.",
    "attachments":[
      {"contentType":"image/png","url":"https://cdn.example.com/chart.png"},
      {"contentType":"application/pdf","url":"https://cdn.example.com/report.pdf"}
    ]
  }
}' http://<device_ip>:8080/message
```

### Example: pull attachments from a URL

```sh
curl -u "<user>:<pass>" -H 'Content-Type: application/json' -d '{
  "phoneNumbers":["+15551234567"],
  "mmsMessage":{
    "attachments":[{
      "contentType":"video/mp4",
      "name":"intro.mp4",
      "url":"https://cdn.example.com/intro.mp4"
    }]
  }
}' http://<device_ip>:8080/message
```

## Receiving MMS

### Flow

1. The carrier delivers a WAP-push notification over SMS. The app
   parses it and emits the `mms:received` webhook (metadata only — the
   message body is not yet downloaded).
2. If the app is the default SMS app, it calls
   `SmsManager.downloadMultimediaMessage()` to fetch the full PDU from the
   carrier's MMSC over the dedicated MMS APN.
3. Once the PDU is downloaded and parsed, the gateway:
   - Saves each non-SMIL attachment to private storage under
     `<appData>/files/mms-in/<messageId>/<partId>-<name>`.
   - Inserts an `incoming_messages` row (type `MMS_DOWNLOADED`).
   - Emits the `mms:downloaded` webhook carrying part metadata, inline
     base64 bytes, and a relative URL to fetch the same bytes from the
     gateway.

If the app is *not* the default SMS app, step 2 is handled by whichever
app is default; once that app writes the message into `content://mms`,
the gateway's content observer picks it up and step 3 runs as usual.

### Webhook: `mms:received`

Fired on the incoming WAP-push notification, before the content has been
downloaded.

```json
{
  "event": "mms:received",
  "payload": {
    "messageId": "hex-derived-id",
    "sender": "+15550009999",
    "recipient": "+15551234567",
    "simNumber": 1,
    "transactionId": "T0123abcd",
    "subject": "Photos",
    "size": 43210,
    "contentClass": "IMAGE_BASIC",
    "receivedAt": "2026-04-20T17:31:00.000+00:00"
  }
}
```

### Webhook: `mms:downloaded`

Fired once the content has been retrieved and stored. Attachments are
included inline as base64 (`data`) **and** as a URL path on the gateway's
local HTTP server (`url`) — consumers may use either.

```json
{
  "event": "mms:downloaded",
  "payload": {
    "messageId": "hex-derived-id",
    "sender": "+15550009999",
    "recipient": "+15551234567",
    "simNumber": 1,
    "subject": "Photos",
    "body": "Hi! Here's the photo.",
    "attachments": [
      {
        "partId": 3,
        "contentType": "image/jpeg",
        "name": "photo.jpg",
        "size": 38421,
        "data": "<base64>",
        "url": "/inbox/hex-derived-id/attachments/3"
      }
    ],
    "receivedAt": "2026-04-20T17:31:14.000+00:00"
  }
}
```

### Fetching attachment bytes

To avoid base64-inflated webhook payloads you can fetch attachments on
demand. Authenticate with the same credentials used for the rest of the
API.

```sh
curl -u "<user>:<pass>" \
  http://<device_ip>:8080/inbox/<messageId>/attachments/<partId> \
  -o photo.jpg
```

`Content-Type` reflects the attachment's MIME type; `Content-Disposition`
carries the original filename when one was present.

Required auth scope: `inbox:read`.

## Inbox API

- `GET /inbox?type=MMS_DOWNLOADED` — list received MMS metadata.
- `GET /inbox/{messageId}` — full message detail, including a list of
  attachment references:

```json
{
  "id": "hex-derived-id",
  "type": "MMS_DOWNLOADED",
  "sender": "+15550009999",
  "recipient": "+15551234567",
  "simNumber": 1,
  "contentPreview": "Hi! Here's the photo.",
  "createdAt": "2026-04-20T17:31:14.000+00:00",
  "attachments": [
    {
      "partId": 3,
      "name": "photo.jpg",
      "size": 38421,
      "contentType": "image/jpeg",
      "url": "/inbox/hex-derived-id/attachments/3"
    }
  ]
}
```

- `GET /inbox/{messageId}/attachments/{partId}` — raw attachment bytes.

All three are available in both Local and Cloud modes.

## Cloud mode

The cloud pull protocol (`GET /message`) accepts the same `mmsMessage`
object inside a `Message` payload. When the device pulls a cloud-queued
MMS, it composes the PDU locally and sends it via the same code path used
for local-server requests. State transitions (Processed / Sent / Failed)
are reported back to the cloud identically to SMS.

## Limits and carrier behavior

- **PDU format.** The gateway composes PDUs with AOSP's reference
  `PduComposer` (ported from klinker-apps's `pdu_alt`, Apache-2.0), so
  the output matches Google Messages' format and is accepted by every
  MMSC we've tested.
- **Body type.** `multipart/related` is used automatically; Content-Type
  parameters `type` and `start` reference the first text part so recipient
  clients render text + attachments correctly even without SMIL.
- **Size.** Carriers impose per-message PDU limits, typically 300 KB to
  1.2 MB. Verizon accepts up to ~1.2 MB in practice; T-Mobile and AT&T
  are similar. Large attachments (video) should be downscaled before
  sending.
- **From.** The gateway uses the WSP `insert-address-token` so the MMSC
  fills in the sender's MSISDN — the approach AOSP and Google Messages
  both use. If your carrier rejects insert-address-token, pass an
  explicit From via `SubscriptionsHelper.getPhoneNumber()` and the sender
  will fall back to that MSISDN.
- **Self-send.** Some carriers (Verizon included) silently drop MMS
  addressed to the sender's own MSISDN. Use a second number for
  round-trip testing.
- **Recipient parser.** Once a recipient's messaging app rejects a
  malformed PDU, some MMSCs enter a backoff window (5–60 min) before
  re-pushing. If you're iterating on PDU issues, test against multiple
  recipient numbers/carriers to avoid getting stuck on one backoff.

## Troubleshooting

- **Send reports `Sent` but recipient never gets the message.** The MMSC
  accepted our HTTP POST (that's what `Sent` means) but may have dropped
  the message later. Confirm the app is the default SMS app, the
  recipient isn't the sender's own number, and the recipient hasn't
  blocked the sender.
- **`MMS_ERROR_IO_ERROR` on send.** The MMS APN was not available or the
  MMSC returned a non-200. Check mobile data is up and that the active
  APN has type `mms`. The carrier's own SMS app will surface APN
  misconfiguration errors that our app cannot — use it as a sanity
  check first.
- **`MMS_ERROR_INVALID_APN` / `MMS_ERROR_NO_DATA_NETWORK`.** No
  MMS-typed APN configured. Add one through Android's Mobile network →
  Access point names settings, or install the carrier's config profile.
- **Inbound MMS shows as `mms:received` but never transitions to
  `mms:downloaded`.** The app likely isn't the default SMS app, or the
  device dropped the MMS APN while downloading. Check the app's log
  screen for `MMS download failed` entries.
- **Inspecting the outgoing PDU for debugging.** With USB debugging
  enabled:
  ```sh
  adb shell run-as me.capcom.smsgateway ls files/mms-out
  adb shell run-as me.capcom.smsgateway cat files/mms-out/<id>.pdu > out.pdu
  xxd out.pdu | head
  ```
  PDU files are deleted automatically after the `mms:sent` state fires;
  to retain them across sends, flip the cleanup flag in
  `MessagesService.processStateIntent`.
