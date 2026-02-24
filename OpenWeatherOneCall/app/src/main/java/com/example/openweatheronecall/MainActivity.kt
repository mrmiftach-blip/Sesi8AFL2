package com.example.weatherapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope

// ==================== DATA MODELS ====================

// City Model
data class City(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String
)

// City Response for Geocoding API
data class CityResponse(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String
)

// Weather Response Model
data class WeatherResponse(
    val coord: Coord,
    val weather: List<Weather>,
    val main: Main,
    val visibility: Int,
    val wind: Wind,
    val rain: Rain?,
    val clouds: Clouds,
    val dt: Long,
    val sys: Sys,
    val timezone: Int,
    val id: Int,
    val name: String,
    val cod: Int
) {
    data class Coord(
        val lon: Double,
        val lat: Double
    )

    data class Weather(
        val id: Int,
        val main: String,
        val description: String,
        val icon: String
    )

    data class Main(
        val temp: Double,
        val feels_like: Double,
        val temp_min: Double,
        val temp_max: Double,
        val pressure: Int,
        val humidity: Int
    )

    data class Wind(
        val speed: Double,
        val deg: Int
    )

    data class Rain(
        @SerializedName("1h")
        val oneHour: Double
    )

    data class Clouds(
        val all: Int
    )

    data class Sys(
        val country: String,
        val sunrise: Long,
        val sunset: Long
    )
}

// One Call API Response Model
data class OneCallResponse(
    val lat: Double,
    val lon: Double,
    val timezone: String,
    val current: Current,
    val hourly: List<Hourly>,
    val daily: List<Daily>
) {
    data class Current(
        val dt: Long,
        val temp: Double,
        val weather: List<Weather>
    ) {
        data class Weather(
            val description: String,
            val icon: String
        )
    }

    data class Hourly(
        val dt: Long,
        val temp: Double,
        val weather: List<Weather>
    ) {
        data class Weather(
            val description: String,
            val icon: String
        )
    }

    data class Daily(
        val dt: Long,
        val temp: Temp,
        val weather: List<Weather>
    ) {
        data class Temp(
            val min: Double,
            val max: Double
        )

        data class Weather(
            val description: String,
            val icon: String
        )
    }
}

// ==================== API SERVICE ====================

interface ApiService {
    @GET("geo/1.0/direct")
    suspend fun getCoordinates(
        @Query("q") cityName: String,
        @Query("limit") limit: Int = 1,
        @Query("appid") apiKey: String
    ): List<CityResponse>

    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse

    @GET("onecall")
    suspend fun getOneCallWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("exclude") exclude: String = "minutely,alerts"
    ): OneCallResponse
}

// ==================== RETROFIT INSTANCE ====================

object RetrofitInstance {
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"
    private const val API_KEY = "YOUR_API_KEY_HERE" // Ganti dengan API key Anda

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: ApiService = retrofit.create(ApiService::class.java)

    fun getApiKey(): String = API_KEY
}

// ==================== VIEWMODEL ====================

class WeatherViewModel : ViewModel() {
    private val _cities = MutableStateFlow<List<City>>(emptyList())
    val cities: StateFlow<List<City>> = _cities.asStateFlow()

    private val _selectedCityWeather = MutableStateFlow<WeatherResponse?>(null)
    val selectedCityWeather: StateFlow<WeatherResponse?> = _selectedCityWeather.asStateFlow()

    private val _oneCallData = MutableStateFlow<OneCallResponse?>(null)
    val oneCallData: StateFlow<OneCallResponse?> = _oneCallData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Daftar kota default
    private val defaultCities = listOf(
        "Jakarta",
        "Surabaya",
        "Bandung",
        "Medan",
        "Makassar"
    )

    init {
        loadDefaultCities()
    }

    fun loadDefaultCities() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val citiesList = mutableListOf<City>()
                for (cityName in defaultCities) {
                    val response = RetrofitInstance.api.getCoordinates(
                        cityName,
                        1,
                        RetrofitInstance.getApiKey()
                    )
                    if (response.isNotEmpty()) {
                        citiesList.add(
                            City(
                                name = response[0].name,
                                lat = response[0].lat,
                                lon = response[0].lon,
                                country = response[0].country
                            )
                        )
                    }
                }
                _cities.value = citiesList
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Gagal memuat data kota: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchCity(cityName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitInstance.api.getCoordinates(
                    cityName,
                    5,
                    RetrofitInstance.getApiKey()
                )

                val citiesList = response.map {
                    City(
                        name = it.name,
                        lat = it.lat,
                        lon = it.lon,
                        country = it.country
                    )
                }
                _cities.value = citiesList
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Kota tidak ditemukan: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCityWeather(city: City) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val weatherResponse = RetrofitInstance.api.getCurrentWeather(
                    city.lat,
                    city.lon,
                    RetrofitInstance.getApiKey()
                )
                _selectedCityWeather.value = weatherResponse

                val oneCallResponse = RetrofitInstance.api.getOneCallWeather(
                    city.lat,
                    city.lon,
                    RetrofitInstance.getApiKey()
                )
                _oneCallData.value = oneCallResponse

                _error.value = null
            } catch (e: Exception) {
                _error.value = "Gagal memuat data cuaca: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

// ==================== THEME ====================

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFFEEEEEE)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2196F3),
    secondary = Color(0xFF03DAC5),
    tertiary = Color(0xFF37474F)
)

@Composable
fun WeatherAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// ==================== COMPOSABLES ====================

// Main Activity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeatherAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val viewModel: WeatherViewModel = viewModel()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onCityClick = { city ->
                                    viewModel.loadCityWeather(city)
                                    navController.navigate("detail/${city.name}")
                                }
                            )
                        }
                        composable("detail/{cityName}") { backStackEntry ->
                            val cityName = backStackEntry.arguments?.getString("cityName") ?: ""
                            DetailScreen(
                                viewModel = viewModel,
                                cityName = cityName,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Home Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WeatherViewModel,
    onCityClick: (City) -> Unit
) {
    val cities by viewModel.cities.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aplikasi Cuaca") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Cari kota...") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear"
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        viewModel.searchCity(searchQuery)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cari")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Kota Populer",
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Error Message
            error?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // City List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cities) { city ->
                        CityCard(
                            city = city,
                            onClick = { onCityClick(city) }
                        )
                    }
                }
            }
        }
    }
}

// City Card
@Composable
fun CityCard(
    city: City,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = city.name,
                    fontSize = 20.sp
                )
                Text(
                    text = "Negara: ${city.country}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null
            )
        }
    }
}

// Detail Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: WeatherViewModel,
    cityName: String,
    onBackClick: () -> Unit
) {
    val weather by viewModel.selectedCityWeather.collectAsState()
    val oneCallData by viewModel.oneCallData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Cuaca $cityName") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current Weather
                weather?.let { currentWeather ->
                    item {
                        CurrentWeatherCard(currentWeather)
                    }
                }

                // 12 Hour Forecast
                oneCallData?.hourly?.take(12)?.let { hourlyData ->
                    item {
                        Text(
                            text = "Prakiraan 12 Jam",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        HourlyForecastRow(hourlyData)
                    }
                }

                // 7 Days Forecast
                oneCallData?.daily?.take(7)?.let { dailyData ->
                    item {
                        Text(
                            text = "Prakiraan 7 Hari",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        DailyForecastList(dailyData)
                    }
                }
            }
        }
    }
}

// Current Weather Card
@Composable
fun CurrentWeatherCard(weather: WeatherResponse) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Weather Icon
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://openweathermap.org/img/wn/${weather.weather[0].icon}@2x.png")
                    .crossfade(true)
                    .build(),
                contentDescription = "Weather Icon",
                modifier = Modifier.size(100.dp),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "${weather.main.temp}°C",
                fontSize = 48.sp
            )

            Text(
                text = weather.weather[0].description.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                },
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeatherInfoItem(
                    label = "Min",
                    value = "${weather.main.temp_min}°C"
                )
                WeatherInfoItem(
                    label = "Max",
                    value = "${weather.main.temp_max}°C"
                )
                WeatherInfoItem(
                    label = "Kelembaban",
                    value = "${weather.main.humidity}%"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeatherInfoItem(
                    label = "Angin",
                    value = "${weather.wind.speed} m/s"
                )
                WeatherInfoItem(
                    label = "Tekanan",
                    value = "${weather.main.pressure} hPa"
                )
            }
        }
    }
}

// Weather Info Item
@Composable
fun WeatherInfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// Hourly Forecast Row
@Composable
fun HourlyForecastRow(hourlyData: List<OneCallResponse.Hourly>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(hourlyData) { hour ->
            HourlyForecastCard(hour)
        }
    }
}

// Hourly Forecast Card
@Composable
fun HourlyForecastCard(hourly: OneCallResponse.Hourly) {
    Card(
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val dateFormat = SimpleDateFormat("HH:00", Locale.getDefault())
            Text(
                text = dateFormat.format(Date(hourly.dt * 1000)),
                fontSize = 12.sp
            )

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://openweathermap.org/img/wn/${hourly.weather[0].icon}.png")
                    .crossfade(true)
                    .build(),
                contentDescription = "Weather Icon",
                modifier = Modifier.size(40.dp)
            )

            Text(
                text = "${hourly.temp.toInt()}°C",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Daily Forecast List
@Composable
fun DailyForecastList(dailyData: List<OneCallResponse.Daily>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        dailyData.forEach { day ->
            DailyForecastItem(day)
        }
    }
}

// Daily Forecast Item
@Composable
fun DailyForecastItem(daily: OneCallResponse.Daily) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dateFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
            Text(
                text = dateFormat.format(Date(daily.dt * 1000)),
                modifier = Modifier.weight(1f)
            )

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://openweathermap.org/img/wn/${daily.weather[0].icon}.png")
                    .crossfade(true)
                    .build(),
                contentDescription = "Weather Icon",
                modifier = Modifier.size(30.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${daily.temp.min.toInt()}°C / ${daily.temp.max.toInt()}°C",
                fontWeight = FontWeight.Bold
            )
        }
    }
}