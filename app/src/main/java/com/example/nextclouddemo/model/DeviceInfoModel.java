package com.example.nextclouddemo.model;

public class DeviceInfoModel {
    public String returnImei;
    public int responseCode;
    public String deveceName;
    public String username;
    public String password;
    public String upload_mode;
    public String upload_index;
    @Override
    public String toString() {
        return "{" +
                ",returnImei='" + returnImei + '\'' +
                ", responseCode=" + responseCode +
                ", userName='" + deveceName + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", upload_mode='" + upload_mode + '\'' +
                ", upload_index='" + upload_index + '\'' +
                '}';
    }


}
