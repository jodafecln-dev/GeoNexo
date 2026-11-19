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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChatScreen(
  channel: Channel?,
  currentUserId: String,
  messagesState: UiState<SyncedCollection<ChannelMessage>>,
  markers: List<GeoMarker>,
  activeThreadParentId: String?,
  onOpenThread: (String?) -> Unit,
  onJumpToMarker: (String) -> Unit,
  onSendMessage: (
    body: String,
    parentMessageId: String?,
    linkedMarker: GeoMarker?,
    attachmentLabel: String?,
    attachmentUri: String?,
  ) -> Unit,
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
  var showMarkerPicker by remember { mutableStateOf(false) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
      onResult = { uri -> selectedPhotoUri = uri },
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
          )
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
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.PinDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
              )
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
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
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
                )
              }
            }
          }
        }
      }
    }

    // Active composer attachments preview
    if (selectedMarker != null || selectedPhotoUri != null) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
      }
    }

    // Composer bar
    Surface(
      tonalElevation = 4.dp,
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.weight(1f).testTag("chat_message_input"),
        )
        Spacer(Modifier.width(6.dp))
        IconButton(
          onClick = {
            val clean = draft.trim()
            if (clean.isNotEmpty()) {
              onSendMessage(
                clean,
                activeThreadParentId,
                selectedMarker,
                if (selectedPhotoUri != null) "Evidencia fotográfica de campo" else null,
                selectedPhotoUri?.toString(),
              )
              draft = ""
              selectedMarker = null
              selectedPhotoUri = null
            }
          },
          enabled = draft.trim().isNotEmpty(),
          modifier = Modifier.testTag("send_message_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Enviar mensaje",
            tint =
              if (draft.trim().isNotEmpty()) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
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
                color = MaterialTheme.colorScheme.surfaceVariant,
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
}

@Composable
private fun MessageBubbleCard(
  message: ChannelMessage,
  isSelf: Boolean,
  repliesCount: Int,
  inThreadMode: Boolean,
  onReplyInThread: () -> Unit,
  onJumpToMarker: (String) -> Unit,
) {
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
  val timeText = message.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Enviando..."

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
  ) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color =
        if (isSelf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        else MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
      modifier = Modifier.fillMaxWidth(0.9f),
    ) {
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = message.senderName,
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

        if (!message.attachmentUri.isNullOrBlank()) {
          AsyncImage(
            model = message.attachmentUri,
            contentDescription = message.attachmentLabel ?: "Foto adjunta",
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.fillMaxWidth()
                .height(150.dp)
                .clip(MaterialTheme.shapes.small),
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
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier =
              Modifier.clickable { onJumpToMarker(message.linkedMarkerId) }
                .testTag("linked_marker_chip_${message.linkedMarkerId}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = "Ver en mapa: ${message.linkedMarkerTitle}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
              )
            }
          }
        }

        if (!inThreadMode) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.clickable(onClick = onReplyInThread)
                .padding(top = 2.dp)
                .testTag("thread_reply_btn_${message.id}"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Reply,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = if (repliesCount > 0) "$repliesCount respuestas en hilo" else "Responder en hilo",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}
