package com.example.data.remote

import com.example.BuildConfig
import com.example.data.model.CitizenProfile
import com.example.data.model.CollaborativeTask
import com.example.data.model.DocumentHaveStatus
import com.example.data.model.SchemeApplication
import com.example.security.TokenStore
import com.example.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class BackendAuthResult(
    val userId: String,
    val displayName: String,
    val role: String,
    val accessToken: String,
    val refreshToken: String
)

data class BackendProfileSnapshot(
    val profile: CitizenProfile,
    val displayName: String,
    val phoneNumber: String,
    val dateOfBirth: String
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

    private fun requireToken(): String =
        tokenStore.getAccessToken() ?: error("Not signed into shared backend API")

    private fun parseIsoToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(iso.take(19))?.time ?: System.currentTimeMillis()
        }.getOrDefault(System.currentTimeMillis())
    }

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

    suspend fun fetchProfile(currentUserId: String): Result<BackendProfileSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireToken()
            val req = Request.Builder()
                .url("$baseUrl/profile")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val bodyStr = res.body?.string().orEmpty()
                if (!res.isSuccessful) error("HTTP ${res.code}: $bodyStr")
                val p = JSONObject(bodyStr).getJSONObject("profile")
                val uid = p.optString("userId", currentUserId)
                val name = p.optString("displayName", "")
                val phone = p.optString("phoneNumber", "")
                val dob = p.optString("dateOfBirth", "")
                val profile = CitizenProfile(
                    id = uid,
                    userId = uid,
                    encryptedName = if (name.isNotBlank()) SecurityUtils.encryptPii(name, currentUserId) else "",
                    encryptedPhone = if (phone.isNotBlank()) SecurityUtils.encryptPii(phone, currentUserId) else "",
                    encryptedDob = if (dob.isNotBlank()) SecurityUtils.encryptPii(dob, currentUserId) else "",
                    birthYear = p.optInt("birthYear", 1985),
                    ageConfirmedViaVoice = p.optBoolean("ageConfirmedViaVoice", true),
                    gender = p.optString("gender", "unknown"),
                    state = p.optString("state", "Uttar Pradesh"),
                    district = p.optString("district", "Varanasi"),
                    residenceType = p.optString("urbanOrRural", "rural"),
                    annualIncome = p.optInt("annualIncome", -1),
                    occupation = p.optString("occupation", "farmer"),
                    bplStatus = p.optString("bplStatus", "unknown"),
                    socialCategory = p.optString("socialCategory", "unknown"),
                    hasBankAccount = p.optString("hasBankAccount", "yes"),
                    hasLpgConnection = p.optString("hasLpgConnection", "unknown"),
                    ownsAgriculturalLand = p.optString("ownsAgriculturalLand", "unknown"),
                    isIncomeTaxPayer = p.optString("isIncomeTaxPayer", "no"),
                    consentGranted = true,
                    preferredLanguage = p.optString("preferredLanguage", "hi"),
                    role = tokenStore.getSavedRole() ?: "CITIZEN",
                    updatedAtMillis = parseIsoToMillis(p.optString("updatedAt"))
                )
                BackendProfileSnapshot(
                    profile = profile,
                    displayName = name,
                    phoneNumber = phone,
                    dateOfBirth = dob
                )
            }
        }
    }

    suspend fun saveProfile(
        profile: CitizenProfile,
        displayName: String,
        phoneNumber: String,
        dateOfBirth: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenStore.getAccessToken() ?: return@runCatching
            val payload = JSONObject()
                .put("displayName", displayName.ifBlank { "Citizen" })
                .put("phoneNumber", phoneNumber)
                .put("dateOfBirth", dateOfBirth)
                .put("birthYear", profile.birthYear)
                .put("ageConfirmedViaVoice", profile.ageConfirmedViaVoice)
                .put("gender", profile.gender)
                .put("state", profile.state)
                .put("district", profile.district)
                .put("urbanOrRural", profile.residenceType)
                .put("annualIncome", profile.annualIncome)
                .put("bplStatus", profile.bplStatus)
                .put("socialCategory", profile.socialCategory)
                .put("ownsAgriculturalLand", profile.ownsAgriculturalLand)
                .put("hasBankAccount", profile.hasBankAccount)
                .put("isIncomeTaxPayer", profile.isIncomeTaxPayer)
                .put("hasLpgConnection", profile.hasLpgConnection)
                .put("occupation", profile.occupation)
                .put("preferredLanguage", profile.preferredLanguage)
                .toString()

            val req = Request.Builder()
                .url("$baseUrl/profile")
                .header("Authorization", "Bearer $token")
                .put(payload.toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}: ${res.body?.string().orEmpty()}")
            }
        }
    }

    suspend fun deleteProfile(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenStore.getAccessToken() ?: return@runCatching
            val req = Request.Builder()
                .url("$baseUrl/profile")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}: ${res.body?.string().orEmpty()}")
            }
        }
    }

    suspend fun fetchChecklists(): Result<Map<String, DocumentHaveStatus>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireToken()
            val req = Request.Builder()
                .url("$baseUrl/checklists")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val bodyStr = res.body?.string().orEmpty()
                if (!res.isSuccessful) error("HTTP ${res.code}: $bodyStr")
                val arr = JSONObject(bodyStr).getJSONArray("items")
                val out = mutableMapOf<String, DocumentHaveStatus>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val docId = item.getString("documentId")
                    val status = runCatching {
                        DocumentHaveStatus.valueOf(item.getString("status"))
                    }.getOrDefault(DocumentHaveStatus.UNKNOWN)
                    out[docId] = status
                }
                out
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

    suspend fun fetchApplications(): Result<List<SchemeApplication>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireToken()
            val req = Request.Builder()
                .url("$baseUrl/applications")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val bodyStr = res.body?.string().orEmpty()
                if (!res.isSuccessful) error("HTTP ${res.code}: $bodyStr")
                val arr = JSONObject(bodyStr).getJSONArray("applications")
                val list = mutableListOf<SchemeApplication>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val status = obj.optString("status", "DRAFT")
                    list.add(
                        SchemeApplication(
                            id = obj.getString("id"),
                            userId = obj.optString("userId", ""),
                            schemeId = obj.optString("schemeId", ""),
                            schemeName = obj.optString("schemeNameEn", ""),
                            status = status,
                            verified = status in setOf("UNDER_REVIEW", "APPROVED", "REJECTED", "ACKNOWLEDGED"),
                            applicantName = obj.optString("applicantName", ""),
                            stateDistrict = obj.optString("stateDistrict", ""),
                            bankIfscSummary = obj.optString("bankIfscSummary", ""),
                            checklistSummary = obj.optString("checklistSummary", ""),
                            snapshotHash = obj.optString("applicantSnapshotHash", ""),
                            explicitCitizenConfirmed = obj.optBoolean("humanConfirmed", false),
                            createdAtMillis = parseIsoToMillis(obj.optString("createdAt")),
                            updatedAtMillis = parseIsoToMillis(obj.optString("updatedAt"))
                        )
                    )
                }
                list
            }
        }
    }

    suspend fun createAndConfirmApplication(app: SchemeApplication): Result<SchemeApplication> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = requireToken()
                val createPayload = JSONObject()
                    .put("schemeId", app.schemeId)
                    .put("applicantName", app.applicantName.ifBlank { "Citizen" })
                    .put("stateDistrict", app.stateDistrict.ifBlank { "Uttar Pradesh - Varanasi" })
                    .put("bankIfscSummary", app.bankIfscSummary.ifBlank { "DBT Enabled" })
                    .put("checklistSummary", app.checklistSummary.ifBlank { "Checklist reviewed" })
                    .toString()

                val createReq = Request.Builder()
                    .url("$baseUrl/applications")
                    .header("Authorization", "Bearer $token")
                    .post(createPayload.toRequestBody(jsonMedia))
                    .build()

                val createdJson = client.newCall(createReq).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) error("HTTP ${res.code}: $body")
                    JSONObject(body).getJSONObject("application")
                }

                val appId = createdJson.getString("id")
                val serverHash = createdJson.optString("applicantSnapshotHash", app.snapshotHash)

                val confirmPayload = JSONObject()
                    .put("confirmSnapshotHash", serverHash)
                    .put("reviewedFields", true)
                    .put("understandsAuthoritativePortal", true)
                    .put("isAuthorizedHuman", true)
                    .toString()

                val confirmReq = Request.Builder()
                    .url("$baseUrl/applications/$appId/confirm")
                    .header("Authorization", "Bearer $token")
                    .post(confirmPayload.toRequestBody(jsonMedia))
                    .build()

                client.newCall(confirmReq).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) error("HTTP ${res.code}: $body")
                    val confirmed = JSONObject(body).getJSONObject("application")
                    app.copy(
                        id = confirmed.getString("id"),
                        status = confirmed.optString("status", "HANDED_OFF"),
                        snapshotHash = confirmed.optString("applicantSnapshotHash", serverHash),
                        explicitCitizenConfirmed = true,
                        createdAtMillis = System.currentTimeMillis(),
                        updatedAtMillis = System.currentTimeMillis()
                    )
                }
            }
        }

    suspend fun reviewApplication(
        applicationId: String,
        decision: String,
        reviewNote: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireToken()
            val mappedDecision = if (decision == "ACKNOWLEDGED") "UNDER_REVIEW" else decision
            val payload = JSONObject()
                .put("decision", mappedDecision)
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

    suspend fun fetchTasks(): Result<List<CollaborativeTask>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireToken()
            val req = Request.Builder()
                .url("$baseUrl/tasks")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val bodyStr = res.body?.string().orEmpty()
                if (!res.isSuccessful) error("HTTP ${res.code}: $bodyStr")
                val arr = JSONObject(bodyStr).getJSONArray("tasks")
                val list = mutableListOf<CollaborativeTask>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val backendStatus = obj.optString("status", "OPEN")
                    val mappedStatus = if (backendStatus == "OPEN") "PENDING" else backendStatus
                    list.add(
                        CollaborativeTask(
                            id = obj.getString("id"),
                            userId = obj.optString("userId", ""),
                            title = obj.optString("titleEn", ""),
                            description = obj.optString("descriptionEn", ""),
                            assigneeName = obj.optString("assignedOperatorName", "CSC Desk"),
                            assigneeRole = obj.optString("assignedToRole", "CSC_OPERATOR"),
                            priority = "HIGH",
                            status = mappedStatus,
                            schemeCode = obj.optString("schemeId", "PM-KISAN"),
                            dueDateLabel = obj.optString("dueDateText", "Pending action"),
                            deliveryChannel = "IN_APP",
                            deliveryStatus = if (mappedStatus == "COMPLETED") "DELIVERED" else "SCHEDULED",
                            createdAtMillis = parseIsoToMillis(obj.optString("createdAt")),
                            updatedAtMillis = parseIsoToMillis(obj.optString("updatedAt"))
                        )
                    )
                }
                list
            }
        }
    }

    suspend fun createTask(task: CollaborativeTask, schemeName: String): Result<CollaborativeTask> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = requireToken()
                val payload = JSONObject()
                    .put("schemeId", task.schemeCode)
                    .put("schemeName", schemeName)
                    .put("titleEn", task.title)
                    .put("titleHi", task.title)
                    .put("descriptionEn", task.description)
                    .put("descriptionHi", task.description)
                    .put("assignedToRole", task.assigneeRole)
                    .put("assignedOperatorName", task.assigneeName)
                    .put("dueDateText", task.dueDateLabel)
                    .toString()
                val req = Request.Builder()
                    .url("$baseUrl/tasks")
                    .header("Authorization", "Bearer $token")
                    .post(payload.toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { res ->
                    val bodyStr = res.body?.string().orEmpty()
                    if (!res.isSuccessful) error("HTTP ${res.code}: $bodyStr")
                    val created = JSONObject(bodyStr).getJSONObject("task")
                    task.copy(
                        id = created.getString("id"),
                        createdAtMillis = System.currentTimeMillis(),
                        updatedAtMillis = System.currentTimeMillis()
                    )
                }
            }
        }

    suspend fun updateTaskStatus(taskId: String, nextStatus: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = tokenStore.getAccessToken() ?: return@runCatching
                val mappedStatus = when (nextStatus) {
                    "PENDING" -> "OPEN"
                    "IN_PROGRESS" -> "IN_PROGRESS"
                    "COMPLETED" -> "COMPLETED"
                    else -> "COMPLETED"
                }
                val payload = JSONObject()
                    .put("status", mappedStatus)
                    .put("isNotificationUnread", mappedStatus != "COMPLETED")
                    .toString()
                val req = Request.Builder()
                    .url("$baseUrl/tasks/$taskId")
                    .header("Authorization", "Bearer $token")
                    .patch(payload.toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) error("HTTP ${res.code}: ${res.body?.string().orEmpty()}")
                }
            }
        }

    suspend fun exportMyDpdpJson(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireToken()
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
