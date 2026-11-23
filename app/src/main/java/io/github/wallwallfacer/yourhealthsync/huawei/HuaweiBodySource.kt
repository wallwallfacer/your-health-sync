package io.github.wallwallfacer.yourhealthsync.huawei

import android.content.Context
import com.huawei.hmf.tasks.Task
import com.huawei.hms.hihealth.HuaweiHiHealth
import com.huawei.hms.hihealth.data.DataType
import com.huawei.hms.hihealth.data.Field
import com.huawei.hms.hihealth.data.Value
import com.huawei.hms.hihealth.options.ReadOptions
import io.github.wallwallfacer.yourhealthsync.model.BodySample
import io.github.wallwallfacer.yourhealthsync.sync.BodySource
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Reads body-composition samples the user shared through Huawei Health Kit. */
class HuaweiBodySource(context: Context) : BodySource {
    private val controller = HuaweiHiHealth.getDataController(context.applicationContext)

    override suspend fun read(startMillis: Long, endMillis: Long): List<BodySample> {
        val options = ReadOptions.Builder()
            .read(DataType.DT_INSTANTANEOUS_BODY_WEIGHT)
            .read(DataType.DT_INSTANTANEOUS_BODY_FAT_RATE)
            .setTimeRange(startMillis, endMillis - 1, TimeUnit.MILLISECONDS)
            .build()
        val reply = controller.read(options).await()
        return reply.sampleSets
            .flatMap { it.samplePoints }
            .flatMap { point ->
                val fields = point.fieldValues
                    .mapNotNull { (name, value) -> value.toDoubleOrNull()?.let { name to it } }
                    .toMap()
                HuaweiFieldParser.parse(point.getStartTime(TimeUnit.MILLISECONDS), fields)
            }
    }

    private fun Value.toDoubleOrNull(): Double? = when {
        !isSet -> null
        format == Field.FORMAT_INT32 -> asIntValue().toDouble()
        format == Field.FORMAT_FLOAT -> asFloatValue().toDouble()
        format == Field.FORMAT_LONG -> asLongValue().toDouble()
        else -> null
    }
}

/** Bridges a Huawei Task to a coroutine. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
