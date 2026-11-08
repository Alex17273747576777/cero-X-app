package com.ceroX.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ceroX.app.ui.theme.CeroXTheme

@Composable
fun ceroXTheme(content: @Composable () -> Unit) {
    CeroXTheme(content = content)
}