package com.example.gafhubforwarder.api;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static String getBaseUrl() {
        if (isEmulator()) {
            return "http://10.0.2.2:3000/api/";
        }
        return "https://echophone.ru/api/";
    }

    private static boolean isEmulator() {
        String fingerprint = android.os.Build.FINGERPRINT;
        String model = android.os.Build.MODEL;
        String product = android.os.Build.PRODUCT;

        return fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")
                || product.contains("sdk_gphone")
                || product.contains("google_sdk")
                || fingerprint.contains("generic");
    }

    private static final String BASE_URL = getBaseUrl();
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
