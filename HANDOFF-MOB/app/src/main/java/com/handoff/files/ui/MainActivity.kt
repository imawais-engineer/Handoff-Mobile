package com.handoff.files.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.handoff.files.ui.screens.*
import com.handoff.files.ui.theme.HandoffTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TransferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HandoffTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val uiState by viewModel.uiState.collectAsState()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                uiState = uiState,
                                onNavigateToSend = { navController.navigate("send") },
                                onNavigateToReceive = {
                                    viewModel.startReceiveMode()
                                    navController.navigate("receive")
                                },
                                onNavigateToHistory = { navController.navigate("history") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }

                        composable("send") {
                            SendScreen(
                                uiState = uiState,
                                onFilesSelected = { viewModel.onFilesSelected(it) },
                                onStartSend = { host, port, token, pin ->
                                    viewModel.startSend(host, port, token, pin)
                                },
                                onCancelTransfer = { viewModel.cancelTransfer() },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("receive") {
                            ReceiveScreen(
                                uiState = uiState,
                                onStartReceive = { viewModel.startReceiveMode() },
                                onStopReceive = { viewModel.stopReceiveMode() },
                                onStartWebTransfer = { viewModel.startWebTransferMode() },
                                onStopWebTransfer = { viewModel.stopWebTransferMode() },
                                onApproveTransfer = { viewModel.approveIncomingTransfer(it) },
                                onApproveWebTransfer = { viewModel.approveWebTransfer(it) },
                                onCancelTransfer = { viewModel.cancelTransfer() },
                                onNavigateBack = {
                                    viewModel.stopReceiveMode()
                                    viewModel.stopWebTransferMode()
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("history") {
                            HistoryScreen(
                                uiState = uiState,
                                onClearHistory = { viewModel.clearHistory() },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                uiState = uiState,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
