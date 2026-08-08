package me.capcom.smsgateway.modules.device.keys

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.util.Date
import javax.security.auth.x500.X500Principal

data class KeyPairResult(
    val keyPair: KeyPair,
    val publicKeyBase64: String,
)

/**
 * Keystore-only E2E key management on all API levels. AndroidKeyStore has
 * existed since API 18: API 21-22 keys are generated with
 * [KeyPairGeneratorSpec], API 23+ with [KeyGenParameterSpec]. Private keys
 * never leave AndroidKeyStore and no software keys exist anywhere.
 */
class KeyStore(
    private val context: Context,
) {

    fun generateKeyPair(alias: String): KeyPairResult {
        val keyPair = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            generateKeyPairInKeystorePreM(alias)
        } else {
            generateKeyPairInKeystore(alias)
        }
        return KeyPairResult(
            keyPair,
            encodePublicKey(keyPair),
        )
    }

    fun getPrivateKey(alias: String): PrivateKey? {
        return getKeyFromKeystore(alias)
    }

    fun delete(alias: String) {
        deleteKeyFromKeystore(alias)
    }

    private fun encodePublicKey(keyPair: KeyPair): String {
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    // -------------------------------------------------------------------------
    // API 23+ : AndroidKeyStore
    // -------------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.M)
    private fun generateKeyPairInKeystore(alias: String): KeyPair {
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT,
        )
            .setKeySize(2048)
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        kpg.initialize(spec)
        return kpg.generateKeyPair()
    }

    private fun getKeyFromKeystore(alias: String): PrivateKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        return ks.getKey(alias, null) as? PrivateKey
    }

    private fun deleteKeyFromKeystore(alias: String) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        ks.deleteEntry(alias)
    }

    // -------------------------------------------------------------------------
    // API 21-22 : AndroidKeyStore via KeyPairGeneratorSpec
    // -------------------------------------------------------------------------

    @Suppress("DEPRECATION") // KeyPairGeneratorSpec is the pre-API-23 spec
    private fun generateKeyPairInKeystorePreM(alias: String): KeyPair {
        val kpg = KeyPairGenerator.getInstance(RSA_ALGORITHM, ANDROID_KEYSTORE)
        val spec = KeyPairGeneratorSpec.Builder(context)
            .setAlias(alias)
            .setKeyType(RSA_ALGORITHM)
            .setKeySize(2048)
            // No setEncryptionRequired() call: pre-M API is parameterless and
            // sets the flag to true (encryption at rest required, which blocks
            // use before device unlock). Default (omitted) = not required,
            // matching the intended setEncryptionRequired(false).
            .setSubject(X500Principal("CN=$alias"))
            .setStartDate(Date())
            .setEndDate(Date(System.currentTimeMillis() + 25L * 365 * 24 * 60 * 60 * 1000))
            .build()
        kpg.initialize(spec)
        return kpg.generateKeyPair()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val RSA_ALGORITHM = "RSA"
    }
}
