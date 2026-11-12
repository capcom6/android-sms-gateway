package me.capcom.smsgateway.modules.messages.providers

import android.content.Context
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.messages.MessagesSettings
import me.capcom.smsgateway.modules.messages.data.SendParams

class SimSelector(
    private val context: Context,
    private val settings: MessagesSettings,
    private val slotsProvider: () -> Set<Int>? = {
        SubscriptionsHelper.selectAvailableSimSlots(context)
    },
) {
    fun select(id: Long, params: SendParams): Int? {
        if (params.simNumber != null) {
            return params.simNumber - 1
        }

        val simSlots = slotsProvider()?.sorted() ?: return null
        if (simSlots.isEmpty()) {
            throw RuntimeException("No SIMs found")
        }

        return when (settings.simSelectionMode) {
            MessagesSettings.SimSelectionMode.OSDefault -> null
            MessagesSettings.SimSelectionMode.RoundRobin -> simSlots[(id % simSlots.size).toInt()]
            MessagesSettings.SimSelectionMode.Random -> simSlots.random()
        }
    }
}
