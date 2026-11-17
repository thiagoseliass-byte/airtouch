package com.airtouch

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.airtouch.mouse.TouchInjector
import com.airtouch.ui.AirTouchScreen
import com.airtouch.ui.theme.AirTouchTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AirTouchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mantém a tela acesa enquanto o app está aberto (a câmera precisa ficar ativa).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AirTouchTheme {
                AirTouchScreen(viewModel, onFullscreen = ::setFullscreen)
            }
        }
        // Toques do mouse virtual são injetados na área de conteúdo do app.
        val content = findViewById<View>(android.R.id.content)
        viewModel.attachInjector(TouchInjector(content))
    }

    /** Tela cheia para apresentações: esconde as barras do sistema. */
    private fun setFullscreen(on: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onDestroy() {
        viewModel.detachInjector()
        super.onDestroy()
    }
}
