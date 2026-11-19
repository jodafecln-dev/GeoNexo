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
