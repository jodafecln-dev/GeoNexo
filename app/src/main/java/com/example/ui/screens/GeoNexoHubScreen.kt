package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import com.example.ai.ChannelContextSnapshot
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOut
import com.example.ui.state.GeminiChatViewModel
import com.example.ui.state.GeoNexoViewModel
import com.example.ui.state.UiState
import com.example.ui.state.WorkspaceTab
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoNexoHubScreen(
  viewModel: GeoNexoViewModel,
  geminiViewModel: GeminiChatViewModel,
  currentUserId: String,
  isAuthenticated: Boolean,
  onExitDemoMode: () -> Unit,
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
  val activeInviteDialog by viewModel.activeInviteDialog.collectAsStateWithLifecycle()
  val inviteActionLoading by viewModel.inviteActionLoading.collectAsStateWithLifecycle()
  val aiStreamState by geminiViewModel.aiStreamState.collectAsStateWithLifecycle()
  val geminiTurns by geminiViewModel.activeChannelTurns.collectAsStateWithLifecycle()

  LaunchedEffect(activeChannel?.id) {
    geminiViewModel.setActiveChannel(activeChannel?.id)
  }

  var showSettingsSheet by remember { mutableStateOf(false) }

  fun launchGoogleSignIn() {
    onGoogleSignInClicked(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {
        showSettingsSheet = false
        scope.launch { snackbarHostState.showSnackbar("Sesión conectada. Gemini IA y Nube activos.") }
      },
      onAuthError = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
      },
      scope = scope,
    )
  }

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
  val messagesList = (messagesState as? UiState.Success)?.data?.items.orEmpty()

  fun buildChannelSnapshot(): ChannelContextSnapshot? {
    val ch = activeChannel ?: return null
    return ChannelContextSnapshot(
      channel = ch,
      recentMessages = messagesList,
      markers = markersList,
      activePresence = presenceList,
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = WindowInsets.navigationBars,
      topBar = {
        Column {
          TopAppBar(
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
              ),
            title = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier.clickable { viewModel.selectTab(WorkspaceTab.CHANNELS) }
                    .testTag("topbar_channel_switcher"),
              ) {
                Box(
                  modifier =
                    Modifier.size(36.dp)
                      .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                      ),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Filled.Tag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(
                    text = activeChannel?.name ?: "GeoNexo",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text =
                      if (!isAuthenticated) "Modo Prueba Local · Inicia sesión para IA"
                      else
                        activeChannel?.topic?.takeIf { it.isNotBlank() }
                          ?: "Colaboración oficina y campo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            },
            actions = {
              SyncStatusChip(
                isDemoMode = !isAuthenticated,
                offlineDisabled = offlineDisabled,
                hasPendingWrites = hasPendingWrites,
                onClick = {
                  if (!isAuthenticated) {
                    viewModel.selectTab(WorkspaceTab.GEMINI)
                  } else {
                    viewModel.toggleOfflineSimulation(!offlineDisabled)
                  }
                },
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
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        }
      },
      bottomBar = {
        if (!useNavigationRail) {
          Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
              tonalElevation = 0.dp,
            ) {
              WorkspaceTab.entries.forEach { tab ->
                val selected = activeTab == tab
                NavigationBarItem(
                  selected = selected,
                  onClick = { viewModel.selectTab(tab) },
                  colors =
                    NavigationBarItemDefaults.colors(
                      selectedIconColor = MaterialTheme.colorScheme.primary,
                      selectedTextColor = MaterialTheme.colorScheme.primary,
                      indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                      unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                  icon = {
                    Icon(
                      imageVector = tabIcon(tab, selected),
                      contentDescription = tab.label,
                    )
                  },
                  label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                  modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                )
              }
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavigationRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxHeight(),
          ) {
            WorkspaceTab.entries.forEach { tab ->
              val selected = activeTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { viewModel.selectTab(tab) },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
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

        AnimatedContent(
          targetState = activeTab,
          transitionSpec = {
            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
          },
          label = "workspace_tab_transition",
          modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { currentTab ->
          when (currentTab) {
            WorkspaceTab.CHANNELS -> {
              ChannelsScreen(
                isAuthenticated = isAuthenticated,
                currentUserId = currentUserId,
                channelsState = channelsState,
                activeChannelId = activeChannel?.id,
                searchQuery = searchQuery,
                inviteActionLoading = inviteActionLoading,
                activeInviteDialog = activeInviteDialog,
                onSearchQueryChange = viewModel::setSearchQuery,
                onSelectChannel = { id -> viewModel.selectChannel(id, navigateToChat = true) },
                onOpenChannelMap = { id ->
                  viewModel.selectChannel(id, navigateToChat = false)
                  viewModel.selectTab(WorkspaceTab.MAP)
                },
                onCreateChannel = viewModel::createChannel,
                onInviteChannel = viewModel::requestInviteCodeForChannel,
                onDismissInviteDialog = viewModel::dismissInviteDialog,
                onJoinChannelByCode = viewModel::joinChannelByCode,
                onRequestGoogleSignInForRemote = { launchGoogleSignIn() },
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
                onSummarizeWithGemini = {
                  viewModel.selectTab(WorkspaceTab.GEMINI)
                  if (isAuthenticated) {
                    buildChannelSnapshot()?.let { snapshot ->
                      geminiViewModel.summarizeChannel(snapshot)
                    }
                  }
                },
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
                onAnalyzeMarkerWithGemini = { mk ->
                  viewModel.selectTab(WorkspaceTab.GEMINI)
                  if (isAuthenticated) {
                    buildChannelSnapshot()?.let { snapshot ->
                      geminiViewModel.analyzeMarker(mk, snapshot)
                    }
                  }
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
                onAssistWithGemini = {
                  viewModel.selectTab(WorkspaceTab.GEMINI)
                  if (isAuthenticated) {
                    buildChannelSnapshot()?.let { snapshot ->
                      geminiViewModel.assistDocument(snapshot)
                    }
                  }
                },
              )
            }
            WorkspaceTab.GEMINI -> {
              GeminiCopilotScreen(
                isAuthenticated = isAuthenticated,
                channel = activeChannel,
                markersCount = markersList.size,
                messagesCount = messagesList.size,
                presenceCount = presenceList.size,
                turns = geminiTurns,
                streamState = aiStreamState,
                onSendPrompt = { prompt, imageUri ->
                  if (isAuthenticated) {
                    buildChannelSnapshot()?.let { snapshot ->
                      geminiViewModel.sendMessage(
                        prompt = prompt,
                        contextSnapshot = snapshot,
                        imageUri = imageUri,
                      )
                    }
                  }
                },
                onApplyAction = viewModel::applyGeminiAction,
                onPublishTextToChat = { text ->
                  viewModel.sendMessage(body = text)
                  viewModel.selectTab(WorkspaceTab.CHAT)
                },
                onAppendTextToDoc = { text ->
                  viewModel.appendToPinnedDoc(text)
                  viewModel.selectTab(WorkspaceTab.DOC)
                },
                onClearHistory = { geminiViewModel.clearHistory(activeChannel?.id) },
              )
            }
          }
        }
      }
    }
  }

  if (showSettingsSheet) {
    PrivacySettingsSheet(
      isAuthenticated = isAuthenticated,
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
      onGoogleSignIn = { launchGoogleSignIn() },
      onExitDemoMode = {
        showSettingsSheet = false
        onExitDemoMode()
      },
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
  isDemoMode: Boolean,
  offlineDisabled: Boolean,
  hasPendingWrites: Boolean,
  onClick: () -> Unit,
) {
  val (icon, label, containerColor, contentColor) =
    when {
      isDemoMode ->
        Quad(
          Icons.Filled.PhoneAndroid,
          "Prueba",
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
        )
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
    shape = CircleShape,
    color = containerColor,
    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.22f)),
    modifier = Modifier.clickable(onClick = onClick).testTag("sync_status_chip"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
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
    WorkspaceTab.GEMINI -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
  }
