package dev.autofilld.fill

import android.app.assist.AssistStructure
import android.view.View

/**
 * Traverses an [AssistStructure] (or abstract [StructureWindow]s) to detect fillable
 * field candidates, associate nearby labels, capture current field values, and isolate
 * the focused window.
 */
object StructureWalker {

    const val MAX_DEPTH = 64
    private const val MAX_LABEL_LENGTH = 80

    private val EXCLUDED_HTML_INPUT_TYPES = setOf(
        "submit", "button", "reset", "image", "hidden",
        "checkbox", "radio", "file"
    )

    /**
     * Traverses given windows and returns the partitioned candidates and session metadata.
     */
    fun walk(windows: List<StructureWindow>, compatFlag: Boolean = false): WalkResult {
        var isWebView = compatFlag
        var webDomain: String? = null
        val candidatesPerWindow = mutableMapOf<Int, MutableList<FieldCandidate>>()

        for (window in windows) {
            val root = window.rootNode ?: continue
            val list = mutableListOf<FieldCandidate>()
            traverse(
                node = root,
                windowId = window.windowId,
                depth = 0,
                parent = null,
                siblingIndex = 0,
                siblings = listOf(root),
                candidates = list,
                onMeta = { foundWebView, domain ->
                    if (foundWebView) isWebView = true
                    if (domain != null && webDomain == null) webDomain = domain
                }
            )
            if (list.isNotEmpty()) {
                candidatesPerWindow[window.windowId] = list
            }
        }

        if (candidatesPerWindow.isEmpty()) {
            return WalkResult(emptyList(), isWebView, webDomain)
        }

        // Multi-window partitioning: prioritize window containing the focused node.
        val focusedWindowEntry = candidatesPerWindow.entries.firstOrNull { (_, list) ->
            list.any { it.isFocused }
        }

        val chosenCandidates = if (focusedWindowEntry != null) {
            focusedWindowEntry.value
        } else {
            // Fallback: pick the first window that produced candidates.
            candidatesPerWindow.values.first()
        }

        return WalkResult(
            candidates = chosenCandidates,
            isWebView = isWebView,
            webDomain = webDomain
        )
    }

    /**
     * Convenience wrapper over Android framework [AssistStructure].
     */
    fun walk(structure: AssistStructure, compatFlag: Boolean = false): WalkResult {
        val windowCount = structure.windowNodeCount
        val windows = (0 until windowCount).map { i ->
            WindowNodeAdapter(structure.getWindowNodeAt(i), windowId = i)
        }
        return walk(windows, compatFlag)
    }

    private fun traverse(
        node: StructureNode,
        windowId: Int,
        depth: Int,
        parent: StructureNode?,
        siblingIndex: Int,
        siblings: List<StructureNode>,
        candidates: MutableList<FieldCandidate>,
        onMeta: (isWebView: Boolean, webDomain: String?) -> Unit
    ) {
        if (depth > MAX_DEPTH) return

        // Invisible nodes and descendants are ignored.
        if (node.visibility != View.VISIBLE) return

        // Entire subtree excluded if NO_EXCLUDE_DESCENDANTS.
        if (node.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS) {
            return
        }

        // Collect session metadata.
        val nodeCls = node.className ?: ""
        val isWeb = nodeCls.contains("WebView", ignoreCase = true) ||
            node.webDomain != null ||
            node.htmlAttrs.isNotEmpty() ||
            node.htmlTag != null
        onMeta(isWeb, node.webDomain)

        val isSelfExcluded = node.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO
        val isCandidate = !isSelfExcluded && isInputCandidate(node)

        if (isCandidate) {
            val labelText = findLabelText(parent, siblingIndex, siblings)
            val signals = FieldSignals(
                autofillHint = node.autofillHints?.firstOrNull() ?: node.htmlAttrs["autocomplete"],
                hintText = node.hint,
                idEntry = node.idEntry,
                labelText = labelText,
                htmlAttrs = node.htmlAttrs,
                inputType = node.inputType,
                className = node.className
            )
            candidates.add(
                FieldCandidate(
                    signals = signals,
                    autofillId = node.autofillId,
                    windowId = windowId,
                    text = node.text?.toString(),
                    isFocused = node.isFocused,
                    testId = node.idEntry ?: node.htmlAttrs["id"]
                )
            )
        }

        // Traverse children.
        val childList = node.children
        for (i in childList.indices) {
            traverse(
                node = childList[i],
                windowId = windowId,
                depth = depth + 1,
                parent = node,
                siblingIndex = i,
                siblings = childList,
                candidates = candidates,
                onMeta = onMeta
            )
        }
    }

    private fun isInputCandidate(node: StructureNode): Boolean {
        val tag = node.htmlTag?.lowercase()
        if (tag != null) {
            if (tag in setOf("input", "textarea")) {
                val type = node.htmlAttrs["type"]?.lowercase()
                if (type in EXCLUDED_HTML_INPUT_TYPES) return false
                return true
            }
            return false
        }
        if (node.autofillType == View.AUTOFILL_TYPE_TEXT) return true
        if (node.inputType != 0) return true
        val cls = node.className ?: ""
        if (cls.contains("EditText", ignoreCase = true)) return true
        return false
    }

    private fun findLabelText(
        parent: StructureNode?,
        siblingIndex: Int,
        siblings: List<StructureNode>
    ): String? {
        // 1. Check preceding siblings (closest earlier non-input sibling with short text).
        for (i in (siblingIndex - 1) downTo 0) {
            val sib = siblings.getOrNull(i) ?: continue
            if (isInputCandidate(sib)) break
            val text = sib.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && text.length <= MAX_LABEL_LENGTH) {
                return text
            }
        }

        // 2. Fallback: check parent container text if input is wrapped (e.g. <label>Email <input></label>).
        if (parent != null && !isInputCandidate(parent)) {
            val parentText = parent.text?.toString()?.trim()
            if (!parentText.isNullOrEmpty() && parentText.length <= MAX_LABEL_LENGTH) {
                return parentText
            }
        }

        return null
    }
}
