package com.example.smartautoclicker

data class ClickRule(
    val id: Int,
    val name: String,
    val clickX: Int = 300,
    val clickY: Int = 300,
    val delayMs: Long = 500L
)
