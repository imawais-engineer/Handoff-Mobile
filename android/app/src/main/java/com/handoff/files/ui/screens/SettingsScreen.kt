package com.handoff.files.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handoff.files.protocol.ProtocolConstants
import com.handoff.files.ui.TransferUiState
import com.handoff.files.ui.theme.*

@Composable
fun SettingsScreen(
    uiState: TransferUiState,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val diagnosticLogs = """
        [Handoff Diagnostics]
        App Version: 1.0.0
        Protocol Version: ${ProtocolConstants.PROTOCOL_VERSION}
        Transport: TCP Local Socket
        Max File Size: 10 MiB (${ProtocolConstants.DEFAULT_MAX_FILE_SIZE_BYTES} bytes)
        Chunk Size: 64 KiB (${ProtocolConstants.CHUNK_SIZE_BYTES} bytes)
        Cipher: AES-256-GCM (12B IV, 16B Tag)
        Key Agreement: ECDH (secp256r1) + HKDF-SHA256
        Local Network IP: ${uiState.localIp ?: "Unassigned / Offline"}
        Receiver Listening: ${uiState.isReceiverListening}
        Active Transfers: ${uiState.activeProgress.status.name}
    """.trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Transfer Limits
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Transfer Policy",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Maximum File Size", fontSize = 13.sp, color = Slate400)
                        Text("10 MiB (10,485,760 B)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Streaming Chunk Size", fontSize = 13.sp, color = Slate400)
                        Text("64 KiB", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pairing Code Expiry", fontSize = 13.sp, color = Slate400)
                        Text("120 seconds", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Sanitized Diagnostics
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sanitized Diagnostics",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "No secrets included",
                            fontSize = 11.sp,
                            color = SemanticGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = diagnosticLogs,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Handoff Diagnostics", diagnosticLogs)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Sanitized Diagnostics")
                    }
                }
            }
        }
    }
}
