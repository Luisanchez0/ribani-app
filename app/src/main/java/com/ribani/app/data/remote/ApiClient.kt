package com.ribani.app.data.remote

import android.content.Context
import com.ribani.app.BuildConfig
import com.ribani.app.data.local.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    /**
     * Base URL inyectada en tiempo de build (app/build.gradle.kts →
     * buildConfigField "BASE_URL"). Ya NO se define en el código.
     *
     * - Dev (por defecto): LAN del PC que corre el server. En el emulador,
     *   cambia el default de build.gradle.kts a "http://10.0.2.2:3000/".
     * - Producción:
     *   ./gradlew assembleRelease -PribaniBaseUrl=https://<dominio-del-backend>/
     */
    val BASE_URL: String = BuildConfig.BASE_URL




    @Volatile
    private var sessionManager: SessionManager? = null


    fun init(context: Context) {
        if (sessionManager == null) {
            synchronized(this) {
                if (sessionManager == null) {
                    sessionManager = SessionManager(context.applicationContext)
                }
            }
        }
    }

    val authService: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }

    val familiaresService: FamiliaresApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FamiliaresApiService::class.java)
    }

    val medicamentosService: MedicamentosApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MedicamentosApiService::class.java)
    }

    val configuracionService: ConfiguracionApiService by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(ConfiguracionApiService::class.java)
    }

    val alertasService: AlertasApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AlertasApiService::class.java)
    }

    val contactosService: ContactosApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ContactosApiService::class.java)
    }

    val ubicacionesService: UbicacionesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UbicacionesApiService::class.java)
    }

    val dispositivosService: DispositivosApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DispositivosApiService::class.java)
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        sessionManager?.let {
            builder.addInterceptor(AuthInterceptor(it))
        }

        builder.build()
    }
}
