package me.capcom.smsgateway.modules.webhooks

import java.net.URI

/**
 * Which webhook destinations the gateway refuses to call.
 *
 * Webhook deliveries are issued from the phone's own network position, and
 * URL validation previously checked only the scheme. An `https://` URL
 * pointing at a private range therefore turned the gateway into a POST proxy
 * into whatever LAN it happened to be on, reachable by anyone holding the
 * `webhooks:write` scope or by the cloud server. Per-attempt status codes are
 * written to the log table, so it also worked as a blind scanner.
 *
 * Deliberately a literal check against the host as written, not a DNS
 * resolution: resolving here and connecting later is a time-of-check problem,
 * and doing name resolution during validation is its own denial-of-service
 * surface. This blocks the direct case; a determined attacker with a domain
 * resolving to a private address is out of scope for this layer and needs
 * the check repeated at connect time.
 *
 * The explicit `http://127.0.0.1` allowance in
 * [WebHooksService.replace][WebHooksService] is intentional and is applied
 * before this check, so self-hosted loopback receivers keep working.
 */
object WebHookUrlPolicy {

    fun isForbiddenHost(url: String): Boolean {
        val host = try {
            URI(url).host?.lowercase()
        } catch (_: Exception) {
            return true // unparseable: refuse rather than guess
        } ?: return true

        val bare = host.trim('[', ']')

        if (bare == "localhost" || bare.endsWith(".localhost")) return true
        if (bare.endsWith(".local") || bare.endsWith(".internal")) return true

        if (isIpv4Literal(bare)) return isForbiddenIpv4(bare)
        if (bare.contains(':')) return isForbiddenIpv6(bare)

        return false
    }

    private fun isIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { p ->
            p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() <= 255
        }
    }

    private fun isForbiddenIpv4(host: String): Boolean {
        val o = host.split('.').map { it.toInt() }
        return when {
            o[0] == 0 -> true                                  // 0.0.0.0/8
            o[0] == 10 -> true                                 // private
            o[0] == 127 -> true                                // loopback
            o[0] == 169 && o[1] == 254 -> true                 // link-local, incl. 169.254.169.254
            o[0] == 172 && o[1] in 16..31 -> true              // private
            o[0] == 192 && o[1] == 168 -> true                 // private
            o[0] == 192 && o[1] == 0 && o[2] == 0 -> true      // IETF protocol assignments
            o[0] == 100 && o[1] in 64..127 -> true             // carrier-grade NAT
            o[0] >= 224 -> true                                // multicast and reserved
            else -> false
        }
    }

    private fun isForbiddenIpv6(host: String): Boolean {
        val h = host.substringBefore('%')          // strip any zone index
        if (h == "::" || h == "::1") return true   // unspecified, loopback
        if (h.startsWith("fe80") || h.startsWith("fec0")) return true  // link/site-local
        if (h.startsWith("fc") || h.startsWith("fd")) return true      // unique local
        if (h.startsWith("ff")) return true                            // multicast
        // IPv4-mapped, e.g. ::ffff:127.0.0.1
        val mapped = h.substringAfterLast(':')
        if (isIpv4Literal(mapped)) return isForbiddenIpv4(mapped)
        return false
    }
}
