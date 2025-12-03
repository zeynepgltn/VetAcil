package com.vetacil.app.network

import com.vetacil.app.model.OverpassResponse
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface OverpassApiService {

    @GET("api/interpreter")
    suspend fun searchVeterinaries(@Query("data") query: String): Response<OverpassResponse>

    companion object {
        // Farklı sunucuları dene:
        private const val BASE_URL = "https://overpass-api.de/"  // DEĞİŞTİRDİK
        // Alternatifler (biri çalışmazsa diğerini dene):
        // "https://overpass.kumi.systems/"
        // "https://overpass.openstreetmap.ru/api/"
        // "https://overpass.openstreetmap.fr/api/"

        fun create(): OverpassApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)  // Artırdık
                .readTimeout(60, TimeUnit.SECONDS)     // Artırdık
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)        // YENİ - otomatik tekrar dene
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(OverpassApiService::class.java)
        }
    }
}