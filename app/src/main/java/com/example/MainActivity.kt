package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.GeminiCopilotService
import com.example.ai.configureAppCheck
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.data.remote.GeoNexoRepository
import com.example.ui.screens.GeoNexoHubScreen
import com.example.ui.state.GeminiChatViewModel
import com.example.ui.state.GeoNexoViewModel
import com.example.ui.theme.GeoNexoTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    setContent { GeoNexoAppNavigation() }
  }
}

@Composable
fun GeoNexoAppNavigation() {
  val auth = Firebase.auth
  val currentUser by
    auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  var isDemoMode by remember { mutableStateOf(false) }

  LaunchedEffect(currentUser) {
    if (currentUser != null) {
      isDemoMode = false
    }
  }

  val user = currentUser
  if (user == null && !isDemoMode) {
    GeoNexoTheme(darkTheme = isSystemInDarkTheme(), highContrastOutdoor = false) {
      AuthScreen(
        onAuthSuccess = { isDemoMode = false },
        onEnterDemoMode = { isDemoMode = true },
      )
    }
  } else {
    val isAuthenticated = user != null
    val sessionKey = if (isAuthenticated) user!!.uid else "demo_local_user"
    key(sessionKey) {
      val viewModel: GeoNexoViewModel =
        viewModel(
          key = sessionKey,
          factory =
            viewModelFactory {
              initializer {
                if (isAuthenticated) {
                  val app =
                    checkNotNull(this[APPLICATION_KEY]) {
                      "APPLICATION_KEY missing from CreationExtras"
                    }
                  val databaseId = app.getString(R.string.firestore_database_id)
                  val db = FirebaseFirestore.getInstance(databaseId)
                  GeoNexoViewModel(
                    repository = GeoNexoRepository(db = db, auth = auth),
                    db = db,
                    currentUserId = sessionKey,
                    isDemoMode = false,
                  )
                } else {
                  GeoNexoViewModel(
                    repository = null,
                    db = null,
                    currentUserId = sessionKey,
                    isDemoMode = true,
                  )
                }
              }
            },
        )
      val geminiViewModel: GeminiChatViewModel =
        viewModel(
          key = "gemini_$sessionKey",
          factory =
            viewModelFactory {
              initializer {
                val app =
                  checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                  }
                GeminiChatViewModel(geminiService = GeminiCopilotService(app.applicationContext))
              }
            },
        )
      val profile by viewModel.userProfile.collectAsStateWithLifecycle()
      GeoNexoTheme(
        darkTheme = profile?.darkMode ?: isSystemInDarkTheme(),
        highContrastOutdoor = profile?.highContrastMode ?: false,
      ) {
        GeoNexoHubScreen(
          viewModel = viewModel,
          geminiViewModel = geminiViewModel,
          currentUserId = sessionKey,
          isAuthenticated = isAuthenticated,
          onExitDemoMode = { isDemoMode = false },
          onSignedOut = { isDemoMode = false },
        )
      }
    }
  }
}
