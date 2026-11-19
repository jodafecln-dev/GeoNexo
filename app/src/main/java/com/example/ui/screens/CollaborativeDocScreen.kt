package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Channel
import com.example.data.model.GeoMarker

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollaborativeDocScreen(
  channel: Channel?,
  markers: List<GeoMarker>,
  onSaveDoc: (title: String, body: String) -> Unit,
  onOpenMarkerOnMap: (String) -> Unit,
  onShowFeedback: (String) -> Unit,
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

  LaunchedEffect(channel.pinnedDocTitle, channel.pinnedDocBody) {
    if (!isEditing) {
      titleDraft = channel.pinnedDocTitle
      bodyDraft = channel.pinnedDocBody
    }
  }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.width(10.dp))
          Column {
            Text(
              text = "Documento anclado a #${channel.name}",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = "Última edición por ${channel.pinnedDocUpdatedBy}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
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
          onClick = { isEditing = !isEditing },
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

    if (isEditing) {
      OutlinedTextField(
        value = titleDraft,
        onValueChange = { titleDraft = it },
        label = { Text("Título de la bitácora") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("doc_title_input"),
      )

      OutlinedTextField(
        value = bodyDraft,
        onValueChange = { bodyDraft = it },
        label = { Text("Contenido colaborativo (notas, acuerdos, checklist)") },
        minLines = 8,
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
          modifier = Modifier.weight(1f),
        ) {
          Text("Descartar")
        }
        Button(
          onClick = {
            onSaveDoc(titleDraft, bodyDraft)
            isEditing = false
          },
          enabled = titleDraft.trim().isNotEmpty(),
          modifier = Modifier.weight(1f).testTag("save_doc_button"),
        ) {
          Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Guardar")
        }
      }
    } else {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
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
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onOpenMarkerOnMap(mk.id) }
                .testTag("doc_marker_link_${mk.id}"),
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
              Column(modifier = Modifier.weight(1f)) {
                Text(mk.title, style = MaterialTheme.typography.titleSmall)
                Text(
                  "${mk.resolvedCategory().labelEs} · ${mk.resolvedStatus().labelEs}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = "Abrir mapa",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }

      Spacer(Modifier.height(16.dp))
    }
  }
}
