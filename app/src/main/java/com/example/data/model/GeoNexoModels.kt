package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ChannelType(val labelEs: String) {
  FIELD("Campo"),
  PROJECT("Proyecto"),
  LOGISTICS("Logística"),
  GENERAL("General");

  companion object {
    fun safeOf(raw: String?): ChannelType =
      entries.firstOrNull { it.name == raw } ?: FIELD
  }
}

enum class MessageType {
  TEXT,
  GEO_PIN,
  PHOTO,
  SYSTEM;

  companion object {
    fun safeOf(raw: String?): MessageType =
      entries.firstOrNull { it.name == raw } ?: TEXT
  }
}

enum class MarkerCategory(val labelEs: String) {
  INCIDENT("Incidencia"),
  PROGRESS("Avance"),
  CHECKPOINT("Punto control"),
  DELIVERY("Entrega"),
  ROUTE("Ruta");

  companion object {
    fun safeOf(raw: String?): MarkerCategory =
      entries.firstOrNull { it.name == raw } ?: CHECKPOINT
  }
}

enum class MarkerStatus(val labelEs: String) {
  OPEN("Abierto"),
  IN_PROGRESS("En curso"),
  RESOLVED("Resuelto");

  companion object {
    fun safeOf(raw: String?): MarkerStatus =
      entries.firstOrNull { it.name == raw } ?: OPEN
  }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val roleTitle: String = "Coordinación de Campo",
  val highContrastMode: Boolean = false,
  val darkMode: Boolean = false,
  val locationSharingEnabled: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Channel(
  @DocumentId val id: String = "",
  val ownerId: String = "",
  val memberUids: List<String> = emptyList(),
  val name: String = "",
  val topic: String = "",
  val channelType: String = ChannelType.FIELD.name,
  val pinnedDocTitle: String = "",
  val pinnedDocBody: String = "",
  val pinnedDocUpdatedBy: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedType(): ChannelType = ChannelType.safeOf(channelType)
}

@Keep
@IgnoreExtraProperties
data class ChannelMessage(
  @DocumentId val id: String = "",
  val channelId: String = "",
  val senderId: String = "",
  val senderName: String = "",
  val memberUids: List<String> = emptyList(),
  val body: String = "",
  val parentMessageId: String? = null,
  val linkedMarkerId: String? = null,
  val linkedMarkerTitle: String? = null,
  val attachmentLabel: String? = null,
  val attachmentUri: String? = null,
  val messageType: String = MessageType.TEXT.name,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedType(): MessageType = MessageType.safeOf(messageType)
}

@Keep
@IgnoreExtraProperties
data class GeoMarker(
  @DocumentId val id: String = "",
  val channelId: String = "",
  val creatorId: String = "",
  val updatedByName: String = "",
  val memberUids: List<String> = emptyList(),
  val title: String = "",
  val notes: String = "",
  val category: String = MarkerCategory.CHECKPOINT.name,
  val status: String = MarkerStatus.OPEN.name,
  val lat: Double = 19.4326,
  val lng: Double = -99.1332,
  val mapX: Double = 0.5,
  val mapY: Double = 0.5,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedCategory(): MarkerCategory = MarkerCategory.safeOf(category)
  fun resolvedStatus(): MarkerStatus = MarkerStatus.safeOf(status)
}

@Keep
@IgnoreExtraProperties
data class ChannelPresence(
  @DocumentId val id: String = "",
  val channelId: String = "",
  val userId: String = "",
  val displayName: String = "",
  val memberUids: List<String> = emptyList(),
  val sharingActive: Boolean = false,
  val lat: Double = 19.4326,
  val lng: Double = -99.1332,
  val mapX: Double = 0.5,
  val mapY: Double = 0.5,
  val accuracyMeters: Double = 15.0,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class SyncedCollection<T>(
  val items: List<T>,
  val hasPendingWrites: Boolean,
  val fromCache: Boolean,
)
