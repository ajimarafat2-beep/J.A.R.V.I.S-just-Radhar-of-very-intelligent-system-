const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_jarvis_uid";
const BOB_UID = "bob_jarvis_uid";

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

test("Unauthenticated user cannot read or write user profiles or creations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("creations").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("creations").doc("c_1").set({
      userId: ALICE_UID,
      mediaType: "VIDEO",
      prompt: "Orbiting satellite over Dhaka",
      title: "Dhaka Orbit",
      modelUsed: "gemini-3.8-flash",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Authenticated owner: valid Kotlin UserProfile and StudioCreation create, update, list, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Arafat",
      email: "arafat@example.com",
      preferredLanguage: "BENGALI",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "AUTO",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("vid_1").set({
      userId: ALICE_UID,
      mediaType: "VIDEO",
      prompt: "Neon Stark workshop hologram tour",
      title: "Stark Workshop Tour",
      summary: "3-scene cinematic AI video with Kore voiceover",
      modelUsed: "gemini-3.8-flash + gemini-nano-banana-2.1",
      voiceName: "Kore",
      durationSeconds: 10,
      localFilePath: "/data/user/0/app/files/jarvis_media/vid_1.mp4",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("vid_1").update({
      title: "Stark Workshop HD Tour",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("vid_1").delete()
  );
});

test("Security bounds: reject @DocumentId 'id', immutable mutations, invalid mediaType, future timestamp, and cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // @DocumentId `id` field in payload must be rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("bad_id_doc").set({
      id: "bad_id_doc",
      userId: ALICE_UID,
      mediaType: "IMAGE",
      prompt: "Cyberpunk tower",
      title: "Cyberpunk Tower",
      modelUsed: "gemini-nano-banana-2.1",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Invalid mediaType must be rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("bad_type").set({
      userId: ALICE_UID,
      mediaType: "EXECUTABLE",
      prompt: "Test",
      title: "Test",
      modelUsed: "gemini-3.8-flash",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Future timestamp must be rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("future_ts").set({
      userId: ALICE_UID,
      mediaType: "AUDIO",
      prompt: "Good morning Sir",
      title: "Greeting Audio",
      modelUsed: "gemini-3.1-flash-tts-preview",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user isolation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("alice_only").set({
      userId: ALICE_UID,
      mediaType: "IMAGE",
      prompt: "Gold core",
      title: "Gold Core",
      modelUsed: "gemini-nano-banana-2.1",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("creations").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("creations").doc("alice_only").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("creations").doc("alice_only").delete());

  // Immutable createdAt mutation must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("alice_only").update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
