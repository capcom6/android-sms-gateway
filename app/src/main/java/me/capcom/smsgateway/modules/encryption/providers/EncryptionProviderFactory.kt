package me.capcom.smsgateway.modules.encryption.providers

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.ConcurrentHashMap

object EncryptionProviderFactory : KoinComponent {
    private val providers = ConcurrentHashMap<String, EncryptionProvider>()

    fun create(algorithm: String): EncryptionProvider {
        return providers[algorithm]
            ?: (when (algorithm) {
                PASSPHRASE_FORMAT -> PassphraseEncryptionProvider(get())
                RSA_FORMAT -> RSAEncryptionProvider(get())
                else -> throw RuntimeException("Method is not supported")
            }.also { providers[algorithm] = it })
    }

    private const val PASSPHRASE_FORMAT = "aes-256-cbc/pbkdf2-sha1"
    private const val RSA_FORMAT = "rsa-oaep-aes-256-gcm"
}