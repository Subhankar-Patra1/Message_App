package com.subhankar.aurachat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.subhankar.aurachat.data.local.SessionManager
import com.subhankar.aurachat.ui.navigation.AuraChatNavGraph
import com.subhankar.aurachat.ui.theme.AuraChatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuraChatTheme {
                AuraChatNavGraph(sessionManager = sessionManager)
            }
        }
    }
}