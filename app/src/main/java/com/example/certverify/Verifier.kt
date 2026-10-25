package com.example.certverify

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

data class CertDetails(
    val candidate: String? = null,
    val course: String? = null,
    val issuedOn: String? = null,
    val status: String? = null
)

data class VerifyResult(
    val payload: String,
    val payloadType: String,   // "url" | "unknown"
    val details: CertDetails?,
    val rawXml: String?
)

object Verifier {

    private val http = OkHttpClient()

    suspend fun verify(payload: String): VerifyResult = withContext(Dispatchers.IO) {
        if (payload.startsWith("http://", true) || payload.startsWith("https://", true)) {
            val req = Request.Builder().url(payload).build()
            val xml = http.newCall(req).execute().use { it.body?.string().orEmpty() }
            val details = parseXml(xml)
            VerifyResult(payload, "url", details, xml)
        } else {
            // Not a URL — cannot verify locally without a backend DB.
            VerifyResult(payload, "unknown", null, null)
        }
    }

    private fun parseXml(xml: String): CertDetails {
        var candidate: String? = null
        var course: String? = null
        var issued: String? = null
        var status: String? = null

        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                // ⚠️ Adjust these tag names after scanning one REAL certificate
                when (parser.name) {
                    "CandidateName" -> candidate = parser.nextText()
                    "CourseName"    -> course = parser.nextText()
                    "IssueDate"     -> issued = parser.nextText()
                    "Status"        -> status = parser.nextText()
                }
            }
            event = parser.next()
        }
        return CertDetails(candidate, course, issued, status)
    }
}