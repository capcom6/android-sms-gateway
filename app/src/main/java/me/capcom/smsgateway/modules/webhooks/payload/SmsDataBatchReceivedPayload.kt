package me.capcom.smsgateway.modules.webhooks.payload

class SmsDataBatchReceivedPayload(
    messages: List<SmsEventPayload.SmsDataReceived>
) : BatchEventPayload<SmsEventPayload.SmsDataReceived>(messages)
