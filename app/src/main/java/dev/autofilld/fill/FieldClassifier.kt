package dev.autofilld.fill

import android.text.InputType
import kotlin.math.min

/**
 * Heuristic field-type classifier.
 *
 * Order of evidence (per project brief):
 *  1. autofillHints fast path — standard hints short-circuit at full confidence.
 *  2. Otherwise score every candidate [FieldType] from: custom autofill hints,
 *     placeholder/hint text, resource id entry, nearby label text, HTML
 *     attributes (id/name/placeholder/autocomplete/type) and inputType.
 *  3. Strongest source + corroboration bonus; argmax wins only at/above
 *     [MIN_SCORE] — below threshold the field stays unfilled (never guess).
 */
object FieldClassifier {

    const val MIN_SCORE = 0.6
    private const val CORROBORATION = 0.12

    // Evidence weights per source.
    private const val W_AUTOCOMPLETE = 0.95
    private const val W_AUTOFILL_HINT = 0.9
    private const val W_HTML_ATTR = 0.8
    private const val W_HINT_TEXT = 0.75
    private const val W_ID_ENTRY = 0.75
    private const val W_LABEL = 0.7
    private const val W_INPUT_TYPE_EMAIL = 0.65
    private const val W_INPUT_TYPE_PHONE = 0.6
    private const val W_INPUT_TYPE_POSTAL = 0.55
    private const val W_INPUT_TYPE_NUMBER = 0.5

    /** Standard autofillHints values that short-circuit at full confidence. */
    private val FAST_HINTS = mapOf(
        "emailaddress" to FieldType.EMAIL,
        "email" to FieldType.EMAIL,
        "phone" to FieldType.PHONE,
        "phonenumber" to FieldType.PHONE,
        "name" to FieldType.FULL_NAME,
        "personname" to FieldType.FULL_NAME,
        "personfullname" to FieldType.FULL_NAME,
        "fullname" to FieldType.FULL_NAME,
        "address" to FieldType.ADDR_LINE1,
        "addressline1" to FieldType.ADDR_LINE1,
        "addressline2" to FieldType.ADDR_LINE2,
        "postaladdress" to FieldType.ADDR_LINE1,
        "postalcode" to FieldType.POSTAL_CODE
    )

    /** W3C autocomplete values (WebView HtmlInfo) matched on whitespace parts. */
    private val AUTOCOMPLETE = mapOf(
        "email" to FieldType.EMAIL,
        "tel" to FieldType.PHONE,
        "tel-national" to FieldType.PHONE,
        "tel-local" to FieldType.PHONE,
        "name" to FieldType.FULL_NAME,
        "street-address" to FieldType.ADDR_LINE1,
        "address-line1" to FieldType.ADDR_LINE1,
        "address-line2" to FieldType.ADDR_LINE2,
        "address-level2" to FieldType.CITY,
        "address-level1" to FieldType.STATE,
        "postal-code" to FieldType.POSTAL_CODE,
        "country" to FieldType.COUNTRY,
        "country-name" to FieldType.COUNTRY
    )

    /** Positive keyword tokens per field type (tokens = lowercase alphanumeric words). */
    private val POSITIVE: Map<FieldType, Set<String>> = mapOf(
        FieldType.FULL_NAME to setOf("name", "names", "fullname"),
        FieldType.EMAIL to setOf("email", "emails", "mail"),
        FieldType.PHONE to setOf(
            "phone", "phones", "mobile", "cellphone", "cell",
            "tel", "telephone", "msisdn"
        ),
        FieldType.ADDR_LINE1 to setOf(
            "address", "addresses", "addr", "street", "road", "line1"
        ),
        FieldType.ADDR_LINE2 to setOf(
            "line2", "addr2", "address2", "apartment", "apt",
            "suite", "unit", "floor", "building", "box"
        ),
        FieldType.CITY to setOf("city", "town", "locality"),
        FieldType.STATE to setOf("state", "province", "region"),
        FieldType.POSTAL_CODE to setOf(
            "postal", "postcode", "postalcode", "zipcode", "zip", "pincode"
        ),
        FieldType.COUNTRY to setOf("country", "nation")
    )

    /**
     * Negative tokens that veto a type when they appear in the same text as its
     * positive keyword — e.g. "First name" must not become FULL_NAME.
     */
    private val NEGATIVE: Map<FieldType, Set<String>> = mapOf(
        FieldType.FULL_NAME to setOf(
            "first", "last", "given", "family", "surname", "middle",
            "initial", "initials", "user", "company", "business",
            "organization", "org"
        )
    )

    /** Any of these tokens in the field's text means: never offer a fill. */
    private val EXCLUDED_TOKENS = setOf(
        "password", "passwd", "pwd", "otp", "cvv", "cvc", "csc"
    )

    private val WORD_SPLIT = Regex("[^a-z0-9]+")

    fun classify(signals: FieldSignals): FieldResult? {
        if (isExcluded(signals)) return null

        signals.autofillHint?.trim()?.lowercase()
            ?.let { FAST_HINTS[it] }
            ?.let { return FieldResult(it, 1.0) }

        val scores = HashMap<FieldType, MutableList<Double>>()
        fun add(type: FieldType, weight: Double) {
            scores.getOrPut(type) { mutableListOf() }.add(weight)
        }

        scan(signals.autofillHint, W_AUTOFILL_HINT, ::add)
        scan(signals.hintText, W_HINT_TEXT, ::add)
        scan(signals.idEntry, W_ID_ENTRY, ::add)
        scan(signals.labelText, W_LABEL, ::add)
        for (key in listOf("id", "name", "placeholder", "aria-label")) {
            scan(signals.htmlAttrs[key], W_HTML_ATTR, ::add)
        }

        signals.htmlAttrs["autocomplete"]?.lowercase()
            ?.split(Regex("\\s+"))
            ?.forEach { part -> AUTOCOMPLETE[part]?.let { add(it, W_AUTOCOMPLETE) } }

        when (signals.htmlAttrs["type"]?.lowercase()) {
            "email" -> add(FieldType.EMAIL, W_AUTOFILL_HINT)
            "tel" -> add(FieldType.PHONE, W_AUTOFILL_HINT)
        }

        inputTypeScores(signals.inputType, ::add)

        var best: FieldResult? = null
        for (type in FieldType.entries) {
            val hits = scores[type] ?: continue
            val score = min(1.0, hits.max() + CORROBORATION * (hits.size - 1))
            if (best == null || score > best.score) {
                best = FieldResult(type, score)
            }
        }
        return best?.takeIf { it.score >= MIN_SCORE }
    }

    private fun scan(
        text: String?,
        weight: Double,
        add: (FieldType, Double) -> Unit
    ) {
        if (text.isNullOrBlank()) return
        val toks = tokens(text)
        for ((type, positives) in POSITIVE) {
            if (toks.any { it in positives }) {
                val negatives = NEGATIVE[type]
                if (negatives != null && toks.any { it in negatives }) continue
                add(type, weight)
            }
        }
    }

    private fun inputTypeScores(inputType: Int, add: (FieldType, Double) -> Unit) {
        if (inputType == 0) return
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        when {
            cls == InputType.TYPE_CLASS_PHONE ->
                add(FieldType.PHONE, W_INPUT_TYPE_PHONE)
            cls == InputType.TYPE_CLASS_TEXT &&
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ->
                add(FieldType.EMAIL, W_INPUT_TYPE_EMAIL)
            cls == InputType.TYPE_CLASS_TEXT &&
                variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS ->
                add(FieldType.POSTAL_CODE, W_INPUT_TYPE_POSTAL)
            cls == InputType.TYPE_CLASS_NUMBER ->
                add(FieldType.POSTAL_CODE, W_INPUT_TYPE_NUMBER)
        }
    }

    private fun isExcluded(s: FieldSignals): Boolean {
        if (s.htmlAttrs["type"]?.lowercase() == "password") return true
        if (s.htmlAttrs["autocomplete"]?.contains("one-time-code", ignoreCase = true) == true) {
            return true
        }
        if (s.inputType != 0) {
            val cls = s.inputType and InputType.TYPE_MASK_CLASS
            val variation = s.inputType and InputType.TYPE_MASK_VARIATION
            val password = when (cls) {
                InputType.TYPE_CLASS_TEXT ->
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                InputType.TYPE_CLASS_NUMBER ->
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                else -> false
            }
            if (password) return true
        }
        val blob = listOfNotNull(
            s.hintText, s.labelText, s.idEntry, s.autofillHint,
            s.htmlAttrs["id"], s.htmlAttrs["name"], s.htmlAttrs["placeholder"]
        ).joinToString(" ")
        return tokens(blob).any { it in EXCLUDED_TOKENS }
    }

    private fun tokens(text: String): Set<String> =
        text.lowercase().split(WORD_SPLIT).filterTo(mutableSetOf()) { it.isNotEmpty() }
}
