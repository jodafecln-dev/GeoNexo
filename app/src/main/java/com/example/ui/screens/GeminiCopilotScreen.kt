package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.ai.GeminiChatTurn
import com.example.ai.GeminiSuggestedAction
import com.example.auth.onGoogleSignInClicked
import com.example.data.model.Channel
import com.example.ui.state.AiStreamState
import com.example.ui.theme.GeoAiGradientEnd
import com.example.ui.theme.GeoAiGradientStart

@Composable
fun GeminiCopilotScreen(
  isAuthenticated: Boolean,
  channel: Channel?,
  markersCount: Int,
  messagesCount: Int,
  presenceCount: Int,
  turns: List<GeminiChatTurn>,
  streamState: AiStreamState,
  onSendPrompt: (prompt: String, imageUri: Uri?) -> Unit,
  onApplyAction: (GeminiSuggestedAction) -> Unit,
  onPublishTextToChat: (String) -> Unit,
  onAppendTextToDoc: (String) -> Unit,
  onClearHistory: () -> Unit,
) {
  if (!isAuthenticated) {
    GeminiSignInGateCard()
    return
  }

  if (channel == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Selecciona un canal para usar el Copiloto Gemini.")
    }
    return
  }

  var promptDraft by remember { mutableStateOf("") }
  var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
      onResult = { uri -> selectedPhotoUri = uri },
    )

  val isBusy = streamState is AiStreamState.Loading || streamState is AiStreamState.Streaming
  val listState = rememberLazyListState()

  LaunchedEffect(turns.size, streamState) {
    val targetIndex = (turns.size + if (isBusy) 1 else 0) - 1
    if (targetIndex >= 0) {
      listState.animateScrollToItem(targetIndex)
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Live Context Ribbon with modern Spatial AI gradient accent
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Box(
            modifier =
              Modifier.size(34.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(GeoAiGradientStart, GeoAiGradientEnd))),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(18.dp),
            )
          }
          Spacer(Modifier.width(10.dp))
          Column {
            Text(
              text = "Copiloto Gemini · #${channel.name}",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = "$markersCount puntos · $messagesCount msgs · $presenceCount GPS · Bitácora",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (turns.isNotEmpty() && !isBusy) {
          IconButton(
            onClick = onClearHistory,
            modifier = Modifier.testTag("clear_gemini_history_btn"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteSweep,
              contentDescription = "Limpiar conversación con Gemini",
            )
          }
        }
      }
    }

    // Quick Action Prompts
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val quickPrompts =
        listOf(
          "Resume mensajes, incidencias y pendientes del canal",
          "¿Qué riesgos ves en los marcadores abiertos?",
          "Redacta reporte de turno listo para la bitácora",
          "Redacta aviso breve para enviar al chat del equipo",
        )
      quickPrompts.forEachIndexed { idx, qp ->
        AssistChip(
          onClick = { if (!isBusy) onSendPrompt(qp, null) },
          enabled = !isBusy,
          shape = CircleShape,
          colors =
            AssistChipDefaults.assistChipColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
          label = { Text(qp) },
          modifier = Modifier.testTag("gemini_quick_prompt_$idx"),
        )
      }
    }

    // Error Banner (Transparent error propagation, never mocked)
    if (streamState is AiStreamState.Error) {
      Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = streamState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    }

    // Chat Stream List
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      if (turns.isEmpty() && !isBusy) {
        Column(
          modifier = Modifier.align(Alignment.Center).padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(72.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(GeoAiGradientStart, GeoAiGradientEnd))),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(34.dp),
            )
          }
          Text(
            text = "Asistente Operativo con Gemini",
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text =
              "Analiza mensajes, marcadores del mapa, ubicación GPS o fotos de campo. Gemini puede proponer marcadores y actualizar la bitácora en un toque.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          state = listState,
          contentPadding = PaddingValues(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(turns, key = { it.id }) { turn ->
            GeminiTurnCard(
              turn = turn,
              onApplyAction = onApplyAction,
              onPublishTextToChat = onPublishTextToChat,
              onAppendTextToDoc = onAppendTextToDoc,
            )
          }

          if (streamState is AiStreamState.Loading) {
            item(key = "gemini_loading") {
              Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border =
                  BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(listOf(GeoAiGradientStart, GeoAiGradientEnd)),
                  ),
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                  Spacer(Modifier.width(10.dp))
                  Text("Analizando contexto del canal...", style = MaterialTheme.typography.bodyMedium)
                }
              }
            }
          } else if (streamState is AiStreamState.Streaming) {
            item(key = "gemini_streaming") {
              Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border =
                  BorderStroke(
                    1.2.dp,
                    Brush.horizontalGradient(listOf(GeoAiGradientStart, GeoAiGradientEnd)),
                  ),
                modifier = Modifier.fillMaxWidth(0.94f),
              ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    text = "Gemini 3.8 Flash",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = streamState.partialText,
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
              }
            }
          }
        }
      }
    }

    if (selectedPhotoUri != null) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AssistChip(
          onClick = { selectedPhotoUri = null },
          label = { Text("Foto de campo lista para analizar") },
          leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp)) },
          trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Quitar foto", modifier = Modifier.size(14.dp)) },
        )
      }
    }

    // Modern Pill Composer
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          enabled = !isBusy,
          modifier = Modifier.testTag("gemini_attach_photo_btn"),
        ) {
          Icon(
            imageVector = Icons.Filled.AddPhotoAlternate,
            contentDescription = "Analizar foto con Gemini",
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
        OutlinedTextField(
          value = promptDraft,
          onValueChange = { promptDraft = it },
          placeholder = { Text("Pregunta a Gemini sobre #${channel.name}...") },
          maxLines = 3,
          shape = RoundedCornerShape(24.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
          modifier = Modifier.weight(1f).testTag("gemini_prompt_input"),
        )
        Spacer(Modifier.width(6.dp))
        val canSend = !isBusy && (promptDraft.trim().isNotEmpty() || selectedPhotoUri != null)
        IconButton(
          onClick = {
            val clean = promptDraft.trim()
            if (clean.isNotEmpty() || selectedPhotoUri != null) {
              val finalPrompt =
                clean.ifEmpty { "Analiza esta imagen de campo, identifica hallazgos o riesgos y dime si debo crear un marcador." }
              onSendPrompt(finalPrompt, selectedPhotoUri)
              promptDraft = ""
              selectedPhotoUri = null
            }
          },
          enabled = canSend,
          modifier = Modifier.size(48.dp).testTag("gemini_send_button"),
        ) {
          Box(
            modifier =
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(
                  if (canSend) Brush.linearGradient(listOf(GeoAiGradientStart, GeoAiGradientEnd))
                  else Brush.linearGradient(
                    listOf(
                      MaterialTheme.colorScheme.surfaceContainerHigh,
                      MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                  )
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Enviar consulta a Gemini",
              tint =
                if (canSend) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GeminiTurnCard(
  turn: GeminiChatTurn,
  onApplyAction: (GeminiSuggestedAction) -> Unit,
  onPublishTextToChat: (String) -> Unit,
  onAppendTextToDoc: (String) -> Unit,
) {
  val shape =
    RoundedCornerShape(
      topStart = 18.dp,
      topEnd = 18.dp,
      bottomStart = if (turn.isUser) 18.dp else 4.dp,
      bottomEnd = if (turn.isUser) 4.dp else 18.dp,
    )

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (turn.isUser) Alignment.End else Alignment.Start,
  ) {
    Surface(
      shape = shape,
      color =
        if (turn.isUser) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLowest,
      border =
        if (turn.isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth(0.94f),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!turn.isUser) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
          }
          Text(
            text = if (turn.isUser) "Tú" else "Gemini Copiloto",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (turn.isUser) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
          )
        }

        if (turn.hasImageAttachment) {
          Text(
            text = "📷 Foto de campo adjuntada",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Text(
          text = turn.text,
          style = MaterialTheme.typography.bodyMedium,
          color = if (turn.isUser) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        )

        if (!turn.isUser) {
          val action = turn.suggestedAction
          if (action != null) {
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                val (icon, title, btnText) =
                  when (action) {
                    is GeminiSuggestedAction.CreateMarker ->
                      Triple(
                        Icons.Filled.AddLocationAlt,
                        "Sugerencia: Crear marcador \"${action.title}\" (${action.category.labelEs})",
                        "Crear en Mapa",
                      )
                    is GeminiSuggestedAction.AppendToDoc ->
                      Triple(
                        Icons.Filled.Description,
                        "Sugerencia: Agregar resumen a la Bitácora",
                        "Guardar en Bitácora",
                      )
                    is GeminiSuggestedAction.PostToChannelChat ->
                      Triple(
                        Icons.Filled.Forum,
                        "Sugerencia: Publicar aviso en el Chat del canal",
                        "Publicar en Chat",
                      )
                  }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                  )
                }
                Button(
                  onClick = { onApplyAction(action) },
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                  modifier = Modifier.fillMaxWidth().height(44.dp).testTag("gemini_apply_suggested_action"),
                ) {
                  Text(btnText)
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { onPublishTextToChat(turn.text) },
              modifier = Modifier.weight(1f).testTag("gemini_push_chat_${turn.id}"),
            ) {
              Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(Modifier.width(4.dp))
              Text("Al Chat", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
              onClick = { onAppendTextToDoc(turn.text) },
              modifier = Modifier.weight(1f).testTag("gemini_push_doc_${turn.id}"),
            ) {
              Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(Modifier.width(4.dp))
              Text("A Bitácora", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GeminiSignInGateCard() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isSigningIn by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  Box(
    modifier = Modifier.fillMaxSize().padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
      border =
        BorderStroke(
          1.2.dp,
          Brush.linearGradient(listOf(GeoAiGradientStart, GeoAiGradientEnd)),
        ),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier =
            Modifier.size(64.dp)
              .clip(CircleShape)
              .background(Brush.linearGradient(listOf(GeoAiGradientStart, GeoAiGradientEnd))),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp),
          )
        }
        Text(
          text = "Activa Gemini IA con Google",
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
        )
        Text(
          text =
            "Estás explorando en Modo Prueba Local (chat, mapa y bitácora funcionan sin login). Para usar el Copiloto Gemini IA y sincronizar canales en tiempo real con tu equipo a distancia, inicia sesión con Google.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        if (errorMsg != null) {
          Surface(
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorMsg!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
        Button(
          onClick = {
            errorMsg = null
            isSigningIn = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = { isSigningIn = false },
              onAuthError = { msg ->
                isSigningIn = false
                errorMsg = msg
              },
              scope = scope,
              onAuthCancelled = { isSigningIn = false },
            )
          },
          enabled = !isSigningIn,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("gemini_auth_signin_btn"),
        ) {
          if (isSigningIn) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
          } else {
            Icon(Icons.Filled.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Iniciar sesión con Google para usar IA", fontWeight = FontWeight.Bold)
          }
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = "Tus canales locales siguen disponibles sin cuenta",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
