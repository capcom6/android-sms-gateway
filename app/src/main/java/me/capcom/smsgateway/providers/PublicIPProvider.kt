package me.capcom.smsgateway.providers

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.GET

class PublicIPProvider: IPProvider {
    override fun getIP(onResult: (String?) -> Unit) {
        API.getPublicIpAddress().enqueue(object: retrofit2.Callback<IpifyResponse> {
            override fun onFailure(call: Call<IpifyResponse>, t: Throwable) {
                t.printStackTrace()
                onResult(null)
            }

            override fun onResponse(call: Call<IpifyResponse>, response: retrofit2.Response<IpifyResponse>) {
                try {
                    if (response.isSuccessful) {
                        onResult(response.body()?.ip)
                    } else {
                        onResult(null)
                    }
                } catch (e: Exception) {
                    onResult(null)
                }
            }
        })
    }

    private interface IpifyService {
        @GET("?format=json")
        fun getPublicIpAddress(): Call<IpifyResponse>
    }

    private data class IpifyResponse(val ip: String)

    companion object {
        private val API = Retrofit.Builder()
            .baseUrl("https://api.ipify.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create<IpifyService>()
    }
}