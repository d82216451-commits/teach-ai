const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
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

test("Unauthenticated user: cannot read teachings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("teachings").get());
});

test("Authenticated user: Alice can create and read her own teaching", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const teachingRef = aliceDb.collection("users").doc(ALICE_UID).collection("teachings").doc("t1");
  await assertSucceeds(
    teachingRef.set({
      userId: ALICE_UID,
      category: "preference",
      content: "I prefer dark mode and concise summaries.",
      importance: "high",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(teachingRef.get());
});

test("Cross-user access: Bob cannot read Alice's teaching", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("teachings").doc("t1").set({
      userId: ALICE_UID,
      category: "personal",
      content: "Alice's secret note",
      importance: "normal",
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("teachings").doc("t1").get());
});

test("Validation: Rejects teaching with invalid category", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const teachingRef = aliceDb.collection("users").doc(ALICE_UID).collection("teachings").doc("t2");
  await assertFails(
    teachingRef.set({
      userId: ALICE_UID,
      category: "unsupported_category",
      content: "Some content",
      importance: "normal",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: Alice can create and read messages, Bob cannot read", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const msgRef = aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("m1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      role: "user",
      content: "Hello AI!",
      createdAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("messages").doc("m1").get());
});
