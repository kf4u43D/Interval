package dev.intervaltablet.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import dev.intervaltablet.ui.theme.IntervalTabletTheme

/** Debug-only host exposing the Android layout boundary around the Compose deck. */
class ToneRowDeckTestActivity : ComponentActivity() {
    lateinit var remainingStage: View
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toneRowState.value = ToneRowUiState(available = true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val deck = ComposeView(this).apply {
            setContent {
                IntervalTabletTheme {
                    ToneRowDeck(
                        state = toneRowState.value,
                        performanceLock = false,
                        compact = true,
                        onIntent = {},
                        onOpenArrangement = {},
                    )
                }
            }
        }
        root.addView(
            deck,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        remainingStage = View(this)
        root.addView(
            remainingStage,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        setContentView(root)
    }

    companion object {
        val toneRowState: MutableState<ToneRowUiState> = mutableStateOf(ToneRowUiState(available = true))
    }
}
