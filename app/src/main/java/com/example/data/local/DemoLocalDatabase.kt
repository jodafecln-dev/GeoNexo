package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Channel
import com.example.data.model.ChannelMessage
import com.example.data.model.ChannelType
import com.example.data.model.DocRevision
import com.example.data.model.GeoMarker
import com.example.data.model.MarkerCategory
import com.example.data.model.MarkerStatus
import com.example.data.model.MessageType
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID

@Entity(tableName = "demo_profile")
data class DemoProfileEntity(
  @PrimaryKey val userId: String,
  val displayName: String,
  val roleTitle: String,
  val highContrastMode: Boolean,
  val darkMode: Boolean,
  val locationSharingEnabled: Boolean,
)

@Entity(tableName = "demo_channels")
data class DemoChannelEntity(
  @PrimaryKey val id: String,
  val ownerId: String,
  val name: String,
  val topic: String,
  val channelType: String,
  val pinnedDocTitle: String,
  val pinnedDocBody: String,
  val pinnedDocUpdatedBy: String,
  val pinnedDocRevision: Long,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
)

@Entity(tableName = "demo_messages")
data class DemoMessageEntity(
  @PrimaryKey val id: String,
  val channelId: String,
  val senderId: String,
  val senderName: String,
  val body: String,
  val parentMessageId: String?,
  val linkedMarkerId: String?,
  val linkedMarkerTitle: String?,
  val attachmentLabel: String?,
  val attachmentUri: String?,
  val attachmentSnippet: String? = null,
  val attachmentData: String?,
  val messageType: String,
  val createdAtMillis: Long,
)

@Entity(tableName = "demo_markers")
data class DemoMarkerEntity(
  @PrimaryKey val id: String,
  val channelId: String,
  val creatorId: String,
  val updatedByName: String,
  val title: String,
  val notes: String,
  val category: String,
  val status: String,
  val lat: Double,
  val lng: Double,
  val mapX: Double,
  val mapY: Double,
  val linkedDocId: String? = null,
  val linkedDocTitle: String? = null,
  val linkedDocUri: String? = null,
  val linkedDocSnippet: String? = null,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
)

@Entity(tableName = "demo_doc_revisions")
data class DemoDocRevisionEntity(
  @PrimaryKey val id: String,
  val channelId: String,
  val authorId: String,
  val authorName: String,
  val title: String,
  val body: String,
  val revisionNumber: Long,
  val createdAtMillis: Long,
)

@Dao
interface DemoLocalDao {
  @Query("SELECT * FROM demo_profile WHERE userId = :uid LIMIT 1")
  fun observeProfile(uid: String): Flow<DemoProfileEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProfile(profile: DemoProfileEntity)

  @Query("SELECT * FROM demo_channels ORDER BY updatedAtMillis DESC")
  fun observeChannels(): Flow<List<DemoChannelEntity>>

  @Query("SELECT COUNT(*) FROM demo_channels")
  suspend fun countChannels(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertChannel(channel: DemoChannelEntity)

  @Query("SELECT * FROM demo_messages WHERE channelId = :channelId ORDER BY createdAtMillis ASC")
  fun observeMessages(channelId: String): Flow<List<DemoMessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(msg: DemoMessageEntity)

  @Query("SELECT * FROM demo_markers WHERE channelId = :channelId ORDER BY updatedAtMillis DESC")
  fun observeMarkers(channelId: String): Flow<List<DemoMarkerEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertMarker(marker: DemoMarkerEntity)

  @Query("DELETE FROM demo_markers WHERE id = :markerId")
  suspend fun deleteMarker(markerId: String)

  @Query("SELECT * FROM demo_doc_revisions WHERE channelId = :channelId ORDER BY revisionNumber DESC")
  fun observeDocRevisions(channelId: String): Flow<List<DemoDocRevisionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDocRevision(rev: DemoDocRevisionEntity)
}

@Database(
  entities = [
    DemoProfileEntity::class,
    DemoChannelEntity::class,
    DemoMessageEntity::class,
    DemoMarkerEntity::class,
    DemoDocRevisionEntity::class,
  ],
  version = 2,
  exportSchema = false,
)
abstract class DemoLocalDatabase : RoomDatabase() {
  abstract fun dao(): DemoLocalDao

  companion object {
    @Volatile private var instance: DemoLocalDatabase? = null

    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE demo_messages ADD COLUMN attachmentSnippet TEXT")
          db.execSQL("ALTER TABLE demo_markers ADD COLUMN linkedDocId TEXT")
          db.execSQL("ALTER TABLE demo_markers ADD COLUMN linkedDocTitle TEXT")
          db.execSQL("ALTER TABLE demo_markers ADD COLUMN linkedDocUri TEXT")
          db.execSQL("ALTER TABLE demo_markers ADD COLUMN linkedDocSnippet TEXT")
        }
      }

    fun getInstance(context: Context): DemoLocalDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                DemoLocalDatabase::class.java,
                "geonexo_demo_local.db",
              )
              .addMigrations(MIGRATION_1_2)
              .build()
              .also { instance = it }
        }
    }
  }
}

class DemoLocalRepository(private val dao: DemoLocalDao, private val demoUid: String) {

  fun observeProfile(): Flow<UserProfile?> =
    dao.observeProfile(demoUid).map { entity ->
      entity?.let {
        UserProfile(
          id = it.userId,
          userId = it.userId,
          displayName = it.displayName,
          roleTitle = it.roleTitle,
          highContrastMode = it.highContrastMode,
          darkMode = it.darkMode,
          locationSharingEnabled = it.locationSharingEnabled,
        )
      }
    }

  fun observeChannels(): Flow<List<Channel>> =
    dao.observeChannels().map { list ->
      list.map { e ->
        Channel(
          id = e.id,
          ownerId = e.ownerId,
          memberUids = listOf(demoUid),
          memberNames = mapOf(demoUid to "Explorador Local"),
          name = e.name,
          topic = e.topic,
          channelType = e.channelType,
          pinnedDocTitle = e.pinnedDocTitle,
          pinnedDocBody = e.pinnedDocBody,
          pinnedDocUpdatedBy = e.pinnedDocUpdatedBy,
          pinnedDocRevision = e.pinnedDocRevision,
          createdAt = Timestamp(Date(e.createdAtMillis)),
          updatedAt = Timestamp(Date(e.updatedAtMillis)),
        )
      }
    }

  fun observeMessages(channelId: String): Flow<List<ChannelMessage>> =
    dao.observeMessages(channelId).map { list ->
      list.map { e ->
        ChannelMessage(
          id = e.id,
          channelId = e.channelId,
          senderId = e.senderId,
          senderName = e.senderName,
          memberUids = listOf(demoUid),
          body = e.body,
          parentMessageId = e.parentMessageId,
          linkedMarkerId = e.linkedMarkerId,
          linkedMarkerTitle = e.linkedMarkerTitle,
          attachmentLabel = e.attachmentLabel,
          attachmentUri = e.attachmentUri,
          attachmentSnippet = e.attachmentSnippet,
          attachmentData = e.attachmentData,
          messageType = e.messageType,
          createdAt = Timestamp(Date(e.createdAtMillis)),
          updatedAt = Timestamp(Date(e.createdAtMillis)),
        )
      }
    }

  fun observeMarkers(channelId: String): Flow<List<GeoMarker>> =
    dao.observeMarkers(channelId).map { list ->
      list.map { e ->
        GeoMarker(
          id = e.id,
          channelId = e.channelId,
          creatorId = e.creatorId,
          updatedByName = e.updatedByName,
          memberUids = listOf(demoUid),
          title = e.title,
          notes = e.notes,
          category = e.category,
          status = e.status,
          lat = e.lat,
          lng = e.lng,
          mapX = e.mapX,
          mapY = e.mapY,
          linkedDocId = e.linkedDocId,
          linkedDocTitle = e.linkedDocTitle,
          linkedDocUri = e.linkedDocUri,
          linkedDocSnippet = e.linkedDocSnippet,
          createdAt = Timestamp(Date(e.createdAtMillis)),
          updatedAt = Timestamp(Date(e.updatedAtMillis)),
        )
      }
    }

  fun observeDocRevisions(channelId: String): Flow<List<DocRevision>> =
    dao.observeDocRevisions(channelId).map { list ->
      list.map { e ->
        DocRevision(
          id = e.id,
          channelId = e.channelId,
          authorId = e.authorId,
          authorName = e.authorName,
          title = e.title,
          body = e.body,
          revisionNumber = e.revisionNumber,
          createdAt = Timestamp(Date(e.createdAtMillis)),
        )
      }
    }

  suspend fun ensureSeeded() {
    val now = System.currentTimeMillis()
    if (dao.countChannels() > 0) return

    dao.upsertProfile(
      DemoProfileEntity(
        userId = demoUid,
        displayName = "Explorador Local",
        roleTitle = "Modo Prueba Persistente",
        highContrastMode = false,
        darkMode = false,
        locationSharingEnabled = false,
      )
    )

    val ch1Id = "demo_sector_norte"
    val doc1Title = "Bitácora Operativa — Sector Norte"
    val doc1Body =
      "Objetivo del turno:\n• Verificar reposición del poste #14 en cruce Av. Industrial.\n• Validar presión de línea hidráulica antes de las 17:00.\n\nChecklist de campo:\n[x] Inspección visual de acometida\n[ ] Registro fotográfico en marcador #Poste-14\n[ ] Cierre de válvula secundaria"

    dao.upsertChannel(
      DemoChannelEntity(
        id = ch1Id,
        ownerId = demoUid,
        name = "sector-norte-obra",
        topic = "Inspección de subestación, postes y zanja hidráulica",
        channelType = ChannelType.FIELD.name,
        pinnedDocTitle = doc1Title,
        pinnedDocBody = doc1Body,
        pinnedDocUpdatedBy = "Explorador Local",
        pinnedDocRevision = 1L,
        createdAtMillis = now - 60_000,
        updatedAtMillis = now,
      )
    )
    dao.insertDocRevision(
      DemoDocRevisionEntity(
        id = "demo_rev_1",
        channelId = ch1Id,
        authorId = demoUid,
        authorName = "Explorador Local",
        title = doc1Title,
        body = doc1Body,
        revisionNumber = 1L,
        createdAtMillis = now - 60_000,
      )
    )

    dao.upsertMarker(
      DemoMarkerEntity(
        id = "demo_mk_1",
        channelId = ch1Id,
        creatorId = demoUid,
        updatedByName = "Cuadrilla Norte",
        title = "Poste #14 — Crucero Norte",
        notes = "Aislador fracturado detectado en ronda matutina. Requiere grúa ligera.",
        category = MarkerCategory.INCIDENT.name,
        status = MarkerStatus.OPEN.name,
        lat = 19.4352,
        lng = -99.1388,
        mapX = 0.34,
        mapY = 0.38,
        createdAtMillis = now - 50_000,
        updatedAtMillis = now - 50_000,
      )
    )
    dao.upsertMarker(
      DemoMarkerEntity(
        id = "demo_mk_2",
        channelId = ch1Id,
        creatorId = demoUid,
        updatedByName = "Oficina Técnica",
        title = "Tramo Zanja B2",
        notes = "Compactación al 85%. Pendiente prueba de hermeticidad.",
        category = MarkerCategory.PROGRESS.name,
        status = MarkerStatus.IN_PROGRESS.name,
        lat = 19.4318,
        lng = -99.1315,
        mapX = 0.62,
        mapY = 0.56,
        createdAtMillis = now - 40_000,
        updatedAtMillis = now - 40_000,
      )
    )
    dao.upsertMarker(
      DemoMarkerEntity(
        id = "demo_mk_3",
        channelId = ch1Id,
        creatorId = demoUid,
        updatedByName = "Almacén",
        title = "Punto Acceso Almacén",
        notes = "Ingreso autorizado para camiones hasta 18:00 h.",
        category = MarkerCategory.CHECKPOINT.name,
        status = MarkerStatus.RESOLVED.name,
        lat = 19.4295,
        lng = -99.1260,
        mapX = 0.78,
        mapY = 0.29,
        createdAtMillis = now - 30_000,
        updatedAtMillis = now - 30_000,
      )
    )

    dao.insertMessage(
      DemoMessageEntity(
        id = "demo_msg_1",
        channelId = ch1Id,
        senderId = "demo_colleague_1",
        senderName = "Ing. Valeria (Campo)",
        body =
          "Modo prueba persistente activo: tus canales, mensajes, marcadores y bitácora se guardan en SQLite local aunque cierres la app.",
        parentMessageId = null,
        linkedMarkerId = "demo_mk_1",
        linkedMarkerTitle = "Poste #14 — Crucero Norte",
        attachmentLabel = null,
        attachmentUri = null,
        attachmentData = null,
        messageType = MessageType.GEO_PIN.name,
        createdAtMillis = now - 20_000,
      )
    )
    dao.insertMessage(
      DemoMessageEntity(
        id = "demo_msg_2",
        channelId = ch1Id,
        senderId = "demo_colleague_2",
        senderName = "Carlos (Oficina)",
        body =
          "Inicia sesión con Google cuando quieras activar Gemini IA, compartir fotos en la nube o invitar compañeros en otra ubicación.",
        parentMessageId = null,
        linkedMarkerId = null,
        linkedMarkerTitle = null,
        attachmentLabel = null,
        attachmentUri = null,
        attachmentData = null,
        messageType = MessageType.TEXT.name,
        createdAtMillis = now - 10_000,
      )
    )

    val ch2Id = "demo_logistica_sur"
    dao.upsertChannel(
      DemoChannelEntity(
        id = ch2Id,
        ownerId = demoUid,
        name = "logistica-ruta-sur",
        topic = "Entregas de material y puntos de control en ruta metropolitana",
        channelType = ChannelType.LOGISTICS.name,
        pinnedDocTitle = "Protocolo de Entregas Sur",
        pinnedDocBody =
          "Ventana de descarga:\n• Unidad 04: 09:00 - 11:30\n• Confirmar recepción con foto adjunta en el chat del canal.",
        pinnedDocUpdatedBy = "Explorador Local",
        pinnedDocRevision = 1L,
        createdAtMillis = now - 70_000,
        updatedAtMillis = now - 5_000,
      )
    )
    dao.upsertMarker(
      DemoMarkerEntity(
        id = "demo_mk_4",
        channelId = ch2Id,
        creatorId = demoUid,
        updatedByName = "Unidad 04",
        title = "Bodega Central Sur",
        notes = "Lote de transformadores listo para despacho.",
        category = MarkerCategory.DELIVERY.name,
        status = MarkerStatus.IN_PROGRESS.name,
        lat = 19.3810,
        lng = -99.1520,
        mapX = 0.48,
        mapY = 0.72,
        createdAtMillis = now - 15_000,
        updatedAtMillis = now - 15_000,
      )
    )
  }

  suspend fun createChannel(name: String, topic: String, type: ChannelType): String {
    val cleanName = name.trim().lowercase().replace(Regex("\\s+"), "-").take(60)
    val id = "demo_ch_${UUID.randomUUID().toString().take(8)}"
    val now = System.currentTimeMillis()
    val docTitle = "Bitácora — $cleanName"
    val docBody = "Notas operativas iniciales del canal #$cleanName."
    dao.upsertChannel(
      DemoChannelEntity(
        id = id,
        ownerId = demoUid,
        name = cleanName,
        topic = topic.trim().take(160),
        channelType = type.name,
        pinnedDocTitle = docTitle,
        pinnedDocBody = docBody,
        pinnedDocUpdatedBy = "Explorador Local",
        pinnedDocRevision = 1L,
        createdAtMillis = now,
        updatedAtMillis = now,
      )
    )
    dao.insertDocRevision(
      DemoDocRevisionEntity(
        id = "rev_${UUID.randomUUID().toString().take(8)}",
        channelId = id,
        authorId = demoUid,
        authorName = "Explorador Local",
        title = docTitle,
        body = docBody,
        revisionNumber = 1L,
        createdAtMillis = now,
      )
    )
    return id
  }

  suspend fun insertMessage(
    channelId: String,
    body: String,
    parentMessageId: String?,
    linkedMarker: GeoMarker?,
    attachmentLabel: String?,
    attachmentUri: String?,
    attachmentSnippet: String? = null,
    attachmentData: String?,
  ) {
    val isDocLink =
      !attachmentUri.isNullOrBlank() &&
        (attachmentUri.startsWith("https://docs.google.com/") ||
          attachmentUri.startsWith("https://drive.google.com/"))
    val type =
      when {
        isDocLink -> MessageType.DOC_LINK
        linkedMarker != null -> MessageType.GEO_PIN
        !attachmentLabel.isNullOrBlank() || !attachmentData.isNullOrBlank() -> MessageType.PHOTO
        else -> MessageType.TEXT
      }
    dao.insertMessage(
      DemoMessageEntity(
        id = "demo_msg_${UUID.randomUUID().toString().take(8)}",
        channelId = channelId,
        senderId = demoUid,
        senderName = "Explorador Local",
        body = body.trim().take(2000),
        parentMessageId = parentMessageId?.takeIf { it.isNotBlank() },
        linkedMarkerId = linkedMarker?.id?.takeIf { it.isNotBlank() },
        linkedMarkerTitle = linkedMarker?.title?.take(80)?.takeIf { it.isNotBlank() },
        attachmentLabel = attachmentLabel?.take(120)?.takeIf { it.isNotBlank() },
        attachmentUri = attachmentUri?.take(500)?.takeIf { it.isNotBlank() },
        attachmentSnippet = attachmentSnippet?.take(500)?.takeIf { it.isNotBlank() },
        attachmentData = attachmentData,
        messageType = type.name,
        createdAtMillis = System.currentTimeMillis(),
      )
    )
  }

  suspend fun createMarker(
    channelId: String,
    title: String,
    notes: String,
    category: MarkerCategory,
    status: MarkerStatus,
    lat: Double,
    lng: Double,
    mapX: Double,
    mapY: Double,
    linkedDocId: String? = null,
    linkedDocTitle: String? = null,
    linkedDocUri: String? = null,
    linkedDocSnippet: String? = null,
  ): String {
    val id = "demo_mk_${UUID.randomUUID().toString().take(8)}"
    val now = System.currentTimeMillis()
    dao.upsertMarker(
      DemoMarkerEntity(
        id = id,
        channelId = channelId,
        creatorId = demoUid,
        updatedByName = "Explorador Local",
        title = title.trim().take(80),
        notes = notes.trim().take(1000),
        category = category.name,
        status = status.name,
        lat = lat,
        lng = lng,
        mapX = mapX,
        mapY = mapY,
        linkedDocId = linkedDocId,
        linkedDocTitle = linkedDocTitle?.take(120),
        linkedDocUri = linkedDocUri?.take(500),
        linkedDocSnippet = linkedDocSnippet?.take(500),
        createdAtMillis = now,
        updatedAtMillis = now,
      )
    )
    return id
  }

  suspend fun updateMarker(
    channelId: String,
    marker: GeoMarker,
    title: String,
    notes: String,
    category: MarkerCategory,
    status: MarkerStatus,
  ) {
    val now = System.currentTimeMillis()
    dao.upsertMarker(
      DemoMarkerEntity(
        id = marker.id,
        channelId = channelId,
        creatorId = marker.creatorId,
        updatedByName = "Explorador Local",
        title = title.trim().take(80),
        notes = notes.trim().take(1000),
        category = category.name,
        status = status.name,
        lat = marker.lat,
        lng = marker.lng,
        mapX = marker.mapX,
        mapY = marker.mapY,
        linkedDocId = marker.linkedDocId,
        linkedDocTitle = marker.linkedDocTitle,
        linkedDocUri = marker.linkedDocUri,
        linkedDocSnippet = marker.linkedDocSnippet,
        createdAtMillis = marker.createdAt?.toDate()?.time ?: now,
        updatedAtMillis = now,
      )
    )
  }

  suspend fun linkDocToMarker(
    channelId: String,
    marker: GeoMarker,
    docId: String?,
    docTitle: String?,
    docUri: String?,
    docSnippet: String?,
  ) {
    val now = System.currentTimeMillis()
    dao.upsertMarker(
      DemoMarkerEntity(
        id = marker.id,
        channelId = channelId,
        creatorId = marker.creatorId,
        updatedByName = "Explorador Local",
        title = marker.title,
        notes = marker.notes,
        category = marker.category,
        status = marker.status,
        lat = marker.lat,
        lng = marker.lng,
        mapX = marker.mapX,
        mapY = marker.mapY,
        linkedDocId = docId?.take(128),
        linkedDocTitle = docTitle?.take(120),
        linkedDocUri = docUri?.take(500),
        linkedDocSnippet = docSnippet?.take(500),
        createdAtMillis = marker.createdAt?.toDate()?.time ?: now,
        updatedAtMillis = now,
      )
    )
  }

  suspend fun deleteMarker(markerId: String) {
    dao.deleteMarker(markerId)
  }

  suspend fun updatePinnedDoc(channel: Channel, title: String, body: String) {
    val now = System.currentTimeMillis()
    val nextRev = channel.pinnedDocRevision + 1L
    val cleanTitle = title.trim().take(100)
    val cleanBody = body.take(12000)
    dao.upsertChannel(
      DemoChannelEntity(
        id = channel.id,
        ownerId = channel.ownerId,
        name = channel.name,
        topic = channel.topic,
        channelType = channel.channelType,
        pinnedDocTitle = cleanTitle,
        pinnedDocBody = cleanBody,
        pinnedDocUpdatedBy = "Explorador Local",
        pinnedDocRevision = nextRev,
        createdAtMillis = channel.createdAt?.toDate()?.time ?: now,
        updatedAtMillis = now,
      )
    )
    dao.insertDocRevision(
      DemoDocRevisionEntity(
        id = "rev_${UUID.randomUUID().toString().take(8)}",
        channelId = channel.id,
        authorId = demoUid,
        authorName = "Explorador Local",
        title = cleanTitle,
        body = cleanBody,
        revisionNumber = nextRev,
        createdAtMillis = now,
      )
    )
  }

  suspend fun updatePreferences(highContrast: Boolean, darkMode: Boolean, locationSharing: Boolean) {
    dao.upsertProfile(
      DemoProfileEntity(
        userId = demoUid,
        displayName = "Explorador Local",
        roleTitle = "Modo Prueba Persistente",
        highContrastMode = highContrast,
        darkMode = darkMode,
        locationSharingEnabled = locationSharing,
      )
    )
  }
}
