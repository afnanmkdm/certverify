package com.example.certverify

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

val PrimaryBlue = Color(0xFF1565C0)
val BackgroundColor = Color(0xFFF5F7FA)
val SuccessGreen = Color(0xFF2E7D32)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = PrimaryBlue,
                    background = BackgroundColor,
                    surface = Color.White
                )
            ) {
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
    var errorMessage by remember { mutableStateOf<String?>(null) } // NEW: Error state
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "btnScale")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundColor, Color.White))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(100.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("CertVerify Pro", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryBlue)
            Text("Professional Certificate Scanner", fontSize = 16.sp, color = Color.Gray, textAlign = TextAlign.Center)
            
            Spacer(modifier = Modifier.height(56.dp))

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null // Reset error
                    scope.launch {
                        try {
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
                            // THIS WILL SHOW THE ERROR ON SCREEN
                            errorMessage = e.localizedMessage ?: "Error: ${e.message}"
                            e.printStackTrace()
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(scale),
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Sign in with Google", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            // NEW: Display Error Message if login fails
            Spacer(modifier = Modifier.height(16.dp))
            errorMessage?.let {
                Text(
                    text = "⚠️ $it",
                    color = Color.Red,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
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
    var isUploadPressed by remember { mutableStateOf(false) }
    val uploadScale by animateFloatAsState(if (isUploadPressed) 0.95f else 1f, label = "uploadScale")

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
                    error = "No QR code found in the document."
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
                title = { Text("Scanner", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue),
                actions = {
                    IconButton(onClick = { 
                        FirebaseAuth.getInstance().signOut()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                    }
                }
            )
        },
        containerColor = BackgroundColor
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(20.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Upload Certificate", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Spacer(Modifier.height(8.dp))
                    Text("Select a PDF, Image, or DOCX file", fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(20.dp))
                    
                    Button(
                        onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                        modifier = Modifier.fillMaxWidth().height(54.dp).scale(uploadScale),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose File", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (busy) {
                CircularProgressIndicator(color = PrimaryBlue)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Scanning Document...", color = Color.Gray)
            }

            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(error ?: "", color = Color.Red, fontWeight = FontWeight.Medium)
                    }
                }
            }

            AnimatedVisibility(
                visible = scannedDetails != null,
                enter = fadeIn(tween(500)) + expandVertically(tween(500)),
                exit = fadeOut() + shrinkVertically()
            ) {
                scannedDetails?.let { details ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Extracted Data", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.DarkGray)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            InfoRow("Name", details.candidate)
                            InfoRow("Course", details.course)
                            InfoRow("Issued", details.issuedOn)
                            InfoRow("Status", details.status)

                            Spacer(modifier = Modifier.height(24.dp))

                            if (!isSubmitted) {
                                Button(
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            val success = Backend.submitToDatabase(details, rawPayload)
                                            busy = false
                                            if (success) isSubmitted = true else error = "Submission failed. Check internet."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                ) { 
                                    Text("Submit to Database", fontSize = 16.sp, fontWeight = FontWeight.Bold) 
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Saved & Sent to XML!", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("$label", fontWeight = FontWeight.Medium, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value ?: "—", fontWeight = FontWeight.SemiBold, color = Color.Black, modifier = Modifier.weight(1.5f))
    }
}