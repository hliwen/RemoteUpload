package com.example.nextclouddemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class TestWifiActivity extends AppCompatActivity {

    private static final String TAG = "MainActivitylog";
    private WifiManager wifiManager;
    private WifiReceiver mWifiReceiver;

    Button connect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tesxwifiactivity);

        connect = findViewById(R.id.connect);

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                File file = new File("/storage/emulated/0/Download/wifi.txt");
                String content = getFileContent(file);
                Log.e(TAG, "onClick: content =" + content);
                String[] data = content.split("\n");
                String wifi = null;
                String pass = null;
                String SN = null;
                for (String datum : data) {
                    if (datum == null)
                        continue;
                    datum.trim();
                    if (datum.startsWith("wifi:")) {
                        wifi = datum;
                    } else if (datum.startsWith("pass:")) {
                        pass = datum;
                    } else if (datum.startsWith("SN:")) {
                        SN = datum;
                    }
                }


                if (wifi != null) {
                    wifi = wifi.substring(5);
                    Log.d(TAG, "onClick: wifi =" + wifi);
                    if (pass == null) {
                        connectWifiNoPws(wifi);
                    } else {
                        pass = pass.substring(5);
                        Log.e(TAG, "onClick: wifi =" + wifi + ",pass =" + pass + ",SN =" + SN);
                        if (pass.length() == 0) {
                            connectWifiNoPws(wifi);
                        } else {
                            connectWifiPws(wifi, pass);
                        }
                    }
                }
            }
        });
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager == null) {
            Log.e(TAG, "onCreate: wifiManager = null ");
        } else {
            boolean wifiEnable = wifiManager.isWifiEnabled();

            Log.d(TAG, "onCreate: wifiEnable =" + wifiEnable);
            if (wifiEnable) {

            } else {
                boolean setEnableSucceed = wifiManager.setWifiEnabled(true);

                Log.e(TAG, "onCreate: setEnableSucceed =" + setEnableSucceed);
            }
        }
        initNetWork();
        mWifiReceiver = new WifiReceiver();
        registerWifiReceiver();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "onCreate: 没有WiFi ACCESS_FINE_LOCATION 权限");
        }

        String[] value = haveNoPermissions(TestWifiActivity.this);
        if (value.length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(value, 111);
        }
    }


    public static final String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
    };

    @SuppressLint("WrongConstant")
    public static String[] haveNoPermissions(Context mActivity) {
        ArrayList<String> haveNo = new ArrayList<>();
        for (String permission : PERMISSIONS) {
            if (PermissionChecker.checkPermission(mActivity, permission, Binder.getCallingPid(), Binder.getCallingUid(), mActivity.getPackageName()) != PackageManager.PERMISSION_GRANTED) {
                haveNo.add(permission);
            }
        }

        return haveNo.toArray(new String[haveNo.size()]);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mWifiReceiver != null) {
            unregisterReceiver(mWifiReceiver);
        }
    }


    private void registerWifiReceiver() {
        if (mWifiReceiver == null)
            return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mWifiReceiver, filter);
    }


    public void initNetWork() {
        Log.e(TAG, "initNetWork: ");
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder request = new NetworkRequest.Builder();

        request.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        request.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);


        NetworkRequest build = request.build();
        connectivityManager.requestNetwork(build, new ConnectivityManager.NetworkCallback() {
            public void onAvailable(Network network) {

                Log.e(TAG, "Network onAvailable");


            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                Log.e(TAG, "Network  onLost: ");

            }
        });
    }


    private class WifiReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            Bundle bundle = intent.getExtras();
            int statusInt = bundle.getInt("wifi_state");
            switch (statusInt) {
                case WifiManager.WIFI_STATE_UNKNOWN:
                    Log.d(TAG, "onReceive: WIFI_STATE_UNKNOWN");
                    break;
                case WifiManager.WIFI_STATE_ENABLING:
                    Log.d(TAG, "onReceive: WIFI_STATE_ENABLING");
                    break;
                case WifiManager.WIFI_STATE_ENABLED:
                    Log.d(TAG, "onReceive: WIFI_STATE_ENABLED");

                    break;

                case WifiManager.WIFI_STATE_DISABLED:
                    Log.d(TAG, "onReceive: WIFI_STATE_DISABLED");
                    break;
                default:
                    break;
            }

            if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {

                    Log.d(TAG, "onReceive: 断开wifi =");
                } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                        Log.d(TAG, "onReceive: 连接wifi =" + wifiManager.getConnectionInfo().getSSID());

                    }
                }
            }


        }
    }


    /**
     * 有密码连接
     *
     * @param ssid
     * @param pws
     */
    public void connectWifiPws(String ssid, String pws) {
        wifiManager.disableNetwork(wifiManager.getConnectionInfo().getNetworkId());
        int netId = wifiManager.addNetwork(getWifiConfig(ssid, pws, true));
        boolean enableNetwork = wifiManager.enableNetwork(netId, true);
        Log.d(TAG, "connectWifiPws: enableNetwork =" + enableNetwork);
    }

    /**
     * 无密码连接
     *
     * @param ssid
     */
    public void connectWifiNoPws(String ssid) {
        wifiManager.disableNetwork(wifiManager.getConnectionInfo().getNetworkId());
        int netId = wifiManager.addNetwork(getWifiConfig(ssid, "", false));
        wifiManager.enableNetwork(netId, true);
    }

    /**
     * wifi设置
     *
     * @param ssid
     * @param pws
     * @param isHasPws
     */
    private WifiConfiguration getWifiConfig(String ssid, String pws, boolean isHasPws) {

        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\"" + ssid + "\"";

        WifiConfiguration tempConfig = isExist(ssid);
        if (tempConfig != null) {

            wifiManager.removeNetwork(tempConfig.networkId);
        }
        if (isHasPws) {

            config.preSharedKey = "\"" + pws + "\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;
        } else {

            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        return config;
    }

    /**
     * 得到配置好的网络连接
     *
     * @param ssid
     * @return
     */
    private WifiConfiguration isExist(String ssid) {
        @SuppressLint("MissingPermission") List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration config : configs) {

            if (config.SSID.equals("\"" + ssid + "\"")) {

                return config;
            }
        }
        return null;
    }


    //读取指定目录下的所有TXT文件的文件内容
    protected String getFileContent(File file) {
        String content = "";
        if (file.isDirectory()) {    //检查此路径名的文件是否是一个目录(文件夹)
            Log.i("zeng", "The File doesn't not exist " + file.getName().toString() + file.getPath().toString());
        } else {
            if (file.getName().endsWith(".txt")) {//文件格式为txt文件
                try {
                    InputStream instream = new FileInputStream(file);
                    if (instream != null) {
                        InputStreamReader inputreader = new InputStreamReader(instream, "GBK");
                        BufferedReader buffreader = new BufferedReader(inputreader);
                        String line = "";
                        //分行读取
                        while ((line = buffreader.readLine()) != null) {
                            content += line + "\n";
                        }
                        instream.close();        //关闭输入流
                    }
                } catch (FileNotFoundException e) {
                    Log.d("TestFile", "The File doesn't not exist.");
                } catch (IOException e) {
                    Log.d("TestFile", e.getMessage());
                }
            }
        }
        return content;
    }
}