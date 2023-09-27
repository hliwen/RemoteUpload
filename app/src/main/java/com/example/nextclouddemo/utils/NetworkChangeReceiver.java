package com.example.nextclouddemo.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;


public class NetworkChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "remotelog_NetworkChangeReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            String networkType = networkInfo.getTypeName();
            Log.d(TAG, "onReceive 网络已连接，类型 " + networkType);
        } else {
            Log.d(TAG, "onReceive 网络已断开");
        }
    }

    public IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        return intentFilter;
    }
}
