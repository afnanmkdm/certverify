package com.example.certverify

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

object Backend {
    // ⚠️ PASTE YOUR TOKEN AND CHAT ID HERE (Inside the quotes)
    private const val TELEGRAM_TOKEN = "8748865028:AAEFLFpgdJPrd5VAOOuuwqvf19w6j5qXGQU" 
    private const val TELEGRAM_CHAT_ID = "7543350109" // <--- PUT THE NUMBER HERE

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val http = OkHttpClient()

    suspend fun submitToDatabase(details: CertDetails, payload: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
            val data = hashMapOf(
                "email" to (user?.email ?: "unknown"),
                "candidate" to details.candidate,
                "course" to details.course,
                "issuedOn" to details.issuedOn,
                "status" to details.status,
                "rawPayload" to payload,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("certificates").add(data).await()
            sendXmlToTelegram(details)
            true
        } catch (e: Exception) {
            Log.e("Backend", "Error submitting", e)
            false
        }
    }

    private fun sendXmlToTelegram(details: CertDetails) {
        val xmlString = """
            <Certificate>
                <CandidateName>${details.candidate}</CandidateName>
                <CourseName>${details.course}</CourseName>
                <IssueDate>${details.issuedOn}</IssueDate>
                <Status>${details.status}</Status>
            </Certificate>
        """.trimIndent()

        val fileBody = xmlString.toRequestBody("text/xml".toMediaTypeOrNull())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", TELEGRAM_CHAT_ID)
            .addFormDataPart("document", "certificate.xml", fileBody)
            .build()

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$TELEGRAM_TOKEN/sendDocument")
            .post(multipart)
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) Log.e("Telegram", "Failed")
        }
    }
}