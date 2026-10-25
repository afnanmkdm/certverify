package com.example.certverify

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    suspend fun verify(payload: String): VerifyResult = withContext(Dispatchers.IO) {
        if (payload.trim().startsWith("{")) {
            try {
                val json = JSONObject(payload)
                val subject = json.optJSONObject("credentialSubject")
                val candidate = subject?.optString("issuedTo")
                val course = subject?.optString("course")
                val issuedOn = subject?.optString("completedOn") ?: json.optString("issuanceDate")
                val proof = json.optJSONObject("proof")
                val status = if (proof != null) "Cryptographically Signed" else "Unsigned"

                val details = CertDetails(candidate, course, issuedOn, status)
                return@withContext VerifyResult(payload, "json_vc", details, payload)
            } catch (e: Exception) {
                Log.e("Verify", "JSON parse error", e)
            }
        }
        VerifyResult(payload, "unknown", null, payload)
    }
}