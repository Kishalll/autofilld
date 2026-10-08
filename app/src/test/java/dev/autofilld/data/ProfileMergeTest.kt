package dev.autofilld.data

import dev.autofilld.fill.FieldType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileMergeTest {

    private val base = Profile(
        fullName = "Ada Lovelace",
        email = "ada@example.com",
        phone = "+1 555 0100",
        addrLine1 = "12 Main St",
        addrLine2 = "Apt 4",
        city = "Springfield",
        state = "IL",
        postalCode = "62701",
        country = "US"
    )

    @Test
    fun `non-blank update overwrites that field`() {
        val merged = ProfileMerge.merge(base, mapOf(FieldType.EMAIL to "new@example.com"))
        assertEquals("new@example.com", merged.email)
    }

    @Test
    fun `blank update leaves field untouched`() {
        val merged = ProfileMerge.merge(base, mapOf(FieldType.EMAIL to ""))
        assertEquals("ada@example.com", merged.email)
    }

    @Test
    fun `whitespace-only update leaves field untouched`() {
        val merged = ProfileMerge.merge(base, mapOf(FieldType.PHONE to "   "))
        assertEquals("+1 555 0100", merged.phone)
    }

    @Test
    fun `update value is trimmed before storing`() {
        val merged = ProfileMerge.merge(base, mapOf(FieldType.FULL_NAME to "  Grace Hopper  "))
        assertEquals("Grace Hopper", merged.fullName)
    }

    @Test
    fun `multiple fields update together`() {
        val merged = ProfileMerge.merge(
            base,
            mapOf(
                FieldType.EMAIL to "grace@example.com",
                FieldType.PHONE to "+1 555 0199",
                FieldType.CITY to "Arlington"
            )
        )
        assertEquals("grace@example.com", merged.email)
        assertEquals("+1 555 0199", merged.phone)
        assertEquals("Arlington", merged.city)
    }

    @Test
    fun `fields not in updates are preserved`() {
        val merged = ProfileMerge.merge(base, mapOf(FieldType.CITY to "Chicago"))
        assertEquals("Chicago", merged.city)
        assertEquals(base.fullName, merged.fullName)
        assertEquals(base.addrLine1, merged.addrLine1)
        assertEquals(base.postalCode, merged.postalCode)
        assertEquals(base.country, merged.country)
    }

    @Test
    fun `empty updates return equivalent profile`() {
        val merged = ProfileMerge.merge(base, emptyMap())
        assertEquals(base, merged)
    }

    @Test
    fun `original profile is not mutated`() {
        ProfileMerge.merge(base, mapOf(FieldType.CITY to "Chicago"))
        assertEquals("Springfield", base.city)
    }

    @Test
    fun `valueFor withValue roundtrips every FieldType`() {
        val empty = Profile()
        for (type in FieldType.entries) {
            val updated = empty.withValue(type, "value-$type")
            assertEquals("value-$type", updated.valueFor(type))
        }
    }

    @Test
    fun `merge into empty profile populates fields while preserving single row id`() {
        val empty = Profile()
        val merged = ProfileMerge.merge(
            empty,
            mapOf(
                FieldType.FULL_NAME to "Alan Turing",
                FieldType.EMAIL to "alan@turing.ac.uk"
            )
        )
        assertEquals(Profile.SINGLE_ROW_ID, merged.id)
        assertEquals("Alan Turing", merged.fullName)
        assertEquals("alan@turing.ac.uk", merged.email)
        assertEquals("", merged.phone)
    }
}
