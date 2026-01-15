package online.hcraft.hvps.network

import okhttp3.OkHttpClient
import online.hcraft.hvps.model.AccountResponse
import online.hcraft.hvps.model.ServerListResponse
import online.hcraft.hvps.model.ServerDetailResponse
import online.hcraft.hvps.model.TaskResponse
import online.hcraft.hvps.utils.TokenManager
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface VpsApi {
    @GET("account")
    suspend fun getAccount(): AccountResponse

    @GET("server")
    suspend fun getServers(
        @Query("results") results: Int = 50
    ): ServerListResponse

    @GET("server/{id}")
    suspend fun getServerDetails(
        @Path("id") id: String,
        @Query("state") state: Boolean = true
    ): ServerDetailResponse

    @GET("server/{id}/tasks")
    suspend fun getServerTasks(
        @Path("id") id: String
    ): Response<Any> // We might need a specific TaskListResponse if we were using it extensively

    // Power Actions
    @POST("server/{id}/boot")
    suspend fun bootServer(@Path("id") id: String): Response<TaskResponse>

    @POST("server/{id}/shutdown")
    suspend fun shutdownServer(@Path("id") id: String): Response<TaskResponse>

    @POST("server/{id}/restart")
    suspend fun restartServer(@Path("id") id: String): Response<TaskResponse>
    
    @POST("server/{id}/powerOff")
    suspend fun powerOffServer(@Path("id") id: String): Response<TaskResponse>
}

object RetrofitClient {
    private const val BASE_URL = "https://vps.danbot.cloud/api/"

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            // Sanitize token to remove newlines or whitespace
            val token = TokenManager.getToken()?.replace("\n", "")?.replace("\r", "")?.trim() ?: ""
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    val api: VpsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VpsApi::class.java)
    }
}