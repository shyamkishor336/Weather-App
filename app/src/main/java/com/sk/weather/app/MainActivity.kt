package com.sk.weather.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.location.*
import com.sk.weather.app.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.RoundingMode
import java.sql.Date
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        supportActionBar?.hide()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        binding.mainLayout.visibility = View.GONE
        getCurrentLocation();
        binding.searchPlaces.setOnEditorActionListener({ v, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                getCityWeather(binding.searchPlaces.text.toString())
                val view = this.currentFocus
                if (view != null) {
                    val imm: InputMethodManager =
                        getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                    binding.searchPlaces.clearFocus()
                }
                true
            } else false
        })
    }

    private fun getCityWeather(cityName: String) {
        binding.pbLoading.visibility = View.VISIBLE
        ApiUtilities.getApiInterface()?.getCityWeatherData(cityName, API_KEY)?.enqueue(object : Callback<ModelClass>{
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResponse(call: Call<ModelClass>, response: Response<ModelClass>) {
              setDataOnViews(response.body())
            }

            override fun onFailure(call: Call<ModelClass>, t: Throwable) {
                Toast.makeText(applicationContext, "Not a valid city name", Toast.LENGTH_SHORT).show()

            }

        })

    }


    private fun getCurrentLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermission()
                    return
                }
                fusedLocationProviderClient.lastLocation.addOnCompleteListener(this) { task ->
                    val location: Location? = task.result
                    if (location == null) {
                        setLocationListner()
                        Toast.makeText(this, "Null location", Toast.LENGTH_SHORT).show()
                    } else {
//                        Toast.makeText(this, "Get Location Successfully!", Toast.LENGTH_SHORT)
//                            .show()
//fetch current location weather
                        fetchCurrentLocationWeather(
                            location.latitude.toString(),
                            location.longitude.toString()
                        )
                    }
                }
            } else {
                Toast.makeText(this, "Turn on location", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
                //setting open here
            }
        } else {
            requestPermission()

            //request permission here
        }
    }
    private fun setLocationListner() {
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        val locationRequest = LocationRequest().setInterval(5000).setFastestInterval(2000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    for (location in locationResult.locations) {
                        fetchCurrentLocationWeather(
                            location.latitude.toString(),
                            location.longitude.toString()
                        )
                    }
                }
            },
            Looper.myLooper()!!
        )
    }

    private fun fetchCurrentLocationWeather(latitude: String, longitude: String) {
        binding.pbLoading.visibility = View.VISIBLE
        ApiUtilities.getApiInterface()?.getCurrentWeatherData(latitude, longitude, API_KEY)
            ?.enqueue(object :
                Callback<ModelClass> {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onResponse(call: Call<ModelClass>, response: Response<ModelClass>) {
                    if (response.isSuccessful) {
                        setDataOnViews(response.body())
                    }
                }

                override fun onFailure(call: Call<ModelClass>, t: Throwable) {
                    Toast.makeText(applicationContext, "ERROR", Toast.LENGTH_SHORT).show()

                }

            })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setDataOnViews(body: ModelClass?) {
        val sdf = SimpleDateFormat("MMM d, yyyy  hh:mm aaa")
        val currentDate = sdf.format(java.util.Date())
        binding.tvDateAndTime.text = currentDate
        binding.tvDayMaxTemp.text = "Day " + kelvinToCelcius(body!!.main.temp_max) + "°C"
        binding.tvDayMinTemp.text = "Night " + kelvinToCelcius(body!!.main.temp_min) + "°C"
        binding.tvTemp.text = "" + kelvinToCelcius(body!!.main.temp) + "°"
        binding.tvFeelsLike.text = "Feels Alike " + kelvinToCelcius(body!!.main.feels_like) + "°C"
        binding.tvWeatherType.text = body.weather[0].main
        binding.tvSunrise.text = timeStampToLocateDate(body.sys.sunrise.toLong())
        binding.tvSunset.text = timeStampToLocateDate(body.sys.sunset.toLong())
        binding.tvPressure.text = body.main.pressure.toString()
        binding.tvHumidity.text = body.main.humidity.toString() + " %"
        binding.tvWindspeed.text = body.wind.speed.toString() + " m/s"
        binding.tvTempfr.text =
            "" + ((kelvinToCelcius(body.main.temp)).times(1.8).plus(32).roundToInt())+ "°F"
        binding.searchPlaces.setText(body.name)

        updateUI(body.weather[0].id)

    }

    private fun updateUI(id: Int) {
        if (id in 200..212) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.thunderstorm)
            binding.rlToolbar.setBackgroundColor(resources.getColor(R.color.thunderstorm))
            binding.rlSubLayout.background =
                ContextCompat.getDrawable(this, R.drawable.thunderstrom_bg)
            binding.llMainBgBelow.background =
                ContextCompat.getDrawable(this, R.drawable.thunderstrom_bg)
            binding.llMainBgAbove.background =
                ContextCompat.getDrawable(this, R.drawable.thunderstrom_bg)
            binding.ivWeatherBg.setImageResource(R.drawable.thunderstrom_bg)
            binding.ivWeatherIcon.setImageResource(R.drawable.thunderstrom)
        } else if (id in 300..321) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.drizzle)
            binding.rlToolbar.setBackgroundColor(resources.getColor(R.color.drizzle))
            binding.rlSubLayout.background = ContextCompat.getDrawable(this, R.drawable.drizzle_bg)
            binding.llMainBgBelow.background =
                ContextCompat.getDrawable(this, R.drawable.drizzle_bg)
            binding.llMainBgAbove.background =
                ContextCompat.getDrawable(this, R.drawable.drizzle_bg)
            binding.ivWeatherBg.setImageResource(R.drawable.drizzle_bg)
            binding.ivWeatherIcon.setImageResource(R.drawable.drizzle)
        } else if (id in 500..531) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.snow)
            binding.rlToolbar.setBackgroundColor(resources.getColor(R.color.snow))
            binding.rlSubLayout.background = ContextCompat.getDrawable(this, R.drawable.snow_bg)
            binding.llMainBgBelow.background = ContextCompat.getDrawable(this, R.drawable.snow_bg)
            binding.llMainBgAbove.background = ContextCompat.getDrawable(this, R.drawable.snow_bg)
            binding.ivWeatherBg.setImageResource(R.drawable.snow_bg)
            binding.ivWeatherIcon.setImageResource(R.drawable.snow)
        } else if (id in 701..781) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.atmosphere)
            binding.rlToolbar.setBackgroundColor(resources.getColor(R.color.atmosphere))
            binding.rlSubLayout.background = ContextCompat.getDrawable(this, R.drawable.mist_bg)
            binding.llMainBgBelow.background = ContextCompat.getDrawable(this, R.drawable.mist_bg)
            binding.llMainBgAbove.background = ContextCompat.getDrawable(this, R.drawable.mist_bg)
            binding.ivWeatherBg.setImageResource(R.drawable.mist_bg)
            binding.ivWeatherIcon.setImageResource(R.drawable.mist)
        } else if (id == 800) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.clear)
            binding.rlToolbar.setBackgroundColor(resources.getColor(R.color.clear))
            binding.rlSubLayout.background = ContextCompat.getDrawable(this, R.drawable.clear_bg)
            binding.llMainBgBelow.background = ContextCompat.getDrawable(this, R.drawable.clear_bg)
            binding.llMainBgAbove.background = ContextCompat.getDrawable(this, R.drawable.clear_bg)
            binding.ivWeatherBg.setImageResource(R.drawable.clear_bg)
            binding.ivWeatherIcon.setImageResource(R.drawable.clear)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = resources.getColor(R.color.clouds)
            binding.rlToolbar.setBackgroundColor(resources.getColor(R.color.clouds))
            binding.rlSubLayout.background = ContextCompat.getDrawable(this, R.drawable.cloud_bg)
            binding.llMainBgBelow.background = ContextCompat.getDrawable(this, R.drawable.cloud_bg)
            binding.llMainBgAbove.background = ContextCompat.getDrawable(this, R.drawable.cloud_bg)
            binding.ivWeatherBg.setImageResource(R.drawable.cloud_bg)
            binding.ivWeatherIcon.setImageResource(R.drawable.clouds)
        }
        binding.pbLoading.visibility = View.GONE
        binding.mainLayout.visibility = View.VISIBLE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun timeStampToLocateDate(timestamp: Long): String {
        val localTime =
            timestamp.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalTime() }
        return localTime.toString()
    }

    private fun kelvinToCelcius(temp: Double): Double {
        var intTemp = temp;
        intTemp = intTemp.minus(273)
        return intTemp.toBigDecimal().setScale(1, RoundingMode.UP).toDouble()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PERMISSION_REQUEST_ACCESS_LOCATION
        )

    }

    companion object {
        private const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
        const val API_KEY = "0d20b796c952ce24f62e0271e15118e6"
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_ACCESS_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(applicationContext, "Granted", Toast.LENGTH_SHORT).show()
                getCurrentLocation()
            } else {
                Toast.makeText(applicationContext, "Granted", Toast.LENGTH_SHORT).show()

            }
        }
    }

}