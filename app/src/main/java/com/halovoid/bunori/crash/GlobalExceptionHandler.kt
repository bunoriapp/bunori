package com.halovoid.bunori.crash

import android.content.Context
import android.content.Intent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

/*
Directly pulled over from tachiyomi source code
 */
class GlobalExceptionHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler,
    private val activityToBeLaunched: Class<*>
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            launchActivity(context, activityToBeLaunched, e)
        } catch (_: Exception) {
            defaultHandler.uncaughtException(t, e)
            return
        }
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(10)
    }

    private fun launchActivity(
        applicationContext: Context,
        activity: Class<*>,
        exception: Throwable
    ) {
        val intent = Intent(applicationContext, activity).apply {
            putExtra(INTENT_EXTRA, Json.encodeToString(ThrowableSerializer, exception))
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        applicationContext.startActivity(intent)
    }

    companion object {
        private const val INTENT_EXTRA = "exception"

        object ThrowableSerializer : KSerializer<Throwable> {
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("Throwable", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): Throwable =
                Throwable(message = decoder.decodeString())

            override fun serialize(encoder: Encoder, value: Throwable) =
                encoder.encodeString(value.stackTraceToString())
        }

        fun initialize(
            applicationContext: Context,
            activityToBeLaunched: Class<*>
        ) {
            val handler = GlobalExceptionHandler(
                applicationContext,
                Thread.getDefaultUncaughtExceptionHandler() as Thread.UncaughtExceptionHandler,
                activityToBeLaunched
            )
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        fun getThrowableFromIntent(intent: Intent): Throwable? {
            return try {
                Json.decodeFromString(ThrowableSerializer, intent.getStringExtra(INTENT_EXTRA)!!)
            } catch (e: Exception) {
                null
            }
        }
    }
}