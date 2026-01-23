package com.utils;

import okhttp3.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OkHttpUtil {

    private static final OkHttpClient client;

    static {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * GET 请求
     */
    public static String get(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);

        // 添加头
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }

        Request request = builder.get().build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }


    /**
     * POST 请求 - JSON
     */
    public static String postJson(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(
                json, MediaType.parse("application/json; charset=utf-8")
        );

        Request.Builder builder = new Request.Builder().url(url);


        Request request = builder.post(body).build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    /**
     * POST 请求 - Form 表单
     */
    public static String postForm(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        FormBody.Builder formBuilder = new FormBody.Builder();

        if (params != null) {
            params.forEach(formBuilder::add);
        }

        Request.Builder builder = new Request.Builder().url(url);

        if (headers != null) {
            headers.forEach(builder::addHeader);
        }

        Request request = builder.post(formBuilder.build()).build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }
}
