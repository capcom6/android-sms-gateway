package me.capcom.smsgateway.modules.encryption.providers

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.ConcurrentHashMap

object EncryptionProviderFactory : KoinComponent {
    private val providers = ConcurrentHashMap<String, EncryptionProvider>()

    fun create(algorithm: String?): EncryptionProvider {
        val algorithm = algorithm ?: DEFAULT_ALGORITHM

        return providers[algorithm]
            ?: (when (algorithm) {
                PASSPHRASE_FORMAT -> PassphraseEncryptionProvider(get())
                else -> throw RuntimeException("Method is not supported")
            }.also { providers[algorithm] = it })
    }

    private const val PASSPHRASE_FORMAT = "aes-256-cbc/pbkdf2-sha1"
    const val DEFAULT_ALGORITHM = PASSPHRASE_FORMAT
}