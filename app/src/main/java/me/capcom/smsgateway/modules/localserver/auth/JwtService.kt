package me.capcom.smsgateway.modules.localserver.auth

import android.content.Context
import android.util.Log
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import me.capcom.smsgateway.data.dao.RefreshRotationResult
import me.capcom.smsgateway.data.dao.TokensDao
import me.capcom.smsgateway.data.entities.Token
import me.capcom.smsgateway.data.entities.TokenUse
import me.capcom.smsgateway.modules.localserver.LocalServerSettings
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import java.util.Date
import kotlin.math.min

data class GeneratedTokenInfo(
    val id: String,
    val token: String,
    val expiresAt: Date,
)

data class GeneratedTokenPair(
    val access: GeneratedTokenInfo,
    val refresh: GeneratedTokenInfo,
)

sealed class RefreshTokenException(message: String) : IllegalArgumentException(message)
class InvalidRefreshTokenException(message: String) : RefreshTokenException(message)
class RefreshTokenReplayException(message: String) : RefreshTokenException(message)

class JwtService(
    context: Context,
    private val settings: LocalServerSettings,
    private val tokensDao: TokensDao,
    private val logsService: LogsService,
) {
    private val issuer = context.packageName

    private val algorithm: Algorithm
        get() = Algorithm.HMAC256(settings.jwtSecret)

    suspend fun generateTokenPair(scopes: List<String>, ttlSeconds: Long?): GeneratedTokenPair {
        val effectiveScopes = normalizeAndValidateScopes(scopes)
        val accessTTL = validateAccessTTL(ttlSeconds ?: settings.jwtTtlSeconds)

        val pair = buildTokenPair(
            requestedScopes = effectiveScopes,
            accessTTL = accessTTL,
        )

        tokensDao.insertPair(pair.accessModel, pair.refreshModel)
        cleanupExpiredTokens()

        return pair.generated
    }

    suspend fun refreshTokenPair(
        refreshTokenID: String,
        originalScopes: List<String>
    ): GeneratedTokenPair {
        val validScopes = try {
            normalizeAndValidateScopes(originalScopes)
        } catch (e: IllegalArgumentException) {
            logsService.insert(
                LogEntry.Priority.WARN,
                TAG,
                "Refresh rejected: invalid original scopes",
                mapOf(
                    "originalScopes" to originalScopes,
                    "exception" to e.message,
                ),
            )
            throw InvalidRefreshTokenException("invalid original scopes")
        }

        val pair = buildTokenPair(
            requestedScopes = validScopes,
            accessTTL = settings.jwtTtlSeconds,
        )

        when (
            tokensDao.rotateRefreshToken(
                currentRefreshJti = refreshTokenID,
                newAccess = pair.accessModel,
                newRefresh = pair.refreshModel,
            )
        ) {
            RefreshRotationResult.Rotated -> {
                logsService.insert(
                    LogEntry.Priority.INFO,
                    TAG,
                    "Refresh token rotated",
                    mapOf(
                        "refreshTokenID" to refreshTokenID,
                        "originalScopes" to originalScopes,
                    ),
                )
                cleanupExpiredTokens()
                return pair.generated
            }

            RefreshRotationResult.AlreadyRevoked -> {
                logsService.insert(
                    LogEntry.Priority.WARN,
                    TAG,
                    "Refresh token replay detected",
                    mapOf(
                        "refreshTokenID" to refreshTokenID,
                        "originalScopes" to originalScopes,
                    ),
                )
                throw RefreshTokenReplayException("refresh token replay detected")
            }

            RefreshRotationResult.NotFound -> {
                logsService.insert(
                    LogEntry.Priority.WARN,
                    TAG,
                    "Refresh token not found",
                    mapOf(
                        "refreshTokenID" to refreshTokenID,
                        "originalScopes" to originalScopes,
                    ),
                )
                throw InvalidRefreshTokenException("refresh token not found")
            }

            RefreshRotationResult.WrongTokenUse -> {
                logsService.insert(
                    LogEntry.Priority.WARN,
                    TAG,
                    "Invalid refresh token use",
                    mapOf(
                        "refreshTokenID" to refreshTokenID,
                        "originalScopes" to originalScopes,
                    ),
                )
                throw InvalidRefreshTokenException("invalid refresh token use")
            }
        }
    }

    fun verifier() = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    suspend fun revokeToken(jti: String) {
        tokensDao.revokeWithChildren(jti)
    }

    suspend fun isTokenRevoked(jti: String): Boolean {
        return tokensDao.isRevoked(jti)
    }

    private suspend fun cleanupExpiredTokens() {
        tokensDao.cleanup()
    }

    private fun normalizeAndValidateScopes(scopes: List<String>): List<String> {
        val effectiveScopes = scopes
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        require(effectiveScopes.isNotEmpty()) { "scopes must not be empty" }
        require(AuthScopes.firstUnsupported(effectiveScopes) == null) { "unsupported scope provided" }

        return effectiveScopes
    }

    private fun validateAccessTTL(ttl: Long): Long {
        require(ttl > 0) { "ttl must be > 0" }
        require(ttl <= LocalServerSettings.MAX_JWT_TTL_SECONDS) { "ttl exceeds maximum allowed value" }
        return ttl
    }

    private fun buildTokenPair(requestedScopes: List<String>, accessTTL: Long): BuiltTokenPair {
        val now = Date()
        val accessExpiresAt = Date(now.time + accessTTL * 1000L)
        val refreshTTL = calculateRefreshTTL(accessTTL)
        val refreshExpiresAt = Date(now.time + refreshTTL * 1000L)

        val accessTokenID = NanoIdUtils.randomNanoId()
        val refreshTokenID = NanoIdUtils.randomNanoId()

        val accessToken = JWT.create()
            .withJWTId(accessTokenID)
            .withIssuer(issuer)
            .withIssuedAt(now)
            .withExpiresAt(accessExpiresAt)
            .withClaim("scopes", requestedScopes)
            .withClaim("token_use", TokenUse.Access.value)
            .sign(algorithm)

        val refreshToken = JWT.create()
            .withJWTId(refreshTokenID)
            .withIssuer(issuer)
            .withIssuedAt(now)
            .withExpiresAt(refreshExpiresAt)
            .withClaim("scopes", listOf(AuthScopes.TokensRefresh.value))
            .withClaim("original_scopes", requestedScopes)
            .withClaim("token_use", TokenUse.Refresh.value)
            .sign(algorithm)

        return BuiltTokenPair(
            generated = GeneratedTokenPair(
                access = GeneratedTokenInfo(
                    id = accessTokenID,
                    token = accessToken,
                    expiresAt = accessExpiresAt,
                ),
                refresh = GeneratedTokenInfo(
                    id = refreshTokenID,
                    token = refreshToken,
                    expiresAt = refreshExpiresAt,
                ),
            ),
            accessModel = Token(
                id = accessTokenID,
                expiresAt = accessExpiresAt.time,
                tokenUse = TokenUse.Access.value,
            ),
            refreshModel = Token(
                id = refreshTokenID,
                expiresAt = refreshExpiresAt.time,
                tokenUse = TokenUse.Refresh.value,
                parentJti = accessTokenID,
            )
        )
    }

    private data class BuiltTokenPair(
        val generated: GeneratedTokenPair,
        val accessModel: Token,
        val refreshModel: Token,
    )

    companion object {
        private const val TAG = "JwtService"

        private fun calculateRefreshTTL(accessTTL: Long): Long {
            if (accessTTL >= LocalServerSettings.MAX_JWT_TTL_SECONDS) {
                return accessTTL
            }
            return maxOf(
                accessTTL + 1,
                min(LocalServerSettings.MAX_JWT_TTL_SECONDS, accessTTL * 7),
            )
        }
    }
}
