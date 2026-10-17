package com.handoff.files.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handoff.files.domain.model.TransferDirection
import com.handoff.files.domain.model.TransferStatus
import com.handoff.files.ui.TransferUiState
import com.handoff.files.ui.theme.*

@Composable
fun HomeScreen(
    uiState: TransferUiState,
    onNavigateToSend: () -> Unit,
    onNavigateToReceive: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Handoff Files",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (uiState.localIp != null) "Ready on ${uiState.localIp}" else "Offline · Connect to Wi-Fi",
                        fontSize = 13.sp,
                        color = if (uiState.localIp != null) SemanticGreen else Slate400
                    )
                }

                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Offline Status Notice
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.localIp != null) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (uiState.localIp != null) SemanticGreen else SemanticAmber
                        )
                        Column {
                            Text(
                                text = if (uiState.localIp != null) "Local Wi-Fi Network Active" else "No Local Wi-Fi Detected",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (uiState.localIp != null)
                                    "Both devices must be on this Wi-Fi network or mobile hotspot."
                                else
                                    "Turn on your portable hotspot or join your Windows PC's Wi-Fi network.",
                                fontSize = 12.sp,
                                color = Slate400
                            )
                        }
                    }
                }
            }

            // Primary Action Buttons (Send / Receive)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Send Card
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigateToSend() },
                        color = PrimaryBlue,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "Send Files",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Max 10 MiB per file",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Receive Card
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigateToReceive() },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "Receive Files",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Show QR & 6-digit PIN",
                                    fontSize = 12.sp,
                                    color = Slate400
                                )
                            }
                        }
                    }
                }
            }

            // Active Transfer Status (if transferring or recently finished)
            if (uiState.activeProgress.status != TransferStatus.IDLE) {
                item {
                    Text(
                        text = "Active Transfer",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                                    text = uiState.activeProgress.filename.ifEmpty { "Transferring..." },
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${uiState.activeProgress.progressPercent}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = PrimaryBlue
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { uiState.activeProgress.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = PrimaryBlue,
                                trackColor = Slate700
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = String.format("%.1f MiB/s", uiState.activeProgress.speedMibPerSec),
                                    fontSize = 12.sp,
                                    color = Slate400
                                )
                                Text(
                                    text = uiState.activeProgress.status.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (uiState.activeProgress.status) {
                                        TransferStatus.COMPLETED -> SemanticGreen
                                        TransferStatus.FAILED -> SemanticRed
                                        else -> PrimaryBlue
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Recent Transfer History Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transfers",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onNavigateToHistory) {
                        Text(text = "View All", color = PrimaryBlue)
                    }
                }
            }

            if (uiState.historyItems.isEmpty()) {
                item {
                    Text(
                        text = "No transfers yet. Connect with your Windows PC to begin.",
                        fontSize = 13.sp,
                        color = Slate400,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(uiState.historyItems.take(5)) { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.direction == TransferDirection.SEND) Icons.Default.NorthEast else Icons.Default.SouthWest,
                                    contentDescription = null,
                                    tint = if (item.direction == TransferDirection.SEND) PrimaryBlue else SemanticGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = item.filename,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${item.formattedSize} · ${item.outcome}",
                                        fontSize = 12.sp,
                                        color = Slate400
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
