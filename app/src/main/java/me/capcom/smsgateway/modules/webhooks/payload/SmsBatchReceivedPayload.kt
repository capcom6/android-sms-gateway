package me.capcom.smsgateway.modules.webhooks.payload

class SmsBatchReceivedPayload(
    messages: List<SmsEventPayload.SmsReceived>
) : BatchEventPayload<SmsEventPayload.SmsReceived>(messages)
