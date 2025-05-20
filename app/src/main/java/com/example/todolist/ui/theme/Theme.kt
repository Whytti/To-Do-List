package com.example.todolist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006E1C),
    onPrimary = Color.White,
    background = Color(0xFFF1F1F1),
    surface = Color.White,
    onSurface = Color.Black
)

@Composable
fun ToDoListTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
