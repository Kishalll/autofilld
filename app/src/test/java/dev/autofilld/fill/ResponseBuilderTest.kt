package dev.autofilld.fill

import android.service.autofill.SaveInfo
import dev.autofilld.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseBuilderTest {

    private val populatedProfile = Profile(
        fullName = "Ada Lovelace",
        email = "ada@example.com",
        phone = "+1 555 0100",
        addrLine1 = "12 Main St",
        addrLine2 = "Suite 200",
        city = "London",
        state = "UK",
        postalCode = "SW1A 1AA",
        country = "UK"
    )

    @Test
    fun `buildSpec packages all classified fields with non-blank profile values`() {
        val candidates = listOf(
            FieldCandidate(
                signals = FieldSignals(autofillHint = "emailAddress"),
                autofillId = null,
                windowId = 0,
                text = null
            ),
            FieldCandidate(
                signals = FieldSignals(autofillHint = "name"),
                autofillId = null,
                windowId = 0,
                text = null
            ),
            FieldCandidate(
                signals = FieldSignals(labelText = "City"),
                autofillId = null,
                windowId = 0,
                text = null
            )
        )

        val spec = ResponseBuilder.buildSpec(candidates, populatedProfile)
        assertNotNull(spec)
        assertEquals(3, spec!!.fieldsToFill.size)

        val types = spec.fieldsToFill.map { it.type }
        assertEquals(listOf(FieldType.EMAIL, FieldType.FULL_NAME, FieldType.CITY), types)

        val values = spec.fieldsToFill.map { it.valueToFill }
        assertEquals(listOf("ada@example.com", "Ada Lovelace", "London"), values)
    }

    @Test
    fun `buildSpec omits fields where profile value is blank`() {
        // Profile with only email and phone set; address fields blank
        val partialProfile = Profile(
            email = "ada@example.com",
            phone = "+1 555 0100"
        )

        val candidates = listOf(
            FieldCandidate(
                signals = FieldSignals(autofillHint = "emailAddress"),
                autofillId = null,
                windowId = 0,
                text = null
            ),
            FieldCandidate(
                signals = FieldSignals(labelText = "Street address"),
                autofillId = null,
                windowId = 0,
                text = null
            ),
            FieldCandidate(
                signals = FieldSignals(labelText = "City"),
                autofillId = null,
                windowId = 0,
                text = null
            )
        )

        val spec = ResponseBuilder.buildSpec(candidates, partialProfile)
        assertNotNull(spec)
        // Only email should be included because addrLine1 and city are blank in profile
        assertEquals(1, spec!!.fieldsToFill.size)
        assertEquals(FieldType.EMAIL, spec.fieldsToFill[0].type)
        assertEquals("ada@example.com", spec.fieldsToFill[0].valueToFill)
    }

    @Test
    fun `buildSpec returns null when all classified fields have blank profile values`() {
        val emptyProfile = Profile() // all blank fields

        val candidates = listOf(
            FieldCandidate(
                signals = FieldSignals(autofillHint = "emailAddress"),
                autofillId = null,
                windowId = 0,
                text = null
            ),
            FieldCandidate(
                signals = FieldSignals(autofillHint = "phone"),
                autofillId = null,
                windowId = 0,
                text = null
            )
        )

        val spec = ResponseBuilder.buildSpec(candidates, emptyProfile)
        assertNull(spec)
    }

    @Test
    fun `buildSpec returns null when no candidates exceed classifier threshold`() {
        val candidates = listOf(
            FieldCandidate(
                signals = FieldSignals(labelText = "Notes", idEntry = "notes_field"),
                autofillId = null,
                windowId = 0,
                text = null
            )
        )

        val spec = ResponseBuilder.buildSpec(candidates, populatedProfile)
        assertNull(spec)
    }

    @Test
    fun `buildSpec sets valid SaveInfo flags`() {
        val candidates = listOf(
            FieldCandidate(
                signals = FieldSignals(autofillHint = "emailAddress"),
                autofillId = null,
                windowId = 0,
                text = null
            )
        )

        val spec = ResponseBuilder.buildSpec(candidates, populatedProfile)
        assertNotNull(spec)
        assertEquals(ResponseBuilder.DEFAULT_SAVE_FLAGS, spec!!.saveTypeFlags)
        assertTrue((spec.saveTypeFlags and SaveInfo.SAVE_DATA_TYPE_ADDRESS) != 0)
        assertTrue((spec.saveTypeFlags and SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS) != 0)
    }

    @Test
    fun `unclassified candidates like password or search are excluded from dataset spec`() {
        val candidates = listOf(
            FieldCandidate(
                signals = FieldSignals(autofillHint = "emailAddress"),
                autofillId = null,
                windowId = 0,
                text = null
            ),
            FieldCandidate(
                signals = FieldSignals(labelText = "Password", htmlAttrs = mapOf("type" to "password")),
                autofillId = null,
                windowId = 0,
                text = null
            ),
            FieldCandidate(
                signals = FieldSignals(htmlAttrs = mapOf("type" to "search", "name" to "query")),
                autofillId = null,
                windowId = 0,
                text = null
            )
        )

        val spec = ResponseBuilder.buildSpec(candidates, populatedProfile)
        assertNotNull(spec)
        assertEquals(1, spec!!.fieldsToFill.size)
        assertEquals(FieldType.EMAIL, spec.fieldsToFill[0].type)
    }
}
