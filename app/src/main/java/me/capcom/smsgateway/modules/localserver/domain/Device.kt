package me.capcom.smsgateway.modules.localserver.domain

import java.util.Date

/**
 * Represents a device known to the local server.
 *
 * Wire contract, kept in parity with the server's `smsgateway.Device`
 * (`client-go/smsgateway/domain_devices.go`): [publicKey] and [keyVersion] are
 * nullable and are omitted from the response when null, mirroring the Go
 * `omitempty` pointers, and are always emitted together.
 *
 * JSON key order is deliberately not part of this contract: objects are
 * unordered, and the serializer emits fields in the JVM's `getDeclaredFields()`
 * order, which the platform does not guarantee. The two key fields are declared
 * last only because they were added last.
 *
 * `deletedAt` is intentionally absent: the server omits it for every listed
 * device, so including it would not change the response.
 */
data class Device(
    val id: String,
    val name: String,
    val createdAt: Date,
    val updatedAt: Date,
    val lastSeen: Date,
    val simCards: List<SimCard> = emptyList(),
    val publicKey: String? = null,
    val keyVersion: Int? = null,
)
