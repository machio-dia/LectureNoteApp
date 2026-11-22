package com.example.lecturenote

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.lecturenote.ui.LectureNoteApp
import com.example.lecturenote.ui.theme.LectureNoteTheme
import com.example.lecturenote.viewmodel.NoteViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideNavigationBar()
        setContent {
            LectureNoteTheme {
                LectureNoteApp(viewModel = viewModel)
            }
        }
    }

    /** 下部のナビゲーションバー（戻る・ホーム）を隠す。画面下端からのスワイプで一時的に表示される */
    private fun hideNavigationBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    override fun onStop() {
        super.onStop()
        viewModel.flushPendingSaveAsync()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val state = viewModel.uiState.value

        if (state.isListeningForStylusKey) {
            val isSystemVolumeOrBack = keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_POWER
            val devName = event.device?.name ?: "Unknown"
            val isFromPenDevice = devName.lowercase().let {
                it.contains("pen") || it.contains("stylus") || it.contains("xiaomi") || it.contains("focus")
            }

            if (!isSystemVolumeOrBack || isFromPenDevice) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    viewModel.onStylusKeyLearned(keyCode, event.scanCode, devName)
                }
                return true
            }
        }

        if (isStylusSideButtonKeyEvent(keyCode, event, state.customStylusKeyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                viewModel.toggleEraserByStylusButton()
            }
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun isStylusSideButtonKeyEvent(
        keyCode: Int,
        event: KeyEvent,
        customKeyCode: Int
    ): Boolean {
        if (customKeyCode > 0 && keyCode == customKeyCode) return true

        when (keyCode) {
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_BUTTON_1,
            KeyEvent.KEYCODE_BUTTON_2,
            KeyEvent.KEYCODE_BUTTON_3,
            KeyEvent.KEYCODE_BUTTON_4,
            308,
            309,
            310,
            311 -> return true
        }

        val device = event.device
        if (device != null) {
            val nameLower = device.name?.lowercase() ?: ""
            if (nameLower.contains("pen") ||
                nameLower.contains("stylus") ||
                nameLower.contains("xiaomi") ||
                nameLower.contains("focus")
            ) {
                return true
            }
            if ((device.sources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS) {
                return true
            }
        }

        return false
    }
}
