package com.example.weatherapp1.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.weatherapp1.data.model.WeatherResponse
import com.example.weatherapp1.data.repository.WeatherRepository
import com.example.weatherapp1.databinding.FragmentDetailBinding
import com.example.weatherapp1.utils.Constants
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailFragment : Fragment() {
    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!
    private val repository = WeatherRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cityName = arguments?.getString("cityName") ?: "Jakarta"
        binding.tvCityName.text = cityName

        showLoading(true)
        getWeatherData(cityName)
    }

    private fun getWeatherData(cityName: String) {
        lifecycleScope.launch {
            // Get coordinates first
            repository.getCoordinates(cityName).collect { result ->
                result.onSuccess { geoResponse ->
                    // Then get weather data
                    repository.getWeather(geoResponse.lat, geoResponse.lon).collect { weatherResult ->
                        showLoading(false)
                        weatherResult.onSuccess { weather ->
                            updateUI(weather)
                        }.onFailure { error ->
                            showError(error.message ?: "Failed to load weather data")
                        }
                    }
                }.onFailure { error ->
                    showLoading(false)
                    showError(error.message ?: "City not found")
                }
            }
        }
    }

    private fun updateUI(weather: WeatherResponse) {
        binding.apply {
            // Main weather info
            tvTemperature.text = Constants.kelvinToCelsius(weather.main.temp)
            tvDescription.text = weather.weather.firstOrNull()?.description?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }
            tvFeelsLike.text = "Terasa seperti ${Constants.kelvinToCelsius(weather.main.feelsLike)}"

            // Additional info
            tvHumidity.text = "${weather.main.humidity}%"
            tvPressure.text = "${weather.main.pressure} hPa"
            tvWindSpeed.text = "${weather.wind.speed} m/s"

            // Sunrise and sunset
            val sunriseTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(weather.sys.sunrise * 1000))
            val sunsetTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(weather.sys.sunset * 1000))
            tvSunrise.text = sunriseTime
            tvSunset.text = sunsetTime

            // Weather icon
            val iconCode = weather.weather.firstOrNull()?.icon ?: "01d"
            Glide.with(this@DetailFragment)
                .load(Constants.getWeatherIconUrl(iconCode))
                .into(ivWeatherIcon)
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.scrollView.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
