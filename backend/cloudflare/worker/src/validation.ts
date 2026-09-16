import { ALLOWED_MIME_TYPES, MAX_BASE64_LENGTH, MAX_PROMPT_LENGTH } from "./creditPolicy";
import type { DiagnoseRequestBody } from "./types";

const VALID_ACTIONS = new Set(["text", "image", "audio", "video"]);

/** برمی‌گرداند: پیام خطا اگر بدنه نامعتبر بود، یا null اگر معتبر بود. */
export function validateDiagnoseBody(body: any): string | null {
  if (!body || typeof body !== "object") return "INVALID_REQUEST";
  if (!VALID_ACTIONS.has(body.action)) return "INVALID_ACTION";
  if (typeof body.prompt !== "string" || !body.prompt.trim() || body.prompt.length > MAX_PROMPT_LENGTH) {
    return "INVALID_PROMPT";
  }
  if (body.action === "text") return null;

  const action = body.action as "image" | "audio" | "video";
  const base64: unknown = body[`${action}Base64`];
  const mimeType: unknown = body[`${action}MimeType`];
  if (typeof base64 !== "string" || base64.length === 0) return "MISSING_MEDIA";
  if (base64.length > MAX_BASE64_LENGTH[action]) return "MEDIA_TOO_LARGE";
  if (typeof mimeType !== "string" || !ALLOWED_MIME_TYPES[action].includes(mimeType.toLowerCase())) {
    return "UNSUPPORTED_MIME_TYPE";
  }
  return null;
}

export function toDiagnoseRequestBody(body: any): DiagnoseRequestBody {
  return {
    action: body.action,
    prompt: body.prompt,
    imageBase64: body.imageBase64,
    imageMimeType: body.imageMimeType,
    audioBase64: body.audioBase64,
    audioMimeType: body.audioMimeType,
    videoBase64: body.videoBase64,
    videoMimeType: body.videoMimeType,
  };
}
