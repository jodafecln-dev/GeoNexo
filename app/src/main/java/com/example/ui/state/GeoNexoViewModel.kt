package com.example.ui.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiSuggestedAction
import com.example.data.model.Channel
import com.example.data.model.ChannelMessage
import com.example.data.model.ChannelPresence
import com.example.data.model.ChannelType
import com.example.data.model.GeoMarker
import com.example.data.model.MarkerCategory
import com.example.data.model.MarkerStatus
import com.example.data.model.MessageType
import com.example.data.model.SyncedCollection
import com.example.data.model.UserProfile
import com.example.data.remote.GeoNexoRepository
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
import java.util.UUID

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

@OptIn(ExperimentalCoroutinesApi::class)
class GeoNexoViewModel(
  private val repository: GeoNexoRepository?,
  private val db: FirebaseFirestore?,
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

  private val _offlineNetworkDisabled = MutableStateFlow(false)
  val offlineNetworkDisabled: StateFlow<Boolean> = _offlineNetworkDisabled.asStateFlow()

  private val _locationSharingRequested = MutableStateFlow(false)
  val locationSharingRequested: StateFlow<Boolean> = _locationSharingRequested.asStateFlow()

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

  // Local in-memory state used exclusively when isDemoMode == true (zero Firestore calls)
  private val _demoProfile =
    MutableStateFlow(
      UserProfile(
        id = currentUserId,
        userId = currentUserId,
        displayName = "Explorador Local",
        roleTitle = "Modo Prueba (sin cuenta)",
        highContrastMode = false,
        darkMode = false,
        locationSharingEnabled = false,
      )
    )
  private val _demoChannels = MutableStateFlow(createInitialDemoChannels(currentUserId))
  private val _demoMessagesByChannel = MutableStateFlow(createInitialDemoMessages(currentUserId))
  private val _demoMarkersByChannel = MutableStateFlow(createInitialDemoMarkers(currentUserId))
  private val _demoPresenceByChannel = MutableStateFlow<Map<String, List<ChannelPresence>>>(emptyMap())

  val userProfile: StateFlow<UserProfile?> =
    if (isDemoMode || repository == null) {
      _demoProfile.asStateFlow()
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
      _demoChannels
        .map<List<Channel>, UiState<SyncedCollection<Channel>>> { items ->
          UiState.Success(SyncedCollection(items = items, hasPendingWrites = false, fromCache = true))
        }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = UiState.Success(
            SyncedCollection(items = _demoChannels.value, hasPendingWrites = false, fromCache = true)
          ),
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
        initialValue = if (isDemoMode) _demoChannels.value.firstOrNull() else null,
      )

  val messagesState: StateFlow<UiState<SyncedCollection<ChannelMessage>>> =
    if (isDemoMode || repository == null) {
      combine(activeChannel, _demoMessagesByChannel) { ch, map ->
          val list = if (ch == null) emptyList() else map[ch.id].orEmpty()
          UiState.Success(SyncedCollection(list, hasPendingWrites = false, fromCache = true))
        }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = UiState.Loading,
        )
    } else {
      activeChannel
        .flatMapLatest { ch ->
          if (ch == null) {
            flowOf(
              UiState.Success(
                SyncedCollection(emptyList(), hasPendingWrites = false, fromCache = false)
              )
            )
          } else {
            repository.observeMessages(ch.id, currentUserId).asUiState("Error al cargar mensajes")
          }
        }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = UiState.Loading,
        )
    }

  val markersState: StateFlow<UiState<SyncedCollection<GeoMarker>>> =
    if (isDemoMode || repository == null) {
      combine(activeChannel, _demoMarkersByChannel) { ch, map ->
          val list = if (ch == null) emptyList() else map[ch.id].orEmpty()
          UiState.Success(SyncedCollection(list, hasPendingWrites = false, fromCache = true))
        }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = UiState.Loading,
        )
    } else {
      activeChannel
        .flatMapLatest { ch ->
          if (ch == null) {
            flowOf(
              UiState.Success(
                SyncedCollection(emptyList(), hasPendingWrites = false, fromCache = false)
              )
            )
          } else {
            repository.observeMarkers(ch.id, currentUserId).asUiState("Error al cargar marcadores")
          }
        }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = UiState.Loading,
        )
    }

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
            repository.observePresence(ch.id, currentUserId).catch { error ->
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
    if (!isDemoMode) {
      bootstrapWorkspace()
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
    if (navigateToChat) {
      _activeTab.value = WorkspaceTab.CHAT
    }
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
          "Modo prueba local: todos tus cambios se guardan localmente en memoria."
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
      val newId = "demo_ch_${UUID.randomUUID().toString().take(8)}"
      val newChannel =
        Channel(
          id = newId,
          ownerId = currentUserId,
          memberUids = listOf(currentUserId),
          name = cleanName,
          topic = cleanTopic,
          channelType = type.name,
          pinnedDocTitle = "Bitácora — $cleanName",
          pinnedDocBody = "Notas operativas iniciales del canal #$cleanName.",
          pinnedDocUpdatedBy = _demoProfile.value.displayName,
          createdAt = Timestamp.now(),
          updatedAt = Timestamp.now(),
        )
      _demoChannels.update { listOf(newChannel) + it }
      _selectedChannelId.value = newId
      _activeTab.value = WorkspaceTab.CHAT
      viewModelScope.launch { _snackbarEvents.emit("Canal #$cleanName creado (prueba local)") }
      return
    }

    viewModelScope.launch {
      val result = repository.createChannel(name, topic, type)
      result
        .onSuccess { newId ->
          _selectedChannelId.value = newId
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
  ) {
    val channel = activeChannel.value ?: return
    val cleanBody = body.trim().take(2000)
    if (cleanBody.isEmpty()) return

    if (isDemoMode || repository == null) {
      val messageType =
        when {
          linkedMarker != null -> MessageType.GEO_PIN
          !attachmentLabel.isNullOrBlank() -> MessageType.PHOTO
          else -> MessageType.TEXT
        }
      val msg =
        ChannelMessage(
          id = "demo_msg_${UUID.randomUUID().toString().take(8)}",
          channelId = channel.id,
          senderId = currentUserId,
          senderName = _demoProfile.value.displayName,
          memberUids = listOf(currentUserId),
          body = cleanBody,
          parentMessageId = parentMessageId?.takeIf { it.isNotBlank() },
          linkedMarkerId = linkedMarker?.id?.takeIf { it.isNotBlank() },
          linkedMarkerTitle = linkedMarker?.title?.take(80)?.takeIf { it.isNotBlank() },
          attachmentLabel = attachmentLabel?.take(120)?.takeIf { it.isNotBlank() },
          attachmentUri = attachmentUri?.take(500)?.takeIf { it.isNotBlank() },
          messageType = messageType.name,
          createdAt = Timestamp.now(),
          updatedAt = Timestamp.now(),
        )
      _demoMessagesByChannel.update { map ->
        val current = map[channel.id].orEmpty()
        map + (channel.id to (current + msg))
      }
      return
    }

    viewModelScope.launch {
      val result =
        repository.sendMessage(
          channel = channel,
          body = body,
          parentMessageId = parentMessageId,
          linkedMarker = linkedMarker,
          attachmentLabel = attachmentLabel,
          attachmentUri = attachmentUri,
        )
      result.onFailure { e ->
        _snackbarEvents.emit(e.localizedMessage ?: "No se pudo enviar el mensaje")
      }
    }
  }

  fun createMarker(
    title: String,
    notes: String,
    category: MarkerCategory,
    status: MarkerStatus,
    mapX: Double,
    mapY: Double,
  ) {
    val channel = activeChannel.value ?: return
    val cleanTitle = title.trim().take(80)
    if (cleanTitle.length < 2) return
    val baseCoords = _lastKnownCoords.value
    val lat = (baseCoords.first + (0.5 - mapY) * 0.02).coerceIn(-90.0, 90.0)
    val lng = (baseCoords.second + (mapX - 0.5) * 0.02).coerceIn(-180.0, 180.0)

    if (isDemoMode || repository == null) {
      val markerId = "demo_mk_${UUID.randomUUID().toString().take(8)}"
      val mk =
        GeoMarker(
          id = markerId,
          channelId = channel.id,
          creatorId = currentUserId,
          updatedByName = _demoProfile.value.displayName,
          memberUids = listOf(currentUserId),
          title = cleanTitle,
          notes = notes.trim().take(1000),
          category = category.name,
          status = status.name,
          lat = lat,
          lng = lng,
          mapX = mapX.coerceIn(0.0, 1.0),
          mapY = mapY.coerceIn(0.0, 1.0),
          createdAt = Timestamp.now(),
          updatedAt = Timestamp.now(),
        )
      _demoMarkersByChannel.update { map ->
        val current = map[channel.id].orEmpty()
        map + (channel.id to (listOf(mk) + current))
      }
      _focusedMarkerId.value = markerId
      viewModelScope.launch { _snackbarEvents.emit("Marcador \"$cleanTitle\" guardado en modo prueba") }
      return
    }

    viewModelScope.launch {
      val result =
        repository.createMarker(
          channel = channel,
          title = title,
          notes = notes,
          category = category,
          status = status,
          lat = lat,
          lng = lng,
          mapX = mapX,
          mapY = mapY,
        )
      result
        .onSuccess { markerId ->
          _focusedMarkerId.value = markerId
          _snackbarEvents.emit("Marcador \"$title\" guardado en el mapa")
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
      _demoMarkersByChannel.update { map ->
        val updated =
          map[channel.id].orEmpty().map { mk ->
            if (mk.id == markerId) {
              mk.copy(
                title = cleanTitle,
                notes = notes.trim().take(1000),
                category = category.name,
                status = status.name,
                updatedByName = _demoProfile.value.displayName,
                updatedAt = Timestamp.now(),
              )
            } else mk
          }
        map + (channel.id to updated)
      }
      viewModelScope.launch { _snackbarEvents.emit("Marcador actualizado") }
      return
    }

    viewModelScope.launch {
      val result =
        repository.updateMarker(
          channelId = channel.id,
          markerId = markerId,
          title = title,
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
      _demoMarkersByChannel.update { map ->
        val remaining = map[channel.id].orEmpty().filterNot { it.id == markerId }
        map + (channel.id to remaining)
      }
      if (_focusedMarkerId.value == markerId) _focusedMarkerId.value = null
      viewModelScope.launch { _snackbarEvents.emit("Marcador eliminado") }
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
      _demoChannels.update { list ->
        list.map { ch ->
          if (ch.id == channel.id) {
            ch.copy(
              pinnedDocTitle = cleanTitle,
              pinnedDocBody = body.take(12000),
              pinnedDocUpdatedBy = _demoProfile.value.displayName,
              updatedAt = Timestamp.now(),
            )
          } else ch
        }
      }
      viewModelScope.launch { _snackbarEvents.emit("Bitácora guardada localmente") }
      return
    }

    viewModelScope.launch {
      val result = repository.updatePinnedDocument(channel.id, title, body)
      result
        .onSuccess { _snackbarEvents.emit("Bitácora del canal guardada") }
        .onFailure { e -> _snackbarEvents.emit(e.localizedMessage ?: "Error al guardar bitácora") }
    }
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
    if (isDemoMode || repository == null) {
      _demoProfile.update {
        it.copy(
          highContrastMode = highContrastMode,
          darkMode = darkMode,
          updatedAt = Timestamp.now(),
        )
      }
      return
    }
    val currentLocationEnabled =
      userProfile.value?.locationSharingEnabled ?: _locationSharingRequested.value
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
  ) {
    _locationSharingRequested.value = enabled
    if (lat != null && lng != null) {
      _lastKnownCoords.value = Pair(lat, lng)
    }
    val coords = _lastKnownCoords.value
    val channel = activeChannel.value

    if (isDemoMode || repository == null) {
      _demoProfile.update {
        it.copy(locationSharingEnabled = enabled, updatedAt = Timestamp.now())
      }
      if (channel != null) {
        val presence =
          ChannelPresence(
            id = currentUserId,
            channelId = channel.id,
            userId = currentUserId,
            displayName = _demoProfile.value.displayName,
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
      viewModelScope.launch {
        _snackbarEvents.emit(
          if (enabled) "GPS local activado (solo visible en tu dispositivo)"
          else "Ubicación pausada inmediatamente"
        )
      }
      return
    }

    val profile = userProfile.value
    viewModelScope.launch {
      repository.updateUserPreferences(
        highContrastMode = profile?.highContrastMode ?: false,
        darkMode = profile?.darkMode ?: false,
        locationSharingEnabled = enabled,
      )
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
      _snackbarEvents.emit(
        if (enabled) "Ubicación en vivo activada para este canal"
        else "Ubicación pausada inmediatamente"
      )
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

    fun createInitialDemoChannels(uid: String): List<Channel> {
      val now = Timestamp.now()
      return listOf(
        Channel(
          id = "demo_sector_norte",
          ownerId = uid,
          memberUids = listOf(uid),
          name = "sector-norte-obra",
          topic = "Inspección de subestación, postes y zanja hidráulica",
          channelType = ChannelType.FIELD.name,
          pinnedDocTitle = "Bitácora Operativa — Sector Norte",
          pinnedDocBody =
            "Objetivo del turno:\n• Verificar reposición del poste #14 en cruce Av. Industrial.\n• Validar presión de línea hidráulica antes de las 17:00.\n\nChecklist de campo:\n[x] Inspección visual de acometida\n[ ] Registro fotográfico en marcador #Poste-14\n[ ] Cierre de válvula secundaria",
          pinnedDocUpdatedBy = "Explorador Local",
          createdAt = now,
          updatedAt = now,
        ),
        Channel(
          id = "demo_logistica_sur",
          ownerId = uid,
          memberUids = listOf(uid),
          name = "logistica-ruta-sur",
          topic = "Entregas de material y puntos de control en ruta metropolitana",
          channelType = ChannelType.LOGISTICS.name,
          pinnedDocTitle = "Protocolo de Entregas Sur",
          pinnedDocBody =
            "Ventana de descarga:\n• Unidad 04: 09:00 - 11:30\n• Confirmar recepción con foto adjunta en el chat del canal.",
          pinnedDocUpdatedBy = "Explorador Local",
          createdAt = now,
          updatedAt = now,
        ),
      )
    }

    fun createInitialDemoMarkers(uid: String): Map<String, List<GeoMarker>> {
      val now = Timestamp.now()
      return mapOf(
        "demo_sector_norte" to
          listOf(
            GeoMarker(
              id = "demo_mk_1",
              channelId = "demo_sector_norte",
              creatorId = uid,
              updatedByName = "Cuadrilla Norte",
              memberUids = listOf(uid),
              title = "Poste #14 — Crucero Norte",
              notes = "Aislador fracturado detectado en ronda matutina. Requiere grúa ligera.",
              category = MarkerCategory.INCIDENT.name,
              status = MarkerStatus.OPEN.name,
              lat = 19.4352,
              lng = -99.1388,
              mapX = 0.34,
              mapY = 0.38,
              createdAt = now,
              updatedAt = now,
            ),
            GeoMarker(
              id = "demo_mk_2",
              channelId = "demo_sector_norte",
              creatorId = uid,
              updatedByName = "Oficina Técnica",
              memberUids = listOf(uid),
              title = "Tramo Zanja B2",
              notes = "Compactación al 85%. Pendiente prueba de hermeticidad.",
              category = MarkerCategory.PROGRESS.name,
              status = MarkerStatus.IN_PROGRESS.name,
              lat = 19.4318,
              lng = -99.1315,
              mapX = 0.62,
              mapY = 0.56,
              createdAt = now,
              updatedAt = now,
            ),
            GeoMarker(
              id = "demo_mk_3",
              channelId = "demo_sector_norte",
              creatorId = uid,
              updatedByName = "Almacén",
              memberUids = listOf(uid),
              title = "Punto Acceso Almacén",
              notes = "Ingreso autorizado para camiones hasta 18:00 h.",
              category = MarkerCategory.CHECKPOINT.name,
              status = MarkerStatus.RESOLVED.name,
              lat = 19.4295,
              lng = -99.1260,
              mapX = 0.78,
              mapY = 0.29,
              createdAt = now,
              updatedAt = now,
            ),
          ),
        "demo_logistica_sur" to
          listOf(
            GeoMarker(
              id = "demo_mk_4",
              channelId = "demo_logistica_sur",
              creatorId = uid,
              updatedByName = "Unidad 04",
              memberUids = listOf(uid),
              title = "Bodega Central Sur",
              notes = "Lote de transformadores listo para despacho.",
              category = MarkerCategory.DELIVERY.name,
              status = MarkerStatus.IN_PROGRESS.name,
              lat = 19.3810,
              lng = -99.1520,
              mapX = 0.48,
              mapY = 0.72,
              createdAt = now,
              updatedAt = now,
            )
          ),
      )
    }

    fun createInitialDemoMessages(uid: String): Map<String, List<ChannelMessage>> {
      val now = Timestamp.now()
      return mapOf(
        "demo_sector_norte" to
          listOf(
            ChannelMessage(
              id = "demo_msg_1",
              channelId = "demo_sector_norte",
              senderId = "demo_colleague_1",
              senderName = "Ing. Valeria (Campo)",
              memberUids = listOf(uid),
              body =
                "Modo prueba activo: ya puedes chatear, crear puntos en el mapa y editar la bitácora sin cuenta.",
              linkedMarkerId = "demo_mk_1",
              linkedMarkerTitle = "Poste #14 — Crucero Norte",
              messageType = MessageType.GEO_PIN.name,
              createdAt = now,
              updatedAt = now,
            ),
            ChannelMessage(
              id = "demo_msg_2",
              channelId = "demo_sector_norte",
              senderId = "demo_colleague_2",
              senderName = "Carlos (Oficina)",
              memberUids = listOf(uid),
              body =
                "Cuando quieras usar Gemini IA o conectar en tiempo real con otro compañero, inicia sesión con Google desde la pestaña Gemini o Ajustes.",
              messageType = MessageType.TEXT.name,
              createdAt = now,
              updatedAt = now,
            ),
          ),
        "demo_logistica_sur" to
          listOf(
            ChannelMessage(
              id = "demo_msg_3",
              channelId = "demo_logistica_sur",
              senderId = "demo_colleague_2",
              senderName = "Unidad 04",
              memberUids = listOf(uid),
              body = "En espera de confirmación de salida en Bodega Central Sur.",
              linkedMarkerId = "demo_mk_4",
              linkedMarkerTitle = "Bodega Central Sur",
              messageType = MessageType.GEO_PIN.name,
              createdAt = now,
              updatedAt = now,
            )
          ),
      )
    }
  }
}
