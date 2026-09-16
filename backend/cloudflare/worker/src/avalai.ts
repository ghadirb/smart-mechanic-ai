import type { DiagnoseRequestBody, Env } from "./types";

// برگرفته دقیق از backend/apps-script/Code.gs که نسخه اثبات‌شده و کارکننده
// است. توجه: نسخه فعلی backend/firebase/functions این system prompt را کم
// دارد (فقط prompt کاربر را می‌فرستد) -- بدون آن، مدل لزوماً JSON ساختاریافته‌ای
// که DiagnosisJsonParser.kt سمت اندروید انتظار دارد را برنمی‌گرداند. اینجا آن
// را برگرداندیم تا پاسخ همیشه قابل‌پارس باشد.
const DIAGNOSIS_SYSTEM_PROMPT = [
  "تو یک دستیار هوشمند عیب‌یابی خودرو هستی، نه تعمیرکار قطعی.",
  "وظیفه تو تحلیل اطلاعات ارائه‌شده توسط کاربر شامل مشخصات خودرو،",
  "توضیح علائم، تصویر، صدا یا ویدئو و ارائه احتمالات منطقی است.",
  "",
  "قوانین:",
  "1. هرگز بدون شواهد کافی خرابی قطعی اعلام نکن.",
  "2. بین مشاهده، احتمال و تشخیص قطعی تفاوت بگذار.",
  "3. اگر داده کافی نیست، حداکثر چند سؤال تکمیلی مهم بپرس.",
  "4. برای موارد ایمنی (ترمز، فرمان، سوخت، برق فشار بالا، داغ‌کردن شدید، دود شدید،",
  "   آتش‌سوزی، نشتی شدید) احتیاط را در اولویت مطلق قرار بده.",
  "5. پاسخ را به زبان فارسی ساده ارائه کن.",
  "6. اگر کیفیت تصویر/صدا/ویدئو کافی نیست، این موضوع را صراحتاً اعلام کن.",
  "7. هیچ درصد احتمال ساختگی تولید نکن؛ از «محتمل‌تر»، «احتمال متوسط»، «نیازمند بررسی» استفاده کن.",
  "8. برای ویدئو، هم تصویر و هم صدای داخل آن را بررسی کن، نه فقط تبدیل گفتار به متن.",
  "9. خروجی را فقط و فقط به‌صورت یک شیء JSON زیر و بدون هیچ متن اضافه، بدون ```json برگردان:",
  "{",
  '  "summary": "خلاصه ساده مشکل به فارسی",',
  '  "possibleCauses": [{"title": "...", "likelihood": "LOW|MEDIUM|HIGH"}],',
  '  "urgency": "NORMAL|NEEDS_CHECK|SOON|SERIOUS|DANGER",',
  '  "recommendations": ["..."],',
  '  "safetyWarning": "متن هشدار ایمنی یا رشته خالی",',
  '  "followUpQuestions": ["..."],',
  '  "mechanicNeeded": true,',
  '  "lowQualityInputNote": "یا رشته خالی"',
  "}",
].join("\n");

function audioFormat(mimeType: string | undefined): string {
  const subtype = String(mimeType || "audio/mp4").toLowerCase().split(";")[0].split("/")[1] || "mp4";
  if (subtype === "mpeg" || subtype === "mpga") return "mp3";
  if (subtype === "mp4" || subtype === "x-m4a") return "m4a";
  return subtype;
}

/** timeout دستی روی fetch -- Workers خودش هیچ سقف صریحی برای زمان انتظار
 * subrequest تعیین نمی‌کند، اما نباید بگذاریم یک تماس کندِ AvalAI برای همیشه
 * منتظر بماند و اعتبار کاربر در حالت «در حال پردازش» قفل بماند. */
async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

export async function callAvalAi(env: Env, body: DiagnoseRequestBody): Promise<string> {
  const apiKey = env.AVALAI_API_KEY;
  const model = env.AVALAI_MODEL;
  if (!apiKey || !model) throw new Error("AI_PROVIDER_NOT_CONFIGURED");

  if (body.action === "audio") {
    const payload = {
      model,
      messages: [
        { role: "system", content: DIAGNOSIS_SYSTEM_PROMPT },
        {
          role: "user",
          content: [
            { type: "text", text: body.prompt },
            { type: "input_audio", input_audio: { data: body.audioBase64, format: audioFormat(body.audioMimeType) } },
          ],
        },
      ],
      temperature: 0.3,
      max_tokens: 2048,
    };
    const response = await fetchWithTimeout(
      "https://api.avalai.ir/v1/chat/completions",
      { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` }, body: JSON.stringify(payload) },
      55_000
    );
    const parsed: any = await response.json().catch(() => null);
    if (!response.ok) throw new Error(`AI_PROVIDER_HTTP_${response.status}`);
    const text = String(parsed?.choices?.[0]?.message?.content ?? "").trim();
    if (!text) throw new Error("AI_PROVIDER_INVALID_RESPONSE");
    return text;
  }

  // متن، عکس و ویدئو -- هر سه از همان endpoint بومی generateContent گوگل
  // عبور می‌کنند که AvalAI فقط آن را proxy می‌کند.
  const parts: Array<Record<string, unknown>> = [{ text: body.prompt }];
  if (body.action === "image" && body.imageBase64 && body.imageMimeType) {
    parts.push({ inlineData: { data: body.imageBase64, mimeType: body.imageMimeType } });
  } else if (body.action === "video" && body.videoBase64 && body.videoMimeType) {
    parts.push({ inlineData: { data: body.videoBase64, mimeType: body.videoMimeType } });
  }

  const payload = {
    contents: [{ role: "user", parts }],
    systemInstruction: { parts: [{ text: DIAGNOSIS_SYSTEM_PROMPT }] },
    generationConfig: { temperature: 0.3, maxOutputTokens: 2048, responseMimeType: "application/json" },
  };
  const url = `https://api.avalai.ir/v1beta/models/${encodeURIComponent(model)}:generateContent`;
  const response = await fetchWithTimeout(
    url,
    { method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey }, body: JSON.stringify(payload) },
    55_000
  );
  const parsed: any = await response.json().catch(() => null);
  if (!response.ok) throw new Error(`AI_PROVIDER_HTTP_${response.status}`);
  const text = String(parsed?.candidates?.[0]?.content?.parts?.map((p: any) => p.text || "").join("") ?? "").trim();
  if (!text) throw new Error("AI_PROVIDER_INVALID_RESPONSE");
  return text;
}
