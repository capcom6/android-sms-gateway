package me.capcom.smsgateway.modules.localserver.auth

import android.content.Context
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.data.dao.RevokedTokensDao
import me.capcom.smsgateway.data.entities.RevokedToken
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import java.util.Date

data class GeneratedToken(
    val id: String,
    val accessToken: String,
    val expiresAt: Date,
)

class JwtService(
    context: Context,
    private val settings: LocalServerSettings,
    private val revokedTokensDao: RevokedTokensDao,
) {
    private val issuer = context.packageName

    private val algorithm: Algorithm
        get() = Algorithm.HMAC256(settings.jwtSecret)

    fun generateToken(scopes: List<String>?, ttlSeconds: Long?): GeneratedToken {
        val effectiveScopes = (scopes ?: emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        require(effectiveScopes.isNotEmpty()) { "scopes must not be empty" }
        require(AuthScopes.firstUnsupported(effectiveScopes) == null) { "unsupported scope provided" }

        val now = Date()
        val ttl = ttlSeconds ?: settings.jwtTtlSeconds
        require(ttl > 0) { "ttl must be > 0" }
        require(ttl <= MAX_TTL_SECONDS) { "ttl exceeds maximum allowed value" }

        val ttlMillis = ttl * 1000L

        val tokenId = NanoIdUtils.randomNanoId()
        val expiresAt = Date(now.time + ttlMillis)

        val token = JWT.create()
            .withJWTId(tokenId)
            .withIssuer(issuer)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .withClaim("scopes", effectiveScopes)
            .sign(algorithm)

        return GeneratedToken(tokenId, token, expiresAt)
    }

    fun verifier() = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    suspend fun revokeToken(jti: String) {
        revokedTokensDao.upsert(RevokedToken(jti))
    }

    suspend fun isTokenRevoked(jti: String): Boolean {
        return revokedTokensDao.exists(jti)
    }

    companion object {
        private const val MAX_TTL_SECONDS: Long = 365L * 24L * 60L * 60L
    }
}
