package com.example.ui.state

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ChannelContextSnapshot
import com.example.ai.GeminiChatTurn
import com.example.ai.GeminiCopilotService
import com.example.data.model.GeoMarker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AiStreamState {
  data object Idle : AiStreamState
  data object Loading : AiStreamState
  data class Streaming(val partialText: String) : AiStreamState
  data class Error(val message: String) : AiStreamState
}

/**
 * ViewModel dedicado a gestionar la comunicación en streaming y multimodal con Gemini
 * a través de Firebase AI Logic SDK (`Firebase.ai`), manteniendo el historial por canal
 * y propagando errores reales hacia la interfaz.
 */
class GeminiChatViewModel(
  private val geminiService: GeminiCopilotService,
) : ViewModel() {

  private val _activeChannelId = MutableStateFlow<String?>(null)
  private val _historyByChannel = MutableStateFlow<Map<String, List<GeminiChatTurn>>>(emptyMap())
  private val _aiStreamState = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
  val aiStreamState: StateFlow<AiStreamState> = _aiStreamState.asStateFlow()

  private var activeInferenceJob: Job? = null

  val activeChannelTurns: StateFlow<List<GeminiChatTurn>> =
    combine(_activeChannelId, _historyByChannel) { channelId, historyMap ->
        if (channelId == null) emptyList() else historyMap[channelId].orEmpty()
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  fun setActiveChannel(channelId: String?) {
    if (_activeChannelId.value != channelId) {
      _activeChannelId.value = channelId
      if (activeInferenceJob?.isActive != true) {
        _aiStreamState.value = AiStreamState.Idle
      }
    }
  }

  fun sendMessage(
    prompt: String,
    contextSnapshot: ChannelContextSnapshot,
    imageUri: Uri? = null,
    imageBytes: ByteArray? = null,
    externalDocContext: String? = null,
  ) {
    val cleanPrompt = prompt.trim()
    if (cleanPrompt.isEmpty()) return

    val channelId = contextSnapshot.channel.id
    _activeChannelId.value = channelId

    val currentHistory = _historyByChannel.value[channelId].orEmpty()
    val userTurn =
      GeminiChatTurn(
        id = UUID.randomUUID().toString(),
        isUser = true,
        text = cleanPrompt,
        hasImageAttachment = imageUri != null || imageBytes != null,
      )

    _historyByChannel.update { map ->
      map + (channelId to (currentHistory + userTurn))
    }

    activeInferenceJob?.cancel()
    activeInferenceJob =
      viewModelScope.launch {
        _aiStreamState.value = AiStreamState.Loading
        val accumulated = StringBuilder()
        try {
          geminiService
            .streamResponse(
              userPrompt = cleanPrompt,
              contextSnapshot = contextSnapshot,
              history = currentHistory,
              attachedImageUri = imageUri,
              attachedImageBytes = imageBytes,
              externalDocContext = externalDocContext,
            )
            .collect { chunk ->
              accumulated.append(chunk)
              _aiStreamState.value = AiStreamState.Streaming(accumulated.toString())
            }

          val finalText = accumulated.toString().trim()
          if (finalText.isEmpty()) {
            _aiStreamState.value = AiStreamState.Error("Gemini devolvió una respuesta vacía.")
            return@launch
          }

          val suggestedAction =
            geminiService.extractSuggestedAction(
              userPrompt = cleanPrompt,
              assistantResponse = finalText,
              contextSnapshot = contextSnapshot,
            )

          val modelTurn =
            GeminiChatTurn(
              id = UUID.randomUUID().toString(),
              isUser = false,
              text = finalText,
              suggestedAction = suggestedAction,
            )

          _historyByChannel.update { map ->
            val list = map[channelId].orEmpty()
            map + (channelId to (list + modelTurn))
          }
          _aiStreamState.value = AiStreamState.Idle
        } catch (e: TimeoutCancellationException) {
          Log.e(TAG, "Gemini request timed out", e)
          _aiStreamState.value =
            AiStreamState.Error("La consulta a Gemini excedió el tiempo límite de 60 segundos.")
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Gemini AI generation failed", e)
          _aiStreamState.value =
            AiStreamState.Error(e.localizedMessage ?: "No se pudo completar la consulta a Gemini.")
        }
      }
  }

  fun summarizeChannel(contextSnapshot: ChannelContextSnapshot) {
    sendMessage(
      prompt =
        "Resume la situación actual de este canal combinando mensajes recientes, estado de marcadores en el mapa y pendientes de la bitácora.",
      contextSnapshot = contextSnapshot,
    )
  }

  fun analyzeMarker(marker: GeoMarker, contextSnapshot: ChannelContextSnapshot) {
    sendMessage(
      prompt =
        "Analiza el marcador '${marker.title}' (${marker.resolvedCategory().labelEs}, estado ${marker.resolvedStatus().labelEs}, notas: '${marker.notes}'). Sugiere pasos operativos y si conviene enviar un aviso al equipo.",
      contextSnapshot = contextSnapshot,
    )
  }

  fun analyzeGoogleDoc(
    docTitle: String,
    docUri: String,
    docContentOrSnippet: String,
    contextSnapshot: ChannelContextSnapshot,
  ) {
    val docContext =
      buildString {
        append("Título: ").append(docTitle).append('\n')
        append("Enlace oficial: ").append(docUri).append('\n')
        append("Contenido / Extracto:\n")
        append(docContentOrSnippet.ifBlank { "Sin texto extraído; evalúa según el título y el contexto del canal." })
      }
    sendMessage(
      prompt =
        "Analiza el documento de Google Docs '$docTitle' adjunto en el contexto del canal. Resume los puntos técnicos clave, riesgos en campo y propone acciones concretas para el equipo.",
      contextSnapshot = contextSnapshot,
      externalDocContext = docContext,
    )
  }

  fun assistDocument(contextSnapshot: ChannelContextSnapshot) {
    sendMessage(
      prompt =
        "Revisa la bitácora actual del canal junto con los marcadores abiertos y redacta una actualización estructurada con pendientes accionables lista para agregar a la bitácora.",
      contextSnapshot = contextSnapshot,
    )
  }

  fun clearHistory(channelId: String? = _activeChannelId.value) {
    activeInferenceJob?.cancel()
    if (channelId != null) {
      _historyByChannel.update { map -> map - channelId }
    }
    _aiStreamState.value = AiStreamState.Idle
  }

  private companion object {
    const val TAG = "GeminiChatVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
