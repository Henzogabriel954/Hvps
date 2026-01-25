package online.hcraft.hvps.network

import okhttp3.OkHttpClient
import online.hcraft.hvps.model.AgentStatsResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface AgentApi {
    @GET
    suspend fun getStats(
        @Url url: String,
        @Header("Authorization") token: String
    ): AgentStatsResponse
}

object AgentClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    val api: AgentApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://localhost/") // Dummy base URL, we use @Url
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AgentApi::class.java)
    }
}
