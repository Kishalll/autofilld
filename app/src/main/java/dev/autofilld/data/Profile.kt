package dev.autofilld.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.autofilld.fill.FieldType

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val addrLine1: String = "",
    val addrLine2: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = ""
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}

fun Profile.valueFor(type: FieldType): String = when (type) {
    FieldType.FULL_NAME -> fullName
    FieldType.EMAIL -> email
    FieldType.PHONE -> phone
    FieldType.ADDR_LINE1 -> addrLine1
    FieldType.ADDR_LINE2 -> addrLine2
    FieldType.CITY -> city
    FieldType.STATE -> state
    FieldType.POSTAL_CODE -> postalCode
    FieldType.COUNTRY -> country
}

fun Profile.withValue(type: FieldType, value: String): Profile = when (type) {
    FieldType.FULL_NAME -> copy(fullName = value)
    FieldType.EMAIL -> copy(email = value)
    FieldType.PHONE -> copy(phone = value)
    FieldType.ADDR_LINE1 -> copy(addrLine1 = value)
    FieldType.ADDR_LINE2 -> copy(addrLine2 = value)
    FieldType.CITY -> copy(city = value)
    FieldType.STATE -> copy(state = value)
    FieldType.POSTAL_CODE -> copy(postalCode = value)
    FieldType.COUNTRY -> copy(country = value)
}
