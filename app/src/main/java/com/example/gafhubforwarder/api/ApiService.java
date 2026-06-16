package com.example.gafhubforwarder.api;

import com.example.gafhubforwarder.models.LoginRequest;
import com.example.gafhubforwarder.models.LoginResponse;
import com.example.gafhubforwarder.models.MessageRequest;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("sms-forwarder/messages")
    Call<Void> sendMessage(
            @Header("X-API-Key") String apiKey,
            @Body MessageRequest request
    );

    @GET("subscriptions/{code}")
    Call<ResponseBody> checkSubscription(
            @Header("X-API-Key") String apiKey,
            @Path("code") String code
    );
}
