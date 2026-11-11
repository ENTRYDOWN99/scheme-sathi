package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CitizenProfile
import com.example.data.model.CollaborativeTask
import com.example.data.model.SchemeApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SchemeSaathiRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- CITIZEN PROFILE OPERATIONS ---

    fun observeProfile(userId: String = requireUserId()): Flow<CitizenProfile?> = flow {
        val docRef = db.collection("users").document(userId).collection("profile").document("main")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(CitizenProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun getProfile(userId: String = requireUserId()): CitizenProfile? {
        val docRef = db.collection("users").document(userId).collection("profile").document("main")
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(CitizenProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun saveProfile(profile: CitizenProfile) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("profile").document("main")
        try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "encryptedName" to profile.encryptedName,
                    "encryptedPhone" to profile.encryptedPhone,
                    "birthYear" to profile.birthYear,
                    "ageConfirmedViaVoice" to profile.ageConfirmedViaVoice,
                    "gender" to profile.gender,
                    "state" to profile.state,
                    "district" to profile.district,
                    "residenceType" to profile.residenceType,
                    "annualIncome" to profile.annualIncome,
                    "occupation" to profile.occupation,
                    "bplStatus" to profile.bplStatus,
                    "socialCategory" to profile.socialCategory,
                    "hasBankAccount" to profile.hasBankAccount,
                    "hasLpgConnection" to profile.hasLpgConnection,
                    "ownsAgriculturalLand" to profile.ownsAgriculturalLand,
                    "isIncomeTaxPayer" to profile.isIncomeTaxPayer,
                    "familySize" to profile.familySize,
                    "consentGranted" to profile.consentGranted,
                    "preferredLanguage" to profile.preferredLanguage,
                    "role" to profile.role,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val existingCreatedAt = existingSnap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
                val updatePayload = mapOf(
                    "userId" to uid,
                    "encryptedName" to profile.encryptedName,
                    "encryptedPhone" to profile.encryptedPhone,
                    "birthYear" to profile.birthYear,
                    "ageConfirmedViaVoice" to profile.ageConfirmedViaVoice,
                    "gender" to profile.gender,
                    "state" to profile.state,
                    "district" to profile.district,
                    "residenceType" to profile.residenceType,
                    "annualIncome" to profile.annualIncome,
                    "occupation" to profile.occupation,
                    "bplStatus" to profile.bplStatus,
                    "socialCategory" to profile.socialCategory,
                    "hasBankAccount" to profile.hasBankAccount,
                    "hasLpgConnection" to profile.hasLpgConnection,
                    "ownsAgriculturalLand" to profile.ownsAgriculturalLand,
                    "isIncomeTaxPayer" to profile.isIncomeTaxPayer,
                    "familySize" to profile.familySize,
                    "consentGranted" to profile.consentGranted,
                    "preferredLanguage" to profile.preferredLanguage,
                    "role" to profile.role,
                    "createdAt" to existingCreatedAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(updatePayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteProfile(userId: String = requireUserId()) {
        val docRef = db.collection("users").document(userId).collection("profile").document("main")
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- SCHEME APPLICATION OPERATIONS ---

    fun observeApplications(userId: String = requireUserId()): Flow<List<SchemeApplication>> = flow {
        val colRef = db.collection("users").document(userId).collection("applications")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SchemeApplication::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "users/$userId/applications")
                    }
                    throw error
                }
        )
    }

    suspend fun createApplication(application: SchemeApplication): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("applications")
        val docRef = if (application.id.isNotBlank()) colRef.document(application.id) else colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "schemeId" to application.schemeId,
            "schemeName" to application.schemeName,
            "ruleVersion" to application.ruleVersion,
            "status" to application.status,
            "verified" to application.verified,
            "applicantName" to application.applicantName,
            "applicantNameSource" to application.applicantNameSource,
            "stateDistrict" to application.stateDistrict,
            "stateDistrictSource" to application.stateDistrictSource,
            "bankIfscSummary" to application.bankIfscSummary,
            "bankIfscSource" to application.bankIfscSource,
            "checklistSummary" to application.checklistSummary,
            "snapshotHash" to application.snapshotHash,
            "explicitCitizenConfirmed" to application.explicitCitizenConfirmed,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateApplication(application: SchemeApplication) {
        val uid = requireUserId()
        require(application.id.isNotBlank()) { "Application ID must not be blank for update" }
        requireNotNull(application.createdAt) { "Application createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid).collection("applications").document(application.id)
        try {
            docRef.set(application.copy(userId = uid, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- COLLABORATIVE TASK & REMINDER OPERATIONS ---

    fun observeTasks(userId: String = requireUserId()): Flow<List<CollaborativeTask>> = flow {
        val colRef = db.collection("users").document(userId).collection("tasks")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CollaborativeTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "users/$userId/tasks")
                    }
                    throw error
                }
        )
    }

    suspend fun createTask(task: CollaborativeTask): String {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("tasks")
        val docRef = if (task.id.isNotBlank()) colRef.document(task.id) else colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "title" to task.title,
            "description" to task.description,
            "assigneeName" to task.assigneeName,
            "assigneeRole" to task.assigneeRole,
            "priority" to task.priority,
            "status" to task.status,
            "schemeCode" to task.schemeCode,
            "dueDateLabel" to task.dueDateLabel,
            "deliveryChannel" to task.deliveryChannel,
            "deliveryStatus" to task.deliveryStatus,
            "activityLogSummary" to task.activityLogSummary,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateTask(task: CollaborativeTask) {
        val uid = requireUserId()
        require(task.id.isNotBlank()) { "Task ID must not be blank for update" }
        requireNotNull(task.createdAt) { "Task createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
        try {
            docRef.set(task.copy(userId = uid, updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteTask(taskId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
