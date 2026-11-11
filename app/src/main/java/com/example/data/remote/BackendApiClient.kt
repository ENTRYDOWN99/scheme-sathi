package com.example.data.remote

import com.example.BuildConfig
import com.example.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BackendAuthResult(
    val userId: String,
    val displayName: String,
    val role: String,
    val accessToken: String,
    val refreshToken: String
)

/**
 * Shared REST API client talking to the SchemeSaathi Node.js/Express backend (/api/v1).
 * Persists JWT tokens in Android Keystore via [TokenStore].
 */
class BackendApiClient(
    private val tokenStore: TokenStore,
    private val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun demoLogin(role: String): Result<BackendAuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().put("role", role).toString()
            val req = Request.Builder()
                .url("$baseUrl/auth/demo-login")
                .post(payload.toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { res ->
                val bodyStr = res.body?.string().orEmpty()
                if (!res.isSuccessful) error("HTTP ${res.code}: $bodyStr")
                val json = JSONObject(bodyStr)
                val userObj = json.getJSONObject("user")
                val access = json.getString("accessToken")
                val refresh = json.getString("refreshToken")
                val assignedRole = userObj.optString("role", role)
                tokenStore.saveTokens(access, refresh, assignedRole)
                BackendAuthResult(
                    userId = userObj.getString("id"),
                    displayName = userObj.optString("displayName", "Citizen"),
                    role = assignedRole,
                    accessToken = access,
                    refreshToken = refresh
                )
            }
        }
    }

    suspend fun upsertChecklistItem(
        schemeId: String,
        documentId: String,
        status: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenStore.getAccessToken() ?: return@runCatching
            val payload = JSONObject()
                .put("schemeId", schemeId)
                .put("documentId", documentId)
                .put("status", status)
                .toString()
            val req = Request.Builder()
                .url("$baseUrl/checklists")
                .header("Authorization", "Bearer $token")
                .put(payload.toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}")
            }
        }
    }

    suspend fun reviewApplication(
        applicationId: String,
        decision: String,
        reviewNote: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenStore.getAccessToken() ?: error("Not signed into backend API")
            val payload = JSONObject()
                .put("decision", decision)
                .put("reviewNote", reviewNote)
                .toString()
            val req = Request.Builder()
                .url("$baseUrl/applications/$applicationId/review")
                .header("Authorization", "Bearer $token")
                .patch(payload.toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    val errBody = res.body?.string().orEmpty()
                    error("HTTP ${res.code}: $errBody")
                }
            }
        }
    }

    suspend fun exportMyDpdpJson(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenStore.getAccessToken() ?: error("Sign in to backend to export live server archive")
            val req = Request.Builder()
                .url("$baseUrl/profile/export")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) error("HTTP ${res.code}: $body")
                body
            }
        }
    }
}

