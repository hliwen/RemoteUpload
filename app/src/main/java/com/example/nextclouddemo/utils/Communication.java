package com.example.nextclouddemo.utils;

import android.net.Uri;

import com.example.nextclouddemo.model.DeviceInfoModel;
import com.example.nextclouddemo.model.ServerUrlModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Communication {

    private static final String TAG = "MainActivitylog";

    public Communication() {

    }

    public ServerUrlModel getServerUrl() {
        ServerUrlModel serverUrlModel = new ServerUrlModel();
        try {
            URL url = new URL(UrlUtils.serverUri);
            HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();
            serverUrlModel.responseCode = ResponseCode;
            if (ResponseCode != 200) {
                return serverUrlModel;
            }

            InputStream inputStream = urlcon.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader reader = new BufferedReader(isr);
            String line;
            StringBuffer buffer = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            String content = buffer.toString();
            Log.d(TAG, "run:  nccontent = " + content);
            JSONObject jsonObject = new JSONObject(content);
            JSONArray jsonArray = new JSONArray(jsonObject.getString("data"));
            jsonObject = new JSONObject(jsonArray.getString(0));
            serverUrlModel.stringServerUri = jsonObject.getString("url");
            Uri serverUri = Uri.parse(serverUrlModel.stringServerUri);
            serverUrlModel.serverUri = serverUri;
        } catch (Exception e) {
            Log.e(TAG, "getServerUrl: e =" + e);
        }
        return serverUrlModel;
    }

    public DeviceInfoModel getDeviceInfo(String phoneImei) {
        DeviceInfoModel deviceInfoModel = new DeviceInfoModel();
        try {
            URL url = new URL(UrlUtils.deviceInfoPrefix + phoneImei + UrlUtils.deviceInfoSuffix);
            HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();
            deviceInfoModel.responseCode = ResponseCode;
            if (ResponseCode != 200) {
                return deviceInfoModel;
            }
            InputStream inputStream = urlcon.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader reader = new BufferedReader(isr);

            StringBuffer buffer = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            String content = buffer.toString();
            Log.d(TAG, "run:getAccout content = " + content);
            JSONObject jsonObject = new JSONObject(content);
            JSONArray jsonArray = new JSONArray(jsonObject.getString("data"));
            jsonObject = new JSONObject(jsonArray.getString(0));

            JSONObject deviceObject = new JSONObject(jsonObject.getString("device"));
            deviceInfoModel.deveceName = deviceObject.getString("name");

            jsonArray = jsonObject.getJSONArray("deviceAttrList");

            for (int i = 0; i < jsonArray.length(); i++) {
                jsonObject = new JSONObject(jsonArray.getString(i));
                String attrKey = jsonObject.getString("attrKey");
                String attrVal = jsonObject.getString("attrVal");

                if ("imei".equals(attrKey)) {
                    deviceInfoModel.returnImei = attrVal;
                } else if ("upload_index".equals(attrKey)) {
                    deviceInfoModel.upload_index = attrVal;
                } else if ("upload_mode".equals(attrKey)) {
                    deviceInfoModel.upload_mode = attrVal;
                } else if ("yunpan_password".equals(attrKey)) {
                    deviceInfoModel.password = attrVal;
                } else if ("monitor_email".equals(attrKey)) {
                    deviceInfoModel.username = attrVal;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "getDeviceInfo: e =" + e);
        }
        return deviceInfoModel;
    }


}
