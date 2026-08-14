import { readFile } from "node:fs/promises";
import { after, afterEach, before, describe, test } from "node:test";
import assert from "node:assert/strict";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
} from "firebase/firestore";

const PROJECT_ID = "ararai-report-test";
const COLLECTION = "generated_content_reports";
const OWNER_UID = "owner-user";
const OTHER_UID = "other-user";
const REPORT_ID = "123e4567-e89b-12d3-a456-426614174000";
const DAY_MILLIS = 24 * 60 * 60 * 1000;

let testEnvironment;

before(async () => {
  testEnvironment = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: await readFile("firestore.rules", "utf8"),
    },
  });
});

afterEach(async () => {
  await testEnvironment.clearFirestore();
});

after(async () => {
  await testEnvironment.cleanup();
});

function reportDocument(uid = OWNER_UID, reportId = REPORT_ID) {
  return doc(
    testEnvironment.authenticatedContext(uid).firestore(),
    COLLECTION,
    `${uid}_${reportId}`,
  );
}

function validReport(overrides = {}) {
  const reportedAtMillis = Date.now() - 60_000;
  return {
    schemaVersion: 1,
    reportId: REPORT_ID,
    ownerUid: OWNER_UID,
    reportedResponse: "Generated response selected by the user.",
    reason: "FalseOrMisleading",
    comment: "Optional reviewed comment",
    context: [
      { role: "User", text: "Selected user context" },
      { role: "Assistant", text: "Selected assistant context" },
    ],
    media: { image: false, audio: false, transcript: false },
    metadata: {
      appVersion: "1.0-test",
      localeTag: "pt-BR",
      modelId: "test-model",
      runtime: "test",
    },
    reportedAt: Timestamp.fromMillis(reportedAtMillis),
    createdAt: serverTimestamp(),
    expiresAt: Timestamp.fromMillis(reportedAtMillis + 90 * DAY_MILLIS),
    ...overrides,
  };
}

async function seedValidReport() {
  await assertSucceeds(setDoc(reportDocument(), validReport()));
}

describe("generated content report creation", () => {
  test("accepts one authenticated, owner-bound, bounded report", async () => {
    await assertSucceeds(setDoc(reportDocument(), validReport()));
  });

  test("rejects unauthenticated creation", async () => {
    const firestore = testEnvironment.unauthenticatedContext().firestore();
    const target = doc(firestore, COLLECTION, `${OWNER_UID}_${REPORT_ID}`);
    await assertFails(setDoc(target, validReport()));
  });

  test("rejects owner and document-id mismatches", async () => {
    await assertFails(setDoc(reportDocument(), validReport({ ownerUid: OTHER_UID })));
    const wrongId = doc(
      testEnvironment.authenticatedContext(OWNER_UID).firestore(),
      COLLECTION,
      `wrong-prefix_${REPORT_ID}`,
    );
    await assertFails(setDoc(wrongId, validReport()));
  });

  test("rejects unknown fields and invalid field types", async () => {
    await assertFails(setDoc(reportDocument(), validReport({ unexpected: true })));
    await assertFails(setDoc(reportDocument(), validReport({ schemaVersion: "1" })));
    await assertFails(setDoc(reportDocument(), validReport({ media: { image: "no", audio: false, transcript: false } })));
  });

  test("rejects invalid enums, identifiers, and empty required content", async () => {
    await assertFails(setDoc(reportDocument(), validReport({ reason: "NotAReason" })));
    await assertFails(setDoc(reportDocument(), validReport({ reportedResponse: "" })));
    const invalidReportId = "not-a-uuid";
    await assertFails(
      setDoc(reportDocument(OWNER_UID, invalidReportId), validReport({ reportId: invalidReportId })),
    );
    const malformedUuid = "123e4567e89b-12d3-a456-426614174000-";
    await assertFails(
      setDoc(reportDocument(OWNER_UID, malformedUuid), validReport({ reportId: malformedUuid })),
    );
  });

  test("rejects content and collection values beyond their bounds", async () => {
    await assertFails(setDoc(reportDocument(), validReport({ reportedResponse: "x".repeat(8001) })));
    await assertFails(setDoc(reportDocument(), validReport({ comment: "x".repeat(501) })));
    await assertFails(
      setDoc(
        reportDocument(),
        validReport({ context: Array.from({ length: 5 }, () => ({ role: "User", text: "x" })) }),
      ),
    );
    await assertFails(
      setDoc(reportDocument(), validReport({ metadata: { ...validReport().metadata, modelId: "x".repeat(129) } })),
    );
  });

  test("rejects invalid report and retention timestamps", async () => {
    const staleMillis = Date.now() - 8 * DAY_MILLIS;
    await assertFails(
      setDoc(
        reportDocument(),
        validReport({
          reportedAt: Timestamp.fromMillis(staleMillis),
          expiresAt: Timestamp.fromMillis(staleMillis + 90 * DAY_MILLIS),
        }),
      ),
    );

    const futureMillis = Date.now() + DAY_MILLIS;
    await assertFails(
      setDoc(
        reportDocument(),
        validReport({
          reportedAt: Timestamp.fromMillis(futureMillis),
          expiresAt: Timestamp.fromMillis(futureMillis + 90 * DAY_MILLIS),
        }),
      ),
    );
    await assertFails(setDoc(reportDocument(), validReport({ expiresAt: Timestamp.now() })));
    await assertFails(setDoc(reportDocument(), validReport({ createdAt: Timestamp.now() })));
  });
});

describe("generated content report access", () => {
  test("allows only the owner to perform a point read", async () => {
    await seedValidReport();
    await assertSucceeds(getDoc(reportDocument()));

    const otherView = doc(
      testEnvironment.authenticatedContext(OTHER_UID).firestore(),
      COLLECTION,
      `${OWNER_UID}_${REPORT_ID}`,
    );
    await assertFails(getDoc(otherView));
    const anonymousView = doc(
      testEnvironment.unauthenticatedContext().firestore(),
      COLLECTION,
      `${OWNER_UID}_${REPORT_ID}`,
    );
    await assertFails(getDoc(anonymousView));
  });

  test("denies list, update, and delete even to the owner", async () => {
    await seedValidReport();
    const ownerFirestore = testEnvironment.authenticatedContext(OWNER_UID).firestore();
    await assertFails(getDocs(collection(ownerFirestore, COLLECTION)));
    await assertFails(updateDoc(reportDocument(), { comment: "changed" }));
    await assertFails(deleteDoc(reportDocument()));
  });

  test("denies every operation outside the report collection", async () => {
    const otherDocument = doc(
      testEnvironment.authenticatedContext(OWNER_UID).firestore(),
      "other_collection",
      "document",
    );
    await assertFails(setDoc(otherDocument, { value: true }));
    await assertFails(getDoc(otherDocument));
  });

  test("does not overwrite an existing idempotency document", async () => {
    await seedValidReport();
    await assertFails(setDoc(reportDocument(), validReport({ comment: "replacement" })));
    const stored = await assertSucceeds(getDoc(reportDocument()));
    assert.equal(stored.data().comment, "Optional reviewed comment");
  });
});
