package com.handoff.files.transport

import com.handoff.files.domain.model.TransferFile
import com.handoff.files.domain.model.TransferProgress
import com.handoff.files.domain.model.TransferStatus
import com.handoff.files.protocol.ProtocolConstants
import com.handoff.files.security.CryptoEngine
import com.handoff.files.security.RateLimiter
import com.handoff.files.transfer.StagingFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID

/**
 * Embedded HTTP server hosted directly by the Android app for zero-install
 * Windows-to-Android transfers via standard desktop web browsers on the same local Wi-Fi / Hotspot.
 */
class WebTransferServer(
    private val stagingManager: StagingFileManager,
    private val onApprovalRequested: suspend (filename: String, sizeBytes: Long, mimeType: String) -> Boolean,
    private val onFileReceived: (File) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val rateLimiter = RateLimiter()
    private val scope = CoroutineScope(Dispatchers.IO)

    var currentPin: String = ""
        private set
    var currentToken: String = ""
        private set
    var isRunning: Boolean = false
        private set
    var localPort: Int = 8080
        private set

    // File queued by Android user to be downloaded by Windows browser
    var queuedDownloadFile: File? = null

    fun start(port: Int = 8080, pin: String, token: String, progressFlow: MutableStateFlow<TransferProgress>) {
        if (isRunning) stop()

        this.currentPin = pin
        this.currentToken = token
        this.localPort = port

        serverJob = scope.launch {
            try {
                val server = ServerSocket(port)
                serverSocket = server
                isRunning = true

                while (!server.isClosed) {
                    val clientSocket = server.accept()
                    scope.launch {
                        handleClient(clientSocket, progressFlow)
                    }
                }
            } catch (_: Exception) {
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        isRunning = false
    }

    private suspend fun handleClient(socket: Socket, progressFlow: MutableStateFlow<TransferProgress>) = withContext(Dispatchers.IO) {
        val remoteIp = socket.inetAddress?.hostAddress ?: "unknown"
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())

        try {
            // Read HTTP request line
            val requestLine = readLine(input) ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val path = parts[1]

            // Read Headers
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(":")
                if (colonIdx > 0) {
                    val k = line.substring(0, colonIdx).trim().lowercase()
                    val v = line.substring(colonIdx + 1).trim()
                    headers[k] = v
                }
            }

            // Route handling
            when {
                // 1. Root: Serve self-contained Windows web client
                method == "GET" && (path == "/" || path.startsWith("/index.html")) -> {
                    serveHtmlClient(output)
                }

                // 2. Pairing PIN validation
                method == "POST" && path == "/api/pair" -> {
                    if (rateLimiter.isLockedOut(remoteIp)) {
                        sendJsonResponse(output, 429, JSONObject().put("error", "Rate limit lockout. Try again in 30 seconds."))
                        return@withContext
                    }

                    val body = readBodyString(input, headers["content-length"]?.toIntOrNull() ?: 0)
                    val json = JSONObject(body)
                    val submittedPin = json.optString("pin", "")

                    if (submittedPin == currentPin) {
                        rateLimiter.recordSuccess(remoteIp)
                        sendJsonResponse(output, 200, JSONObject().put("status", "OK").put("token", currentToken))
                    } else {
                        val locked = rateLimiter.recordFailure(remoteIp)
                        val code = if (locked) 429 else 401
                        val msg = if (locked) "Too many failed attempts. Locked out for 30s." else "Invalid 6-digit PIN."
                        sendJsonResponse(output, code, JSONObject().put("error", msg))
                    }
                }

                // 3. File upload from Windows browser to Android
                method == "POST" && path == "/api/upload" -> {
                    handleBrowserUpload(input, output, headers, remoteIp, progressFlow)
                }

                // 4. File download from Android to Windows browser
                method == "GET" && path == "/api/download" -> {
                    handleBrowserDownload(output, headers)
                }

                // 5. Status / Metadata probe
                method == "GET" && path == "/api/status" -> {
                    val status = JSONObject().apply {
                        put("version", ProtocolConstants.PROTOCOL_VERSION)
                        put("maxSizeBytes", ProtocolConstants.DEFAULT_MAX_FILE_SIZE_BYTES)
                        put("hasDownloadPending", queuedDownloadFile != null)
                        queuedDownloadFile?.let {
                            put("downloadFilename", it.name)
                            put("downloadSizeBytes", it.length())
                        }
                    }
                    sendJsonResponse(output, 200, status)
                }

                else -> {
                    sendTextResponse(output, 404, "Not Found")
                }
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun handleBrowserUpload(
        input: InputStream,
        output: OutputStream,
        headers: Map<String, String>,
        remoteIp: String,
        progressFlow: MutableStateFlow<TransferProgress>
    ) {
        // Verify authentication token
        val authHeader = headers["x-session-token"] ?: headers["authorization"]?.removePrefix("Bearer ")?.trim()
        if (authHeader != currentToken) {
            sendJsonResponse(output, 401, JSONObject().put("error", "Unauthorized: pairing required"))
            return
        }

        val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
        if (contentLength <= 0 || contentLength > ProtocolConstants.DEFAULT_MAX_FILE_SIZE_BYTES) {
            sendJsonResponse(output, 400, JSONObject().put("error", "File exceeds 10 MiB limit or invalid Content-Length"))
            return
        }

        val rawName = headers["x-file-name"] ?: "windows_upload_${System.currentTimeMillis()}.dat"
        val filename = URLDecoder.decode(rawName, "UTF-8")
        val mimeType = headers["x-file-type"] ?: "application/octet-stream"
        val expectedSha256 = headers["x-file-sha256"]?.lowercase() ?: ""

        // Prompt Android user for explicit interactive approval
        val accepted = onApprovalRequested(filename, contentLength, mimeType)
        if (!accepted) {
            sendJsonResponse(output, 403, JSONObject().put("error", "Transfer declined by Android receiver"))
            return
        }

        // Stream into staging file
        val transferId = UUID.randomUUID().toString()
        val stagingFile = stagingManager.getStagingFile(transferId)

        progressFlow.value = TransferProgress(
            transferId = transferId,
            filename = filename,
            totalBytes = contentLength,
            status = TransferStatus.TRANSFERRING
        )

        var bytesReceived = 0L
        val startTime = System.currentTimeMillis()

        try {
            FileOutputStream(stagingFile).use { fileOut ->
                val buffer = ByteArray(ProtocolConstants.CHUNK_SIZE_BYTES)
                var remaining = contentLength

                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val count = input.read(buffer, 0, toRead)
                    if (count < 0) throw EOFException("Premature EOF during upload")

                    fileOut.write(buffer, 0, count)
                    bytesReceived += count
                    remaining -= count

                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val speed = (bytesReceived / 1024.0 / 1024.0) / (elapsed / 1000.0)

                    progressFlow.value = progressFlow.value.copy(
                        bytesTransferred = bytesReceived,
                        speedMibPerSec = speed,
                        elapsedSeconds = elapsed / 1000
                    )
                }
            }

            // Verify integrity and publish atomically
            progressFlow.value = progressFlow.value.copy(status = TransferStatus.VERIFYING)
            val published = stagingManager.verifyAndPublish(transferId, filename, expectedSha256)

            onFileReceived(published)
            progressFlow.value = progressFlow.value.copy(
                status = TransferStatus.COMPLETED,
                verified = true
            )

            sendJsonResponse(output, 200, JSONObject().put("status", "SUCCESS").put("savedAs", published.name))

        } catch (e: Exception) {
            stagingManager.cleanupStagingFile(transferId)
            progressFlow.value = progressFlow.value.copy(
                status = TransferStatus.FAILED,
                errorMessage = e.message ?: "Upload failed"
            )
            sendJsonResponse(output, 500, JSONObject().put("error", e.message ?: "Integrity or storage failure"))
        }
    }

    private fun handleBrowserDownload(output: OutputStream, headers: Map<String, String>) {
        val file = queuedDownloadFile
        if (file == null || !file.exists()) {
            sendTextResponse(output, 404, "No file queued for download")
            return
        }

        val authHeader = headers["x-session-token"] ?: headers["authorization"]?.removePrefix("Bearer ")?.trim()
        if (authHeader != currentToken) {
            sendJsonResponse(output, 401, JSONObject().put("error", "Unauthorized: pairing required"))
            return
        }

        val headerBuilder = StringBuilder()
        headerBuilder.append("HTTP/1.1 200 OK\r\n")
        headerBuilder.append("Content-Type: application/octet-stream\r\n")
        headerBuilder.append("Content-Length: ${file.length()}\r\n")
        headerBuilder.append("Content-Disposition: attachment; filename=\"${file.name}\"\r\n")
        headerBuilder.append("Connection: close\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
        FileInputStream(file).use { it.copyTo(output) }
        output.flush()
    }

    private fun serveHtmlClient(output: OutputStream) {
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Handoff Files - Windows Transfer</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0B0F19; color: #F8FAFC; margin: 0; padding: 30px; display: flex; justify-content: center; }
    .card { background: #0F172A; border: 1px solid #1E293B; border-radius: 16px; padding: 28px; max-width: 540px; width: 100%; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
    h1 { font-size: 20px; font-weight: 700; margin-top: 0; margin-bottom: 8px; color: #FFFFFF; }
    p { font-size: 13px; color: #94A3B8; margin-top: 0; line-height: 1.5; }
    .pin-box { display: flex; gap: 8px; margin: 16px 0; }
    input[type="text"] { background: #020617; border: 1px solid #334155; border-radius: 8px; padding: 10px 14px; color: #FFFFFF; font-family: monospace; font-size: 16px; letter-spacing: 2px; width: 100%; }
    button { background: #4F46E5; border: none; color: white; border-radius: 8px; padding: 10px 18px; font-size: 13px; font-weight: 600; cursor: pointer; transition: background 0.2s; }
    button:hover { background: #4338CA; }
    .drop-zone { border: 2px dashed #334155; border-radius: 12px; padding: 24px; text-align: center; margin: 20px 0; cursor: pointer; transition: border-color 0.2s; }
    .drop-zone:hover { border-color: #6366F1; }
    .progress-bar { background: #020617; border: 1px solid #1E293B; border-radius: 6px; height: 10px; overflow: hidden; margin: 14px 0 6px 0; }
    .progress-fill { background: #4F46E5; height: 100%; width: 0%; transition: width 0.15s; }
    .status-badge { font-size: 12px; padding: 6px 12px; border-radius: 6px; margin-top: 12px; display: none; }
    .status-success { background: rgba(16, 185, 129, 0.15); color: #10B981; border: 1px solid rgba(16, 185, 129, 0.3); }
    .status-error { background: rgba(239, 68, 68, 0.15); color: #EF4444; border: 1px solid rgba(239, 68, 68, 0.3); }
  </style>
</head>
<body>
  <div class="card">
    <h1>Handoff Files · Local Transfer</h1>
    <p>Direct local wireless transfer between Windows and Android without internet.</p>

    <div id="authSection">
      <p style="margin-bottom: 6px; font-weight: 600; color: #E2E8F0;">1. Enter 6-digit PIN shown on Android:</p>
      <div class="pin-box">
        <input type="text" id="pinInput" maxlength="6" placeholder="e.g. 492817" />
        <button onclick="pairDevice()">Verify</button>
      </div>
    </div>

    <div id="transferSection" style="display: none;">
      <p style="margin-bottom: 6px; font-weight: 600; color: #E2E8F0;">2. Send File to Android (Max 10 MiB):</p>
      <div class="drop-zone" onclick="document.getElementById('fileInput').click()">
        <span id="fileLabel">Click to choose file or drag & drop</span>
        <input type="file" id="fileInput" style="display: none;" onchange="fileSelected(this)" />
      </div>

      <button id="sendBtn" style="width: 100%;" disabled onclick="uploadFile()">Send to Android</button>

      <div class="progress-bar">
        <div class="progress-fill" id="progressFill"></div>
      </div>
      <div style="display: flex; justify-content: space-between; font-size: 11px; color: #64748B; font-family: monospace;">
        <span id="transferredBytes">0 / 0 B</span>
        <span id="transferSpeed">0.0 MiB/s</span>
      </div>
    </div>

    <div id="statusBadge" class="status-badge"></div>
  </div>

  <script>
    let sessionToken = null;
    let selectedFile = null;

    async function pairDevice() {
      const pin = document.getElementById('pinInput').value.trim();
      const res = await fetch('/api/pair', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pin })
      });
      const data = await res.json();
      if (res.ok) {
        sessionToken = data.token;
        document.getElementById('authSection').style.display = 'none';
        document.getElementById('transferSection').style.display = 'block';
        showStatus('Paired successfully with Android device!', false);
      } else {
        showStatus(data.error || 'Pairing failed', true);
      }
    }

    function fileSelected(input) {
      if (!input.files[0]) return;
      selectedFile = input.files[0];
      if (selectedFile.size > 10 * 1024 * 1024) {
        showStatus('File exceeds 10 MiB limit (' + (selectedFile.size / (1024*1024)).toFixed(2) + ' MiB)', true);
        document.getElementById('sendBtn').disabled = true;
        return;
      }
      document.getElementById('fileLabel').innerText = selectedFile.name + ' (' + (selectedFile.size / (1024*1024)).toFixed(2) + ' MiB)';
      document.getElementById('sendBtn').disabled = false;
    }

    async function computeSha256(file) {
      const buffer = await file.arrayBuffer();
      const hash = await crypto.subtle.digest('SHA-256', buffer);
      return Array.from(new Uint8Array(hash)).map(b => b.toString(16).padStart(2, '0')).join('');
    }

    async function uploadFile() {
      if (!selectedFile || !sessionToken) return;
      showStatus('Computing SHA-256 and waiting for Android receiver approval...', false);

      const sha256 = await computeSha256(selectedFile);
      const xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/upload', true);
      xhr.setRequestHeader('X-Session-Token', sessionToken);
      xhr.setRequestHeader('X-File-Name', encodeURIComponent(selectedFile.name));
      xhr.setRequestHeader('X-File-Size', selectedFile.size.toString());
      xhr.setRequestHeader('X-File-Type', selectedFile.type || 'application/octet-stream');
      xhr.setRequestHeader('X-File-Sha256', sha256);

      const startTime = performance.now();
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const pct = Math.round((e.loaded / e.total) * 100);
          document.getElementById('progressFill').style.width = pct + '%';
          const elapsed = (performance.now() - startTime) / 1000;
          const speed = (e.loaded / (1024 * 1024)) / (elapsed || 0.001);
          document.getElementById('transferredBytes').innerText = (e.loaded / (1024*1024)).toFixed(2) + ' / ' + (e.total / (1024*1024)).toFixed(2) + ' MiB (' + pct + '%)';
          document.getElementById('transferSpeed').innerText = speed.toFixed(2) + ' MiB/s';
        }
      };

      xhr.onload = () => {
        if (xhr.status === 200) {
          showStatus('Transfer complete! File verified (SHA-256 matched) and saved on Android.', false);
        } else {
          try {
            const err = JSON.parse(xhr.responseText);
            showStatus('Transfer failed: ' + (err.error || xhr.statusText), true);
          } catch(e) {
            showStatus('Transfer failed: ' + xhr.statusText, true);
          }
        }
      };

      xhr.send(selectedFile);
    }

    function showStatus(text, isError) {
      const badge = document.getElementById('statusBadge');
      badge.innerText = text;
      badge.style.display = 'block';
      badge.className = 'status-badge ' + (isError ? 'status-error' : 'status-success');
    }
  </script>
</body>
</html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val statusText = if (statusCode == 200) "OK" else if (statusCode == 401) "Unauthorized" else if (statusCode == 429) "Too Many Requests" else "Bad Request"
        val header = "HTTP/1.1 $statusCode $statusText\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: ${bytes.size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendTextResponse(output: OutputStream, statusCode: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $statusCode Text\r\nContent-Type: text/plain\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c == -1) {
                return if (baos.size() > 0) baos.toString("UTF-8") else null
            }
            if (c == '\n'.code) break
            if (c != '\r'.code) baos.write(c)
        }
        return baos.toString("UTF-8")
    }

    private fun readBodyString(input: InputStream, length: Int): String {
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val count = input.read(bytes, totalRead, length - totalRead)
            if (count < 0) break
            totalRead += count
        }
        return String(bytes, 0, totalRead, Charsets.UTF_8)
    }
}
