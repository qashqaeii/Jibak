package ir.jibik.app

import ir.jibik.core.finance.Account
import ir.jibik.core.finance.LedgerRecord
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected export of the same CSV as LedgerCsvExport.
 *
 * Binary format V1: ASCII "JIBIKCSV1" (9 bytes) | random salt (16 bytes) |
 * random GCM nonce (12 bytes) | AES-256-GCM ciphertext + 16-byte tag.
 * PBKDF2-HMAC-SHA256 with 210,000 iterations derives the key. All header
 * bytes are authenticated as GCM additional data.
 *
 * This is a protected CSV export, NOT a restorable backup or Room encryption.
 * No passphrase, plaintext CSV or derived key is stored on the device.
 */
internal object EncryptedLedgerCsvExport {
    internal val MAGIC = "JIBIKCSV1".toByteArray(StandardCharsets.US_ASCII)
    internal const val SALT_BYTES = 16
    internal const val NONCE_BYTES = 12
    internal const val ITERATIONS = 210_000
    internal const val MIN_PASSWORD_CHARS = 12

    fun write(
        stream: OutputStream,
        passphrase: CharArray,
        accounts: List<Account>,
        records: List<LedgerRecord>,
    ) {
        require(passphrase.size in MIN_PASSWORD_CHARS..256) {
            "A passphrase of 12 to 256 characters is required"
        }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = MAGIC + salt + nonce
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            // Do not write any plaintext bytes before encryption is active.
            stream.write(header)
            CipherOutputStream(stream, cipher).use { encrypted ->
                LedgerCsvExport.write(encrypted, accounts, records)
            }
        } finally {
            keyBytes.fill(0)
            salt.fill(0)
            nonce.fill(0)
        }
    }
}
