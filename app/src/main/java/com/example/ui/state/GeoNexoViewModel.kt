package com.example.ui.state

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiSuggestedAction
import com.example.data.local.DemoLocalRepository
import com.example.data.model.Channel
import com.example.data.model.ChannelMessage
import com.example.data.model.ChannelPresence
import com.example.data.model.ChannelType
import com.example.data.model.DocRevision
import com.example.data.model.GeoMarker
import com.example.data.model.MarkerCategory
import com.example.data.model.MarkerStatus
import com.example.data.model.SyncedCollection
import com.example.data.model.UserProfile
import com.example.data.remote.GeoNexoRepository
import com.example.util.FieldMediaAndAlerts
import com.example.workspace.GoogleDocSummary
import com.example.workspace.GoogleWorkspaceManager
import com.example.workspace.WorkspaceAuthResult
import com.example.workspace.WorkspaceUnauthorizedException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class WorkspaceTab(val label: String) {
  CHANNELS("Canales"),
  CHAT("Chat"),
  MAP("Mapa"),
  DOC("Bitácora"),
  GEMINI("Gemini"),
}

data class ActiveInviteCode(
  val channelName: String,
  val inviteCode: String,
)

data class FieldAlertItem(
  val id: String,
  val channelId: String,
  val channelName: String,
  val title: String,
  val body: String,
  val markerId: String?,
  val timestampMillis: Long,
  val unread: Boolean = true,
)

data class WorkspaceUiState(
  val isAuthorized: Boolean = false,
  val isLoading: Boolean = false,
  val docs: List<GoogleDocSummary> = emptyList(),
  val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GeoNexoViewModel(
  private val repository: GeoNexoRepository?,
  private val db: FirebaseFirestore?,
  private val demoLocalRepository: DemoLocalRepository?,
  private val appContext: Context,
  private val currentUserId: String,
  val isDemoMode: Boolean = false,
) : ViewModel() {

  private val _activeTab = MutableStateFlow(WorkspaceTab.CHAT)
  val activeTab: StateFlow<WorkspaceTab> = _activeTab.asStateFlow()

  private val _selectedChannelId = MutableStateFlow<String?>(null)
  val selectedChannelId: StateFlow<String?> = _selectedChannelId.asStateFlow()

  private val _focusedMarkerId = MutableStateFlow<String?>(null)
  val focusedMarkerId: StateFlow<String?> = _focusedMarkerId.asStateFlow()

  private val _activeThreadParentId = MutableStateFlow<String?>(null)
  val activeThreadParentId: StateFlow<String?> = _activeThreadParentId.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _messagePageLimit = MutableStateFlow(INITIAL_MESSAGE_PAGE_SIZE)
  val messagePageLimit: StateFlow<Int> = _messagePageLimit.asStateFlow()

  private val _offlineNetworkDisabled = MutableStateFlow(false)
  val offlineNetworkDisabled: StateFlow<Boolean> = _offlineNetworkDisabled.asStateFlow()

  private val _locationSharingRequested = MutableStateFlow(false)
  val locationSharingRequested: StateFlow<Boolean> = _locationSharingRequested.asStateFlow()

  private val _fieldAlertsEnabled = MutableStateFlow(true)
  val fieldAlertsEnabled: StateFlow<Boolean> = _fieldAlertsEnabled.asStateFlow()

  private val _lastKnownCoords = MutableStateFlow(Pair(19.4326, -99.1332))
  val lastKnownCoords: StateFlow<Pair<Double, Double>> = _lastKnownCoords.asStateFlow()

  private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

  private val _initError = MutableStateFlow<String?>(null)
  val initError: StateFlow<String?> = _initError.asStateFlow()

  private val _activeInviteDialog = MutableStateFlow<ActiveInviteCode?>(null)
  val activeInviteDialog: StateFlow<ActiveInviteCode?> = _activeInviteDialog.asStateFlow()

  private val _inviteActionLoading = MutableStateFlow(false)
  val inviteActionLoading: StateFlow<Boolean> = _inviteActionLoading.asStateFlow()

  private val _fieldAlertsHistory = MutableStateFlow<List<FieldAlertItem>>(emptyList())
  val fieldAlertsHistory: StateFlow<List<FieldAlertItem>> = _fieldAlertsHistory.asStateFlow()

  private val _workspaceAccessToken = MutableStateFlow<String?>(null)
  private val _workspaceState = MutableStateFlow(WorkspaceUiState())
  val workspaceState: StateFlow<WorkspaceUiState> = _workspaceState.asStateFlow()

  private val _pendingWorkspaceConsent = MutableSharedFlow<IntentSender>(extraBufferCapacity = 2)
  val pendingWorkspaceConsent: SharedFlow<IntentSender> = _pendingWorkspaceConsent.asSharedFlow()

  private val _demoPresenceByChannel = MutableStateFlow<Map<String, List<ChannelPresence>>>(emptyMap())

  // Tracks IDs already seen per channel so we only notify on genuinely new remote alerts
  private var alertBaselineChannelId: String? = null
  private val seenMarkerIds = mutableSetOf<String>()
  private val seenMessageIds = mutableSetOf<String>()
  private var markersInitializedForChannel = false
  private var messagesInitializedForChannel = false

  val userProfile: StateFlow<UserProfile?> =
    if (isDemoMode || repository == null) {
      (demoLocalRepository?.observeProfile() ?: flowOf(null))
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue =
            UserProfile(
              id = currentUserId,
              userId = currentUserId,
              displayName = "Explorador Local",
              roleTitle = "Modo Prueba Persistente",
            ),
        )
    } else {
      repository
        .observeUserProfile(currentUserId)
        .catch { error ->
          unwrapFirestoreError(error)?.let { Log.w(TAG, "Error observing profile", it) }
          emit(null)
        }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = null,
        )
    }

  val channelsState: StateFlow<UiState<SyncedCollection<Channel>>> =
    if (isDemoMode || repository == null) {
      (demoLocalRepository?.observeChannels() ?: flowOf(emptyList()))
        .map<List<Channel>, UiState<SyncedCollection<Channel>>> { items ->
          UiState.Success(SyncedCollection(items = items, hasPendingWrites = false, fromCache = true))
        }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = UiState.Loading,
        )
    } else {
      repository
        .observeChannels(currentUserId)
        .asUiState("No se pudieron cargar los canales")
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = UiState.Loading,
        )
    }

  val activeChannel: StateFlow<Channel?> =
    combine(channelsState, _selectedChannelId) { state, selectedId ->
        val list = (state as? UiState.Success)?.data?.items.orEmpty()
        list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
      )

  val messagesState: StateFlow<UiState<SyncedCollection<ChannelMessage>>> =
    combine(activeChannel, _messagePageLimit) { ch, limit -> Pair(ch, limit) }
      .flatMapLatest { (ch, limit) ->
        if (ch == null) {
          flowOf(
            UiState.Success(
              SyncedCollection(emptyList(), hasPendingWrites = false, fromCache = isDemoMode)
            )
          )
        } else if (isDemoMode || repository == null) {
          (demoLocalRepository?.observeMessages(ch.id) ?: flowOf(emptyList()))
            .map<List<ChannelMessage>, UiState<SyncedCollection<ChannelMessage>>> { list ->
              val paged = if (list.size > limit) list.takeLast(limit) else list
              UiState.Success(SyncedCollection(paged, hasPendingWrites = false, fromCache = true))
            }
        } else {
          repository.observeMessages(ch.id, currentUserId, limit).asUiState("Error al cargar mensajes")
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val markersState: StateFlow<UiState<SyncedCollection<GeoMarker>>> =
    activeChannel
      .flatMapLatest { ch ->
        if (ch == null) {
          flowOf(
            UiState.Success(
              SyncedCollection(emptyList(), hasPendingWrites = false, fromCache = isDemoMode)
            )
          )
        } else if (isDemoMode || repository == null) {
          (demoLocalRepository?.observeMarkers(ch.id) ?: flowOf(emptyList()))
            .map<List<GeoMarker>, UiState<SyncedCollection<GeoMarker>>> { list ->
              UiState.Success(SyncedCollection(list, hasPendingWrites = false, fromCache = true))
            }
        } else {
          repository.observeMarkers(ch.id, currentUserId).asUiState("Error al cargar marcadores")
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val docRevisions: StateFlow<List<DocRevision>> =
    activeChannel
      .flatMapLatest { ch ->
        if (ch == null) {
          flowOf(emptyList())
        } else if (isDemoMode || repository == null) {
          demoLocalRepository?.observeDocRevisions(ch.id) ?: flowOf(emptyList())
        } else {
          repository.observeDocRevisions(ch.id, currentUserId).catch { error ->
            unwrapFirestoreError(error)?.let { Log.w(TAG, "Error observing revisions", it) }
            emit(emptyList())
          }
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  val presenceList: StateFlow<List<ChannelPresence>> =
    if (isDemoMode || repository == null) {
      combine(activeChannel, _demoPresenceByChannel) { ch, map ->
          if (ch == null) emptyList() else map[ch.id].orEmpty().filter { it.sharingActive }
        }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = emptyList(),
        )
    } else {
      activeChannel
        .flatMapLatest { ch ->
          if (ch == null) flowOf(emptyList())
          else
            repository
              .observePresence(ch.id, currentUserId)
              .map { list -> list.filter { it.sharingActive && it.userId in ch.memberUids } }
              .catch { error ->
                unwrapFirestoreError(error)?.let { Log.w(TAG, "Error observing presence", it) }
                emit(emptyList())
              }
        }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = emptyList(),
        )
    }

  init {
    if (isDemoMode) {
      viewModelScope.launch { demoLocalRepository?.ensureSeeded() }
    } else {
      bootstrapWorkspace()
    }
    observeRemoteAlerts()
  }

  private fun observeRemoteAlerts() {
    viewModelScope.launch {
      activeChannel.collect { ch ->
        if (ch?.id != alertBaselineChannelId) {
          alertBaselineChannelId = ch?.id
          seenMarkerIds.clear()
          seenMessageIds.clear()
          markersInitializedForChannel = false
          messagesInitializedForChannel = false
        }
      }
    }
    viewModelScope.launch {
      markersState.collect { state ->
        val ch = activeChannel.value ?: return@collect
        if (ch.id != alertBaselineChannelId) return@collect
        val list = (state as? UiState.Success)?.data?.items ?: return@collect
        if (!markersInitializedForChannel) {
          seenMarkerIds.addAll(list.map { it.id })
          markersInitializedForChannel = true
          return@collect
        }
        val newlyAdded = list.filter { it.id !in seenMarkerIds }
        seenMarkerIds.addAll(newlyAdded.map { it.id })
        if (isDemoMode) return@collect
        newlyAdded
          .filter { it.creatorId != currentUserId && it.resolvedCategory() == MarkerCategory.INCIDENT }
          .forEach { incident ->
            val title = "Incidencia en #${ch.name}"
            val body = "${incident.updatedByName}: ${incident.title}"
            appendAlertHistory(
              FieldAlertItem(
                id = "mk_${incident.id}",
                channelId = ch.id,
                channelName = ch.name,
                title = title,
                body = body,
                markerId = incident.id,
                timestampMillis = System.currentTimeMillis(),
              )
            )
            if (_fieldAlertsEnabled.value) {
              FieldMediaAndAlerts.showFieldAlertNotification(
                context = appContext,
                notificationId = incident.id.hashCode(),
                title = title,
                body = body,
              )
            }
          }
      }
    }
    viewModelScope.launch {
      messagesState.collect { state ->
        val ch = activeChannel.value ?: return@collect
        if (ch.id != alertBaselineChannelId) return@collect
        val list = (state as? UiState.Success)?.data?.items ?: return@collect
        if (!messagesInitializedForChannel) {
          seenMessageIds.addAll(list.map { it.id })
          messagesInitializedForChannel = true
          return@collect
        }
        val newlyAdded = list.filter { it.id !in seenMessageIds }
        seenMessageIds.addAll(newlyAdded.map { it.id })
        if (isDemoMode) return@collect
        newlyAdded
          .filter { it.senderId != currentUserId && !it.linkedMarkerId.isNullOrBlank() }
          .forEach { msg ->
            val title = "#${ch.name} · ${msg.senderName}"
            val body = "${msg.linkedMarkerTitle ?: "Punto"}: ${msg.body}"
            appendAlertHistory(
              FieldAlertItem(
                id = "msg_${msg.id}",
                channelId = ch.id,
                channelName = ch.name,
                title = title,
                body = body,
                markerId = msg.linkedMarkerId,
                timestampMillis = System.currentTimeMillis(),
              )
            )
            if (_fieldAlertsEnabled.value) {
              FieldMediaAndAlerts.showFieldAlertNotification(
                context = appContext,
                notificationId = msg.id.hashCode(),
                title = title,
                body = body,
              )
            }
          }
      }
    }
  }

  private fun appendAlertHistory(alert: FieldAlertItem) {
    _fieldAlertsHistory.update { existing ->
      if (existing.any { it.id == alert.id }) existing
      else (listOf(alert) + existing).take(25)
    }
  }

  fun markAllAlertsRead() {
    _fieldAlertsHistory.update { list -> list.map { it.copy(unread = false) } }
  }

  fun openAlertTarget(alert: FieldAlertItem) {
    _fieldAlertsHistory.update { list ->
      list.map { if (it.id == alert.id) it.copy(unread = false) else it }
    }
    _selectedChannelId.value = alert.channelId
    if (!alert.markerId.isNullOrBlank()) {
      _focusedMarkerId.value = alert.markerId
      _activeTab.value = WorkspaceTab.MAP
    } else {
      _activeTab.value = WorkspaceTab.CHAT
    }
  }

  fun bootstrapWorkspace() {
    if (isDemoMode || repository == null) return
    viewModelScope.launch {
      _initError.value = null
      val res = repository.ensureUserProfileAndStarterWorkspace()
      res.onFailure { err ->
        _initError.value = err.localizedMessage ?: "No se pudo inicializar el espacio de trabajo."
      }
    }
  }

  fun selectTab(tab: WorkspaceTab) {
    _activeTab.value = tab
  }

  fun selectChannel(channelId: String, navigateToChat: Boolean = true) {
    _selectedChannelId.value = channelId
    _activeThreadParentId.value = null
    _focusedMarkerId.value = null
    _messagePageLimit.value = INITIAL_MESSAGE_PAGE_SIZE
    if (navigateToChat) {
      _activeTab.value = WorkspaceTab.CHAT
    }
  }

  fun loadOlderMessages() {
    _messagePageLimit.update { (it + INITIAL_MESSAGE_PAGE_SIZE).coerceAtMost(300) }
  }

  fun openMarkerOnMap(markerId: String) {
    _focusedMarkerId.value = markerId
    _activeTab.value = WorkspaceTab.MAP
  }

  fun clearFocusedMarker() {
    _focusedMarkerId.value = null
  }

  fun openThread(parentMessageId: String?) {
    _activeThreadParentId.value = parentMessageId
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setFieldAlertsEnabled(enabled: Boolean) {
    _fieldAlertsEnabled.value = enabled
  }

  fun dismissInviteDialog() {
    _activeInviteDialog.value = null
  }

  fun requestInviteCodeForChannel(channel: Channel) {
    if (isDemoMode || repository == null) {
      viewModelScope.launch {
        _snackbarEvents.emit(
          "Modo prueba local: inicia sesión con Google para generar códigos de invitación en la nube."
        )
      }
      return
    }
    viewModelScope.launch {
      _inviteActionLoading.value = true
      val result = repository.createChannelInvite(channel)
      _inviteActionLoading.value = false
      result
        .onSuccess { code ->
          _activeInviteDialog.value = ActiveInviteCode(channelName = channel.name, inviteCode = code)
        }
        .onFailure { e ->
          _snackbarEvents.emit(e.localizedMessage ?: "No se pudo generar el código de invitación.")
        }
    }
  }

  fun revokeInviteCode(channel: Channel) {
    if (isDemoMode || repository == null) return
    viewModelScope.launch {
      _inviteActionLoading.value = true
      val result = repository.revokeChannelInvite(channel)
      _inviteActionLoading.value = false
      result
        .onSuccess { _snackbarEvents.emit("Código de invitación revocado") }
        .onFailure { e ->
          _snackbarEvents.emit(e.localizedMessage ?: "No se pudo revocar el código.")
        }
    }
  }

  fun removeMember(channel: Channel, targetUid: String) {
    if (isDemoMode || repository == null) return
    viewModelScope.launch {
      val result = repository.removeChannelMember(channel, targetUid)
      result
        .onSuccess { _snackbarEvents.emit("Miembro retirado del canal") }
        .onFailure { e ->
          _snackbarEvents.emit(e.localizedMessage ?: "No se pudo retirar al miembro.")
        }
    }
  }

  fun leaveChannel(channel: Channel) {
    if (isDemoMode || repository == null) return
    viewModelScope.launch {
      val result = repository.leaveChannel(channel)
      result
        .onSuccess {
          if (_selectedChannelId.value == channel.id) {
            _selectedChannelId.value = null
          }
          _snackbarEvents.emit("Saliste de #${channel.name}")
        }
        .onFailure { e ->
          _snackbarEvents.emit(e.localizedMessage ?: "No pudiste salir del canal.")
        }
    }
  }

  fun joinChannelByCode(code: String, onSuccess: () -> Unit = {}) {
    if (isDemoMode || repository == null) {
      viewModelScope.launch {
        _snackbarEvents.emit(
          "Modo prueba local: inicia sesión con Google para unirte a canales de otros usuarios."
        )
      }
      return
    }
    viewModelScope.launch {
      _inviteActionLoading.value = true
      val result = repository.joinChannelByInviteCode(code)
      _inviteActionLoading.value = false
      result
        .onSuccess { (channelId, channelName) ->
          _selectedChannelId.value = channelId
          _messagePageLimit.value = INITIAL_MESSAGE_PAGE_SIZE
          _activeTab.value = WorkspaceTab.CHAT
          _snackbarEvents.emit("Conectado al canal #$channelName")
          onSuccess()
        }
        .onFailure { e ->
          _snackbarEvents.emit(e.localizedMessage ?: "No se pudo unir al canal con ese código.")
        }
    }
  }

  fun toggleOfflineSimulation(enableOffline: Boolean) {
    if (isDemoMode || db == null) {
      _offlineNetworkDisabled.value = enableOffline
      viewModelScope.launch {
        _snackbarEvents.emit(
          "Modo prueba persistente: tus cambios se guardan en SQLite local."
        )
      }
      return
    }
    viewModelScope.launch {
      try {
        if (enableOffline) {
          db.disableNetwork().await()
          _offlineNetworkDisabled.value = true
          _snackbarEvents.emit("Modo sin cobertura activo: tus cambios se guardan en cola local.")
        } else {
          db.enableNetwork().await()
          _offlineNetworkDisabled.value = false
          _snackbarEvents.emit("Conexión restablecida: sincronizando cambios pendientes.")
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _snackbarEvents.emit(e.localizedMessage ?: "Error al cambiar estado de red.")
      }
    }
  }

  fun createChannel(name: String, topic: String, type: ChannelType) {
    val cleanName = name.trim().lowercase().replace(Regex("\\s+"), "-").take(60)
    if (cleanName.length < 2) return
    val cleanTopic = topic.trim().take(160)

    if (isDemoMode || repository == null) {
      viewModelScope.launch {
        val newId = demoLocalRepository?.createChannel(cleanName, cleanTopic, type) ?: return@launch
        _selectedChannelId.value = newId
        _messagePageLimit.value = INITIAL_MESSAGE_PAGE_SIZE
        _activeTab.value = WorkspaceTab.CHAT
        _snackbarEvents.emit("Canal #$cleanName guardado en dispositivo")
      }
      return
    }

    viewModelScope.launch {
      val result = repository.createChannel(name, topic, type)
      result
        .onSuccess { newId ->
          _selectedChannelId.value = newId
          _messagePageLimit.value = INITIAL_MESSAGE_PAGE_SIZE
          _activeTab.value = WorkspaceTab.CHAT
          _snackbarEvents.emit("Canal #$name creado")
        }
        .onFailure { e ->
          _snackbarEvents.emit(e.localizedMessage ?: "Error al crear canal")
        }
    }
  }

  fun sendMessage(
    body: String,
    parentMessageId: String? = null,
    linkedMarker: GeoMarker? = null,
    attachmentLabel: String? = null,
    attachmentUri: String? = null,
    attachmentSnippet: String? = null,
  ) {
    val channel = activeChannel.value ?: return
    val cleanBody = body.trim().take(2000)
    if (cleanBody.isEmpty()) return

    viewModelScope.launch {
      val trimmedUri = attachmentUri?.trim()?.takeIf { it.isNotBlank() }
      val isGoogleDocUri =
        trimmedUri != null && GoogleWorkspaceManager.isValidGoogleWorkspaceUrl(trimmedUri)

      if (trimmedUri != null && trimmedUri.startsWith("https://") && !isGoogleDocUri) {
        _snackbarEvents.emit(
          "Por seguridad solo se permiten enlaces oficiales de docs.google.com o drive.google.com."
        )
        return@launch
      }

      val compressedBase64 =
        if (trimmedUri != null && !isGoogleDocUri) {
          runCatching { FieldMediaAndAlerts.compressImageToBase64(appContext, Uri.parse(trimmedUri)) }
            .getOrNull()
        } else {
          null
        }

      if (trimmedUri != null && !isGoogleDocUri && compressedBase64 == null) {
        _snackbarEvents.emit(
          "No se pudo procesar o comprimir la foto seleccionada. Intenta con otra imagen."
        )
        return@launch
      }

      // Best-effort live snippet enrichment if attaching a Google Doc while authorized
      var resolvedLabel = attachmentLabel
      var resolvedSnippet = attachmentSnippet
      if (isGoogleDocUri && resolvedSnippet.isNullOrBlank()) {
        val token = _workspaceAccessToken.value
        val docId = GoogleWorkspaceManager.extractGoogleDocId(trimmedUri!!)
        if (token != null && docId != null) {
          GoogleWorkspaceManager.fetchDocTextAndMeta(token, docId).onSuccess { (_, summary) ->
            if (resolvedLabel.isNullOrBlank()) resolvedLabel = summary.title
            resolvedSnippet = summary.snippet
          }
        }
      }

      if (isDemoMode || repository == null) {
        demoLocalRepository?.insertMessage(
          channelId = channel.id,
          body = cleanBody,
          parentMessageId = parentMessageId,
          linkedMarker = linkedMarker,
          attachmentLabel = resolvedLabel,
          attachmentUri = if (isGoogleDocUri) trimmedUri else trimmedUri,
          attachmentSnippet = resolvedSnippet,
          attachmentData = compressedBase64,
        )
        return@launch
      }

      // Never transmit device-local content:// URIs to Firestore; share compressed attachmentData or validated Google Doc URL
      val result =
        repository.sendMessage(
          channel = channel,
          body = cleanBody,
          parentMessageId = parentMessageId,
          linkedMarker = linkedMarker,
          attachmentLabel = resolvedLabel,
          attachmentUri = if (isGoogleDocUri) trimmedUri else null,
          attachmentSnippet = resolvedSnippet,
          attachmentData = compressedBase64,
        )
      result.onFailure { e ->
        _snackbarEvents.emit(e.localizedMessage ?: "No se pudo enviar el mensaje")
      }
    }
  }

  fun requestWorkspaceAuthAndLoadDocs(queryFilter: String = "") {
    if (isDemoMode || repository == null) {
      _workspaceState.update {
        it.copy(
          isAuthorized = false,
          isLoading = false,
          errorMessage = "Inicia sesión con Google para explorar tus documentos de Drive y Docs.",
        )
      }
      return
    }
    viewModelScope.launch {
      _workspaceState.update { it.copy(isLoading = true, errorMessage = null) }
      val cachedToken = _workspaceAccessToken.value
      if (cachedToken != null) {
        loadDocsWithToken(cachedToken, queryFilter)
        return@launch
      }
      when (val authRes = GoogleWorkspaceManager.authorize(appContext)) {
        is WorkspaceAuthResult.Authorized -> {
          _workspaceAccessToken.value = authRes.accessToken
          _workspaceState.update { it.copy(isAuthorized = true) }
          loadDocsWithToken(authRes.accessToken, queryFilter)
        }
        is WorkspaceAuthResult.NeedsConsent -> {
          _workspaceState.update { it.copy(isLoading = false) }
          _pendingWorkspaceConsent.emit(authRes.intentSender)
        }
        is WorkspaceAuthResult.Error -> {
          _workspaceState.update {
            it.copy(isAuthorized = false, isLoading = false, errorMessage = authRes.message)
          }
        }
      }
    }
  }

  fun onWorkspaceConsentCompleted(data: Intent?, queryFilter: String = "") {
    val tokenResult = GoogleWorkspaceManager.extractTokenFromIntent(appContext, data)
    tokenResult
      .onSuccess { token ->
        _workspaceAccessToken.value = token
        _workspaceState.update { it.copy(isAuthorized = true, errorMessage = null) }
        viewModelScope.launch { loadDocsWithToken(token, queryFilter) }
      }
      .onFailure { err ->
        _workspaceState.update {
          it.copy(
            isAuthorized = false,
            isLoading = false,
            errorMessage = err.localizedMessage ?: "Autorización de Google Docs cancelada.",
          )
        }
      }
  }

  private suspend fun loadDocsWithToken(token: String, queryFilter: String) {
    _workspaceState.update { it.copy(isLoading = true, errorMessage = null) }
    val res = GoogleWorkspaceManager.listGoogleDocs(token, queryFilter)
    res
      .onSuccess { docs ->
        _workspaceState.update {
          it.copy(isAuthorized = true, isLoading = false, docs = docs, errorMessage = null)
        }
      }
      .onFailure { err ->
        if (err is WorkspaceUnauthorizedException) {
          _workspaceAccessToken.value = null
          _workspaceState.update {
            it.copy(
              isAuthorized = false,
              isLoading = false,
              errorMessage = err.localizedMessage,
            )
          }
        } else {
          _workspaceState.update {
            it.copy(
              isLoading = false,
              errorMessage = err.localizedMessage ?: "No se pudieron cargar tus Google Docs.",
            )
          }
        }
      }
  }

  fun linkGoogleDocToMarker(marker: GeoMarker, doc: GoogleDocSummary) {
    val channel = activeChannel.value ?: return
    val safeUri = GoogleWorkspaceManager.canonicalDocUrl(doc.id, doc.webViewLink)
    if (!GoogleWorkspaceManager.isValidGoogleWorkspaceUrl(safeUri)) {
      viewModelScope.launch {
        _snackbarEvents.emit("Solo se permiten URLs oficiales de docs.google.com o drive.google.com.")
      }
      return
    }
    viewModelScope.launch {
      var snippet = doc.snippet
      if (snippet.isNullOrBlank() && doc.id.isNotBlank()) {
        val token = _workspaceAccessToken.value
        if (token != null) {
          GoogleWorkspaceManager.fetchDocTextAndMeta(token, doc.id).onSuccess { (_, summary) ->
            snippet = summary.snippet
          }
        }
      }
      if (isDemoMode || repository == null) {
        demoLocalRepository?.linkDocToMarker(
          channelId = channel.id,
          marker = marker,
          docId = doc.id.takeIf { it.isNotBlank() },
          docTitle = doc.title,
          docUri = safeUri,
          docSnippet = snippet,
        )
        _snackbarEvents.emit("Google Doc vinculado a \"${marker.title}\"")
        return@launch
      }
      repository
        .linkDocToMarker(
          channelId = channel.id,
          markerId = marker.id,
          docId = doc.id.takeIf { it.isNotBlank() },
          docTitle = doc.title,
          docUri = safeUri,
          docSnippet = snippet,
        )
        .onSuccess { _snackbarEvents.emit("Google Doc vinculado a \"${marker.title}\"") }
        .onFailure { e ->
          _snackbarEvents.emit(e.localizedMessage ?: "No se pudo vincular el documento al marcador.")
        }
    }
  }

  fun unlinkGoogleDocFromMarker(marker: GeoMarker) {
    val channel = activeChannel.value ?: return
    viewModelScope.launch {
      if (isDemoMode || repository == null) {
        demoLocalRepository?.linkDocToMarker(
          channelId = channel.id,
          marker = marker,
          docId = null,
          docTitle = null,
          docUri = null,
          docSnippet = null,
        )
        _snackbarEvents.emit("Documento desvinculado")
        return@launch
      }
      repository
        .linkDocToMarker(
          channelId = channel.id,
          markerId = marker.id,
          docId = null,
          docTitle = null,
          docUri = null,
          docSnippet = null,
        )
        .onSuccess { _snackbarEvents.emit("Documento desvinculado del marcador") }
        .onFailure { e ->
          _snackbarEvents.emit(e.localizedMessage ?: "No se pudo desvincular el documento.")
        }
    }
  }

  fun createAndShareGoogleDoc(
    title: String,
    bodyText: String,
    targetMarker: GeoMarker? = null,
    postToChat: Boolean = true,
    onCreated: (GoogleDocSummary) -> Unit = {},
  ) {
    val channel = activeChannel.value ?: return
    if (isDemoMode || repository == null) {
      viewModelScope.launch {
        _snackbarEvents.emit(
          "Inicia sesión con Google para crear documentos reales en Google Docs/Drive."
        )
      }
      return
    }
    viewModelScope.launch {
      _workspaceState.update { it.copy(isLoading = true, errorMessage = null) }
      val token = _workspaceAccessToken.value
      val resolvedToken =
        if (token != null) token
        else {
          when (val authRes = GoogleWorkspaceManager.authorize(appContext)) {
            is WorkspaceAuthResult.Authorized -> {
              _workspaceAccessToken.value = authRes.accessToken
              _workspaceState.update { it.copy(isAuthorized = true) }
              authRes.accessToken
            }
            is WorkspaceAuthResult.NeedsConsent -> {
              _workspaceState.update { it.copy(isLoading = false) }
              _pendingWorkspaceConsent.emit(authRes.intentSender)
              _snackbarEvents.emit("Autoriza Google Docs y vuelve a pulsar para crear el documento.")
              return@launch
            }
            is WorkspaceAuthResult.Error -> {
              _workspaceState.update { it.copy(isLoading = false, errorMessage = authRes.message) }
              _snackbarEvents.emit(authRes.message)
              return@launch
            }
          }
        }

      val result =
        GoogleWorkspaceManager.createAndShareGoogleDoc(
          accessToken = resolvedToken,
          title = title,
          bodyText = bodyText,
          makeAnyoneReader = true,
        )
      _workspaceState.update { it.copy(isLoading = false) }

      result
        .onSuccess { doc ->
          _workspaceState.update { state -> state.copy(docs = listOf(doc) + state.docs) }
          if (targetMarker != null) {
            repository.linkDocToMarker(
              channelId = channel.id,
              markerId = targetMarker.id,
              docId = doc.id,
              docTitle = doc.title,
              docUri = doc.webViewLink,
              docSnippet = doc.snippet,
            )
          }
          if (postToChat) {
            repository.sendMessage(
              channel = channel,
              body =
                if (targetMarker != null)
                  "Nuevo Google Doc vinculado al marcador #${targetMarker.title}: ${doc.title}"
                else "Nuevo documento compartido desde GeoNexo: ${doc.title}",
              linkedMarker = targetMarker,
              attachmentLabel = doc.title,
              attachmentUri = doc.webViewLink,
              attachmentSnippet = doc.snippet,
            )
          }
          val shareMsg =
            if (doc.shared) "Google Doc creado y compartido con enlace de lectura."
            else "Google Doc creado (verifica permisos de acceso en Drive para tu equipo)."
          _snackbarEvents.emit(shareMsg)
          onCreated(doc)
        }
        .onFailure { err ->
          if (err is WorkspaceUnauthorizedException) {
            _workspaceAccessToken.value = null
            _workspaceState.update { it.copy(isAuthorized = false) }
          }
          _snackbarEvents.emit(err.localizedMessage ?: "No se pudo crear el Google Doc.")
        }
    }
  }

  fun exportBitacoraToGoogleDoc(channel: Channel, markers: List<GeoMarker>) {
    val reportBody =
      buildString {
        append("Canal operativo: #").append(channel.name).append('\n')
        if (channel.topic.isNotBlank()) {
          append("Objetivo: ").append(channel.topic).append('\n')
        }
        append("Revisión local: ").append(channel.pinnedDocRevision).append('\n')
        append("Última actualización por: ").append(channel.pinnedDocUpdatedBy).append("\n\n")
        append(channel.pinnedDocBody.ifBlank { "Sin notas en bitácora." }).append("\n\n")
        if (markers.isNotEmpty()) {
          append("--- Marcadores activos en mapa (").append(markers.size).append(") ---\n")
          markers.forEach { mk ->
            append("• ").append(mk.title)
              .append(" [").append(mk.resolvedCategory().labelEs)
              .append(" · ").append(mk.resolvedStatus().labelEs).append("]")
              .append(" GPS(").append(String.format("%.5f", mk.lat))
              .append(", ").append(String.format("%.5f", mk.lng)).append(")\n")
            if (mk.notes.isNotBlank()) {
              append("  Notas: ").append(mk.notes).append('\n')
            }
          }
        }
      }
    createAndShareGoogleDoc(
      title = channel.pinnedDocTitle.ifBlank { "Bitácora #${channel.name}" },
      bodyText = reportBody,
      targetMarker = null,
      postToChat = true,
    )
  }

  fun fetchGoogleDocContentForGemini(
    docTitle: String,
    docUri: String,
    cachedSnippet: String?,
    onContentReady: (resolvedTitle: String, resolvedUri: String, contentText: String) -> Unit,
  ) {
    val docId = GoogleWorkspaceManager.extractGoogleDocId(docUri)
    if (isDemoMode || repository == null || docId == null) {
      onContentReady(
        docTitle,
        docUri,
        cachedSnippet?.takeIf { it.isNotBlank() }
          ?: "Documento enlazado ($docUri). Sin lectura API directa disponible en modo local.",
      )
      return
    }
    viewModelScope.launch {
      var token = _workspaceAccessToken.value
      if (token == null) {
        when (val authRes = GoogleWorkspaceManager.authorize(appContext)) {
          is WorkspaceAuthResult.Authorized -> {
            token = authRes.accessToken
            _workspaceAccessToken.value = token
            _workspaceState.update { it.copy(isAuthorized = true) }
          }
          is WorkspaceAuthResult.NeedsConsent -> {
            // Fall back to cached snippet immediately if available, and also inform user
            if (!cachedSnippet.isNullOrBlank()) {
              _snackbarEvents.emit("Analizando extracto guardado. Autoriza Drive para lectura completa.")
              onContentReady(docTitle, docUri, cachedSnippet)
            } else {
              _pendingWorkspaceConsent.emit(authRes.intentSender)
              _snackbarEvents.emit("Autoriza lectura de Google Docs/Drive para analizar el contenido completo.")
            }
            return@launch
          }
          is WorkspaceAuthResult.Error -> {
            onContentReady(docTitle, docUri, cachedSnippet ?: "")
            return@launch
          }
        }
      }

      val fetchResult = GoogleWorkspaceManager.fetchDocTextAndMeta(token!!, docId)
      fetchResult
        .onSuccess { (fullText, summary) ->
          onContentReady(summary.title, summary.webViewLink, fullText)
        }
        .onFailure { err ->
          if (err is WorkspaceUnauthorizedException) {
            _workspaceAccessToken.value = null
            _workspaceState.update { it.copy(isAuthorized = false) }
          }
          if (!cachedSnippet.isNullOrBlank()) {
            _snackbarEvents.emit("${err.localizedMessage} Usando extracto en caché.")
            onContentReady(docTitle, docUri, cachedSnippet)
          } else {
            _snackbarEvents.emit(err.localizedMessage ?: "No se pudo leer el Google Doc.")
          }
        }
    }
  }

  fun createMarker(
    title: String,
    notes: String,
    category: MarkerCategory,
    status: MarkerStatus,
    mapX: Double = 0.5,
    mapY: Double = 0.5,
    lat: Double? = null,
    lng: Double? = null,
  ) {
    val channel = activeChannel.value ?: return
    val cleanTitle = title.trim().take(80)
    if (cleanTitle.length < 2) return
    val baseCoords = _lastKnownCoords.value
    val finalLat = (lat ?: (baseCoords.first + (0.5 - mapY) * 0.02)).coerceIn(-90.0, 90.0)
    val finalLng = (lng ?: (baseCoords.second + (mapX - 0.5) * 0.02)).coerceIn(-180.0, 180.0)
    val finalMapX =
      if (lat != null && lng != null) {
        (0.5 + (lng - baseCoords.second) / 0.02).coerceIn(0.05, 0.95)
      } else {
        mapX.coerceIn(0.0, 1.0)
      }
    val finalMapY =
      if (lat != null && lng != null) {
        (0.5 - (lat - baseCoords.first) / 0.02).coerceIn(0.05, 0.95)
      } else {
        mapY.coerceIn(0.0, 1.0)
      }

    if (isDemoMode || repository == null) {
      viewModelScope.launch {
        val markerId =
          demoLocalRepository?.createMarker(
            channelId = channel.id,
            title = cleanTitle,
            notes = notes,
            category = category,
            status = status,
            lat = finalLat,
            lng = finalLng,
            mapX = finalMapX,
            mapY = finalMapY,
          ) ?: return@launch
        _focusedMarkerId.value = markerId
        _snackbarEvents.emit("Marcador \"$cleanTitle\" guardado localmente")
      }
      return
    }

    viewModelScope.launch {
      val result =
        repository.createMarker(
          channel = channel,
          title = cleanTitle,
          notes = notes,
          category = category,
          status = status,
          lat = finalLat,
          lng = finalLng,
          mapX = finalMapX,
          mapY = finalMapY,
        )
      result
        .onSuccess { markerId ->
          _focusedMarkerId.value = markerId
          _snackbarEvents.emit("Marcador \"$cleanTitle\" guardado en el mapa")
        }
        .onFailure { e ->
          _snackbarEvents.emit(e.localizedMessage ?: "No se pudo guardar el marcador")
        }
    }
  }

  fun updateMarker(
    markerId: String,
    title: String,
    notes: String,
    category: MarkerCategory,
    status: MarkerStatus,
  ) {
    val channel = activeChannel.value ?: return
    val cleanTitle = title.trim().take(80)
    if (cleanTitle.length < 2) return

    if (isDemoMode || repository == null) {
      val currentMarker =
        (markersState.value as? UiState.Success)?.data?.items?.firstOrNull { it.id == markerId }
          ?: return
      viewModelScope.launch {
        demoLocalRepository?.updateMarker(
          channelId = channel.id,
          marker = currentMarker,
          title = cleanTitle,
          notes = notes,
          category = category,
          status = status,
        )
        _snackbarEvents.emit("Marcador actualizado")
      }
      return
    }

    viewModelScope.launch {
      val result =
        repository.updateMarker(
          channelId = channel.id,
          markerId = markerId,
          title = cleanTitle,
          notes = notes,
          category = category,
          status = status,
        )
      result
        .onSuccess { _snackbarEvents.emit("Marcador actualizado") }
        .onFailure { e -> _snackbarEvents.emit(e.localizedMessage ?: "Error al actualizar") }
    }
  }

  fun deleteMarker(markerId: String) {
    val channel = activeChannel.value ?: return

    if (isDemoMode || repository == null) {
      viewModelScope.launch {
        demoLocalRepository?.deleteMarker(markerId)
        if (_focusedMarkerId.value == markerId) _focusedMarkerId.value = null
        _snackbarEvents.emit("Marcador eliminado")
      }
      return
    }

    viewModelScope.launch {
      val result = repository.deleteMarker(channel.id, markerId)
      result
        .onSuccess {
          if (_focusedMarkerId.value == markerId) _focusedMarkerId.value = null
          _snackbarEvents.emit("Marcador eliminado")
        }
        .onFailure { e -> _snackbarEvents.emit(e.localizedMessage ?: "Error al eliminar") }
    }
  }

  fun updatePinnedDoc(title: String, body: String) {
    val channel = activeChannel.value ?: return
    val cleanTitle = title.trim().take(100)
    if (cleanTitle.isEmpty()) return

    if (isDemoMode || repository == null) {
      viewModelScope.launch {
        demoLocalRepository?.updatePinnedDoc(channel, cleanTitle, body)
        _snackbarEvents.emit("Bitácora e historial guardados localmente")
      }
      return
    }

    viewModelScope.launch {
      val result = repository.updatePinnedDocument(channel, cleanTitle, body)
      result
        .onSuccess { _snackbarEvents.emit("Bitácora guardada (Rev. ${channel.pinnedDocRevision + 1})") }
        .onFailure { e -> _snackbarEvents.emit(e.localizedMessage ?: "Error al guardar bitácora") }
    }
  }

  fun restoreDocRevision(revision: DocRevision) {
    updatePinnedDoc(title = revision.title, body = revision.body)
  }

  fun appendToPinnedDoc(sectionText: String) {
    val channel = activeChannel.value ?: return
    val updatedBody =
      if (channel.pinnedDocBody.isBlank()) sectionText
      else "${channel.pinnedDocBody}\n\n$sectionText"
    updatePinnedDoc(channel.pinnedDocTitle, updatedBody)
  }

  fun applyGeminiAction(action: GeminiSuggestedAction) {
    when (action) {
      is GeminiSuggestedAction.CreateMarker -> {
        createMarker(
          title = action.title,
          notes = action.notes,
          category = action.category,
          status = action.status,
          mapX = 0.5,
          mapY = 0.5,
        )
        _activeTab.value = WorkspaceTab.MAP
      }
      is GeminiSuggestedAction.AppendToDoc -> {
        appendToPinnedDoc(action.sectionText)
        _activeTab.value = WorkspaceTab.DOC
      }
      is GeminiSuggestedAction.PostToChannelChat -> {
        sendMessage(body = action.messageBody)
        _activeTab.value = WorkspaceTab.CHAT
      }
    }
  }

  fun updateAccessibilityAndTheme(
    highContrastMode: Boolean,
    darkMode: Boolean,
  ) {
    val currentLocationEnabled =
      userProfile.value?.locationSharingEnabled ?: _locationSharingRequested.value
    if (isDemoMode || repository == null) {
      viewModelScope.launch {
        demoLocalRepository?.updatePreferences(
          highContrast = highContrastMode,
          darkMode = darkMode,
          locationSharing = currentLocationEnabled,
        )
      }
      return
    }
    viewModelScope.launch {
      repository
        .updateUserPreferences(
          highContrastMode = highContrastMode,
          darkMode = darkMode,
          locationSharingEnabled = currentLocationEnabled,
        )
        .onFailure { e ->
          _snackbarEvents.emit(e.localizedMessage ?: "No se pudieron guardar las preferencias")
        }
    }
  }

  fun setLocationSharingEnabled(
    enabled: Boolean,
    lat: Double? = null,
    lng: Double? = null,
    accuracy: Double = 14.0,
    silentUpdate: Boolean = false,
  ) {
    _locationSharingRequested.value = enabled
    if (lat != null && lng != null) {
      _lastKnownCoords.value = Pair(lat, lng)
    }
    val coords = _lastKnownCoords.value
    val channel = activeChannel.value
    val profile = userProfile.value

    if (isDemoMode || repository == null) {
      viewModelScope.launch {
        demoLocalRepository?.updatePreferences(
          highContrast = profile?.highContrastMode ?: false,
          darkMode = profile?.darkMode ?: false,
          locationSharing = enabled,
        )
        if (channel != null) {
          val presence =
            ChannelPresence(
              id = currentUserId,
              channelId = channel.id,
              userId = currentUserId,
              displayName = profile?.displayName ?: "Explorador Local",
              memberUids = listOf(currentUserId),
              sharingActive = enabled,
              lat = coords.first,
              lng = coords.second,
              mapX = 0.52,
              mapY = 0.48,
              accuracyMeters = accuracy,
              updatedAt = Timestamp.now(),
            )
          _demoPresenceByChannel.update { map -> map + (channel.id to listOf(presence)) }
        }
        if (!silentUpdate) {
          _snackbarEvents.emit(
            if (enabled) "GPS local activado (bajo consumo)"
            else "Ubicación pausada inmediatamente"
          )
        }
      }
      return
    }

    viewModelScope.launch {
      if (!silentUpdate) {
        repository.updateUserPreferences(
          highContrastMode = profile?.highContrastMode ?: false,
          darkMode = profile?.darkMode ?: false,
          locationSharingEnabled = enabled,
        )
      }
      if (channel != null) {
        repository.updatePresence(
          channel = channel,
          sharingActive = enabled,
          lat = coords.first,
          lng = coords.second,
          mapX = 0.52,
          mapY = 0.48,
          accuracyMeters = accuracy,
        )
      }
      if (!silentUpdate) {
        _snackbarEvents.emit(
          if (enabled) "Ubicación en vivo activada para este canal"
          else "Ubicación pausada inmediatamente"
        )
      }
    }
  }

  private fun <T> Flow<T>.asUiState(fallbackMessage: String): Flow<UiState<T>> =
    map<T, UiState<T>> { UiState.Success(it) }.catch { error ->
      val firestoreError = unwrapFirestoreError(error)
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, fallbackMessage, rootError)
      emit(UiState.Error(rootError.localizedMessage ?: fallbackMessage))
    }

  private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
    generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()

  private companion object {
    const val TAG = "GeoNexoVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
    const val INITIAL_MESSAGE_PAGE_SIZE = 30
  }
}
