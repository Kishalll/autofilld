package dev.autofilld.fill

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldClassifierTest {

    // --- Fast path: standard autofillHints ---

    @Test
    fun `standard autofill hint emailAddress short-circuits to EMAIL at full confidence`() {
        val result = FieldClassifier.classify(FieldSignals(autofillHint = "emailAddress"))
        assertEquals(FieldType.EMAIL, result!!.type)
        assertEquals(1.0, result.score, 0.0)
    }

    @Test
    fun `standard autofill hint postalCode short-circuits to POSTAL_CODE`() {
        val result = FieldClassifier.classify(FieldSignals(autofillHint = "postalCode"))
        assertEquals(FieldType.POSTAL_CODE, result!!.type)
        assertEquals(1.0, result.score, 0.0)
    }

    @Test
    fun `standard autofill hint name short-circuits to FULL_NAME`() {
        val result = FieldClassifier.classify(FieldSignals(autofillHint = "name"))
        assertEquals(FieldType.FULL_NAME, result!!.type)
    }

    @Test
    fun `standard autofill hint phone short-circuits to PHONE`() {
        val result = FieldClassifier.classify(FieldSignals(autofillHint = "phone"))
        assertEquals(FieldType.PHONE, result!!.type)
    }

    // --- Individual heuristic signals ---

    @Test
    fun `placeholder text identifies email`() {
        val result = FieldClassifier.classify(FieldSignals(hintText = "Enter your email"))
        assertEquals(FieldType.EMAIL, result!!.type)
        assertTrue(result.score >= FieldClassifier.MIN_SCORE)
    }

    @Test
    fun `resource id entry identifies email`() {
        val result = FieldClassifier.classify(FieldSignals(idEntry = "email_input"))
        assertEquals(FieldType.EMAIL, result!!.type)
    }

    @Test
    fun `nearby label identifies phone`() {
        val result = FieldClassifier.classify(FieldSignals(labelText = "Phone number"))
        assertEquals(FieldType.PHONE, result!!.type)
    }

    @Test
    fun `html autocomplete tel identifies phone at high confidence`() {
        val result = FieldClassifier.classify(
            FieldSignals(htmlAttrs = mapOf("autocomplete" to "tel"))
        )
        assertEquals(FieldType.PHONE, result!!.type)
        assertTrue(result.score >= 0.95)
    }

    @Test
    fun `html autocomplete address-level2 identifies CITY not ADDR_LINE1`() {
        val result = FieldClassifier.classify(
            FieldSignals(htmlAttrs = mapOf("autocomplete" to "address-level2"))
        )
        assertEquals(FieldType.CITY, result!!.type)
    }

    @Test
    fun `email inputType alone clears threshold`() {
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val result = FieldClassifier.classify(FieldSignals(inputType = inputType))
        assertEquals(FieldType.EMAIL, result!!.type)
    }

    @Test
    fun `phone class inputType alone clears threshold`() {
        val result = FieldClassifier.classify(
            FieldSignals(inputType = InputType.TYPE_CLASS_PHONE)
        )
        assertEquals(FieldType.PHONE, result!!.type)
    }

    @Test
    fun `html id attribute identifies phone`() {
        val result = FieldClassifier.classify(
            FieldSignals(htmlAttrs = mapOf("id" to "phone_number"))
        )
        assertEquals(FieldType.PHONE, result!!.type)
    }

    @Test
    fun `html type email attribute identifies EMAIL`() {
        val result = FieldClassifier.classify(
            FieldSignals(htmlAttrs = mapOf("type" to "email"))
        )
        assertEquals(FieldType.EMAIL, result!!.type)
    }

    // --- Address sub-fields ---

    @Test
    fun `label City identifies CITY`() {
        assertEquals(
            FieldType.CITY,
            FieldClassifier.classify(FieldSignals(labelText = "City"))!!.type
        )
    }

    @Test
    fun `label State identifies STATE`() {
        assertEquals(
            FieldType.STATE,
            FieldClassifier.classify(FieldSignals(labelText = "State / Province"))!!.type
        )
    }

    @Test
    fun `label Postal code identifies POSTAL_CODE`() {
        assertEquals(
            FieldType.POSTAL_CODE,
            FieldClassifier.classify(FieldSignals(labelText = "Postal code"))!!.type
        )
    }

    @Test
    fun `label Country identifies COUNTRY`() {
        assertEquals(
            FieldType.COUNTRY,
            FieldClassifier.classify(FieldSignals(labelText = "Country"))!!.type
        )
    }

    @Test
    fun `label Address identifies ADDR_LINE1`() {
        assertEquals(
            FieldType.ADDR_LINE1,
            FieldClassifier.classify(FieldSignals(labelText = "Street address"))!!.type
        )
    }

    @Test
    fun `label Apartment identifies ADDR_LINE2 not ADDR_LINE1`() {
        assertEquals(
            FieldType.ADDR_LINE2,
            FieldClassifier.classify(FieldSignals(labelText = "Apartment / Suite"))!!.type
        )
    }

    @Test
    fun `label Address line 2 identifies ADDR_LINE2 not ADDR_LINE1`() {
        assertEquals(
            FieldType.ADDR_LINE2,
            FieldClassifier.classify(FieldSignals(labelText = "Address line 2"))!!.type
        )
    }

    @Test
    fun `label Street address 2 identifies ADDR_LINE2`() {
        assertEquals(
            FieldType.ADDR_LINE2,
            FieldClassifier.classify(FieldSignals(labelText = "Street address 2"))!!.type
        )
    }

    @Test
    fun `label Address line 1 identifies ADDR_LINE1`() {
        assertEquals(
            FieldType.ADDR_LINE1,
            FieldClassifier.classify(FieldSignals(labelText = "Address line 1"))!!.type
        )
    }

    @Test
    fun `id entry address_2 identifies ADDR_LINE2`() {
        assertEquals(
            FieldType.ADDR_LINE2,
            FieldClassifier.classify(FieldSignals(idEntry = "address_2"))!!.type
        )
    }

    @Test
    fun `id entry zip_code identifies POSTAL_CODE`() {
        assertEquals(
            FieldType.POSTAL_CODE,
            FieldClassifier.classify(FieldSignals(idEntry = "zip_code"))!!.type
        )
    }

    // --- Name handling ---

    @Test
    fun `label Full name identifies FULL_NAME`() {
        assertEquals(
            FieldType.FULL_NAME,
            FieldClassifier.classify(FieldSignals(labelText = "Full name"))!!.type
        )
    }

    @Test
    fun `label First name does NOT become FULL_NAME`() {
        assertNull(FieldClassifier.classify(FieldSignals(labelText = "First name")))
    }

    @Test
    fun `label User name does NOT become FULL_NAME`() {
        assertNull(FieldClassifier.classify(FieldSignals(labelText = "User name")))
    }

    @Test
    fun `label Company name does NOT become FULL_NAME`() {
        assertNull(FieldClassifier.classify(FieldSignals(labelText = "Company name")))
    }

    // --- Conflicts, threshold, corroboration ---

    @Test
    fun `conflicting id and label resolves to higher scoring type`() {
        // id says email (0.75), label says mobile (0.7) -> email wins
        val result = FieldClassifier.classify(
            FieldSignals(idEntry = "email_input", labelText = "Mobile number")
        )
        assertEquals(FieldType.EMAIL, result!!.type)
    }

    @Test
    fun `no meaningful signals returns null`() {
        assertNull(
            FieldClassifier.classify(
                FieldSignals(
                    hintText = "Notes",
                    labelText = "Comments",
                    idEntry = "notes_field"
                )
            )
        )
    }

    @Test
    fun `bare number inputType alone is below threshold`() {
        assertNull(
            FieldClassifier.classify(FieldSignals(inputType = InputType.TYPE_CLASS_NUMBER))
        )
    }

    @Test
    fun `corroborating sources boost score above single source`() {
        val single = FieldClassifier.classify(FieldSignals(hintText = "Email address"))!!
        val corroborated = FieldClassifier.classify(
            FieldSignals(
                hintText = "Email address",
                labelText = "Email address",
                idEntry = "email_input"
            )
        )!!
        assertTrue(corroborated.score > single.score)
        assertTrue(corroborated.score >= 0.9)
    }

    // --- Hard exclusions ---

    @Test
    fun `label Password always excluded`() {
        assertNull(FieldClassifier.classify(FieldSignals(labelText = "Password")))
    }

    @Test
    fun `label Email on password-like text still excluded`() {
        assertNull(
            FieldClassifier.classify(FieldSignals(hintText = "Choose a password"))
        )
    }

    @Test
    fun `html type password excluded`() {
        assertNull(
            FieldClassifier.classify(FieldSignals(htmlAttrs = mapOf("type" to "password")))
        )
    }

    @Test
    fun `password inputType excluded even with email label`() {
        val passwordInputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertNull(
            FieldClassifier.classify(
                FieldSignals(labelText = "Password", inputType = passwordInputType)
            )
        )
    }

    @Test
    fun `autocomplete one-time-code excluded`() {
        assertNull(
            FieldClassifier.classify(
                FieldSignals(
                    labelText = "Verification code",
                    htmlAttrs = mapOf("autocomplete" to "one-time-code")
                )
            )
        )
    }

    @Test
    fun `cvv token excluded`() {
        assertNull(FieldClassifier.classify(FieldSignals(labelText = "CVV")))
    }
}
