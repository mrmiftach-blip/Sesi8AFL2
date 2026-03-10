package com.example.weatherapp1.data.api

import com.example.weatherapp1.data.model.WeatherResponse
import com.example.weatherapp1.utils.Constants
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") appId: String = Constants.API_KEY
    ): WeatherResponse
}
