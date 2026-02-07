package com.claudeusage.widget.data.repository

import com.claudeusage.widget.data.model.Credentials
import com.claudeusage.widget.data.model.UsageData
import kotlinx.coroutines.Dispatchers
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
                val request = Request.Builder()
                    .url("$BASE_URL/api/organizations/${credentials.organizationId}/usage")
                    .addHeader("Cookie", "sessionKey=${credentials.sessionKey}")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .addHeader("Referer", BASE_URL)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                when {
                    response.code == 401 || response.code == 403 -> {
                        Result.failure(AuthException("Session expired. Please log in again."))
                    }
                    !response.isSuccessful -> {
                        Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                    }
                    body.contains("Just a moment") || body.contains("Enable JavaScript") -> {
                        Result.failure(CloudflareException("Cloudflare challenge detected. Please try again."))
                    }
                    body.trimStart().startsWith("<") -> {
                        Result.failure(IOException("Unexpected HTML response from server."))
                    }
                    else -> {
                        val json = JSONObject(body)
                        Result.success(UsageData.fromJson(json))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
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

class AuthException(message: String) : Exception(message)
class CloudflareException(message: String) : Exception(message)
