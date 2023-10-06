package com.example.nextclouddemo;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class MyServer extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


}
