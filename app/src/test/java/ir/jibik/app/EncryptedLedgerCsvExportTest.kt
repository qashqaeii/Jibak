package ir.jibik.app

import ir.jibik.core.finance.Account
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedLedgerCsvExportTest {
    private val password = "Long passphrase for Jibik".toCharArray()
    private val accounts = listOf(Account("wallet", "کیف پول", 100_000))
    private val records = listOf(LedgerRecord(
        id = "entry-1", kind = LedgerKind.EXPENSE, amountToman = 25_000,
        fromAccountId = "wallet", toAccountId = null,
        category = "خوراک", note = "خرید با کارت",
        timestampMillis = 1_700_000_000_000L,
    ))

    private fun export(password: CharArray = this.password): ByteArray {
        val output = ByteArrayOutputStream()
        EncryptedLedgerCsvExport.write(output, password, accounts, records)
        return output.toByteArray()
    }

    // Independent reader for on-disk format, not used by the app at runtime.
    private fun decrypt(file: ByteArray, password: CharArray = this.password): ByteArray {
        val magic = EncryptedLedgerCsvExport.MAGIC
        assertArrayEquals(magic, file.copyOfRange(0, magic.size))
        val headerLen = magic.size + EncryptedLedgerCsvExport.SALT_BYTES +
            EncryptedLedgerCsvExport.NONCE_BYTES
        val salt = file.copyOfRange(magic.size,
            magic.size + EncryptedLedgerCsvExport.SALT_BYTES)
        val nonce = file.copyOfRange(headerLen - EncryptedLedgerCsvExport.NONCE_BYTES, headerLen)
        val spec = PBEKeySpec(password, salt, EncryptedLedgerCsvExport.ITERATIONS, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, nonce))
            cipher.updateAAD(file.copyOfRange(0, headerLen))
            cipher.doFinal(file.copyOfRange(headerLen, file.size))
        } finally {
            key.fill(0)
        }
    }

    @Test fun roundTripRestoresIdenticalUtf8Csv() {
        val plaintext = ByteArrayOutputStream().also {
            LedgerCsvExport.write(it, accounts, records)
        }.toByteArray()
        assertArrayEquals(plaintext, decrypt(export()))
    }

    @Test fun encryptsContentWithRandomSaltAndNonce() {
        val first = export()
        val second = export()
        assertFalse(first.contentEquals(second))
        assertFalse(String(first, Charsets.ISO_8859_1).contains("خرید با کارت"))
        assertArrayEquals(decrypt(first), decrypt(second))
    }

    @Test fun rejectsWrongPasswordAndAlteredCiphertext() {
        val file = export()
        var wrongPasswordRejected = false
        try { decrypt(file, "Completely different secret".toCharArray()) }
        catch (_: java.security.GeneralSecurityException) { wrongPasswordRejected = true }
        assertTrue(wrongPasswordRejected)

        file[file.lastIndex - 4] = (file[file.lastIndex - 4].toInt() xor 1).toByte()
        var tamperRejected = false
        try { decrypt(file) }
        catch (_: java.security.GeneralSecurityException) { tamperRejected = true }
        assertTrue(tamperRejected)
    }

    @Test fun rejectsWeakPasswordWithoutWritingFile() {
        val out = ByteArrayOutputStream()
        var rejected = false
        try { EncryptedLedgerCsvExport.write(out, "weak".toCharArray(), accounts, records) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertTrue(out.size() == 0)
    }
}
