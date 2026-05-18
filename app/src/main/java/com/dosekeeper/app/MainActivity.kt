package com.dosekeeper.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dosekeeper.app.data.DoseRepository
import com.dosekeeper.app.notifications.DoseNotificationManager
import com.dosekeeper.app.ui.DoseKeeperApp
import com.dosekeeper.app.ui.DoseKeeperTheme
import com.dosekeeper.app.ui.DoseKeeperViewModel
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class MainActivity : ComponentActivity() {
    private val viewModel: DoseKeeperViewModel by viewModels()
    private val notificationsAllowed = mutableStateOf(false)
    private var quizInterstitialAd: InterstitialAd? = null
    private var loadingQuizInterstitial = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationsAllowed.value = hasNotificationPermission()
        DoseNotificationManager.scheduleAll(this, DoseRepository(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this)
        loadQuizInterstitial()
        DoseNotificationManager.ensureChannels(this)
        notificationsAllowed.value = hasNotificationPermission()
        requestNotificationsIfNeeded()

        setContent {
            DoseKeeperTheme {
                DoseKeeperApp(
                    viewModel = viewModel,
                    notificationsAllowed = notificationsAllowed.value,
                    onRequestNotificationPermission = ::requestNotificationsIfNeeded,
                    onQuizCompleted = ::showQuizInterstitial,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationsAllowed.value = hasNotificationPermission()
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission()
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun loadQuizInterstitial(showWhenReady: Boolean = false) {
        if (loadingQuizInterstitial || quizInterstitialAd != null) return
        loadingQuizInterstitial = true
        InterstitialAd.load(
            this,
            QUIZ_INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingQuizInterstitial = false
                    quizInterstitialAd = ad
                    if (showWhenReady) showQuizInterstitial()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingQuizInterstitial = false
                    quizInterstitialAd = null
                }
            },
        )
    }

    private fun showQuizInterstitial() {
        val ad = quizInterstitialAd
        if (ad == null) {
            loadQuizInterstitial(showWhenReady = true)
            return
        }

        quizInterstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadQuizInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                loadQuizInterstitial()
            }
        }
        ad.show(this)
    }

    private companion object {
        private const val QUIZ_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
}
