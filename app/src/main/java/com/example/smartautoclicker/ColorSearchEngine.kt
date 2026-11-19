package com.example.smartautoclicker

import android.graphics.Bitmap
import android.graphics.Color

object ColorSearchEngine {
    data class Match(val x: Int, val y: Int)

    fun find(bitmap: Bitmap, rule: ClickRule): Match? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val target = try { Color.parseColor(rule.colorHex.trim()) } catch (_: IllegalArgumentException) { return null }

        val left = rule.left.coerceIn(0, bitmap.width - 1)
        val top = rule.top.coerceIn(0, bitmap.height - 1)
        val right = rule.right.coerceIn(left + 1, bitmap.width)
        val bottom = rule.bottom.coerceIn(top + 1, bitmap.height)

        val targetR = Color.red(target)
        val targetG = Color.green(target)
        val targetB = Color.blue(target)
        val tolerance = rule.tolerance.coerceIn(0, 255)
        val row = IntArray(right - left)

        for (y in top until bottom) {
            bitmap.getPixels(row, 0, row.size, left, y, row.size, 1)
            for (xOffset in row.indices) {
                val pixel = row[xOffset]
                if (kotlin.math.abs(Color.red(pixel) - targetR) <= tolerance &&
                    kotlin.math.abs(Color.green(pixel) - targetG) <= tolerance &&
                    kotlin.math.abs(Color.blue(pixel) - targetB) <= tolerance
                ) return Match(left + xOffset, y)
            }
        }
        return null
    }
}
