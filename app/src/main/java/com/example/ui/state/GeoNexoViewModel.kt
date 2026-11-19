package com.example.ui.state

import android.util.Log
import com.example.data.model.Channel
import com.example.data.model.ChannelMessage
import com.example.data.model.ChannelPresence
import com.example.data.model.ChannelType
import com.example.data.model.GeoMarker
import com.example.data.model.MarkerCategory
import com.example.data.model.MarkerStatus
import com.example.data.model.SyncedCollection
import com.example.data.model.UserProfile
import com.example.data.remote.GeoNexoRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

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
}

@OptIn(ExperimentalCoroutinesApi::class)
class GeoNexoViewModel(
  private val repository: GeoNexoRepository,
  private val db: FirebaseFirestore,
  private val currentUserId: String,
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

  val userProfile: StateFlow<UserProfile?> =
    repository
      .observeUserProfile(currentUserId)
      .catch { error ->
        unwrapFirestoreError(error)?.let {
          Log.w(TAG, "Error observing profile", it)
        }
        emit(null)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
      )

  val channelsState: StateFlow<UiState<SyncedCollection<Channel>>> =
    repository
      .observeChannels(currentUserId)
      .asUiState("No se pudieron cargar los canales")
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

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
    activeChannel
      .flatMapLatest { ch ->
        if (ch == null) {
          flowOf(UiState.Success(SyncedCollection(emptyList(), hasPendingWrites = false, fromCache = false)))
        } else {
          repository.observeMessages(ch.id, currentUserId).asUiState("Error al cargar mensajes")
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
          flowOf(UiState.Success(SyncedCollection(emptyList(), hasPendingWrites = false, fromCache = false)))
        } else {
          repository.observeMarkers(ch.id, currentUserId).asUiState("Error al cargar marcadores")
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val presenceList: StateFlow<List<ChannelPresence>> =
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

  init {
    bootstrapWorkspace()
  }

  fun bootstrapWorkspace() {
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

  fun toggleOfflineSimulation(enableOffline: Boolean) {
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
    val baseCoords = _lastKnownCoords.value
    val lat = baseCoords.first + (0.5 - mapY) * 0.02
    val lng = baseCoords.second + (mapX - 0.5) * 0.02

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
    viewModelScope.launch {
      val result = repository.updatePinnedDocument(channel.id, title, body)
      result
        .onSuccess { _snackbarEvents.emit("Bitácora del canal guardada") }
        .onFailure { e -> _snackbarEvents.emit(e.localizedMessage ?: "Error al guardar bitácora") }
    }
  }

  fun updateAccessibilityAndTheme(
    highContrastMode: Boolean,
    darkMode: Boolean,
  ) {
    val currentLocationEnabled = userProfile.value?.locationSharingEnabled ?: _locationSharingRequested.value
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

  fun setLocationSharingEnabled(enabled: Boolean, lat: Double? = null, lng: Double? = null, accuracy: Double = 14.0) {
    _locationSharingRequested.value = enabled
    if (lat != null && lng != null) {
      _lastKnownCoords.value = Pair(lat, lng)
    }
    val coords = _lastKnownCoords.value
    val channel = activeChannel.value
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

  fun reportLiveLocationPing(lat: Double, lng: Double, accuracyMeters: Double) {
    _lastKnownCoords.value = Pair(lat, lng)
    if (!_locationSharingRequested.value) return
    val channel = activeChannel.value ?: return
    viewModelScope.launch {
      repository.updatePresence(
        channel = channel,
        sharingActive = true,
        lat = lat,
        lng = lng,
        mapX = (0.5 + ((lng + 99.1332) * 20.0)).coerceIn(0.08, 0.92),
        mapY = (0.5 - ((lat - 19.4326) * 20.0)).coerceIn(0.08, 0.92),
        accuracyMeters = accuracyMeters,
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
  }
}
