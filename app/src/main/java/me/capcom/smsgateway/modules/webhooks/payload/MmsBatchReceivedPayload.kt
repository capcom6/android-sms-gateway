package me.capcom.smsgateway.modules.webhooks.payload

class MmsBatchReceivedPayload(
    messages: List<MmsReceivedPayload>
) : BatchEventPayload<MmsReceivedPayload>(messages)
