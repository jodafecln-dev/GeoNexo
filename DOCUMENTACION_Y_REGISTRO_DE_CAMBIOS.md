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
| **v0.7.0 — Mapa OSMDroid Real, Fotos Compartidas, Room Persistente, Conflictos de Bitácora, Ciclo de Miembros, Paginación y Alertas** | 2026-11-19 | 1) **OSMDroid (`osmdroid-android:6.1.20`)** con capas reales OpenStreetMap Calles y OpenTopoMap Topográfico (caché interno en `cacheDir`, cero permisos de almacenamiento) + fallback vectorial Esquemático y coordenadas `(lat, lng)` reales. 2) **Fotos compartidas entre dispositivos:** compresión automática JPEG (`<= 720px`, `<= 140 KB` en Base64 `attachmentData`) renderizada en Chat con vista ampliada y botón **"Analizar con Gemini"**. 3) **Modo Prueba Persistente con Room + KSP (`DemoLocalDatabase`)**. 4) **Bitácora resistente a conflictos + Historial inmutable (`doc_revisions`)** con alerta y opciones Combinar / Cargar equipo / Mantener / Restaurar. 5) **Gestión completa de equipo (`ChannelMembersDialog`):** nombres (`memberNames`), revocar códigos, expulsar o abandonar canal. 6) **Paginación incremental (`orderBy(createdAt, DESC).limit(pageSize)`), GPS continuo de bajo consumo (`PRIORITY_BALANCED_POWER_ACCURACY`, 30 s / 15 m) y alertas locales opt-in (`POST_NOTIFICATIONS`)** ante nuevas incidencias remotas. | `libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`, `firebase-blueprint.json`, `firestore.rules`, `firestore.test.js`, `GeoNexoModels.kt`, `DemoLocalDatabase.kt`, `FieldMediaAndAlerts.kt`, `GeoNexoRepository.kt`, `GeminiCopilotService.kt`, `GeminiChatViewModel.kt`, `GeoNexoViewModel.kt`, `MainActivity.kt`, `OsmMapViewport.kt`, `SharedMapScreen.kt`, `ChatScreen.kt`, `CollaborativeDocScreen.kt`, `ChannelMembersDialog.kt`, `ChannelsScreen.kt`, `PrivacySettingsSheet.kt`, `GeoNexoHubScreen.kt`, `ExampleRobolectricTest.kt` | Verificado (`compile_applet` + Robolectric + Emulador 5/5) |
| **v0.8.0 — Auditoría Zero-Trust, Cero 404 y Cero Puntos Ciegos** | 2026-11-19 | 1) **Aislamiento inmediato de ex-miembros en `firestore.rules`:** `messages`, `markers` y `presence` exigen `isChannelMember(channelId)` en `get`, `list`, `create`, `update` y `delete` (eliminando lecturas residuales por `memberUids` históricos); límite alineado a `200` miembros por canal; integridad de `pinnedDocRevision` y `memberNames` al unirse. 2) **Invitaciones con caducidad (`expiresAt` 7 días) y `WriteBatch` atómico** + manejo limpio de `NOT_FOUND` (sin errores 404 crudos) y limpieza de pin GPS al salir. 3) **Fotos sin fugas locales:** bloqueo en reglas de URIs `content://`/`file://`, omisión en envíos cloud y fallo explícito al usuario si la compresión falla. 4) **Mapa OSMDroid sin tiles 404:** límite de zoom OpenTopoMap a `15.0`, `setUseDataConnection(!offlineNetworkDisabled)`, validación de coordenadas finitas e indicador de caché offline en HUD. 5) **Centro de Alertas in-app (`Badge` + modal)** y `PendingIntent` en notificaciones para cero puntos ciegos. | `firebase-blueprint.json`, `firestore.rules`, `firestore.test.js`, `GeoNexoRepository.kt`, `FieldMediaAndAlerts.kt`, `GeoNexoViewModel.kt`, `OsmMapViewport.kt`, `SharedMapScreen.kt`, `GeoNexoHubScreen.kt` | Verificado y Desplegado (Emulador 6/6) |
| **v0.9.0 — Integración Google Docs & Drive (Opción A + B) + Análisis Gemini** | 2026-11-19 | 1) **Opción A (Tarjetas enriquecidas y Zero-Trust):** soporte `MessageType.DOC_LINK` en Chat y campos `linkedDocId`/`linkedDocTitle`/`linkedDocUri`/`linkedDocSnippet` en marcadores de Mapa; validación estricta de dominio `^https://(docs\|drive)\.google\.com/.*` en `firestore.rules` y Kotlin; caché local de extracto (*snippet*) para lectura offline y apertura externa segura vía `Intent.ACTION_VIEW`. 2) **Opción B (OAuth 2.0 nativo + Drive v3 / Docs v1 + Gemini IA):** autorización incremental con `Identity.getAuthorizationClient` (`documents`, `drive.readonly`, `drive.file`) sin backend ni `requestOfflineAccess`; diálogo selector `GoogleDocPickerDialog` (buscar en Drive, crear Doc compartido o pegar enlace validado); exportación directa de la **Bitácora + Marcadores** a Google Docs (`Exportar bitácora y mapa a Google Docs`); lectura de texto plano de Docs v1 inyectada a `GeminiChatViewModel.analyzeGoogleDoc(...)` mediante botón **"Analizar IA"** en Chat y Mapa. 3) **Migración Room `1 -> 2` (`MIGRATION_1_2`)** para persistencia local de enlaces y extractos sin pérdida de datos. | `libs.versions.toml`, `app/build.gradle.kts`, `firebase-blueprint.json`, `firestore.rules`, `firestore.test.js`, `GeoNexoModels.kt`, `GoogleWorkspaceManager.kt`, `DemoLocalDatabase.kt`, `GeoNexoRepository.kt`, `GeminiCopilotService.kt`, `GeminiChatViewModel.kt`, `GeoNexoViewModel.kt`, `GoogleDocPickerDialog.kt`, `ChatScreen.kt`, `SharedMapScreen.kt`, `CollaborativeDocScreen.kt`, `GeoNexoHubScreen.kt`, `ExampleRobolectricTest.kt` | Verificado y Desplegado (`compile_applet` + Robolectric + Emulador 7/7) |

---

## 3. Sugerencias de Mejora por Sección (Camino a Producción y Cumplimiento de RNF)

### A. Autenticación, Multi-Tenant y RBAC
1. **Gestión de miembros, caducidad y revocación de invitaciones (Implementado en v0.7.0–v0.8.0):** `memberNames`, revocación atómica con `WriteBatch`, `expiresAt` automático a 7 días, aislamiento inmediato al expulsar miembros y tope de `200` miembros validado en `firestore.rules` (6/6 pruebas en emulador). **Siguiente paso:** Límite opcional de usos máximos por código (`maxUses`).
2. **Aislamiento por Organización (`organizationId`) y Roles Granulares:** Agregar entidad `Organization` y `Membership` (`ADMIN`, `COORDINATOR`, `FIELD`, `GUEST`) validada en `firestore.rules`.
3. **SSO Corporativo y MFA:** Integrar OIDC/SAML (Google Workspace / Microsoft Entra ID) y TOTP MFA obligatorio para roles administrativos.

### B. Chat en Tiempo Real, Adjuntos y Búsqueda
1. **Paginación incremental (Implementado en v0.7.0):** `observeMessages` ahora consulta por `.orderBy("createdAt", DESC).limit(pageSize)` con botón **"Cargar mensajes anteriores"**.
2. **Fotos compartidas entre dispositivos (Implementado en v0.7.0):** Imágenes de campo comprimidas (`<= 140 KB` JPEG -> Base64 `attachmentData` validado `<= 350 KB` en reglas) visibles para todo el equipo remoto y analizables directamente con Gemini. **Siguiente paso para archivos hasta 50 MB (PDFs, planos DWG, video):** habilitar Cloud Storage / S3 con URLs prefirmadas y escaneo antivirus.
3. **Búsqueda Global (`Meilisearch` / `OpenSearch`):** Indexar mensajes, bitácoras y marcadores filtrados por `organizationId` y `memberUids`.

### C. Mapa Compartido, GPS y Eficiencia Energética (< 5%/hora)
1. **Motor cartográfico real OSMDroid + modo Esquemático offline (Implementado en v0.7.0):** Tiles reales OpenStreetMap y OpenTopoMap cacheados en `cacheDir` sin permisos invasivos de almacenamiento. **Siguiente paso:** Descarga explícita de regiones (*bounding box*) con `CacheManager` para expediciones 100% sin señal.
2. **Muestreo GPS continuo balanceado (Implementado en v0.7.0):** `LocationCallback` con `PRIORITY_BALANCED_POWER_ACCURACY` (cada 30 s o 15 m mínimos de desplazamiento) desmontado automáticamente al pausar.
3. **Polígonos y geocercas:** Exportación/importación GeoJSON y KML.

### D. Documentos Colaborativos (Bitácora)
1. **Detección de conflictos e historial inmutable (Implementado en v0.7.0):** `pinnedDocRevision` + subcolección inmutable `/doc_revisions` con resolución no destructiva (Combinar / Ver equipo / Mantener / Restaurar). **Siguiente paso:** Edición CRDT en tiempo real por carácter (Yjs) si varios editores escriben simultáneamente cada segundo.
2. **Checklist Interactivo y Plantillas:** Convertir líneas `[ ]` en casillas tocables directamente en modo vista.

### E. Persistencia Local, Alertas y Calidad
1. **Room Persistente en Modo Prueba (Implementado en v0.7.0):** `DemoLocalDatabase` conserva canales, chat, marcadores, bitácora y preferencias tras cerrar la app, sin tocar Firestore ni `signInAnonymously()`.
2. **Alertas locales opt-in (`POST_NOTIFICATIONS`) (Implementado en v0.7.0):** Notificaciones en tiempo real cuando un compañero remoto registra una incidencia o pin geoespacial. **Siguiente paso:** Push en segundo plano cuando la app está cerrada mediante Cloud Functions + FCM.

### F. Seguridad, Privacidad y Cumplimiento (LFPDPPP / GDPR / CCPA)
1. **Purga Automática de Historial de Ubicación (TTL):** Configurar política de retención máxima (ej. 7–30 días) y borrado automático en servidor.
2. **Portal/Flujo In-App de Derechos ARCO y Exportación:** Añadir en `PrivacySettingsSheet` un botón funcional para exportar datos del usuario en JSON/PDF y solicitar eliminación/anonimización verificable de cuenta.
