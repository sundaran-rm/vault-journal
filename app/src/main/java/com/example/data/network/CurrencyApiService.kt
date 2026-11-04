package com.example.data.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class FrankfurterLatestResponse(
    val amount: Double = 1.0,
    val base: String = "USD",
    val date: String = "",
    val rates: Map<String, Double> = emptyMap()
)

interface FrankfurterApiService {
    @GET("v1/latest")
    suspend fun getLatestRates(@Query("base") baseCurrency: String): FrankfurterLatestResponse
}

class CurrencyRepository(
    private val apiService: FrankfurterApiService = createDefaultService()
) {
    // Baseline reference rates relative to USD used immediately before live ECB fetch completes or when offline
    private val fallbackRatesFromUsd: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "EUR" to 0.92,
        "GBP" to 0.79,
        "JPY" to 152.40,
        "CAD" to 1.39,
        "AUD" to 1.53,
        "CHF" to 0.88,
        "INR" to 84.35,
        "SGD" to 1.33,
        "MXN" to 20.15,
        "BRL" to 5.75,
        "KRW" to 1385.0
    )

    val supportedCurrencies: List<CurrencyInfo> = listOf(
        CurrencyInfo("USD", "US Dollar", "$"),
        CurrencyInfo("EUR", "Euro", "€"),
        CurrencyInfo("GBP", "British Pound", "£"),
        CurrencyInfo("JPY", "Japanese Yen", "¥"),
        CurrencyInfo("CAD", "Canadian Dollar", "CA$"),
        CurrencyInfo("AUD", "Australian Dollar", "A$"),
        CurrencyInfo("CHF", "Swiss Franc", "CHF"),
        CurrencyInfo("INR", "Indian Rupee", "₹"),
        CurrencyInfo("SGD", "Singapore Dollar", "S$"),
        CurrencyInfo("MXN", "Mexican Peso", "MX$"),
        CurrencyInfo("BRL", "Brazilian Real", "R$"),
        CurrencyInfo("KRW", "South Korean Won", "₩")
    )

    suspend fun fetchRates(baseCurrency: String): ExchangeRatesSnapshot {
        return try {
            val response = apiService.getLatestRates(baseCurrency)
            val combined = response.rates.toMutableMap()
            combined[baseCurrency] = 1.0
            ExchangeRatesSnapshot(
                baseCurrency = baseCurrency,
                dateLabel = response.date.ifBlank { "Live ECB" },
                rates = combined,
                isLive = true
            )
        } catch (e: Exception) {
            val baseInUsd = fallbackRatesFromUsd[baseCurrency] ?: 1.0
            val derived = fallbackRatesFromUsd.mapValues { (_, usdRate) ->
                usdRate / baseInUsd
            }
            ExchangeRatesSnapshot(
                baseCurrency = baseCurrency,
                dateLabel = "Offline Reference Rates",
                rates = derived,
                isLive = false
            )
        }
    }

    companion object {
        private fun createDefaultService(): FrankfurterApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.frankfurter.dev/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(FrankfurterApiService::class.java)
        }
    }
}

data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String
)

data class ExchangeRatesSnapshot(
    val baseCurrency: String,
    val dateLabel: String,
    val rates: Map<String, Double>,
    val isLive: Boolean
)
