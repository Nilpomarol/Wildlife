package com.wildlife.feasibility

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

fun Activity.enableSafeSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

fun View.applySystemBarPadding() {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        view.updatePadding(
            left = initialLeft + insets.left,
            top = initialTop + insets.top,
            right = initialRight + insets.right,
            bottom = initialBottom + insets.bottom,
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
