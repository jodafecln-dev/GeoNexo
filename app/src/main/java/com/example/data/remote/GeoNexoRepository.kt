package com.example.data.remote

import android.content.Context
import com.example.R
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
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class GeoNexoRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("Debes iniciar sesión con Google para continuar.")
  }

  private fun requireDisplayName(): String {
    val user = auth.currentUser
    val raw = user?.displayName?.trim().takeIf { !it.isNullOrBlank() }
      ?: user?.email?.substringBefore('@')?.trim().takeIf { !it.isNullOrBlank() }
      ?: "Coordinador"
    return raw.take(60)
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    val path = "users/$userId"
    emitAll(
      db.collection("users")
        .document(userId)
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              UserProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          } else {
            null
          }
        }
        .catch { error ->
          logFlowError(error, OperationType.GET, path, userId)
          throw error
        }
    )
  }

  fun observeChannels(userId: String): Flow<SyncedCollection<Channel>> = flow {
    val path = "channels"
    emitAll(
      db.collection("channels")
        .whereArrayContains("memberUids", userId)
        .snapshots()
        .map { snapshot ->
          val items =
            snapshot
              .toObjects(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
              .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
          SyncedCollection(
            items = items,
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            fromCache = snapshot.metadata.isFromCache,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  fun observeMessages(channelId: String, userId: String): Flow<SyncedCollection<ChannelMessage>> = flow {
    val path = "channels/$channelId/messages"
    emitAll(
      db.collection("channels")
        .document(channelId)
        .collection("messages")
        .snapshots()
        .map { snapshot ->
          val items =
            snapshot
              .toObjects(
                ChannelMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
              )
              .sortedBy { it.createdAt ?: Timestamp(0, 0) }
          SyncedCollection(
            items = items,
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            fromCache = snapshot.metadata.isFromCache,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  fun observeMarkers(channelId: String, userId: String): Flow<SyncedCollection<GeoMarker>> = flow {
    val path = "channels/$channelId/markers"
    emitAll(
      db.collection("channels")
        .document(channelId)
        .collection("markers")
        .snapshots()
        .map { snapshot ->
          val items =
            snapshot
              .toObjects(GeoMarker::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
              .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
          SyncedCollection(
            items = items,
            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
            fromCache = snapshot.metadata.isFromCache,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  fun observePresence(channelId: String, userId: String): Flow<List<ChannelPresence>> = flow {
    val path = "channels/$channelId/presence"
    emitAll(
      db.collection("channels")
        .document(channelId)
        .collection("presence")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              ChannelPresence::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .filter { it.sharingActive }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path, userId)
          throw error
        }
    )
  }

  suspend fun ensureUserProfileAndStarterWorkspace(): Result<Unit> {
    var path = "users"
    return try {
      val uid = requireUserId()
      val name = requireDisplayName()
      val userRef = db.collection("users").document(uid)
      path = userRef.path
      val userSnap = userRef.get().await()
      if (!userSnap.exists()) {
        val profilePayload =
          mapOf(
            "userId" to uid,
            "displayName" to name,
            "roleTitle" to "Coordinación de Campo",
            "highContrastMode" to false,
            "darkMode" to false,
            "locationSharingEnabled" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        userRef.set(profilePayload).await()
      }

      path = "channels"
      val existingChannels =
        db.collection("channels").whereArrayContains("memberUids", uid).limit(1).get().await()

      if (existingChannels.isEmpty) {
        seedInitialChannels(uid, name)
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  private suspend fun seedInitialChannels(uid: String, displayName: String) {
    val members = listOf(uid)

    val ch1Ref = db.collection("channels").document()
    val ch1Id = ch1Ref.id
    ch1Ref
      .set(
        mapOf(
          "ownerId" to uid,
          "memberUids" to members,
          "name" to "sector-norte-obra",
          "topic" to "Inspección de subestación, postes y zanja hidráulica",
          "channelType" to ChannelType.FIELD.name,
          "pinnedDocTitle" to "Bitácora Operativa — Sector Norte",
          "pinnedDocBody" to
            "Objetivo del turno:\n• Verificar reposición del poste #14 en cruce Av. Industrial.\n• Validar presión de línea hidráulica antes de las 17:00.\n\nChecklist de campo:\n[x] Inspección visual de acometida\n[ ] Registro fotográfico en marcador #Poste-14\n[ ] Cierre de válvula secundaria",
          "pinnedDocUpdatedBy" to displayName,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()

    val mk1Ref = ch1Ref.collection("markers").document()
    mk1Ref
      .set(
        mapOf(
          "channelId" to ch1Id,
          "creatorId" to uid,
          "updatedByName" to displayName,
          "memberUids" to members,
          "title" to "Poste #14 — Crucero Norte",
          "notes" to "Aislador fracturado detectado en ronda matutina. Requiere grúa ligera.",
          "category" to MarkerCategory.INCIDENT.name,
          "status" to MarkerStatus.OPEN.name,
          "lat" to 19.4352,
          "lng" to -99.1388,
          "mapX" to 0.34,
          "mapY" to 0.38,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()

    val mk2Ref = ch1Ref.collection("markers").document()
    mk2Ref
      .set(
        mapOf(
          "channelId" to ch1Id,
          "creatorId" to uid,
          "updatedByName" to displayName,
          "memberUids" to members,
          "title" to "Tramo Zanja B2",
          "notes" to "Compactación al 85%. Pendiente prueba de hermeticidad.",
          "category" to MarkerCategory.PROGRESS.name,
          "status" to MarkerStatus.IN_PROGRESS.name,
          "lat" to 19.4318,
          "lng" to -99.1315,
          "mapX" to 0.62,
          "mapY" to 0.56,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()

    val mk3Ref = ch1Ref.collection("markers").document()
    mk3Ref
      .set(
        mapOf(
          "channelId" to ch1Id,
          "creatorId" to uid,
          "updatedByName" to displayName,
          "memberUids" to members,
          "title" to "Punto Acceso Almacén",
          "notes" to "Ingreso autorizado para camiones hasta 18:00 h.",
          "category" to MarkerCategory.CHECKPOINT.name,
          "status" to MarkerStatus.RESOLVED.name,
          "lat" to 19.4295,
          "lng" to -99.1260,
          "mapX" to 0.78,
          "mapY" to 0.29,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()

    ch1Ref
      .collection("messages")
      .document()
      .set(
        mapOf(
          "channelId" to ch1Id,
          "senderId" to uid,
          "senderName" to displayName,
          "memberUids" to members,
          "body" to
            "Canal operativo activo. Revisar el punto marcado en el crucero norte antes de mover la cuadrilla.",
          "linkedMarkerId" to mk1Ref.id,
          "linkedMarkerTitle" to "Poste #14 — Crucero Norte",
          "messageType" to MessageType.GEO_PIN.name,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()

    val ch2Ref = db.collection("channels").document()
    val ch2Id = ch2Ref.id
    ch2Ref
      .set(
        mapOf(
          "ownerId" to uid,
          "memberUids" to members,
          "name" to "logistica-ruta-sur",
          "topic" to "Entregas de material y puntos de control en ruta metropolitana",
          "channelType" to ChannelType.LOGISTICS.name,
          "pinnedDocTitle" to "Protocolo de Entregas Sur",
          "pinnedDocBody" to
            "Ventana de descarga:\n• Unidad 04: 09:00 - 11:30\n• Confirmar recepción con foto adjunta en el chat del canal.",
          "pinnedDocUpdatedBy" to displayName,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()

    ch2Ref
      .collection("markers")
      .document()
      .set(
        mapOf(
          "channelId" to ch2Id,
          "creatorId" to uid,
          "updatedByName" to displayName,
          "memberUids" to members,
          "title" to "Bodega Central Sur",
          "notes" to "Lote de transformadores listo para despacho.",
          "category" to MarkerCategory.DELIVERY.name,
          "status" to MarkerStatus.IN_PROGRESS.name,
          "lat" to 19.3810,
          "lng" to -99.1520,
          "mapX" to 0.48,
          "mapY" to 0.72,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun createChannel(
    name: String,
    topic: String,
    type: ChannelType,
  ): Result<String> {
    var path = "channels"
    return try {
      val uid = requireUserId()
      val displayName = requireDisplayName()
      val cleanName = name.trim().lowercase().replace(Regex("\\s+"), "-").take(60)
      require(cleanName.length >= 2) { "El nombre del canal debe tener al menos 2 caracteres." }
      val cleanTopic = topic.trim().take(160)

      val docRef = db.collection("channels").document()
      path = docRef.path
      docRef
        .set(
          mapOf(
            "ownerId" to uid,
            "memberUids" to listOf(uid),
            "name" to cleanName,
            "topic" to cleanTopic,
            "channelType" to type.name,
            "pinnedDocTitle" to "Bitácora — $cleanName",
            "pinnedDocBody" to "Notas operativas iniciales del canal #$cleanName.",
            "pinnedDocUpdatedBy" to displayName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun createChannelInvite(channel: Channel): Result<String> {
    var path = "channel_invites"
    return try {
      val uid = requireUserId()
      require(channel.ownerId == uid) {
        "Solo el propietario del canal puede generar códigos de invitación."
      }
      val existingCode = channel.joinCode?.trim()?.uppercase()?.takeIf { it.length in 6..16 }
      if (existingCode != null) {
        val existingSnap = db.collection("channel_invites").document(existingCode).get().await()
        if (existingSnap.exists()) {
          return Result.success(existingCode)
        }
      }

      val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
      val code =
        buildString {
          append("GNX")
          repeat(5) { append(alphabet.random()) }
        }

      val inviteRef = db.collection("channel_invites").document(code)
      path = inviteRef.path
      inviteRef
        .set(
          mapOf(
            "inviteCode" to code,
            "channelId" to channel.id,
            "channelName" to channel.name.take(60),
            "ownerId" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      db.collection("channels")
        .document(channel.id)
        .update(
          mapOf(
            "joinCode" to code,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      Result.success(code)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun joinChannelByInviteCode(rawCode: String): Result<Pair<String, String>> {
    var path = "channel_invites"
    return try {
      val uid = requireUserId()
      val cleanCode = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
      require(cleanCode.length in 6..16) {
        "Ingresa un código válido de al menos 6 caracteres (ej. GNX42A8K)."
      }

      val inviteRef = db.collection("channel_invites").document(cleanCode)
      path = inviteRef.path
      val inviteSnap = inviteRef.get().await()
      if (!inviteSnap.exists()) {
        return Result.failure(
          IllegalArgumentException("No se encontró ningún canal para el código $cleanCode.")
        )
      }

      val channelId =
        inviteSnap.getString("channelId")?.takeIf { it.isNotBlank() }
          ?: return Result.failure(IllegalStateException("Invitación inválida."))
      val channelName = inviteSnap.getString("channelName") ?: "canal"

      // If the user is already a member, avoid a redundant write that could fail non-owner rules
      val alreadyMember =
        try {
          db.collection("channels").document(channelId).get().await().exists()
        } catch (_: FirebaseFirestoreException) {
          false
        }
      if (alreadyMember) {
        return Result.success(Pair(channelId, channelName))
      }

      path = "channels/$channelId"
      db.collection("channels")
        .document(channelId)
        .update(
          mapOf(
            "memberUids" to FieldValue.arrayUnion(uid),
            "joinCode" to cleanCode,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      Result.success(Pair(channelId, channelName))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun sendMessage(
    channel: Channel,
    body: String,
    parentMessageId: String? = null,
    linkedMarker: GeoMarker? = null,
    attachmentLabel: String? = null,
    attachmentUri: String? = null,
  ): Result<Unit> {
    var path = "channels/${channel.id}/messages"
    return try {
      val uid = requireUserId()
      val senderName = requireDisplayName()
      val cleanBody = body.trim().take(2000)
      require(cleanBody.isNotEmpty()) { "El mensaje no puede estar vacío." }

      val messageType =
        when {
          linkedMarker != null -> MessageType.GEO_PIN
          !attachmentLabel.isNullOrBlank() -> MessageType.PHOTO
          else -> MessageType.TEXT
        }

      val docRef = db.collection("channels").document(channel.id).collection("messages").document()
      path = docRef.path

      val payload =
        mapOf(
          "channelId" to channel.id,
          "senderId" to uid,
          "senderName" to senderName,
          "memberUids" to channel.memberUids,
          "body" to cleanBody,
          "parentMessageId" to parentMessageId?.takeIf { it.isNotBlank() },
          "linkedMarkerId" to linkedMarker?.id?.takeIf { it.isNotBlank() },
          "linkedMarkerTitle" to linkedMarker?.title?.take(80)?.takeIf { it.isNotBlank() },
          "attachmentLabel" to attachmentLabel?.take(120)?.takeIf { it.isNotBlank() },
          "attachmentUri" to attachmentUri?.take(500)?.takeIf { it.isNotBlank() },
          "messageType" to messageType.name,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
          .filterValues { it != null }

      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun createMarker(
    channel: Channel,
    title: String,
    notes: String,
    category: MarkerCategory,
    status: MarkerStatus,
    lat: Double,
    lng: Double,
    mapX: Double,
    mapY: Double,
  ): Result<String> {
    var path = "channels/${channel.id}/markers"
    return try {
      val uid = requireUserId()
      val displayName = requireDisplayName()
      val cleanTitle = title.trim().take(80)
      require(cleanTitle.length >= 2) { "El título del marcador debe tener al menos 2 caracteres." }
      val cleanNotes = notes.trim().take(1000)

      val docRef = db.collection("channels").document(channel.id).collection("markers").document()
      path = docRef.path
      docRef
        .set(
          mapOf(
            "channelId" to channel.id,
            "creatorId" to uid,
            "updatedByName" to displayName,
            "memberUids" to channel.memberUids,
            "title" to cleanTitle,
            "notes" to cleanNotes,
            "category" to category.name,
            "status" to status.name,
            "lat" to lat.coerceIn(-90.0, 90.0),
            "lng" to lng.coerceIn(-180.0, 180.0),
            "mapX" to mapX.coerceIn(0.0, 1.0),
            "mapY" to mapY.coerceIn(0.0, 1.0),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateMarker(
    channelId: String,
    markerId: String,
    title: String,
    notes: String,
    category: MarkerCategory,
    status: MarkerStatus,
  ): Result<Unit> {
    val path = "channels/$channelId/markers/$markerId"
    return try {
      requireUserId()
      val displayName = requireDisplayName()
      val cleanTitle = title.trim().take(80)
      require(cleanTitle.length >= 2) { "El título debe tener al menos 2 caracteres." }
      db.collection("channels")
        .document(channelId)
        .collection("markers")
        .document(markerId)
        .update(
          mapOf(
            "title" to cleanTitle,
            "notes" to notes.trim().take(1000),
            "category" to category.name,
            "status" to status.name,
            "updatedByName" to displayName,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteMarker(channelId: String, markerId: String): Result<Unit> {
    val path = "channels/$channelId/markers/$markerId"
    return try {
      requireUserId()
      db.collection("channels")
        .document(channelId)
        .collection("markers")
        .document(markerId)
        .delete()
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun updatePinnedDocument(
    channelId: String,
    title: String,
    body: String,
  ): Result<Unit> {
    val path = "channels/$channelId"
    return try {
      requireUserId()
      val displayName = requireDisplayName()
      val cleanTitle = title.trim().take(100)
      require(cleanTitle.isNotEmpty()) { "El título del documento es obligatorio." }
      db.collection("channels")
        .document(channelId)
        .update(
          mapOf(
            "pinnedDocTitle" to cleanTitle,
            "pinnedDocBody" to body.take(12000),
            "pinnedDocUpdatedBy" to displayName,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateUserPreferences(
    highContrastMode: Boolean,
    darkMode: Boolean,
    locationSharingEnabled: Boolean,
  ): Result<Unit> {
    var path = "users"
    return try {
      val uid = requireUserId()
      val ref = db.collection("users").document(uid)
      path = ref.path
      ref
        .update(
          mapOf(
            "highContrastMode" to highContrastMode,
            "darkMode" to darkMode,
            "locationSharingEnabled" to locationSharingEnabled,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updatePresence(
    channel: Channel,
    sharingActive: Boolean,
    lat: Double,
    lng: Double,
    mapX: Double,
    mapY: Double,
    accuracyMeters: Double,
  ): Result<Unit> {
    var path = "channels/${channel.id}/presence"
    return try {
      val uid = requireUserId()
      val displayName = requireDisplayName()
      val ref = db.collection("channels").document(channel.id).collection("presence").document(uid)
      path = ref.path
      val existingSnap = ref.get().await()
      if (!existingSnap.exists()) {
        ref
          .set(
            mapOf(
              "channelId" to channel.id,
              "userId" to uid,
              "displayName" to displayName,
              "memberUids" to channel.memberUids,
              "sharingActive" to sharingActive,
              "lat" to lat.coerceIn(-90.0, 90.0),
              "lng" to lng.coerceIn(-180.0, 180.0),
              "mapX" to mapX.coerceIn(0.0, 1.0),
              "mapY" to mapY.coerceIn(0.0, 1.0),
              "accuracyMeters" to accuracyMeters.coerceIn(0.0, 5000.0),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        ref
          .update(
            mapOf(
              "displayName" to displayName,
              "sharingActive" to sharingActive,
              "lat" to lat.coerceIn(-90.0, 90.0),
              "lng" to lng.coerceIn(-180.0, 180.0),
              "mapX" to mapX.coerceIn(0.0, 1.0),
              "mapY" to mapY.coerceIn(0.0, 1.0),
              "accuracyMeters" to accuracyMeters.coerceIn(0.0, 5000.0),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  private fun logFlowError(
    error: Throwable,
    operationType: OperationType,
    path: String,
    userId: String,
  ) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val toLog = firestoreError ?: (error as? Exception)
    if (toLog != null && auth.currentUser?.uid == userId) {
      handleFirestoreError(toLog, operationType, path)
    }
  }
}
