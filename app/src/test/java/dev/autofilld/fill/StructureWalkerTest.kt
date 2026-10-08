package dev.autofilld.fill

import android.view.View
import android.view.autofill.AutofillId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureWalkerTest {

    private data class FakeNode(
        override val autofillId: AutofillId? = null,
        override val autofillHints: Array<String>? = null,
        override val hint: String? = null,
        override val idEntry: String? = null,
        override val text: CharSequence? = null,
        override val className: String? = null,
        override val inputType: Int = 0,
        override val autofillType: Int = View.AUTOFILL_TYPE_NONE,
        override val importantForAutofill: Int = View.IMPORTANT_FOR_AUTOFILL_AUTO,
        override val visibility: Int = View.VISIBLE,
        override val isFocused: Boolean = false,
        override val htmlAttrs: Map<String, String> = emptyMap(),
        override val htmlTag: String? = null,
        override val webDomain: String? = null,
        override val children: List<StructureNode> = emptyList()
    ) : StructureNode

    private data class FakeWindow(
        override val windowId: Int = 0,
        override val rootNode: StructureNode? = null
    ) : StructureWindow

    @Test
    fun `label association preceding sibling text is associated as label`() {
        val root = FakeNode(
            className = "LinearLayout",
            children = listOf(
                FakeNode(
                    className = "TextView",
                    text = "Email Address"
                ),
                FakeNode(
                    className = "EditText",
                    idEntry = "email_input",
                    autofillType = View.AUTOFILL_TYPE_TEXT
                )
            )
        )

        val result = StructureWalker.walk(listOf(FakeWindow(0, root)))
        assertEquals(1, result.candidates.size)
        val candidate = result.candidates[0]
        assertEquals("Email Address", candidate.signals.labelText)
        assertEquals("email_input", candidate.testId)
    }

    @Test
    fun `label association wrapped parent text is associated when input is nested`() {
        val root = FakeNode(
            htmlTag = "label",
            text = "Phone Number",
            children = listOf(
                FakeNode(
                    htmlTag = "input",
                    htmlAttrs = mapOf("type" to "tel", "id" to "phone_field")
                )
            )
        )

        val result = StructureWalker.walk(listOf(FakeWindow(0, root)))
        assertEquals(1, result.candidates.size)
        val candidate = result.candidates[0]
        assertEquals("Phone Number", candidate.signals.labelText)
        assertEquals("phone_field", candidate.testId)
    }

    @Test
    fun `htmlInfo and webDomain captured from webview virtual nodes`() {
        val root = FakeNode(
            webDomain = "checkout.example.com",
            children = listOf(
                FakeNode(
                    htmlTag = "input",
                    htmlAttrs = mapOf(
                        "autocomplete" to "shipping street-address",
                        "id" to "street_1",
                        "name" to "address1",
                        "placeholder" to "123 Main St"
                    )
                )
            )
        )

        val result = StructureWalker.walk(listOf(FakeWindow(0, root)))
        assertTrue(result.isWebView)
        assertEquals("checkout.example.com", result.webDomain)
        assertEquals(1, result.candidates.size)

        val signals = result.candidates[0].signals
        assertEquals("shipping street-address", signals.autofillHint)
        assertEquals("shipping street-address", signals.htmlAttrs["autocomplete"])
        assertEquals("street_1", signals.htmlAttrs["id"])
        assertEquals("address1", signals.htmlAttrs["name"])
        assertEquals("123 Main St", signals.htmlAttrs["placeholder"])
    }

    @Test
    fun `compat flag propagates to session meta`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(idEntry = "native_field", autofillType = View.AUTOFILL_TYPE_TEXT)
            )
        )
        val result = StructureWalker.walk(listOf(FakeWindow(0, root)), compatFlag = true)
        assertTrue(result.isWebView)
        assertEquals(1, result.candidates.size)
    }

    @Test
    fun `importantForAutofill NO is excluded from candidates but children traversed`() {
        val root = FakeNode(
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO,
            autofillType = View.AUTOFILL_TYPE_TEXT,
            idEntry = "trap_field",
            children = listOf(
                FakeNode(
                    idEntry = "child_input",
                    autofillType = View.AUTOFILL_TYPE_TEXT
                )
            )
        )

        val result = StructureWalker.walk(listOf(FakeWindow(0, root)))
        assertEquals(1, result.candidates.size)
        assertEquals("child_input", result.candidates[0].testId)
    }

    @Test
    fun `importantForAutofill NO_EXCLUDE_DESCENDANTS prunes entire subtree`() {
        val root = FakeNode(
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
            children = listOf(
                FakeNode(
                    idEntry = "nested_input",
                    autofillType = View.AUTOFILL_TYPE_TEXT
                )
            )
        )

        val result = StructureWalker.walk(listOf(FakeWindow(0, root)))
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `multi-window partitioning selects candidates only from focused window`() {
        val win0 = FakeWindow(
            windowId = 0,
            rootNode = FakeNode(
                children = listOf(
                    FakeNode(
                        idEntry = "bg_field",
                        autofillType = View.AUTOFILL_TYPE_TEXT,
                        isFocused = false
                    )
                )
            )
        )
        val win1 = FakeWindow(
            windowId = 1,
            rootNode = FakeNode(
                children = listOf(
                    FakeNode(
                        idEntry = "dialog_field",
                        autofillType = View.AUTOFILL_TYPE_TEXT,
                        isFocused = true
                    )
                )
            )
        )

        val result = StructureWalker.walk(listOf(win0, win1))
        assertEquals(1, result.candidates.size)
        assertEquals("dialog_field", result.candidates[0].testId)
        assertEquals(1, result.candidates[0].windowId)
        assertTrue(result.candidates[0].isFocused)
    }

    @Test
    fun `no-focus fallback preserves candidates from primary window`() {
        val win0 = FakeWindow(
            windowId = 0,
            rootNode = FakeNode(
                children = listOf(
                    FakeNode(idEntry = "win0_field", autofillType = View.AUTOFILL_TYPE_TEXT)
                )
            )
        )
        val win1 = FakeWindow(
            windowId = 1,
            rootNode = FakeNode(
                children = listOf(
                    FakeNode(idEntry = "win1_field", autofillType = View.AUTOFILL_TYPE_TEXT)
                )
            )
        )

        val result = StructureWalker.walk(listOf(win0, win1))
        assertEquals(1, result.candidates.size)
        assertEquals("win0_field", result.candidates[0].testId)
        assertEquals(0, result.candidates[0].windowId)
    }

    @Test
    fun `invisible nodes excluded from candidates`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(idEntry = "invis_field", visibility = View.INVISIBLE, autofillType = View.AUTOFILL_TYPE_TEXT),
                FakeNode(idEntry = "gone_field", visibility = View.GONE, autofillType = View.AUTOFILL_TYPE_TEXT),
                FakeNode(idEntry = "vis_field", visibility = View.VISIBLE, autofillType = View.AUTOFILL_TYPE_TEXT)
            )
        )

        val result = StructureWalker.walk(listOf(FakeWindow(0, root)))
        assertEquals(1, result.candidates.size)
        assertEquals("vis_field", result.candidates[0].testId)
    }

    @Test
    fun `current node text captured in candidate for save request`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(
                    idEntry = "email_field",
                    autofillType = View.AUTOFILL_TYPE_TEXT,
                    text = "saved_user@example.com"
                )
            )
        )

        val result = StructureWalker.walk(listOf(FakeWindow(0, root)))
        assertEquals(1, result.candidates.size)
        assertEquals("saved_user@example.com", result.candidates[0].text)
    }

    @Test
    fun `excluded html input types like submit button are not candidates`() {
        val root = FakeNode(
            children = listOf(
                FakeNode(htmlTag = "input", htmlAttrs = mapOf("type" to "submit", "id" to "btn_submit")),
                FakeNode(htmlTag = "input", htmlAttrs = mapOf("type" to "button", "id" to "btn_click")),
                FakeNode(htmlTag = "input", htmlAttrs = mapOf("type" to "hidden", "id" to "csrf_token")),
                FakeNode(htmlTag = "input", htmlAttrs = mapOf("type" to "checkbox", "id" to "agree")),
                FakeNode(htmlTag = "input", htmlAttrs = mapOf("type" to "radio", "id" to "opt1")),
                FakeNode(htmlTag = "input", htmlAttrs = mapOf("type" to "text", "id" to "actual_text"))
            )
        )

        val result = StructureWalker.walk(listOf(FakeWindow(0, root)))
        assertEquals(1, result.candidates.size)
        assertEquals("actual_text", result.candidates[0].testId)
    }

    @Test
    fun `deep tree recursion safety avoids stack overflow`() {
        // Build 100 levels of nesting (exceeds MAX_DEPTH 64)
        var current = FakeNode(idEntry = "deepest_input", autofillType = View.AUTOFILL_TYPE_TEXT)
        for (i in 1..100) {
            current = FakeNode(className = "ViewGroup", children = listOf(current))
        }

        val result = StructureWalker.walk(listOf(FakeWindow(0, current)))
        // Should terminate cleanly at MAX_DEPTH without throwing StackOverflowError
        assertTrue(result.candidates.isEmpty())
    }
}
