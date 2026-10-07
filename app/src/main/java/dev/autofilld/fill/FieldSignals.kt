package dev.autofilld.fill

/**
 * Classification inputs gathered for a single fillable field node.
 * Pure data — no Android types beyond raw ints, so it is JVM-testable.
 */
data class FieldSignals(
    val autofillHint: String? = null,
    val hintText: String? = null,
    val idEntry: String? = null,
    val labelText: String? = null,
    val htmlAttrs: Map<String, String> = emptyMap(),
    val inputType: Int = 0,
    val className: String? = null
)

data class FieldResult(
    val type: FieldType,
    val score: Double
)
