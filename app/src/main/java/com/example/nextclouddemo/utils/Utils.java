package com.example.nextclouddemo.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;

import com.example.nextclouddemo.MyApplication;
import com.example.nextclouddemo.VariableInstance;
import com.example.nextclouddemo.model.UploadFileModel;

import androidx.core.content.PermissionChecker;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Utils {
    private static final String TAG = "MainActivitylog";
    private static final long minMemory = 1024 * 1024 * 500;

    public static void checkSDAvailableSize() {
        if (Utils.getSDAvailableSize() < minMemory) {
            Log.e(TAG, "checkSDAvailableSize: 手机内部存储过小，需要删除部分文件 ");
            File localTpmFlie = new File(VariableInstance.getInstance().TFCardUploadPictureDir);
            if (localTpmFlie != null && localTpmFlie.exists()) {
                File[] files = localTpmFlie.listFiles();
                if (files != null) {
                    // 按文件名进行排序由小到大
                    Arrays.sort(files, (file1, file2) -> file1.getName().compareTo(file2.getName()));
                    for (int i = 0; i < 5; i++) {
                        if (files.length - i > 0) {
                            files[i].delete();
                        }
                    }
                }
            }
        }
    }

    /*** 获得SD卡总大小** @return*/
    public static String getSDTotalSize() {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return Formatter.formatFileSize(MyApplication.getContext(), blockSize * totalBlocks);
    }

    /*** 获得sd卡剩余容量，即可用大小** @return*/
    public static long getSDAvailableSize() {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return blockSize * availableBlocks;
    }

    public static void resetDir(String dir) {
        File dirFlie = new File(dir);
        if (dirFlie != null && dirFlie.exists()) {
            Utils.deleteAllFiles(dirFlie);
            dirFlie = new File(dir);
            if (!dirFlie.exists()) {
                dirFlie.mkdir();
            }
        }
    }

    public static File makeDir(String dir) {
        File fileFolder = new File(dir);
        if (!fileFolder.exists()) {
            fileFolder.mkdir();
        }
        return fileFolder;
    }

    @SuppressLint("SimpleDateFormat")
    private static DateFormat getDateFormat(String pattern) {
        String p = pattern;
        Map<String, DateFormat> formatMap = local.get();
        DateFormat df = formatMap.get(p);
        if (df == null) {
            df = new SimpleDateFormat(p);
            formatMap.put(p, df);
        }
        return df;
    }


    private static ThreadLocal<Map<String, DateFormat>> local = new ThreadLocal<Map<String, DateFormat>>() {
        @Override
        protected Map<String, DateFormat> initialValue() {
            return new HashMap<String, DateFormat>();
        }
    };

    public static String getyyyyMMString() {
        try {
            return getDateFormat("yyyyMM").format(new Date());
        } catch (Exception e) {
        }
        return System.currentTimeMillis() + "";
    }


    public static int getyyMMddtringInt(long time) {
        try {
            String s = getDateFormat("yyMMdd").format(new Date(time));
            return Integer.parseInt(s);
        } catch (Exception e) {

        }
        return 100000;
    }

    public static String getyyyyMMtring(long time) {
        try {
            return getDateFormat("yyyyMM").format(new Date(time));
        } catch (Exception e) {

        }
        return "100000";
    }


    public static void closeAndroid() {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot -p"});  //关机
            proc.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static final String[] PERMISSIONS = {

            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,

            Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,};

    @SuppressLint("WrongConstant")
    public static String[] haveNoPermissions(Context mActivity) {
        ArrayList<String> haveNo = new ArrayList<>();
        for (String permission : PERMISSIONS) {
            if (PermissionChecker.checkPermission(mActivity, permission, Binder.getCallingPid(), Binder.getCallingUid(), mActivity.getPackageName()) != PackageManager.PERMISSION_GRANTED) {
                haveNo.add(permission);
                Log.d(TAG, "haveNoPermissions: " + Build.BRAND);
                Log.i(TAG, "haveNoPermissions : " + permission);
            }
        }

        return haveNo.toArray(new String[haveNo.size()]);
    }


    public static void deleteAllFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        if (dir.listFiles() != null) for (File file : dir.listFiles()) {
            if (file.isFile()) file.delete(); // 删除所有文件
            else if (file.isDirectory()) deleteAllFiles(file); // 递规的方式删除文件夹
        }
        dir.delete();// 删除目录本身
    }

}
