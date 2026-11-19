package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Channel
import com.example.data.model.ChannelType
import com.example.data.model.SyncedCollection
import com.example.ui.state.ActiveInviteCode
import com.example.ui.state.UiState

@Composable
fun ChannelsScreen(
  isAuthenticated: Boolean,
  currentUserId: String,
  channelsState: UiState<SyncedCollection<Channel>>,
  activeChannelId: String?,
  searchQuery: String,
  inviteActionLoading: Boolean,
  activeInviteDialog: ActiveInviteCode?,
  onSearchQueryChange: (String) -> Unit,
  onSelectChannel: (String) -> Unit,
  onOpenChannelMap: (String) -> Unit,
  onCreateChannel: (name: String, topic: String, type: ChannelType) -> Unit,
  onInviteChannel: (Channel) -> Unit,
  onRevokeInvite: (Channel) -> Unit,
  onRemoveMember: (Channel, String) -> Unit,
  onLeaveChannel: (Channel) -> Unit,
  onDismissInviteDialog: () -> Unit,
  onJoinChannelByCode: (code: String, onSuccess: () -> Unit) -> Unit,
  onRequestGoogleSignInForRemote: () -> Unit,
  onRetryBootstrap: () -> Unit,
) {
  var showCreateDialog by remember { mutableStateOf(false) }
  var showJoinDialog by remember { mutableStateOf(false) }
  var showRemoteAuthDialog by remember { mutableStateOf(false) }
  var managingMembersChannelId by remember { mutableStateOf<String?>(null) }
  var selectedTypeFilter by remember { mutableStateOf<ChannelType?>(null) }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Pill search bar + remote join action
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text("Buscar canales o sectores...") },
          leadingIcon = {
            Icon(
              Icons.Filled.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(26.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
          modifier = Modifier.weight(1f).testTag("channels_search_input"),
        )
        OutlinedButton(
          onClick = {
            if (isAuthenticated) showJoinDialog = true
            else showRemoteAuthDialog = true
          },
          shape = CircleShape,
          modifier = Modifier.testTag("join_channel_code_button"),
        ) {
          Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Unirse")
        }
      }

      // Quick operation type filter strip
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = selectedTypeFilter == null,
          onClick = { selectedTypeFilter = null },
          label = { Text("Todos") },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        listOf(ChannelType.FIELD, ChannelType.PROJECT, ChannelType.LOGISTICS).forEach { type ->
          FilterChip(
            selected = selectedTypeFilter == type,
            onClick = {
              selectedTypeFilter = if (selectedTypeFilter == type) null else type
            },
            label = { Text(type.labelEs) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
          )
        }
      }

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
            remember(channelsState.data.items, searchQuery, selectedTypeFilter) {
              channelsState.data.items.filter { ch ->
                val matchesType =
                  selectedTypeFilter == null || ch.resolvedType() == selectedTypeFilter
                val matchesQuery =
                  if (searchQuery.isBlank()) true
                  else {
                    val q = searchQuery.trim().lowercase()
                    ch.name.lowercase().contains(q) ||
                      ch.topic.lowercase().contains(q) ||
                      ch.pinnedDocTitle.lowercase().contains(q)
                  }
                matchesType && matchesQuery
              }
            }

          if (filtered.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Column(
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
                    imageVector = Icons.Filled.Workspaces,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                  )
                }
                Text(
                  text = "Sin canales que coincidan",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Crea un canal operativo o únete con un código de equipo.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            LazyColumn(
              contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(filtered, key = { it.id }) { channel ->
                val isSelected = channel.id == activeChannelId
                ChannelWorkspaceCard(
                  channel = channel,
                  selected = isSelected,
                  canInvite = channel.ownerId == currentUserId,
                  onOpenChat = { onSelectChannel(channel.id) },
                  onOpenMap = { onOpenChannelMap(channel.id) },
                  onOpenMembers = { managingMembersChannelId = channel.id },
                  onInvite = {
                    if (isAuthenticated) onInviteChannel(channel)
                    else showRemoteAuthDialog = true
                  },
                )
              }
            }
          }
        }
      }
    }

    ExtendedFloatingActionButton(
      onClick = { showCreateDialog = true },
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary,
      icon = { Icon(Icons.Filled.Add, contentDescription = null) },
      text = { Text("Nuevo canal", fontWeight = FontWeight.SemiBold) },
      modifier =
        Modifier.align(Alignment.BottomEnd)
          .padding(16.dp)
          .testTag("create_channel_fab"),
    )
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

  if (showJoinDialog) {
    JoinChannelByCodeDialog(
      isLoading = inviteActionLoading,
      onDismiss = { showJoinDialog = false },
      onConfirm = { code ->
        onJoinChannelByCode(code) { showJoinDialog = false }
      },
    )
  }

  if (activeInviteDialog != null) {
    ShareInviteCodeDialog(
      invite = activeInviteDialog,
      onDismiss = onDismissInviteDialog,
    )
  }

  val liveManagingChannel =
    managingMembersChannelId?.let { id ->
      (channelsState as? UiState.Success)?.data?.items?.firstOrNull { it.id == id }
    }
  if (liveManagingChannel != null) {
    ChannelMembersDialog(
      channel = liveManagingChannel,
      currentUserId = currentUserId,
      isAuthenticated = isAuthenticated,
      inviteActionLoading = inviteActionLoading,
      onGenerateInvite = onInviteChannel,
      onRevokeInvite = onRevokeInvite,
      onRemoveMember = onRemoveMember,
      onLeaveChannel = onLeaveChannel,
      onDismiss = { managingMembersChannelId = null },
    )
  }

  if (showRemoteAuthDialog) {
    AlertDialog(
      onDismissRequest = { showRemoteAuthDialog = false },
      title = { Text("Conexión remota en la nube") },
      text = {
        Text(
          "Dos usuarios en lugares distintos se conectan en tiempo real a través de Cloud Firestore usando códigos de invitación de canal. Inicia sesión con Google para activar la nube e invitar o unirte a otros equipos."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showRemoteAuthDialog = false
            onRequestGoogleSignInForRemote()
          },
          modifier = Modifier.testTag("remote_connect_signin_btn"),
        ) {
          Text("Iniciar sesión con Google")
        }
      },
      dismissButton = {
        TextButton(onClick = { showRemoteAuthDialog = false }) { Text("Seguir en prueba") }
      },
    )
  }
}

@Composable
private fun ChannelWorkspaceCard(
  channel: Channel,
  selected: Boolean,
  canInvite: Boolean,
  onOpenChat: () -> Unit,
  onOpenMap: () -> Unit,
  onOpenMembers: () -> Unit,
  onInvite: () -> Unit,
) {
  val channelType = channel.resolvedType()
  val (badgeBg, badgeTint, typeIcon) =
    when (channelType) {
      ChannelType.LOGISTICS ->
        Triple(
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
          Icons.Filled.LocalShipping,
        )
      ChannelType.FIELD ->
        Triple(
          MaterialTheme.colorScheme.primaryContainer,
          MaterialTheme.colorScheme.onPrimaryContainer,
          Icons.Filled.Engineering,
        )
      else ->
        Triple(
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
          Icons.Filled.Tag,
        )
    }

  val border =
    if (selected) BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

  Card(
    shape = MaterialTheme.shapes.medium,
    border = border,
    elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
          else MaterialTheme.colorScheme.surfaceContainerLowest
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
          Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(badgeBg),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = typeIcon,
              contentDescription = null,
              tint = badgeTint,
              modifier = Modifier.size(20.dp),
            )
          }
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "#${channel.name}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (selected) {
                Spacer(Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = "Canal activo",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
            Text(
              text = "${channelType.labelEs.uppercase()} · ${channel.memberUids.size} miembro(s)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier =
              Modifier.clickable(onClick = onOpenMembers)
                .testTag("channel_members_btn_${channel.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.Group,
                contentDescription = "Ver equipo del canal",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = "${channel.memberUids.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
          if (canInvite) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
              modifier =
                Modifier.clickable(onClick = onInvite)
                  .testTag("channel_invite_btn_${channel.id}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Filled.Share,
                  contentDescription = "Invitar al canal",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                  text = channel.joinCode ?: "Invitar",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
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
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.weight(1f),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier =
            Modifier.clickable(onClick = onOpenMap)
              .testTag("channel_map_btn_${channel.id}"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Filled.LocationOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "Mapa",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun JoinChannelByCodeDialog(
  isLoading: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var code by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Unirse a un canal remoto") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            "Ingresa el código que generó el creador del canal desde otro dispositivo para sincronizar chat, mapa y bitácora en tiempo real.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = code,
          onValueChange = { code = it.uppercase().take(16) },
          label = { Text("Código de invitación (ej. GNX42A8K)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("join_code_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(code) },
        enabled = !isLoading && code.trim().length >= 6,
        modifier = Modifier.testTag("confirm_join_channel_button"),
      ) {
        if (isLoading) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
          Text("Conectar")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancelar") }
    },
  )
}

@Composable
private fun ShareInviteCodeDialog(
  invite: ActiveInviteCode,
  onDismiss: () -> Unit,
) {
  val clipboardManager = LocalClipboardManager.current
  var copied by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Código para #${invite.channelName}") },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
              text = invite.inviteCode,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
        Text(
          text =
            "Envía este código por WhatsApp, correo o llamada a tu compañero. Al abrir GeoNexo con su cuenta de Google y pulsar 'Unirse', verá este canal en tiempo real desde cualquier lugar.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          clipboardManager.setText(AnnotatedString(invite.inviteCode))
          copied = true
        },
        modifier = Modifier.testTag("copy_invite_code_button"),
      ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (copied) "Código copiado" else "Copiar código")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
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
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
