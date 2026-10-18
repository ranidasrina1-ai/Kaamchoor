package com.kaamchor.data.remote

import com.google.gson.Gson
import com.kaamchor.data.local.SessionStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object GsonProvider {
    val gson: Gson = Gson()
}

fun normalizeBaseUrl(raw: String): String {
    var url = raw.trim()
    if (url.isEmpty()) return "http://10.0.2.2:5000/"
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = "http://$url"
    }
    if (!url.endsWith("/")) url += "/"
    return url
}

class ApiClient(private val session: SessionStore) {

    @Volatile private var cachedBaseUrl: String? = null
    @Volatile private var retrofit: Retrofit? = null

    private val authInterceptor = Interceptor { chain ->
        val token = session.accessTokenBlocking()
        val request: Request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val authenticator = okhttp3.Authenticator { route, response ->
        if (response.request.header("Authorization") == null) return@Authenticator null

        synchronized(this) {
            val refresh = session.refreshTokenBlocking() ?: return@synchronized null
            val base = normalizeBaseUrl(session.serverUrlBlocking())
            val refreshRequest = Request.Builder()
                .url(base + "api/auth/refresh")
                .header("Authorization", "Bearer $refresh")
                .post("".toRequestBody())
                .build()

            try {
                rawClient.newCall(refreshRequest).execute().use { res ->
                    if (!res.isSuccessful) {
                        return@synchronized null
                    }
                    val body = res.body?.string() ?: return@synchronized null
                    val auth = GsonProvider.gson.fromJson(body, AuthResponse::class.java)
                    kotlinx.coroutines.runBlocking {
                        session.saveAuth(auth.token, auth.refreshToken, GsonProvider.gson.toJson(auth.user))
                    }
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${auth.token}")
                        .build()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private val rawClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val client: OkHttpClient = rawClient.newBuilder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .authenticator(authenticator)
        .build()

    fun api(): ApiService {
        val base = normalizeBaseUrl(session.serverUrlBlocking())
        val current = retrofit
        if (current == null || cachedBaseUrl != base) {
            synchronized(this) {
                if (retrofit == null || cachedBaseUrl != base) {
                    retrofit = Retrofit.Builder()
                        .baseUrl(base)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create(GsonProvider.gson))
                        .build()
                    cachedBaseUrl = base
                }
            }
        }
        return retrofit!!.create(ApiService::class.java)
    }

    fun baseUrl(): String = normalizeBaseUrl(session.serverUrlBlocking())

    private fun String.toRequestBody(): okhttp3.RequestBody =
        okhttp3.RequestBody.create(null, this)
}
