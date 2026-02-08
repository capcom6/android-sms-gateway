package me.stappmus.messagegateway.ui.styles

import android.graphics.Color

val me.stappmus.messagegateway.domain.ProcessingState.color: Int
    get() = when (this) {
        me.stappmus.messagegateway.domain.ProcessingState.Pending -> Color.parseColor("#FFBB86FC")
        me.stappmus.messagegateway.domain.ProcessingState.Processed -> Color.parseColor("#FF6200EE")
        me.stappmus.messagegateway.domain.ProcessingState.Sent -> Color.parseColor("#FF3700B3")
        me.stappmus.messagegateway.domain.ProcessingState.Delivered -> Color.parseColor("#FF03DAC5")
        me.stappmus.messagegateway.domain.ProcessingState.Failed -> Color.parseColor("#FF018786")
    }