package com.wedley.prompy

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
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
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.Executor

class AuthActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private var failureCount = 0
    private lateinit var authStatus: TextView
    private lateinit var btnRetryFingerprint: Button
    private lateinit var pinContainer: LinearLayout
    private lateinit var pinInput: TextInputEditText
    private lateinit var btnSubmitPin: Button
    private var currentOnSurfaceColor = Color.BLACK

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPrefs = getSharedPreferences("prompy_settings", MODE_PRIVATE)
        val theme = sharedPrefs.getString("theme", "light")

        // 1. Setup system bars immediately to avoid flashing
        setupSystemBars(theme)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        // 2. Apply theme colors to UI components
        applyThemeToUI(theme)

        authStatus = findViewById(R.id.auth_status)
        btnRetryFingerprint = findViewById(R.id.btn_retry_fingerprint)
        pinContainer = findViewById(R.id.pin_container)
        pinInput = findViewById(R.id.pin_input)
        btnSubmitPin = findViewById(R.id.btn_submit_pin)

        executor = ContextCompat.getMainExecutor(this)
        setupBiometricPrompt()

        btnRetryFingerprint.setOnClickListener {
            resetToInitialState()
            showBiometricPrompt()
        }

        btnSubmitPin.setOnClickListener {
            if (pinInput.text.toString() == "0002") {
                onAuthSuccess()
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            }
        }

        // Check if biometric is enabled in settings
        val isBiometricEnabled = sharedPrefs.getBoolean("biometric_enabled", true)

        if (isBiometricEnabled) {
            // Start biometric prompt immediately
            showBiometricPrompt()
        } else {
            // If biometric is disabled, go straight to MainActivity
            onAuthSuccess()
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
                    // If user cancels or too many attempts via system dialog
                    authStatus.text = "Authentication failed: $errString"
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
            .setTitle("Biometric login for Prompy")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Cancel")
            .build()
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                // Biometrics not available, fallback to PIN if needed or just show PIN container
                showPinEntry()
            }
        }
    }

    private fun handleFailure() {
        failureCount++
        if (failureCount >= 2) {
            showPinEntry()
        } else {
            showFailureScreen()
        }
    }

    private fun showFailureScreen() {
        authStatus.text = "Authentication Failed"
        authStatus.setTextColor(Color.RED)
        btnRetryFingerprint.text = "Try Again"
        btnRetryFingerprint.visibility = View.VISIBLE
        findViewById<ImageView>(R.id.auth_icon).apply {
            setImageResource(android.R.drawable.stat_notify_error)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.RED)
        }
    }

    private fun showPinEntry() {
        authStatus.text = "Too Many Failures"
        authStatus.setTextColor(currentOnSurfaceColor)
        btnRetryFingerprint.visibility = View.GONE
        pinContainer.visibility = View.VISIBLE
        findViewById<ImageView>(R.id.auth_icon).apply {
            setImageResource(android.R.drawable.ic_lock_lock)
            imageTintList = android.content.res.ColorStateList.valueOf(currentOnSurfaceColor)
        }
        findViewById<TextView>(R.id.pin_label).setTextColor(currentOnSurfaceColor)
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

        findViewById<View>(R.id.auth_root)?.let { root ->
            root.setBackgroundColor(surfaceColor)
            findViewById<TextView>(R.id.auth_title)?.setTextColor(onSurfaceColor)
            findViewById<TextView>(R.id.auth_status)?.setTextColor(onSurfaceColor)
            findViewById<ImageView>(R.id.auth_icon)?.imageTintList = 
                android.content.res.ColorStateList.valueOf(onSurfaceColor)
            
            // PIN entry elements
            findViewById<TextView>(R.id.pin_label)?.setTextColor(onSurfaceColor)
            findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.pin_input_layout)?.let { til ->
                til.defaultHintTextColor = android.content.res.ColorStateList.valueOf(onSurfaceColor)
                til.hintTextColor = android.content.res.ColorStateList.valueOf(onSurfaceColor)
                til.setBoxStrokeColor(onSurfaceColor)
            }
            findViewById<TextInputEditText>(R.id.pin_input)?.setTextColor(onSurfaceColor)
        }
    }
}
