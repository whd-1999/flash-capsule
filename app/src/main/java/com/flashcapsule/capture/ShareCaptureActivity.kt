package com.flashcapsule.capture

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.flashcapsule.FlashCapsuleApp
import kotlinx.coroutines.launch

/** 无界面：接住系统分享的内容，落库后即退出。 */
class ShareCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = ShareCaptureSource.parse(intent)
        if (raw == null) {
            finish()
            return
        }
        val repo = FlashCapsuleApp.from(this).repository
        lifecycleScope.launch {
            repo.ingest(raw)
            Toast.makeText(this@ShareCaptureActivity, "已存入闪念胶囊", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
