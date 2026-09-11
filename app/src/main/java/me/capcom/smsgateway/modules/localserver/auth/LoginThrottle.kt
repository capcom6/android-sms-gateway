package me.capcom.smsgateway.modules.localserver.auth

import android.util.Log
import java.security.MessageDigest
import java.util.ArrayDeque

/**
 * Constant-time credential comparison and a failed-attempt lockout for the
 * local HTTP server.
 *
 * Before this, [WebService][me.capcom.smsgateway.modules.localserver.WebService]
 * compared the password with `==`. Two problems:
 *
 *  1. [String.equals] returns on the first differing byte, so the comparison
 *     leaks the length of the matching prefix through timing.
 *  2. Nothing anywhere in the app counted failed attempts. The generated
 *     default password is 8 characters, the server speaks plain HTTP on every
 *     interface, and that password grants every scope — so anyone on the same
 *     network could guess at the full speed of the device, indefinitely, and
 *     leave no trace of having tried.
 *
 * (2) is the one that matters in practice; (1) is fixed because it is free.
 *
 * The lockout is deliberately global rather than per-client: the remote
 * address of a LAN peer is trivially spoofed or rotated, so per-address
 * counting would provide false comfort. A legitimate operator who mistypes
 * their password a few times waits [LOCKOUT_MILLIS]; an attacker is reduced
 * from unlimited guesses to [MAX_ATTEMPTS] per lockout window.
 */
object LoginThrottle {

    const val MAX_ATTEMPTS = 10
    const val ATTEMPT_WINDOW_MILLIS = 5 * 60 * 1000L
    const val LOCKOUT_MILLIS = 5 * 60 * 1000L

    private val attempts = ArrayDeque<Long>()
    private var lockedUntil = 0L
    private val lock = Any()

    /** True when authentication attempts are currently refused. */
    fun isLockedOut(now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        now < lockedUntil
    }

    fun recordFailure(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        while (attempts.isNotEmpty() && now - attempts.peekFirst() > ATTEMPT_WINDOW_MILLIS) {
            attempts.pollFirst()
        }
        attempts.addLast(now)
        if (attempts.size >= MAX_ATTEMPTS) {
            lockedUntil = now + LOCKOUT_MILLIS
            attempts.clear()
            Log.w(
                "LoginThrottle",
                "Local API authentication locked out for ${LOCKOUT_MILLIS / 1000}s " +
                        "after $MAX_ATTEMPTS failed attempts"
            )
        }
    }

    fun recordSuccess() = synchronized(lock) {
        attempts.clear()
        lockedUntil = 0L
    }

    /** Test hook. */
    fun reset() = synchronized(lock) {
        attempts.clear()
        lockedUntil = 0L
    }

    /**
     * Compare two secrets without leaking the matching prefix length.
     *
     * [MessageDigest.isEqual] is documented as constant-time on Android for
     * equal-length inputs. Length itself is not hidden, which is acceptable:
     * the password length is not the secret.
     */
    fun secretsMatch(expected: String?, presented: String?): Boolean {
        if (expected.isNullOrEmpty() || presented == null) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            presented.toByteArray(Charsets.UTF_8)
        )
    }
}
