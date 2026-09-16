// شناسایی کاربر با یک توکن تصادفی که خودِ این Worker صادر می‌کند، نه با
// user_id ای که کلاینت انتخاب می‌کند -- تا تغییر user_id توسط کاربر باعث
// دسترسی به موجودی کاربر دیگر نشود (رجوع کنید به README، بخش امنیت).
// فقط هش SHA-256 توکن در دیتابیس ذخیره می‌شود، نه خودِ توکن.

export async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomHex(byteLength: number): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function generateToken(): string {
  return randomHex(32); // ۲۵۶ بیت آنتروپی -- برای حدس زدن غیرممکن است
}

export function generateUserId(): string {
  return crypto.randomUUID();
}

export function bearerToken(authorization: string | null): string | null {
  if (!authorization) return null;
  const match = authorization.match(/^Bearer (.+)$/i);
  return match ? match[1].trim() : null;
}
