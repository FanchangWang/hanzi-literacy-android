package com.pupil.hanzi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.pupil.hanzi.ui.HanziViewModel
import com.pupil.hanzi.ui.MainScreen
import com.pupil.hanzi.ui.theme.HanziliteracyandroidTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HanziViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HanziliteracyandroidTheme {
                MainScreen(viewModel)
            }
        }
    }
}