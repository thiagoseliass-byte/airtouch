package com.airtouch

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.airtouch.ui.AirTouchScreen
import com.airtouch.ui.theme.AirTouchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mantém a tela acesa enquanto o app está aberto (a câmera precisa ficar ativa).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AirTouchTheme {
                AirTouchScreen()
            }
        }
    }
}
