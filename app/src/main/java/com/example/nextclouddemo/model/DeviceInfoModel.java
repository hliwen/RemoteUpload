package com.example.nextclouddemo.model;

public class DeviceInfoModel {
    public String serverUri;
    public String returnImei;
    public int responseCode;
    public String deveceName;
    public String username;
    public String password;
    @Override
    public String toString() {
        return "MainActivitylog DeviceInfoModel{" +
                "serverUri='" + serverUri + '\'' +
                "returnImei='" + returnImei + '\'' +
                ", responseCode=" + responseCode +
                ", userName='" + deveceName + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                '}';
    }


}
