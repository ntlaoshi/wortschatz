package de.wortschatz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import de.wortschatz.app.ui.WortschatzApp
import de.wortschatz.app.ui.theme.WortschatzTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // ViewModel 需在主题外层创建：主题模式存在其中，切换时要能触发重组
            val vm: AppViewModel = viewModel()
            val dark = when (vm.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            WortschatzTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WortschatzApp(vm = vm)
                }
            }
        }
    }
}
