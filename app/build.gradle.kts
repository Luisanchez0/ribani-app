plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ribani.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ribani.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Base URL del backend, en tiempo de build (ya no en código).
        // Dev: por defecto, la LAN del PC que corre el server.
        // Producción (release o cualquier variante):
        //   ./gradlew assembleRelease -PribaniBaseUrl=https://<dominio-del-backend>/
        val baseUrl = providers
            .gradleProperty("ribaniBaseUrl")
            .orElse("http://192.168.100.192:3000/")
        buildConfigField("String", "BASE_URL", "\"${baseUrl.get()}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Habilita la generación de BuildConfig (AGP 9 lo deshabilita por
        // defecto); BASE_URL se define arriba en defaultConfig.
        buildConfig = true
    }
}

dependencies {

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // PullToRefreshBox (stable 1.4.0; fuera del BOM 2024.12.01)
    implementation(libs.androidx.compose.material3.ptr)
    implementation(libs.androidx.compose.ui)
    implementation("androidx.compose.runtime:runtime")
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("com.google.maps.android:maps-compose:6.4.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Red: Retrofit + OkHttp + Gson
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

}