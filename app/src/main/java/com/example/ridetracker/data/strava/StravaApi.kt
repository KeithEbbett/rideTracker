package com.example.ridetracker.data.strava

import okhttp3.MultipartBody
import retrofit2.http.*

interface StravaApi {
    @POST("oauth/token")
    suspend fun refreshToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("refresh_token") refreshToken: String,
        @Query("grant_type") grantType: String = "refresh_token"
    ): StravaTokenResponse

    @Multipart
    @POST("uploads")
    suspend fun uploadActivity(
        @Part file: MultipartBody.Part,
        @Part("data_type") dataType: String = "gpx",
        @Part("activity_type") activityType: String = "ride"
    ): StravaUploadResponse
}

data class StravaTokenResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_at: Long
)

data class StravaUploadResponse(
    val id: Long,
    val status: String,
    val error: String?
)
