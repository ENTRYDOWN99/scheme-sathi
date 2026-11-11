package com.example.data.repository

import android.util.Log
import com.example.util.SecurityUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("authenticated", currentUser != null)
        put("emailVerified", currentUser?.isEmailVerified ?: false)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val safeMessage = SecurityUtils.redactPiiForLlm(rootError.message ?: rootError.toString())
    val errorInfoJson = JSONObject().apply {
        put("error", safeMessage)
        put("operationType", operationType.value)
        put("path", path?.let { SecurityUtils.redactPiiForLlm(it) })
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error (${operationType.value}): $safeMessage")
    return jsonString
}
