package com.example.nextclouddemo;

import android.app.Application;
import android.content.Context;

import com.blankj.utilcode.util.Utils;
import com.example.nextclouddemo.operation.LogcatHelper;


public class MyApplication extends Application {

    private static Context context;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        Utils.init(this);
        LogcatHelper.getInstance().start();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
    }

    public static Context getContext() {
        return context;
    }
}
