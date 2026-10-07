package dev.autofilld.data

import dev.autofilld.fill.FieldType

object ProfileMerge {

    /**
     * Merges detected field values into the existing profile. Only fields present
     * in [updates] with a non-blank (after trim) value are overwritten; everything
     * else is preserved untouched.
     */
    fun merge(existing: Profile, updates: Map<FieldType, String>): Profile {
        var result = existing
        for ((type, rawValue) in updates) {
            val value = rawValue.trim()
            if (value.isNotEmpty()) {
                result = result.withValue(type, value)
            }
        }
        return result
    }
}
