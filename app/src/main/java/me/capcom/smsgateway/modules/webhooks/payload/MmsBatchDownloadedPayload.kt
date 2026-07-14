package me.capcom.smsgateway.modules.webhooks.payload

class MmsBatchDownloadedPayload(
    messages: List<MmsDownloadedPayload>
) : BatchEventPayload<MmsDownloadedPayload>(messages)
