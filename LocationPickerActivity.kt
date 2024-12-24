package com.capstime.timecapsule.presentation.location_map_activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.capstime.timecapsule.Constants
import com.capstime.timecapsule.R
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.capstime.timecapsule.databinding.ActivityMapsBinding
import com.capstime.timecapsule.domain.AlertDialogManager
import com.capstime.timecapsule.domain.AppDispatchers
import com.capstime.timecapsule.domain.CheckVersionOSWhenCreateFragment
import com.capstime.timecapsule.domain.GeocoderString
import com.capstime.timecapsule.domain.RemoteConfigGetVariable
import com.capstime.timecapsule.domain.ResourceMapper
import com.capstime.timecapsule.domain.model.EnumAndroidWords
import com.capstime.timecapsule.domain.model.LoggerEvent
import com.capstime.timecapsule.extentions.buildRecyclerView
import com.capstime.timecapsule.extentions.hideKeyBoard
import com.capstime.timecapsule.extentions.showError
import com.capstime.timecapsule.extentions.showKeyBoard
import com.capstime.timecapsule.presentation.location_map_activity.location_picker_activity_search_adapter.MapSearchAdapter
import com.capstime.timecapsule.presentation.location_map_activity.location_picker_activity_search_adapter.MapSearchObject
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.Marker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.NullPointerException
import javax.inject.Inject

/*LocationPickerActivity.kt
* 08.12.2023 Globa Lev*/

/*Класс для выбора геопозиции*/
@AndroidEntryPoint
class LocationPickerActivity : AppCompatActivity(), OnMapReadyCallback, CheckVersionOSWhenCreateFragment {


    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    private val locationPickerViewModel by viewModels<LocationPickerViewModel>()

    @Inject
    lateinit var remoteConfigGetVariable: RemoteConfigGetVariable

    private var locationPermissionRequest: ActivityResultLauncher<Array<String>>? = null

    @Inject
    lateinit var resourceMapper: ResourceMapper
    @Inject
    lateinit var appDispatchers: AppDispatchers
    @Inject
    lateinit var alertDialogManager: AlertDialogManager
    @Inject
    lateinit var geocoderString: GeocoderString

    private var resultPosition: LatLng? = null

    private var locationAddress: String? = null

    private var oldMarker: Marker? = null

    private var tryCount = 0

    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private var fusedLocationClient: FusedLocationProviderClient? = null

    /*Создает активити*/
    override fun onCreate(savedInstanceState: Bundle?) {
        checkOSfrNavigationBarTurnOn()

        super.onCreate(savedInstanceState)
        locationPickerViewModel.logEvent(LoggerEvent.LOCATION_PICKER_SCREEN_IS_OPEN)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@LocationPickerActivity)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.activity_container_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        /*Запрашивает разрешение на геопозицию пользователя и проверяет*/
        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    settingUserLocation()
                }

                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    settingUserLocation()
                }

                else -> {
                    // No location access granted.
                    locationPickerViewModel.logEvent(LoggerEvent.LOCATION_PICKER_PERMISSION_TO_POSITION_FAIL)
                    Timber.tag("mapActivity").i("locationPermissionRequest: false")

                    if (tryCount >= 2)
                        alertDialogManager.createShowPermissionMap(this)
                    else
                        tryCount ++

                }
            }
        }
        initBinding()
        geocoderString.getAddress(
            queryString = null, getAddress = { mutableList ->
                locationPickerViewModel.setPosition(
                    mutableList?.firstOrNull(), Constants.UNITED_ARAB_EMIRATES_LATITUDE,
                    Constants.UNITED_ARAB_EMIRATES_LONGITUDE
                )
            },
            latitude = Constants.UNITED_ARAB_EMIRATES_LATITUDE,
            longitude = Constants.UNITED_ARAB_EMIRATES_LONGITUDE
        )
    }

    override fun onDestroy() {
        geocoderString.stopJob()
        fusedLocationClient = null
        locationPickerViewModel.run {
            setMarker(oldMarker)
            saveSearchText(binding.mapActivitySearchView.query.toString())
        }
        checkOSfrNavigationBarTurnOf()
        locationPickerViewModel.setTheNumberOfDeviations(tryCount)
        super.onDestroy()
    }

    /*Устанавливает текст из remote config для полей*/
    private fun initBinding() {
        binding.run {
            mapActivitySearchView.queryHint = remoteConfigGetVariable.rcGetString(EnumAndroidWords.MAP_ACTIVITY_SEARCH)
            btUseThisLocation.text = remoteConfigGetVariable.rcGetString(EnumAndroidWords.MAP_ACTIVITY_USE_THIS_LOCATION)
            ViewCompat.setOnApplyWindowInsetsListener(mapActivitySearchView) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = insets.top + 10
                }
                WindowInsetsCompat.CONSUMED
            }
            ViewCompat.setOnApplyWindowInsetsListener(cardInformationActivityMap) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = insets.bottom + 20
                }
                WindowInsetsCompat.CONSUMED
            }
            ViewCompat.setOnApplyWindowInsetsListener(btMyLocation) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = insets.bottom + 20
                }
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    /*Отслеживает взаимодействие пользователя с экраном*/
    private fun initTouch() {


        mMap.setOnMapClickListener {
            locationPickerViewModel.logEvent(LoggerEvent.LOCATION_PICKER_TAP_ON_MAP)
            locationPickerViewModel.run {
                clearReyclerView()
                loadCardInfo()
                geocoderString.getAddress(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    getAddress = { address: MutableList<Address>? -> setPosition(address?.firstOrNull(), it.latitude, it.longitude) }
                )
            }
            this@LocationPickerActivity.hideKeyBoard()
        }
        mMap.setOnCameraMoveListener {
            this@LocationPickerActivity.hideKeyBoard()
        }
        mMap.setOnMyLocationClickListener {
            myLocation()
        }
        binding.run {
            btCloseCardInformation.setOnClickListener {
                locationPickerViewModel.seekCardInformation()
            }
            btUseThisLocation.setOnClickListener {
                locationPickerViewModel.logEvent(LoggerEvent.LOCATION_PICKER_BUTTON_USE_THIS_LOCATION_CLICK)
                if (resultPosition != null) {
                    val intent = Intent().also {
                        it.putExtra(Constants.LATITUDE, resultPosition?.latitude)
                        it.putExtra(Constants.LONGITUDE, resultPosition?.longitude)
                        it.putExtra(Constants.LOCATION_ADDRESS, locationAddress)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                } else
                    showError(locationPickerViewModel.setText(EnumAndroidWords.LOCATION_PICKER_FAILED_TO_GET_LOCATION))
            }
            mapActivitySearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

                /*Проверяет что ввел пользователь в поле searchView и ищет гео данные о месте */
                override fun onQueryTextSubmit(query: String?): Boolean {
                    val location = mapActivitySearchView.query.toString()
                    if (location.isNotBlank()) {
                        locationPickerViewModel.logEvent(LoggerEvent.LOCATION_PICKER_SEARCHING)
                        Timber.tag("mapActivity").i("1 onQueryTextSubmit : $query\n$location")
                        locationPickerViewModel.run {
                            loadCardInfo()
                            clearAutofocusesView()
                            clearReyclerView()
                            geocoderString.getAddress(
                                queryString = location,
                                getAddress = { addresses: MutableList<Address>? ->
                                    setPosition(
                                        addresses?.firstOrNull()
                                    )
                                }
                            )
                            this@LocationPickerActivity.hideKeyBoard()
                        }
                    }
                    return false
                }

                /*Отслеживает каждый введеный  символ и ищет информацию*/
                override fun onQueryTextChange(newText: String?): Boolean {
                    if ((newText?.length ?: 0) >= 2 && newText?.isNotBlank() == true) {
                        Timber.tag("mapActivity").e("onQueryTextChange : $newText")
                        locationPickerViewModel.logEvent(LoggerEvent.LOCATION_PICKER_SEARCHING)
                        geocoderString.getAddress(
                            queryString = newText,
                            maxReuslt = 4,
                            getAddress = { mutableListAddress: MutableList<Address>? ->
                                locationPickerViewModel.createRecyclerViewModel(
                                    mutableListAddress
                                )
                            }
                        )
                    }
                    return false
                }
            })
            mapActivitySearchView.findViewById<ImageView>(R.id.search_mag_icon).setOnClickListener {
                locationPickerViewModel.logEvent(LoggerEvent.LOCATION_PICKER_BUTTON_SEARCH_CLICK_ON)
                showKeyBoard(mapActivitySearchView.findViewById(R.id.search_src_text))
            }
            mapActivitySearchView.findViewById<ImageView>(R.id.search_close_btn).setOnClickListener {
                Timber.tag("mapActivity").e("closeSearch")
                locationPickerViewModel.run {
                    clearAutofocusesView()
                }
            }

            btMyLocation.setOnClickListener {
                locationPickerViewModel.logEvent(LoggerEvent.LOCATION_PICKER_GEO_POSITION_CLICK)
                Timber.tag("mapActivity").e("bt_my_location")
                locationPickerViewModel.clearReyclerView()
                myLocation()
                this@LocationPickerActivity.hideKeyBoard()
            }
        }
    }

    /*Проверяет, предоставил ли пользователь все разрешения*/
    @SuppressLint("SuspiciousIndentation")
    private fun settingUserLocation() {
        if ((ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) || (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED)
        ) {
            locationPickerViewModel.logEvent(LoggerEvent.LOCATION_PICKER_PERMISSION_TO_POSITION_SUCCESS)
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = false
            mMap.uiSettings.isCompassEnabled = false
            myLocation()
        } else {
            locationPickerViewModel.logEvent(LoggerEvent.LOCATION_PICKER_SHOW_PERMISSION_TO_POSITION)
            locationPermissionRequest?.launch(permissions)
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    /*Функция callBack, срабатывает когда google map готова к работе*/
    override fun onMapReady(googleMap: GoogleMap) {
        Timber.tag("mapActivity").d("map is ready: $googleMap")
        mMap = googleMap
        settingUserLocation()
        initTouch()
        observer()
    }

    private fun observer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
                locationPickerViewModel.uiState.collect { locationPickerUiStat ->
                    Timber.tag("mapActivity").d("uiStat update: $locationPickerUiStat")
                    resultPosition = locationPickerUiStat.position

                    tryCount = locationPickerUiStat.viewModelTryCount
                    binding.run {
                        cardInformationActivityMap.isVisible =
                            locationPickerUiStat.cardInformationSeen
                        mapActivityProgressBar.isVisible =
                            locationPickerUiStat.cardInformationProgressBar
                        btUseThisLocation.isVisible = !locationPickerUiStat.cardInformationProgressBar
                        btCloseCardInformation.isVisible = locationPickerUiStat.btCardView

                        textViewAdress.run {
                            text = locationPickerUiStat.textViewAddress
                            isVisible = !locationPickerUiStat.cardInformationProgressBar
                        }
                        textViewAdressCountry.run {
                            text = locationPickerUiStat.textViewAddressCountry
                            isVisible = !locationPickerUiStat.cardInformationProgressBar
                        }

                        if (!locationPickerUiStat.searchViewText.isNullOrEmpty() || locationPickerUiStat.clearSearchView)
                            mapActivitySearchView.setQuery(locationPickerUiStat.searchViewText, false)
                        if (locationPickerUiStat.clearSearchView)
                            mapActivitySearchView.clearFocus()
                    }
                    locationAddress = locationPickerUiStat.textViewAddress
                    val cameraUpdate = CameraUpdateFactory.newLatLngZoom(locationPickerUiStat.moveCamera.position, locationPickerUiStat.moveCamera.zoom)
                    mMap.animateCamera(cameraUpdate)
                    if ((oldMarker != locationPickerUiStat.oldMarker) || locationPickerUiStat.oldMarker == null) {
                        oldMarker?.remove()
                        oldMarker = addMarker(locationPickerUiStat.position)
                    }
                    if (locationPickerUiStat.arrayAddress.isNotEmpty()) {
                        binding.mapActivityRecyclerView.isVisible = true
                        createRecyclerView(locationPickerUiStat.arrayAddress)
                    }
                    else
                        binding.mapActivityRecyclerView.isVisible = false
                    if (!locationPickerUiStat.message.isNullOrEmpty()) {
                        Toast.makeText(
                            this@LocationPickerActivity,
                            locationPickerUiStat.message,
                            Toast.LENGTH_LONG
                        ).show()
                        locationPickerViewModel.clearMessage()
                    }
                }
            }
        }
    }

    /*Создает список вариантов того, что вел ппользователь*/
    @SuppressLint("NotifyDataSetChanged")
    private fun createRecyclerView(listAddress: List<MapSearchObject>) {
        MapSearchAdapter(listAddress).also { mapSearchAdapter ->
            Timber.tag("mapActivity").i("createRecyclerView :  create recyclerView")
            binding.mapActivityRecyclerView.buildRecyclerView(
                adapter = mapSearchAdapter,
                itemViewCacheSize = 15,
                fixSize = false,
            )
            mapSearchAdapter.notifyDataSetChanged()
            Timber.tag("mapActivity").i("createRecyclerView : visibility")
        }
    }

    /*Добавляет маркер на карте и двигает камеру к маркеру*/
    private fun addMarker(position: LatLng): Marker? = mMap.addMarker(MarkerOptions().position(position))


    private fun myLocation() {
        if (ActivityCompat.checkSelfPermission(
                this@LocationPickerActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this@LocationPickerActivity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
            fusedLocationClient?.lastLocation?.addOnSuccessListener {
                locationPickerViewModel.run {
                    loadCardInfo()
                    try {
                        if ((getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(
                                LocationManager.GPS_PROVIDER
                            )
                        ) {
                            val lat = it.latitude
                            val lon = it.longitude
                            geocoderString.getAddress(
                                latitude = lat,
                                longitude = lon,
                                getAddress = { address: MutableList<Address>? ->
                                    setPosition(
                                        address?.firstOrNull(),
                                        lat,
                                        lon
                                    )
                                }
                            )
                        } else {
                            stopLoadCardInfo()
                            alertDialogManager.createGPSIsDisable(this@LocationPickerActivity)
                        }
                    } catch (e: NullPointerException) {
                        stopLoadCardInfo()
                        showError(setText(EnumAndroidWords.LOCATION_PICKER_FAILED_TO_GET_LOCATION))
                    }
                }
            }
        else
            locationPermissionRequest?.launch(permissions)
    }

    @Suppress("DEPRECATION")
    override fun checkOSfrNavigationBarTurnOn() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            window.run {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                navigationBarColor = resources.getColor(R.color.navigation_bar)
            }
        } else
            enableEdgeToEdge()
    }

    @Suppress("DEPRECATION")
    override fun checkOSfrNavigationBarTurnOf() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            enableEdgeToEdge()
            window.navigationBarColor = resources.getColor(R.color.navigation_bar)
        }
    }

}
