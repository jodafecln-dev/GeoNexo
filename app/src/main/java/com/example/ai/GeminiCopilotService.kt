package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.Channel
import com.example.data.model.ChannelMessage
import com.example.data.model.ChannelPresence
import com.example.data.model.GeoMarker
import com.example.data.model.MarkerCategory
import com.example.data.model.MarkerStatus
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class ChannelContextSnapshot(
  val channel: Channel,
  val recentMessages: List<ChannelMessage>,
  val markers: List<GeoMarker>,
  val activePresence: List<ChannelPresence>,
)

data class GeminiChatTurn(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val hasImageAttachment: Boolean = false,
  val suggestedAction: GeminiSuggestedAction? = null,
)

sealed interface GeminiSuggestedAction {
  data class CreateMarker(
    val title: String,
    val notes: String,
    val category: MarkerCategory,
    val status: MarkerStatus,
  ) : GeminiSuggestedAction

  data class AppendToDoc(
    val sectionText: String,
  ) : GeminiSuggestedAction

  data class PostToChannelChat(
    val messageBody: String,
  ) : GeminiSuggestedAction
}

class GeminiCopilotService(private val appContext: Context) {

  private val chatModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      systemInstruction =
        content {
          text(
            "Eres el Copiloto Operativo de GeoNexo, asistente experto para equipos que combinan oficina y trabajo en campo (construcción, logística, topografía, servicios técnicos). " +
              "Responde siempre en español claro, concreto y accionable. Usa el contexto en vivo del canal (mensajes, marcadores del mapa, ubicación del equipo y bitácora) para responder con precisión. " +
              "No inventes datos fuera del contexto proporcionado; si falta información, dilo brevemente."
          )
        },
    )
  }

  private val actionExtractorModel by lazy {
    val actionSchema =
      Schema.obj(
        properties =
          mapOf(
            "actionType" to
              Schema.enumeration(
                values = listOf("NONE", "CREATE_MARKER", "APPEND_DOC", "POST_CHAT"),
                description = "Tipo de acción operativa recomendada en GeoNexo",
              ),
            "markerTitle" to Schema.string(description = "Título corto del marcador si actionType es CREATE_MARKER", nullable = true),
            "markerNotes" to Schema.string(description = "Notas de campo del marcador", nullable = true),
            "markerCategory" to
              Schema.enumeration(
                values = listOf("INCIDENT", "PROGRESS", "CHECKPOINT", "DELIVERY", "ROUTE"),
                description = "Categoría del marcador",
              ),
            "markerStatus" to
              Schema.enumeration(
                values = listOf("OPEN", "IN_PROGRESS", "RESOLVED"),
                description = "Estado operativo del marcador",
              ),
            "docAppendText" to Schema.string(description = "Texto breve para agregar a la bitácora si actionType es APPEND_DOC", nullable = true),
            "chatMessageText" to Schema.string(description = "Mensaje listo para publicar al equipo si actionType es POST_CHAT", nullable = true),
          ),
        optionalProperties = listOf("markerTitle", "markerNotes", "docAppendText", "chatMessageText"),
      )

    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = actionSchema
        },
    )
  }

  fun streamResponse(
    userPrompt: String,
    contextSnapshot: ChannelContextSnapshot,
    history: List<GeminiChatTurn>,
    attachedImageUri: Uri?,
  ): Flow<String> = flow {
    val contextBlock = buildContextPrompt(contextSnapshot)
    val imageBitmap = attachedImageUri?.let { loadScaledBitmap(it) }

    val historyContents: List<Content> =
      history.takeLast(8).map { turn ->
        content(role = if (turn.isUser) "user" else "model") { text(turn.text) }
      }

    val promptContent =
      content(role = "user") {
        text("$contextBlock\n\nCONSULTA DEL USUARIO:\n$userPrompt")
        if (imageBitmap != null) {
          image(imageBitmap)
        }
      }

    val fullInput = historyContents + promptContent
    withTimeout(60_000L) {
      chatModel.generateContentStream(fullInput).collect { chunk ->
        val chunkText = chunk.text
        if (!chunkText.isNullOrEmpty()) {
          emit(chunkText)
        }
      }
    }
  }

  suspend fun extractSuggestedAction(
    userPrompt: String,
    assistantResponse: String,
    contextSnapshot: ChannelContextSnapshot,
  ): GeminiSuggestedAction? {
    return try {
      val extractionPrompt =
        """
        Analiza la petición del usuario y la respuesta del asistente en el canal #${contextSnapshot.channel.name}.
        Determina si conviene proponer UNA acción directa de un clic en GeoNexo:
        - CREATE_MARKER: si se reporta una incidencia, entrega, avance o punto físico que aún no es marcador o requiere registrarse en el mapa.
        - APPEND_DOC: si se generó un resumen, acuerdo o checklist ideal para guardar en la bitácora del canal.
        - POST_CHAT: si se redactó un aviso/comunicado listo para enviar al chat del equipo.
        - NONE: si fue solo una consulta informativa.

        Petición: $userPrompt
        Respuesta: $assistantResponse
        """.trimIndent()

      val response =
        withTimeout(30_000L) {
          actionExtractorModel.generateContent(extractionPrompt)
        }
      val rawJson = response.text ?: return null
      val json = JSONObject(rawJson)
      when (json.optString("actionType", "NONE")) {
        "CREATE_MARKER" -> {
          val title = json.optString("markerTitle").trim()
          if (title.length < 2) null
          else
            GeminiSuggestedAction.CreateMarker(
              title = title.take(80),
              notes = json.optString("markerNotes").trim().take(1000),
              category = MarkerCategory.safeOf(json.optString("markerCategory")),
              status = MarkerStatus.safeOf(json.optString("markerStatus")),
            )
        }
        "APPEND_DOC" -> {
          val text = json.optString("docAppendText").trim()
          if (text.isEmpty()) null else GeminiSuggestedAction.AppendToDoc(text)
        }
        "POST_CHAT" -> {
          val msg = json.optString("chatMessageText").trim()
          if (msg.isEmpty()) null else GeminiSuggestedAction.PostToChannelChat(msg)
        }
        else -> null
      }
    } catch (e: Exception) {
      Log.w(TAG, "Structured action extraction skipped: ${e.message}")
      null
    }
  }

  private fun buildContextPrompt(snapshot: ChannelContextSnapshot): String {
    val ch = snapshot.channel
    val markersSummary =
      if (snapshot.markers.isEmpty()) "Sin marcadores registrados."
      else
        snapshot.markers.take(20).joinToString("\n") { mk ->
          "- [${mk.title}] (${mk.resolvedCategory().labelEs}, ${mk.resolvedStatus().labelEs}) Coords(${
            String.format("%.4f", mk.lat)
          }, ${String.format("%.4f", mk.lng)}) Nota: ${mk.notes.ifBlank { "ninguna" }}"
        }

    val messagesSummary =
      if (snapshot.recentMessages.isEmpty()) "Sin mensajes recientes."
      else
        snapshot.recentMessages.takeLast(15).joinToString("\n") { msg ->
          val pinInfo = msg.linkedMarkerTitle?.let { " [Vinculado a #$it]" } ?: ""
          "- ${msg.senderName}: ${msg.body}$pinInfo"
        }

    val presenceSummary =
      if (snapshot.activePresence.isEmpty()) "Nadie compartiendo GPS en vivo ahora."
      else
        snapshot.activePresence.joinToString("\n") { p ->
          "- ${p.displayName} en (${String.format("%.4f", p.lat)}, ${String.format("%.4f", p.lng)}), precisión ±${p.accuracyMeters.toInt()}m"
        }

    return """
      CONTEXTO EN VIVO DEL CANAL GEONEXO
      Canal: #${ch.name} (${ch.resolvedType().labelEs})
      Objetivo: ${ch.topic.ifBlank { "No especificado" }}

      BITÁCORA ANCLADA (${ch.pinnedDocTitle}):
      ${ch.pinnedDocBody.take(2500).ifBlank { "Vacía" }}

      MARCADORES EN MAPA COMPARTIDO (${snapshot.markers.size}):
      $markersSummary

      EQUIPO CON GPS EN VIVO:
      $presenceSummary

      MENSAJES RECIENTES DEL EQUIPO:
      $messagesSummary
    """.trimIndent()
  }

  private suspend fun loadScaledBitmap(uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
      try {
        val rawBytes =
          appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return@withContext null
        val maxDim = 1280
        val scaled =
          if (decoded.width > maxDim || decoded.height > maxDim) {
            val ratio = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(
              decoded,
              (decoded.width * ratio).toInt().coerceAtLeast(1),
              (decoded.height * ratio).toInt().coerceAtLeast(1),
              true,
            )
          } else {
            decoded
          }
        // Verify compressed payload remains well under 20MB inline limit
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val compressedBytes = out.toByteArray()
        BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
      } catch (e: Exception) {
        Log.e(TAG, "Error loading bitmap for Gemini multimodal input", e)
        null
      }
    }

  private companion object {
    const val TAG = "GeminiCopilot"
  }
}
