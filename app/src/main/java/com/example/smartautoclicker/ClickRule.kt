package com.example.smartautoclicker

data class ClickRule(
    val id: Int,
    val name: String,
    val colorHex: String = "#FF0000",
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 500,
    val bottom: Int = 500,
    val clickX: Int = 300,
    val clickY: Int = 300,
    val tolerance: Int = 15,
    val delayMs: Long = 500L
)
