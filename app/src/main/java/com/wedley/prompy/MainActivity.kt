package com.wedley.prompy

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.text.InputType
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.lingala.zip4j.model.enums.AesKeyStrength
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import android.graphics.BitmapFactory
import android.util.Base64 as AndroidBase64
import androidx.biometric.BiometricPrompt
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            showSettingsDialog()
        }
    }

    private fun showPrompyDialog(
        title: String,
        message: String? = null,
        customView: android.view.View? = null,
        confirmText: String = "Confirm",
        cancelText: String? = "Cancel",
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_prompy, null)
        val titleTv = dialogView.findViewById<android.widget.TextView>(R.id.dialog_title)
        val messageTv = dialogView.findViewById<android.widget.TextView>(R.id.dialog_message)
        val contentFrame = dialogView.findViewById<android.widget.FrameLayout>(R.id.dialog_custom_content)
        val confirmBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialog_confirm_btn)
        val cancelBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialog_cancel_btn)

        titleTv.text = title
        if (message != null) {
            messageTv.text = message
            messageTv.visibility = android.view.View.VISIBLE
        } else {
            messageTv.visibility = android.view.View.GONE
        }

        if (customView != null) {
            contentFrame.addView(customView)
            contentFrame.visibility = android.view.View.VISIBLE
        } else {
            contentFrame.visibility = android.view.View.GONE
        }

        confirmBtn.text = confirmText
        if (cancelText != null) {
            cancelBtn.text = cancelText
            cancelBtn.visibility = android.view.View.VISIBLE
        } else {
            cancelBtn.visibility = android.view.View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(this, R.style.Prompy_Dialog)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        confirmBtn.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        cancelBtn.setOnClickListener {
            onCancel?.invoke()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSettingsDialog() {
        showPrompyDialog(
            title = "Permissions Required",
            message = "Storage permissions are required for some features. Please enable them in settings.",
            confirmText = "Settings",
            onConfirm = {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            },
            onCancel = {
                Toast.makeText(this, "Permissions required for some features", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showAllFilesAccessDialog() {
        showPrompyDialog(
            title = "All Files Access Required",
            message = "This app requires access to all files for full functionality. Please enable it in the next screen.",
            confirmText = "Enable",
            onConfirm = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
            }
        )
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val uri = data?.data
            val results = if (uri != null) arrayOf(uri) else null
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private var importCallback: ValueCallback<String>? = null
    private var pendingImportUri: Uri? = null

    private val zipPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                pendingImportUri = uri
                checkZipSecurity(uri)
            }
        } else {
            importCallback?.onReceiveValue(null)
            importCallback = null
            pendingImportUri = null
        }
    }

    private fun checkZipSecurity(uri: Uri) {
        try {
            val tempFile = File(cacheDir, "import_temp.zip")
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val zipFile = net.lingala.zip4j.ZipFile(tempFile)
            if (zipFile.isEncrypted) {
                showPasswordInputDialog(tempFile)
            } else {
                val jsonResult = handleZip4jImport(tempFile, null)
                importCallback?.onReceiveValue(jsonResult)
                importCallback = null
                tempFile.delete()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read ZIP: ${e.message}", Toast.LENGTH_SHORT).show()
            importCallback?.onReceiveValue(null)
            importCallback = null
        }
    }

    private fun showPasswordInputDialog(zipFile: File) {
        val sharedPrefs = getSharedPreferences("prompy_settings", MODE_PRIVATE)
        val onSurfaceColor = if (sharedPrefs.getString("theme", "light") == "light") Color.BLACK else Color.WHITE

        val til = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = "Enter ZIP Password"
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(16f, 16f, 16f, 16f)
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        
        val input = com.google.android.material.textfield.TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        til.addView(input)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val margin = (24 * resources.displayMetrics.density).toInt()
            setPadding(margin, (8 * resources.displayMetrics.density).toInt(), margin, 0)
            addView(til)
        }

        showPrompyDialog(
            title = "Password Protected",
            message = "This backup is encrypted. Please enter the password to restore prompts.",
            customView = container,
            confirmText = "Unlock",
            onConfirm = {
                val password = input.text.toString()
                val jsonResult = handleZip4jImport(zipFile, password)
                if (jsonResult == "[]" || jsonResult == "null") {
                    Toast.makeText(this, "Incorrect password or invalid ZIP", Toast.LENGTH_SHORT).show()
                    importCallback?.onReceiveValue(null)
                } else {
                    importCallback?.onReceiveValue(jsonResult)
                }
                importCallback = null
                zipFile.delete()
            },
            onCancel = {
                importCallback?.onReceiveValue(null)
                importCallback = null
                zipFile.delete()
            }
        )
    }

    private fun handleZip4jImport(file: File, password: String?): String {
        val prompts = JSONArray()
        try {
            val zipFile = if (password != null) {
                net.lingala.zip4j.ZipFile(file, password.toCharArray())
            } else {
                net.lingala.zip4j.ZipFile(file)
            }

            if (!zipFile.isValidZipFile) return "[]"

            val folderDataMap = mutableMapOf<String, JSONObject>()
            val folderImageMap = mutableMapOf<String, ByteArray>()

            for (fileHeader in zipFile.fileHeaders) {
                if (!fileHeader.isDirectory) {
                    val pathParts = fileHeader.fileName.split("/")
                    if (pathParts.size >= 2) {
                        val folderName = pathParts[pathParts.size - 2]
                        val fileName = pathParts.last()

                        if (fileName == "prompt_info.txt") {
                            zipFile.getInputStream(fileHeader).use { it.bufferedReader().readText() }.let {
                                folderDataMap[folderName] = JSONObject(it)
                            }
                        } else if (fileName == "image.png") {
                            zipFile.getInputStream(fileHeader).use { it.readBytes() }.let {
                                folderImageMap[folderName] = it
                            }
                        }
                    }
                }
            }

            for ((folder, data) in folderDataMap) {
                val imageBytes = folderImageMap[folder]
                if (imageBytes != null) {
                    val base64Image = "data:image/png;base64," + AndroidBase64.encodeToString(imageBytes, AndroidBase64.NO_WRAP)
                    data.put("image", base64Image)
                }
                prompts.put(data)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "[]"
        }
        return prompts.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPrefs = getSharedPreferences("prompy_settings", MODE_PRIVATE)
        val theme = sharedPrefs.getString("theme", "light")
        applyThemeBars(theme)

        val allowScreenshots = sharedPrefs.getBoolean("allow_screenshots", false)
        if (!allowScreenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        
        val root = findViewById<LinearLayout>(R.id.main_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        webView = findViewById(R.id.webView)

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        setupWebView()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showAllFilesAccessDialog()
            }
        } else {
            val permissionsToRequest = mutableListOf<String>()

            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (permissionsToRequest.isNotEmpty()) {
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        webView.webViewClient = WebViewClient()

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                filePickerLauncher.launch(intent)
                return true
            }
        }

        webView.setOnLongClickListener {
            val hitTestResult = webView.hitTestResult
            if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

                val imageUrl = hitTestResult.extra ?: return@setOnLongClickListener false
                
                val script = """
                    (function() {
                        var imgs = document.getElementsByTagName('img');
                        for (var i = 0; i < imgs.length; i++) {
                            if (imgs[i].src === '${imageUrl.replace("'", "\\'")}') {
                                var card = imgs[i].closest('.prompt-card');
                                if (card) return card.querySelector('.card-title').innerText;
                                var view = imgs[i].closest('#screen-view');
                                if (view) return view.querySelector('.view-title').innerText;
                            }
                        }
                        return null;
                    })()
                """.trimIndent()

                webView.evaluateJavascript(script) { result ->
                    val title = result?.trim('"')?.replace("\\\"", "\"")?.takeIf { it != "null" && it.isNotBlank() }
                    showCustomImageDialog(imageUrl, title)
                }
                return@setOnLongClickListener true
            }
            false
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun showCustomImageDialog(imageUrl: String, promptTitle: String? = null) {
        val imageView = com.google.android.material.imageview.ShapeableImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 240).toInt()
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true

            val radius = resources.displayMetrics.density * 16
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(radius)
                .build()
            
            if (imageUrl.startsWith("data:image")) {
                try {
                    val base64Data = imageUrl.substringAfter(",")
                    val imageBytes = AndroidBase64.decode(base64Data, AndroidBase64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    setImageBitmap(bitmap)
                } catch (e: Exception) {
                    setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }

        showPrompyDialog(
            title = "Image Options",
            customView = imageView,
            confirmText = "Download",
            onConfirm = { downloadImage(imageUrl, promptTitle) },
            onCancel = { }
        )
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Image URL", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "URL Copied!", Toast.LENGTH_SHORT).show()
    }

    private fun downloadImage(url: String, suggestedName: String? = null) {
        if (url.startsWith("data:image")) {
            saveBase64Image(url, suggestedName)
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            val cleanName = suggestedName?.replace("[^a-zA-Z0-9.-]".toRegex(), "_") ?: "image_${System.currentTimeMillis()}"
            val fileName = "$cleanName.jpg"
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Prompy/$fileName")

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(this, "Downloading to Prompy...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to download", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBase64Image(dataUri: String, suggestedName: String? = null) {
        try {
            val extension = when {
                dataUri.startsWith("data:image/png") -> "png"
                dataUri.startsWith("data:image/webp") -> "webp"
                dataUri.startsWith("data:image/gif") -> "gif"
                else -> "jpg"
            }

            val base64Data = dataUri.substringAfter(",")
            val imageBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Base64.getDecoder().decode(base64Data)
            } else {
                android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            }
            
            val cleanName = suggestedName?.replace("[^a-zA-Z0-9.-]".toRegex(), "_") ?: "image_${System.currentTimeMillis()}"
            val fileName = "$cleanName.$extension"
            
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val prompyDir = File(downloadsDir, "Prompy")
            if (!prompyDir.exists()) prompyDir.mkdirs()
            val file = File(prompyDir, fileName)

            FileOutputStream(file).use { it.write(imageBytes) }

            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null) { _, _ -> }
            
            Toast.makeText(this, "Image saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyThemeBars(theme: String?) {
        val (statusStyle, navStyle) = when (theme) {
            "dark" -> {
                val darkColor = Color.parseColor("#1C1B1F")
                SystemBarStyle.dark(darkColor) to SystemBarStyle.dark(darkColor)
            }
            "amoled" -> {
                SystemBarStyle.dark(Color.BLACK) to SystemBarStyle.dark(Color.BLACK)
            }
            else -> {
                SystemBarStyle.light(Color.WHITE, Color.WHITE) to SystemBarStyle.light(Color.WHITE, Color.WHITE)
            }
        }
        runOnUiThread {
            enableEdgeToEdge(statusBarStyle = statusStyle, navigationBarStyle = navStyle)
        }
    }

    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun setBiometricEnabled(enabled: Boolean) {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("biometric_enabled", enabled).apply()
        }

        @JavascriptInterface
        fun isBiometricEnabled(): Boolean {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            return sharedPrefs.getBoolean("biometric_enabled", false)
        }

        @JavascriptInterface
        fun setVaultBiometricEnabled(enabled: Boolean) {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("vault_biometric_enabled", enabled).apply()
        }

        @JavascriptInterface
        fun isVaultBiometricEnabled(): Boolean {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            return sharedPrefs.getBoolean("vault_biometric_enabled", false)
        }

        @JavascriptInterface
        fun canAuthenticate(): Boolean {
            val biometricManager = androidx.biometric.BiometricManager.from(mContext)
            return biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        }

        @JavascriptInterface
        fun setAppPin(pin: String?) {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("app_pin", pin).apply()
        }

        @JavascriptInterface
        fun setVaultPin(pin: String?) {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("vault_pin", pin).apply()
        }

        @JavascriptInterface
        fun setAppLockEnabled(enabled: Boolean) {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("app_lock_enabled", enabled).apply()
        }

        @JavascriptInterface
        fun isAppLockEnabled(): Boolean {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            return sharedPrefs.getBoolean("app_lock_enabled", true)
        }

        @JavascriptInterface
        fun setAllowScreenshots(enabled: Boolean) {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("allow_screenshots", enabled).apply()
        }

        @JavascriptInterface
        fun isAllowScreenshotsEnabled(): Boolean {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            return sharedPrefs.getBoolean("allow_screenshots", false)
        }

        @JavascriptInterface
        fun updateTheme(theme: String) {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("theme", theme).apply()
            applyThemeBars(theme)
        }

        @JavascriptInterface
        fun updateStatusBarColor(colorHex: String?) {
            val sharedPrefs = mContext.getSharedPreferences("prompy_settings", Context.MODE_PRIVATE)
            val theme = sharedPrefs.getString("theme", "light")
            
            runOnUiThread {
                if (colorHex != null) {
                    try {
                        val color = Color.parseColor(colorHex)
                        val navColor = when (theme) {
                            "dark" -> Color.parseColor("#1C1B1F")
                            "amoled" -> Color.BLACK
                            else -> Color.WHITE
                        }
                        
                        val isDark = ColorUtils.calculateLuminance(color) < 0.5
                        val statusStyle = if (isDark) {
                            SystemBarStyle.dark(color)
                        } else {
                            SystemBarStyle.light(color, color)
                        }
                        
                        val isNavDark = ColorUtils.calculateLuminance(navColor) < 0.5
                        val navStyle = if (isNavDark) {
                            SystemBarStyle.dark(navColor)
                        } else {
                            SystemBarStyle.light(navColor, navColor)
                        }
                        
                        enableEdgeToEdge(
                            statusBarStyle = statusStyle,
                            navigationBarStyle = navStyle
                        )
                    } catch (e: Exception) {
                        applyThemeBars(theme)
                    }
                } else {
                    applyThemeBars(theme)
                }
            }
        }

        @JavascriptInterface
        fun exportPrompts(jsonStr: String, fileName: String, password: String? = null) {
            try {
                val prompts = JSONArray(jsonStr)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val prompyDir = File(downloadsDir, "Prompy")
                if (!prompyDir.exists()) prompyDir.mkdirs()
                
                val zipFile = File(prompyDir, fileName)
                if (zipFile.exists()) zipFile.delete()

                val zipFileObj = if (!password.isNullOrEmpty()) {
                    net.lingala.zip4j.ZipFile(zipFile, password.toCharArray())
                } else {
                    net.lingala.zip4j.ZipFile(zipFile)
                }

                zipFileObj.use { zfo ->
                    val zipParameters = ZipParameters().apply {
                        if (!password.isNullOrEmpty()) {
                            isEncryptFiles = true
                            encryptionMethod = EncryptionMethod.AES
                            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                        }
                    }

                    for (i in 0 until prompts.length()) {
                        val p = prompts.getJSONObject(i)
                        val title = p.optString("title", "Untitled").replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                        val folderName = "${title}_${i}/"

                        val info = JSONObject(p.toString())
                        info.remove("image")

                        val dataBytes = info.toString(2).toByteArray()
                        zipParameters.fileNameInZip = folderName + "prompt_info.txt"
                        zfo.addStream(ByteArrayInputStream(dataBytes), zipParameters)

                        val imgData = p.optString("image", "")
                        if (imgData.isNotEmpty() && imgData.contains(",")) {
                            val b64 = imgData.substringAfter(",")
                            val bytes = AndroidBase64.decode(b64, AndroidBase64.DEFAULT)
                            zipParameters.fileNameInZip = folderName + "image.png"
                            zfo.addStream(ByteArrayInputStream(bytes), zipParameters)
                        }
                    }
                }
                
                runOnUiThread {
                    MediaScannerConnection.scanFile(mContext, arrayOf(zipFile.absolutePath), null) { _, _ -> }
                    Toast.makeText(mContext, "Exported to Downloads/Prompy/$fileName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(mContext, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }

        @JavascriptInterface
        fun importPrompts(callbackName: String) {
            runOnUiThread {
                this@MainActivity.importCallback = ValueCallback { result ->
                    webView.evaluateJavascript("window['$callbackName']($result)", null)
                }
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/zip"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                this@MainActivity.zipPickerLauncher.launch(intent)
            }
        }

        @JavascriptInterface
        fun authenticateBiometric(callbackName: String) {
            runOnUiThread {
                val executor = ContextCompat.getMainExecutor(mContext)
                val biometricPrompt = BiometricPrompt(this@MainActivity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            webView.evaluateJavascript("window['$callbackName'](false, '$errString')", null)
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            webView.evaluateJavascript("window['$callbackName'](true)", null)
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            // This is called when a fingerprint is recognized but doesn't match
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Biometric Authentication")
                    .setSubtitle("Confirm your identity to continue")
                    .setNegativeButtonText("Use PIN")
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
        }

        @JavascriptInterface
        fun openExternalUrl(url: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                mContext.startActivity(intent)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(mContext, "Could not open browser", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}
