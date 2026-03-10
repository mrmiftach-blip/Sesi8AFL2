package com.example.weatherapp1.data.repository

import com.example.weatherapp1.data.api.RetrofitClient
import com.example.weatherapp1.data.model.GeocodingResponse
import com.example.weatherapp1.data.model.WeatherResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.HttpException
import java.io.IOException

class WeatherRepository {
    private val weatherApi = RetrofitClient.weatherApiService
    private val geocodingApi = RetrofitClient.geocodingApiService

    fun getCoordinates(cityName: String): Flow<Result<GeocodingResponse>> = flow {
        try {
            val response = geocodingApi.getCoordinates(cityName)
            if (response.isNotEmpty()) {
                emit(Result.success(response[0]))
            } else {
                emit(Result.failure(Exception("City not found")))
            }
        } catch (e: IOException) {
            emit(Result.failure(Exception("Network error: ${e.message}")))
        } catch (e: HttpException) {
            emit(Result.failure(Exception("HTTP error: ${e.message()}")))
        }
    }.flowOn(Dispatchers.IO)

    fun getWeather(lat: Double, lon: Double): Flow<Result<WeatherResponse>> = flow {
        try {
            val response = weatherApi.getCurrentWeather(lat, lon)
            emit(Result.success(response))
        } catch (e: IOException) {
            emit(Result.failure(Exception("Network error: ${e.message}")))
        } catch (e: HttpException) {
            emit(Result.failure(Exception("HTTP error: ${e.message()}")))
        }
    }.flowOn(Dispatchers.IO)
}
