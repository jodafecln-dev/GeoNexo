package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsSheet(
  displayName: String,
  darkMode: Boolean,
  highContrastMode: Boolean,
  locationSharingActive: Boolean,
  offlineModeSimulated: Boolean,
  onToggleDarkMode: (Boolean) -> Unit,
  onToggleHighContrast: (Boolean) -> Unit,
  onToggleLocationSharing: (Boolean) -> Unit,
  onToggleOfflineMode: (Boolean) -> Unit,
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Column {
        Text(
          text = "Campo, Privacidad y Accesibilidad",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text = "Sesión activa: $displayName",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      SettingsSwitchRow(
        icon = Icons.Filled.Contrast,
        title = "Modo Alto Contraste Exterior",
        subtitle = "Máxima legibilidad bajo luz solar directa en terreno",
        checked = highContrastMode,
        onCheckedChange = onToggleHighContrast,
        testTag = "switch_high_contrast",
      )

      SettingsSwitchRow(
        icon = Icons.Filled.DarkMode,
        title = "Modo Oscuro",
        subtitle = "Reduce fatiga visual en turnos nocturnos",
        checked = darkMode,
        onCheckedChange = onToggleDarkMode,
        testTag = "switch_dark_mode",
      )

      SettingsSwitchRow(
        icon = Icons.Filled.GpsFixed,
        title = stringResource(R.string.location_consent_title),
        subtitle = "Opt-in explícito con pausa inmediata por canal",
        checked = locationSharingActive,
        onCheckedChange = onToggleLocationSharing,
        testTag = "switch_location_sharing",
      )

      SettingsSwitchRow(
        icon = Icons.Filled.CloudOff,
        title = "Simular Sin Cobertura (Offline-First)",
        subtitle = "Encola mensajes y marcadores localmente hasta reconectar",
        checked = offlineModeSimulated,
        onCheckedChange = onToggleOfflineMode,
        testTag = "switch_offline_mode",
      )

      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            imageVector = Icons.Filled.PrivacyTip,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(Modifier.width(10.dp))
          Text(
            text = stringResource(R.string.location_consent_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      HorizontalDivider()

      OutlinedButton(
        onClick = onSignOut,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Cerrar sesión")
      }

      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      modifier = Modifier.weight(1f).padding(end = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.width(12.dp))
      Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}
