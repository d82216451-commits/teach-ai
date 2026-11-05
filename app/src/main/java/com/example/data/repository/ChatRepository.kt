package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
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

class ChatRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeMessages(userId: String): Flow<List<ChatMessage>> {
        return db.collection("users").document(userId).collection("messages")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.seconds ?: 0L }
            }
    }

    suspend fun addMessage(
        userId: String,
        role: String,
        content: String,
        mediaType: String? = null,
        mediaUrl: String? = null
    ): Result<String> {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/messages"
        val collection = db.collection("users").document(userId).collection("messages")
        val docRef = collection.document()
        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "role" to role,
            "content" to content,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (mediaType != null) payload["mediaType"] = mediaType
        if (mediaUrl != null) payload["mediaUrl"] = mediaUrl

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

    suspend fun deleteMessage(userId: String, messageId: String): Result<Unit> {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val docRef = db.collection("users").document(userId).collection("messages").document(messageId)
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
