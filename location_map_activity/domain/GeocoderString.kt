package domain

import android.location.Address


/*GeocoderString.kt
* 13.12.2023 Globa Lev*/
interface GeocoderString {
    fun getAddress(
        queryString: String? = null,
        maxReuslt: Int = 1,
        getAddress: (MutableList<Address>?) -> Unit,
        latitude: Double? = null,
        longitude: Double? = null,
    )

    fun stopJob()
}
