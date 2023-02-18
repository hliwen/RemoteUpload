package com.example.nextclouddemo.utils;

import com.example.nextclouddemo.model.MyMessage;

import org.greenrobot.eventbus.EventBus;

public class Log {
    public static final boolean debug = true;
    private static String TAG = "MainActivitylog";

    public static void i(String tag, String message) {
        if (!debug)
            return;
        android.util.Log.i(TAG, message);
        EventBus.getDefault().post(new MyMessage(message));
    }

    public static void v(String tag, String message) {
        if (!debug)
            return;
        android.util.Log.v(TAG, message);
        EventBus.getDefault().post(new MyMessage(message));
    }

    public static void d(String tag, String message) {
        if (!debug)
            return;
        android.util.Log.d(TAG, message);
        EventBus.getDefault().post(new MyMessage(message));
    }

    public static void w(String tag, String message) {
        if (!debug)
            return;
        android.util.Log.w(TAG, message);
        EventBus.getDefault().post(new MyMessage(message));
    }


    public static void e(String tag, String message) {
        if (!debug)
            return;
        android.util.Log.e(TAG, message);
        EventBus.getDefault().post(new MyMessage(message));
    }
}
