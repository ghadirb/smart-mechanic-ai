/**
 * مکانیک هوشمند AI — Backend واسط روی Google Apps Script
 * ---------------------------------------------------------
 * این اسکریپت به‌عنوان یک Web App رایگان عمل می‌کند که کلید gapgpt.app را
 * سمت سرور نگه می‌دارد و هرگز آن را به کلاینت (اپلیکیشن اندروید) نمی‌فرستد.
 * اپ اندروید فقط به این آدرس Web App درخواست می‌فرستد، نه مستقیماً به gapgpt.app.
 *
 * نصب:
 * 1) https://script.google.com → پروژه جدید بسازید → این فایل را در Code.gs پیست کنید.
 * 2) از منوی چرخ‌دنده (Project Settings) → Script Properties → یک Property با نام
 *    GAPGPT_API_KEY و مقدار کلید gapgpt.app خودتان اضافه کنید. (هرگز کلید را
 *    مستقیم داخل کد ننویسید.)
 * 3) Deploy → New deployment → نوع: Web app
 *      - Execute as: Me
 *      - Who has access: Anyone
 * 4) آدرس Web app (چیزی شبیه .../exec) را کپی کنید و در local.properties برنامه
 *    اندروید به‌عنوان PROXY_URL قرار دهید.
 *
 * توجه امنیتی: چون Access روی "Anyone" است، هر کسی که آدرس را داشته باشد می‌تواند
 * از سهمیه کلید gapgpt شما استفاده کند. برای محدودسازی بیشتر می‌توانید یک رمز
 * ساده (APP_SECRET) در Script Properties تعریف و از کلاینت در هر درخواست بفرستید
 * (کد زیر از این الگو پشتیبانی می‌کند — به APP_SECRET_CHECK مراجعه کنید).
 */

var GAPGPT_BASE_URL = "https://api.gapgpt.app/v1";

var DIAGNOSIS_SYSTEM_PROMPT = [
  "تو یک دستیار هوشمند عیب‌یابی خودرو هستی، نه تعمیرکار قطعی.",
  "وظیفه تو تحلیل اطلاعات ارائه‌شده توسط کاربر شامل مشخصات خودرو،",
  "توضیح علائم، تصویر یا صدا و ارائه احتمالات منطقی است.",
  "",
  "قوانین:",
  "1. هرگز بدون شواهد کافی خرابی قطعی اعلام نکن.",
  "2. بین مشاهده، احتمال و تشخیص قطعی تفاوت بگذار.",
  "3. اگر داده کافی نیست، حداکثر چند سؤال تکمیلی مهم بپرس.",
  "4. برای موارد ایمنی (ترمز، فرمان، سوخت، برق فشار بالا، داغ‌کردن شدید، دود شدید،",
  "   آتش‌سوزی، نشتی شدید) احتیاط را در اولویت مطلق قرار بده.",
  "5. پاسخ را به زبان فارسی ساده ارائه کن.",
  "6. اگر کیفیت تصویر/صدا کافی نیست، این موضوع را صراحتاً اعلام کن.",
  "7. هیچ درصد احتمال ساختگی تولید نکن؛ از «محتمل‌تر»، «احتمال متوسط»، «نیازمند بررسی» استفاده کن.",
  "8. خروجی را فقط و فقط به‌صورت یک شیء JSON زیر و بدون هیچ متن اضافه برگردان:",
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
    var apiKey = PropertiesService.getScriptProperties().getProperty("GAPGPT_API_KEY");
    if (!apiKey) {
      return jsonResponse({ success: false, error: "GAPGPT_API_KEY در Script Properties تنظیم نشده است." });
    }

    // بررسی اختیاری رمز اپ (در صورت تعریف APP_SECRET در Script Properties)
    var appSecret = PropertiesService.getScriptProperties().getProperty("APP_SECRET");
    if (appSecret && body.appSecret !== appSecret) {
      return jsonResponse({ success: false, error: "دسترسی غیرمجاز." });
    }

    var action = body.action;
    var rawModelText;

    if (action === "text") {
      rawModelText = callChatCompletion(apiKey, body.prompt, null, null);
    } else if (action === "image") {
      rawModelText = callChatCompletion(apiKey, body.prompt, body.imageBase64, body.imageMimeType);
    } else if (action === "audio") {
      var transcript = callWhisperTranscription(apiKey, body.audioBase64, body.audioMimeType);
      var combinedPrompt = body.prompt + "\n\nمتن پیاده‌سازی‌شده از صدای ضبط‌شده کاربر:\n" + transcript;
      rawModelText = callChatCompletion(apiKey, combinedPrompt, null, null);
    } else {
      return jsonResponse({ success: false, error: "action نامعتبر است." });
    }

    return jsonResponse({ success: true, raw: rawModelText });
  } catch (err) {
    return jsonResponse({ success: false, error: String(err) });
  }
}

/**
 * فراخوانی chat/completions با پشتیبانی اختیاری از یک تصویر (فرمت سازگار با OpenAI Vision).
 * توجه: پشتیبانی از تصویر برای مدل gemini-3.6-flash از طریق gapgpt به‌صورت رسمی در
 * مستندات موجود ذکر نشده است؛ این بخش به‌صورت best-effort پیاده شده — اگر مدل تصویر را
 * نپذیرفت، action="image" را موقتاً غیرفعال کرده و فقط از "text"/"audio" استفاده کنید.
 */
function callChatCompletion(apiKey, promptText, imageBase64, imageMimeType) {
  var userContent;
  if (imageBase64) {
    userContent = [
      { type: "text", text: promptText },
      { type: "image_url", image_url: { url: "data:" + (imageMimeType || "image/jpeg") + ";base64," + imageBase64 } }
    ];
  } else {
    userContent = promptText;
  }

  var payload = {
    model: "gemini-3.6-flash",
    messages: [
      { role: "system", content: DIAGNOSIS_SYSTEM_PROMPT },
      { role: "user", content: userContent }
    ]
  };

  var options = {
    method: "post",
    contentType: "application/json",
    headers: { Authorization: "Bearer " + apiKey },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  };

  var response = UrlFetchApp.fetch(GAPGPT_BASE_URL + "/chat/completions", options);
  var parsed = JSON.parse(response.getContentText());
  if (parsed.error) {
    throw new Error("gapgpt error: " + JSON.stringify(parsed.error));
  }
  return parsed.choices[0].message.content;
}

/** ارسال فایل صوتی به whisper-1 برای تبدیل به متن (STT). */
function callWhisperTranscription(apiKey, audioBase64, audioMimeType) {
  var bytes = Utilities.base64Decode(audioBase64);
  var blob = Utilities.newBlob(bytes, audioMimeType || "audio/mp4", "audio.m4a");

  var payload = {
    model: "whisper-1",
    file: blob
  };

  var options = {
    method: "post",
    headers: { Authorization: "Bearer " + apiKey },
    payload: payload,
    muteHttpExceptions: true
  };

  var response = UrlFetchApp.fetch(GAPGPT_BASE_URL + "/audio/transcriptions", options);
  var parsed = JSON.parse(response.getContentText());
  if (parsed.error) {
    throw new Error("gapgpt whisper error: " + JSON.stringify(parsed.error));
  }
  return parsed.text;
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
