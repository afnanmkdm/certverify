package com.example.certverify

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { VerifyScreen() } }
    }
}

@Composable
fun VerifyScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<VerifyResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        var name = "file"
        ctx.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) name = c.getString(0) }

        busy = true; error = null; result = null
        scope.launch {
            try {
                val payload = Decoder.extractQr(ctx, uri, name)
                if (payload == null) {
                    error = "No QR code found in this file."
                } else {
                    result = Verifier.verify(payload)
                }
            } catch (e: Exception) {
                error = e.message ?: "Unexpected error"
            } finally {
                busy = false
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Verify Certificate", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    picker.launch(arrayOf(
                        "image/*",
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Pick PDF / Image / DOCX") }

            Spacer(Modifier.height(16.dp))

            when {
                busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning…")
                }
                error != null -> Text("❌ $error", color = MaterialTheme.colorScheme.error)
                result != null -> {
                    val r = result!!
                    Text("✅ Result (${r.payloadType})")
                    Spacer(Modifier.height(8.dp))
                    Text("Payload: ${r.payload}", style = MaterialTheme.typography.bodySmall)
                    r.details?.let { d ->
                        Text("Candidate: ${d.candidate ?: "—"}")
                        Text("Course: ${d.course ?: "—"}")
                        Text("Issued: ${d.issuedOn ?: "—"}")
                        Text("Status: ${d.status ?: "—"}")
                    }
                    r.rawXml?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("Raw XML (first 800 chars):",
                            style = MaterialTheme.typography.labelMedium)
                        Text(it.take(800), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}