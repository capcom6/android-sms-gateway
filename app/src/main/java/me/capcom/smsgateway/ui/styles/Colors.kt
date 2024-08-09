package me.capcom.smsgateway.ui.styles

import android.graphics.Color

val me.capcom.smsgateway.domain.ProcessingState.color: Int
    get() = when (this) {
        me.capcom.smsgateway.domain.ProcessingState.Pending -> Color.parseColor("#FFBB86FC")
        me.capcom.smsgateway.domain.ProcessingState.Processed -> Color.parseColor("#FF6200EE")
        me.capcom.smsgateway.domain.ProcessingState.Sent -> Color.parseColor("#FF3700B3")
        me.capcom.smsgateway.domain.ProcessingState.Delivered -> Color.parseColor("#FF03DAC5")
        me.capcom.smsgateway.domain.ProcessingState.Failed -> Color.parseColor("#FF018786")
    }