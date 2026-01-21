package com.pelotcl.app.utils

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Utilitaire pour mesurer les recompositions Compose.
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
 * Pour activer le Layout Inspector dans Android Studio:
 * 1. Run > Edit Configurations > App
 * 2. Cocher "Enable advanced profiling"
 * 3. Build and run l'app
 * 4. View > Tool Windows > Layout Inspector
 * 5. Sélectionner le processus de l'app
 * 6. Cliquer sur l'icône "Show Recomposition Counts" (icône avec compteur)
 *
 * Les compteurs montrent:
 * - Nombre de recompositions
 * - Nombre de "skips" (recompositions évitées grâce aux clés stables)
 */

private const val TAG = "RecompositionCounter"
private const val ENABLED = true // Mettre à false en production

/**
 * Compte et log le nombre de recompositions pour un composable donné.
 * Utiliser uniquement en debug.
 */
@Composable
fun RecompositionCounter(name: String) {
    if (!ENABLED) return

    val counter = remember { RecompositionCounterState(name) }
    SideEffect {
        counter.increment()
    }
}

/**
 * Version inline pour mesurer une section spécifique
 */
@Composable
inline fun <T> measureRecomposition(name: String, content: @Composable () -> T): T {
    RecompositionCounter(name)
    return content()
}

private class RecompositionCounterState(private val name: String) {
    private var count = 0

    fun increment() {
        count++
        Log.d(TAG, "[$name] Recomposition #$count")
    }
}

/**
 * Compteur de recomposition pour les items de liste.
 * Utile pour vérifier que les clés fonctionnent correctement.
 *
 * Note: Une recomposition #2+ n'est PAS toujours un problème.
 * C'est normal si les paramètres du composable ont changé (ex: isFirst, isLast).
 * Le warning sert à identifier les cas où il faudrait vérifier.
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
            // Log info (pas warning) - les recompositions peuvent être légitimes
            // si les paramètres du composable ont changé
            Log.d(TAG, "[$listName] Item '$itemKey' recomposed #$count times (may be legitimate if params changed)")
        }
    }
}

/**
 * Résumé global des recompositions - à appeler périodiquement
 */
object RecompositionStats {
    private val stats = mutableMapOf<String, Int>()

    fun record(name: String) {
        stats[name] = (stats[name] ?: 0) + 1
    }

    fun logSummary() {
        if (stats.isEmpty()) return
        Log.d(TAG, "=== Recomposition Summary ===")
        stats.entries.sortedByDescending { it.value }.forEach { (name, count) ->
            Log.d(TAG, "  $name: $count recompositions")
        }
        Log.d(TAG, "=============================")
    }

    fun reset() {
        stats.clear()
    }
}
