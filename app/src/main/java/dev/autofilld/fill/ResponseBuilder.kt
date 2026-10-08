package dev.autofilld.fill

import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import dev.autofilld.R
import dev.autofilld.data.Profile
import dev.autofilld.data.valueFor

/**
 * A classified field candidate paired with its detected type and profile value.
 */
data class ClassifiedField(
    val candidate: FieldCandidate,
    val type: FieldType,
    val valueToFill: String
)

/**
 * Pure Kotlin descriptor of a fill response, enabling unit test verification on plain JVM.
 */
data class FillResponseSpec(
    val fieldsToFill: List<ClassifiedField>,
    val saveTypeFlags: Int,
    val requiredIds: List<AutofillId?>,
    val optionalIds: List<AutofillId?>
) {
    val isEmpty: Boolean get() = fieldsToFill.isEmpty()
}

/**
 * Constructs autofill responses and save info configurations from field candidates and user profile.
 */
object ResponseBuilder {

    const val DEFAULT_SAVE_FLAGS =
        SaveInfo.SAVE_DATA_TYPE_GENERIC or
            SaveInfo.SAVE_DATA_TYPE_ADDRESS or
            SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS

    /**
     * Builds a pure Kotlin response spec by classifying candidate fields against the user profile.
     * Returns null if no candidates match or if all matching profile fields are blank.
     */
    fun buildSpec(
        candidates: List<FieldCandidate>,
        profile: Profile
    ): FillResponseSpec? {
        val matchedFields = mutableListOf<ClassifiedField>()

        for (candidate in candidates) {
            val result = FieldClassifier.classify(candidate.signals) ?: continue
            val value = profile.valueFor(result.type).trim()
            if (value.isNotEmpty()) {
                matchedFields.add(ClassifiedField(candidate, result.type, value))
            }
        }

        if (matchedFields.isEmpty()) return null

        val ids = matchedFields.mapNotNull { it.candidate.autofillId }
        val requiredIds = if (ids.isNotEmpty()) listOf(ids.first()) else emptyList()
        val optionalIds = if (ids.size > 1) ids.drop(1) else emptyList()

        return FillResponseSpec(
            fieldsToFill = matchedFields,
            saveTypeFlags = DEFAULT_SAVE_FLAGS,
            requiredIds = requiredIds,
            optionalIds = optionalIds
        )
    }

    /**
     * Converts a [FillResponseSpec] into an Android framework [FillResponse] at runtime.
     */
    fun buildFillResponse(
        spec: FillResponseSpec,
        packageName: String
    ): FillResponse? {
        if (spec.isEmpty) return null

        val datasetBuilder = Dataset.Builder()
        var hasValues = false

        for (field in spec.fieldsToFill) {
            val id = field.candidate.autofillId ?: continue
            val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                setTextViewText(R.id.autofill_item_text, field.valueToFill)
            }
            datasetBuilder.setValue(
                id,
                AutofillValue.forText(field.valueToFill),
                presentation
            )
            hasValues = true
        }

        if (!hasValues) return null

        val responseBuilder = FillResponse.Builder()
            .addDataset(datasetBuilder.build())

        val nonNullRequired = spec.requiredIds.filterNotNull().toTypedArray()
        if (nonNullRequired.isNotEmpty()) {
            val saveInfoBuilder = SaveInfo.Builder(spec.saveTypeFlags, nonNullRequired)
            val nonNullOptional = spec.optionalIds.filterNotNull().toTypedArray()
            if (nonNullOptional.isNotEmpty()) {
                saveInfoBuilder.setOptionalIds(nonNullOptional)
            }
            responseBuilder.setSaveInfo(saveInfoBuilder.build())
        }

        return responseBuilder.build()
    }
}
