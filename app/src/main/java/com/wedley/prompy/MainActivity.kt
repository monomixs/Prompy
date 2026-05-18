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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import android.graphics.BitmapFactory
import android.util.Base64 as AndroidBase64

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // This handles the permission request response
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("Storage permissions are required for some features. Please enable them in settings.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Permissions required for some features", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showAllFilesAccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("All Files Access Required")
            .setMessage("This app requires access to all files for full functionality. Please enable it in the next screen.")
            .setPositiveButton("Enable") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "All files access is required", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // This handles the file picker when the HTML asks for an upload
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

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        
        val root = findViewById<LinearLayout>(R.id.main_root)
        root.setBackgroundColor(Color.WHITE)
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
        // 2. Enable JS and File Access
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
        }

        // Keeps clicked links inside the app instead of opening Chrome
        webView.webViewClient = WebViewClient()

        // 3. Handle File Uploads (when HTML has <input type="file">)
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

        // 4. Custom Long Press Pop-up for Images
        webView.setOnLongClickListener {
            val hitTestResult = webView.hitTestResult
            // Check if what we clicked is an image
            if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

                val imageUrl = hitTestResult.extra ?: return@setOnLongClickListener false
                showCustomImageDialog(imageUrl)
                return@setOnLongClickListener true // Tells Android we handled the long press
            }
            false
        }

        // Load your local site
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun showCustomImageDialog(imageUrl: String) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 200).toInt()
            ).apply {
                setPadding(40, 40, 40, 0)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            
            if (imageUrl.startsWith("data:image")) {
                try {
                    val base64Data = imageUrl.substringAfter(",")
                    val imageBytes = AndroidBase64.decode(base64Data, AndroidBase64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    setImageBitmap(bitmap)
                } catch (e: Exception) {
                    setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else if (imageUrl.startsWith("file:///android_asset/")) {
                // For assets, we'd need to load from AssetManager, but usually extras are full URLs or data
                setImageResource(android.R.drawable.ic_menu_gallery)
            } else {
                // For remote URLs, we'd ideally use Glide/Coil, but we'll stick to basic for now
                // or just show a placeholder if we don't have a loader
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(imageView)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Image Options")
            .setView(container)
            .setPositiveButton("Download") { _, _ -> downloadImage(imageUrl) }
            .setNeutralButton("Copy URL") { _, _ -> copyToClipboard(imageUrl) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Image URL", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "URL Copied!", Toast.LENGTH_SHORT).show()
    }

    private fun downloadImage(url: String) {
        if (url.startsWith("data:image")) {
            saveBase64Image(url)
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to download", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBase64Image(dataUri: String) {
        try {
            val base64Data = dataUri.substringAfter(",")
            val imageBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Base64.getDecoder().decode(base64Data)
            } else {
                android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            }
            
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            FileOutputStream(file).use { it.write(imageBytes) }

            // Scan file to make it appear in gallery
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null) { _, _ -> }
            
            Toast.makeText(this, "Image saved to Downloads", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

}
