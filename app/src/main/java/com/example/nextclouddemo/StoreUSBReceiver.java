package com.example.nextclouddemo;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import com.example.nextclouddemo.utils.Log;
import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileOutputStream;
import com.github.mjdev.libaums.partition.Partition;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StoreUSBReceiver extends BroadcastReceiver {
    private static final String TAG = "MainActivitylog2";

    public static final String INIT_STORE_USB_PERMISSION = "INIT_STORE_USB_PERMISSION";
    private StoreUSBListener storeUSBListener;

    private ExecutorService initStoreUSBThreadExecutor;
    private UsbManager usbManager;

    private FileSystem storeUSBFs;
    private UsbFile storeUSBLogcatDirUsbFile;
    private UsbFile storeUSBPictureDirUsbFile;
    private UsbFile storeUSBWifiConfigurationFile;


    public StoreUSBReceiver(Context context, StoreUSBListener storeUSBListener) {
        this.storeUSBListener = storeUSBListener;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        initStoreUSBDevice();
    }


    public void stopStoreUSBInitThreadExecutor() {
        Log.e(TAG, "stopStoreUSBInitThreadExecutor: ");
        try {
            if (initStoreUSBThreadExecutor != null)
                initStoreUSBThreadExecutor.shutdown();
        } catch (Exception e) {
        }
        VariableInstance.getInstance().storeUSBDeviceID = -1;
        storeUSBFs = null;
        storeUSBLogcatDirUsbFile = null;
        storeUSBPictureDirUsbFile = null;
        storeUSBWifiConfigurationFile = null;
        initStoreUSBThreadExecutor = null;
    }

    public int getStoreUSBCapacity() {
        if (storeUSBFs == null)
            return 0;
        int capacity = (int) (storeUSBFs.getCapacity() / (1024 * 1024));
        return capacity;
    }

    public int getStoreUSBFreeSpace() {
        if (storeUSBFs == null)
            return 0;
        int freeSpace = (int) (storeUSBFs.getFreeSpace() / (1024 * 1024));
        return freeSpace;
    }

    private boolean formatException = false;

    public void formatStoreUSB() {
        formatException = false;
        if (storeUSBPictureDirUsbFile != null) {
            try {
                formatStoreUSB(storeUSBPictureDirUsbFile);
            } catch (Exception e) {
                Log.e(TAG, "storeUSBPictureDirUsbFile : Exception =" + e);
                formatException = true;
            }
        }

        storeUSBListener.formatStoreUSBException(formatException);

        if (storeUSBLogcatDirUsbFile != null) {
            try {
                UsbFile[] usbLogcatDirFileList = storeUSBLogcatDirUsbFile.listFiles();
                for (UsbFile file : usbLogcatDirFileList) {
                    file.delete();
                }
            } catch (Exception | OutOfMemoryError e) {
                Log.e(TAG, "formatUSB: storeUSBLogcatDirUsbFile Exception =" + e);
            }
        }
    }

    public void formatStoreUSB(UsbFile usbFile) {

        try {
            Log.e(TAG, "formatStoreUSB: " + usbFile.getName());
            if (usbFile.isDirectory()) {
                UsbFile[] usbLogcatDirFileList = usbFile.listFiles();
                for (UsbFile file : usbLogcatDirFileList) {
                    if (file.isDirectory()) {
                        formatStoreUSB(file);
                    } else {
                        if (file.getLength() == 0)
                            continue;

                        Log.e(TAG, "formatStoreUSB: 1111  delete " + file.getName());
                        file.delete();
                    }
                }
            } else {
                if (usbFile.getLength() != 0) {
                    Log.e(TAG, "formatStoreUSB:2222 delete ");
                    usbFile.delete();
                }
            }
        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "formatStoreUSB: Exception =" + e);
            formatException = true;
        }
    }


    public void uploadLogcatToUSB() {
        Log.e(TAG, "uploadLogcatToUSB:asdfadsfad logcatFileDirUsbFile =" + storeUSBLogcatDirUsbFile + ",logcatFilePath =" + LogcatHelper.getInstance().logcatFilePath);
        if (storeUSBLogcatDirUsbFile == null || LogcatHelper.getInstance().logcatFilePath == null || storeUSBFs == null)
            return;
        UsbFileOutputStream os = null;
        InputStream is = null;
        File localFile = null;
        try {
            localFile = new File(LogcatHelper.getInstance().logcatFilePath);
            UsbFile create = storeUSBLogcatDirUsbFile.createFile(localFile.getName());
            os = new UsbFileOutputStream(create);
            is = new FileInputStream(localFile);

            int bytesRead;
            byte[] buffer = new byte[storeUSBFs.getChunkSize()];
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            Log.e(TAG, "asdfadsfad uploadLogcatToUSB: Exception =" + e);
        } finally {
            try {
                if (os != null) {
                    os.flush();
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "uploadLogcatToUSB: asdfadsfad Exception =" + e);
            }

            if (LogcatHelper.getInstance().logcatFilePath.startsWith("1970")) {
                @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH_mm");
                String date = format.format(new Date(System.currentTimeMillis()));
                File file = new File(VariableInstance.getInstance().LogcatDir, "logcat" + date + ".txt");
                if (localFile != null)
                    localFile.renameTo(file);
            }

        }
    }


    public boolean uploadToUSB(File localFile, String yearMonth) {
        if (localFile == null || !localFile.exists()) {
            Log.e(TAG, "uploadToUSB: \ntodayDir =" + "\n localFile =" + localFile);
            return false;
        }

        if (storeUSBPictureDirUsbFile == null || storeUSBFs == null) {
            Log.e(TAG, "uploadToUSB: uploadFileDirUsbFile == null");
            return false;
        }

        long time = System.currentTimeMillis();
        long fileSize = 0;
        Log.d(TAG, "uploadToUSB: localFile =" + localFile);
        UsbFileOutputStream os = null;
        InputStream is = null;
        try {

            UsbFile yearMonthUsbFile = null;
            UsbFile[] usbFileList = storeUSBPictureDirUsbFile.listFiles();

            if (usbFileList != null) {
                for (UsbFile usbFile : usbFileList) {
                    if (usbFile.getName().contains(yearMonth)) {
                        yearMonthUsbFile = usbFile;
                        break;
                    }
                }
            }


            if (yearMonthUsbFile == null) {
                yearMonthUsbFile = storeUSBPictureDirUsbFile.createDirectory(yearMonth);
            }

            UsbFile create = yearMonthUsbFile.createFile(localFile.getName());
            os = new UsbFileOutputStream(create);
            is = new FileInputStream(localFile);
            fileSize = is.available();
            int bytesRead;
            byte[] buffer = new byte[storeUSBFs.getChunkSize()];
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            VariableInstance.getInstance().downdNum++;
            long time11 = ((System.currentTimeMillis() - time) / 1000);
            if (time11 == 0)
                time11 = 1;

            String speed = fileSize / time11 / 1024 + "";
            Log.d(TAG, "uploadToUSB: 上传USB速度：speed =" + speed + ",fileSize =" + fileSize);
            storeUSBListener.storeUSBSaveOnePictureComplete(speed);
        } catch (Exception e) {
            if (e.toString().contains("Item already exists")) {
                Log.d(TAG, "uploadToUSB: U盘已存在同名文件");
            } else
                Log.e(TAG, "uploadToUSB: 111111111 Exception =" + e);
        } finally {
            try {
                if (os != null) {
                    os.flush();
                    os.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (Exception e) {
            }
        }
        Log.d(TAG, "uploadToUSB:完成 ");
        return true;
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED: {
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.e(TAG, "onReceive: ACTION_USB_DEVICE_ATTACHED ");
                if (usbDevice == null) {
                    return;
                }

                if (VariableInstance.getInstance().storeUSBDeviceID == -1) {
                    initStoreUSBDevice();
                }
            }
            break;
            case UsbManager.ACTION_USB_DEVICE_DETACHED: {
                Log.e(TAG, "onReceive: 断开USB设备");
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice == null) {
                    Log.e(TAG, "onReceive: mUSBDevice == null");
                    return;
                }
                try {
                    UsbMassStorageDevice usbMassStorageDevice = getUsbMass(usbDevice);
                    if (usbMassStorageDevice != null) {
                        usbMassStorageDevice.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onReceive: 设备断开异常 e =" + e);
                }
                Log.e(TAG, "onReceive:断开USB设备 mUSBDevice id = " + usbDevice.getDeviceId() + ",deviceID =" + VariableInstance.getInstance().storeUSBDeviceID);
                if (usbDevice.getDeviceId() == VariableInstance.getInstance().storeUSBDeviceID) {
                    stopStoreUSBInitThreadExecutor();
                    storeUSBListener.storeUSBDeviceDetached();
                    VariableInstance.getInstance().downdNum = 0;
                    VariableInstance.getInstance().uploadNum = 0;
                    VariableInstance.getInstance().usbFileNameList.clear();
                } else {
                    getUSBPictureCount();
                }
            }
            break;

            case INIT_STORE_USB_PERMISSION:
                Log.d(TAG, "onReceive: CHECK_UPLOAD_PERMISSION");
                initStoreUSBDevice();
                break;
            default:
                break;
        }
    }


    public void initStoreUSBDevice() {
        Log.e(TAG, "initStoreUSBDevice: ");
        stopStoreUSBInitThreadExecutor();
        initStoreUSBThreadExecutor = Executors.newSingleThreadExecutor();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (usbManager == null)
                    usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);

                if (usbManager == null) {
                    Log.e(TAG, "initStoreUSBDevice: usbManager==null");
                    return;
                }
                //获取所有已连接上的USB列表
                HashMap<String, UsbDevice> allConnectedUSBDeviceList = usbManager.getDeviceList();
                if (allConnectedUSBDeviceList == null || allConnectedUSBDeviceList.size() <= 0) {
                    return;
                }

                Log.d(TAG, "initStoreUSBDevice: " + "当前连接设备个数:" + allConnectedUSBDeviceList.size());

                Collection<UsbDevice> usbDevices = allConnectedUSBDeviceList.values();

                if (usbDevices == null)
                    return;

                for (UsbDevice usbDevice : usbDevices) {
                    if (usbDevice == null) {
                        Log.e(TAG, "initStoreUSBDevice run: allConnectedUSBDeviceList usbDevice = null ");
                        continue;
                    }

                    if (!usbManager.hasPermission(usbDevice)) {
                        Log.e(TAG, "usbDeviceScaner: hasPermission =false");
                        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(MyApplication.getContext(), 0, new Intent(INIT_STORE_USB_PERMISSION), 0);
                        usbManager.requestPermission(usbDevice, pendingIntent);
                        continue;
                    }

                    //遍历连接的设备接口
                    int interfaceCount = usbDevice.getInterfaceCount();
                    Log.e(TAG, "initStoreUSBDevice run: 遍历连接的设备接口 interfaceCount =" + interfaceCount);
                    for (int i = 0; i < interfaceCount; i++) {
                        UsbInterface usbInterface = usbDevice.getInterface(i);
                        if (usbInterface == null)
                            continue;
                        int interfaceClass = usbInterface.getInterfaceClass();

                        if (interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                            Log.d(TAG, "initStoreUSBDevice: deviceID =" + VariableInstance.getInstance().storeUSBDeviceID);
                            UsbMassStorageDevice device = getUsbMass(usbDevice);
                            boolean initSucceed = initDevice(device, usbDevice);
                            Log.e(TAG, "initStoreUSBDevice run: initSucceed =" + initSucceed);
                            if (initSucceed) {
                                return;
                            }
                        }
                    }
                }
            }
        };
        initStoreUSBThreadExecutor.execute(runnable);
    }


    private boolean initDevice(UsbMassStorageDevice device, UsbDevice usbDevice) {
        if (device == null) {
            Log.e(TAG, "initDevice: device == null");
            return false;
        }
        try {
            device.init();
        } catch (Exception e) {
            Log.e(TAG, "initDevice :device.init() error:" + e);
            return false;
        }

        if (device.getPartitions().size() <= 0) {
            Log.e(TAG, "initDevice: " + "device.getPartitions().size() error");
            return false;
        }
        Partition partition = device.getPartitions().get(0);
        FileSystem currentFs = partition.getFileSystem();
        UsbFile mRootFolder = currentFs.getRootDirectory();

        try {
            UsbFile[] usbFileList = mRootFolder.listFiles();
            for (UsbFile usbFileItem : usbFileList) {
                if (usbFileItem.getName().contains(VariableInstance.getInstance().LogcatDirName)) {
                    storeUSBLogcatDirUsbFile = usbFileItem;
                } else if (usbFileItem.getName().contains(VariableInstance.getInstance().wifiConfigurationFileName)) {
                    storeUSBWifiConfigurationFile = usbFileItem;
                } else if (usbFileItem.getName().contains(VariableInstance.getInstance().PictureDirName)) {
                    storeUSBFs = currentFs;
                    storeUSBPictureDirUsbFile = usbFileItem;
                    VariableInstance.getInstance().storeUSBDeviceID = usbDevice.getDeviceId();
                }
            }

            if (storeUSBPictureDirUsbFile == null) {
                storeUSBFs = currentFs;
                storeUSBPictureDirUsbFile = mRootFolder.createDirectory(VariableInstance.getInstance().PictureDirName);
                VariableInstance.getInstance().storeUSBDeviceID = usbDevice.getDeviceId();
            }

            if (storeUSBLogcatDirUsbFile == null)
                storeUSBLogcatDirUsbFile = mRootFolder.createDirectory(VariableInstance.getInstance().LogcatDirName);


        } catch (Exception e) {
            Log.e(TAG, "run: initDevice Exception =" + e);
        }


        Log.d(TAG, "usbDeviceScaner: deviceID =" + VariableInstance.getInstance().storeUSBDeviceID);
        if (VariableInstance.getInstance().storeUSBDeviceID == -1) {
            return false;
        } else {
            getUSBPictureCount();
            storeUSBListener.initStoreUSBComplete(storeUSBWifiConfigurationFile);
        }
        return true;
    }


    public int getUSBPictureCount() {
        if (storeUSBPictureDirUsbFile == null)
            return -1;
        VariableInstance.getInstance().usbFileNameList.clear();
        getStoreUSBPictureCount(storeUSBPictureDirUsbFile);
        storeUSBListener.storeUSBPictureCount(VariableInstance.getInstance().usbFileNameList.size());
        return VariableInstance.getInstance().usbFileNameList.size();
    }


    private void getStoreUSBPictureCount(UsbFile usbFile) {
        try {
            UsbFile[] usbFileList = usbFile.listFiles();
            for (UsbFile usbFileItem : usbFileList) {
                if (usbFileItem.isDirectory()) {
                    getStoreUSBPictureCount(usbFileItem);
                } else {
                    String name = usbFileItem.getName();
                    String FileEnd = name.substring(usbFileItem.getName().lastIndexOf(".") + 1).toLowerCase();
                    if (pictureFormatFile(FileEnd)) {
                        VariableInstance.getInstance().usbFileNameList.add(name);
                    }
                }
            }
        } catch (Exception e) {

        }
    }


    private boolean pictureFormatFile(String FileEnd) {
        if ((FileEnd.equals("nif") || FileEnd.equals("crw") || FileEnd.equals("raw")
                || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("crw")
                || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2")) || (FileEnd.equals("jpg")))
            return true;
        return false;
    }


    /**
     * USBDevice 转换成UsbMassStorageDevice 对象
     */
    private UsbMassStorageDevice getUsbMass(UsbDevice usbDevice) {
        UsbMassStorageDevice[] storageDevices = UsbMassStorageDevice.getMassStorageDevices(MyApplication.getContext());

        for (UsbMassStorageDevice device : storageDevices) {
            if (usbDevice.equals(device.getUsbDevice())) {
                return device;
            }
        }
        return null;
    }


    public interface StoreUSBListener {

        void storeUSBPictureCount(int count);

        void initStoreUSBComplete(UsbFile wifiConfigurationFile);

        void storeUSBDeviceDetached();

        void formatStoreUSBException(boolean exception);

        void storeUSBSaveOnePictureComplete(String speed);

    }

}

