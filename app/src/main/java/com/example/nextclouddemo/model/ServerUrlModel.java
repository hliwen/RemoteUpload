package com.example.nextclouddemo.model;

import android.net.Uri;

public class ServerUrlModel {
    public int responseCode;
    public Uri serverUri;
    public String stringServerUri;

    @Override
    public String toString() {
        return "MainActivitylog ServerUrlModel{" + "responseCode=" + responseCode + ", stringServerUri=" + stringServerUri + '}';
    }


}
