package me.capcom.smsgateway.modules.encryption.providers

import java.security.SecureRandom
import me.capcom.smsgateway.modules.encryption.EncryptionSettings

class LegacyPassphraseEncryptionProvider(
    settings: EncryptionSettings,
) : BasePassphraseEncryptionProvider(settings) {

    override fun generateIvAndSalt(): Pair<ByteArray, ByteArray> {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt to salt
    }

    override fun formatOutput(ivEnc: String, saltEnc: String, ctEnc: String): String {
        return paramsString() + "$" + saltEnc + "$" + ctEnc
    }

    override fun parseChunks(chunks: List<String>): Parsed {
        if (chunks.size < 3) {
            throw RuntimeException("Invalid passphrase encrypted data format")
        }

        val salt = decode(chunks[1])
        return Parsed(
            params = parseParams(chunks[0]),
            iv = salt,
            salt = salt,
            ct = chunks[2],
        )
    }
}
