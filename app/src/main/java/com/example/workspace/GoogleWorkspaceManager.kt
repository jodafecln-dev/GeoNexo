package com.example.workspace

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class GoogleDocSummary(
  val id: String,
  val title: String,
  val webViewLink: String,
  val modifiedTime: String? = null,
  val shared: Boolean = false,
  val snippet: String? = null,
)

sealed interface WorkspaceAuthResult {
  data class Authorized(val accessToken: String) : WorkspaceAuthResult
  data class NeedsConsent(val intentSender: IntentSender) : WorkspaceAuthResult
  data class Error(val message: String) : WorkspaceAuthResult
}

class WorkspaceUnauthorizedException(message: String) : IllegalStateException(message)

object GoogleWorkspaceManager {

  private const val TAG = "GoogleWorkspaceMgr"

  private val WORKSPACE_SCOPES =
    listOf(
      Scope("https://www.googleapis.com/auth/documents"),
      Scope("https://www.googleapis.com/auth/drive.readonly"),
      Scope("https://www.googleapis.com/auth/drive.file"),
    )

  private val ALLOWED_HOSTS = setOf("docs.google.com", "drive.google.com")
  private val DOC_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{6,128}$")
  private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .build()
  }

  fun isValidGoogleWorkspaceUrl(rawUrl: String): Boolean {
    val trimmed = rawUrl.trim()
    if (trimmed.length !in 24..500) return false
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return scheme == "https" && host in ALLOWED_HOSTS
  }

  fun extractGoogleDocId(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (!isValidGoogleWorkspaceUrl(trimmed)) return null
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val segments = uri.pathSegments.orEmpty()
    val dIndex = segments.indexOf("d")
    if (dIndex != -1 && dIndex + 1 < segments.size) {
      val candidate = segments[dIndex + 1]
      if (DOC_ID_REGEX.matches(candidate)) return candidate
    }
    val queryId = uri.getQueryParameter("id")?.trim()
    if (queryId != null && DOC_ID_REGEX.matches(queryId)) return queryId
    return null
  }

  fun canonicalDocUrl(docId: String, rawUri: String? = null): String {
    val cleanRaw = rawUri?.trim()
    if (!cleanRaw.isNullOrBlank() && isValidGoogleWorkspaceUrl(cleanRaw)) {
      return cleanRaw.take(500)
    }
    return "https://docs.google.com/document/d/$docId/edit"
  }

  suspend fun authorize(context: Context): WorkspaceAuthResult {
    return try {
      val request =
        AuthorizationRequest.builder()
          .setRequestedScopes(WORKSPACE_SCOPES)
          .build()
      val result =
        Identity.getAuthorizationClient(context)
          .authorize(request)
          .await()
      if (result.hasResolution()) {
        val pendingIntent = result.pendingIntent
        if (pendingIntent != null) {
          WorkspaceAuthResult.NeedsConsent(pendingIntent.intentSender)
        } else {
          WorkspaceAuthResult.Error("No se recibió la pantalla de permisos de Google Drive/Docs.")
        }
      } else {
        val token = result.accessToken
        if (!token.isNullOrBlank()) {
          WorkspaceAuthResult.Authorized(token)
        } else {
          WorkspaceAuthResult.Error("Google no devolvió un token de acceso válido.")
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: ApiException) {
      Log.w(TAG, "AuthorizationClient ApiException (${e.statusCode}): ${e.message}")
      WorkspaceAuthResult.Error(
        "No se pudo autorizar Google Docs/Drive (código ${e.statusCode}). Verifica tu sesión de Google."
      )
    } catch (e: Exception) {
      Log.e(TAG, "AuthorizationClient failure", e)
      WorkspaceAuthResult.Error(e.localizedMessage ?: "Error al conectar con Google Drive/Docs.")
    }
  }

  fun extractTokenFromIntent(context: Context, data: Intent?): Result<String> {
    return try {
      val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
      val token = result.accessToken
      if (!token.isNullOrBlank()) {
        Result.success(token)
      } else {
        Result.failure(IllegalStateException("Autorización cancelada o sin token disponible."))
      }
    } catch (e: ApiException) {
      Result.failure(
        IllegalStateException("Permiso de Google Docs/Drive cancelado (código ${e.statusCode}).", e)
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun listGoogleDocs(
    accessToken: String,
    queryFilter: String = "",
  ): Result<List<GoogleDocSummary>> =
    withContext(Dispatchers.IO) {
      try {
        val sanitizedQuery = queryFilter.trim().replace("'", "").replace("\\", "").take(60)
        val qParts =
          mutableListOf(
            "mimeType='application/vnd.google-apps.document'",
            "trashed=false",
          )
        if (sanitizedQuery.isNotEmpty()) {
          qParts.add("name contains '$sanitizedQuery'")
        }
        val encodedQ = URLEncoder.encode(qParts.joinToString(" and "), "UTF-8")
        val url =
          "https://www.googleapis.com/drive/v3/files" +
            "?q=$encodedQ" +
            "&orderBy=modifiedTime%20desc" +
            "&pageSize=25" +
            "&fields=files(id,name,webViewLink,modifiedTime,shared)"

        val request =
          Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
          val bodyStr = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext Result.failure(mapHttpError(response.code, bodyStr))
          }
          val json = JSONObject(bodyStr)
          val files = json.optJSONArray("files") ?: JSONArray()
          val list =
            buildList {
              for (i in 0 until files.length()) {
                val item = files.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val title = item.optString("name").ifBlank { "Documento sin título" }.take(120)
                val link =
                  item.optString("webViewLink").takeIf { isValidGoogleWorkspaceUrl(it) }
                    ?: "https://docs.google.com/document/d/$id/edit"
                add(
                  GoogleDocSummary(
                    id = id,
                    title = title,
                    webViewLink = link.take(500),
                    modifiedTime = item.optString("modifiedTime").takeIf { it.isNotBlank() },
                    shared = item.optBoolean("shared", false),
                  )
                )
              }
            }
          Result.success(list)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error listing Google Docs", e)
        Result.failure(e)
      }
    }

  suspend fun fetchDocTextAndMeta(
    accessToken: String,
    docId: String,
  ): Result<Pair<String, GoogleDocSummary>> =
    withContext(Dispatchers.IO) {
      try {
        require(DOC_ID_REGEX.matches(docId)) { "Identificador de Google Doc inválido." }

        val metaUrl =
          "https://www.googleapis.com/drive/v3/files/$docId" +
            "?fields=id,name,webViewLink,modifiedTime,shared,trashed,mimeType,description"
        val metaReq =
          Request.Builder()
            .url(metaUrl)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        var title = "Documento Google"
        var webViewLink = "https://docs.google.com/document/d/$docId/edit"
        var modifiedTime: String? = null
        var shared = false
        var mimeType = "application/vnd.google-apps.document"
        var description = ""

        httpClient.newCall(metaReq).execute().use { resp ->
          val bodyStr = resp.body?.string().orEmpty()
          if (!resp.isSuccessful) {
            return@withContext Result.failure(mapHttpError(resp.code, bodyStr))
          }
          val json = JSONObject(bodyStr)
          if (json.optBoolean("trashed", false)) {
            return@withContext Result.failure(
              IllegalStateException("Este documento fue enviado a la papelera de Google Drive.")
            )
          }
          title = json.optString("name").ifBlank { title }.take(120)
          webViewLink =
            json.optString("webViewLink").takeIf { isValidGoogleWorkspaceUrl(it) } ?: webViewLink
          modifiedTime = json.optString("modifiedTime").takeIf { it.isNotBlank() }
          shared = json.optBoolean("shared", false)
          mimeType = json.optString("mimeType").ifBlank { mimeType }
          description = json.optString("description").trim()
        }

        val fullText =
          if (mimeType == "application/vnd.google-apps.document") {
            val docsUrl = "https://docs.googleapis.com/v1/documents/$docId"
            val docsReq =
              Request.Builder()
                .url(docsUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(docsReq).execute().use { resp ->
              val bodyStr = resp.body?.string().orEmpty()
              if (!resp.isSuccessful) {
                return@withContext Result.failure(mapHttpError(resp.code, bodyStr))
              }
              extractPlainTextFromDocsJson(JSONObject(bodyStr))
            }
          } else {
            description.ifBlank { "Archivo de Google Drive ($mimeType): $title" }
          }

        val cleanText = fullText.trim().take(7000)
        val snippet = cleanText.replace(Regex("\\s+"), " ").trim().take(320).ifBlank { null }
        val summary =
          GoogleDocSummary(
            id = docId,
            title = title,
            webViewLink = webViewLink,
            modifiedTime = modifiedTime,
            shared = shared,
            snippet = snippet,
          )
        Result.success(Pair(cleanText, summary))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error fetching Google Doc $docId", e)
        Result.failure(e)
      }
    }

  suspend fun createAndShareGoogleDoc(
    accessToken: String,
    title: String,
    bodyText: String,
    makeAnyoneReader: Boolean = true,
  ): Result<GoogleDocSummary> =
    withContext(Dispatchers.IO) {
      try {
        val cleanTitle = title.trim().ifBlank { "Bitácora GeoNexo" }.take(120)
        val cleanBody = bodyText.trim().take(20_000)

        val createPayload = JSONObject().put("title", cleanTitle).toString()
        val createReq =
          Request.Builder()
            .url("https://docs.googleapis.com/v1/documents")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(createPayload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val documentId =
          httpClient.newCall(createReq).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
              return@withContext Result.failure(mapHttpError(resp.code, bodyStr))
            }
            JSONObject(bodyStr).getString("documentId")
          }

        if (cleanBody.isNotEmpty()) {
          val insertAction =
            JSONObject()
              .put(
                "insertText",
                JSONObject()
                  .put("location", JSONObject().put("index", 1))
                  .put("text", "$cleanBody\n"),
              )
          val batchPayload = JSONObject().put("requests", JSONArray().put(insertAction)).toString()
          val batchReq =
            Request.Builder()
              .url("https://docs.googleapis.com/v1/documents/$documentId:batchUpdate")
              .addHeader("Authorization", "Bearer $accessToken")
              .post(batchPayload.toRequestBody(JSON_MEDIA_TYPE))
              .build()
          httpClient.newCall(batchReq).execute().use { resp ->
            if (!resp.isSuccessful) {
              val bodyStr = resp.body?.string().orEmpty()
              Log.w(TAG, "batchUpdate warning ${resp.code}: $bodyStr")
            }
          }
        }

        var sharedSuccess = false
        if (makeAnyoneReader) {
          val permPayload =
            JSONObject()
              .put("role", "reader")
              .put("type", "anyone")
              .toString()
          val permReq =
            Request.Builder()
              .url("https://www.googleapis.com/drive/v3/files/$documentId/permissions")
              .addHeader("Authorization", "Bearer $accessToken")
              .post(permPayload.toRequestBody(JSON_MEDIA_TYPE))
              .build()
          runCatching {
            httpClient.newCall(permReq).execute().use { resp ->
              sharedSuccess = resp.isSuccessful
            }
          }
        }

        val snippet = cleanBody.replace(Regex("\\s+"), " ").trim().take(320).ifBlank { null }
        Result.success(
          GoogleDocSummary(
            id = documentId,
            title = cleanTitle,
            webViewLink = "https://docs.google.com/document/d/$documentId/edit",
            shared = sharedSuccess,
            snippet = snippet,
          )
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error creating Google Doc", e)
        Result.failure(e)
      }
    }

  private fun extractPlainTextFromDocsJson(docJson: JSONObject): String {
    val bodyObj = docJson.optJSONObject("body") ?: return ""
    val contentArray = bodyObj.optJSONArray("content") ?: return ""
    val sb = StringBuilder()
    appendStructuralElements(contentArray, sb)
    return sb.toString()
  }

  private fun appendStructuralElements(elements: JSONArray, sb: StringBuilder) {
    for (i in 0 until elements.length()) {
      if (sb.length >= 7000) break
      val el = elements.optJSONObject(i) ?: continue
      el.optJSONObject("paragraph")?.let { paragraph ->
        val pEls = paragraph.optJSONArray("elements") ?: return@let
        for (j in 0 until pEls.length()) {
          val textRun = pEls.optJSONObject(j)?.optJSONObject("textRun") ?: continue
          val content = textRun.optString("content")
          if (content.isNotEmpty()) sb.append(content)
        }
      }
      el.optJSONObject("table")?.let { table ->
        val rows = table.optJSONArray("tableRows") ?: return@let
        for (r in 0 until rows.length()) {
          val cells = rows.optJSONObject(r)?.optJSONArray("tableCells") ?: continue
          for (c in 0 until cells.length()) {
            val cellContent = cells.optJSONObject(c)?.optJSONArray("content") ?: continue
            appendStructuralElements(cellContent, sb)
          }
        }
      }
    }
  }

  private fun mapHttpError(code: Int, body: String): Exception {
    return when (code) {
      401 ->
        WorkspaceUnauthorizedException(
          "Tu autorización de Google Docs/Drive expiró. Vuelve a autorizar el acceso."
        )
      403 ->
        IllegalStateException(
          "Sin permiso para leer este documento en Drive (403). Pide al autor compartirlo o verifica tus permisos."
        )
      404 ->
        IllegalStateException(
          "El documento no existe o fue eliminado de Google Drive (404)."
        )
      else -> {
        Log.w(TAG, "Workspace HTTP $code: ${body.take(300)}")
        IllegalStateException("Error de Google Docs/Drive (HTTP $code).")
      }
    }
  }
}
