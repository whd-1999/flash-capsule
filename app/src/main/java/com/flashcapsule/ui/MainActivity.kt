package com.flashcapsule.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashcapsule.FlashCapsuleApp
import com.flashcapsule.overlay.OverlayService
import com.flashcapsule.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = FlashCapsuleApp.from(this)
        setContent {
            AppTheme {
                val context = LocalContext.current
                val vm: InboxViewModel = viewModel(
                    factory = InboxViewModel.Factory(app.repository, app.settings)
                )
                var overlayOn by remember {
                    mutableStateOf(
                        app.settings.overlayEnabled &&
                            android.provider.Settings.canDrawOverlays(context)
                    )
                }
                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    if (android.provider.Settings.canDrawOverlays(context)) {
                        OverlayService.start(context)
                        app.settings.overlayEnabled = true
                        overlayOn = true
                    }
                }
                InboxScreen(
                    vm = vm,
                    onCapture = { startActivity(Intent(this, CaptureActivity::class.java)) },
                    onVoiceCapture = {
                        startActivity(
                            Intent(this, CaptureActivity::class.java)
                                .putExtra(CaptureActivity.EXTRA_VOICE, true)
                        )
                    },
                    overlayOn = overlayOn,
                    onToggleOverlay = {
                        if (overlayOn) {
                            OverlayService.stop(context)
                            app.settings.overlayEnabled = false
                            overlayOn = false
                        } else if (android.provider.Settings.canDrawOverlays(context)) {
                            OverlayService.start(context)
                            app.settings.overlayEnabled = true
                            overlayOn = true
                        } else {
                            permLauncher.launch(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                    },
                )
            }
        }
    }
}
