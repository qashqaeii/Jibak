package ir.jibik.app

import java.net.URI
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class JiboAiNetworkException(message: String) : Exception(message)

internal data class JiboAiAnswer(val summary: String, val tips: List<String>, val cached: Boolean)

/** Only communicates with a configured Jibik HTTPS server, never directly with Arvan. */
internal class JiboAiClient(serverUrl: String) {
    private val root: String

    init {
        val parsed = try { URI(serverUrl.trim()) } catch (_: Exception) { null }
        val host = parsed?.host?.lowercase().orEmpty()
        require(
            parsed?.scheme == "https" && host.isNotBlank()
                && host != "localhost" && !host.endsWith(".local")
                && !host.endsWith(".internal")
                && parsed.rawUserInfo == null
                && parsed.rawQuery == null && parsed.rawFragment == null
                && parsed.port in listOf(-1, 443)
        ) { "A valid HTTPS Jibik server is required" }
        root = serverUrl.trim().trimEnd('/')
    }

    private suspend fun post(path: String, body: JSONObject, token: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val endpoint = URL("$root/api/v1/$path")
            val connection = endpoint.openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 16_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Cache-Control", "no-store")
                if (!token.isNullOrBlank()) {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                }
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    throw JiboAiNetworkException(
                        when (status) {
                            400 -> "اطلاعات ارسال‌شده معتبر نیست؛ دوباره تلاش کن."
                            401, 403 -> "ورود معتبر نیست یا منقضی شده؛ دوباره وارد شو."
                            409 -> "برای تحلیل به تراکنش‌های بیشتری نیاز داریم."
                            429 -> "سهمیه امروز کامل شده یا سرویس شلوغ است."
                            502, 503, 504 -> "دستیار ابری فعلاً در دسترس نیست."
                            else -> "اتصال به سرویس هوشمند ناموفق بود."
                        }
                    )
                }
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                    it.readText()
                }
                if (response.length > 12_000) throw JiboAiNetworkException(
                    "پاسخ سرویس قابل خواندن نیست."
                )
                JSONObject(response)
            } catch (e: JiboAiNetworkException) {
                throw e
            } catch (_: Exception) {
                throw JiboAiNetworkException("ارتباط امن با سرور برقرار نشد؛ اینترنت را بررسی کن.")
            } finally {
                connection.disconnect()
            }
        }

    suspend fun login(username: String, password: String): String {
        if (username.isBlank() || password.isBlank()) {
            throw JiboAiNetworkException("نام کاربری و رمز عبور را وارد کن.")
        }
        val response = post("auth/token/", JSONObject()
            .put("username", username.trim()).put("password", password))
        return response.optString("access").takeIf { it.length in 20..4000 }
            ?: throw JiboAiNetworkException("پاسخ ورود معتبر نیست.")
    }

    suspend fun analyze(token: String, feature: JiboAiFeature, snapshot: JiboFinancialSnapshot): JiboAiAnswer {
        if (!snapshot.hasData) throw JiboAiNetworkException(
            "ابتدا درآمد یا هزینه‌ای ثبت کن."
        )
        val categories = JSONArray()
        snapshot.categories.forEach {
            categories.put(JSONObject().put("code", it.code).put("amount_toman", it.amountToman))
        }
        val body = JSONObject()
            .put("feature", feature.code)
            .put("consent", true)
            .put("summary", JSONObject()
                .put("period", "rolling_30_days")
                .put("income_toman", snapshot.incomeToman)
                .put("expense_toman", snapshot.expenseToman)
                .put("income_count", snapshot.incomeCount)
                .put("expense_count", snapshot.expenseCount)
                .put("categories", categories))
        val response = post("ai/mobile-summary/", body, token)
        val result = response.optJSONObject("result")
            ?: throw JiboAiNetworkException("پاسخ دستیار ناقص بود.")
        val summary = result.optString("summary").trim().takeIf { it.isNotEmpty() }
            ?: throw JiboAiNetworkException("پاسخ دستیار ناقص بود.")
        val suggestions = result.optJSONArray("tips")
            ?: throw JiboAiNetworkException("پاسخ دستیار ناقص بود.")
        val tips = (0 until minOf(3, suggestions.length())).map {
            suggestions.optString(it).trim()
        }.filter { it.isNotBlank() }
        return JiboAiAnswer(summary.take(420), tips, response.optBoolean("cached"))
    }
}
