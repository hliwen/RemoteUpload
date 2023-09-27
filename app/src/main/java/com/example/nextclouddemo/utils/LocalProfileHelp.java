package com.example.nextclouddemo.utils;


import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Environment;


import com.example.nextclouddemo.MyApplication;
import com.example.nextclouddemo.ProfileModel;
import com.example.nextclouddemo.VariableInstance;

import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.ReadFolderRemoteOperation;
import com.owncloud.android.lib.resources.files.model.RemoteFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;


import me.jahnen.libaums.core.fs.UsbFile;

public class LocalProfileHelp {

    private static final String TAG = "remotelog_LocalProfileHelp";

    public static final String remoteFilePath = Environment.getExternalStorageDirectory() + File.separator + "remotePictureList.txt";
    public static final String usbFilePath = Environment.getExternalStorageDirectory() + File.separator + "usbPictureList.txt";

    public List<String> remotePictureList = Collections.synchronizedList(new ArrayList<>());
    public List<String> usbPictureList = Collections.synchronizedList(new ArrayList<>());

    private static LocalProfileHelp instance = null;


    public static LocalProfileHelp getInstance() {
        if (instance == null) {
            synchronized (LocalProfileHelp.class) {
                if (instance == null) {
                    instance = new LocalProfileHelp();
                }
            }
        }
        return instance;
    }


    private LocalProfileHelp() {
        remotePictureList = Collections.synchronizedList(new ArrayList<>());
        usbPictureList = Collections.synchronizedList(new ArrayList<>());
    }

    public void resetBackup() {
        File file = new File(remoteFilePath);
        if (file.exists()) {
            file.delete();
        }

        file = new File(usbFilePath);
        if (file.exists()) {
            file.delete();
        }
    }


    public boolean initLocalRemotePictureList() {
        Log.d(TAG, "initLocalRemotePictureList: ");
        try {
            InputStream inputStream = new FileInputStream(remoteFilePath);
            InputStreamReader inputreader = new InputStreamReader(inputStream, "GBK");
            BufferedReader buffreader = new BufferedReader(inputreader);
            String pictureName = "";
            //分行读取
            while ((pictureName = buffreader.readLine()) != null) {
                if (pictureName.trim().length() > 0) {
                    if (!remotePictureList.contains(pictureName)) {
                        remotePictureList.add(pictureName);
                    }
                }
            }
            inputreader.close();
        } catch (Exception e) {
            Log.e(TAG, "initLocalRemotePictureList: IOException =" + e);
            return false;
        }
        Log.e(TAG, "initLocalRemotePictureList: ............................ remotePictureList =" + remotePictureList.size());
        if (remotePictureList.size() == 0) return false;
        VariableInstance.getInstance().remoteListInit = true;
        return true;
    }

    public void createRemotePictureList(String userName) {
        Log.e(TAG, "createRemotePictureList: userName =" + userName);
        String remotePath = "/" + userName + "/" + RemoteOperationUtils.cameraDir + "/";
        ReadFolderRemoteOperation dateReadFolderRemoteOperation = new ReadFolderRemoteOperation(userName + "/" + RemoteOperationUtils.cameraDir);
        RemoteOperationResult dateRemoteOperationResult = dateReadFolderRemoteOperation.execute(VariableInstance.getInstance().ownCloudClient);
        if (dateRemoteOperationResult == null || !dateRemoteOperationResult.isSuccess()) {
            Log.e(TAG, "createRemotePictureList: dateRemoteOperationResult =" + dateRemoteOperationResult);
            return;
        }
        FileWriter remotePictureFileWriter = null;
        try {
            File file = new File(remoteFilePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            remotePictureFileWriter = new FileWriter(file, true);
        } catch (Exception e) {
            Log.e(TAG, "createRemotePictureList: Exception =" + e);
        }


        for (Object obj : dateRemoteOperationResult.getData()) {
            RemoteFile dateRemoteFile = (RemoteFile) obj;
            Log.d(TAG, "createRemotePictureList:  dateRemoteFile =" + dateRemoteFile.getRemotePath());


            if (remotePath.equals(dateRemoteFile.getRemotePath())) {
                Log.w(TAG, "createRemotePictureList:  dateRemoteFile =" + dateRemoteFile.getRemotePath());
                continue;
            }

            ReadFolderRemoteOperation dirReadFolderRemoteOperation = new ReadFolderRemoteOperation(dateRemoteFile.getRemotePath());
            RemoteOperationResult dirRemoteOperationResult = dirReadFolderRemoteOperation.execute(VariableInstance.getInstance().ownCloudClient);
            if (dirRemoteOperationResult == null || !dirRemoteOperationResult.isSuccess()) {
                Log.e(TAG, "createRemotePictureList: dirRemoteOperationResult =" + dirRemoteOperationResult);
                continue;
            }
            for (Object obj1 : dirRemoteOperationResult.getData()) {
                RemoteFile pictureRemoteFile = (RemoteFile) obj1;
                String pictureRemotePath = pictureRemoteFile.getRemotePath();
                pictureRemotePath = pictureRemotePath.substring(dateRemoteFile.getRemotePath().length());
                if (pictureRemotePath != null && pictureRemotePath.length() > 5) {

                    if (!remotePictureList.contains(pictureRemotePath)) {
                        remotePictureList.add(pictureRemotePath);

                        if (remotePictureFileWriter == null) {
                            return;
                        }
                        pictureRemotePath = pictureRemotePath + "\n";
                        try {
                            remotePictureFileWriter.write(pictureRemotePath);
                        } catch (IOException e) {
                            Log.e(TAG, "createRemotePictureList appendPathToRemotePictureFile: IOException =" + e);
                        }
                    }
                }
            }
        }
        if (remotePictureFileWriter == null) {
            return;
        }
        try {
            remotePictureFileWriter.flush();
            remotePictureFileWriter.close();
        } catch (Exception e) {
            Log.e(TAG, "createRemotePictureList: remotePictureFileWriter FileNotFoundException =" + e);
        }
        VariableInstance.getInstance().remoteListInit = true;
        Log.e(TAG, "createRemotePictureList: ............................ remotePictureList =" + remotePictureList.size());
    }


    public void addLocalRemotePictureList(String pictureName) {
        if (pictureFormatFile(pictureName)) {
            if (!remotePictureList.contains(pictureName)) {
                remotePictureList.add(pictureName);
                File file = new File(remoteFilePath);
                if (!file.exists()) {
                    try {
                        file.createNewFile();
                    } catch (Exception e) {
                        Log.e(TAG, "createLocalUSBPictureList: createNewFile Exception =" + e);
                    }
                }
                FileWriter fileWriter = null;
                try {
                    fileWriter = new FileWriter(file, true);
                    pictureName = pictureName + "\n";
                    fileWriter.write(pictureName);
                    fileWriter.flush();
                } catch (Exception e) {
                    Log.e(TAG, "initRemotePictureList: new FileWriter Exception =" + e);
                } finally {
                    if (fileWriter != null) {
                        try {
                            fileWriter.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }
    }

    public boolean initLocalUSBPictureList() {
        Log.d(TAG, "initLocalUSBPictureList: start.................");
        try {
            InputStream inputStream = new FileInputStream(usbFilePath);
            InputStreamReader inputreader = new InputStreamReader(inputStream, "GBK");
            BufferedReader buffreader = new BufferedReader(inputreader);
            String pictureName = "";
            //分行读取
            while ((pictureName = buffreader.readLine()) != null) {
                if (pictureName.trim().length() > 0) {
                    if (!usbPictureList.contains(pictureName)) {
                        usbPictureList.add(pictureName);
                    }
                }
            }
            inputreader.close();
        } catch (Exception e) {
            Log.e(TAG, "initLocalUSBPictureList: IOException =" + e);
            return false;
        }
        Log.e(TAG, "initLocalUSBPictureList: ............................ usbPictureList =" + usbPictureList.size());
        Collections.sort(usbPictureList, new UsbPictureComparator());

        if ((usbPictureList.size() == 0)) {
            return false;
        }

        VariableInstance.getInstance().backupListInit = true;
        return true;
    }

    public void createLocalUSBPictureList(UsbFile storeUSBPictureDirUsbFile) {
        Log.d(TAG, "createLocalUSBPictureList: 创建已备份照片数据库开始............................");

        if (storeUSBPictureDirUsbFile == null) return;

        if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
            Log.e(TAG, "createLocalUSBPictureList:  正在格式化，不需要扫描");
            return;
        }

        FileWriter fileWriter = null;
        File file = new File(usbFilePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (Exception e) {
                Log.e(TAG, "createLocalUSBPictureList: createNewFile Exception =" + e);
            }
        }

        try {
            fileWriter = new FileWriter(file, true);
        } catch (Exception e) {
            Log.e(TAG, "initRemotePictureList: new FileWriter Exception =" + e);
        }
        try {
            UsbFile[] storeUSBPictureDirUsbFileList = storeUSBPictureDirUsbFile.listFiles();
            for (UsbFile dateUsbFile : storeUSBPictureDirUsbFileList) {
                if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
                    if (fileWriter != null) {
                        try {
                            fileWriter.close();
                        } catch (Exception e) {

                        }
                    }
                    if (file.exists()) {
                        file.delete();
                    }
                    return;
                }
                if (dateUsbFile.isDirectory()) {
                    UsbFile[] dateUsbFileDirList = dateUsbFile.listFiles();
                    for (UsbFile usbFile : dateUsbFileDirList) {
                        if (VariableInstance.getInstance().isFormatingUSB.formatState != 0) {
                            if (fileWriter != null) {
                                try {
                                    fileWriter.close();
                                } catch (Exception e) {

                                }
                            }
                            if (file.exists()) {
                                file.delete();
                            }
                            return;
                        }

                        String pictureName = usbFile.getName();
                        String FileEnd = pictureName.substring(pictureName.lastIndexOf(".") + 1).toLowerCase();
                        if (pictureFormatFile(FileEnd)) {
                            if (!usbPictureList.contains(pictureName)) {
                                usbPictureList.add(pictureName);
                            }
                            pictureName = pictureName + "\n";
                            try {
                                fileWriter.write(pictureName);
                            } catch (IOException e) {
                                Log.e(TAG, "appendPathToRemotePictureFile: IOException =" + e);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {

        }

        if (fileWriter != null) {
            try {
                fileWriter.close();
            } catch (Exception e) {

            }
        }
        VariableInstance.getInstance().backupListInit = true;
        Log.d(TAG, "createLocalUSBPictureList: 创建已备份照片数据库结束............................");
    }

    public void addLocalUSBPictureList(String pictureName) {
        if (pictureFormatFile(pictureName)) {
            if (!usbPictureList.contains(pictureName)) {
                usbPictureList.add(pictureName);


                File file = new File(usbFilePath);
                if (!file.exists()) {
                    try {
                        file.createNewFile();
                    } catch (Exception e) {
                        Log.e(TAG, "createLocalUSBPictureList: createNewFile Exception =" + e);
                    }
                }
                FileWriter fileWriter = null;
                try {
                    fileWriter = new FileWriter(file, true);
                    pictureName = pictureName + "\n";
                    fileWriter.write(pictureName);
                    fileWriter.flush();
                } catch (Exception e) {
                    Log.e(TAG, "initRemotePictureList: new FileWriter Exception =" + e);
                } finally {
                    if (fileWriter != null) {
                        try {
                            fileWriter.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }
    }

    public void saveProfileFile(Context context, ProfileModel profileModel) {
        if (profileModel == null) {
            return;
        }
        SharedPreferences.Editor editor = context.getSharedPreferences("Cloud", MODE_PRIVATE).edit();
        if (profileModel.imei != null) {
            editor.putString("imei", profileModel.imei);
        }
        if (profileModel.wifi != null) {
            editor.putString("wifi", profileModel.wifi);
        }
        if (profileModel.pass != null) {
            editor.putString("pass", profileModel.pass);
        }
        if (profileModel.SN != null) {
            editor.putString("SN", profileModel.SN);
        }
        editor.apply();
    }

    public ProfileModel getProfileFile(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("Cloud", MODE_PRIVATE);
        ProfileModel profileModel = new ProfileModel();
        profileModel.imei = sharedPreferences.getString("imei", null);
        profileModel.wifi = sharedPreferences.getString("wifi", null);
        profileModel.pass = sharedPreferences.getString("pass", null);
        profileModel.SN = sharedPreferences.getString("SN", null);

        if (profileModel.imei == null && profileModel.SN == null) {
            return null;
        }
        return profileModel;
    }

    public int checkDeviceStyle() {
        int deviceStyle = 0;//0 是还不确定是蜂窝板还是WiFi版，1是蜂窝版，2是WiFi版

        try {
            UsbManager usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> connectedUSBDeviceList = usbManager.getDeviceList();
            if (connectedUSBDeviceList == null || connectedUSBDeviceList.size() <= 0) {
                return deviceStyle;
            }
            Collection<UsbDevice> usbDevices = connectedUSBDeviceList.values();
            if (usbDevices == null) {
                return deviceStyle;
            }

            for (UsbDevice usbDevice : usbDevices) {
                String usbProductName = usbDevice.getProductName();
                Log.e(TAG, "initDevice: usbProductName =" + usbProductName);
                if (usbProductName == null) {
                    continue;
                }
                usbProductName = usbProductName.trim();
                if (usbProductName.startsWith("802.")) {
                    deviceStyle = 2;
                    break;
                } else if (usbProductName.startsWith("EC25") || usbProductName.startsWith("EG25") || usbProductName.startsWith("EC20") || usbProductName.startsWith("EC200T")) {
                    deviceStyle = 1;
                    break;
                }
            }
        } catch (Exception e) {

        }
        return deviceStyle;
    }

    private boolean pictureFormatFile(String FileEnd) {
        if (FileEnd == null || FileEnd.trim().length() == 0) {
            return false;
        }
        return FileEnd.equals("nif") || FileEnd.equals("crw") || FileEnd.equals("raw") || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2") || FileEnd.equals("cr3") || FileEnd.equals("jpg");
    }

    public class UsbPictureComparator implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            return o2.compareTo(o1);
        }
    }
}
