package dev.autofilld.fill

import android.view.autofill.AutofillId

/**
 * Represents a fillable input candidate extracted by [StructureWalker].
 */
data class FieldCandidate(
    val signals: FieldSignals,
    val autofillId: AutofillId?,
    val windowId: Int,
    val text: String?,
    val isFocused: Boolean = false,
    val testId: String? = null
)

/**
 * Output of hierarchy traversal across all windows in a session.
 */
data class WalkResult(
    val candidates: List<FieldCandidate>,
    val isWebView: Boolean = false,
    val webDomain: String? = null
)
