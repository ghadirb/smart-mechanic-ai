import type { DiagnosisAction } from "./types";

export const CREDIT_COST: Record<DiagnosisAction, number> = {
  text: 1,
  image: 3,
  audio: 5,
  video: 12,
};

export const CREDIT_PACKAGES = [
  { productId: "credit_20", credits: 20 },
  { productId: "credit_50", credits: 50 },
  { productId: "credit_150", credits: 150 },
] as const;

export function creditsForProduct(productId: string): number | null {
  return CREDIT_PACKAGES.find((item) => item.productId === productId)?.credits ?? null;
}

// اعتبار رایگان اولیه‌ای که هر نصب جدید هنگام ثبت‌نام (POST /api/register)
// دریافت می‌کند تا بدون نیاز به خرید، امکان تست اپ را داشته باشد.
export const INITIAL_FREE_CREDITS = 5;

// سقف اندازه base64 برای هر نوع رسانه (دفاع در عمق -- کلاینت هم این محدودیت‌ها
// را اعمال می‌کند، اما یک کلاینت دستکاری‌شده نباید بتواند با تماس مستقیم با
// این Worker از آن‌ها عبور کند). این اعداد تقریباً معادل نسخه base64 همان
// سقف‌های بایت خام در AIConfig.kt سمت اندروید هستند (نسبت ۴ به ۳).
export const MAX_PROMPT_LENGTH = 8000;
export const MAX_BASE64_LENGTH: Record<Exclude<DiagnosisAction, "text">, number> = {
  image: 14_000_000, // ~10MB خام
  audio: 11_000_000, // ~8MB خام
  video: 34_000_000, // ~25MB خام
};

export const ALLOWED_MIME_TYPES: Record<Exclude<DiagnosisAction, "text">, string[]> = {
  image: ["image/jpeg", "image/png", "image/webp"],
  audio: ["audio/mpeg", "audio/mp4", "audio/aac", "audio/3gpp", "audio/amr", "audio/wav", "audio/ogg", "audio/x-m4a"],
  video: ["video/mp4", "video/3gpp", "video/webm"],
};
