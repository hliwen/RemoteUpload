package com.example.nextclouddemo.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {
    private static final String TAG = "remotelog_Utils";
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
        long remain = blockSize * availableBlocks;
        Log.e(TAG, "getSDAvailableSize: 内置卡剩余内存：" + remain);
        return remain;
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
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        if (dir.listFiles() != null) {
            for (File file : dir.listFiles()) {
                if (file.isFile()) {
                    file.delete();
                } else if (file.isDirectory()) {
                    deleteAllFiles(file); // 递规的方式删除文件夹
                }
            }
        }
        dir.delete();// 删除目录本身
    }


    public static List<String> getUSBDeviceName() {
        List<String> usbDevices = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec("cat /proc/mounts");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("/mnt/media_rw/")) {
                    String usbDevice = line.substring(line.indexOf("/dev/"));
                    Log.e(TAG, "getUSBDeviceName: usbDeviceName = " + usbDevice);
                    if (usbDevice != null) {
                        usbDevices.add(usbDevice);
                    }
                }
            }
            reader.close();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return usbDevices;
    }


    public static void formatUSB(String usbDeviceName) {
        try {
            // 获取 root 权限
            Process suProcess = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
            // 获取 U 盘设备名称，可以通过命令行查看
            String usbDevice = "/dev/" + usbDeviceName;  // 替换为你的 U 盘设备名称
            // 卸载 U 盘
            os.writeBytes("umount " + usbDevice + "\n");

            // 格式化 U 盘为 ext4 文件系统
            //os.writeBytes("mkfs.ext4 " + usbDevice + "\n");

            //格式化为 FAT32 文件系统
            os.writeBytes("mkfs.vfat -F 32 " + usbDevice + "\n");

            //格式化为 NTFS 文件系统（需要设备支持）
            // os.writeBytes("mkfs.ntfs " + usbDevice + "\n");

            // 关闭输出流和进程
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            suProcess.waitFor();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "formatUSB: e =" + e);
        }
    }


    public static void test() {
        String usbDevicePath = "/dev/sdX1";  // 替换为你的 U 盘设备路径
        String mountPoint = "/mnt/usb";  // 替换为你的挂载点路径

        // 检查是否已经挂载
        if (!isMounted(mountPoint)) {
            // 挂载 U 盘
            if (mountUSB(usbDevicePath, mountPoint)) {
                System.out.println("USB drive mounted.");
            } else {
                System.out.println("Failed to mount USB drive.");
                return;
            }
        }

        // 在挂载点下读取文件列表
        listFiles(mountPoint);

        // 卸载 U 盘
        unmountUSB(mountPoint);
    }

    public static boolean isMounted(String path) {
        try {
            Process process = Runtime.getRuntime().exec("mount");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.contains(path)) {
                    reader.close();
                    process.waitFor();
                    return true;
                }
            }

            reader.close();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return false;
    }

    public static boolean mountUSB(String usbDevicePath, String mountPoint) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder command = new StringBuilder();
            command.append("mount -t vfat ").append(usbDevicePath).append(" ").append(mountPoint);

            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command.toString());
            os.flush();
            os.close();

            process.waitFor();
            reader.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static void listFiles(String path) {
        File directory = new File(path);
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                System.out.println(file.getName());
            }
        }
    }

    public static void unmountUSB(String mountPoint) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder command = new StringBuilder();
            command.append("umount ").append(mountPoint);

            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command.toString());
            os.flush();
            os.close();

            process.waitFor();
            reader.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static boolean installApk(String apkPath) {
        BufferedReader es = null;
        DataOutputStream os = null;

        boolean succeed = false;
        try {
            Process process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            String command = "pm install -r " + apkPath + "\n";
            os.write(command.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.writeBytes("exit\n");
            os.flush();

            process.waitFor();
            es = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = es.readLine()) != null) {
                builder.append(line);
            }
            if (!builder.toString().contains("Failure")) {
                succeed = true;
            }
        } catch (Exception e) {

        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                if (es != null) {
                    es.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "installAPKServer: 安装服务apk失败：" + e);
            }
        }
        return succeed;
    }

    public static int getServerVersionCode(Context context, String packageName) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            return packageInfo.versionCode;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static String getServerVersionName(Context context, String packageName) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            return packageInfo.packageName;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0";
    }

    public static void uninstallapk() {
        Process process = null;
        DataOutputStream dataOutputStream = null;
        try {
            process = Runtime.getRuntime().exec("su");
            dataOutputStream = new DataOutputStream(process.getOutputStream());
            String command = "pm uninstall com.remoteupload.apkserver" + "\n";
            dataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
            dataOutputStream.flush();
            dataOutputStream.writeBytes("exit\n");
            dataOutputStream.flush();

        } catch (IOException e) {
            e.printStackTrace();

        } finally {
            try {
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
            } catch (Exception e) {
            }
        }
    }


    public static void startRemoteActivity() {
        Log.d(TAG, "startServerActivity: ");
        DataOutputStream dataOutputStream = null;
        try {
            Process process = Runtime.getRuntime().exec("su");
            dataOutputStream = new DataOutputStream(process.getOutputStream());
            String command = " am start -W -n com.remoteupload.apkserver/com.remoteupload.apkserver.MainActivity";
            dataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
            dataOutputStream.flush();

        } catch (Exception e) {

        } finally {
            try {
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
            } catch (IOException e) {
            }
        }
    }

    public static boolean isAppInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }



    public static boolean copyAPKServer(String localPath, String ASSETS_NAME, Context context) {
        File file = new File(localPath);
        if (file.exists()) {
            file.delete();
        }
        try {
            InputStream is = context.getResources().getAssets().open(ASSETS_NAME);
            FileOutputStream fos = new FileOutputStream(localPath);
            byte[] buffer = new byte[2048];
            int count = 0;
            while ((count = is.read(buffer)) > 0) {
                fos.write(buffer, 0, count);
            }
            fos.close();
            is.close();
            return true;
        } catch (Exception e) {

        }
        return false;
    }

}
