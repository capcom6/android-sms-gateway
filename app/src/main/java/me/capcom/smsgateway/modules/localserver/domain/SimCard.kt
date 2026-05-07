package me.capcom.smsgateway.modules.localserver.domain

/**
 * Represents a SIM card in the device.
 *
 * @property slotIndex 0-based physical SIM slot index (0, 1, 2, etc.)
 * @property simNumber 1-based SIM slot number (1, 2, or 3)
 * @property phoneNumber Phone number associated with the SIM (null if READ_PHONE_STATE not granted)
 * @property carrierName Carrier/network operator name (null if unavailable)
 * @property iccid Integrated Circuit Card Identifier (null if unavailable)
 */
data class SimCard(
    val slotIndex: Int,
    val simNumber: Int,
    val phoneNumber: String?,
    val carrierName: String?,
    val iccid: String?,
)
