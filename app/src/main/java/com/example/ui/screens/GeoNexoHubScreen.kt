package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.signOut
import com.example.ui.state.GeoNexoViewModel
import com.example.ui.state.UiState
import com.example.ui.state.WorkspaceTab
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoNexoHubScreen(
  viewModel: GeoNexoViewModel,
  currentUserId: String,
  onSignedOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
  val channelsState by viewModel.channelsState.collectAsStateWithLifecycle()
  val activeChannel by viewModel.activeChannel.collectAsStateWithLifecycle()
  val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
  val markersState by viewModel.markersState.collectAsStateWithLifecycle()
  val presenceList by viewModel.presenceList.collectAsStateWithLifecycle()
  val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
  val focusedMarkerId by viewModel.focusedMarkerId.collectAsStateWithLifecycle()
  val activeThreadParentId by viewModel.activeThreadParentId.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val offlineDisabled by viewModel.offlineNetworkDisabled.collectAsStateWithLifecycle()
  val locationSharingActive by viewModel.locationSharingRequested.collectAsStateWithLifecycle()

  var showSettingsSheet by remember { mutableStateOf(false) }

  val fusedLocationClient = remember(context) {
    LocationServices.getFusedLocationProviderClient(context)
  }

  @SuppressLint("MissingPermission")
  fun fetchDeviceLocationAndEnable() {
    fusedLocationClient
      .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
      .addOnSuccessListener { loc ->
        if (loc != null) {
          viewModel.setLocationSharingEnabled(
            enabled = true,
            lat = loc.latitude,
            lng = loc.longitude,
            accuracy = loc.accuracy.toDouble(),
          )
        } else {
          viewModel.setLocationSharingEnabled(enabled = true)
        }
      }
      .addOnFailureListener {
        viewModel.setLocationSharingEnabled(enabled = true)
      }
  }

  val locationPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
      val granted =
        grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      if (granted) {
        fetchDeviceLocationAndEnable()
      } else {
        viewModel.setLocationSharingEnabled(enabled = false)
      }
    }

  fun handleToggleLocationSharing(enable: Boolean) {
    if (!enable) {
      viewModel.setLocationSharingEnabled(enabled = false)
      return
    }
    val hasCoarse =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val hasFine =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (hasCoarse || hasFine) {
      fetchDeviceLocationAndEnable()
    } else {
      locationPermissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.ACCESS_FINE_LOCATION,
        )
      )
    }
  }

  LaunchedEffect(Unit) {
    viewModel.snackbarEvents.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  val hasPendingWrites =
    (messagesState as? UiState.Success)?.data?.hasPendingWrites == true ||
      (markersState as? UiState.Success)?.data?.hasPendingWrites == true ||
      (channelsState as? UiState.Success)?.data?.hasPendingWrites == true

  val markersList = (markersState as? UiState.Success)?.data?.items.orEmpty()

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = WindowInsets.navigationBars,
      topBar = {
        TopAppBar(
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
              titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
          title = {
            Column(
              modifier =
                Modifier.clickable { viewModel.selectTab(WorkspaceTab.CHANNELS) }
                  .testTag("topbar_channel_switcher")
            ) {
              Text(
                text = activeChannel?.let { "#${it.name}" } ?: "GeoNexo",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  activeChannel?.topic?.takeIf { it.isNotBlank() }
                    ?: "Colaboración oficina y campo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          },
          actions = {
            SyncStatusChip(
              offlineDisabled = offlineDisabled,
              hasPendingWrites = hasPendingWrites,
              onClick = { viewModel.toggleOfflineSimulation(!offlineDisabled) },
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
              onClick = { showSettingsSheet = true },
              modifier = Modifier.testTag("open_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Ajustes de campo y privacidad",
              )
            }
          },
        )
      },
      bottomBar = {
        if (!useNavigationRail) {
          NavigationBar {
            WorkspaceTab.entries.forEach { tab ->
              val selected = activeTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { viewModel.selectTab(tab) },
                icon = {
                  Icon(
                    imageVector = tabIcon(tab, selected),
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavigationRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            WorkspaceTab.entries.forEach { tab ->
              val selected = activeTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { viewModel.selectTab(tab) },
                icon = {
                  Icon(
                    imageVector = tabIcon(tab, selected),
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}"),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          when (activeTab) {
            WorkspaceTab.CHANNELS -> {
              ChannelsScreen(
                channelsState = channelsState,
                activeChannelId = activeChannel?.id,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::setSearchQuery,
                onSelectChannel = { id -> viewModel.selectChannel(id, navigateToChat = true) },
                onOpenChannelMap = { id ->
                  viewModel.selectChannel(id, navigateToChat = false)
                  viewModel.selectTab(WorkspaceTab.MAP)
                },
                onCreateChannel = viewModel::createChannel,
                onRetryBootstrap = viewModel::bootstrapWorkspace,
              )
            }
            WorkspaceTab.CHAT -> {
              ChatScreen(
                channel = activeChannel,
                currentUserId = currentUserId,
                messagesState = messagesState,
                markers = markersList,
                activeThreadParentId = activeThreadParentId,
                onOpenThread = viewModel::openThread,
                onJumpToMarker = viewModel::openMarkerOnMap,
                onSendMessage = viewModel::sendMessage,
              )
            }
            WorkspaceTab.MAP -> {
              SharedMapScreen(
                channel = activeChannel,
                markersState = markersState,
                presenceList = presenceList,
                focusedMarkerId = focusedMarkerId,
                locationSharingActive = locationSharingActive,
                onClearFocusedMarker = viewModel::clearFocusedMarker,
                onToggleLocationSharing = {
                  handleToggleLocationSharing(!locationSharingActive)
                },
                onCreateMarker = viewModel::createMarker,
                onUpdateMarker = viewModel::updateMarker,
                onDeleteMarker = viewModel::deleteMarker,
                onDiscussMarkerInChat = { mk ->
                  viewModel.sendMessage(
                    body = "Seguimiento sobre marcador #${mk.title} (${mk.resolvedStatus().labelEs})",
                    linkedMarker = mk,
                  )
                  viewModel.selectTab(WorkspaceTab.CHAT)
                },
              )
            }
            WorkspaceTab.DOC -> {
              CollaborativeDocScreen(
                channel = activeChannel,
                markers = markersList,
                onSaveDoc = viewModel::updatePinnedDoc,
                onOpenMarkerOnMap = viewModel::openMarkerOnMap,
                onShowFeedback = { /* handled via clipboard + UI */ },
              )
            }
          }
        }
      }
    }
  }

  if (showSettingsSheet) {
    PrivacySettingsSheet(
      displayName = userProfile?.displayName ?: "Equipo GeoNexo",
      darkMode = userProfile?.darkMode ?: false,
      highContrastMode = userProfile?.highContrastMode ?: false,
      locationSharingActive = locationSharingActive,
      offlineModeSimulated = offlineDisabled,
      onToggleDarkMode = { dark ->
        viewModel.updateAccessibilityAndTheme(
          highContrastMode = userProfile?.highContrastMode ?: false,
          darkMode = dark,
        )
      },
      onToggleHighContrast = { hc ->
        viewModel.updateAccessibilityAndTheme(
          highContrastMode = hc,
          darkMode = userProfile?.darkMode ?: false,
        )
      },
      onToggleLocationSharing = { enabled ->
        handleToggleLocationSharing(enabled)
      },
      onToggleOfflineMode = viewModel::toggleOfflineSimulation,
      onSignOut = {
        showSettingsSheet = false
        signOut(credentialManager, scope, onSignOutComplete = onSignedOut)
      },
      onDismiss = { showSettingsSheet = false },
    )
  }
}

@Composable
private fun SyncStatusChip(
  offlineDisabled: Boolean,
  hasPendingWrites: Boolean,
  onClick: () -> Unit,
) {
  val (icon, label, containerColor, contentColor) =
    when {
      offlineDisabled ->
        Quad(
          Icons.Filled.CloudOff,
          "Offline",
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
        )
      hasPendingWrites ->
        Quad(
          Icons.Filled.CloudUpload,
          "Cola",
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
        )
      else ->
        Quad(
          Icons.Filled.CloudDone,
          "Sync",
          MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
          MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }

  Surface(
    shape = MaterialTheme.shapes.small,
    color = containerColor,
    modifier = Modifier.clickable(onClick = onClick).testTag("sync_status_chip"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun tabIcon(tab: WorkspaceTab, selected: Boolean) =
  when (tab) {
    WorkspaceTab.CHANNELS -> if (selected) Icons.Filled.Workspaces else Icons.Outlined.Workspaces
    WorkspaceTab.CHAT -> if (selected) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline
    WorkspaceTab.MAP -> if (selected) Icons.Filled.Map else Icons.Outlined.Map
    WorkspaceTab.DOC -> if (selected) Icons.Filled.Description else Icons.Outlined.Description
  }
