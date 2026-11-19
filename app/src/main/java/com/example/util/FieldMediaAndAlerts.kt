package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object FieldMediaAndAlerts {

  private const val ALERT_CHANNEL_ID = "geonexo_field_alerts"

  suspend fun compressImageToBase64(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
      try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
          return@withContext null
        }
        val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
        var sampleSize = 1
        while (maxSide / sampleSize > 720) {
          sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded =
          context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
          } ?: return@withContext null

        val scaled =
          if (maxOf(decoded.width, decoded.height) > 720) {
            val scale = 720f / maxOf(decoded.width, decoded.height).toFloat()
            Bitmap.createScaledBitmap(
              decoded,
              (decoded.width * scale).toInt().coerceAtLeast(1),
              (decoded.height * scale).toInt().coerceAtLeast(1),
              true,
            )
          } else {
            decoded
          }

        val out = ByteArrayOutputStream()
        var quality = 68
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > 140_000 && quality > 30) {
          out.reset()
          quality -= 12
          scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()

        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        if (b64.length > 340_000) null else b64
      } catch (_: Exception) {
        null
      }
    }

  fun decodeBase64ToBytes(base64: String?): ByteArray? {
    if (base64.isNullOrBlank()) return null
    return try {
      Base64.decode(base64, Base64.DEFAULT)
    } catch (_: Exception) {
      null
    }
  }

  fun ensureAlertNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel =
        NotificationChannel(
          ALERT_CHANNEL_ID,
          "Alertas de campo e incidencias",
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "Notificaciones instantáneas de incidencias en mapa y mensajes con pin."
        }
      manager.createNotificationChannel(channel)
    }
  }

  fun showFieldAlertNotification(
    context: Context,
    notificationId: Int,
    title: String,
    body: String,
  ): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) return false
    }
    ensureAlertNotificationChannel(context)
    val safeId = (notificationId and 0x7FFFFFFF).coerceAtLeast(1)
    val launchIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        safeId,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    return try {
      NotificationManagerCompat.from(context).notify(safeId, notification)
      true
    } catch (_: SecurityException) {
      false
    }
  }
}
