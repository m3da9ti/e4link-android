package com.empatica.sample;


import com.google.gson.JsonObject;
import com.squareup.okhttp.Call;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.POST;

interface ApiInterface {

    @POST("/data")
    void sendData(@Body EventData data, Callback<JsonObject> callback);
}
