import assert from "node:assert/strict";
import test from "node:test";
import { validatedDiagnosisJson } from "../src/avalai";
import { CREDIT_COST } from "../src/creditPolicy";
import { validateDiagnoseBody } from "../src/validation";

test("credit policy matches the published media prices", () => {
  assert.deepEqual(CREDIT_COST, { text: 1, image: 3, audio: 5, video: 12 });
});

test("validates text and media requests", () => {
  assert.equal(validateDiagnoseBody({ action: "text", prompt: "صدای تق تق موتور" }), null);
  assert.equal(
    validateDiagnoseBody({ action: "image", prompt: "چراغ چک", imageBase64: "aGVsbG8=", imageMimeType: "image/jpeg" }),
    null
  );
});

test("rejects malformed or unsafe media requests before charging credits", () => {
  assert.equal(validateDiagnoseBody({ action: "video", prompt: "x" }), "MISSING_MEDIA");
  assert.equal(
    validateDiagnoseBody({ action: "audio", prompt: "x", audioBase64: "aGVsbG8=", audioMimeType: "audio/exe" }),
    "UNSUPPORTED_MIME_TYPE"
  );
  assert.equal(validateDiagnoseBody({ action: "text", prompt: "" }), "INVALID_PROMPT");
});

test("rejects truncated AI JSON so the caller can refund the credit", () => {
  assert.throws(
    () => validatedDiagnosisJson('{"summary":"ناقص", "followUpQuestions": ['),
    /AI_PROVIDER_INVALID_RESPONSE/
  );
});

test("accepts a complete contracted AI response", () => {
  const raw = JSON.stringify({
    summary: "نمونه",
    possibleCauses: [],
    urgency: "NORMAL",
    recommendations: [],
    followUpQuestions: [],
    mechanicNeeded: false,
  });
  assert.equal(validatedDiagnosisJson(raw), raw);
});
