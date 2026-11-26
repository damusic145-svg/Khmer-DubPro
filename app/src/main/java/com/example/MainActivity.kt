package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.configureAppCheck
import com.example.ui.DubberViewModel
import com.example.ui.navigation.DubberAppShell
import com.example.ui.theme.KhmerDubberTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DubberViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ -> }

                LaunchedEffect(Unit) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            KhmerDubberTheme(themePreference = settings.themePreference) {
                DubberAppShell(viewModel = viewModel)
            }
        }
    }
}
