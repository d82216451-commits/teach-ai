package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Teaching
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TeachingRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeTeachings(userId: String): Flow<List<Teaching>> {
        val path = "users/$userId/teachings"
        return db.collection("users").document(userId).collection("teachings")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Teaching::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun getTeachingsOnce(userId: String): Result<List<Teaching>> {
        val path = "users/$userId/teachings"
        return try {
            val snapshot = db.collection("users").document(userId).collection("teachings").get().await()
            val list = snapshot.toObjects(Teaching::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun createTeaching(
        userId: String,
        category: String,
        content: String,
        importance: String = "normal"
    ): Result<String> {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/teachings"
        val collection = db.collection("users").document(userId).collection("teachings")
        val docRef = collection.document()
        val payload = mapOf(
            "userId" to userId,
            "category" to category,
            "content" to content.trim(),
            "importance" to importance,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "${path}/${docRef.id}")
            Result.failure(e)
        }
    }

    suspend fun updateTeaching(userId: String, teaching: Teaching): Result<Unit> {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        require(teaching.id.isNotBlank()) { "Teaching ID must not be blank" }
        requireNotNull(teaching.createdAt) { "Teaching createdAt must not be null" }
        val docRef = db.collection("users").document(userId).collection("teachings").document(teaching.id)
        return try {
            docRef.update(
                mapOf(
                    "category" to teaching.category,
                    "content" to teaching.content.trim(),
                    "importance" to teaching.importance,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTeaching(userId: String, teachingId: String): Result<Unit> {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val docRef = db.collection("users").document(userId).collection("teachings").document(teachingId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
