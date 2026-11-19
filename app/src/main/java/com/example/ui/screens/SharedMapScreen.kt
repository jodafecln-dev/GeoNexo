package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Channel
import com.example.data.model.ChannelPresence
import com.example.data.model.GeoMarker
import com.example.data.model.MarkerCategory
import com.example.data.model.MarkerStatus
import com.example.data.model.SyncedCollection
import com.example.ui.state.UiState
import com.example.ui.theme.GeoMapTerrainDark
import com.example.ui.theme.GeoMapTerrainLight
import com.example.ui.theme.GeoRouteBlue
import com.example.ui.theme.GeoStatusInProgress
import com.example.ui.theme.GeoStatusOpen
import com.example.ui.theme.GeoStatusResolved
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMapScreen(
  channel: Channel?,
  markersState: UiState<SyncedCollection<GeoMarker>>,
  presenceList: List<ChannelPresence>,
  focusedMarkerId: String?,
  locationSharingActive: Boolean,
  onClearFocusedMarker: () -> Unit,
  onToggleLocationSharing: () -> Unit,
  onCreateMarker: (
    title: String,
    notes: String,
    category: MarkerCategory,
    status: MarkerStatus,
    mapX: Double,
    mapY: Double,
  ) -> Unit,
  onUpdateMarker: (
    markerId: String,
    title: String,
    notes: String,
    category: MarkerCategory,
    status: MarkerStatus,
  ) -> Unit,
  onDeleteMarker: (String) -> Unit,
  onDiscussMarkerInChat: (GeoMarker) -> Unit,
  onAnalyzeMarkerWithGemini: (GeoMarker) -> Unit,
) {
  if (channel == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Selecciona un canal para abrir su mapa compartido.")
    }
    return
  }

  val markers = (markersState as? UiState.Success)?.data?.items.orEmpty()
  var selectedCategoryFilter by remember { mutableStateOf<MarkerCategory?>(null) }
  var showTeamLayer by remember { mutableStateOf(true) }
  var showRoutesLayer by remember { mutableStateOf(true) }

  var placingMarkerMode by remember { mutableStateOf(false) }
  var pendingPlacementCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
  var activeMarkerSheet by remember { mutableStateOf<GeoMarker?>(null) }
  var editingMarker by remember { mutableStateOf<GeoMarker?>(null) }

  var zoom by remember { mutableFloatStateOf(1f) }
  var panOffset by remember { mutableStateOf(Offset.Zero) }

  val filteredMarkers =
    remember(markers, selectedCategoryFilter) {
      if (selectedCategoryFilter == null) markers
      else markers.filter { it.resolvedCategory() == selectedCategoryFilter }
    }

  LaunchedEffect(focusedMarkerId, markers) {
    if (focusedMarkerId != null) {
      val target = markers.firstOrNull { it.id == focusedMarkerId }
      if (target != null) {
        activeMarkerSheet = target
        onClearFocusedMarker()
      }
    }
  }

  val pulseTransition = rememberInfiniteTransition(label = "gps_pulse")
  val pulseScale by
    pulseTransition.animateFloat(
      initialValue = 1f,
      targetValue = 2.1f,
      animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
      label = "pulse_scale",
    )

  val surfaceTint =
    if (isSystemInDarkTheme()) GeoMapTerrainDark else GeoMapTerrainLight
  val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
  val minorGridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
  val contourColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary

  Column(modifier = Modifier.fillMaxSize()) {
    // Layer Controls Strip
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FilterChip(
        selected = selectedCategoryFilter == null,
        onClick = { selectedCategoryFilter = null },
        label = { Text("Todos (${markers.size})") },
        leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(16.dp)) },
      )
      MarkerCategory.entries.forEach { cat ->
        FilterChip(
          selected = selectedCategoryFilter == cat,
          onClick = {
            selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
          },
          label = { Text(cat.labelEs) },
        )
      }
      FilterChip(
        selected = showTeamLayer,
        onClick = { showTeamLayer = !showTeamLayer },
        label = { Text("Equipo (${presenceList.size})") },
        leadingIcon = { Icon(Icons.Filled.ShareLocation, contentDescription = null, modifier = Modifier.size(16.dp)) },
      )
      FilterChip(
        selected = showRoutesLayer,
        onClick = { showRoutesLayer = !showRoutesLayer },
        label = { Text("Rutas") },
      )
    }

    if (placingMarkerMode) {
      Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Toca cualquier punto del mapa para colocar el marcador",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { placingMarkerMode = false }) {
            Icon(Icons.Filled.Close, contentDescription = "Cancelar colocación")
          }
        }
      }
    }

    // Interactive Topographic Map Viewport
    Box(
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 12.dp)
          .clip(MaterialTheme.shapes.large)
          .background(
            Brush.radialGradient(
              listOf(
                surfaceTint,
                MaterialTheme.colorScheme.surfaceContainer,
              )
            )
          )
    ) {
      when (markersState) {
        is UiState.Loading -> {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        is UiState.Error -> {
          Text(
            text = markersState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.align(Alignment.Center).padding(20.dp),
          )
        }
        is UiState.Success -> {
          Canvas(
            modifier =
              Modifier.fillMaxSize()
                .pointerInput(Unit) {
                  detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(0.8f, 3.2f)
                    val maxPanX = size.width * 0.45f * zoom
                    val maxPanY = size.height * 0.45f * zoom
                    panOffset =
                      Offset(
                        x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                        y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY),
                      )
                  }
                }
                .pointerInput(placingMarkerMode, filteredMarkers, zoom, panOffset) {
                  detectTapGestures { tapOffset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val center = Offset(w / 2f, h / 2f)
                    val normX = (((tapOffset.x - panOffset.x - center.x) / zoom) + center.x) / w
                    val normY = (((tapOffset.y - panOffset.y - center.y) / zoom) + center.y) / h

                    if (placingMarkerMode) {
                      pendingPlacementCoords =
                        Pair(
                          normX.toDouble().coerceIn(0.05, 0.95),
                          normY.toDouble().coerceIn(0.05, 0.95),
                        )
                      placingMarkerMode = false
                    } else {
                      // Hit test nearest marker
                      val nearest =
                        filteredMarkers.minByOrNull { mk ->
                          val mx = (((mk.mapX.toFloat() * w) - center.x) * zoom) + center.x + panOffset.x
                          val my = (((mk.mapY.toFloat() * h) - center.y) * zoom) + center.y + panOffset.y
                          hypot(tapOffset.x - mx, tapOffset.y - my)
                        }
                      if (nearest != null) {
                        val mx = (((nearest.mapX.toFloat() * w) - center.x) * zoom) + center.x + panOffset.x
                        val my = (((nearest.mapY.toFloat() * h) - center.y) * zoom) + center.y + panOffset.y
                        if (hypot(tapOffset.x - mx, tapOffset.y - my) <= 56f) {
                          activeMarkerSheet = nearest
                        }
                      }
                    }
                  }
                }
          ) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)

            fun toScreen(nx: Double, ny: Double): Offset {
              val rawX = nx.toFloat() * w
              val rawY = ny.toFloat() * h
              return Offset(
                x = ((rawX - center.x) * zoom) + center.x + panOffset.x,
                y = ((rawY - center.y) * zoom) + center.y + panOffset.y,
              )
            }

            // Minor + Major coordinate grid lines
            val cols = 12
            val rows = 12
            for (i in 1 until cols) {
              val x = w * (i / cols.toFloat())
              drawLine(
                color = if (i % 2 == 0) gridColor else minorGridColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = if (i % 2 == 0) 1.5f else 1f,
              )
            }
            for (j in 1 until rows) {
              val y = h * (j / rows.toFloat())
              drawLine(
                color = if (j % 2 == 0) gridColor else minorGridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (j % 2 == 0) 1.5f else 1f,
              )
            }

            // Topographic elevation contour curves
            for (ring in 1..4) {
              val rFactor = ring * 0.14f * zoom
              val path =
                Path().apply {
                  val cx = center.x + panOffset.x * 0.5f
                  val cy = center.y + panOffset.y * 0.5f
                  moveTo(cx - w * rFactor, cy)
                  cubicTo(
                    cx - w * rFactor * 0.8f,
                    cy - h * rFactor * 1.1f,
                    cx + w * rFactor * 0.9f,
                    cy - h * rFactor * 0.7f,
                    cx + w * rFactor,
                    cy + h * rFactor * 0.1f,
                  )
                  cubicTo(
                    cx + w * rFactor * 0.7f,
                    cy + h * rFactor * 0.95f,
                    cx - w * rFactor * 0.6f,
                    cy + h * rFactor * 0.85f,
                    cx - w * rFactor,
                    cy,
                  )
                }
              drawPath(path, color = contourColor, style = Stroke(width = 2.2f))
            }

            // Route line connecting checkpoints/routes
            if (showRoutesLayer && filteredMarkers.size >= 2) {
              val routePts =
                filteredMarkers
                  .sortedBy { it.mapX }
                  .map { toScreen(it.mapX, it.mapY) }
              val routePath =
                Path().apply {
                  moveTo(routePts.first().x, routePts.first().y)
                  routePts.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
                }
              drawPath(
                path = routePath,
                color = GeoRouteBlue.copy(alpha = 0.65f),
                style =
                  Stroke(
                    width = 5f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f),
                  ),
              )
            }

            // Live Team Member Pins
            if (showTeamLayer) {
              presenceList.forEach { member ->
                val pt = toScreen(member.mapX, member.mapY)
                drawCircle(
                  color = primaryColor.copy(alpha = (2.2f - pulseScale).coerceIn(0.05f, 0.35f)),
                  radius = 18f * pulseScale,
                  center = pt,
                )
                drawCircle(
                  color = Color.White,
                  radius = 14f,
                  center = pt,
                )
                drawCircle(
                  color = secondaryColor,
                  radius = 10f,
                  center = pt,
                )
              }
            }

            // GeoMarkers
            filteredMarkers.forEach { mk ->
              val pt = toScreen(mk.mapX, mk.mapY)
              val pinColor =
                when (mk.resolvedStatus()) {
                  MarkerStatus.OPEN -> GeoStatusOpen
                  MarkerStatus.IN_PROGRESS -> GeoStatusInProgress
                  MarkerStatus.RESOLVED -> GeoStatusResolved
                }
              val isFocused = activeMarkerSheet?.id == mk.id
              val radius = if (isFocused) 24f else 18f

              drawCircle(
                color = pinColor.copy(alpha = 0.22f),
                radius = radius + 10f,
                center = pt,
              )
              drawCircle(
                color = Color.White,
                radius = radius + 3f,
                center = pt,
              )
              drawCircle(
                color = pinColor,
                radius = radius,
                center = pt,
              )
            }
          }
        }
      }

      // Top-left Live Zone HUD
      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
      ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
          Text(
            text = "ZONA #${channel.name}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Zoom ${(zoom * 100).toInt()}% · ${filteredMarkers.size} puntos",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Bottom-left 1-Tap GPS Pause / Share Control
      Surface(
        shape = MaterialTheme.shapes.medium,
        color =
          if (locationSharingActive) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier =
          Modifier.align(Alignment.BottomStart)
            .padding(12.dp)
            .clickable(onClick = onToggleLocationSharing)
            .testTag("map_gps_toggle_pill"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = if (locationSharingActive) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
            contentDescription = null,
            tint =
              if (locationSharingActive) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (locationSharingActive) "GPS en vivo · Pausar" else "Compartir GPS",
            style = MaterialTheme.typography.labelLarge,
            color =
              if (locationSharingActive) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      // Bottom-right Reset View + Drop Marker FABs
      Column(
        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SmallFloatingActionButton(
          onClick = {
            zoom = 1f
            panOffset = Offset.Zero
          },
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
          Icon(Icons.Filled.MyLocation, contentDescription = "Centrar mapa")
        }
        FloatingActionButton(
          onClick = { placingMarkerMode = !placingMarkerMode },
          containerColor = MaterialTheme.colorScheme.tertiary,
          contentColor = MaterialTheme.colorScheme.onTertiary,
          modifier = Modifier.testTag("add_marker_fab"),
        ) {
          Icon(Icons.Filled.AddLocationAlt, contentDescription = "Agregar marcador en mapa")
        }
      }
    }

    // Quick Horizontal Marker Tray (One-Handed Bottom Ergonomics)
    if (filteredMarkers.isNotEmpty()) {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(filteredMarkers, key = { it.id }) { mk ->
          val statusColor =
            when (mk.resolvedStatus()) {
              MarkerStatus.OPEN -> GeoStatusOpen
              MarkerStatus.IN_PROGRESS -> GeoStatusInProgress
              MarkerStatus.RESOLVED -> GeoStatusResolved
            }
          Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier =
              Modifier.width(226.dp)
                .clickable { activeMarkerSheet = mk }
                .testTag("marker_tray_card_${mk.id}"),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
              ) {
                Box(modifier = Modifier.size(11.dp).background(statusColor, CircleShape))
              }
              Spacer(Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = mk.title,
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${mk.resolvedCategory().labelEs} · ${mk.resolvedStatus().labelEs}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }
    } else {
      Spacer(Modifier.height(10.dp))
    }
  }

  // Create Marker Dialog
  if (pendingPlacementCoords != null) {
    MarkerEditorDialog(
      dialogTitle = "Nuevo marcador en mapa",
      initialTitle = "",
      initialNotes = "",
      initialCategory = MarkerCategory.INCIDENT,
      initialStatus = MarkerStatus.OPEN,
      onDismiss = { pendingPlacementCoords = null },
      onSave = { title, notes, category, status ->
        val coords = pendingPlacementCoords!!
        onCreateMarker(title, notes, category, status, coords.first, coords.second)
        pendingPlacementCoords = null
      },
    )
  }

  // Edit Marker Dialog
  if (editingMarker != null) {
    val target = editingMarker!!
    MarkerEditorDialog(
      dialogTitle = "Editar marcador",
      initialTitle = target.title,
      initialNotes = target.notes,
      initialCategory = target.resolvedCategory(),
      initialStatus = target.resolvedStatus(),
      onDismiss = { editingMarker = null },
      onSave = { title, notes, category, status ->
        onUpdateMarker(target.id, title, notes, category, status)
        editingMarker = null
        activeMarkerSheet = null
      },
    )
  }

  // Marker Detail ModalBottomSheet
  if (activeMarkerSheet != null) {
    val mk = activeMarkerSheet!!
    ModalBottomSheet(onDismissRequest = { activeMarkerSheet = null }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = mk.title, style = MaterialTheme.typography.headlineSmall)
            Text(
              text = "${mk.resolvedCategory().labelEs} · Actualizado por ${mk.updatedByName}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = { editingMarker = mk }) {
            Icon(Icons.Filled.Edit, contentDescription = "Editar marcador")
          }
          IconButton(
            onClick = {
              onDeleteMarker(mk.id)
              activeMarkerSheet = null
            }
          ) {
            Icon(
              Icons.Filled.Delete,
              contentDescription = "Eliminar marcador",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }

        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Text(
            text = "Coords: ${String.format("%.4f", mk.lat)}, ${String.format("%.4f", mk.lng)}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }

        Text(
          text = mk.notes.ifBlank { "Sin notas operativas registradas." },
          style = MaterialTheme.typography.bodyMedium,
        )

        Text("Estado operativo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MarkerStatus.entries.forEach { st ->
            FilterChip(
              selected = mk.resolvedStatus() == st,
              onClick = {
                onUpdateMarker(mk.id, mk.title, mk.notes, mk.resolvedCategory(), st)
                activeMarkerSheet = mk.copy(status = st.name)
              },
              label = { Text(st.labelEs) },
            )
          }
        }

        FilledTonalButton(
          onClick = {
            activeMarkerSheet = null
            onAnalyzeMarkerWithGemini(mk)
          },
          modifier = Modifier.fillMaxWidth().testTag("marker_analyze_gemini_btn"),
        ) {
          Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text("Analizar punto con Gemini")
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = { activeMarkerSheet = null },
            modifier = Modifier.weight(1f),
          ) {
            Text("Cerrar")
          }
          Button(
            onClick = {
              activeMarkerSheet = null
              onDiscussMarkerInChat(mk)
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1.4f).testTag("discuss_marker_in_chat_button"),
          ) {
            Text("Comentar en Chat")
          }
        }
      }
    }
  }
}

@Composable
private fun MarkerEditorDialog(
  dialogTitle: String,
  initialTitle: String,
  initialNotes: String,
  initialCategory: MarkerCategory,
  initialStatus: MarkerStatus,
  onDismiss: () -> Unit,
  onSave: (String, String, MarkerCategory, MarkerStatus) -> Unit,
) {
  var title by remember { mutableStateOf(initialTitle) }
  var notes by remember { mutableStateOf(initialNotes) }
  var category by remember { mutableStateOf(initialCategory) }
  var status by remember { mutableStateOf(initialStatus) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(dialogTitle) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Título del punto") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("marker_title_input"),
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notas de campo") },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth().testTag("marker_notes_input"),
        )
        Text("Categoría", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          MarkerCategory.entries.forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat.labelEs) },
            )
          }
        }
        Text("Estado", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          MarkerStatus.entries.forEach { st ->
            FilterChip(
              selected = status == st,
              onClick = { status = st },
              label = { Text(st.labelEs) },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(title, notes, category, status) },
        enabled = title.trim().length >= 2,
        modifier = Modifier.testTag("save_marker_button"),
      ) {
        Text("Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
