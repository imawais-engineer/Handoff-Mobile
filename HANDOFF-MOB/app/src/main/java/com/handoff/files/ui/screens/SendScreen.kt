package com.handoff.files.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handoff.files.domain.model.TransferFile
import com.handoff.files.domain.model.TransferStatus
import com.handoff.files.protocol.ProtocolConstants
import com.handoff.files.transport.NetworkResolver
import com.handoff.files.ui.TransferUiState
import com.handoff.files.ui.theme.*

@Composable
fun SendScreen(
    uiState: TransferUiState,
    onFilesSelected: (List<TransferFile>) -> Unit,
    onStartSend: (host: String, port: Int, qrToken: String?, manualPin: String?) -> Unit,
    onCancelTransfer: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var receiverIp by remember { mutableStateOf("") }
    var receiverPort by remember { mutableStateOf("8943") }
    var manualPin by remember { mutableStateOf("") }
    var qrToken by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // SAF Document Picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "unknown"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val selected = TransferFile(
                uriString = uri.toString(),
                name = fileName,
                sizeBytes = fileSize,
                mimeType = mimeType
            )
            onFilesSelected(listOf(selected))
        }
    }

    val selectedFile = uiState.selectedFiles.firstOrNull()
    val isTransferring = uiState.activeProgress.status in listOf(
        TransferStatus.CONNECTING,
        TransferStatus.PAIRING,
        TransferStatus.AWAITING_APPROVAL,
        TransferStatus.TRANSFERRING,
        TransferStatus.VERIFYING
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Files", fontWeight = FontWeight.Bold) },
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
            // 1. Storage Access Framework File Picker
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Select File",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedFile == null) {
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose from Storage (SAF)")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedFile.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${selectedFile.formattedSize} · ${selectedFile.mimeType}",
                                    fontSize = 12.sp,
                                    color = Slate400
                                )
                            }
                            TextButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                                Text("Change")
                            }
                        }

                        // Size Limit Check
                        if (selectedFile.isOversized) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SemanticRedBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = SemanticRed)
                                    Text(
                                        text = "File exceeds 10 MiB limit (${selectedFile.formattedSize}). Cannot send.",
                                        color = SemanticRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Peer Pairing Details (IP & Port & PIN)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. Receiver Connection",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Enter the IP, Port, and 6-digit PIN shown on your Windows PC or peer device.",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = receiverIp,
                        onValueChange = { receiverIp = it.trim() },
                        label = { Text("Receiver IP Address (e.g. 192.168.1.50)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = receiverPort,
                            onValueChange = { receiverPort = it.trim() },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = manualPin,
                            onValueChange = { if (it.length <= 6) manualPin = it.trim() },
                            label = { Text("6-digit PIN") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    if (validationError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = validationError ?: "",
                            color = SemanticRed,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 3. Transfer Control & Progress Card
            if (isTransferring) {
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
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.activeProgress.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = PrimaryBlue,
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
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onCancelTransfer,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SemanticRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel Transfer")
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        validationError = null
                        val portInt = receiverPort.toIntOrNull() ?: 0
                        if (!NetworkResolver.validatePeerEndpoint(receiverIp, portInt)) {
                            validationError = "Please enter a valid local IPv4 address and port (1024-65535)."
                            return@Button
                        }
                        if (manualPin.length != 6) {
                            validationError = "Please enter the 6-digit PIN displayed on the receiver."
                            return@Button
                        }
                        if (selectedFile == null) {
                            validationError = "Please select a file to send."
                            return@Button
                        }
                        if (selectedFile.isOversized) {
                            validationError = "File exceeds the 10 MiB limit."
                            return@Button
                        }

                        onStartSend(receiverIp, portInt, qrToken, manualPin)
                    },
                    enabled = selectedFile != null && !selectedFile.isOversized,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect and Transfer", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
