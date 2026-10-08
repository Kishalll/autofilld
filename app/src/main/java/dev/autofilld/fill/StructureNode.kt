package dev.autofilld.fill

import android.app.assist.AssistStructure
import android.view.View
import android.view.autofill.AutofillId

/**
 * Interface abstraction over a view hierarchy node. Decouples traversal and
 * heuristics from Android SDK classes, allowing pure JVM testing with fake trees.
 */
interface StructureNode {
    val autofillId: AutofillId? get() = null
    val autofillHints: Array<String>? get() = null
    val hint: String? get() = null
    val idEntry: String? get() = null
    val text: CharSequence? get() = null
    val className: String? get() = null
    val inputType: Int get() = 0
    val autofillType: Int get() = View.AUTOFILL_TYPE_NONE
    val importantForAutofill: Int get() = View.IMPORTANT_FOR_AUTOFILL_AUTO
    val visibility: Int get() = View.VISIBLE
    val isFocused: Boolean get() = false
    val htmlAttrs: Map<String, String> get() = emptyMap()
    val htmlTag: String? get() = null
    val webDomain: String? get() = null
    val children: List<StructureNode> get() = emptyList()
}

/**
 * Interface abstraction over a window in the AssistStructure.
 */
interface StructureWindow {
    val windowId: Int
    val rootNode: StructureNode?
}

/**
 * Runtime adapter wrapping Android framework [AssistStructure.ViewNode].
 */
class ViewNodeAdapter(private val viewNode: AssistStructure.ViewNode) : StructureNode {
    override val autofillId: AutofillId? get() = viewNode.autofillId
    override val autofillHints: Array<String>? get() = viewNode.autofillHints
    override val hint: String? get() = viewNode.hint
    override val idEntry: String? get() = viewNode.idEntry
    override val text: CharSequence? get() = viewNode.text
    override val className: String? get() = viewNode.className
    override val inputType: Int get() = viewNode.inputType
    override val autofillType: Int get() = viewNode.autofillType
    override val importantForAutofill: Int
        get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            viewNode.importantForAutofill
        } else {
            View.IMPORTANT_FOR_AUTOFILL_AUTO
        }
    override val visibility: Int get() = viewNode.visibility
    override val isFocused: Boolean get() = viewNode.isFocused
    override val webDomain: String? get() = viewNode.webDomain

    override val htmlTag: String?
        get() = viewNode.htmlInfo?.tag

    override val htmlAttrs: Map<String, String> by lazy {
        val info = viewNode.htmlInfo ?: return@lazy emptyMap()
        val attrs = mutableMapOf<String, String>()
        info.attributes?.forEach { pair ->
            val k = pair.first
            val v = pair.second
            if (k != null && v != null) {
                attrs[k.lowercase()] = v
            }
        }
        attrs
    }

    override val children: List<StructureNode> by lazy {
        val count = viewNode.childCount
        List(count) { i -> ViewNodeAdapter(viewNode.getChildAt(i)) }
    }
}

/**
 * Runtime adapter wrapping Android framework [AssistStructure.WindowNode].
 */
class WindowNodeAdapter(
    private val windowNode: AssistStructure.WindowNode,
    override val windowId: Int
) : StructureWindow {
    override val rootNode: StructureNode? by lazy {
        windowNode.rootViewNode?.let { ViewNodeAdapter(it) }
    }
}
