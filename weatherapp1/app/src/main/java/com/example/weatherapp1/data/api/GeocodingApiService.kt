package com.example.weatherapp1.data.api

import com.example.weatherapp1.data.model.GeocodingResponse
import com.example.weatherapp1.utils.Constants
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApiService {
    @GET("direct")
    suspend fun getCoordinates(
        @Query("q") cityName: String,
        @Query("limit") limit: Int = 1,
        @Query("appid") appId: String = Constants.API_KEY
    ): List<GeocodingResponse>
}
