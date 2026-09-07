# قوانین پایه ProGuard/R8 برای Smart Mechanic AI
-keepattributes Signature
-keepattributes *Annotation*

# مدل‌های Gson/Retrofit — حفظ فیلدها برای serialize/deserialize صحیح JSON
-keep class com.smartmechanic.ai.data.remote.** { *; }
-keep class com.smartmechanic.ai.data.model.** { *; }

# Room entities
-keep class com.smartmechanic.ai.data.local.entities.** { *; }
