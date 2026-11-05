package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.AiService
import com.example.data.repository.ChatRepository
import com.example.data.repository.TeachingRepository
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.theme.TeachAiTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.util.configureAppCheck
import com.example.voice.SpeechRecognizerHelper
import com.example.voice.TextToSpeechHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            TeachAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        SignInScreen(
            onAuthSuccess = { currentUser = auth.currentUser }
        )
    } else {
        val context = LocalContext.current
        val databaseId = remember(context) {
            context.getString(R.string.firestore_database_id)
        }

        val viewModel: MainViewModel = viewModel(
            key = user.uid,
            factory = viewModelFactory {
                initializer {
                    val db = FirebaseFirestore.getInstance(databaseId)
                    val teachingRepo = TeachingRepository(db)
                    val chatRepo = ChatRepository(db)
                    val aiService = AiService()
                    MainViewModel(teachingRepo, chatRepo, aiService, user.uid)
                }
            }
        )

        val ttsHelper = remember(context) { TextToSpeechHelper(context) }
        val speechHelper = remember(context) { SpeechRecognizerHelper(context) }

        DisposableEffect(Unit) {
            onDispose {
                ttsHelper.shutdown()
                speechHelper.stopListening()
            }
        }

        MainScreen(
            viewModel = viewModel,
            ttsHelper = ttsHelper,
            speechHelper = speechHelper,
            currentUser = user
        )
    }
}
