package io.github.waltoncomonline.samsungmimh02

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.waltoncomonline.samsungmimh02.ui.PairingScreen
import io.github.waltoncomonline.samsungmimh02.ui.PairingViewModel
import io.github.waltoncomonline.samsungmimh02.ui.ScheduleViewModel
import io.github.waltoncomonline.samsungmimh02.ui.SamsungMimH02App
import io.github.waltoncomonline.samsungmimh02.ui.theme.SamsungMimH02Theme

class MainActivity : FragmentActivity() {
    private val pairingViewModel by viewModels<PairingViewModel>()
    private val scheduleViewModel by viewModels<ScheduleViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SamsungMimH02Theme {
                SamsungMimH02App(
                    pairingViewModel = pairingViewModel,
                    scheduleViewModel = scheduleViewModel,
                    onRevealToken = ::authenticateForTokenReveal,
                )
            }
        }
    }

    private fun authenticateForTokenReveal() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    pairingViewModel.revealToken()
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Reveal AC token")
            .setSubtitle("Confirm your identity to view the saved token")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(promptInfo)
    }
}