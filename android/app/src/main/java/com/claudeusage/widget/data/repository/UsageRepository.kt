package com.claudeusage.widget.data.repository

import com.claudeusage.widget.data.model.Credentials
import com.claudeusage.widget.data.model.ExtraUsageInfo
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.data.model.UsageMetric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class UsageRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchUsageData(credentials: Credentials): Result<UsageData> =
        withContext(Dispatchers.IO) {
            try {
                coroutineScope {
                    val usageDeferred = async {
                        fetchJson(
                            "$BASE_URL/api/organizations/${credentials.organizationId}/usage",
                            credentials.sessionKey
                        )
                    }
                    val overageDeferred = async {
                        runCatching {
                            fetchJson(
                                "$BASE_URL/api/organizations/${credentials.organizationId}/overage_spend_limit",
                                credentials.sessionKey
                            )
                        }.getOrNull()
                    }
                    val prepaidDeferred = async {
                        runCatching {
                            fetchJson(
                                "$BASE_URL/api/organizations/${credentials.organizationId}/prepaid/credits",
                                credentials.sessionKey
                            )
                        }.getOrNull()
                    }

                    val usageResult = usageDeferred.await()
                    val overageJson = overageDeferred.await()
                    val prepaidJson = prepaidDeferred.await()

                    val usageData = UsageData.fromJson(usageResult)

                    // Merge overage spending data
                    var extraMetric = usageData.extraUsage
                    var extraInfo: ExtraUsageInfo? = null

                    if (overageJson != null) {
                        val limit = overageJson.optIntOrNull("monthly_credit_limit")
                            ?: overageJson.optIntOrNull("spend_limit_amount_cents")
                        val used = overageJson.optIntOrNull("used_credits")
                            ?: overageJson.optIntOrNull("balance_cents")
                        val enabled = if (overageJson.has("is_enabled"))
                            overageJson.optBoolean("is_enabled") else (limit != null)

                        if (enabled && limit != null && limit > 0 && used != null) {
                            val utilization = (used.toDouble() / limit) * 100
                            extraMetric = UsageMetric(utilization, null)
                            extraInfo = ExtraUsageInfo(usedCents = used, limitCents = limit)
                        }
                    }

                    // Merge prepaid balance
                    if (prepaidJson != null) {
                        val amount = prepaidJson.optIntOrNull("amount")
                        if (amount != null) {
                            extraInfo = (extraInfo ?: ExtraUsageInfo()).copy(balanceCents = amount)
                        }
                    }

                    Result.success(
                        usageData.copy(
                            extraUsage = extraMetric ?: usageData.extraUsage,
                            extraUsageInfo = extraInfo
                        )
                    )
                }
            } catch (e: AuthException) {
                Result.failure(e)
            } catch (e: CloudflareException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun fetchJson(url: String, sessionKey: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", "sessionKey=$sessionKey")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept", "application/json")
            .addHeader("Referer", BASE_URL)
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""

        when {
            response.code == 401 || response.code == 403 -> {
                throw AuthException("Session expired. Please log in again.")
            }
            !response.isSuccessful -> {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            body.contains("Just a moment") || body.contains("Enable JavaScript") -> {
                throw CloudflareException("Cloudflare challenge detected. Please try again.")
            }
            body.trimStart().startsWith("<") -> {
                throw IOException("Unexpected HTML response from server.")
            }
        }

        return JSONObject(body)
    }

    suspend fun fetchOrganizationId(sessionKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/api/organizations")
                    .addHeader("Cookie", "sessionKey=$sessionKey")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .addHeader("Referer", BASE_URL)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                when {
                    response.code == 401 || response.code == 403 -> {
                        Result.failure(AuthException("Invalid session key."))
                    }
                    !response.isSuccessful -> {
                        Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                    }
                    else -> {
                        val jsonArray = JSONArray(body)
                        if (jsonArray.length() > 0) {
                            val orgId = jsonArray.getJSONObject(0).getString("uuid")
                            Result.success(orgId)
                        } else {
                            Result.failure(IOException("No organizations found."))
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun validateSession(credentials: Credentials): Boolean =
        withContext(Dispatchers.IO) {
            fetchUsageData(credentials).isSuccess
        }

    companion object {
        const val BASE_URL = "https://claude.ai"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    return if (has(key) && !isNull(key)) optInt(key) else null
}

class AuthException(message: String) : Exception(message)
class CloudflareException(message: String) : Exception(message)
