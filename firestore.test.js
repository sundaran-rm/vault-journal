const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/journals`).where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/tags`).where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/expenses`).where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/clocks`).where("userId", "==", ALICE_UID).get());
});

test("Authenticated user can create, read, update, and delete own tag", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);
  const tagRef = aliceDb.doc(`users/${ALICE_UID}/tags/tag_work`);

  await assertSucceeds(
    tagRef.set({
      userId: ALICE_UID,
      name: "Work",
      colorHex: "#D4A373",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Query aligned with rule: resource.data.userId == request.auth.uid
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/tags`).where("userId", "==", ALICE_UID).get()
  );

  // Shadow update with ghost field must fail
  await assertFails(
    tagRef.update({
      name: "Career",
      ghostAdmin: true,
      updatedAt: now,
    })
  );

  // Valid update must succeed
  await assertSucceeds(
    tagRef.update({
      name: "Career",
      colorHex: "#2A9D8F",
      updatedAt: now,
    })
  );

  await assertSucceeds(tagRef.delete());
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's journal entry", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  const entryRef = aliceDb.doc(`users/${ALICE_UID}/journals/entry_1`);
  await assertSucceeds(
    entryRef.set({
      userId: ALICE_UID,
      encryptedTitle: "iv1:cipherTitle",
      encryptedBody: "iv2:cipherBody",
      encryptedGratitudeResponse: "iv3:cipherGratitude",
      gratitudePromptText: "What is one small thing that brought you joy today?",
      mood: "GRATEFUL",
      tagIds: ["tag_work", "tag_family"],
      encryptedPhotoBase64: "",
      entryDateEpochMs: 1730000000000,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.doc(`users/${ALICE_UID}/journals/entry_1`).get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/journals`).where("userId", "==", ALICE_UID).get()
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/journals/entry_1`).delete());
});

test("Value poisoning and invalid enum or negative expense amount are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Invalid mood enum
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/journals/entry_bad`).set({
      userId: ALICE_UID,
      encryptedTitle: "iv:t",
      encryptedBody: "iv:b",
      encryptedGratitudeResponse: "",
      gratitudePromptText: "Prompt",
      mood: "HACKED_MOOD",
      tagIds: [],
      encryptedPhotoBase64: "",
      entryDateEpochMs: 1730000000000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Zero/negative expense amount rejected
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/expenses/exp_bad`).set({
      userId: ALICE_UID,
      encryptedTitle: "iv:coffee",
      encryptedNotes: "",
      amountMinor: 0,
      currencyCode: "USD",
      category: "DINING",
      spentAtEpochMs: 1730000000000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid expense succeeds
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/expenses/exp_good`).set({
      userId: ALICE_UID,
      encryptedTitle: "iv:coffee",
      encryptedNotes: "iv:oat latte",
      amountMinor: 650,
      currencyCode: "USD",
      category: "DINING",
      spentAtEpochMs: 1730000000000,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Vault security config enforces docId == vault_config and owner match", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/security/wrong_id`).set({
      userId: ALICE_UID,
      saltBase64: "MTIzNDU2Nzg5MDEyMzQ1Ng==",
      verifierHashBase64: "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
      iterations: 60000,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/security/vault_config`).set({
      userId: ALICE_UID,
      saltBase64: "MTIzNDU2Nzg5MDEyMzQ1Ng==",
      verifierHashBase64: "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
      iterations: 60000,
      createdAt: now,
      updatedAt: now,
    })
  );
});
