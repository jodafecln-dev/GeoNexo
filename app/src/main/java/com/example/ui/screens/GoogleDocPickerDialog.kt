package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.state.WorkspaceUiState
import com.example.workspace.GoogleDocSummary
import com.example.workspace.GoogleWorkspaceManager

private enum class GoogleDocPickerMode(val labelEs: String) {
  DRIVE("Drive"),
  CREATE("Crear Doc"),
  PASTE_URL("Pegar URL"),
}

@Composable
fun GoogleDocCard(
  title: String,
  docUri: String,
  snippet: String? = null,
  onOpenExternalError: (String) -> Unit = {},
  onAnalyzeWithGemini: (() -> Unit)? = null,
  onUnlink: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(36.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(19.dp),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title.ifBlank { "Documento Google" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "Google Workspace · Enlace verificado",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        if (onUnlink != null) {
          IconButton(
            onClick = onUnlink,
            modifier = Modifier.size(40.dp).testTag("unlink_google_doc_btn"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "Desvincular Google Doc",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      if (!snippet.isNullOrBlank()) {
        Text(
          text = snippet,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = {
            runCatching {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(docUri))
              context.startActivity(intent)
            }.onFailure {
              onOpenExternalError(
                "No se encontró un navegador o app de Google Docs para abrir el enlace."
              )
            }
          },
          modifier = Modifier.weight(1f).testTag("open_google_doc_btn"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text("Abrir Doc", maxLines = 1)
        }

        if (onAnalyzeWithGemini != null) {
          Button(
            onClick = onAnalyzeWithGemini,
            modifier = Modifier.weight(1f).testTag("analyze_google_doc_gemini_btn"),
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Analizar IA", maxLines = 1)
          }
        }
      }
    }
  }
}

@Composable
fun GoogleDocPickerDialog(
  dialogTitle: String,
  defaultCreateTitle: String,
  defaultCreateBody: String,
  isAuthenticated: Boolean,
  workspaceState: WorkspaceUiState,
  onRequestAuthorizeAndLoad: (String) -> Unit,
  onSelectExistingDoc: (GoogleDocSummary) -> Unit,
  onCreateNewDoc: (title: String, bodyText: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var mode by remember {
    mutableStateOf(
      if (isAuthenticated) GoogleDocPickerMode.DRIVE else GoogleDocPickerMode.PASTE_URL
    )
  }
  var searchQuery by remember { mutableStateOf("") }
  var createTitle by remember { mutableStateOf(defaultCreateTitle) }
  var createBody by remember { mutableStateOf(defaultCreateBody) }
  var pastedTitle by remember { mutableStateOf("") }
  var pastedUrl by remember { mutableStateOf("") }

  LaunchedEffect(isAuthenticated, mode) {
    if (isAuthenticated && mode == GoogleDocPickerMode.DRIVE && workspaceState.isAuthorized && workspaceState.docs.isEmpty()) {
      onRequestAuthorizeAndLoad(searchQuery)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(dialogTitle) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          GoogleDocPickerMode.entries.forEach { tab ->
            FilterChip(
              selected = mode == tab,
              onClick = { mode = tab },
              label = { Text(tab.labelEs, maxLines = 1) },
              modifier = Modifier.testTag("gdoc_picker_tab_${tab.name.lowercase()}"),
            )
          }
        }

        when (mode) {
          GoogleDocPickerMode.DRIVE -> {
            if (!isAuthenticated) {
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text =
                    "En modo prueba local usa 'Pegar URL' con un enlace de docs.google.com, o inicia sesión con Google para explorar y crear documentos reales en tu Drive.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(12.dp),
                )
              }
            } else if (!workspaceState.isAuthorized) {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text =
                    "Conecta tus permisos de Google Docs y Drive para listar tus documentos recientes o leerlos con Gemini.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                  onClick = { onRequestAuthorizeAndLoad(searchQuery) },
                  enabled = !workspaceState.isLoading,
                  modifier = Modifier.fillMaxWidth().testTag("authorize_google_docs_btn"),
                ) {
                  if (workspaceState.isLoading) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(16.dp),
                      strokeWidth = 2.dp,
                      color = MaterialTheme.colorScheme.onPrimary,
                    )
                  } else {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                  }
                  Spacer(Modifier.width(8.dp))
                  Text("Conectar Google Drive y Docs")
                }
              }
            } else {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                OutlinedTextField(
                  value = searchQuery,
                  onValueChange = { searchQuery = it },
                  placeholder = { Text("Buscar documento...") },
                  singleLine = true,
                  leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                  modifier = Modifier.weight(1f).testTag("gdoc_drive_search_input"),
                )
                IconButton(
                  onClick = { onRequestAuthorizeAndLoad(searchQuery) },
                  modifier = Modifier.size(48.dp).testTag("gdoc_drive_refresh_btn"),
                ) {
                  Icon(Icons.Filled.Refresh, contentDescription = "Actualizar lista de Drive")
                }
              }

              if (workspaceState.isLoading) {
                Box(
                  modifier = Modifier.fillMaxWidth().height(120.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  CircularProgressIndicator()
                }
              } else if (workspaceState.docs.isEmpty()) {
                Text(
                  text =
                    "No se encontraron Google Docs en tu Drive con ese filtro. Puedes crear uno en la pestaña 'Crear Doc'.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                LazyColumn(
                  modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  items(workspaceState.docs, key = { it.id }) { doc ->
                    Surface(
                      shape = MaterialTheme.shapes.small,
                      color = MaterialTheme.colorScheme.surfaceContainerLow,
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                      modifier =
                        Modifier.fillMaxWidth()
                          .clickable { onSelectExistingDoc(doc) }
                          .testTag("drive_doc_item_${doc.id}"),
                    ) {
                      Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Icon(
                          imageVector = Icons.Filled.Description,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = doc.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                          )
                          Text(
                            text =
                              if (doc.shared) "Compartido en Drive"
                              else "Privado · verifica acceso para tu equipo",
                            style = MaterialTheme.typography.labelSmall,
                            color =
                              if (doc.shared) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.tertiary,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }

            if (!workspaceState.errorMessage.isNullOrBlank()) {
              Text(
                text = workspaceState.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }

          GoogleDocPickerMode.CREATE -> {
            if (!isAuthenticated) {
              Text(
                text =
                  "Inicia sesión con Google para crear un nuevo documento directamente en Google Docs y compartirlo con el canal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              OutlinedTextField(
                value = createTitle,
                onValueChange = { createTitle = it },
                label = { Text("Título del nuevo Google Doc") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_gdoc_title_input"),
              )
              OutlinedTextField(
                value = createBody,
                onValueChange = { createBody = it },
                label = { Text("Contenido inicial") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("new_gdoc_body_input"),
              )
              Text(
                text = "Se creará en tu Drive y se intentará habilitar enlace de lectura para el equipo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          GoogleDocPickerMode.PASTE_URL -> {
            OutlinedTextField(
              value = pastedTitle,
              onValueChange = { pastedTitle = it },
              label = { Text("Título o etiqueta del documento") },
              placeholder = { Text("Ej. Plano eléctrico sector norte") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("paste_gdoc_title_input"),
            )
            OutlinedTextField(
              value = pastedUrl,
              onValueChange = { pastedUrl = it },
              label = { Text("Enlace HTTPS de Google Docs / Drive") },
              placeholder = { Text("https://docs.google.com/document/d/...") },
              singleLine = true,
              leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
              modifier = Modifier.fillMaxWidth().testTag("paste_gdoc_url_input"),
            )
            val trimmedUrl = pastedUrl.trim()
            val urlValid = GoogleWorkspaceManager.isValidGoogleWorkspaceUrl(trimmedUrl)
            if (trimmedUrl.isNotEmpty()) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (urlValid) {
                  Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = "Dominio oficial verificado (docs/drive.google.com)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                } else {
                  Text(
                    text = "Solo se permiten URLs HTTPS de docs.google.com o drive.google.com",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      when (mode) {
        GoogleDocPickerMode.CREATE -> {
          if (isAuthenticated) {
            Button(
              onClick = {
                onCreateNewDoc(createTitle.trim(), createBody.trim())
                onDismiss()
              },
              enabled = createTitle.trim().length >= 2 && !workspaceState.isLoading,
              modifier = Modifier.testTag("create_google_doc_btn"),
            ) {
              Icon(Icons.Filled.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Crear y vincular")
            }
          }
        }
        GoogleDocPickerMode.PASTE_URL -> {
          val cleanUrl = pastedUrl.trim()
          val valid = GoogleWorkspaceManager.isValidGoogleWorkspaceUrl(cleanUrl)
          Button(
            onClick = {
              val extractedId = GoogleWorkspaceManager.extractGoogleDocId(cleanUrl).orEmpty()
              val label = pastedTitle.trim().ifBlank { "Documento Google" }
              onSelectExistingDoc(
                GoogleDocSummary(
                  id = extractedId,
                  title = label,
                  webViewLink = cleanUrl,
                )
              )
              onDismiss()
            },
            enabled = valid,
            modifier = Modifier.testTag("confirm_paste_gdoc_btn"),
          ) {
            Text("Vincular enlace")
          }
        }
        GoogleDocPickerMode.DRIVE -> Unit
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cerrar") }
    },
  )
}
