package app.olauncher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

/** Paleta e identidad visual tipo Pip-Boy / CRT para el launcher. */
object VtTheme {
    val GREEN = Color.rgb(63, 255, 82)
    val GREEN_DIM = Color.rgb(28, 94, 44)
    val AMBER = Color.rgb(255, 200, 0)
    val RED = Color.rgb(255, 72, 72)
    val BACKGROUND = Color.rgb(3, 9, 4)

    fun typeface(context: Context): Typeface {
        return try {
            Typeface.createFromAsset(context.assets, "fonts/VT323-Regular.ttf")
        } catch (e: Exception) {
            Typeface.MONOSPACE
        }
    }
}