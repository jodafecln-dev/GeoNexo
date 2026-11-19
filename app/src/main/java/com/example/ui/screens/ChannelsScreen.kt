package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Channel
import com.example.data.model.ChannelType
import com.example.data.model.SyncedCollection
import com.example.ui.state.UiState

@Composable
fun ChannelsScreen(
  channelsState: UiState<SyncedCollection<Channel>>,
  activeChannelId: String?,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onSelectChannel: (String) -> Unit,
  onOpenChannelMap: (String) -> Unit,
  onCreateChannel: (name: String, topic: String, type: ChannelType) -> Unit,
  onRetryBootstrap: () -> Unit,
) {
  var showCreateDialog by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text("Buscar canales, bitácoras o sectores...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("channels_search_input"),
      )

      when (channelsState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
        is UiState.Error -> {
          Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = channelsState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
              )
              Button(onClick = onRetryBootstrap) { Text("Reintentar") }
            }
          }
        }
        is UiState.Success -> {
          val filtered =
            remember(channelsState.data.items, searchQuery) {
              if (searchQuery.isBlank()) channelsState.data.items
              else {
                val q = searchQuery.trim().lowercase()
                channelsState.data.items.filter {
                  it.name.lowercase().contains(q) ||
                    it.topic.lowercase().contains(q) ||
                    it.pinnedDocTitle.lowercase().contains(q)
                }
              }
            }

          if (filtered.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Workspaces,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(48.dp),
                )
                Text(
                  text = "Sin canales que coincidan",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Crea un canal operativo para unir chat, mapa y bitácora.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            LazyColumn(
              contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(filtered, key = { it.id }) { channel ->
                val isSelected = channel.id == activeChannelId
                ChannelWorkspaceCard(
                  channel = channel,
                  selected = isSelected,
                  onOpenChat = { onSelectChannel(channel.id) },
                  onOpenMap = { onOpenChannelMap(channel.id) },
                )
              }
            }
          }
        }
      }
    }

    FloatingActionButton(
      onClick = { showCreateDialog = true },
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      modifier =
        Modifier.align(Alignment.BottomEnd)
          .padding(16.dp)
          .testTag("create_channel_fab"),
    ) {
      Icon(Icons.Filled.Add, contentDescription = "Crear canal")
    }
  }

  if (showCreateDialog) {
    CreateChannelDialog(
      onDismiss = { showCreateDialog = false },
      onConfirm = { name, topic, type ->
        onCreateChannel(name, topic, type)
        showCreateDialog = false
      },
    )
  }
}

@Composable
private fun ChannelWorkspaceCard(
  channel: Channel,
  selected: Boolean,
  onOpenChat: () -> Unit,
  onOpenMap: () -> Unit,
) {
  val border =
    if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

  Card(
    shape = MaterialTheme.shapes.medium,
    border = border,
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
          else MaterialTheme.colorScheme.surface
      ),
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onOpenChat)
        .testTag("channel_card_${channel.id}"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector =
              when (channel.resolvedType()) {
                ChannelType.LOGISTICS -> Icons.Filled.LocalShipping
                else -> Icons.Filled.Tag
              },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = channel.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Text(
            text = channel.resolvedType().labelEs.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      if (channel.topic.isNotBlank()) {
        Text(
          text = channel.topic,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.weight(1f),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.Description,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = channel.pinnedDocTitle,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        TextButton(
          onClick = onOpenMap,
          modifier = Modifier.testTag("channel_map_btn_${channel.id}"),
        ) {
          Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Mapa")
        }
      }
    }
  }
}

@Composable
private fun CreateChannelDialog(
  onDismiss: () -> Unit,
  onConfirm: (String, String, ChannelType) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var topic by remember { mutableStateOf("") }
  var selectedType by remember { mutableStateOf(ChannelType.FIELD) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nuevo canal operativo") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Nombre (ej. obra-sur)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("new_channel_name_input"),
        )
        OutlinedTextField(
          value = topic,
          onValueChange = { topic = it },
          label = { Text("Objetivo del canal") },
          maxLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("new_channel_topic_input"),
        )
        Text("Tipo de operación", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(ChannelType.FIELD, ChannelType.PROJECT, ChannelType.LOGISTICS).forEach { type ->
            FilterChip(
              selected = selectedType == type,
              onClick = { selectedType = type },
              label = { Text(type.labelEs) },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(name, topic, selectedType) },
        enabled = name.trim().length >= 2,
        modifier = Modifier.testTag("confirm_create_channel_button"),
      ) {
        Text("Crear")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
