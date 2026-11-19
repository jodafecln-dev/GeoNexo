package com.example.ui.screens

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.ChannelPresence
import com.example.data.model.GeoMarker
import com.example.data.model.MarkerStatus
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

enum class MapTileLayerMode(val labelEs: String) {
  STREETS("Calles"),
  TOPO("Topográfico"),
  SCHEMATIC("Esquemático"),
}

@Composable
fun OsmMapViewport(
  tileMode: MapTileLayerMode,
  offlineNetworkDisabled: Boolean,
  markers: List<GeoMarker>,
  presenceList: List<ChannelPresence>,
  showTeamLayer: Boolean,
  showRoutesLayer: Boolean,
  placingMarkerMode: Boolean,
  centerCoords: Pair<Double, Double>,
  recenterTrigger: Int,
  focusedMarker: GeoMarker?,
  onMapTapToPlace: (lat: Double, lng: Double) -> Unit,
  onSelectMarker: (GeoMarker) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val latestPlacingMode by rememberUpdatedState(placingMarkerMode)
  val latestOnPlace by rememberUpdatedState(onMapTapToPlace)
  val latestOnSelect by rememberUpdatedState(onSelectMarker)

  // OpenTopoMap tiles above zoom 15 frequently return HTTP 404 in regional tiles
  val topoTileSource = remember {
    XYTileSource(
      "OpenTopoMap",
      0,
      15,
      256,
      ".png",
      arrayOf(
        "https://a.tile.opentopomap.org/",
        "https://b.tile.opentopomap.org/",
        "https://c.tile.opentopomap.org/",
      ),
      "© OpenTopoMap (CC-BY-SA)",
    )
  }

  val mapView = remember(context) {
    // Store tiles strictly in internal app cache directory (zero storage permissions needed)
    val osmConfig = Configuration.getInstance()
    val baseDir = File(context.cacheDir, "osmdroid").apply { mkdirs() }
    val tileDir = File(baseDir, "tiles").apply { mkdirs() }
    osmConfig.osmdroidBasePath = baseDir
    osmConfig.osmdroidTileCache = tileDir
    osmConfig.userAgentValue = "GeoNexoField/1.0 (${context.packageName})"

    MapView(context).apply {
      setTileSource(TileSourceFactory.MAPNIK)
      setMultiTouchControls(true)
      isTilesScaledToDpi = true
      minZoomLevel = 4.0
      maxZoomLevel = 18.0
      zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
      controller.setZoom(14.8)
      val initialPt =
        markers.firstOrNull { it.lat.isFinite() && it.lng.isFinite() }
          ?.let { GeoPoint(it.lat, it.lng) }
          ?: GeoPoint(centerCoords.first, centerCoords.second)
      controller.setCenter(initialPt)
    }
  }

  var hasCenteredInitially by remember { mutableStateOf(false) }

  LaunchedEffect(markers, recenterTrigger) {
    if (recenterTrigger > 0 || (!hasCenteredInitially && markers.isNotEmpty())) {
      val target =
        if (recenterTrigger > 0) GeoPoint(centerCoords.first, centerCoords.second)
        else markers.firstOrNull { it.lat.isFinite() && it.lng.isFinite() }
          ?.let { GeoPoint(it.lat, it.lng) }
          ?: GeoPoint(centerCoords.first, centerCoords.second)
      mapView.controller.animateTo(target)
      hasCenteredInitially = true
    }
  }

  LaunchedEffect(focusedMarker?.id) {
    focusedMarker?.takeIf { it.lat.isFinite() && it.lng.isFinite() }?.let { mk ->
      mapView.controller.animateTo(GeoPoint(mk.lat, mk.lng))
    }
  }

  DisposableEffect(lifecycleOwner, mapView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      mapView.onDetach()
    }
  }

  AndroidView(
    factory = { mapView },
    update = { mv ->
      mv.setUseDataConnection(!offlineNetworkDisabled)
      val desiredMaxZoom = if (tileMode == MapTileLayerMode.TOPO) 15.0 else 18.0
      mv.minZoomLevel = 4.0
      mv.maxZoomLevel = desiredMaxZoom
      if (mv.zoomLevelDouble > desiredMaxZoom) {
        mv.controller.setZoom(desiredMaxZoom)
      }

      val desiredSource =
        when (tileMode) {
          MapTileLayerMode.TOPO -> topoTileSource
          else -> TileSourceFactory.MAPNIK
        }
      if (mv.tileProvider.tileSource.name() != desiredSource.name()) {
        mv.setTileSource(desiredSource)
      }

      mv.overlays.clear()

      val eventsOverlay =
        MapEventsOverlay(
          object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
              if (latestPlacingMode) {
                latestOnPlace(p.latitude, p.longitude)
                return true
              }
              return false
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
              latestOnPlace(p.latitude, p.longitude)
              return true
            }
          }
        )
      mv.overlays.add(eventsOverlay)

      val validMarkers =
        markers.filter {
          it.lat.isFinite() && it.lng.isFinite() && it.lat in -90.0..90.0 && it.lng in -180.0..180.0
        }

      if (showRoutesLayer && validMarkers.size >= 2) {
        val routeLine =
          Polyline(mv).apply {
            setPoints(validMarkers.sortedBy { it.lng }.map { GeoPoint(it.lat, it.lng) })
            outlinePaint.color = AndroidColor.parseColor("#2563EB")
            outlinePaint.strokeWidth = 7f
          }
        mv.overlays.add(routeLine)
      }

      if (showTeamLayer) {
        presenceList
          .filter {
            it.lat.isFinite() && it.lng.isFinite() && it.lat in -90.0..90.0 && it.lng in -180.0..180.0
          }
          .forEach { member ->
            val memberPin =
              Marker(mv).apply {
                position = GeoPoint(member.lat, member.lng)
                title = "${member.displayName} (GPS en vivo)"
                snippet = "Precisión ±${member.accuracyMeters.toInt()} m"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createCirclePinDrawable(AndroidColor.parseColor("#0D9488"), sizePx = 38)
              }
            mv.overlays.add(memberPin)
          }
      }

      validMarkers.forEach { mk ->
        val pinColor =
          when (mk.resolvedStatus()) {
            MarkerStatus.OPEN -> AndroidColor.parseColor("#DC2626")
            MarkerStatus.IN_PROGRESS -> AndroidColor.parseColor("#D97706")
            MarkerStatus.RESOLVED -> AndroidColor.parseColor("#059669")
          }
        val markerOverlay =
          Marker(mv).apply {
            position = GeoPoint(mk.lat, mk.lng)
            title = mk.title
            snippet = "${mk.resolvedCategory().labelEs} · ${mk.resolvedStatus().labelEs}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = createCirclePinDrawable(pinColor, sizePx = 46)
            setOnMarkerClickListener { _, _ ->
              latestOnSelect(mk)
              true
            }
          }
        mv.overlays.add(markerOverlay)
      }

      mv.invalidate()
    },
    modifier = modifier,
  )
}

private fun createCirclePinDrawable(fillColor: Int, sizePx: Int): GradientDrawable {
  return GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setSize(sizePx, sizePx)
    setColor(fillColor)
    setStroke(6, AndroidColor.WHITE)
  }
}
