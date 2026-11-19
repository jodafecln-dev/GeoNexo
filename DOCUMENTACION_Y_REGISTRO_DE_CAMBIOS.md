# GeoNexo — Documentación Técnica, Registro de Cambios y Mejoras por Sección

**Aplicación:** GeoNexo (`com.geonexo.app`)  
**Plataforma actual en workspace:** Android (Kotlin + Jetpack Compose + Material 3 + Firebase Firestore & Auth)  
**Última actualización:** 2026-11-19  

---

## 1. Resumen Detallado de Funciones Implementadas

### 1.1 Identidad, Tema y Accesibilidad (`ui/theme/`)
- **Paletas (`Color.kt`, `Theme.kt`):**
  - **Modo Claro (`GeoLightColorScheme`):** Superficies limpias (`#F4F6F9` / `#FFFFFF`), primario teal geoespacial (`#0D7C73`), secundario pizarra (`#1E3A5F`) y acento ámbar (`#D97706`).
  - **Modo Oscuro (`GeoDarkColorScheme`):** Fondo nocturno (`#0B1120`), tarjetas elevadas (`#131C31`) y acentos de alta visibilidad (`#2DD4BF`, `#FBBF24`).
  - **Modo Alto Contraste Exterior (`GeoOutdoorHighContrastScheme`):** Fondo blanco puro (`#FFFFFF`), tinta negra (`#000000`) y acentos saturados (`#005B52`, `#C2410C`) para legibilidad bajo luz solar directa en campo.
  - **Estados semánticos de mapa:** `GeoStatusOpen` (`#EF4444`), `GeoStatusInProgress` (`#F59E0B`), `GeoStatusResolved` (`#10B981`), `GeoRouteBlue` (`#3B82F6`).
- **Tipografía (`Type.kt` + `res/font/`):**
  - Fuentes locales empaquetadas con `font-util`: `Space Grotesk` (títulos y encabezados), `Plus Jakarta Sans` (cuerpo y controles) y `JetBrains Mono` (coordenadas, etiquetas técnicas y marcas de tiempo).

### 1.2 Autenticación y Control de Sesión (`auth/`)
- **`AuthManager.kt`:**
  - `attemptAutoSignIn`: inicio de sesión silencioso al abrir la app mediante `GetGoogleIdOption`.
  - `onGoogleSignInClicked`: flujo interactivo mediante `GetSignInWithGoogleOption` con manejo separado de `GetCredentialCancellationException`.
  - `signOut`: limpieza de credenciales en `withContext(NonCancellable)` antes de cerrar sesión en `Firebase.auth`.
- **`AuthScreen.kt`:**
  - Pantalla de bienvenida con ilustración hero (`img_auth_hero.jpg`), resumen de capacidades (chat/hilos, mapa compartido, bitácora offline), botón de Google Sign-In (`testTag = "google_sign_in_button"`) e indicador de privacidad GPS.
- **`MainActivity.kt` (`GeoNexoAppNavigation`):**
  - `enableEdgeToEdge()`, observación reactiva de `authStateFlow()`, bloqueo de vistas de producto hasta autenticar y aislamiento de estado/ViewModel por `key(uid)`.

### 1.3 Modelo de Datos, Seguridad y Persistencia (`data/model/`, `data/remote/`, `firestore.rules`)
- **`GeoNexoModels.kt`:**
  - Entidades `@Keep @IgnoreExtraProperties` con `@DocumentId` y `@ServerTimestamp`: `UserProfile`, `Channel`, `ChannelMessage`, `GeoMarker`, `ChannelPresence` y contenedor `SyncedCollection<T>` (`hasPendingWrites`, `fromCache`).
  - Enums seguros (`ChannelType`, `MessageType`, `MarkerCategory`, `MarkerStatus`) con método `safeOf()` resistente a variaciones de esquema.
- **`GeoNexoRepository.kt`:**
  - Conectado al ID de base de datos configurado en `R.string.firestore_database_id` (nunca `(default)`).
  - Flujos en tiempo real (`.snapshots()`) con `ServerTimestampBehavior.ESTIMATE` para `users/{uid}`, `channels`, `messages`, `markers` y `presence`.
  - `ensureUserProfileAndStarterWorkspace()`: crea perfil privado inicial y siembra 2 canales operativos (`#sector-norte-obra` y `#logistica-ruta-sur`) con marcadores y mensaje vinculado al primer ingreso.
  - Mutaciones seguras (`Result<T>`) con validación previa de longitudes/rangos, `FieldValue.serverTimestamp()` y registro estructurado JSON en `FirestoreErrorHandler.kt`.
- **`firestore.rules` y `firebase-blueprint.json`:**
  - Regla global *default-deny*, aislamiento estricto de `users/{userId}` al propietario (sin exposición de PII entre miembros), pertenencia obligatoria en `memberUids` para `channels`, `messages`, `markers` y `presence`, validación `isValid[Entity]`, inmutabilidad de `createdAt` / `ownerId` / `creatorId` / `senderId` y verificación de existencia del canal padre.

### 1.4 Interfaz Operativa y Flujos Principales (`ui/state/`, `ui/screens/`)
- **`GeoNexoViewModel.kt`:**
  - Gestión de pestaña activa (`CHANNELS`, `CHAT`, `MAP`, `DOC`), canal activo, marcador enfocado, hilo activo, búsqueda local, simulación de red offline (`db.disableNetwork()` / `db.enableNetwork()`) y pings GPS.
- **`GeoNexoHubScreen.kt`:**
  - Diseño adaptativo mediante `BoxWithConstraints`: `NavigationBar` en pantallas `< 600.dp` y `NavigationRail` en tablets/plegables `>= 600.dp`.
  - Barra superior con selector de canal, chip interactivo de estado de sincronización (`Sync` / `Cola` / `Offline`) y acceso a ajustes.
  - Solicitud bajo demanda de permisos `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` mediante `FusedLocationProviderClient` únicamente cuando el usuario activa compartir GPS.
- **`ChannelsScreen.kt`:**
  - Listado y filtrado de canales por nombre, objetivo o bitácora; diálogo de creación de canal (`create_channel_fab`).
- **`ChatScreen.kt`:**
  - Chat en tiempo real, vista dedicada de hilos con soporte `BackHandler`, selector para vincular `#marcador` del mapa (con salto directo al mapa al tocar el chip) y adjunto de imágenes mediante **Android Photo Picker** (`PickVisualMedia`).
- **`SharedMapScreen.kt`:**
  - Lienzo topográfico interactivo (`Canvas`) con gestos de *pan & pinch-zoom*, cuadrícula de coordenadas, curvas de nivel, polilíneas de ruta entre puntos, pines por estado, capas filtrables y presencia del equipo en vivo con pulso animado.
  - Modo de colocación táctil (`+ Marcador`), bandeja horizontal ergonómica para uso con una mano, botón de **Pausar/Compartir GPS en 1 toque** y `ModalBottomSheet` de detalle para editar notas, cambiar estado, eliminar o comentar en chat.
- **`CollaborativeDocScreen.kt`:**
  - Bitácora anclada al canal con alternancia Vista/Edición, inserción rápida de referencias a marcadores del mapa, exportación al portapapeles en Markdown y enlaces directos al mapa.
- **`PrivacySettingsSheet.kt`:**
  - Interruptores de **Modo Alto Contraste Exterior**, **Modo Oscuro**, **Ubicación en vivo (Opt-In + Pausa)**, **Simular Sin Cobertura**, aviso de privacidad y botón de cierre de sesión.

### 1.5 Copiloto Operativo Gemini IA (`ai/`, `ui/screens/GeminiCopilotScreen.kt`)
- **Infraestructura segura (`AppCheckInit.kt`, `firebase-ai-logic-android`):**
  - Provisión de **Firebase AI Logic** (`Firebase.ai`) sin claves `GEMINI_API_KEY` en el cliente, protegido mediante **Firebase App Check** inicializado al inicio de `MainActivity.onCreate()` (`DebugAppCheckProviderFactory` en compilaciones debug y `RecaptchaAppCheckProviderFactory` en producción).
- **`GeminiCopilotService.kt`:**
  - Modelo `gemini-3.8-flash` con respuesta en *streaming* (`generateContentStream`) y tiempo límite controlado (`withTimeout(60_000L)`).
  - Inyección de contexto en vivo del canal (`ChannelContextSnapshot`): bitácora anclada, hasta 20 marcadores del mapa con coordenadas/estado, miembros con GPS en vivo y últimos 15 mensajes del canal.
  - Entrada multimodal mediante **Android Photo Picker** con escalado seguro de imagen (`<= 1280px`, compresión JPEG bajo 20 MB).
  - Extractor estructurado (`Schema.obj` + `responseMimeType = "application/json"`) que sugiere acciones operativas de un toque (`CREATE_MARKER`, `APPEND_DOC`, `POST_CHAT`).
- **Integración transversal en la interfaz y ViewModel dedicado (`GeminiChatViewModel.kt`):**
  - `GeminiChatViewModel` gestiona exclusivamente el ciclo de streaming, historial por canal, timeouts y errores de Gemini (`AiStreamState`), desacoplado de `GeoNexoViewModel`.
  - Pestaña dedicada **Gemini** (`WorkspaceTab.GEMINI`) en `GeoNexoHubScreen` con historial por canal, chips de prompts rápidos, barra de contexto vivo y botones directos **Al Chat** / **A Bitácora** o **Aplicar Acción Sugerida**.
  - Acceso directo desde **Chat** (`Resumir con Gemini`), **Mapa** (`Analizar punto con Gemini` en detalle del marcador) y **Bitácora** (`Completar o resumir bitácora con Gemini`).

---

## 2. Registro de Cambios (`Changelog`)

| Versión / Hito | Fecha | Descripción del Cambio | Archivos Afectados | Estado |
|---|---|---|---|---|
| **v0.1.0 — Base e Identidad** | 2026-11-19 | Configuración de `applicationId` (`com.geonexo.app`), nombre `GeoNexo`, icono adaptativo personalizado (`#0B162A`), ilustración onboarding y fuentes locales (`Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`). | `app/build.gradle.kts`, `settings.gradle.kts`, `strings.xml`, `metadata.json`, `res/drawable/*`, `res/mipmap-*/*`, `res/font/*` | Completado |
| **v0.2.0 — Backend Firebase y Reglas** | 2026-11-19 | Provisión de proyecto Firebase (`gen-lang-client-0477105695`), esquema IR, reglas zero-trust con paridad Kotlin y pruebas unitarias en emulador local (3/3 aprobadas) + despliegue con `DeployRules`. | `google-services.json`, `firebase_applet_config.xml`, `firebase-blueprint.json`, `firestore.rules`, `firebase.json`, `firestore.test.js` | Verificado y Desplegado |
| **v0.3.0 — App GeoNexo MVP Compose** | 2026-11-19 | Implementación completa de autenticación Google Credential Manager, arquitectura MVVM, tema Claro/Oscuro/Exterior, canales, chat con hilos/fotos/#marcadores, mapa topográfico interactivo con GPS opt-in y bitácora colaborativa. | `MainActivity.kt`, `auth/*`, `data/*`, `ui/theme/*`, `ui/state/*`, `ui/screens/*`, `AndroidManifest.xml` | Compilado y Probado (`compile_applet` + `:app:testDebugUnitTest`) |
| **v0.4.0 — Copiloto Gemini IA Integrado** | 2026-11-19 | Integración completa de Firebase AI Logic (`gemini-3.8-flash`) y App Check, `GeminiChatViewModel` dedicado para streaming consciente del contexto del canal (mensajes, mapa, GPS, bitácora), análisis multimodal de fotos de campo, extracción estructurada de acciones en 1 toque y disparadores contextuales en Chat, Mapa y Bitácora. | `.env.example`, `app/build.gradle.kts`, `firebase_applet_config.xml`, `MainActivity.kt`, `ai/*`, `GeminiChatViewModel.kt`, `GeoNexoViewModel.kt`, `GeoNexoHubScreen.kt`, `GeminiCopilotScreen.kt`, `ChatScreen.kt`, `SharedMapScreen.kt`, `CollaborativeDocScreen.kt` | Compilado y Probado |
| **v0.5.0 — Rediseño Visual y Cromático** | 2026-11-19 | Renovación de paleta *Spatial Daylight* y *Obsidian Spatial* con jerarquía tonal Material 3 (`surfaceContainer*`), gradientes espaciales teal-cobalto, burbujas asimétricas con avatares en Chat, barra de búsqueda/filtros tipo píldora y `ExtendedFAB` en Canales, mapa con fondo radial y cuadrícula mayor/menor, tarjetas agrupadas en Ajustes y transiciones suaves entre pestañas. | `Color.kt`, `Theme.kt`, `AuthScreen.kt`, `GeoNexoHubScreen.kt`, `ChannelsScreen.kt`, `ChatScreen.kt`, `SharedMapScreen.kt`, `CollaborativeDocScreen.kt`, `GeminiCopilotScreen.kt`, `PrivacySettingsSheet.kt` | Implementado |
| **v0.6.0 — Modo Prueba sin Login y Conexión Remota por Invitación** | 2026-11-19 | Entrada directa en **Modo Prueba Local** (canales, chat, mapa, GPS local y bitácora 100% funcionales en memoria sin cuenta ni llamadas a Firestore); **login con Google bajo demanda** exclusivamente al usar **Gemini IA** o conexión remota; flujo real multiusuario vía Cloud Firestore con códigos de invitación (`/channel_invites/{inviteCode}`), unión por código (`Unirse`), reglas zero-trust verificadas en emulador (4/4) y desplegadas. | `MainActivity.kt`, `AuthScreen.kt`, `GeoNexoViewModel.kt`, `GeoNexoRepository.kt`, `GeoNexoModels.kt`, `ChannelsScreen.kt`, `GeminiCopilotScreen.kt`, `GeoNexoHubScreen.kt`, `PrivacySettingsSheet.kt`, `strings.xml`, `firebase-blueprint.json`, `firestore.rules`, `firestore.test.js` | Verificado y Desplegado |

---

## 3. Sugerencias de Mejora por Sección (Camino a Producción y Cumplimiento de RNF)

### A. Autenticación, Multi-Tenant y RBAC
1. **Invitación y unión real a canales por código (Implementado en v0.6.0):** Se agregó `/channel_invites/{inviteCode}` con validación estricta en `firestore.rules` para conectar usuarios en ubicaciones distintas en tiempo real sin exponer listas de códigos. **Siguiente paso:** Agregar fecha de expiración (`expiresAt`) y revocación/expulsión de miembros desde ajustes del canal.
2. **Aislamiento por Organización (`organizationId`) y Roles Granulares:** Agregar entidad `Organization` y `Membership` (`ADMIN`, `COORDINATOR`, `FIELD`, `GUEST`) validada en `firestore.rules` y límites explícitos (`memberUids.size() <= 200` conforme al RNF).
3. **SSO Corporativo y MFA:** Integrar OIDC/SAML (Google Workspace / Microsoft Entra ID) y TOTP MFA obligatorio para roles administrativos.

### B. Chat en Tiempo Real, Adjuntos y Búsqueda
1. **Paginación por Cursor y Virtualización:** `observeMessages` actualmente carga todos los mensajes del canal. **Mejora:** Aplicar `.orderBy("createdAt", DESC).limit(50)` con paginación incremental al hacer *scroll* hacia arriba para mantener latencia `p95 < 300 ms` en canales con miles de mensajes.
2. **Subida Real de Archivos (hasta 50 MB) a Object Storage:** Hoy `attachmentUri` almacena la URI local devuelta por Photo Picker. **Mejora:** Integrar subida directa a S3/Cloud Storage mediante URLs prefirmadas con compresión automática de imágenes en campo, generación de miniaturas y escaneo antivirus asíncrono.
3. **Búsqueda Global (`Meilisearch` / `OpenSearch`):** Indexar mensajes, bitácoras y marcadores filtrados por `organizationId` y `memberUids`.

### C. Mapa Compartido, GPS y Eficiencia Energética (< 5%/hora)
1. **Motor Cartográfico Real con Tiles Offline (`MapLibre GL` + `PMTiles`):** Reemplazar/complementar el lienzo vectorial actual con `MapLibre Compose` cargando calles/satélite reales y descarga previa de regiones (*bounding box*) para zonas sin señal.
2. **Muestreo GPS Adaptativo (`FusedLocationProviderClient` + `ActivityRecognition`):** Ajustar intervalo de reporte según movimiento/batería (ej. cada 30–60 s en desplazamiento, pausa automática si está estacionario o batería < 15%) y agrupar pings (*batching*) para garantizar **consumo < 5% por hora**.
3. **Polígonos y Edición de Vértices de Rutas:** Permitir trazar polilíneas y geocercas multi-punto directamente sobre el mapa y exportar/importar GeoJSON/KML.

### D. Documentos Colaborativos (Bitácora)
1. **Edición Simultánea Sin Conflictos (CRDT / Yjs):** Hoy `pinnedDocBody` sobrescribe el campo completo al pulsar *Guardar*. **Mejora:** Migrar a actualizaciones CRDT incrementales (Yjs o delta ops) con cursores de presencia en vivo para evitar pérdida de ediciones concurrentes al reconectar desde modo offline.
2. **Checklist Interactivo y Plantillas:** Convertir líneas `[ ]` en casillas tocables directamente en modo vista y permitir plantillas por industria (inspección de obra, entrega logística, bitácora de mantenimiento).

### E. Offline-First, Sincronización (< 10 s) y Calidad
1. **Cola Outbox Persistente + `WorkManager`:** Complementar la caché nativa de Firestore con una cola explícita para subida de fotos/archivos binarios grandes y reintentos con *backoff* exponencial al recuperar red, midiendo telemetría de reconciliación `< 10 s`.
2. **Suite de Pruebas UI Compose y Carga:** Añadir pruebas de interfaz con `ComposeTestRule` aprovechando los `testTag` ya incluidos en todos los controles clave, más pruebas de carga (`k6`) en backend.

### F. Seguridad, Privacidad y Cumplimiento (LFPDPPP / GDPR / CCPA)
1. **Purga Automática de Historial de Ubicación (TTL):** Configurar política de retención máxima (ej. 7–30 días) y borrado automático en servidor.
2. **Portal/Flujo In-App de Derechos ARCO y Exportación:** Añadir en `PrivacySettingsSheet` un botón funcional para exportar datos del usuario en JSON/PDF y solicitar eliminación/anonimización verificable de cuenta.
3. **Auditoría Inmutable (`audit_logs`):** Registrar cambios de membresía, permisos, descargas de documentos y borrados de marcadores críticos.
