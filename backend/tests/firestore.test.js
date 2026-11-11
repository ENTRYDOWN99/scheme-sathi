const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rulesPath = fs.existsSync("./firestore.rules")
    ? "./firestore.rules"
    : path.join(__dirname, "../firestore.rules");
  const rules = fs.readFileSync(rulesPath, "utf8");
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

test("Unauthenticated user: cannot read or write profile, applications, or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("profile").doc("main").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("applications").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
});

test("Authenticated user: can create and read own valid CitizenProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("profile")
      .doc("main")
      .set({
        userId: ALICE_UID,
        encryptedName: "ENC:Ramesh Kumar",
        encryptedPhone: "ENC:9876543210",
        birthYear: 1962,
        ageConfirmedViaVoice: true,
        gender: "male",
        state: "Uttar Pradesh",
        district: "Varanasi",
        residenceType: "rural",
        annualIncome: 48000,
        occupation: "farmer",
        bplStatus: "yes",
        socialCategory: "obc",
        hasBankAccount: "yes",
        hasLpgConnection: "no",
        ownsAgriculturalLand: "yes",
        isIncomeTaxPayer: "no",
        familySize: 4,
        consentGranted: true,
        preferredLanguage: "hi",
        role: "CITIZEN",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("profile").doc("main").get()
  );
});

test("Authenticated user: cannot read another user's profile or tasks (cross-user isolation)", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("profile")
      .doc("main")
      .set({
        userId: BOB_UID,
        encryptedName: "ENC:Bob",
        encryptedPhone: "ENC:9123456789",
        birthYear: 1980,
        ageConfirmedViaVoice: true,
        gender: "male",
        state: "Bihar",
        district: "Patna",
        residenceType: "rural",
        annualIncome: 60000,
        occupation: "farmer",
        bplStatus: "yes",
        socialCategory: "general",
        hasBankAccount: "yes",
        hasLpgConnection: "yes",
        ownsAgriculturalLand: "yes",
        isIncomeTaxPayer: "no",
        familySize: 3,
        consentGranted: true,
        preferredLanguage: "hi",
        role: "CITIZEN",
        createdAt: now,
        updatedAt: now,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("profile").doc("main").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("tasks").get()
  );
});

test("Shadow Update Test: rejects ghost fields on CollaborativeTask", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .set({
        userId: ALICE_UID,
        title: "Verify Land Record",
        description: "Check Khatauni at CSC",
        assigneeName: "Suresh CSC",
        assigneeRole: "CSC_OPERATOR",
        priority: "HIGH",
        status: "PENDING",
        schemeCode: "PM-KISAN",
        dueDateLabel: "Tomorrow 10:00 AM",
        deliveryChannel: "IN_APP",
        deliveryStatus: "SCHEDULED",
        activityLogSummary: "Created task",
        isGhostAdminField: true,
        createdAt: now,
        updatedAt: now,
      })
  );
});
