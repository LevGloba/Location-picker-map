package com.capstime.timecapsule.presentation.location_map_activity

import android.annotation.SuppressLint
import android.location.Address
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstime.timecapsule.Constants
import com.capstime.timecapsule.delegates.view_models.LogEvent
import com.capstime.timecapsule.delegates.view_models.StateViewModel
import com.capstime.timecapsule.domain.SetText
import com.capstime.timecapsule.domain.interact.data_store.configuration.read.ReadNumberOfDeviationsLocationInteractor
import com.capstime.timecapsule.domain.interact.data_store.configuration.write.WriteNumberOfDeviationsLocationInteractor
import com.capstime.timecapsule.domain.map.InterfaceNumberOfDeviations
import com.capstime.timecapsule.domain.model.EnumAndroidWords
import com.capstime.timecapsule.domain.model.LoggerEvent
import com.capstime.timecapsule.extentions.launch
import com.capstime.timecapsule.presentation.location_map_activity.location_picker_activity_search_adapter.MapSearchObject
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/*LocationPickerViewModel.kt
* 13.12.2023 Globa Lev*/

data class Camera(
    val position: LatLng,
    val zoom: Float
)

data class LocationPickerUiStat(
    val cardInformationProgressBar: Boolean = false,
    val cardInformationSeen: Boolean = false,
    val btCardView: Boolean = false,
    val textViewAddress: String? = null,
    val textViewAddressCountry: String? = null,
    val oldMarker: Marker? = null,
    val arrayAddress: List<MapSearchObject> = emptyList(),
    val searchViewText: String? = null,
    val clearSearchView: Boolean = false,
    val position: LatLng = LatLng(
        Constants.UNITED_ARAB_EMIRATES_LATITUDE,
        Constants.UNITED_ARAB_EMIRATES_LONGITUDE
    ),
    val moveCamera: Camera = Camera(
        position, Constants.MAP_ZOOM_NORMAL
    ),
    val message: String? = null,
    val viewModelTryCount: Int = 0
)

@HiltViewModel
class LocationPickerViewModel @Inject constructor(
    setText: SetText,
    logEvent: LogEvent,
    stateViewModel: StateViewModel<LocationPickerUiStat>,
    private val readNumberOfDeviationsLocationInteractor: ReadNumberOfDeviationsLocationInteractor,
    private val writeNumberOfDeviationsLocationInteractor: WriteNumberOfDeviationsLocationInteractor
) : StateViewModel<LocationPickerUiStat> by stateViewModel,
    LogEvent by logEvent,
    SetText by setText,
    ViewModel(),
    InterfaceNumberOfDeviations {

    init {
        state = LocationPickerUiStat()

        launch {
            val count = readNumberOfDeviationsLocationInteractor.invoke()

            emitNewState{
                it.copy(
                    viewModelTryCount = count
                )
            }
        }
    }

    fun setPosition(address: Address?, lat: Double? = null, lon: Double? = null) = viewModelScope.launch {
        address?.let {
            logEvent(LoggerEvent.LOCATION_PICKER_SEARCH_SUCCESS)
            val position = LatLng(lat?: address.latitude, lon?: address.longitude)
            emitNewState {
                it.copy(
                    position = position,
                    moveCamera = Camera(
                        position,
                        Constants.MAP_ZOOM_NORMAL
                    )
                )
            }.also {
                getCardInformation(address)
            }
        }
        if (address == null) {
            logEvent(LoggerEvent.LOCATION_PICKER_SEARCH_FAIL)
            emitNewState {
                it.copy(
                    message = setText(EnumAndroidWords.MAP_ACTIVITY_RESULT_NOT_FOUND)
                )
            }
        }
    }

    fun loadCardInfo() = emitNewState {
            it.copy(
                cardInformationProgressBar = true,
                cardInformationSeen = true,
                btCardView = false
            )
        }

    fun stopLoadCardInfo() =  emitNewState {
        it.copy(
            cardInformationProgressBar = false,
            btCardView = true
        )
    }

    override fun setTheNumberOfDeviations(count: Int) {
        launch {
            writeNumberOfDeviationsLocationInteractor.invoke(count)
        }
    }

    /*fun tapToScreenMap(latitude: Double, longitude: Double) = viewModelScope.launch {
        loadCardInfo()
        _uiState.update {
            val position = LatLng(latitude, longitude)
            it.copy(
                moveCamera = CameraUpdateFactory.newLatLngZoom(position, Constants.MAP_ZOOM_NORMAL),
            )
        }
    }*/

    /*fun onQueryTextSubmit(query: String) = viewModelScope.launch {
        geocoderString.getAddress(
            queryString = query,
            getAddress = { address: MutableList<Address> -> setPosition(address.first()) }
        ).also {
            loadCardInfo()
        }
    }

    fun onQueryTextChange(newText: String) = viewModelScope.launch {
        geocoderString.getAddress(
            queryString = newText,
            maxReuslt = 4,
            getAddress = { mutableListAddress: MutableList<Address> ->
                createRecyclerViewModel(
                    mutableListAddress
                )
            }
        )
    }*/

    @SuppressLint("SuspiciousIndentation")
    fun createRecyclerViewModel(address: List<Address>?) =
        launch {
            address?.let {
                logEvent(LoggerEvent.LOCATION_PICKER_SEARCH_SUCCESS)
                val ad = mutableListOf<MapSearchObject>()
                    address.forEach {
                        ad += MapSearchObject(
                            description = "${it.adminArea}, ${it.featureName}",
                            address = it,
                            onCallback = { address: Address -> setPosition(address) },
                            clearAutofocusesViewAndHideKeyboard = { clearAutofocusesView() }
                        )
                    }
                Timber.tag("mapActivity").i("createRecyclerView :  $ad")
                if (ad.size > 0 && (ad != uiState.value.arrayAddress)) {
                    emitNewState {
                        it.copy(
                            arrayAddress = ad,
                            clearSearchView = false
                        )
                    }
                }
            }
            if (address != null)
                logEvent(LoggerEvent.LOCATION_PICKER_SEARCH_FAIL)
        }

    fun seekCardInformation() = launch {
        emitNewState {
            it.copy(
                cardInformationSeen = false
            )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun getCardInformation(value: Address) = viewModelScope.launch {
        val latitude = value.latitude
        val longitude = value.longitude
        val admin = value.adminArea ?: latitude
        val locatity = value.locality ?: longitude
        val thoroughfare = value.thoroughfare ?: locatity
        val featueName = value.featureName ?: admin
        val subAdmin = value.subAdminArea ?: locatity
        val countryName = value.countryName ?: subAdmin
        val postalCode = value.postalCode ?: admin
        val streatHouse =
            if (thoroughfare != latitude && featueName != longitude) "$thoroughfare, $featueName" else "$latitude\n$longitude"
        val dobInfa = "$countryName\n$postalCode"
        emitNewState {
            it.copy(
                cardInformationSeen = true,
                cardInformationProgressBar = false,
                textViewAddress = streatHouse,
                textViewAddressCountry = dobInfa,
                btCardView = true
            )
        }
    }

    fun clearAutofocusesView() = launch {
        emitNewState {
            it.copy(
                searchViewText = "",
                clearSearchView = true,
                arrayAddress = emptyList()
            )
        }
    }


    fun clearReyclerView() = launch {
        emitNewState {
            it.copy(
                arrayAddress = emptyList()
            )
        }
    }

    fun saveSearchText(searchText: String) = launch {
        emitNewState {
            it.copy(
                searchViewText = searchText
            )
        }
    }

    fun setMarker(marker: Marker?) = launch {
        emitNewState {
            it.copy(
                oldMarker = marker
            )
        }
    }

    fun clearMessage() = launch {
        emitNewState {
            it.copy(
                message = null
            )
        }
    }
}
