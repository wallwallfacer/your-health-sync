package io.github.wallwallfacer.yourhealthsync.huawei

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.huawei.hms.hihealth.HuaweiHiHealth
import com.huawei.hms.hihealth.data.Scopes
import io.github.wallwallfacer.yourhealthsync.BuildConfig

/** Huawei Health Kit authorization: read-only body data plus one year of history. */
object HuaweiAuth {
    private val scopes = arrayOf(
        Scopes.HEALTHKIT_HEIGHTWEIGHT_READ,
        Scopes.HEALTHKIT_BODYFAT_READ,
        Scopes.HEALTHKIT_HISTORYDATA_OPEN_YEAR,
    )

    /** Intent that shows Huawei's consent screen; start it for a result. */
    fun requestIntent(activity: Activity): Intent =
        HuaweiHiHealth.getSettingController(activity).requestAuthorizationIntent(scopes, true)

    fun isSuccess(activity: Activity, data: Intent?): Boolean =
        data != null && HuaweiHiHealth.getSettingController(activity).parseHealthKitAuthResultFromIntent(data)?.isSuccess == true

    /** Withdraws every scope this app holds. */
    suspend fun revoke(context: Context) {
        HuaweiHiHealth.getConsentsController(context).cancelAuthorization(BuildConfig.HUAWEI_APP_ID).await()
    }
}
