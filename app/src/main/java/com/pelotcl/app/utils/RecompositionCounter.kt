package com.pelotcl.app.utils

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Utility for measuring Compose recompositions.
 *
 * Usage:
 * ```
 * @Composable
 * fun MyComposable() {
 *     RecompositionCounter("MyComposable")
 *     // ... rest of composable
 * }
 * ```
 *
 * To enable Layout Inspector in Android Studio:
 * 1. Run > Edit Configurations > App
 * 2. Check "Enable advanced profiling"
 * 3. Build and run the app
 * 4. View > Tool Windows > Layout Inspector
 * 5. Select the app process
 * 6. Click the "Show Recomposition Counts" icon (icon with a counter)
 *
 * The counters show:
 * - Number of recompositions
 * - Number of "skips" (recompositions avoided due to stable keys)
 */

private const val TAG = "RecompositionCounter"
private const val ENABLED = false // Set to true for debugging recompositions

/**
 * Recomposition counter for list items.
 * Useful for verifying that keys are working correctly.
 *
 * Note: A recomposition #2+ is NOT always a problem.
 * It is normal if the composable's parameters have changed (e.g., isFirst, isLast).
 * The warning is meant to identify cases that should be checked.
 */
@Composable
fun ListItemRecompositionCounter(listName: String, itemKey: Any) {
    if (!ENABLED) return

    val counter = remember { ListItemCounterState(listName, itemKey.toString()) }
    SideEffect {
        counter.increment()
    }
}

private class ListItemCounterState(
    private val listName: String,
    private val itemKey: String
) {
    private var count = 0

    fun increment() {
        count++
        if (count > 1) {
            // Log info (not warning) - recompositions may be legitimate
            // if the composable's parameters have changed
            Log.d(TAG, "[$listName] Item '$itemKey' recomposed #$count times (may be legitimate if params changed)")
        }
    }
}
