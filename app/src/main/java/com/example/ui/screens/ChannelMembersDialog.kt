package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun ChannelMembersDialog(
  channel: Channel,
  currentUserId: String,
  isAuthenticated: Boolean,
  inviteActionLoading: Boolean,
  onGenerateInvite: (Channel) -> Unit,
  onRevokeInvite: (Channel) -> Unit,
  onRemoveMember: (Channel, String) -> Unit,
  onLeaveChannel: (Channel) -> Unit,
  onDismiss: () -> Unit,
) {
  val isOwner = channel.ownerId == currentUserId
  val clipboardManager = LocalClipboardManager.current
  var copiedCode by remember(channel.joinCode) { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text("Equipo en #${channel.name}")
        Text(
          text = "${channel.memberUids.size} participante(s) activos",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (!isAuthenticated) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                "Modo prueba local: inicia sesión con Google para invitar o gestionar integrantes remotos en la nube.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(10.dp),
            )
          }
        } else if (isOwner) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Código de invitación del canal",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
              if (!channel.joinCode.isNullOrBlank()) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = channel.joinCode,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                      onClick = {
                        clipboardManager.setText(AnnotatedString(channel.joinCode))
                        copiedCode = true
                      },
                      modifier = Modifier.testTag("members_copy_invite_btn"),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = if (copiedCode) "Copiado" else "Copiar código",
                      )
                    }
                    IconButton(
                      onClick = { onRevokeInvite(channel) },
                      enabled = !inviteActionLoading,
                      modifier = Modifier.testTag("revoke_invite_button"),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.LinkOff,
                        contentDescription = "Revocar código de invitación",
                        tint = MaterialTheme.colorScheme.error,
                      )
                    }
                  }
                }
                if (copiedCode) {
                  Text(
                    text = "Código copiado al portapapeles.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              } else {
                OutlinedButton(
                  onClick = { onGenerateInvite(channel) },
                  enabled = !inviteActionLoading,
                  modifier = Modifier.fillMaxWidth().testTag("members_generate_invite_btn"),
                ) {
                  Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Generar código de acceso")
                }
              }
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        channel.memberUids.forEach { memberUid ->
          val resolvedName =
            channel.memberNames[memberUid]?.takeIf { it.isNotBlank() }
              ?: if (memberUid == currentUserId) "Tú" else "Integrante ${memberUid.take(6)}"
          val isMemberOwner = memberUid == channel.ownerId
          val isSelf = memberUid == currentUserId

          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(34.dp)
                    .clip(CircleShape)
                    .background(
                      if (isMemberOwner) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = resolvedName.firstOrNull()?.uppercaseChar()?.toString() ?: "G",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (isMemberOwner) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
              Spacer(Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (isSelf) "$resolvedName (Tú)" else resolvedName,
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (isMemberOwner) {
                    Icon(
                      imageVector = Icons.Filled.Shield,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                      text = "Propietario",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  } else {
                    Text(
                      text = "Miembro operativo",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
              if (isAuthenticated && isOwner && !isMemberOwner) {
                IconButton(
                  onClick = { onRemoveMember(channel, memberUid) },
                  modifier = Modifier.testTag("remove_member_btn_$memberUid"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.PersonRemove,
                    contentDescription = "Retirar miembro",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }

        if (isAuthenticated && !isOwner) {
          Button(
            onClick = {
              onLeaveChannel(channel)
              onDismiss()
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
              ),
            modifier = Modifier.fillMaxWidth().testTag("leave_channel_button"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Salir de este canal")
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}
