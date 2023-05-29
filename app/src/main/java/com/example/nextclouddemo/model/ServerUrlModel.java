package com.example.nextclouddemo.model;

import android.net.Uri;

public class ServerUrlModel {
    public int responseCode;

    @Override
    public String toString() {
        return "MainActivitylog ServerUrlModel{" +
                "responseCode=" + responseCode +
                ", stringServerUri=" + stringServerUri +
                '}';
    }

    public Uri serverUri;
    public String stringServerUri;


}
