package me.capcom.smsgateway.modules.encryption.providers

import java.security.SecureRandom
import me.capcom.smsgateway.modules.encryption.EncryptionSettings

class HardenedPassphraseEncryptionProvider(
    settings: EncryptionSettings,
) : BasePassphraseEncryptionProvider(settings) {

    override fun generateIvAndSalt(): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(SALT_LENGTH)
        val salt = ByteArray(SALT_LENGTH)
        val random = SecureRandom()
        random.nextBytes(iv)
        random.nextBytes(salt)
        return iv to salt
    }

    override fun formatOutput(ivEnc: String, saltEnc: String, ctEnc: String): String {
        return paramsString() + "$" + ivEnc + "$" + saltEnc + "$" + ctEnc
    }

    override fun parseChunks(chunks: List<String>): Parsed {
        if (chunks.size < 4) {
            throw RuntimeException("Invalid hardened encrypted data format")
        }

        return Parsed(
            params = parseParams(chunks[0]),
            iv = decode(chunks[1]),
            salt = decode(chunks[2]),
            ct = chunks[3],
        )
    }
}
