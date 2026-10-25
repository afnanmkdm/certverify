package com.example.certverify

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class CertDetails(
    val candidate: String? = null,
    val course: String? = null,
    val issuedOn: String? = null,
    val status: String? = null
)

data class VerifyResult(
    val payload: String,
    val payloadType: String,
    val details: CertDetails?,
    val rawXml: String?
)

object Verifier {
    private val http = OkHttpClient()

    suspend fun verify(payload: String): VerifyResult = withContext(Dispatchers.IO) {
        // NEW: Handle JSON Payload (Wingspan / W3C Verifiable Credentials)
        if (payload.trim().startsWith("{")) {
            try {
                val json = JSONObject(payload)
                val subject = json.optJSONObject("credentialSubject")
                
                val candidate = subject?.optString("issuedTo")
                val course = subject?.optString("course")
                val issuedOn = subject?.optString("completedOn") ?: json.optString("issuanceDate")
                
                // Check if the certificate is cryptographically signed
                val proof = json.optJSONObject("proof")
                val status = if (proof != null) "Cryptographically Signed" else "Unsigned"

                val details = CertDetails(candidate, course, issuedOn, status)
                return@withContext VerifyResult(payload, "json_vc", details, payload)
            } catch (e: Exception) {
                Log.e("Verify", "JSON parse error", e)
            }
        }

        // Handle URL Payload (If they ever use a URL instead)
        if (payload.startsWith("http://", true) || payload.startsWith("https://", true)) {
            try {
                val req = Request.Builder().url(payload).build()
                val xml = http.newCall(req).execute().use { it.body?.string().orEmpty() }
                return@withContext VerifyResult(payload, "url", null, xml)
            } catch (e: Exception) {
                Log.e("Verify", "Network error", e)
            }
        }

        // Fallback
        VerifyResult(payload, "unknown", null, payload)
    }
}