package com.handoff.files.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handoff.files.domain.model.TransferFile
import com.handoff.files.domain.model.TransferStatus
import com.handoff.files.ui.TransferUiState
import com.handoff.files.ui.theme.*

@Composable
fun ReceiveScreen(
    uiState: TransferUiState,
    onStartReceive: () -> Unit,
    onStopReceive: () -> Unit,
    onStartWebTransfer: () -> Unit,
    onStopWebTransfer: () -> Unit,
    onApproveTransfer: (Boolean) -> Unit,
    onApproveWebTransfer: (Boolean) -> Unit,
    onCancelTransfer: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val pairing = uiState.pairingInfo
    val isListening = uiState.isReceiverListening
    val isWebActive = uiState.isWebTransferRunning
    val incomingProposal = uiState.incomingProposal
    val webPrompt = uiState.webTransferPrompt

    // 1. Native TCP Receiver Approval Dialog (FR-04, FR-06)
    if (incomingProposal != null) {
        AlertDialog(
            onDismissRequest = { /* Require explicit user action */ },
            title = {
                Text(
                    text = "Incoming File Transfer (Native)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "A native peer device wants to transfer a file directly to this device:",
                        fontSize = 14.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = incomingProposal.filename,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${TransferFile.formatBytes(incomingProposal.sizeBytes)} · ${incomingProposal.mimeType}",
                                fontSize = 13.sp,
                                color = Slate400
                            )
                        }
                    }
                    Text(
                        text = "Accept this transfer?",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onApproveTransfer(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Accept Transfer")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onApproveTransfer(false) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticRed)
                ) {
                    Text("Decline")
                }
            }
        )
    }

    // 2. Windows Browser Upload Approval Dialog
    if (webPrompt != null) {
        AlertDialog(
            onDismissRequest = { /* Require explicit user action */ },
            title = {
                Text(
                    text = "Windows Browser Upload Request",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "A Windows desktop web browser wants to transfer this file to your device:",
                        fontSize = 14.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = webPrompt.filename,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${TransferFile.formatBytes(webPrompt.sizeBytes)} · ${webPrompt.mimeType}",
                                fontSize = 13.sp,
                                color = Slate400
                            )
                        }
                    }
                    Text(
                        text = "Accept incoming file from Windows browser?",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onApproveWebTransfer(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Accept File")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onApproveWebTransfer(false) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticRed)
                ) {
                    Text("Decline")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Files", fontWeight = FontWeight.Bold) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Option A: Windows Browser Transfer Card (Zero install required on Windows)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Laptop,
                                contentDescription = null,
                                tint = if (isWebActive) SemanticGreen else PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Windows Browser Mode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "No Windows app installation required",
                                    fontSize = 12.sp,
                                    color = Slate400
                                )
                            }
                        }

                        if (!isWebActive) {
                            Button(
                                onClick = onStartWebTransfer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Start Web Mode")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onStopWebTransfer,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Stop")
                            }
                        }
                    }

                    if (isWebActive && uiState.webTransferUrl != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Open this URL in Chrome/Edge on your Windows PC:",
                                    fontSize = 12.sp,
                                    color = Slate400
                                )
                                Text(
                                    text = uiState.webTransferUrl ?: "",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = PrimaryBlue
                                )
                            }
                        }
                    }
                }
            }

            // Option B: Native TCP Socket Server
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Sensors else Icons.Default.SensorsOff,
                                contentDescription = null,
                                tint = if (isListening) SemanticGreen else Slate400,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Native TCP Peer Mode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (isListening) "Listening on port ${pairing?.port ?: 8943}" else "For native Android & Windows apps",
                                    fontSize = 12.sp,
                                    color = Slate400
                                )
                            }
                        }

                        if (!isListening) {
                            Button(
                                onClick = onStartReceive,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Start TCP Mode")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onStopReceive,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }

            // Active Pairing PIN Card (used by both Web mode & TCP mode)
            if ((isListening || isWebActive) && pairing != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "6-DIGIT PAIRING PIN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PrimaryBlue.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${pairing.sasPinCode.take(3)} ${pairing.sasPinCode.takeLast(3)}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enter this PIN to authenticate connection · Expires in 120s",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }
            }

            // Transfer Progress while receiving
            if (uiState.activeProgress.status != TransferStatus.IDLE) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = uiState.activeProgress.status.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (uiState.activeProgress.status == TransferStatus.COMPLETED) SemanticGreen else PrimaryBlue
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.activeProgress.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (uiState.activeProgress.status == TransferStatus.COMPLETED) SemanticGreen else PrimaryBlue,
                            trackColor = Slate700
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${uiState.activeProgress.progressPercent}% (${TransferFile.formatBytes(uiState.activeProgress.bytesTransferred)})",
                                fontSize = 12.sp,
                                color = Slate400
                            )
                            Text(
                                text = String.format("%.1f MiB/s", uiState.activeProgress.speedMibPerSec),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (uiState.activeProgress.status == TransferStatus.COMPLETED) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "File verified (SHA-256 matched) and saved to device storage.",
                                color = SemanticGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
