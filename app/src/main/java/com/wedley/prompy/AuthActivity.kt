package com.wedley.prompy

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class AuthActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private var failureCount = 0
    private lateinit var authStatus: TextView
    private lateinit var btnRetryFingerprint: Button
    private lateinit var pinContainer: LinearLayout
    private lateinit var keypadGrid: GridLayout
    private var currentOnSurfaceColor = Color.BLACK
    
    private var pinBuffer = ""
    private var storedPin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPrefs = getSharedPreferences("prompy_settings", MODE_PRIVATE)
        val theme = sharedPrefs.getString("theme", "light")
        val isBiometricEnabled = sharedPrefs.getBoolean("biometric_enabled", false) // Default OFF
        storedPin = sharedPrefs.getString("app_pin", null)

        setupSystemBars(theme)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        applyThemeToUI(theme)

        authStatus = findViewById(R.id.auth_status)
        btnRetryFingerprint = findViewById(R.id.btn_retry_fingerprint)
        pinContainer = findViewById(R.id.pin_container)
        keypadGrid = findViewById(R.id.keypad_grid)

        executor = ContextCompat.getMainExecutor(this)
        setupBiometricPrompt()

        btnRetryFingerprint.setOnClickListener {
            resetToInitialState()
            showBiometricPrompt()
        }

        buildKeypad()

        if (isBiometricEnabled) {
            showBiometricPrompt()
        } else {
            onAuthSuccess()
        }
    }

    private fun buildKeypad() {
        val keys = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "DEL")
        val density = resources.displayMetrics.density
        val btnSize = (64 * density).toInt()
        val margin = (8 * density).toInt()

        for (key in keys) {
            if (key.isEmpty()) {
                val space = View(this)
                val params = GridLayout.LayoutParams()
                params.width = btnSize
                params.height = btnSize
                params.setMargins(margin, margin, margin, margin)
                keypadGrid.addView(space, params)
                continue
            }

            val btn = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
                text = key
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                insetTop = 0
                insetBottom = 0
                cornerRadius = (16 * density).toInt()
                
                if (key == "DEL") {
                    setIconResource(android.R.drawable.ic_input_delete)
                    iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                    iconPadding = 0
                    text = ""
                }
            }

            val params = GridLayout.LayoutParams()
            params.width = btnSize
            params.height = btnSize
            params.setMargins(margin, margin, margin, margin)
            
            btn.setOnClickListener { onKeyClick(key) }
            keypadGrid.addView(btn, params)
        }
    }

    private fun onKeyClick(key: String) {
        if (key == "DEL") {
            if (pinBuffer.isNotEmpty()) {
                pinBuffer = pinBuffer.dropLast(1)
            }
        } else if (pinBuffer.length < 4) {
            pinBuffer += key
        }

        updatePinDots()

        if (pinBuffer.length == 4) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (pinBuffer == storedPin) {
                    onAuthSuccess()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    pinBuffer = ""
                    updatePinDots()
                }
            }, 100)
        }
    }

    private fun updatePinDots() {
        val dots = arrayOf(
            findViewById<View>(R.id.dot1),
            findViewById<View>(R.id.dot2),
            findViewById<View>(R.id.dot3),
            findViewById<View>(R.id.dot4)
        )
        for (i in dots.indices) {
            dots[i].setBackgroundResource(if (i < pinBuffer.length) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty)
        }
    }

    private fun resetToInitialState() {
        authStatus.text = "Please use fingerprint to continue"
        authStatus.setTextColor(currentOnSurfaceColor)
        findViewById<ImageView>(R.id.auth_icon).apply {
            setImageResource(android.R.drawable.ic_lock_idle_lock)
            imageTintList = android.content.res.ColorStateList.valueOf(currentOnSurfaceColor)
        }
    }

    private fun setupBiometricPrompt() {
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    authStatus.text = "Authentication error: $errString"
                    handleFailure()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    authStatus.text = "Fingerprint not recognized"
                    handleFailure()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Required")
            .setSubtitle("Authenticate to access Prompy")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                showPinEntry()
            }
        }
    }

    private fun handleFailure() {
        failureCount++
        if (failureCount >= 2) {
            showPinEntry()
        } else {
            btnRetryFingerprint.visibility = View.VISIBLE
        }
    }

    private fun showPinEntry() {
        authStatus.text = "Security Fallback"
        btnRetryFingerprint.visibility = View.GONE
        pinContainer.visibility = View.VISIBLE
        findViewById<ImageView>(R.id.auth_icon).setImageResource(android.R.drawable.ic_lock_lock)
    }

    private fun onAuthSuccess() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setupSystemBars(theme: String?) {
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
        enableEdgeToEdge(statusBarStyle = statusStyle, navigationBarStyle = navStyle)
    }

    private fun applyThemeToUI(theme: String?) {
        val surfaceColor: Int
        val onSurfaceColor: Int

        when (theme) {
            "dark" -> {
                surfaceColor = Color.parseColor("#1C1B1F")
                onSurfaceColor = Color.parseColor("#E6E1E5")
            }
            "amoled" -> {
                surfaceColor = Color.BLACK
                onSurfaceColor = Color.parseColor("#E6E1E5")
            }
            else -> {
                surfaceColor = Color.WHITE
                onSurfaceColor = Color.BLACK
            }
        }
        
        currentOnSurfaceColor = onSurfaceColor
        findViewById<View>(R.id.auth_root)?.setBackgroundColor(surfaceColor)
        findViewById<TextView>(R.id.auth_title)?.setTextColor(onSurfaceColor)
        findViewById<TextView>(R.id.auth_status)?.setTextColor(onSurfaceColor)
        findViewById<ImageView>(R.id.auth_icon)?.imageTintList = android.content.res.ColorStateList.valueOf(onSurfaceColor)
        findViewById<TextView>(R.id.pin_label)?.setTextColor(onSurfaceColor)
    }
}
