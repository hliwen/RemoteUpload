package com.example.nextclouddemo.model;

public class DeviceInfoModel {
    public String returnImei;
    public int responseCode;
    public String deviceName;
    public String username;
    public String password;
    public String upload_mode;
    public String upload_index;
    @Override
    public String toString() {
        return "{" +
                ",returnImei='" + returnImei + '\'' +
                ", responseCode=" + responseCode +
                ", deviceName='" + deviceName + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", upload_mode='" + upload_mode + '\'' +
                ", upload_index='" + upload_index + '\'' +
                '}';
    }


}
