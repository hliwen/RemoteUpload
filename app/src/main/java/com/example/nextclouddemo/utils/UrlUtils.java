package com.example.nextclouddemo.utils;

public class UrlUtils {

    public static final String serverUri = "http://www.iothm.top:9090/nc/info";
    public static final String deviceInfoPrefix = "https://www.iothm.top:12443/v2/device/attrKey/4g_imei/";
    public static final String deviceInfoSuffix = "/info?infoFilter=imei,monitor_email,yunpan_password,upload_mode,upload_index";
    public static final String appVersionURL = "https://www.iothm.top:12443/v2/app/autoUpdate/V3/version/latest";
    public static final String appVersionURL_Beta = "https://www.iothm.top:12443/v2/app/autoUpdate/V3_beta/version/latest";
    public static final String appDowloadURL = "https://www.iothm.top:12443/v2/app/autoUpdate/V3/version/";
    public static final String appDowloadURL_Beta = "https://www.iothm.top:12443/v2/app/autoUpdate/V3_beta/version/";
}
