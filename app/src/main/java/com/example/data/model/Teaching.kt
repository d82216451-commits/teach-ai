package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Teaching(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "general", // "personal", "preference", "rule", "work", "general"
    val content: String = "",
    val importance: String = "normal", // "normal", "high", "core"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
