package com.example.weatherapptraining.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.weatherapptraining.R
import com.example.weatherapptraining.models.WeatherResponse
import com.example.weatherapptraining.network.WeatherService
import com.example.weatherapptraining.utils.Constants
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity: AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var progressDialog: Dialog? = null

    private var latitude: Double = 0.0
    // A global variable for Current Longitude
    private var longitude: Double = 0.0

    private lateinit var sharedPreferences: SharedPreferences

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Enciende tu proveedor de localización.",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {

            Dexter.withActivity(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "se requiere permisos de localización.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {R.id.action_refresh -> {
                getLocationWeatherDetails()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun isLocationEnabled(): Boolean {

        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }


    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("Para volver a dar permisos,ir a herramientas")
            .setPositiveButton(
                "IR A HERRAMIENTAS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("paquete", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancelado") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }
    // END


    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        fusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }


    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation: Location = locationResult.lastLocation
            val latitude = lastLocation.latitude
            Log.i("Latitud actual", "$latitude")

            val longitude = lastLocation.longitude
            Log.i("Longitud actual", "$longitude")

            getLocationWeatherDetails()
        }
    }
    private fun getLocationWeatherDetails(){

        if (Constants.isNetworkAvailable(this@MainActivity)) {

            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                @RequiresApi(Build.VERSION_CODES.N)
                @SuppressLint("SetTextI18n")
                override fun onResponse(
                    response: Response<WeatherResponse>,
                    retrofit: Retrofit
                ) {

                    if (response.isSuccess) {

                        hideProgressDialog()

                        val weatherList: WeatherResponse = response.body()
                        Log.i("Respuesta Resultado", "$weatherList")

                        val weatherResponseJsonString = Gson().toJson(weatherList)

                        val editor = sharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()
                    } else {
                        val sc = response.code()
                        when (sc) {
                            400 -> {
                                Log.e("Error 400", "Mala Petición")
                            }
                            404 -> {
                                Log.e("Error 404", "No encontrado")
                            }
                            else -> {
                                Log.e("Error", "error general")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable) {
                    hideProgressDialog()
                    Log.e("Errorrrrr", t.message.toString())
                }
            })
        } else {
            Toast.makeText(
                this@MainActivity,
                "No hay conexion a internet disponible.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private fun showCustomProgressDialog() {
        progressDialog = Dialog(this)


        progressDialog!!.setContentView(R.layout.dialog_custom_progress)

        progressDialog!!.show()
    }


    private fun hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog!!.dismiss()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupUI() {
        val weatherResponseJsonString =
            sharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")
        if (!weatherResponseJsonString.isNullOrEmpty()) {

            val weatherList =
                Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (z in weatherList.weather.indices) {
                Log.i("NAMEEEEEEEE", weatherList.weather[z].main)

                tv_main.text = weatherList.weather[z].main
                tv_main_description.text = weatherList.weather[z].description
                tv_temp.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                tv_humidity.text = weatherList.main.humidity.toString() + " per cent"
                tv_min.text = weatherList.main.tempMin.toString() + " min"
                tv_max.text = weatherList.main.tempMax.toString() + " max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country
                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise.toLong())
                tv_sunset_time.text = unixTime(weatherList.sys.sunset.toLong())

                when (weatherList.weather[z].icon) {
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                }
            }
        }
    }



    private fun getUnit(value: String): String? {
        Log.i("unitttttt", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }


    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat") val sdf =
            SimpleDateFormat("HH:mm:ss")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }


}