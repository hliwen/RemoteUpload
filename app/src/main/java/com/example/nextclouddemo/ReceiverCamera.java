package com.example.nextclouddemo;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;

import com.example.nextclouddemo.model.UploadFileModel;
import com.example.nextclouddemo.utils.Log;
import com.example.nextclouddemo.utils.Utils;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jahnen.libaums.core.UsbMassStorageDevice;
import me.jahnen.libaums.core.fs.FileSystem;
import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;
import me.jahnen.libaums.core.partition.Partition;

public class ReceiverCamera extends BroadcastReceiver {
    private static final String TAG = "MainActivitylog2";
    public static final String CHECK_PERMISSION = "CHECK_PERMISSION";
    private static final int FormatDay = 14;
    private CameraScanerListener downloadFlieListener;
    private ExecutorService scanerThreadExecutor;
    private UsbManager usbManager;
    private int cameraDeviceID;
    private int cameraTotalPicture;
    private int requestPerminssCount;

    private ArrayList<SameDayPicutreInfo> pictureInfoList;


    class SameDayPicutreInfo {
        public int yearMonthDay;
        public ArrayList<PictureInfo> rowPictureInfos;
        public ArrayList<PictureInfo> jpgPictureInfos;

        public SameDayPicutreInfo(int yearMonthDay) {
            this.yearMonthDay = yearMonthDay;
            rowPictureInfos = new ArrayList<>();
            jpgPictureInfos = new ArrayList<>();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SameDayPicutreInfo)) return false;
            SameDayPicutreInfo that = (SameDayPicutreInfo) o;
            return yearMonthDay == that.yearMonthDay && Objects.equals(yearMonthDay, that.yearMonthDay);
        }
    }

    class PictureInfo {
        public boolean mtpModel;
        public String pictureName;
        public long pictureCreateData;
        public int mtpPictureID;
        public UsbFile cameraUsbFile;
        public FileSystem cameraUsbFileSystem;
        public boolean isJpg;

        public PictureInfo(boolean mtpModel, String pictureName, long pictureCreateData, int mtpPictureID, FileSystem usbFileSystem, UsbFile usbFile, boolean isJpg) {
            this.mtpModel = mtpModel;
            this.pictureName = pictureName;
            this.pictureCreateData = pictureCreateData;
            this.mtpPictureID = mtpPictureID;
            this.cameraUsbFile = usbFile;
            this.cameraUsbFileSystem = usbFileSystem;
            this.isJpg = isJpg;
        }

        @Override
        public String toString() {
            return pictureName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PictureInfo)) return false;
            PictureInfo that = (PictureInfo) o;
            return isJpg == that.isJpg && Objects.equals(pictureName, that.pictureName);
        }
    }

    public class order implements Comparator<PictureInfo> {
        @Override
        public int compare(PictureInfo lhs, PictureInfo rhs) {
            if (rhs.pictureCreateData > lhs.pictureCreateData) return -1;
            else if (rhs.pictureCreateData == lhs.pictureCreateData) return 0;
            return 1;
        }
    }

    public class SameOrder implements Comparator<SameDayPicutreInfo> {
        @Override
        public int compare(SameDayPicutreInfo lhs, SameDayPicutreInfo rhs) {
            if (rhs.yearMonthDay > lhs.yearMonthDay) return -1;
            else if (rhs.yearMonthDay == lhs.yearMonthDay) return 0;
            return 1;
        }
    }


    public ReceiverCamera(Context context, CameraScanerListener downloadFlieListener) {
        this.downloadFlieListener = downloadFlieListener;
        requestPerminssCount = 0;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        pictureInfoList = new ArrayList<>();
        Utils.makeDir(VariableInstance.getInstance().TFCardPictureDir);
        Utils.makeDir(VariableInstance.getInstance().TFCardUploadPictureDir);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                if (downloadFlieListener != null) {
                    downloadFlieListener.cameraDeviceAttached();
                }
                usbConnect(intent.getParcelableExtra(UsbManager.EXTRA_DEVICE), context);
                break;
            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                usbDissConnect(intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                break;
            case CHECK_PERMISSION:
                Log.e(TAG, "onReceive:CHECK_PERMISSION");
                checkConnectedDevice(intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                break;
            default:
                break;
        }
    }


    public void openDeviceTimeOut() {
        UsbManager usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            Log.d(TAG, "openDeviceTimeOut:usbManager==null ");
            return;
        }
        HashMap<String, UsbDevice> connectedUSBDeviceList = usbManager.getDeviceList();
        if (connectedUSBDeviceList == null || connectedUSBDeviceList.size() <= 0) {
            Log.e(TAG, "openDeviceTimeOut:  没有检测到有设备列表");
            return;
        }
        Collection<UsbDevice> usbDevices = connectedUSBDeviceList.values();
        if (usbDevices == null) {
            Log.e(TAG, "openDeviceTimeOut:  没有检测到有设备接入");
            return;
        }

        Log.e(TAG, "openDeviceTimeOut: usbDevices.size =" + usbDevices.size());

        for (UsbDevice usbDevice : usbDevices) {
            if (usbDevice == null) {
                continue;
            }

            String usbProductName = usbDevice.getProductName();
            int deviceID = usbDevice.getDeviceId();
            Log.e(TAG, "openDeviceTimeOut: 当前设备名称：" + usbProductName + ",deviceID = " + deviceID + ",storeUSBDeviceID =" + VariableInstance.getInstance().storeUSBDeviceID);

            if (usbProductName == null) {
                continue;
            }

            usbProductName = usbProductName.trim();
            if (usbProductName.contains("802.11n NIC") || usbProductName.contains("USB Optical Mouse") || usbProductName.contains("USB Charger") || usbProductName.startsWith("EC25") || usbProductName.startsWith("EG25") || usbProductName.startsWith("EC20") || usbProductName.startsWith("EC200T")) {
                continue;
            }

            if (deviceID == VariableInstance.getInstance().storeUSBDeviceID) {
                continue;
            }

            if (!usbManager.hasPermission(usbDevice)) {
                Log.e(TAG, "openDeviceTimeOut: 当前设备没有授权");
                @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(MyApplication.getContext(), 0, new Intent(CHECK_PERMISSION), 0);
                usbManager.requestPermission(usbDevice, pendingIntent);
                continue;
            }

            for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
                UsbInterface usbInterface = usbDevice.getInterface(i);
                if (usbInterface == null) {
                    continue;
                }

                switch (usbInterface.getInterfaceClass()) {
                    case UsbConstants.USB_CLASS_STILL_IMAGE:
                    case UsbConstants.USB_CLASS_MASS_STORAGE:
                        Log.e(TAG, "openDeviceTimeOut: faceClass = " + usbInterface.getInterfaceClass());
                        checkConnectedDevice(usbDevice);
                        break;
                    default:
                        break;
                }

            }
        }
    }

    private void usbConnect(UsbDevice usbDevice, Context context) {
        Log.e(TAG, "onReceive: ACTION_USB_DEVICE_ATTACHED");
        if (usbDevice == null || usbDevice.getDeviceId() == -1 || VariableInstance.getInstance().storeUSBDeviceID == -1) {
            return;
        }

        if (usbDevice.getDeviceId() == VariableInstance.getInstance().storeUSBDeviceID) {
            return;
        }

        String usbProductName = usbDevice.getProductName();
        Log.e(TAG, "usbConnect: usbProductName =" + usbProductName);

        if (usbProductName == null) {
            return;
        }

        usbProductName = usbProductName.trim();
        if (usbProductName.contains("802.11n NIC") || usbProductName.contains("USB Optical Mouse") || usbProductName.contains("USB Charger") || usbProductName.startsWith("EC25") || usbProductName.startsWith("EG25") || usbProductName.startsWith("EC20") || usbProductName.startsWith("EC200T")) {
            return;
        }


        try {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usbManager.hasPermission(usbDevice)) {
                checkConnectedDevice(usbDevice);
            } else {
                VariableInstance.getInstance().errorLogNameList.add(ErrorName.相机无权限重新授权);
                Log.e(TAG, "onReceive: 接收到相机挂载 ，相机无权限，重新授权,productName:" + usbDevice.getProductName());
                @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, new Intent(CHECK_PERMISSION), 0);
                usbManager.requestPermission(usbDevice, pendingIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "ACTION_USB_DEVICE_ATTACHED: 相机挂载异常 =" + e);
        }
    }

    private void usbDissConnect(UsbDevice usbDevice) {
        if (usbDevice == null) {
            return;
        }
        try {
            UsbMassStorageDevice usbMassStorageDevice = getUsbMass(usbDevice);
            if (usbMassStorageDevice != null) {
                usbMassStorageDevice.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "onReceive:ACTION_USB_DEVICE_DETACHED 设备断开异常 e =" + e);
        }
        Log.e(TAG, "onReceive:断开USB设备，设备id = " + usbDevice.getDeviceId() + ",cameraDeviceID =" + cameraDeviceID + ",storeUSBDeviceID =" + VariableInstance.getInstance().storeUSBDeviceID);
        if (usbDevice.getDeviceId() != VariableInstance.getInstance().storeUSBDeviceID && cameraDeviceID == usbDevice.getDeviceId()) {
            Log.e(TAG, "onReceive:ACTION_USB_DEVICE_DETACHED 相机断开");
            cameraDeviceID = -1;
            pictureInfoList.clear();
            downloadFlieListener.cameraDeviceDetached();
            stopScanerThread(4);
        }
    }

    private void stopScanerThread(int position) {
        Log.d(TAG, "stopScanerThread: position =" + position);
        try {
            if (scanerThreadExecutor != null) {
                scanerThreadExecutor.shutdown();
            }
        } catch (Exception e) {

        }
        scanerThreadExecutor = null;
    }

    public void storeUSBDetached() {
        pictureInfoList.clear();
        stopScanerThread(1);
    }

    public void formatCamera() {
        try {
            stopScanerThread(2);
            pictureInfoList.clear();
        } catch (Exception e) {

        }
    }

    private void checkConnectedDevice(UsbDevice device) {  /*  cameraDeviceID = usbDevice.getDeviceId();*/
        Log.e(TAG, "checkConnectedDevice:  start.......................");

        stopScanerThread(3);
        scanerThreadExecutor = Executors.newSingleThreadExecutor();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (usbManager == null) {
                    usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
                }
                if (usbManager == null) {
                    Log.e(TAG, "checkConnectedDevice run: usbManager==null ");
                    return;
                }

                try {
                    if (device == null) {
                        Log.e(TAG, "checkConnectedDevice run: device==null");
                        return;
                    }


                    if (device.getDeviceId() == VariableInstance.getInstance().storeUSBDeviceID) {
                        Log.e(TAG, "USB_CLASS_MASS_STORAGE 检测到当前的U盘设备为上传usb, 结束扫描");
                        return;
                    }


                    String devieName = device.getProductName();

                    Log.e(TAG, "checkConnectedDevice: productName =" + devieName);

                    try {
                        if (!usbManager.hasPermission(device)) {
                            Log.e(TAG, "checkConnectedDevice: 无法扫描相机，权限未获取,productName" + device.getProductName());
                            VariableInstance.getInstance().errorLogNameList.add(ErrorName.相机无权限重新授权);

                            requestPerminssCount++;
                            @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(MyApplication.getContext(), 0, new Intent(CHECK_PERMISSION), 0);
                            usbManager.requestPermission(device, pendingIntent);
                            if (requestPerminssCount < 10) {
                                Log.e(TAG, "checkConnectedDevice: 连续申请10次权限都无法扫描相机，没有后续操作");
                                VariableInstance.getInstance().errorLogNameList.add(ErrorName.连续申请10次权限都无法扫描相机没有后续操作);
                                downloadFlieListener.cameraOperationEnd(cameraTotalPicture);
                                return;
                            }
                        }
                    } catch (Exception e) {

                    }


                    boolean isMassStorage = false;
                    boolean needScaner = false;

                    for (int i = 0; i < device.getInterfaceCount(); i++) {
                        UsbInterface usbInterface = device.getInterface(i);
                        if (usbInterface == null) {
                            continue;
                        }

                        Log.e(TAG, "checkConnectedDevice: interfaceClass =" + usbInterface.getInterfaceClass());

                        switch (usbInterface.getInterfaceClass()) {
                            case UsbConstants.USB_CLASS_STILL_IMAGE:
                                isMassStorage = false;
                                needScaner = true;
                                break;
                            case UsbConstants.USB_CLASS_MASS_STORAGE:
                                isMassStorage = true;
                                needScaner = true;
                                break;
                            default:
                                break;
                        }
                    }

                    if (!needScaner) {
                        return;
                    }

                    downloadFlieListener.cameraOperationStart();
                    if (isMassStorage) {
                        usbDeviceScaner(device);
                    } else {
                        mtpDeviceScaner(device);
                    }
                    VariableInstance.getInstance().isFormaringCamera.formatState = 0;
                    downloadFlieListener.cameraOperationEnd(cameraTotalPicture);

                } catch (Exception e) {
                }

            }
        };
        if (scanerThreadExecutor != null) {
            scanerThreadExecutor.execute(runnable);
        }
    }

    private void mtpDeviceScaner(UsbDevice usbDevice) {
        Log.d(TAG, "mtpDeviceScaner mtp模式相机开始扫描................................");
        UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(usbDevice);
        if (usbDeviceConnection == null) {
            Log.e(TAG, "mtpDeviceScaner: usbDeviceConnection == null 打开相机失败，结束扫描");
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.打开相机失败结束扫描);
            return;
        }


        MtpDevice mtpDevice = new MtpDevice(usbDevice);
        if (mtpDevice == null) {
            Log.e(TAG, "mtpDeviceScaner 数码相机打开失败 mtpDevice == null 初始usb设备为mtp设备失败，结束扫描");
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.创建mtp模式失败结束扫描);
            return;
        }

        if (!mtpDevice.open(usbDeviceConnection)) {
            Log.e(TAG, "mtpDeviceScaner 数码相机打开失败 mtpDevice.open 打开Mtp失败 结束扫描");
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.打开mtp模式失败结束扫描);
            return;
        }

        cameraDeviceID = usbDevice.getDeviceId();

        int[] storageIds = mtpDevice.getStorageIds();
        if (storageIds == null || storageIds.length == 0) {
            Log.e(TAG, "mtpDeviceScaner: 数码相机存储卷不可用 storageIds == null 结束扫描");
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.mtp模式获取分区失败结束扫描);
            return;
        }

        Log.e(TAG, "mtpDeviceScaner: 设备一共几个盘符，storageIds.length =" + storageIds.length);

        pictureInfoList.clear();
        cameraTotalPicture = 0;

        for (int storageId : storageIds) {
            int[] pictureHandlesItem = mtpDevice.getObjectHandles(storageId, 0, 0);
            Log.e(TAG, "mtpDeviceScaner: 获取当前盘符全部照片数组,pictureHandlesItem =" + pictureHandlesItem);
            if (pictureHandlesItem != null) {
                Log.e(TAG, "mtpDeviceScaner: 当前盘符全部文件总数，pictureHandlesItem.lenght =" + pictureHandlesItem.length);
                for (int i : pictureHandlesItem) {
                    while (VariableInstance.getInstance().isScanningStoreUSB) {
                        Log.e(TAG, "mtpDeviceScaner: 正在扫描U盘，暂停相机扫描");
                        if (cameraDeviceID == -1) {
                            return;
                        }
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {

                        }
                    }

                    if (cameraDeviceID == -1) {
                        return;
                    }

                    MtpObjectInfo mtpObjectInfo = mtpDevice.getObjectInfo(i);

                    if (mtpObjectInfo == null) {
                        Log.e(TAG, "mtpDeviceScaner: mtpObjectInfo == null,当前文件信息无法获取");
                        continue;
                    }
                    long createDate = mtpObjectInfo.getDateCreated() - 1000L * 60 * 60 * 8;
                    int yymmdd = Utils.getyyMMddtringInt(createDate);
                    String pictureName = yymmdd + "-" + mtpObjectInfo.getName();
                    String FileEnd = pictureName.substring(pictureName.lastIndexOf(".") + 1).toLowerCase();

                    if (!pictureFormatFile(FileEnd)) {
                        continue;
                    }

                    if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
                        if (VariableInstance.getInstance().isFormaringCamera.formatState == 1) {
                            boolean delectResult = mtpDevice.deleteObject(i);
                            if (!delectResult) {
                                Log.e(TAG, "mtpDeviceScaner: 格式化过程中，删除照片失败，name = " + mtpObjectInfo.getName());
                            }
                            continue;
                        } else if (VariableInstance.getInstance().isFormaringCamera.formatState == 2) {
                            int systemTime = Utils.getyyMMddtringInt(System.currentTimeMillis());
                            if (systemTime == 900101) {
                                continue;
                            }
                            if (systemTime - yymmdd > FormatDay) {
                                boolean delectResult = mtpDevice.deleteObject(i);
                                if (!delectResult) {
                                    Log.e(TAG, "mtpDeviceScaner: 格式化过程中，删除照片失败，name = " + mtpObjectInfo.getName());
                                }
                            }
                            continue;
                        }

                    }
                    cameraTotalPicture++;

                    SameDayPicutreInfo sameDayPicutreInfo = new SameDayPicutreInfo(yymmdd);
                    int index = pictureInfoList.indexOf(sameDayPicutreInfo);
                    if (index > -1) {
                        sameDayPicutreInfo = pictureInfoList.get(index);
                    } else {
                        pictureInfoList.add(sameDayPicutreInfo);
                    }

                    if (needDownloadCameraPictureToUSB(pictureName)) {
                        if (rowFormatFile(FileEnd)) {
                            PictureInfo pictureInfo = new PictureInfo(true, pictureName, createDate, i, null, null, false);
                            sameDayPicutreInfo.rowPictureInfos.add(pictureInfo);
                        } else if (jPGFormatFile(FileEnd)) {
                            PictureInfo pictureInfo = new PictureInfo(true, pictureName, createDate, i, null, null, true);
                            sameDayPicutreInfo.jpgPictureInfos.add(pictureInfo);
                        }
                    }
                }
            }
        }

        Collections.sort(pictureInfoList, new SameOrder());

        int pictureCount = 0;
        for (SameDayPicutreInfo sameDayPicutreInfo : pictureInfoList) {

            if (cameraDeviceID == -1) {
                break;
            }

            Collections.sort(sameDayPicutreInfo.jpgPictureInfos, new order());
            Collections.sort(sameDayPicutreInfo.rowPictureInfos, new order());
            pictureCount += sameDayPicutreInfo.jpgPictureInfos.size();
            pictureCount += sameDayPicutreInfo.rowPictureInfos.size();
        }


        Log.d(TAG, "mtpDeviceScaner: 扫描到需要下载图片 ：" + pictureCount + ",相机总共照片 cameraTotalPicture =" + cameraTotalPicture + ",deviceName = " + usbDevice.getProductName());

        VariableInstance.getInstance().isConnectCamera = true;
        downloadFlieListener.scannerCameraComplete(pictureCount, cameraTotalPicture, usbDevice.getProductName());

        for (SameDayPicutreInfo pictureItem : pictureInfoList) {
            if (cameraDeviceID == -1) {
                break;
            }
            if (VariableInstance.getInstance().UploadMode == 1) {
                for (PictureInfo pictureInfo : pictureItem.rowPictureInfos) {
                    downloadMTPCameraPictureToTFCard(mtpDevice, pictureInfo, true);
                }
                for (PictureInfo pictureInfo : pictureItem.jpgPictureInfos) {
                    downloadMTPCameraPictureToTFCard(mtpDevice, pictureInfo, false);
                }
            } else if (VariableInstance.getInstance().UploadMode == 2) {
                for (PictureInfo pictureInfo : pictureItem.rowPictureInfos) {
                    downloadMTPCameraPictureToTFCard(mtpDevice, pictureInfo, false);
                }
                for (PictureInfo pictureInfo : pictureItem.jpgPictureInfos) {
                    downloadMTPCameraPictureToTFCard(mtpDevice, pictureInfo, true);
                }
            } else if (VariableInstance.getInstance().UploadMode == 3) {
                for (int i = 0; i < pictureItem.rowPictureInfos.size(); i++) {
                    boolean needUpload = false;
                    Integer integer = i + 1;
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    Log.d(TAG, "mtpDeviceScaner: index =" + index + ",integer =" + integer);
                    if (index > -1) {
                        needUpload = true;
                    }
                    downloadMTPCameraPictureToTFCard(mtpDevice, pictureItem.rowPictureInfos.get(i), needUpload);
                }

                for (int i = 0; i < pictureItem.jpgPictureInfos.size(); i++) {
                    boolean needUpload = false;
                    Integer integer = i + 1;
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    Log.d(TAG, "mtpDeviceScaner: index =" + index + ",integer =" + integer);
                    if (index > -1) {
                        needUpload = true;
                    }
                    downloadMTPCameraPictureToTFCard(mtpDevice, pictureItem.jpgPictureInfos.get(i), needUpload);
                }

            } else if (VariableInstance.getInstance().UploadMode == 4) {
                for (int i = 0; i < pictureItem.rowPictureInfos.size(); i++) {
                    Integer integer = i + 1;
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1) {
                        downloadMTPCameraPictureToTFCard(mtpDevice, pictureItem.rowPictureInfos.get(i), true);
                    }
                }

                for (int i = 0; i < pictureItem.jpgPictureInfos.size(); i++) {
                    Integer integer = i + 1;
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1) {
                        downloadMTPCameraPictureToTFCard(mtpDevice, pictureItem.jpgPictureInfos.get(i), true);
                    }
                }
            }
        }

        usbDeviceConnection.close();
        mtpDevice.close();
    }

    private void downloadMTPCameraPictureToTFCard(MtpDevice mtpDevice, PictureInfo pictureInfo, boolean needUpload) {

        if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
            Log.e(TAG, "downloadMTPCameraPictureToTFCard: 正在格式化相机，不做处理");
            return;
        }

        Log.d(TAG, "downloadMTPCameraPictureToTFCard: pictureInfo =" + pictureInfo + ",needUpload =" + needUpload);
        try {
            String pictureSaveLocalPath = VariableInstance.getInstance().TFCardPictureDir + File.separator + pictureInfo.pictureName;
            File pictureSaveFile = new File(pictureSaveLocalPath);
            if (pictureSaveFile != null && pictureSaveFile.exists()) {
                pictureSaveFile.delete();
            }

            if (cameraDeviceID == -1) {
                Log.e(TAG, "downloadMTPCameraPictureToTFCard: 相机已断开，停止下载");
                stopScanerThread(5);
                return;
            }


            boolean importResult = mtpDevice.importFile(pictureInfo.mtpPictureID, pictureSaveLocalPath);

            if (!importResult) {
                Log.e(TAG, "downloadMTPCameraPictureToTFCard: 111 导出相机照片失败");
            }

            if (pictureSaveFile != null && pictureSaveFile.exists()) {

                String yearMonth = Utils.getyyyyMMtring(pictureInfo.pictureCreateData);
                boolean uploadSucceed = downloadFlieListener.uploadToUSB(pictureSaveFile, yearMonth);
                pictureSaveFile.delete();

                if (uploadSucceed) {
                    if (needUpload) {
                        Utils.checkSDAvailableSize();
                        String pictureSaveUploadLocalPath = VariableInstance.getInstance().TFCardUploadPictureDir + File.separator + pictureInfo.pictureName;
                        File pictureUploadSaveFile = new File(pictureSaveUploadLocalPath);
                        if (pictureUploadSaveFile != null && pictureUploadSaveFile.exists()) {
                            pictureUploadSaveFile.delete();
                        }
                        importResult = mtpDevice.importFile(pictureInfo.mtpPictureID, pictureSaveUploadLocalPath);
                        if (!importResult) {
                            Log.e(TAG, "downloadMTPCameraPictureToTFCard:222 导出相机照片失败");
                        }
                        if (pictureUploadSaveFile != null && pictureUploadSaveFile.exists()) {
                            downloadFlieListener.addUploadRemoteFile(new UploadFileModel(pictureSaveUploadLocalPath));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "downloadMTPCameraPictureToTFCard: 图片复制出错 e =" + e);
            String error = ErrorName.mtp导出相机照片失败 + ":" + e.toString();
            VariableInstance.getInstance().errorLogNameList.add(error);
        }
        Log.e(TAG, "downloadMTPCameraPictureToTFCard: end");
    }


    private void usbDeviceScaner(UsbDevice usbDevice) {
        Log.d(TAG, "usbDeviceScaner start");
        if (usbDevice == null) {
            Log.e(TAG, "usbDeviceScaner: USB设备为空 结束扫描");
            return;
        }

        cameraDeviceID = usbDevice.getDeviceId();

        UsbMassStorageDevice device = getUsbMass(usbDevice);
        if (device == null) {
            Log.e(TAG, "usbDeviceScaner: device == null 结束扫描");
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.USB模式创建访问相机模式失败);
            return;
        }
        try {
            device.init();
        } catch (Exception e) {
            Log.e(TAG, "usbDeviceScaner: 结束扫描 device.init() error:" + e);
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.USB模式初始化访问相机失败);
            return;
        }

        if (device.getPartitions().size() <= 0) {
            Log.e(TAG, "usbDeviceScaner: " + "device.getPartitions().size() error 结束扫描");
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.USB模式获取分区失败结束扫描);
            return;
        }
        Partition partition = device.getPartitions().get(0);
        FileSystem currentFs = partition.getFileSystem();
        UsbFile mRootFolder = currentFs.getRootDirectory();

        pictureInfoList.clear();
        cameraTotalPicture = 0;
        readPicFileFromUSBFile(currentFs, mRootFolder);

        Collections.sort(pictureInfoList, new SameOrder());
        int pictureCount = 0;
        for (SameDayPicutreInfo sameDayPicutreInfo : pictureInfoList) {
            Collections.sort(sameDayPicutreInfo.jpgPictureInfos, new order());
            Collections.sort(sameDayPicutreInfo.rowPictureInfos, new order());
            pictureCount += sameDayPicutreInfo.jpgPictureInfos.size();
            pictureCount += sameDayPicutreInfo.rowPictureInfos.size();
        }

        Log.d(TAG, "usbDeviceScaner: 扫描到需要下载图片 ：" + pictureCount + ",相机总共照片 cameraTotalPicture =" + cameraTotalPicture + ",deviceName = " + usbDevice.getProductName());

        VariableInstance.getInstance().isConnectCamera = true;

        downloadFlieListener.scannerCameraComplete(pictureCount, cameraTotalPicture, usbDevice.getProductName());

        for (SameDayPicutreInfo pictureItem : pictureInfoList) {

            if (cameraDeviceID == -1) {
                break;
            }

            if (VariableInstance.getInstance().UploadMode == 1) {
                for (PictureInfo pictureInfo : pictureItem.rowPictureInfos) {
                    downloadUSBCameraPictureToTFCard(pictureInfo, true);
                }
                for (PictureInfo pictureInfo : pictureItem.jpgPictureInfos) {
                    downloadUSBCameraPictureToTFCard(pictureInfo, false);
                }
            } else if (VariableInstance.getInstance().UploadMode == 2) {
                for (PictureInfo pictureInfo : pictureItem.rowPictureInfos) {
                    downloadUSBCameraPictureToTFCard(pictureInfo, false);
                }
                for (PictureInfo pictureInfo : pictureItem.jpgPictureInfos) {
                    downloadUSBCameraPictureToTFCard(pictureInfo, true);
                }
            } else if (VariableInstance.getInstance().UploadMode == 3) {

                for (int i = 0; i < pictureItem.rowPictureInfos.size(); i++) {
                    boolean needUpload = false;
                    Integer integer = i + 1;
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    Log.d(TAG, "usbDeviceScaner: index =" + index + ",integer =" + integer);
                    if (index > -1) {
                        needUpload = true;
                    }
                    downloadUSBCameraPictureToTFCard(pictureItem.rowPictureInfos.get(i), needUpload);
                }

                for (int i = 0; i < pictureItem.jpgPictureInfos.size(); i++) {
                    boolean needUpload = false;
                    Integer integer = i + 1;
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    Log.d(TAG, "usbDeviceScaner: index =" + index + ",integer =" + integer);
                    if (index > -1) {
                        needUpload = true;
                    }
                    downloadUSBCameraPictureToTFCard(pictureItem.jpgPictureInfos.get(i), needUpload);
                }


            } else if (VariableInstance.getInstance().UploadMode == 4) {
                for (int i = 0; i < pictureItem.rowPictureInfos.size(); i++) {
                    Integer integer = i + 1;
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1) {
                        downloadUSBCameraPictureToTFCard(pictureItem.rowPictureInfos.get(i), true);
                    }
                }

                for (int i = 0; i < pictureItem.jpgPictureInfos.size(); i++) {
                    Integer integer = i + 1;
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1) {
                        downloadUSBCameraPictureToTFCard(pictureItem.jpgPictureInfos.get(i), true);
                    }
                }
            }
        }
    }

    private void readPicFileFromUSBFile(FileSystem fileSystem, UsbFile usbFile) {

        UsbFile[] usbFileList = new UsbFile[0];
        try {
            usbFileList = usbFile.listFiles();
        } catch (IOException e) {
            Log.e(TAG, "readPicFileFromUSBFile:usbFile.listFiles IOException e =" + e);
        }
        for (UsbFile usbFileItem : usbFileList) {

            while (VariableInstance.getInstance().isScanningStoreUSB) {
                Log.e(TAG, "mtpDeviceScaner: isScanerStoreUSB waiting");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                }
            }

            if (usbFileItem.isDirectory()) {
                readPicFileFromUSBFile(fileSystem, usbFileItem);
            } else {
                //获取文件后缀
                long createDate = usbFileItem.createdAt() - 1000L * 60 * 60 * 8;
                int yymmdd = Utils.getyyMMddtringInt(createDate);
                String name = usbFileItem.getName();
                String fileName = yymmdd + "-" + name;
                String FileEnd = name.substring(name.lastIndexOf(".") + 1).toLowerCase();

                if (!pictureFormatFile(FileEnd)) continue;
                cameraTotalPicture++;


                if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
                    if (VariableInstance.getInstance().isFormaringCamera.formatState == 1) {
                        try {
                            usbFile.delete();
                        } catch (IOException e) {
                            Log.e(TAG, "readPicFileFromUSBFile: usbFile.delete IOException =" + e);
                        }
                        continue;
                    } else if (VariableInstance.getInstance().isFormaringCamera.formatState == 2) {
                        int systemTime = Utils.getyyMMddtringInt(System.currentTimeMillis());
                        if (systemTime == 900101) {
                            continue;
                        }
                        if (systemTime - yymmdd > FormatDay) {
                            try {
                                usbFile.delete();
                            } catch (IOException e) {
                                Log.e(TAG, "readPicFileFromUSBFile: usbFile.delete IOException =" + e);
                            }
                        }
                        continue;
                    }
                }

                SameDayPicutreInfo sameDayPicutreInfo = new SameDayPicutreInfo(yymmdd);
                int index = pictureInfoList.indexOf(sameDayPicutreInfo);

                if (index > -1) {
                    sameDayPicutreInfo = pictureInfoList.get(index);
                } else {
                    pictureInfoList.add(sameDayPicutreInfo);
                }

                if (needDownloadCameraPictureToUSB(fileName)) {
                    if (rowFormatFile(FileEnd)) {
                        PictureInfo pictureInfo = new PictureInfo(false, fileName, createDate, 0, fileSystem, usbFileItem, false);
                        sameDayPicutreInfo.rowPictureInfos.add(pictureInfo);
                    } else if (jPGFormatFile(FileEnd)) {
                        PictureInfo pictureInfo = new PictureInfo(false, fileName, createDate, 0, fileSystem, usbFileItem, true);
                        sameDayPicutreInfo.jpgPictureInfos.add(pictureInfo);
                    }
                }
            }
        }

    }


    private boolean needDownloadCameraPictureToUSB(String fileName) {

        if (fileName == null) {
            return false;
        }

        if (VariableInstance.getInstance().usbFileNameList.contains(fileName)) {
            return false;
        }

        if (VariableInstance.getInstance().syncAllCameraPicture) {
            return true;
        } else {
            if (VariableInstance.getInstance().copyReferenceDate == null) {//没有格式化过
                if (VariableInstance.getInstance().cyclicDeletion) {
                    if (VariableInstance.getInstance().usbFileNameList.size() > VariableInstance.getInstance().MAX_NUM / 2) {
                        if (VariableInstance.getInstance().usbFileNameList.get(VariableInstance.getInstance().usbFileNameList.size() - 1).compareTo(fileName) < 0) {
                            return true;
                        }
                    } else {
                        return true;
                    }
                } else {
                    return true;
                }
            } else {//格式化的已格式化日期为主
                if (VariableInstance.getInstance().cyclicDeletion) {
                    if (VariableInstance.getInstance().usbFileNameList.size() > VariableInstance.getInstance().MAX_NUM / 2) {
                        if (VariableInstance.getInstance().usbFileNameList.get(VariableInstance.getInstance().usbFileNameList.size() - 1).compareTo(fileName) < 0) {
                            return true;
                        }
                    } else {
                        if (VariableInstance.getInstance().copyReferenceDate.compareTo(fileName) < 0) {
                            return true;
                        }
                    }
                } else {
                    if (VariableInstance.getInstance().copyReferenceDate.compareTo(fileName) < 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void downloadUSBCameraPictureToTFCard(PictureInfo pictureInfo, boolean needUpload) {
        Log.d(TAG, "downloadUSBCameraPictureToTFCard: pictureInfo =" + pictureInfo + ",needUpload =" + needUpload);

        if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
            Log.e(TAG, "downloadUSBCameraPictureToTFCard: 正在格式化相机，不做处理");
            return;
        }
        if (cameraDeviceID == -1) {
            Log.e(TAG, "downloadUSBCameraPictureToTFCard: 相机已断开，停止下载");
            stopScanerThread(6);
            return;
        }


        FileOutputStream out = null;
        InputStream in = null;
        String pictureSavePath = null;
        File pictureSaveLocalFile = null;

        try {
            pictureSavePath = VariableInstance.getInstance().TFCardPictureDir + File.separator + pictureInfo.pictureName;
            pictureSaveLocalFile = new File(pictureSavePath);

            if (pictureSaveLocalFile.exists()) {
                pictureSaveLocalFile.delete();
            }
            out = new FileOutputStream(pictureSavePath);
            in = new UsbFileInputStream(pictureInfo.cameraUsbFile);
            int bytesRead = 0;
            byte[] buffer = new byte[pictureInfo.cameraUsbFileSystem.getChunkSize()];
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            Log.e(TAG, "downloadUSBCameraPictureToTFCard: Exception =" + e);
        } finally {
            try {
                if (out != null) {
                    out.flush();
                    out.close();
                }

                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "downloadUSBCameraPictureToTFCard : 11111 Exception =" + e);
            }
        }

        FileOutputStream uploadout = null;
        InputStream uploadin = null;
        try {
            if (pictureSaveLocalFile != null && pictureSaveLocalFile.exists()) {
                String yearMonth = Utils.getyyyyMMtring(pictureInfo.pictureCreateData);
                boolean uploadSucceed = downloadFlieListener.uploadToUSB(pictureSaveLocalFile, yearMonth);
                pictureSaveLocalFile.delete();

                if (uploadSucceed) {
                    if (needUpload) {
                        Utils.checkSDAvailableSize();
                        String pictureSaveUploadLocalPath = VariableInstance.getInstance().TFCardUploadPictureDir + File.separator + pictureInfo.pictureName;
                        File pictureUploadSaveFile = new File(pictureSaveUploadLocalPath);
                        if (pictureUploadSaveFile.exists()) {
                            pictureUploadSaveFile.delete();
                        }

                        uploadout = new FileOutputStream(pictureSaveUploadLocalPath);
                        uploadin = new UsbFileInputStream(pictureInfo.cameraUsbFile);
                        int bytesRead = 0;
                        byte[] buffer = new byte[pictureInfo.cameraUsbFileSystem.getChunkSize()];
                        while ((bytesRead = uploadin.read(buffer)) != -1) {
                            uploadout.write(buffer, 0, bytesRead);
                        }

                        if (pictureUploadSaveFile != null && pictureUploadSaveFile.exists()) {
                            downloadFlieListener.addUploadRemoteFile(new UploadFileModel(pictureSaveUploadLocalPath));
                        }
                    }
                }
            }
        } catch (Exception e) {
            VariableInstance.getInstance().errorLogNameList.add(e.toString());
            Log.e(TAG, "downloadUSBCameraPictureToTFCard : 222222222222 Exception =" + e);
        } finally {
            try {
                if (uploadout != null) {
                    uploadout.flush();
                    uploadout.close();
                }

                if (uploadin != null) {
                    uploadin.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "downloadUSBCameraPictureToTFCard : 11111 Exception =" + e);
            }
        }

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

    private boolean pictureFormatFile(String FileEnd) {
        if ((FileEnd.equals("nif") || FileEnd.equals("raw") || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("crw") || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2") || FileEnd.equals("cr3")) || (FileEnd.equals("jpg")))
            return true;
        return false;
    }

    private boolean rowFormatFile(String FileEnd) {
        if ((FileEnd.equals("nif") || FileEnd.equals("raw") || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("crw") || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2") || FileEnd.equals("cr3"))) return true;
        return false;
    }

    private boolean jPGFormatFile(String FileEnd) {
        if ((FileEnd.equals("jpg"))) return true;
        return false;
    }

    public interface CameraScanerListener {
        void addUploadRemoteFile(UploadFileModel uploadFileModel);

        void cameraOperationStart();

        void cameraOperationEnd(int cameraTotalPicture);

        boolean uploadToUSB(File localFile, String yearMonth);

        void cameraDeviceDetached();


        void scannerCameraComplete(int needDownloadConut, int pictureCont, String deviceName);

        void cameraDeviceAttached();

    }


}

