package io.github.wallwallfacer.yourhealthsync.huawei

import android.content.Context
import android.util.Log
import com.huawei.hmf.tasks.Task
import com.huawei.hms.hihealth.HuaweiHiHealth
import com.huawei.hms.hihealth.data.DataType
import com.huawei.hms.hihealth.data.Field
import com.huawei.hms.hihealth.data.SamplePoint
import com.huawei.hms.hihealth.data.Value
import com.huawei.hms.hihealth.options.ReadOptions
import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.sync.BodySource
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Reads body-composition samples the user shared through Huawei Health Kit. */
class HuaweiBodySource(context: Context) : BodySource {
    private val controller = HuaweiHiHealth.getDataController(context.applicationContext)

    /**
     * Each data type is read on its own, so a missing body-fat scope does not
     * block weight. Throws only when every type fails.
     */
    override suspend fun read(startMillis: Long, endMillis: Long): List<BodySample> {
        val samples = mutableListOf<BodySample>()
        var firstError: Exception? = null
        var anySucceeded = false
        for (type in TYPES) {
            try {
                samples += readType(type, startMillis, endMillis)
                anySucceeded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Reading ${type.name} failed", e)
                if (firstError == null) firstError = e
            }
        }
        if (!anySucceeded && firstError != null) throw firstError
        return samples
    }

    private suspend fun readType(type: DataType, startMillis: Long, endMillis: Long): List<BodySample> {
        val options = ReadOptions.Builder()
            .read(type)
            .setTimeRange(startMillis, endMillis - 1, TimeUnit.MILLISECONDS)
            .build()
        val reply = controller.read(options).await()
        return reply.sampleSets.flatMap { it.samplePoints }.flatMap { point ->
            Log.d(TAG, "raw point: $point")
            val fields = point.fieldValues
                .mapNotNull { (name, value) -> value.toDoubleOrNull()?.let { name to it } }
                .toMap()
            HuaweiFieldParser.parse(point.timeMillis(), fields)
        }
    }

    // Instantaneous points carry a sampling time; fall back to start time on older data.
    private fun SamplePoint.timeMillis(): Long =
        getSamplingTime(TimeUnit.MILLISECONDS).takeIf { it > 0 } ?: getStartTime(TimeUnit.MILLISECONDS)

    private fun Value.toDoubleOrNull(): Double? = when {
        !isSet -> null
        format == Field.FORMAT_INT32 -> asIntValue().toDouble()
        // Via toString so 72.4f becomes 72.4, not 72.40000152587891.
        format == Field.FORMAT_FLOAT -> asFloatValue().toString().toDouble()
        format == Field.FORMAT_LONG -> asLongValue().toDouble()
        else -> null
    }

    private companion object {
        const val TAG = "HuaweiBodySource"
        val TYPES = listOf(DataType.DT_INSTANTANEOUS_BODY_WEIGHT, DataType.DT_INSTANTANEOUS_BODY_FAT_RATE)
    }
}

/** Bridges a Huawei Task to a coroutine. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
