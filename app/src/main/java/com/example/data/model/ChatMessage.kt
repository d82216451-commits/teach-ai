package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user", // "user", "model"
    val content: String = "",
    val mediaType: String? = null, // "image", "file", "video", "none"
    val mediaUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)
