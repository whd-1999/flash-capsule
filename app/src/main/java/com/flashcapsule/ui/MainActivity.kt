package com.flashcapsule.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flashcapsule.FlashCapsuleApp
import com.flashcapsule.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = FlashCapsuleApp.from(this)
        setContent {
            AppTheme {
                val vm: InboxViewModel = viewModel(
                    factory = InboxViewModel.Factory(app.repository, app.settings)
                )
                InboxScreen(
                    vm = vm,
                    onCapture = { startActivity(Intent(this, CaptureActivity::class.java)) },
                )
            }
        }
    }
}
