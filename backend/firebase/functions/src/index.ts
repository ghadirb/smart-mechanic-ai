import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { onRequest } from "firebase-functions/v2/https";
import { CREDIT_COST, CREDIT_PACKAGES, type DiagnosisAction } from "./creditPolicy.js";

initializeApp();
const db = getFirestore();
const avalAiKey = defineSecret("AVALAI_API_KEY");
const avalAiModel = defineSecret("AVALAI_MODEL");

type DiagnosisRequest = {
  action: DiagnosisAction;
  prompt: string;
  imageBase64?: string;
  imageMimeType?: string;
  audioBase64?: string;
  audioMimeType?: string;
  videoBase64?: string;
  videoMimeType?: string;
};

function sendJson(res: any, status: number, body: unknown) {
  res.status(status).json(body);
}

async function authenticatedUid(authorization?: string): Promise<string> {
  const match = authorization?.match(/^Bearer (.+)$/i);
  if (!match) throw new Error("UNAUTHENTICATED");
  const token = await getAuth().verifyIdToken(match[1]);
  return token.uid;
}

function validAction(value: unknown): value is DiagnosisAction {
  return value === "text" || value === "image" || value === "audio" || value === "video";
}

async function callAvalAi(request: DiagnosisRequest): Promise<string> {
  const apiKey = avalAiKey.value();
  const model = avalAiModel.value();
  if (!apiKey || !model) throw new Error("AI_PROVIDER_NOT_CONFIGURED");

  const parts: Array<Record<string, unknown>> = [{ text: request.prompt }];
  const media = request.action === "image"
    ? { data: request.imageBase64, mimeType: request.imageMimeType }
    : request.action === "video"
      ? { data: request.videoBase64, mimeType: request.videoMimeType }
      : null;
  if (media?.data && media.mimeType) {
    parts.push({ inlineData: { data: media.data, mimeType: media.mimeType } });
  }

  // Audio uses AvalAI's OpenAI-compatible input_audio endpoint.
  const url = request.action === "audio"
    ? "https://api.avalai.ir/v1/chat/completions"
    : `https://api.avalai.ir/v1beta/models/${encodeURIComponent(model)}:generateContent`;
  const body = request.action === "audio"
    ? {
        model,
        messages: [{ role: "user", content: [
          { type: "text", text: request.prompt },
          { type: "input_audio", input_audio: { data: request.audioBase64, format: String(request.audioMimeType || "audio/mp4").split("/")[1] } },
        ] }],
      }
    : { contents: [{ role: "user", parts }] };

  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => null) as any;
  if (!response.ok) throw new Error(`AI_PROVIDER_HTTP_${response.status}`);
  const raw = request.action === "audio"
    ? payload?.choices?.[0]?.message?.content
    : payload?.candidates?.[0]?.content?.parts?.map((part: any) => part.text || "").join("");
  if (typeof raw !== "string" || raw.trim() === "") throw new Error("AI_PROVIDER_INVALID_RESPONSE");
  return raw;
}

export const api = onRequest(
  { region: "europe-west1", timeoutSeconds: 120, memory: "512MiB", secrets: [avalAiKey, avalAiModel] },
  async (req, res) => {
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Idempotency-Key");
    if (req.method === "OPTIONS") return res.status(204).send();

    try {
      const uid = await authenticatedUid(req.header("Authorization"));
      if (req.method === "GET" && req.path.endsWith("/credits")) {
        const user = await db.collection("users").doc(uid).get();
        return sendJson(res, 200, { balance: user.data()?.balance ?? 0, packages: CREDIT_PACKAGES, costs: CREDIT_COST });
      }
      if (req.method !== "POST" || !req.path.endsWith("/diagnose")) return sendJson(res, 404, { error: "NOT_FOUND" });

      const input = req.body as DiagnosisRequest;
      const requestId = req.header("X-Idempotency-Key");
      if (!requestId || requestId.length < 16 || !validAction(input?.action) || !input.prompt?.trim()) {
        return sendJson(res, 400, { error: "INVALID_REQUEST" });
      }
      const cost = CREDIT_COST[input.action];
      const ledgerRef = db.collection("users").doc(uid).collection("ledger").doc(requestId);
      const userRef = db.collection("users").doc(uid);
      await db.runTransaction(async (transaction) => {
        const existing = await transaction.get(ledgerRef);
        if (existing.exists) throw new Error("DUPLICATE_REQUEST");
        const user = await transaction.get(userRef);
        const balance = Number(user.data()?.balance ?? 0);
        if (balance < cost) throw new Error("INSUFFICIENT_CREDITS");
        transaction.set(userRef, { balance: balance - cost, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
        transaction.create(ledgerRef, { type: "diagnosis", action: input.action, delta: -cost, status: "processing", createdAt: FieldValue.serverTimestamp() });
      });

      try {
        const raw = await callAvalAi(input);
        await ledgerRef.update({ status: "completed", completedAt: FieldValue.serverTimestamp() });
        return sendJson(res, 200, { success: true, raw, creditsCharged: cost });
      } catch (error) {
        await db.runTransaction(async (transaction) => {
          transaction.update(userRef, { balance: FieldValue.increment(cost), updatedAt: FieldValue.serverTimestamp() });
          transaction.update(ledgerRef, { status: "refunded", refundReason: String(error), refundedAt: FieldValue.serverTimestamp() });
        });
        throw error;
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "INTERNAL_ERROR";
      const status = message === "UNAUTHENTICATED" ? 401
        : message === "INSUFFICIENT_CREDITS" ? 402
          : message === "DUPLICATE_REQUEST" ? 409
            : 500;
      return sendJson(res, status, { error: message });
    }
  },
);
