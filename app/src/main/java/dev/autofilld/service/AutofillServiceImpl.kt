package dev.autofilld.service

import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import dev.autofilld.App
import dev.autofilld.fill.FieldClassifier
import dev.autofilld.fill.FieldType
import dev.autofilld.fill.ResponseBuilder
import dev.autofilld.fill.StructureWalker
import kotlinx.coroutines.runBlocking

/**
 * Main Android Autofill Service implementation for autofilld.
 * Dispatches fill and save requests to background executor and connects
 * structure traversal, heuristic classification, and Room profile storage.
 */
class AutofillServiceImpl : AutofillService() {

    companion object {
        private const val TAG = "Autofilld"
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val isCompat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (request.flags and FillRequest.FLAG_COMPATIBILITY_MODE_REQUEST) != 0
        } else {
            false
        }

        val app = applicationContext as? App
        if (app == null) {
            callback.onSuccess(null)
            return
        }

        app.ioExecutor.execute {
            try {
                if (cancellationSignal.isCanceled) return@execute

                val profile = runBlocking { app.profileRepository.getProfile() }
                val walkResult = StructureWalker.walk(structure, isCompat)

                if (walkResult.isWebView) {
                    Log.i(TAG, "Fill request from WebView / compat mode (domain=${walkResult.webDomain})")
                }

                val spec = ResponseBuilder.buildSpec(walkResult.candidates, profile)
                if (spec == null || spec.isEmpty) {
                    callback.onSuccess(null)
                } else {
                    val fillResponse = ResponseBuilder.buildFillResponse(spec, packageName)
                    callback.onSuccess(fillResponse)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling onFillRequest", e)
                callback.onFailure(e.message)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val app = applicationContext as? App
        if (app == null) {
            callback.onSuccess()
            return
        }

        app.ioExecutor.execute {
            try {
                val contexts = request.fillContexts
                val updates = mutableMapOf<FieldType, String>()

                for (ctx in contexts) {
                    val walkResult = StructureWalker.walk(ctx.structure)
                    for (candidate in walkResult.candidates) {
                        val rawText = candidate.text?.trim()
                        if (!rawText.isNullOrEmpty()) {
                            val classification = FieldClassifier.classify(candidate.signals)
                            if (classification != null) {
                                updates[classification.type] = rawText
                                Log.i(
                                    TAG,
                                    "Save detected field: type=${classification.type}, domain=${walkResult.webDomain}"
                                )
                            }
                        }
                    }
                }

                if (updates.isNotEmpty()) {
                    runBlocking { app.profileRepository.applyUpdates(updates) }
                    Log.i(TAG, "Successfully updated ${updates.size} profile fields: ${updates.keys}")
                }

                callback.onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling onSaveRequest", e)
                callback.onFailure(e.message)
            }
        }
    }

    override fun onConnected() {
        super.onConnected()
        Log.i(TAG, "AutofillService connected")
    }

    override fun onDisconnected() {
        super.onDisconnected()
        Log.i(TAG, "AutofillService disconnected")
    }
}
