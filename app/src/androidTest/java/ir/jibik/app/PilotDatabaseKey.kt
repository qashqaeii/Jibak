package ir.jibik.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Isolated pilot only. Never called for the production ledger. */
internal class PilotDatabaseKey(private val alias: String, file: File) {
    private val blob = AtomicFile(file)
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun existingKey(): SecretKey =
        keyStore().getKey(alias, null) as? SecretKey ?: error("Wrapping key unavailable")
    private fun aad() = ("jibik-sqlcipher-pilot-v1:" + alias).toByteArray(Charsets.UTF_8)

    fun getOrCreate(databaseExists: Boolean): ByteArray {
        // openRead recovers AtomicFile's legacy backup when necessary. An I/O
        // error is not evidence that a key is absent.
        val bytes = try { blob.readFully() } catch (e: java.io.FileNotFoundException) {
            if (blob.baseFile.exists() || File(blob.baseFile.path + ".bak").exists() ||
                File(blob.baseFile.path + ".new").exists()) throw e
            null
        }
        if (bytes != null) {
            require(bytes.size == 1 + 12 + 32 + 16 && bytes[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.updateAAD(aad())
            return cipher.doFinal(bytes, 13, bytes.size - 13).also { require(it.size == 32) }
        }
        check(!databaseExists) { "Existing database has no wrapped key; refusing to replace it" }
        val ks = keyStore()
        val key = if (ks.containsAlias(alias)) existingKey() else KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build())
            }.generateKey()
        val secret = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(aad())
            require(cipher.iv.size == 12)
            val wrapped = byteArrayOf(1) + cipher.iv + cipher.doFinal(secret)
            val output = blob.startWrite()
            try { output.write(wrapped); blob.finishWrite(output) }
            catch (e: Exception) { blob.failWrite(output); throw e }
            return secret
        } catch (e: Exception) { secret.fill(0); throw e }
    }
    fun deleteFixture() { blob.delete(); keyStore().deleteEntry(alias) }
}
