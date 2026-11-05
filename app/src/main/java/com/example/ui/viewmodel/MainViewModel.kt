package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiService
import com.example.data.model.ChatMessage
import com.example.data.model.Teaching
import com.example.data.repository.ChatRepository
import com.example.data.repository.TeachingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab {
    ASSISTANT,
    TEACH,
    STUDIO
}

enum class ChatPersona(val displayName: String, val roleInstruction: String) {
    ASSISTANT("Personal AI", "You are TeachAI, an attentive, adaptive personal companion. Adapt directly to the user's habits and communication style."),
    CODER("Code Guru", "You are a world-class senior software architect and Kotlin/Android engineer. Provide pristine code, best practices, and elegant explanations."),
    TUTOR("Socratic Tutor", "You are an inspiring educator and tutor. Guide the user with step-by-step clarity, real-world examples, and insightful follow-up questions."),
    CREATIVE("Creative Spark", "You are a creative writer, poet, and visual visionary. Express yourself with evocative imagery, rich vocabulary, and artistic flair."),
    EXECUTIVE("Executive Brief", "You are an executive chief of staff. Deliver razor-sharp, actionable, bulleted executive summaries with zero fluff.")
}

class MainViewModel(
    private val teachingRepository: TeachingRepository,
    private val chatRepository: ChatRepository,
    private val aiService: AiService,
    val currentUserId: String
) : ViewModel() {

    private val _activeTab = MutableStateFlow(MainTab.ASSISTANT)
    val activeTab: StateFlow<MainTab> = _activeTab.asStateFlow()

    private val _selectedPersona = MutableStateFlow(ChatPersona.ASSISTANT)
    val selectedPersona: StateFlow<ChatPersona> = _selectedPersona.asStateFlow()

    private val _isGeneratingAi = MutableStateFlow(false)
    val isGeneratingAi: StateFlow<Boolean> = _isGeneratingAi.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _autoSpeak = MutableStateFlow(true)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    // Attachments
    private val _attachedBitmap = MutableStateFlow<Bitmap?>(null)
    val attachedBitmap: StateFlow<Bitmap?> = _attachedBitmap.asStateFlow()

    private val _attachedFileName = MutableStateFlow<String?>(null)
    val attachedFileName: StateFlow<String?> = _attachedFileName.asStateFlow()

    private val _attachedFileContent = MutableStateFlow<String?>(null)
    val attachedFileContent: StateFlow<String?> = _attachedFileContent.asStateFlow()

    private val _attachedVideoName = MutableStateFlow<String?>(null)
    val attachedVideoName: StateFlow<String?> = _attachedVideoName.asStateFlow()

    // Teach filtering
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    // Studio state
    private val _studioResult = MutableStateFlow<String?>(null)
    val studioResult: StateFlow<String?> = _studioResult.asStateFlow()

    private val _isGeneratingStudio = MutableStateFlow(false)
    val isGeneratingStudio: StateFlow<Boolean> = _isGeneratingStudio.asStateFlow()

    // Real-time Teachings Flow
    val teachings: StateFlow<List<Teaching>> = teachingRepository.observeTeachings(currentUserId)
        .catch { e ->
            Log.e("MainVM", "Error observing teachings", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Real-time Chat Messages Flow
    val messages: StateFlow<List<ChatMessage>> = chatRepository.observeMessages(currentUserId)
        .catch { e ->
            Log.e("MainVM", "Error observing chat messages", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun setActiveTab(tab: MainTab) {
        _activeTab.value = tab
    }

    fun setPersona(persona: ChatPersona) {
        _selectedPersona.value = persona
    }

    fun toggleAutoSpeak() {
        _autoSpeak.value = !_autoSpeak.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun attachBitmap(bitmap: Bitmap) {
        _attachedBitmap.value = bitmap
    }

    fun attachFile(fileName: String, content: String) {
        _attachedFileName.value = fileName
        _attachedFileContent.value = content
    }

    fun attachVideo(videoName: String) {
        _attachedVideoName.value = videoName
    }

    fun clearAttachments() {
        _attachedBitmap.value = null
        _attachedFileName.value = null
        _attachedFileContent.value = null
        _attachedVideoName.value = null
    }

    fun clearError() {
        _aiError.value = null
    }

    fun sendUserMessage(
        text: String,
        onAiGenerated: (String) -> Unit = {}
    ) {
        val trimmed = text.trim()
        val currentBitmap = _attachedBitmap.value
        val currentFileContent = _attachedFileContent.value
        val currentFileName = _attachedFileName.value
        val currentVideoName = _attachedVideoName.value

        if (trimmed.isBlank() && currentBitmap == null && currentFileContent == null && currentVideoName == null) {
            return
        }

        viewModelScope.launch {
            _isGeneratingAi.value = true
            _aiError.value = null

            val mediaType = when {
                currentBitmap != null -> "image"
                currentVideoName != null -> "video"
                currentFileName != null -> "file"
                else -> null
            }
            val mediaUrl = currentFileName ?: currentVideoName ?: if (currentBitmap != null) "camera_or_gallery_photo" else null

            // 1. Persist user message to Firestore
            val userMsgContent = if (trimmed.isNotBlank()) trimmed else "(Sent attached $mediaType)"
            chatRepository.addMessage(
                userId = currentUserId,
                role = "user",
                content = userMsgContent,
                mediaType = mediaType,
                mediaUrl = mediaUrl
            )

            // Clear attachments from input bar
            clearAttachments()

            // 2. Query Gemini with personalized teachings and multi-turn history
            val currentTeachings = teachings.value
            val currentHistory = messages.value
            val persona = _selectedPersona.value

            val result = aiService.generateResponse(
                prompt = userMsgContent,
                teachings = currentTeachings,
                bitmap = currentBitmap,
                fileContext = currentFileContent,
                conversationHistory = currentHistory,
                personaInstruction = persona.roleInstruction
            )

            _isGeneratingAi.value = false

            result.fold(
                onSuccess = { reply ->
                    // 3. Persist model reply to Firestore
                    chatRepository.addMessage(
                        userId = currentUserId,
                        role = "model",
                        content = reply
                    )
                    onAiGenerated(reply)
                },
                onFailure = { error ->
                    Log.e("MainVM", "AI generation failed", error)
                    _aiError.value = error.localizedMessage ?: "Failed to generate AI response. Please check network/app check."
                }
            )
        }
    }

    fun teach(category: String, content: String, importance: String = "normal", onComplete: (Boolean) -> Unit = {}) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            val result = teachingRepository.createTeaching(
                userId = currentUserId,
                category = category,
                content = trimmed,
                importance = importance
            )
            onComplete(result.isSuccess)
        }
    }

    fun updateTeaching(teaching: Teaching, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = teachingRepository.updateTeaching(currentUserId, teaching)
            onComplete(result.isSuccess)
        }
    }

    fun deleteTeaching(teachingId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = teachingRepository.deleteTeaching(currentUserId, teachingId)
            onComplete(result.isSuccess)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(currentUserId, messageId)
        }
    }

    fun clearAllMessages() {
        val currentMsgs = messages.value
        viewModelScope.launch {
            currentMsgs.forEach { msg ->
                if (msg.id.isNotBlank()) {
                    chatRepository.deleteMessage(currentUserId, msg.id)
                }
            }
        }
    }

    fun generateStudioCreation(mediaType: String, prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _isGeneratingStudio.value = true
            _studioResult.value = null
            val result = aiService.generateCreativeMediaConcept(mediaType, prompt, teachings.value)
            _isGeneratingStudio.value = false
            result.fold(
                onSuccess = { _studioResult.value = it },
                onFailure = { _aiError.value = it.localizedMessage ?: "Creative generation failed" }
            )
        }
    }
}
