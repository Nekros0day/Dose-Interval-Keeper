package com.dosekeeper.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.dosekeeper.app.notifications.DoseNotificationManager
import com.dosekeeper.app.ui.DoseKeeperApp
import com.dosekeeper.app.ui.DoseKeeperTheme
import com.dosekeeper.app.ui.DoseKeeperViewModel
import java.util.concurrent.Executor

class MainActivity : FragmentActivity() {
    private val viewModel: DoseKeeperViewModel by viewModels()
    private lateinit var executor: Executor

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        DoseNotificationManager.scheduleAll(this, com.dosekeeper.app.data.DoseRepository(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        executor = ContextCompat.getMainExecutor(this)
        DoseNotificationManager.ensureChannels(this)
        requestNotificationsIfNeeded()

        setContent {
            DoseKeeperTheme {
                DoseKeeperApp(
                    viewModel = viewModel,
                    onRequestNotificationPermission = ::requestNotificationsIfNeeded,
                    onAuthenticateNotes = ::authenticateForNotes,
                )
            }
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun authenticateForNotes(itemId: String) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.unlockNotes(itemId)
            return
        }

        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.unlockNotes(itemId)
                }
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock medication notes")
                .setSubtitle("Notes stay encrypted on this device.")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }
}
