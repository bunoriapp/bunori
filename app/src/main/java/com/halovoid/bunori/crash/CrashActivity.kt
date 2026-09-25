package com.halovoid.bunori.crash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.halovoid.bunori.MainActivity
import com.halovoid.bunori.data.repository.StorageRepositoryImpl
import com.halovoid.bunori.ui.core.theme.BunoriTheme
import com.halovoid.bunori.ui.feature.settings.CrashScreen

/*
Directly pulled over from tachiyomi source code
 */
class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val exception = GlobalExceptionHandler.getThrowableFromIntent(intent)
        val storageRepository = StorageRepositoryImpl.getInstance(applicationContext)

        setContent {
            BunoriTheme {
                CrashScreen(
                    exception = exception,
                    storageRepository = storageRepository,
                    onRestartClick = {
                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}
