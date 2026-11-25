package ir.jibik.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val APP_LOCK_DELAY_MS = 30_000L
internal fun appLockExpired(enabled: Boolean, stoppedAt: Long?, now: Long): Boolean =
    enabled && stoppedAt != null && (now < stoppedAt || now - stoppedAt >= APP_LOCK_DELAY_MS)

/** UI access gate. No credentials, keys or successful-auth state are persisted.
 * This is separate from encryption at rest; never treat this as a database cipher.
 */
internal class AppProtection(private val activity: ComponentActivity) {
    private val prefs = activity.getSharedPreferences("app_protection", Context.MODE_PRIVATE)
    private val keyguard = activity.getSystemService(KeyguardManager::class.java)
    var enabled by mutableStateOf(prefs.getBoolean("enabled", false))
        private set
    var blockCapture by mutableStateOf(prefs.getBoolean("block_capture", false))
        private set
    var locked by mutableStateOf(enabled)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    private var stoppedAt: Long? = null
    private var pending: Purpose? = null
    private var cancellation: CancellationSignal? = null
    private var disposed = false
    private enum class Purpose { ENABLE, DISABLE, UNLOCK }

    private val credential = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (pending != null) complete(result.resultCode == Activity.RESULT_OK)
    }

    init { applyWindowProtection() }

    private fun applyWindowProtection() {
        if (enabled || blockCapture) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun onForeground() {
        if (appLockExpired(enabled, stoppedAt, SystemClock.elapsedRealtime())) locked = true
        stoppedAt = null
    }
    fun onBackground() { stoppedAt = SystemClock.elapsedRealtime() }
    fun lockNow() { if (enabled && !busy) { locked = true; message = "" } }

    fun setCapture(value: Boolean) {
        if (busy || locked || enabled) return
        if (prefs.edit().putBoolean("block_capture", value).commit()) {
            blockCapture = value; applyWindowProtection()
        } else message = "تنظیمات ذخیره نشد."
    }

    fun requestEnabled(value: Boolean) {
        if (locked || value == enabled || busy) return
        authenticate(if (value) Purpose.ENABLE else Purpose.DISABLE, credentialOnly = false)
    }
    fun unlock(credentialOnly: Boolean = false) {
        if (locked && !busy) authenticate(Purpose.UNLOCK, credentialOnly)
    }

    private fun authenticate(purpose: Purpose, credentialOnly: Boolean) {
        if (disposed || busy) return
        message = ""
        if (!keyguard.isDeviceSecure) {
            message = "ابتدا در تنظیمات امنیت گوشی، رمز، الگو یا PIN صفحه قفل را فعال کن."
            return
        }
        pending = purpose; busy = true
        try {
            if (Build.VERSION.SDK_INT >= 30 && !credentialOnly) {
                val signal = CancellationSignal()
                cancellation = signal
                BiometricPrompt.Builder(activity)
                    .setTitle("تأیید هویت برای جیبیک")
                    .setSubtitle("اثر انگشت، تشخیص چهره امن یا رمز گوشی")
                    .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()
                    .authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (cancellation === signal) complete(true)
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (cancellation === signal) complete(false)
                        }
                    })
            } else {
                @Suppress("DEPRECATION")
                val intent = keyguard.createConfirmDeviceCredentialIntent("تأیید هویت جیبیک", "رمز صفحه قفل گوشی را وارد کن.")
                if (intent == null) complete(false) else credential.launch(intent)
            }
        } catch (_: Exception) { complete(false) }
    }

    private fun complete(success: Boolean) {
        val purpose = pending ?: return
        pending = null; cancellation = null; busy = false
        if (disposed) return
        if (!success || !keyguard.isDeviceSecure) {
            message = "تأیید هویت کامل نشد؛ دوباره تلاش کن."
            return
        }
        when (purpose) {
            Purpose.UNLOCK -> { locked = false; stoppedAt = null }
            Purpose.ENABLE, Purpose.DISABLE -> {
                val value = purpose == Purpose.ENABLE
                if (prefs.edit().putBoolean("enabled", value).commit()) {
                    enabled = value; locked = false; stoppedAt = null; applyWindowProtection()
                    message = if (value) "قفل جیبیک فعال شد." else "قفل جیبیک غیرفعال شد."
                } else message = "تنظیمات ذخیره نشد."
            }
        }
    }

    fun dispose() {
        disposed = true
        pending = null
        cancellation?.cancel()
        cancellation = null
    }
}
