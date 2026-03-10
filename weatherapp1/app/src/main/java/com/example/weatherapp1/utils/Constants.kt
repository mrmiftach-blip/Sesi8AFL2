package com.example.weatherapp1.utils

object Constants {
    const val BASE_URL = "https://api.openweathermap.org/data/2.5/"
    const val GEOCODING_BASE_URL = "http://api.openweathermap.org/geo/1.0/"
    const val API_KEY = "https://api.openweathermap.org/data/3.0/onecall?lat={lat}&lon={lon}&exclude={part}&appid={API key}" // Ganti dengan API key Anda

    // Daftar kota default
    val DEFAULT_CITIES = listOf(
        "Jakarta",
        "Surabaya",
        "Bandung",
        "Medan",
        "Yogyakarta"
    )

    fun getWeatherIconUrl(iconCode: String): String {
        return "https://openweathermap.org/img/wn/$iconCode@2x.png"
    }

    fun kelvinToCelsius(kelvin: Double): String {
        return "${(kelvin - 273.15).toInt()}°C"
    }
}