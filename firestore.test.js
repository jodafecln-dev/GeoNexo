const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHANNEL_ID = "ch_campo_norte";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("channels").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(
    unauthDb.collection("channels").doc(CHANNEL_ID).set({
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      name: "Test",
      topic: "Topic",
      channelType: "FIELD",
      pinnedDocTitle: "Doc",
      pinnedDocBody: "",
      pinnedDocUpdatedBy: "Alice",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profile: owner can create/update; cross-user read and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Campo",
      roleTitle: "Coordinadora",
      highContrastMode: false,
      darkMode: true,
      locationSharingEnabled: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      highContrastMode: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Ghost field denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Channel, Messages, GeoMarkers & Presence: member flows succeed, non-member and unfiltered queries fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t0 = new Date(Date.now() - 5000);

  // Create channel
  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).set({
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      name: "Obra Sector Norte",
      topic: "Inspeccion electrica y red hidraulica",
      channelType: "FIELD",
      pinnedDocTitle: "Bitacora Sector Norte",
      pinnedDocBody: "# Resumen\n- Revision subestacion",
      pinnedDocUpdatedBy: "Alice",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Filtered query matches Kotlin repository
  await assertSucceeds(
    aliceDb.collection("channels").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Unfiltered channel list fails
  await assertFails(aliceDb.collection("channels").get());

  // Non-member Bob cannot read channel
  await assertFails(bobDb.collection("channels").doc(CHANNEL_ID).get());

  // Member Alice creates marker
  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("markers").doc("mk_1").set({
      channelId: CHANNEL_ID,
      creatorId: ALICE_UID,
      updatedByName: "Alice",
      memberUids: [ALICE_UID],
      title: "Poste #14 danado",
      notes: "Requiere cuadrilla con grua",
      category: "INCIDENT",
      status: "OPEN",
      lat: 19.4326,
      lng: -99.1332,
      mapX: 0.45,
      mapY: 0.62,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Member Alice creates message linking marker (optional fields omitted when null)
  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_1").set({
      channelId: CHANNEL_ID,
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID],
      body: "Revisen el punto marcado en el sector norte",
      linkedMarkerId: "mk_1",
      linkedMarkerTitle: "Poste #14 danado",
      messageType: "GEO_PIN",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Non-member Bob cannot post message into Alice's channel even if adding himself to memberUids
  await assertFails(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_bad").set({
      channelId: CHANNEL_ID,
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [BOB_UID],
      body: "Intrusion",
      messageType: "TEXT",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Presence opt-in & instant pause
  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("presence").doc(ALICE_UID).set({
      channelId: CHANNEL_ID,
      userId: ALICE_UID,
      displayName: "Alice",
      memberUids: [ALICE_UID],
      sharingActive: true,
      lat: 19.4330,
      lng: -99.1330,
      mapX: 0.5,
      mapY: 0.5,
      accuracyMeters: 12.5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("presence").doc(ALICE_UID).update({
      sharingActive: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("markers").doc("mk_future").set({
      channelId: CHANNEL_ID,
      creatorId: ALICE_UID,
      updatedByName: "Alice",
      memberUids: [ALICE_UID],
      title: "Future",
      notes: "",
      category: "CHECKPOINT",
      status: "OPEN",
      lat: 19.4,
      lng: -99.1,
      mapX: 0.2,
      mapY: 0.2,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Remote multi-user connection: Alice generates invite code, Bob joins and collaborates, Carol stays blocked", async () => {
  const CAROL_UID = "carol_uid_789";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore();
  const t0 = new Date(Date.now() - 5000);

  // Alice creates channel and initial message/marker before Bob joins
  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).set({
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      name: "obra-remota",
      topic: "Coordinacion entre dos ubicaciones",
      channelType: "FIELD",
      pinnedDocTitle: "Bitacora Remota",
      pinnedDocBody: "Inicio",
      pinnedDocUpdatedBy: "Alice",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_initial").set({
      channelId: CHANNEL_ID,
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID],
      body: "Mensaje antes de invitar a Bob",
      messageType: "TEXT",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("markers").doc("mk_initial").set({
      channelId: CHANNEL_ID,
      creatorId: ALICE_UID,
      updatedByName: "Alice",
      memberUids: [ALICE_UID],
      title: "Punto inicial",
      notes: "Creado por Alice",
      category: "CHECKPOINT",
      status: "OPEN",
      lat: 19.43,
      lng: -99.13,
      mapX: 0.5,
      mapY: 0.5,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Alice creates invite code GNX42A
  await assertSucceeds(
    aliceDb.collection("channel_invites").doc("GNX42A").set({
      inviteCode: "GNX42A",
      channelId: CHANNEL_ID,
      channelName: "obra-remota",
      ownerId: ALICE_UID,
      createdAt: t0,
    })
  );

  // Non-owner Bob cannot create invite for Alice's channel
  await assertFails(
    bobDb.collection("channel_invites").doc("BOB999").set({
      inviteCode: "BOB999",
      channelId: CHANNEL_ID,
      channelName: "obra-remota",
      ownerId: BOB_UID,
      createdAt: t0,
    })
  );

  // Listing all invites is strictly denied (no code scraping)
  await assertFails(bobDb.collection("channel_invites").get());

  // Bob looks up exact code GNX42A
  await assertSucceeds(bobDb.collection("channel_invites").doc("GNX42A").get());

  // Carol cannot join without a valid invite code
  await assertFails(
    carolDb.collection("channels").doc(CHANNEL_ID).update({
      memberUids: [ALICE_UID, CAROL_UID],
      joinCode: "FAKE00",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob joins channel using GNX42A (cannot add Carol or modify name/ownerId)
  await assertFails(
    bobDb.collection("channels").doc(CHANNEL_ID).update({
      memberUids: [ALICE_UID, BOB_UID, CAROL_UID],
      joinCode: "GNX42A",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).update({
      memberUids: [ALICE_UID, BOB_UID],
      joinCode: "GNX42A",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Once joined, Bob sees the channel, Alice's pre-existing messages & markers, and posts a reply
  await assertSucceeds(
    bobDb.collection("channels").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").get()
  );
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("markers").get()
  );
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_bob").set({
      channelId: CHANNEL_ID,
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      body: "Conectado desde campo sur!",
      messageType: "TEXT",
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Carol is still denied read access to messages & markers
  await assertFails(
    carolDb.collection("channels").doc(CHANNEL_ID).collection("messages").get()
  );
  await assertFails(
    carolDb.collection("channels").doc(CHANNEL_ID).collection("markers").get()
  );
});

test("Shared Base64 photo, doc revisions, message pagination query, invite revocation & member leave", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t0 = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).set({
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      name: "obra-fotos",
      topic: "Bitacora con historial y fotos",
      channelType: "FIELD",
      pinnedDocTitle: "Bitacora v1",
      pinnedDocBody: "Estado inicial",
      pinnedDocUpdatedBy: "Alice",
      pinnedDocRevision: 1,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  await assertSucceeds(
    aliceDb.collection("channel_invites").doc("GNX888").set({
      inviteCode: "GNX888",
      channelId: CHANNEL_ID,
      channelName: "obra-fotos",
      ownerId: ALICE_UID,
      createdAt: t0,
    })
  );

  // Bob joins and registers his display name in memberNames
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      joinCode: "GNX888",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Bob posts a compressed shared field photo
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_photo").set({
      channelId: CHANNEL_ID,
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      body: "Foto de valvula reparada",
      attachmentLabel: "Foto de campo compartida",
      attachmentData: "/9j/4AAQSkZJRgABAQAAAQABAAD/2w==",
      messageType: "PHOTO",
      createdAt: new Date(Date.now() - 1500),
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Oversized attachmentData (> 350000 chars) rejected
  await assertFails(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_huge").set({
      channelId: CHANNEL_ID,
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      body: "Too big",
      attachmentData: "A".repeat(360000),
      messageType: "PHOTO",
      createdAt: new Date(Date.now() - 1200),
      updatedAt: new Date(Date.now() - 1200),
    })
  );

  // Paginated message query works
  await assertSucceeds(
    aliceDb
      .collection("channels")
      .doc(CHANNEL_ID)
      .collection("messages")
      .orderBy("createdAt", "desc")
      .limit(30)
      .get()
  );

  // Immutable bitacora revision creation
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("doc_revisions").doc("rev_2").set({
      channelId: CHANNEL_ID,
      authorId: BOB_UID,
      authorName: "Bob",
      title: "Bitacora v2",
      body: "Valvula cerrada",
      revisionNumber: 2,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Cannot mutate or delete immutable doc_revision
  await assertFails(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("doc_revisions").doc("rev_2").delete()
  );

  // Bob can leave the channel cleanly
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).update({
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Alice revokes invite code
  await assertSucceeds(aliceDb.collection("channel_invites").doc("GNX888").delete());
  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).update({
      joinCode: null,
      updatedAt: new Date(Date.now() - 300),
    })
  );
});

test("Zero-Trust Hardening: expelled member loses old reads/writes, expired invites fail, content:// URI blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t0 = new Date(Date.now() - 10000);
  const futureExpiry = new Date(Date.now() + 7 * 24 * 3600 * 1000);
  const pastExpiry = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).set({
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      name: "seguridad-zt",
      topic: "Pruebas de aislamiento estricto",
      channelType: "FIELD",
      pinnedDocTitle: "Bitacora ZT",
      pinnedDocBody: "Inicial",
      pinnedDocUpdatedBy: "Alice",
      pinnedDocRevision: 1,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Expired invite code (createdAt earlier than pastExpiry)
  await assertSucceeds(
    aliceDb.collection("channel_invites").doc("EXP001").set({
      inviteCode: "EXP001",
      channelId: CHANNEL_ID,
      channelName: "seguridad-zt",
      ownerId: ALICE_UID,
      createdAt: t0,
      expiresAt: pastExpiry,
    })
  );

  // Joining with expired code must fail
  await assertFails(
    bobDb.collection("channels").doc(CHANNEL_ID).update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      joinCode: "EXP001",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Valid invite code with 7-day expiry
  await assertSucceeds(
    aliceDb.collection("channel_invites").doc("VAL007").set({
      inviteCode: "VAL007",
      channelId: CHANNEL_ID,
      channelName: "seguridad-zt",
      ownerId: ALICE_UID,
      createdAt: t0,
      expiresAt: futureExpiry,
    })
  );

  // Joining user cannot tamper with pinnedDocRevision or rename Alice
  await assertFails(
    bobDb.collection("channels").doc(CHANNEL_ID).update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      pinnedDocRevision: 99,
      joinCode: "VAL007",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("channels").doc(CHANNEL_ID).update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Impostor", [BOB_UID]: "Bob" },
      joinCode: "VAL007",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Valid join succeeds
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      joinCode: "VAL007",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Local content:// URI must be rejected in Firestore messages
  await assertFails(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_local_uri").set({
      channelId: CHANNEL_ID,
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      body: "Foto con uri local",
      attachmentUri: "content://media/picker/10001",
      messageType: "PHOTO",
      createdAt: new Date(Date.now() - 800),
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Bob creates a valid message, marker, and presence while a member
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_bob_ok").set({
      channelId: CHANNEL_ID,
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      body: "Reporte valido",
      messageType: "TEXT",
      createdAt: new Date(Date.now() - 700),
      updatedAt: new Date(Date.now() - 700),
    })
  );
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("markers").doc("mk_bob").set({
      channelId: CHANNEL_ID,
      creatorId: BOB_UID,
      updatedByName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      title: "Valvula Norte",
      notes: "Revision",
      category: "CHECKPOINT",
      status: "OPEN",
      lat: 19.43,
      lng: -99.13,
      mapX: 0.5,
      mapY: 0.5,
      createdAt: new Date(Date.now() - 700),
      updatedAt: new Date(Date.now() - 700),
    })
  );
  await assertSucceeds(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("presence").doc(BOB_UID).set({
      channelId: CHANNEL_ID,
      userId: BOB_UID,
      displayName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      sharingActive: true,
      lat: 19.43,
      lng: -99.13,
      mapX: 0.5,
      mapY: 0.5,
      accuracyMeters: 10.0,
      updatedAt: new Date(Date.now() - 700),
    })
  );

  // Alice expels Bob from the channel
  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).update({
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Bob IMMEDIATELY loses read, list, update, and delete access even to docs containing his old UID
  await assertFails(bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_bob_ok").get());
  await assertFails(bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").get());
  await assertFails(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_bob_ok").update({
      body: "Edit after expulsion",
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(bobDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_bob_ok").delete());
  await assertFails(bobDb.collection("channels").doc(CHANNEL_ID).collection("markers").doc("mk_bob").get());
  await assertFails(bobDb.collection("channels").doc(CHANNEL_ID).collection("markers").doc("mk_bob").delete());
  await assertFails(bobDb.collection("channels").doc(CHANNEL_ID).collection("presence").doc(BOB_UID).get());
  await assertFails(
    bobDb.collection("channels").doc(CHANNEL_ID).collection("presence").doc(BOB_UID).update({
      sharingActive: false,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Google Docs & Drive integration: valid Docs/Drive URLs in Chat and Markers succeed, non-Google phishing URLs fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const t0 = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).set({
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      name: "docs-obra",
      topic: "Planos y protocolos en Google Docs",
      channelType: "PROJECT",
      pinnedDocTitle: "Bitacora",
      pinnedDocBody: "Inicial",
      pinnedDocUpdatedBy: "Alice",
      pinnedDocRevision: 1,
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Valid Google Doc in Chat
  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_gdoc").set({
      channelId: CHANNEL_ID,
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID],
      body: "Adjunto manual de seguridad electrica",
      attachmentLabel: "Manual Electrico Sector Norte",
      attachmentUri: "https://docs.google.com/document/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/edit",
      attachmentSnippet: "Desenergizar tablero principal antes de intervenir poste 14.",
      messageType: "DOC_LINK",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Arbitrary third-party / phishing HTTPS URL must be rejected
  await assertFails(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("messages").doc("msg_phish").set({
      channelId: CHANNEL_ID,
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID],
      body: "Enlace externo no permitido",
      attachmentLabel: "Archivo sospechoso",
      attachmentUri: "https://evil.example.com/fake-doc",
      messageType: "DOC_LINK",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // GeoMarker with linked Google Doc succeeds and can be unlinked
  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("markers").doc("mk_gdoc").set({
      channelId: CHANNEL_ID,
      creatorId: ALICE_UID,
      updatedByName: "Alice",
      memberUids: [ALICE_UID],
      title: "Transformador T2",
      notes: "Revisar diagrama unifilar en Doc adjunto",
      category: "CHECKPOINT",
      status: "OPEN",
      lat: 19.4326,
      lng: -99.1332,
      mapX: 0.5,
      mapY: 0.5,
      linkedDocId: "1AbCdEfGhIjKlMnOpQrStUvWxYz",
      linkedDocTitle: "Diagrama Unifilar T2",
      linkedDocUri: "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/view",
      linkedDocSnippet: "Voltaje nominal 23kV, fusible 40A.",
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Non-Google URL in GeoMarker rejected
  await assertFails(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("markers").doc("mk_gdoc").update({
      linkedDocUri: "https://not-google.com/doc",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Unlinking Google Doc via null update succeeds
  await assertSucceeds(
    aliceDb.collection("channels").doc(CHANNEL_ID).collection("markers").doc("mk_gdoc").update({
      linkedDocId: null,
      linkedDocTitle: null,
      linkedDocUri: null,
      linkedDocSnippet: null,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});


