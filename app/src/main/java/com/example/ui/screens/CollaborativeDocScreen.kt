package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Channel
import com.example.data.model.DocRevision
import com.example.data.model.GeoMarker
import com.example.ui.theme.GeoAiGradientEnd
import com.example.ui.theme.GeoAiGradientStart
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CollaborativeDocScreen(
  channel: Channel?,
  isAuthenticated: Boolean,
  workspaceLoading: Boolean,
  markers: List<GeoMarker>,
  docRevisions: List<DocRevision>,
  onSaveDoc: (title: String, body: String) -> Unit,
  onRestoreRevision: (DocRevision) -> Unit,
  onOpenMarkerOnMap: (String) -> Unit,
  onExportToGoogleDocs: () -> Unit,
  onShowFeedback: (String) -> Unit,
  onAssistWithGemini: () -> Unit,
) {
  if (channel == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Selecciona un canal para editar su bitácora.", style = MaterialTheme.typography.bodyLarge)
    }
    return
  }

  val context = LocalContext.current
  var isEditing by remember(channel.id) { mutableStateOf(false) }
  var titleDraft by remember(channel.id, channel.pinnedDocTitle) { mutableStateOf(channel.pinnedDocTitle) }
  var bodyDraft by remember(channel.id, channel.pinnedDocBody) { mutableStateOf(channel.pinnedDocBody) }
  var editBaseRevision by remember(channel.id) { mutableLongStateOf(channel.pinnedDocRevision) }
  var editBaseBody by remember(channel.id) { mutableStateOf(channel.pinnedDocBody) }
  var showHistorySheet by remember { mutableStateOf(false) }

  LaunchedEffect(channel.pinnedDocTitle, channel.pinnedDocBody, channel.pinnedDocRevision) {
    if (!isEditing) {
      titleDraft = channel.pinnedDocTitle
      bodyDraft = channel.pinnedDocBody
      editBaseRevision = channel.pinnedDocRevision
      editBaseBody = channel.pinnedDocBody
    }
  }

  val hasRemoteConflict =
    isEditing &&
      (channel.pinnedDocRevision != editBaseRevision || channel.pinnedDocBody != editBaseBody)

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Box(
            modifier =
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.Description,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(20.dp),
            )
          }
          Spacer(Modifier.width(12.dp))
          Column {
            Text(
              text = "Bitácora · #${channel.name}",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = "Rev. ${channel.pinnedDocRevision} · Por ${channel.pinnedDocUpdatedBy}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        IconButton(
          onClick = { showHistorySheet = true },
          modifier = Modifier.testTag("doc_history_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.History,
            contentDescription = "Historial de revisiones",
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
        IconButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val markdown = "# $titleDraft\n\n$bodyDraft"
            clipboard.setPrimaryClip(ClipData.newPlainText("Bitácora GeoNexo", markdown))
            onShowFeedback("Bitácora copiada al portapapeles")
          },
          modifier = Modifier.testTag("copy_doc_button"),
        ) {
          Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar documento")
        }
        IconButton(
          onClick = {
            if (!isEditing) {
              editBaseRevision = channel.pinnedDocRevision
              editBaseBody = channel.pinnedDocBody
            }
            isEditing = !isEditing
          },
          modifier = Modifier.testTag("toggle_doc_edit_button"),
        ) {
          Icon(
            imageVector = if (isEditing) Icons.Filled.Visibility else Icons.Filled.Edit,
            contentDescription = if (isEditing) "Vista previa" else "Editar bitácora",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }

    if (hasRemoteConflict) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().testTag("doc_conflict_banner"),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.WarningAmber,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text =
                "${channel.pinnedDocUpdatedBy} guardó la Rev. ${channel.pinnedDocRevision} mientras editabas.",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            AssistChip(
              onClick = {
                bodyDraft = "$bodyDraft\n\n--- Actualización de ${channel.pinnedDocUpdatedBy} ---\n${channel.pinnedDocBody}"
                editBaseRevision = channel.pinnedDocRevision
                editBaseBody = channel.pinnedDocBody
              },
              label = { Text("Combinar cambios") },
            )
            AssistChip(
              onClick = {
                titleDraft = channel.pinnedDocTitle
                bodyDraft = channel.pinnedDocBody
                editBaseRevision = channel.pinnedDocRevision
                editBaseBody = channel.pinnedDocBody
              },
              label = { Text("Cargar versión equipo") },
            )
            AssistChip(
              onClick = {
                editBaseRevision = channel.pinnedDocRevision
                editBaseBody = channel.pinnedDocBody
              },
              label = { Text("Mantener mi texto") },
            )
          }
        }
      }
    }

    // Modern AI Assistant Gradient-Bordered Banner Button
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      border =
        BorderStroke(
          1.2.dp,
          Brush.horizontalGradient(listOf(GeoAiGradientStart, GeoAiGradientEnd)),
        ),
      modifier =
        Modifier.fillMaxWidth()
          .clickable(onClick = onAssistWithGemini)
          .testTag("doc_assist_gemini_btn"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Completar o resumir bitácora con Gemini",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Genera acuerdos y pendientes desde el mapa y chat",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    OutlinedButton(
      onClick = onExportToGoogleDocs,
      enabled = !workspaceLoading,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("export_bitacora_gdoc_btn"),
    ) {
      if (workspaceLoading) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
      } else {
        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.width(8.dp))
      Text(
        if (isAuthenticated) "Exportar bitácora y mapa a Google Docs"
        else "Exportar a Google Docs (requiere sesión Google)",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (isEditing) {
      OutlinedTextField(
        value = titleDraft,
        onValueChange = { titleDraft = it },
        label = { Text("Título de la bitácora") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          ),
        modifier = Modifier.fillMaxWidth().testTag("doc_title_input"),
      )

      OutlinedTextField(
        value = bodyDraft,
        onValueChange = { bodyDraft = it },
        label = { Text("Contenido colaborativo (notas, acuerdos, checklist)") },
        minLines = 8,
        shape = MaterialTheme.shapes.medium,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          ),
        modifier = Modifier.fillMaxWidth().testTag("doc_body_input"),
      )

      if (markers.isNotEmpty()) {
        Text(
          text = "Insertar referencia a marcador del mapa:",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          markers.take(6).forEach { mk ->
            AssistChip(
              onClick = {
                bodyDraft =
                  if (bodyDraft.isBlank()) "• [${mk.title}] (${mk.resolvedStatus().labelEs})"
                  else "$bodyDraft\n• [${mk.title}] (${mk.resolvedStatus().labelEs})"
              },
              label = { Text("+ ${mk.title}") },
              leadingIcon = {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
              },
            )
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
          onClick = {
            titleDraft = channel.pinnedDocTitle
            bodyDraft = channel.pinnedDocBody
            isEditing = false
          },
          modifier = Modifier.weight(1f).height(48.dp),
        ) {
          Text("Descartar")
        }
        Button(
          onClick = {
            onSaveDoc(titleDraft, bodyDraft)
            isEditing = false
          },
          enabled = titleDraft.trim().isNotEmpty(),
          modifier = Modifier.weight(1f).height(48.dp).testTag("save_doc_button"),
        ) {
          Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Guardar")
        }
      }
    } else {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = channel.pinnedDocTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = channel.pinnedDocBody.ifBlank { "Sin contenido todavía. Pulsa Editar para iniciar la bitácora." },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      if (markers.isNotEmpty()) {
        Text(
          text = "Puntos vinculados en este canal (${markers.size})",
          style = MaterialTheme.typography.titleSmall,
        )
        markers.forEach { mk ->
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onOpenMarkerOnMap(mk.id) }
                .testTag("doc_marker_link_${mk.id}"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Filled.LocationOn,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.size(18.dp),
                )
              }
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(mk.title, style = MaterialTheme.typography.titleSmall)
                Text(
                  "${mk.resolvedCategory().labelEs} · ${mk.resolvedStatus().labelEs}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Text(
                  text = "Mapa",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
              }
            }
          }
        }
      }

      Spacer(Modifier.height(16.dp))
    }
  }

  if (showHistorySheet) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Historial de revisiones (${docRevisions.size})",
          style = MaterialTheme.typography.titleLarge,
        )
        if (docRevisions.isEmpty()) {
          Text(
            text = "Guarda una actualización de la bitácora para comenzar el historial inmutable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          docRevisions.forEach { rev ->
            val timeText = rev.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Reciente"
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Rev. ${rev.revisionNumber} · ${rev.authorName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = "$timeText — ${rev.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = rev.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                  onClick = {
                    onRestoreRevision(rev)
                    showHistorySheet = false
                    isEditing = false
                  },
                  modifier = Modifier.testTag("restore_revision_${rev.revisionNumber}"),
                ) {
                  Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Restaurar")
                }
              }
            }
          }
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}
