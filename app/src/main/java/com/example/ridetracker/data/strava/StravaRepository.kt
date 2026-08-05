package com.example.ridetracker.data.strava

import com.example.ridetracker.BuildConfig
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StravaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stravaApi: StravaApi
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "strava_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPrefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    fun isLoggedIn(): Boolean = getAccessToken() != null

    suspend fun completeLogin(code: String) {
        val response = stravaApi.getTokens(
            clientId = BuildConfig.STRAVA_CLIENT_ID,
            clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
            code = code
        )
        saveTokens(response.access_token, response.refresh_token)
    }

    fun logout() {
        sharedPrefs.edit().clear().apply()
    }

    fun getAccessToken(): String? = sharedPrefs.getString("access_token", null)

    suspend fun uploadActivity(file: File) {
        val token = getAccessToken() ?: throw Exception("Not logged in to Strava")
        val requestFile = file.asRequestBody("application/gpx+xml".toMediaType())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        
        val dataType = "gpx".toRequestBody("text/plain".toMediaType())
        val activityType = "ride".toRequestBody("text/plain".toMediaType())
        
        stravaApi.uploadActivity("Bearer $token", body, dataType, activityType)
    }
}
