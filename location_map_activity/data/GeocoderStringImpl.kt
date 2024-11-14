package data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.capstime.timecapsule.domain.AppDispatchers
import com.capstime.timecapsule.domain.GeocoderString
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import timber.log.Timber
import javax.inject.Inject

/*GeocoderStringImpl.kt
* 13.12.2023 Globa Lev*/
class GeocoderStringImpl @Inject constructor(
    @ActivityContext private val  context: Context,
    private val appDispatchers: AppDispatchers):
    GeocoderString {
    private val geocoder = Geocoder(context)
    private var job: Job? = null

    override fun stopJob() {
        job?.cancel()
    }

    @Suppress("DEPRECATION")
    override fun getAddress(
        queryString: String?,
        maxReuslt: Int,
        getAddress: (MutableList<Address>?) -> Unit,
        latitude: Double?,
        longitude: Double?
    ) {
        when {
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) -> {
                if (!queryString.isNullOrEmpty())
                    geocoder.getFromLocationName(queryString, maxReuslt) { address ->
                        Timber.tag("mapActivity")
                            .i("1 onQueryTextbutg : $address")
                        getAddress(address)
                    }
                else if (latitude != null && longitude != null)
                    geocoder.getFromLocation(latitude, longitude, maxReuslt) { address ->
                        Timber.tag("mapActivity")
                            .i("1 latitude, longitude : $address")
                        getAddress(address)
                    }
            }

            else -> {
                job?.cancel()
                job = CoroutineScope(appDispatchers.io).launch {
                    try {
                        yield()
                        val address = if (!queryString.isNullOrEmpty())
                            geocoder.getFromLocationName(queryString, maxReuslt)
                        else if (latitude != null && longitude != null)
                            geocoder.getFromLocation(latitude,longitude,maxReuslt)
                        else
                            null
                        Timber.tag("mapActivity")
                            .i("2 onQueryText $queryString: $address")
                        getAddress(address)
                    } catch (e: Throwable) {
                        Timber.tag("mapActivity")
                            .e("2 onQueryTextSubmit : $e")
                    }
                }
            }
        }
    }
}
