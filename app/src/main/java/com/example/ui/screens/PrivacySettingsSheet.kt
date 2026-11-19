package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsSheet(
  isAuthenticated: Boolean,
  displayName: String,
  darkMode: Boolean,
  highContrastMode: Boolean,
  locationSharingActive: Boolean,
  offlineModeSimulated: Boolean,
  onToggleDarkMode: (Boolean) -> Unit,
  onToggleHighContrast: (Boolean) -> Unit,
  onToggleLocationSharing: (Boolean) -> Unit,
  onToggleOfflineMode: (Boolean) -> Unit,
  onGoogleSignIn: () -> Unit,
  onExitDemoMode: () -> Unit,
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Column {
        Text(
          text = "Campo, Privacidad y Accesibilidad",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text =
            if (isAuthenticated) "Sesión activa: $displayName"
            else "Modo Prueba Local (sin cuenta · sin IA en la nube)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SettingsSwitchRow(
            icon = Icons.Filled.Contrast,
            title = "Modo Alto Contraste Exterior",
            subtitle = "Máxima legibilidad bajo luz solar directa",
            checked = highContrastMode,
            onCheckedChange = onToggleHighContrast,
            testTag = "switch_high_contrast",
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          SettingsSwitchRow(
            icon = Icons.Filled.DarkMode,
            title = "Modo Oscuro",
            subtitle = "Reduce fatiga visual en turnos nocturnos",
            checked = darkMode,
            onCheckedChange = onToggleDarkMode,
            testTag = "switch_dark_mode",
          )
        }
      }

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SettingsSwitchRow(
            icon = Icons.Filled.GpsFixed,
            title = stringResource(R.string.location_consent_title),
            subtitle = "Opt-in explícito con pausa inmediata por canal",
            checked = locationSharingActive,
            onCheckedChange = onToggleLocationSharing,
            testTag = "switch_location_sharing",
          )
          if (isAuthenticated) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsSwitchRow(
              icon = Icons.Filled.CloudOff,
              title = "Simular Sin Cobertura (Offline-First)",
              subtitle = "Encola mensajes y marcadores localmente hasta reconectar",
              checked = offlineModeSimulated,
              onCheckedChange = onToggleOfflineMode,
              testTag = "switch_offline_mode",
            )
          }
        }
      }

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
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

      if (isAuthenticated) {
        OutlinedButton(
          onClick = onSignOut,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Cerrar sesión")
        }
      } else {
        Button(
          onClick = onGoogleSignIn,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_google_signin_button"),
        ) {
          Icon(Icons.Filled.Login, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Iniciar sesión con Google (IA y Equipo)")
        }
        OutlinedButton(
          onClick = onExitDemoMode,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("exit_demo_button"),
        ) {
          Text("Volver a pantalla de bienvenida")
        }
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
      Box(
        modifier =
          Modifier.size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(18.dp),
        )
      }
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
