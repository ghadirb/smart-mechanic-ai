package com.smartmechanic.ai.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Creates a stable anonymous Firebase identity for each installation.
 *
 * The identity, not a client-side secret, will authorize calls to the credit
 * backend. Firebase keeps the same anonymous user across normal app restarts.
 */
object FirebaseSession {
    private const val TAG = "FirebaseSession"

    fun ensureSignedIn() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return

        auth.signInAnonymously().addOnFailureListener { error ->
            // Do not surface a technical Firebase message to the user here;
            // diagnosis requests will display the existing friendly error UI.
            Log.w(TAG, "Anonymous Firebase sign-in failed", error)
        }
    }

    /**
     * برمی‌گرداند: یک ID Token معتبر Firebase برای هدر Authorization درخواست‌های
     * بک‌اند اعتباری، یا null اگر ورود ناشناس (anonymous sign-in) با شکست مواجه شود.
     *
     * اگر onCreate هنوز ورود را کامل نکرده باشد (مثلاً اولین تشخیص بلافاصله بعد
     * از باز شدن اپ اجرا شود)، این تابع منتظر می‌ماند تا ورود کامل شود، به‌جای
     * اینکه فرض کند currentUser از قبل موجود است.
     */
    suspend fun currentIdToken(): String? {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: signInAndAwait(auth) ?: return null
        return awaitIdToken(user)
    }

    private suspend fun signInAndAwait(auth: FirebaseAuth) = suspendCancellableCoroutine { continuation ->
        auth.signInAnonymously()
            .addOnSuccessListener { result -> continuation.resume(result.user) }
            .addOnFailureListener { error ->
                Log.w(TAG, "Anonymous Firebase sign-in failed", error)
                continuation.resume(null)
            }
    }

    private suspend fun awaitIdToken(user: com.google.firebase.auth.FirebaseUser) =
        suspendCancellableCoroutine<String?> { continuation ->
            user.getIdToken(false)
                .addOnSuccessListener { result -> continuation.resume(result.token) }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Fetching Firebase ID token failed", error)
                    continuation.resume(null)
                }
        }
}
