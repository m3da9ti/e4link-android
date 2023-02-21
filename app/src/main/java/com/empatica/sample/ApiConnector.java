package com.empatica.sample;

import retrofit.RestAdapter;

class ApiConnector {

    static ApiInterface connect(String serverAddress) {

        RestAdapter adapter = new RestAdapter.Builder()
                .setEndpoint("http://" + serverAddress + ":8989/") //Set the Root URL
                .build();

        ApiInterface api = adapter.create(ApiInterface.class);
        return api;
    }

}
