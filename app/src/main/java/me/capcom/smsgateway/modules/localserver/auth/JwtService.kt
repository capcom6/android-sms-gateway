package me.capcom.smsgateway.modules.localserver.auth

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
    private val settings: LocalServerSettings,
    private val revokedTokensDao: RevokedTokensDao,
) {
    private val algorithm: Algorithm
        get() = Algorithm.HMAC256(settings.jwtSecret)

    fun generateToken(scopes: List<String>?, ttlSeconds: Long?): GeneratedToken {
        val effectiveScopes = (scopes ?: emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty {
                AuthScopes.parseCsv(settings.jwtDefaultScopes)
            }

        require(effectiveScopes.isNotEmpty()) { "scopes must not be empty" }
        require(AuthScopes.firstUnsupported(effectiveScopes) == null) { "unsupported scope provided" }

        val now = Date()
        val ttl = ttlSeconds ?: settings.jwtTtlSeconds
        require(ttl > 0) { "ttl must be > 0" }

        val tokenId = NanoIdUtils.randomNanoId()
        val expiresAt = Date(now.time + (ttl * 1000))

        val token = JWT.create()
            .withJWTId(tokenId)
            .withIssuer(settings.jwtIssuer)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .withClaim("scopes", effectiveScopes)
            .sign(algorithm)

        return GeneratedToken(tokenId, token, expiresAt)
    }

    fun verifier() = JWT.require(algorithm)
        .withIssuer(settings.jwtIssuer)
        .build()

    fun revokeToken(jti: String) {
        revokedTokensDao.upsert(RevokedToken(jti))
    }

    fun isTokenRevoked(jti: String): Boolean {
        return revokedTokensDao.exists(jti)
    }
}
