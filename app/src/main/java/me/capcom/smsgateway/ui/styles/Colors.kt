package me.capcom.smsgateway.ui.styles

import android.graphics.Color
import me.capcom.smsgateway.data.entities.Message

val Message.State.color: Int
    get() = when (this) {
        Message.State.Pending -> Color.parseColor("#FFBB86FC")
        Message.State.Processed -> Color.parseColor("#FF6200EE")
        Message.State.Sent -> Color.parseColor("#FF3700B3")
        Message.State.Delivered -> Color.parseColor("#FF03DAC5")
        Message.State.Failed -> Color.parseColor("#FF018786")
    }