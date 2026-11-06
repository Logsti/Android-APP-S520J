package com.s520j.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.s520j.app.ui.AppViewModel
import com.s520j.app.ui.navigation.MainShell
import com.s520j.app.ui.screens.auth.AuthScreen
import com.s520j.app.ui.theme.S520JTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel by viewModels()
            val fontScale by vm.fontScale.collectAsState()
            val session by vm.session.collectAsState()

            LaunchedEffect(Unit) { vm.seedIfNeeded() }

            S520JTheme(fontScale = fontScale) {
                if (session.loggedIn) MainShell(vm) else AuthScreen(vm)
            }
        }
    }
}
