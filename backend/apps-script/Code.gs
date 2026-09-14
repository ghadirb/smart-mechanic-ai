/**
 * مکانیک هوشمند AI — Backend واسط روی Google Apps Script
 * ---------------------------------------------------------
 * این نسخه بر پایه معماری اثبات‌شده و تست‌شده در پروژه DriveMate-AI ساخته شده:
 * به‌جای gapgpt، از AvalAI (یک پروکسی سازگار با فرمت بومی Gemini) استفاده می‌کند.
 * مزیت اصلی نسبت به نسخه قبلی: چون AvalAI همان endpoint بومی
 * generateContent گوگل را proxy می‌کند، تحلیل ویدئو هم کامل کار می‌کند
 * (نه فقط متن، عکس و صدا).
 *
 * کلید API فقط سمت سرور (Script Properties) نگه‌داری می‌شود و هرگز به کلاینت
 * اندروید فرستاده نمی‌شود.
 *
 * نصب:
 * 1) https://script.google.com → پروژه جدید → این فایل را در Code.gs پیست کنید.
 * 2) از AvalAI (https://avalai.ir) یک کلید API بگیرید.
 * 3) Project Settings → Script Properties → این مقدارها را اضافه کنید:
 *      - AVALAI_API_KEY  = کلید AvalAI شما
 *      - CAR_DIAGNOSIS_MODEL = gemini-3.1-pro-preview   (یا هر مدل دیگری که حساب شما فعال دارد)
 *      - AVALAI_BASE_URL = https://api.avalai.ir         (اختیاری، این مقدار پیش‌فرض است)
 *      - APP_SECRET      = یک رشته دلخواه (اختیاری، برای محدود کردن دسترسی)
 * 4) Deploy → New deployment → Web app → Execute as: Me، Who has access: Anyone
 * 5) آدرس .../exec را در local.properties اپ اندروید به‌عنوان PROXY_URL بگذارید.
 *
 * برای تعویض مدل در آینده، فقط CAR_DIAGNOSIS_MODEL را اینجا تغییر دهید و دوباره
 * Deploy کنید — نیازی به انتشار نسخه جدید اپلیکیشن نیست.
 */

var DIAGNOSIS_SYSTEM_PROMPT = [
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
  "}"
].join("\n");

function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents);
    var props = PropertiesService.getScriptProperties();

    var appSecret = props.getProperty("APP_SECRET");
    if (appSecret && body.appSecret !== appSecret) {
      return jsonResponse({ success: false, error: "دسترسی غیرمجاز." });
    }

    var apiKey = props.getProperty("AVALAI_API_KEY");
    if (!apiKey) {
      return jsonResponse({ success: false, error: "AVALAI_API_KEY در Script Properties تنظیم نشده است." });
    }
    var model = props.getProperty("CAR_DIAGNOSIS_MODEL") || "gemini-3.1-pro-preview";
    var baseUrl = (props.getProperty("AVALAI_BASE_URL") || "https://api.avalai.ir").replace(/\/$/, "");

    var action = body.action;
    var raw;

    if (action === "text") {
      raw = callGeminiNative_(baseUrl, apiKey, model, body.prompt, null, null);
    } else if (action === "image") {
      raw = callGeminiNative_(baseUrl, apiKey, model, body.prompt, body.imageBase64, body.imageMimeType);
    } else if (action === "video") {
      raw = callGeminiNative_(baseUrl, apiKey, model, body.prompt, body.videoBase64, body.videoMimeType);
    } else if (action === "audio") {
      // مطابق تجربه تست‌شده در DriveMate-AI: مسیر صوت به‌طور جداگانه و از طریق
      // فرمت سازگار با OpenAI (input_audio) قابل‌اطمینان‌تر است.
      raw = callChatCompletionAudio_(baseUrl, apiKey, model, body.prompt, body.audioBase64, body.audioMimeType);
    } else {
      return jsonResponse({ success: false, error: "action نامعتبر است." });
    }

    return jsonResponse({ success: true, raw: raw });
  } catch (err) {
    return jsonResponse({ success: false, error: String(err && err.message || err) });
  }
}

/** برای بررسی زنده‌بودن آدرس از داخل مرورگر؛ تحلیل واقعی فقط با POST انجام می‌شود. */
function doGet() {
  return jsonResponse({ success: true, service: "smart-mechanic-ai-proxy" });
}

/**
 * فراخوانی endpoint بومی Gemini از طریق AvalAI برای متن/عکس/ویدئو.
 * (این همان endpoint اصلی generateContent گوگل است؛ AvalAI فقط آن را proxy می‌کند،
 * پس تصویر و ویدئو دقیقاً مثل تماس مستقیم با Gemini کار می‌کنند.)
 */
function callGeminiNative_(baseUrl, apiKey, model, promptText, mediaBase64, mediaMimeType) {
  var parts = [{ text: promptText }];
  if (mediaBase64) {
    parts.push({ inlineData: { mimeType: mediaMimeType, data: mediaBase64 } });
  }

  var payload = {
    contents: [{ role: "user", parts: parts }],
    systemInstruction: { parts: [{ text: DIAGNOSIS_SYSTEM_PROMPT }] },
    generationConfig: {
      temperature: 0.3,
      maxOutputTokens: 2048,
      responseMimeType: "application/json"
    }
  };

  var options = {
    method: "post",
    contentType: "application/json",
    headers: { "x-goog-api-key": apiKey },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  };

  var endpoint = baseUrl + "/v1beta/models/" + encodeURIComponent(model) + ":generateContent";
  var response = UrlFetchApp.fetch(endpoint, options);
  if (response.getResponseCode() >= 300) {
    throw new Error("AvalAI HTTP " + response.getResponseCode() + ": " + response.getContentText().slice(0, 500));
  }

  var parsed = JSON.parse(response.getContentText());
  var candidateParts = (((parsed.candidates || [])[0] || {}).content || {}).parts || [];
  var text = candidateParts.map(function (p) { return p.text || ""; }).join("");
  text = String(text).trim();
  if (!text) throw new Error("پاسخ تحلیلی از مدل دریافت نشد.");
  return text;
}

/** فراخوانی chat/completions سازگار با OpenAI برای تحلیل صدا (فرمت input_audio). */
function callChatCompletionAudio_(baseUrl, apiKey, model, promptText, audioBase64, audioMimeType) {
  var format = audioFormat_(audioMimeType);
  var payload = {
    model: model,
    messages: [
      { role: "system", content: DIAGNOSIS_SYSTEM_PROMPT },
      {
        role: "user",
        content: [
          { type: "text", text: promptText },
          { type: "input_audio", input_audio: { data: audioBase64, format: format } }
        ]
      }
    ],
    temperature: 0.3,
    max_tokens: 2048
  };

  var options = {
    method: "post",
    contentType: "application/json",
    headers: { Authorization: "Bearer " + apiKey },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  };

  var response = UrlFetchApp.fetch(baseUrl + "/v1/chat/completions", options);
  if (response.getResponseCode() >= 300) {
    throw new Error("AvalAI HTTP " + response.getResponseCode() + ": " + response.getContentText().slice(0, 500));
  }

  var parsed = JSON.parse(response.getContentText());
  var text = String((((parsed.choices || [])[0] || {}).message || {}).content || "").trim();
  if (!text) throw new Error("پاسخ تحلیلی از مدل دریافت نشد.");
  return text;
}

/** تبدیل mimeType اندروید به فرمت کوتاهی که input_audio انتظار دارد. */
function audioFormat_(mimeType) {
  var subtype = String(mimeType || "audio/mp4").toLowerCase().split(";")[0].split("/")[1] || "mp4";
  if (subtype === "mpeg" || subtype === "mpga") return "mp3";
  // MediaRecorder اندروید یک جریان AAC داخل قالب MPEG-4 می‌سازد؛ نام رایج تبادل آن m4a است.
  if (subtype === "mp4" || subtype === "x-m4a") return "m4a";
  return subtype;
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

/** برای تست دستی سریع از داخل ویرایشگر Apps Script (Run → testDoPost). */
function testDoPost() {
  var fakeEvent = {
    postData: {
      contents: JSON.stringify({
        action: "text",
        prompt: "مشخصات خودرو: سایپا تیبا 1395\nتوضیح مشکل: صدای تق‌تق از موتور می‌آید."
      })
    }
  };
  Logger.log(doPost(fakeEvent).getContent());
}
