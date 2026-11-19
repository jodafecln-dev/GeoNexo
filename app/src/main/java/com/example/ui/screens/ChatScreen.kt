package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.Channel
import com.example.data.model.ChannelMessage
import com.example.data.model.GeoMarker
import com.example.data.model.SyncedCollection
import com.example.ui.state.UiState
import com.example.ui.state.WorkspaceUiState
import com.example.ui.theme.GeoAiGradientEnd
import com.example.ui.theme.GeoAiGradientStart
import com.example.util.FieldMediaAndAlerts
import com.example.workspace.GoogleDocSummary
import com.example.workspace.GoogleWorkspaceManager
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChatScreen(
  channel: Channel?,
  currentUserId: String,
  isAuthenticated: Boolean,
  messagesState: UiState<SyncedCollection<ChannelMessage>>,
  markers: List<GeoMarker>,
  workspaceState: WorkspaceUiState,
  activeThreadParentId: String?,
  currentPageLimit: Int,
  onLoadOlderMessages: () -> Unit,
  onOpenThread: (String?) -> Unit,
  onJumpToMarker: (String) -> Unit,
  onSendMessage: (
    body: String,
    parentMessageId: String?,
    linkedMarker: GeoMarker?,
    attachmentLabel: String?,
    attachmentUri: String?,
    attachmentSnippet: String?,
  ) -> Unit,
  onRequestDriveDocs: (String) -> Unit,
  onCreateGoogleDocForChat: (title: String, body: String) -> Unit,
  onSummarizeWithGemini: () -> Unit,
  onAnalyzePhotoWithGemini: (ChannelMessage, ByteArray) -> Unit,
  onAnalyzeGoogleDocWithGemini: (title: String, uri: String, snippet: String?) -> Unit,
  onShowFeedback: (String) -> Unit,
) {
  if (channel == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Selecciona o crea un canal para chatear", style = MaterialTheme.typography.bodyLarge)
    }
    return
  }

  BackHandler(enabled = activeThreadParentId != null) { onOpenThread(null) }

  var draft by remember { mutableStateOf("") }
  var selectedMarker by remember { mutableStateOf<GeoMarker?>(null) }
  var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
  var selectedGoogleDoc by remember { mutableStateOf<GoogleDocSummary?>(null) }
  var showMarkerPicker by remember { mutableStateOf(false) }
  var showGoogleDocPicker by remember { mutableStateOf(false) }
  var previewPhotoPair by remember { mutableStateOf<Pair<ChannelMessage, ByteArray?>?>(null) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
      onResult = { uri ->
        selectedPhotoUri = uri
        if (uri != null) selectedGoogleDoc = null
      },
    )

  Column(modifier = Modifier.fillMaxSize()) {
    if (activeThreadParentId != null) {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = { onOpenThread(null) },
            modifier = Modifier.testTag("close_thread_button"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al canal")
          }
          Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Hilo de conversación",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
          )
        }
      }
    } else {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
              modifier =
                Modifier.size(28.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(15.dp),
              )
            }
            Spacer(Modifier.width(8.dp))
            Text(
              text = "${markers.size} marcadores activos",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border =
              BorderStroke(
                1.dp,
                Brush.horizontalGradient(listOf(GeoAiGradientStart, GeoAiGradientEnd)),
              ),
            modifier =
              Modifier.clickable(onClick = onSummarizeWithGemini)
                .testTag("chat_summarize_gemini_btn"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "Resumir IA",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (messagesState) {
        is UiState.Loading -> {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        is UiState.Error -> {
          Text(
            text = messagesState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
          )
        }
        is UiState.Success -> {
          val allMessages = messagesState.data.items
          val threadCounts =
            remember(allMessages) {
              allMessages
                .mapNotNull { it.parentMessageId }
                .groupingBy { it }
                .eachCount()
            }
          val visibleMessages =
            remember(allMessages, activeThreadParentId) {
              if (activeThreadParentId == null) {
                allMessages.filter { it.parentMessageId == null }
              } else {
                allMessages.filter {
                  it.id == activeThreadParentId || it.parentMessageId == activeThreadParentId
                }
              }
            }

          val listState = rememberLazyListState()
          LaunchedEffect(visibleMessages.size) {
            if (visibleMessages.isNotEmpty()) {
              listState.animateScrollToItem(visibleMessages.lastIndex)
            }
          }

          if (visibleMessages.isEmpty()) {
            Column(
              modifier = Modifier.align(Alignment.Center).padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Filled.PinDrop,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(30.dp),
                )
              }
              Text(
                text = "Sin mensajes aún en #${channel.name}",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "Envía una actualización o vincula un marcador del mapa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(
              state = listState,
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              if (allMessages.size >= currentPageLimit && activeThreadParentId == null) {
                item(key = "load_older_header") {
                  OutlinedButton(
                    onClick = onLoadOlderMessages,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().testTag("load_older_messages_btn"),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.ExpandLess,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Cargar mensajes anteriores")
                  }
                }
              }
              items(visibleMessages, key = { it.id }) { msg ->
                val isSelf = msg.senderId == currentUserId
                val repliesCount = threadCounts[msg.id] ?: 0
                MessageBubbleCard(
                  message = msg,
                  isSelf = isSelf,
                  repliesCount = repliesCount,
                  inThreadMode = activeThreadParentId != null,
                  onReplyInThread = { onOpenThread(msg.id) },
                  onJumpToMarker = onJumpToMarker,
                  onPreviewPhoto = { bytes -> previewPhotoPair = Pair(msg, bytes) },
                  onAnalyzeGoogleDoc = { title, uri, snippet ->
                    onAnalyzeGoogleDocWithGemini(title, uri, snippet)
                  },
                  onShowFeedback = onShowFeedback,
                )
              }
            }
          }
        }
      }
    }

    // Active composer attachments preview
    if (selectedMarker != null || selectedPhotoUri != null || selectedGoogleDoc != null) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (selectedMarker != null) {
          AssistChip(
            onClick = { selectedMarker = null },
            label = { Text("#${selectedMarker!!.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Quitar marcador", modifier = Modifier.size(14.dp)) },
          )
        }
        if (selectedPhotoUri != null) {
          AssistChip(
            onClick = { selectedPhotoUri = null },
            label = { Text("Foto adjunta") },
            leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Quitar foto", modifier = Modifier.size(14.dp)) },
          )
        }
        if (selectedGoogleDoc != null) {
          AssistChip(
            onClick = { selectedGoogleDoc = null },
            label = {
              Text(
                selectedGoogleDoc!!.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Quitar Google Doc", modifier = Modifier.size(14.dp)) },
          )
        }
      }
    }

    // Modern Floating Pill Composer
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = { showMarkerPicker = true },
          modifier = Modifier.testTag("attach_marker_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = "Vincular marcador del mapa",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          modifier = Modifier.testTag("attach_photo_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.AddPhotoAlternate,
            contentDescription = "Adjuntar foto con Photo Picker",
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
        IconButton(
          onClick = { showGoogleDocPicker = true },
          modifier = Modifier.testTag("attach_google_doc_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = "Adjuntar Google Doc",
            tint = MaterialTheme.colorScheme.tertiary,
          )
        }
        OutlinedTextField(
          value = draft,
          onValueChange = { draft = it },
          placeholder = {
            Text(
              if (activeThreadParentId != null) "Responder en hilo..."
              else "Mensaje a #${channel.name}..."
            )
          },
          maxLines = 3,
          shape = RoundedCornerShape(24.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
          modifier = Modifier.weight(1f).testTag("chat_message_input"),
        )
        Spacer(Modifier.width(4.dp))
        val canSend = draft.trim().isNotEmpty() || selectedGoogleDoc != null
        IconButton(
          onClick = {
            val clean =
              draft.trim().ifEmpty {
                selectedGoogleDoc?.let { "Compartió Google Doc: ${it.title}" }.orEmpty()
              }
            if (clean.isNotEmpty()) {
              val doc = selectedGoogleDoc
              onSendMessage(
                clean,
                activeThreadParentId,
                selectedMarker,
                doc?.title ?: if (selectedPhotoUri != null) "Evidencia fotográfica de campo" else null,
                doc?.webViewLink ?: selectedPhotoUri?.toString(),
                doc?.snippet,
              )
              draft = ""
              selectedMarker = null
              selectedPhotoUri = null
              selectedGoogleDoc = null
            }
          },
          enabled = canSend,
          modifier = Modifier.size(48.dp).testTag("send_message_button"),
        ) {
          Box(
            modifier =
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(
                  if (canSend) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Enviar mensaje",
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

  if (showMarkerPicker) {
    AlertDialog(
      onDismissRequest = { showMarkerPicker = false },
      title = { Text("Vincular punto del mapa") },
      text = {
        if (markers.isEmpty()) {
          Text("No hay marcadores creados en este canal todavía.")
        } else {
          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(markers, key = { it.id }) { mk ->
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier =
                  Modifier.fillMaxWidth().clickable {
                    selectedMarker = mk
                    showMarkerPicker = false
                  },
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Spacer(Modifier.width(10.dp))
                  Column {
                    Text(mk.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                      "${mk.resolvedCategory().labelEs} · ${mk.resolvedStatus().labelEs}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showMarkerPicker = false }) { Text("Cerrar") }
      },
    )
  }

  if (previewPhotoPair != null) {
    val (photoMsg, photoBytes) = previewPhotoPair!!
    AlertDialog(
      onDismissRequest = { previewPhotoPair = null },
      title = { Text(photoMsg.senderName) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          AsyncImage(
            model = photoBytes ?: photoMsg.attachmentUri,
            contentDescription = photoMsg.attachmentLabel ?: "Evidencia fotográfica",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(280.dp).clip(MaterialTheme.shapes.medium),
          )
          Text(photoMsg.body, style = MaterialTheme.typography.bodyMedium)
        }
      },
      confirmButton = {
        if (photoBytes != null) {
          Button(
            onClick = {
              previewPhotoPair = null
              onAnalyzePhotoWithGemini(photoMsg, photoBytes)
            },
            modifier = Modifier.testTag("analyze_chat_photo_gemini_btn"),
          ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Analizar con Gemini")
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { previewPhotoPair = null }) { Text("Cerrar") }
      },
    )
  }
  if (showGoogleDocPicker) {
    GoogleDocPickerDialog(
      dialogTitle = "Adjuntar Google Doc al chat",
      defaultCreateTitle = "Reporte — #${channel.name}",
      defaultCreateBody = "Resumen operativo compartido desde #${channel.name}:\n\n",
      isAuthenticated = isAuthenticated,
      workspaceState = workspaceState,
      onRequestAuthorizeAndLoad = onRequestDriveDocs,
      onSelectExistingDoc = { doc ->
        selectedGoogleDoc = doc
        selectedPhotoUri = null
        showGoogleDocPicker = false
      },
      onCreateNewDoc = { title, body ->
        onCreateGoogleDocForChat(title, body)
      },
      onDismiss = { showGoogleDocPicker = false },
    )
  }
}

@Composable
private fun MessageBubbleCard(
  message: ChannelMessage,
  isSelf: Boolean,
  repliesCount: Int,
  inThreadMode: Boolean,
  onReplyInThread: () -> Unit,
  onJumpToMarker: (String) -> Unit,
  onPreviewPhoto: (ByteArray?) -> Unit,
  onAnalyzeGoogleDoc: (title: String, uri: String, snippet: String?) -> Unit,
  onShowFeedback: (String) -> Unit,
) {
  val decodedBytes =
    remember(message.attachmentData) { FieldMediaAndAlerts.decodeBase64ToBytes(message.attachmentData) }
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
  val timeText = message.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Enviando..."
  val bubbleShape =
    RoundedCornerShape(
      topStart = 18.dp,
      topEnd = 18.dp,
      bottomStart = if (isSelf) 18.dp else 4.dp,
      bottomEnd = if (isSelf) 4.dp else 18.dp,
    )

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Bottom,
  ) {
    if (!isSelf) {
      val initial = message.senderName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"
      Box(
        modifier =
          Modifier.size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = initial,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          fontWeight = FontWeight.Bold,
        )
      }
      Spacer(Modifier.width(8.dp))
    }

    Surface(
      shape = bubbleShape,
      color =
        if (isSelf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.surfaceContainerLowest,
      border =
        BorderStroke(
          1.dp,
          if (isSelf) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
          else MaterialTheme.colorScheme.outlineVariant,
        ),
      modifier = Modifier.fillMaxWidth(if (isSelf) 0.85f else 0.88f),
    ) {
      Column(
        modifier = Modifier.padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (isSelf) "Tú" else message.senderName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Text(
          text = message.body,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )

        val googleDocUri =
          message.attachmentUri?.takeIf { GoogleWorkspaceManager.isValidGoogleWorkspaceUrl(it) }
        val photoModel: Any? =
          if (googleDocUri == null) decodedBytes ?: message.attachmentUri?.takeIf { it.isNotBlank() }
          else null

        if (googleDocUri != null) {
          val docTitle = message.attachmentLabel?.ifBlank { "Documento Google" } ?: "Documento Google"
          GoogleDocCard(
            title = docTitle,
            docUri = googleDocUri,
            snippet = message.attachmentSnippet,
            onOpenExternalError = onShowFeedback,
            onAnalyzeWithGemini = {
              onAnalyzeGoogleDoc(docTitle, googleDocUri, message.attachmentSnippet)
            },
            modifier = Modifier.testTag("chat_gdoc_card_${message.id}"),
          )
        } else if (photoModel != null) {
          AsyncImage(
            model = photoModel,
            contentDescription = message.attachmentLabel ?: "Foto adjunta",
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.fillMaxWidth()
                .height(170.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { onPreviewPhoto(decodedBytes) }
                .testTag("chat_photo_${message.id}"),
          )
        } else if (!message.attachmentLabel.isNullOrBlank()) {
          Text(
            text = "📎 ${message.attachmentLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }

        if (!message.linkedMarkerId.isNullOrBlank() && !message.linkedMarkerTitle.isNullOrBlank()) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier =
              Modifier.clickable { onJumpToMarker(message.linkedMarkerId) }
                .testTag("linked_marker_chip_${message.linkedMarkerId}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(15.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "Mapa · ${message.linkedMarkerTitle}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }

        if (!inThreadMode) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier =
              Modifier.clickable(onClick = onReplyInThread)
                .testTag("thread_reply_btn_${message.id}"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
              )
              Spacer(Modifier.width(5.dp))
              Text(
                text = if (repliesCount > 0) "$repliesCount respuestas" else "Responder en hilo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}
