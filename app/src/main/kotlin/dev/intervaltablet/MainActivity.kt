package dev.intervaltablet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.intervaltablet.ui.IntervalTabletApp
import dev.intervaltablet.ui.theme.IntervalTabletTheme

class MainActivity : ComponentActivity() {
    private val viewModel: IntervalTabletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            IntervalTabletTheme {
                IntervalTabletApp(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onHostStart()
    }

    override fun onStop() {
        viewModel.onHostStop()
        super.onStop()
    }
}
