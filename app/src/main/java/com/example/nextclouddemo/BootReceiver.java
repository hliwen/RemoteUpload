package com.example.nextclouddemo;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.gpiotest.LedControl;
import com.example.nextclouddemo.operation.LocalProfileHelp;
import com.example.nextclouddemo.utils.Log;

public class BootReceiver extends BroadcastReceiver {
    @SuppressLint("UnsafeIntentLaunch")
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("remotelog_BootReceiver", "onReceive: intent.getAction() =" + intent.getAction());
        if (intent == null)
            return;
        String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals("android.intent.action.BOOT_COMPLETED") || action.equals("android.intent.action.VOLUME_CHANGED_ACTION")) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClass(context, MainActivity.class);
            context.startActivity(intent);
        } else if (action.equals("Initing_USB")) {
            boolean initing = intent.getBooleanExtra("BroadcastInitingUSB", false);
            int position = intent.getIntExtra("position", 0);
            Log.e("remotelog_BootReceiver", "onReceive: BroadcastInitingUSB initing =" + initing + ",position =" + position);
            VariableInstance.getInstance().serverApkInitingUSB = initing;
        } else if (action.equals("resetBackupData")) {
            LocalProfileHelp.getInstance().resetBackup();
        } else if (action.equals("openUploadApk")) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClass(context, MainActivity.class);
            context.startActivity(intent);
        } else if (action.equals("OpenCameraDevice")) {
            LedControl.writeGpio('b', 2, 1);
        } else if (action.equals("CloseCameraDevice")) {
            LedControl.writeGpio('b', 2, 0);
        } else if (action.equals(Intent.ACTION_PACKAGE_REPLACED)) {
            String packageName = intent.getData().getSchemeSpecificPart();
            if (packageName != null && packageName.equals(context.getPackageName())) {
                Intent restartIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(restartIntent);
            }
        }
    }
}