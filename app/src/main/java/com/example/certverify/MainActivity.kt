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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = FirebaseAuth.getInstance()
    var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
    } else {
        MainScreen(onLogout = { isLoggedIn = false })
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = CredentialManager.create(context)
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("CertVerify", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
        Text("Professional Certificate Scanner", fontSize = 16.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                isLoading = true
                scope.launch {
                    try {
                        // ⚠️ PASTE YOUR WEB CLIENT ID HERE
                        val webClientId = "430703422343-vhc6n4ho0grdre70i686oijp53r6a7ic.apps.googleusercontent.com" 
                        
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(webClientId)
                            .build()

                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()

                        val result = credentialManager.getCredential(context, request)
                        val credential = result.credential

                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                            onLoginSuccess()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("Sign in with Google", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    var scannedDetails by remember { mutableStateOf<CertDetails?>(null) }
    var rawPayload by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        var name = "file"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> 
            if (c.moveToFirst()) name = c.getString(0) 
        }

        busy = true; error = null; scannedDetails = null; isSubmitted = false
        scope.launch {
            try {
                val payload = Decoder.extractQr(ctx, uri, name)
                if (payload == null) {
                    error = "No QR code found."
                } else {
                    rawPayload = payload
                    val result = Verifier.verify(payload)
                    if (result.details != null) {
                        scannedDetails = result.details
                    } else {
                        error = "Unrecognized QR format."
                    }
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Certificate", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { 
                        FirebaseAuth.getInstance().signOut()
                        onLogout()
                    }) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Upload PDF or Image") }

            Spacer(Modifier.height(24.dp))

            if (busy) {
                CircularProgressIndicator()
                Text("Processing...", modifier = Modifier.padding(top = 8.dp))
            }

            error?.let {
                Text("❌ $it", color = MaterialTheme.colorScheme.error)
            }

            scannedDetails?.let { details ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Extracted Data", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        
                        InfoRow("Name", details.candidate)
                        InfoRow("Course", details.course)
                        InfoRow("Issued", details.issuedOn)
                        InfoRow("Status", details.status)

                        Spacer(Modifier.height(16.dp))

                        if (!isSubmitted) {
                            Button(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        val success = Backend.submitToDatabase(details, rawPayload)
                                        busy = false
                                        if (success) isSubmitted = true else error = "Submission failed"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Submit to Database") }
                        } else {
                            Text("✅ Successfully Saved & Sent to XML!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label:", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value ?: "—", modifier = Modifier.weight(1.5f))
    }
}
