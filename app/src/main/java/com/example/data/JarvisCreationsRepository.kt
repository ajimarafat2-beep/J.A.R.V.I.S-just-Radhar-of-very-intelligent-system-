package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JarvisCreationsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun upsertUserProfile(
        displayName: String,
        email: String?,
        photoUrl: String?,
        preferredLanguage: String
    ): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val safeName = displayName.trim().ifBlank { "Sir" }.take(100)
            val safeLang = preferredLanguage.trim().ifBlank { "AUTO" }.take(24)
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path

            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "preferredLanguage" to safeLang,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                email?.trim()?.takeIf { it.isNotBlank() }?.let { createPayload["email"] = it.take(160) }
                photoUrl?.trim()?.takeIf { it.isNotBlank() }?.let { createPayload["photoUrl"] = it.take(500) }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "displayName" to safeName,
                    "preferredLanguage" to safeLang,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                email?.trim()?.takeIf { it.isNotBlank() }?.let { updatePayload["email"] = it.take(160) }
                photoUrl?.trim()?.takeIf { it.isNotBlank() }?.let { updatePayload["photoUrl"] = it.take(500) }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeCreations(userId: String): Flow<List<StudioCreation>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/creations"
        emitAll(
            db.collection("users").document(uid).collection("creations")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(40)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudioCreation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveCreation(
        mediaType: CreationMediaType,
        prompt: String,
        title: String,
        summary: String?,
        modelUsed: String,
        voiceName: String? = null,
        durationSeconds: Int = 0,
        localFilePath: String? = null
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/creations"
        return try {
            val uid = requireUserId()
            val safePrompt = prompt.trim().ifBlank { "Untitled Prompt" }.take(1000)
            val safeTitle = title.trim().ifBlank { safePrompt }.take(200)
            val safeModel = modelUsed.trim().ifBlank { "gemini-3.8-flash" }.take(80)
            val safeDuration = durationSeconds.coerceIn(0, 600)

            val docRef = db.collection("users").document(uid).collection("creations").document()
            docPath = docRef.path

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "mediaType" to mediaType.name,
                "prompt" to safePrompt,
                "title" to safeTitle,
                "modelUsed" to safeModel,
                "durationSeconds" to safeDuration,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            summary?.trim()?.takeIf { it.isNotBlank() }?.let { payload["summary"] = it.take(2000) }
            voiceName?.trim()?.takeIf { it.isNotBlank() }?.let { payload["voiceName"] = it.take(40) }
            localFilePath?.trim()?.takeIf { it.isNotBlank() }?.let { payload["localFilePath"] = it.take(500) }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCreation(creationId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/creations/$creationId"
        return try {
            val uid = requireUserId()
            require(creationId.isNotBlank()) { "Creation ID must not be blank." }
            val docRef = db.collection("users").document(uid).collection("creations").document(creationId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
