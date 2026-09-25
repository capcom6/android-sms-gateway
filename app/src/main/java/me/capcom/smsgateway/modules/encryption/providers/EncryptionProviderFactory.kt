package me.capcom.smsgateway.modules.encryption.providers

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.ConcurrentHashMap

object EncryptionProviderFactory : KoinComponent {
    private val providers = ConcurrentHashMap<String, EncryptionProvider>()

    fun create(algorithm: String?): EncryptionProvider {
        val resolved = algorithm ?: DEFAULT_ALGORITHM

        return providers[resolved]
            ?: (when (resolved) {
                PASSPHRASE_FORMAT -> LegacyPassphraseEncryptionProvider(get())
                PASSPHRASE_FORMAT_V2 -> HardenedPassphraseEncryptionProvider(get())
                RSA_FORMAT -> RSAEncryptionProvider(get())
                else -> throw RuntimeException("Method is not supported")
            }.also { providers[resolved] = it })
    }

    private const val PASSPHRASE_FORMAT = "aes-256-cbc/pbkdf2-sha1"
    private const val PASSPHRASE_FORMAT_V2 = "aes-256-cbc/pbkdf2-sha1/v2"
    private const val RSA_FORMAT = "rsa-oaep-aes-256-gcm"
    const val DEFAULT_ALGORITHM = PASSPHRASE_FORMAT_V2
}