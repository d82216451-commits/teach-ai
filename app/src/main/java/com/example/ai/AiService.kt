package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.Teaching
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

class AiService {

    // Lazy initialization per Firebase AI Logic skill mandate
    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    suspend fun generateResponse(
        prompt: String,
        teachings: List<Teaching>,
        bitmap: Bitmap? = null,
        fileContext: String? = null,
        conversationHistory: List<ChatMessage> = emptyList(),
        personaInstruction: String? = null
    ): Result<String> {
        return try {
            val systemContext = buildSystemContext(teachings, personaInstruction)
            val fullPrompt = buildString {
                append(systemContext)

                // Multi-turn conversational memory: include recent turns for conversational continuity
                if (conversationHistory.isNotEmpty()) {
                    append("\n\n[RECENT CONVERSATION HISTORY]:")
                    val recentTurns = conversationHistory.takeLast(6)
                    recentTurns.forEach { msg ->
                        val speaker = if (msg.role == "user") "User" else "TeachAI"
                        append("\n$speaker: ${msg.content.take(400)}")
                    }
                }

                append("\n\nCurrent User Message: ")
                append(prompt)
                if (!fileContext.isNullOrBlank()) {
                    append("\n\nAttached Document/Data Content:\n")
                    append(fileContext)
                }
            }

            val response = withTimeout(60_000L) {
                if (bitmap != null) {
                    model.generateContent(
                        content {
                            text(fullPrompt)
                            image(bitmap)
                        }
                    )
                } else {
                    model.generateContent(fullPrompt)
                }
            }

            val reply = response.text.orEmpty().trim()
            if (reply.isNotBlank()) {
                Result.success(reply)
            } else {
                Result.failure(IllegalStateException("AI model returned an empty response."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AiService", "Generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateCreativeMediaConcept(
        mediaType: String, // "image" or "video"
        prompt: String,
        teachings: List<Teaching>
    ): Result<String> {
        return try {
            val systemContext = buildSystemContext(teachings)
            val instruction = if (mediaType == "video") {
                """
                You are an award-winning creative AI video director and storyboard architect.
                Generate a rich, cinematic video concept for: "$prompt"
                Provide:
                1. 🎬 Title & Logline
                2. 🎨 Visual Style, Aspect Ratio, Color Palette & Lighting
                3. ⏱️ Scene-by-scene Timeline & Storyboard (Shot Type, Camera Movement, Action, Sound Design)
                4. 💡 Generative AI Prompts (Veo / Runway / Sora optimized prompts for each scene)
                Keep it inspiring, vivid, and highly actionable.
                """.trimIndent()
            } else {
                """
                You are a visionary digital artist and prompt craft master.
                Generate a stunning generative image concept and rendering specification for: "$prompt"
                Provide:
                1. 🖼️ Artwork Title & Core Theme
                2. 🔍 Detailed Visual Composition (foreground, lighting, textures, depth)
                3. 🎨 Color Harmony & Mood
                4. ✨ Optimized Diffusion Prompt with positive and negative keywords
                """.trimIndent()
            }

            val fullPrompt = "$systemContext\n\n$instruction"
            val response = withTimeout(60_000L) {
                model.generateContent(fullPrompt)
            }
            val reply = response.text.orEmpty().trim()
            if (reply.isNotBlank()) {
                Result.success(reply)
            } else {
                Result.failure(IllegalStateException("Model returned an empty media generation response."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AiService", "Media concept generation failed", e)
            Result.failure(e)
        }
    }

    private fun buildSystemContext(teachings: List<Teaching>, personaInstruction: String? = null): String {
        val builder = StringBuilder()
        if (!personaInstruction.isNullOrBlank()) {
            builder.append(personaInstruction)
            builder.append("\n\n")
        }
        builder.append("You are TeachAI, an ultra-intelligent, helpful, adaptive personal AI companion with encyclopedic knowledge of the world, science, code, philosophy, arts, and everyday problem-solving.")
        builder.append("\nYour voice is articulate, warm, and natural.")

        if (teachings.isNotEmpty()) {
            builder.append("\n\n[USER PERSONALIZATION & TAUGHT MEMORIES]:")
            builder.append("\nThe user has explicitly taught you the following personal facts, rules, and preferences. You MUST ALWAYS follow them strictly and tailor your answers to reflect them:")
            teachings.forEach { teaching ->
                builder.append("\n- [${teaching.category.uppercase()} / Priority: ${teaching.importance.uppercase()}]: ${teaching.content}")
            }
        }
        return builder.toString()
    }
}
