package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DemoLocalDatabase
import com.example.data.local.DemoLocalRepository
import com.example.data.model.ChannelType
import com.example.data.model.MarkerCategory
import com.example.data.model.MarkerStatus
import com.example.data.model.MessageType
import com.example.util.FieldMediaAndAlerts
import com.example.workspace.GoogleWorkspaceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var db: DemoLocalDatabase
  private lateinit var repo: DemoLocalRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, DemoLocalDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repo = DemoLocalRepository(db.dao(), "demo_test_uid")
  }

  @After
  fun teardown() {
    db.close()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GeoNexo", appName)
  }

  @Test
  fun `demo repository persists seeded channels, markers, messages and doc revisions`() = runBlocking {
    repo.ensureSeeded()
    val channels = repo.observeChannels().first()
    assertTrue(channels.size >= 2)

    val sectorNorte = channels.first { it.name == "sector-norte-obra" }
    val markers = repo.observeMarkers(sectorNorte.id).first()
    assertEquals(3, markers.size)

    val newChannelId = repo.createChannel("obras-centro", "Inspección eléctrica", ChannelType.PROJECT)
    repo.insertMessage(
      channelId = newChannelId,
      body = "Foto de prueba adjunta",
      parentMessageId = null,
      linkedMarker = null,
      attachmentLabel = "Evidencia",
      attachmentUri = null,
      attachmentData = "dGVzdA==",
    )
    repo.createMarker(
      channelId = newChannelId,
      title = "Subestación 02",
      notes = "Voltaje nominal verificado",
      category = MarkerCategory.CHECKPOINT,
      status = MarkerStatus.RESOLVED,
      lat = 19.4326,
      lng = -99.1332,
      mapX = 0.5,
      mapY = 0.5,
    )
    val updatedChannels = repo.observeChannels().first()
    val createdChannel = updatedChannels.first { it.id == newChannelId }
    repo.updatePinnedDoc(createdChannel, createdChannel.pinnedDocTitle, "Nueva revisión v2")

    val revisions = repo.observeDocRevisions(newChannelId).first()
    assertEquals(2, revisions.size)
    assertEquals(2L, revisions.first().revisionNumber)
    assertEquals("Nueva revisión v2", revisions.first().body)

    val messages = repo.observeMessages(newChannelId).first()
    assertEquals(1, messages.size)
    assertEquals("dGVzdA==", messages.first().attachmentData)
  }

  @Test
  fun `base64 photo decode helper handles valid payload`() {
    val bytes = FieldMediaAndAlerts.decodeBase64ToBytes("SG9sYSBHZW9OZXhv")
    assertArrayEquals("Hola GeoNexo".toByteArray(), bytes)
  }

  @Test
  fun `google workspace url validator accepts official urls and blocks phishing hosts`() {
    val docUrl = "https://docs.google.com/document/d/1aBcDeFgHiJkLmNoPqRsTuVwXyZ_0123/edit"
    val driveFileUrl = "https://drive.google.com/file/d/9876543210_AbCdEf/view?usp=sharing"
    val driveOpenUrl = "https://drive.google.com/open?id=AbCdEf123456_789"

    assertTrue(GoogleWorkspaceManager.isValidGoogleWorkspaceUrl(docUrl))
    assertTrue(GoogleWorkspaceManager.isValidGoogleWorkspaceUrl(driveFileUrl))
    assertTrue(GoogleWorkspaceManager.isValidGoogleWorkspaceUrl(driveOpenUrl))

    assertEquals("1aBcDeFgHiJkLmNoPqRsTuVwXyZ_0123", GoogleWorkspaceManager.extractGoogleDocId(docUrl))
    assertEquals("9876543210_AbCdEf", GoogleWorkspaceManager.extractGoogleDocId(driveFileUrl))
    assertEquals("AbCdEf123456_789", GoogleWorkspaceManager.extractGoogleDocId(driveOpenUrl))

    assertFalse(GoogleWorkspaceManager.isValidGoogleWorkspaceUrl("http://docs.google.com/document/d/123456/edit"))
    assertFalse(GoogleWorkspaceManager.isValidGoogleWorkspaceUrl("https://evil.com/docs.google.com/document/d/123456"))
    assertFalse(GoogleWorkspaceManager.isValidGoogleWorkspaceUrl("https://docs.google.com"))
    assertNull(GoogleWorkspaceManager.extractGoogleDocId("https://evil.com/document/d/12345678"))
  }

  @Test
  fun `demo repository persists google doc messages and links or unlinks marker docs`() = runBlocking {
    val channelId = repo.createChannel("campo-docs", "Pruebas Google Docs", ChannelType.FIELD)
    val docUrl = "https://docs.google.com/document/d/AbCdEfGhIjKlMnOpQr1234/edit"

    repo.insertMessage(
      channelId = channelId,
      body = "Adjunto el protocolo de seguridad actualizado",
      parentMessageId = null,
      linkedMarker = null,
      attachmentLabel = "Protocolo Sector Norte",
      attachmentUri = docUrl,
      attachmentSnippet = "Checklist de aislamiento eléctrico y EPP obligatorio.",
      attachmentData = null,
    )

    val messages = repo.observeMessages(channelId).first()
    assertEquals(1, messages.size)
    assertEquals(MessageType.DOC_LINK.name, messages.first().messageType)
    assertEquals("Protocolo Sector Norte", messages.first().attachmentLabel)
    assertEquals(docUrl, messages.first().attachmentUri)
    assertEquals("Checklist de aislamiento eléctrico y EPP obligatorio.", messages.first().attachmentSnippet)

    val markerId =
      repo.createMarker(
        channelId = channelId,
        title = "Torre 07",
        notes = "Revisar cimentación",
        category = MarkerCategory.CHECKPOINT,
        status = MarkerStatus.OPEN,
        lat = 19.4401,
        lng = -99.1402,
        mapX = 0.4,
        mapY = 0.6,
      )
    val marker = repo.observeMarkers(channelId).first().first { it.id == markerId }
    repo.linkDocToMarker(
      channelId = channelId,
      marker = marker,
      docId = "AbCdEfGhIjKlMnOpQr1234",
      docTitle = "Plano Cimentación Torre 07",
      docUri = docUrl,
      docSnippet = "Especificación de concreto 250 kg/cm2.",
    )

    val linkedMarker = repo.observeMarkers(channelId).first().first { it.id == markerId }
    assertEquals("AbCdEfGhIjKlMnOpQr1234", linkedMarker.linkedDocId)
    assertEquals("Plano Cimentación Torre 07", linkedMarker.linkedDocTitle)
    assertEquals(docUrl, linkedMarker.linkedDocUri)
    assertEquals("Especificación de concreto 250 kg/cm2.", linkedMarker.linkedDocSnippet)

    repo.linkDocToMarker(
      channelId = channelId,
      marker = linkedMarker,
      docId = null,
      docTitle = null,
      docUri = null,
      docSnippet = null,
    )
    val unlinkedMarker = repo.observeMarkers(channelId).first().first { it.id == markerId }
    assertNull(unlinkedMarker.linkedDocId)
    assertNull(unlinkedMarker.linkedDocTitle)
    assertNull(unlinkedMarker.linkedDocUri)
    assertNull(unlinkedMarker.linkedDocSnippet)
  }
}
