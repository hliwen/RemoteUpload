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
import com.example.nextclouddemo.utils.LocalProfileHelp;
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
import java.util.List;
import java.util.Objects;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jahnen.libaums.core.UsbMassStorageDevice;
import me.jahnen.libaums.core.fs.FileSystem;
import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;
import me.jahnen.libaums.core.partition.Partition;

public class ReceiverCamera extends BroadcastReceiver {
    private static final String TAG = "remotelog_ReceiverCamera";
    private int requestPermissionCount;
    private CameraScanerListener downloadFlieListener;
    private ExecutorService scanerThreadExecutor;
    private UsbManager usbManager;
    private int cameraDeviceID;
    private int cameraTotalPicture;


    class SameDayPicutreInfo {
        public int yearMonthDay;
        public List<PictureInfo> rowPictureInfos;
        public List<PictureInfo> jpgPictureInfos;

        public SameDayPicutreInfo(int yearMonthDay) {
            this.yearMonthDay = yearMonthDay;
            rowPictureInfos = Collections.synchronizedList(new ArrayList<>());
            jpgPictureInfos = Collections.synchronizedList(new ArrayList<>());
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


    public ReceiverCamera(Context context, CameraScanerListener downloadFlieListener) {
        this.downloadFlieListener = downloadFlieListener;
        requestPermissionCount = 0;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        Utils.makeDir(VariableInstance.getInstance().TFCardPictureDir);
        Utils.makeDir(VariableInstance.getInstance().TFCardUploadPictureDir);
    }


    public void formatCamera() {
        try {
            stopScanerThread(2);

        } catch (Exception e) {

        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
            case VariableInstance.GET_STORE_CAMERA_PERMISSION: {
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice == null) {
                    Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ",usbDevice == null");
                    return;
                }
                Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                if (VariableInstance.getInstance().isOthreDevice(usbDevice.getProductName())) {
                    Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ", isOthreDevice");
                    return;
                }
                usbConnect(usbDevice);
            }
            break;
            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice == null) {
                    Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ",usbDevice == null");
                    return;
                }
                Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                if (VariableInstance.getInstance().isOthreDevice(usbDevice.getProductName())) {
                    Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ", isOthreDevice");
                    return;
                }
                requestPermissionCount = 0;
                usbDissConnect(usbDevice);
                break;
            default:
                break;
        }
    }

    private void usbDissConnect(UsbDevice usbDevice) {
        if (usbDevice == null) {
            return;
        }
        if (cameraDeviceID == -1) {
            Log.d(TAG, "usbDissConnect: 相机未初始化return");
            return;
        }

        try {
            UsbMassStorageDevice usbMassStorageDevice = getUsbMass(usbDevice);
            if (usbMassStorageDevice != null) {
                usbMassStorageDevice.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "usbDissConnect 设备断开异常 e =" + e);
        }

        Log.e(TAG, " usbDissConnect，usbDevice.getDeviceId() = " + usbDevice.getDeviceId() + ",getProductName= " + usbDevice.getProductName() + ",cameraDeviceID =" + cameraDeviceID + ",storeUSBDeviceID =" + VariableInstance.getInstance().storeUSBDeviceID);
        if (cameraDeviceID == usbDevice.getDeviceId()) {
            Log.e(TAG, "usbDissConnect 相机断开................................");
            cameraDeviceID = -1;
            stopScanerThread(4);
        }
    }

    private void stopScanerThread(int position) {
        Log.d(TAG, "stopScanerThread: position =" + position);
        try {
            if (scanerThreadExecutor != null) {
                scanerThreadExecutor.shutdownNow();
            }
        } catch (Exception e) {

        }
        scanerThreadExecutor = null;
    }

    public void openDeviceTimeOut() {
        Log.d(TAG, "openDeviceTimeOut: ...............................");
        if (usbManager == null) {
            usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
        }

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
            Log.e(TAG, "openDeviceTimeOut: 当前设备名称：" + usbProductName + ",deviceID = " + deviceID + ",storeUSBDeviceID =" + VariableInstance.getInstance().storeUSBDeviceID + ",cameraDeviceID=" + cameraDeviceID);

            if (usbProductName == null) {
                continue;
            }

            usbProductName = usbProductName.trim();

            if (VariableInstance.getInstance().isOthreDevice(usbProductName)) {
                continue;
            }

            if (deviceID == VariableInstance.getInstance().storeUSBDeviceID) {
                continue;
            }

            usbConnect(usbDevice);
        }
    }

    private void usbConnect(UsbDevice device) {
        if (device == null) {
            Log.d(TAG, "usbConnect: return 1 ");
            return;
        }
        String usbProductName = device.getProductName();
        Log.e(TAG, "usbConnect: usbProductName =" + usbProductName);

        if (device.getDeviceId() == -1 || device.getDeviceId() == VariableInstance.getInstance().storeUSBDeviceID) {
            Log.d(TAG, "usbConnect: return 2  device.getDeviceId() =" + device.getDeviceId());
            return;
        }


        if (usbProductName == null) {
            Log.d(TAG, "usbConnect: return 3");
            return;
        }

        stopScanerThread(3);
        scanerThreadExecutor = Executors.newSingleThreadExecutor();

        scanerThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                VariableInstance.getInstance().isOperationCamera = true;
                if (usbManager == null) {
                    usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
                }
                if (usbManager == null) {
                    Log.e(TAG, "usbConnect run: usbManager==null ");
                    VariableInstance.getInstance().isOperationCamera = false;
                    return;
                }
                if (device == null) {
                    Log.e(TAG, "usbConnect run: device==null");
                    VariableInstance.getInstance().isOperationCamera = false;
                    return;
                }


                try {
                    if (!usbManager.hasPermission(device)) {
                        if (requestPermissionCount > 10) {
                            Log.e(TAG, "usbConnect: 当前设备没有授权,申请超过10次仍然失败 :" + device.getProductName());
                            VariableInstance.getInstance().isOperationCamera = false;
                            return;
                        }
                        requestPermissionCount++;
                        Log.e(TAG, "usbConnect: 无法扫描相机，权限未获取,productName:" + device.getProductName() + ",requestPermissionCount=" + requestPermissionCount);
                        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(MyApplication.getContext(), 0, new Intent(VariableInstance.GET_STORE_CAMERA_PERMISSION), 0);
                        usbManager.requestPermission(device, pendingIntent);
                        return;
                    }
                } catch (Exception e) {

                }
                try {
                    boolean isStorageModel = false;
                    boolean isMtpModel = false;

                    for (int i = 0; i < device.getInterfaceCount(); i++) {
                        UsbInterface usbInterface = device.getInterface(i);
                        if (usbInterface == null) {
                            continue;
                        }
                        Log.e(TAG, "usbConnect: interfaceClass =" + usbInterface.getInterfaceClass());
                        switch (usbInterface.getInterfaceClass()) {
                            case UsbConstants.USB_CLASS_STILL_IMAGE:
                                isMtpModel = true;
                                break;
                            case UsbConstants.USB_CLASS_MASS_STORAGE:
                                isStorageModel = true;
                                break;
                            default:
                                break;
                        }
                    }

                    Log.e(TAG, "usbConnect run: isStorageModel =" + isStorageModel + ",isMtpModel=" + isMtpModel);
                    if (!isStorageModel && !isMtpModel) {
                        VariableInstance.getInstance().isOperationCamera = false;
                        return;
                    }

                    downloadFlieListener.cameraOperationStart();
                    if (isStorageModel) {
                        usbDeviceScaner(device);
                    }
                    if (isMtpModel) {
                        mtpDeviceScaner(device);
                    }
                    VariableInstance.getInstance().isOperationCamera = false;
                    VariableInstance.getInstance().isFormaringCamera.formatState = 0;
                    downloadFlieListener.cameraOperationEnd(cameraTotalPicture);

                } catch (Exception e) {
                }

            }
        });

    }


    private void mtpDeviceScaner(UsbDevice usbDevice) {
        Log.d(TAG, "mtpDeviceScaner mtp模式相机开始扫描................................");
        if (usbDevice == null) {
            Log.d(TAG, "mtpDeviceScaner: return 1");
            return;
        }

        cameraDeviceID = usbDevice.getDeviceId();
        cameraTotalPicture = 0;


        if (usbManager == null) {
            usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
        }

        if (usbManager == null) {
            Log.d(TAG, "mtpDeviceScaner:usbManager==null ");
            return;
        }

        UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(usbDevice);
        if (usbDeviceConnection == null) {
            Log.e(TAG, "mtpDeviceScaner: usbDeviceConnection == null 打开相机失败，结束扫描");
            return;
        }

        MtpDevice mtpDevice = new MtpDevice(usbDevice);
        if (mtpDevice == null) {
            Log.e(TAG, "mtpDeviceScaner 数码相机打开失败 mtpDevice == null 初始usb设备为mtp设备失败，结束扫描");
            return;
        }
        if (!mtpDevice.open(usbDeviceConnection)) {
            Log.e(TAG, "mtpDeviceScaner 数码相机打开失败 mtpDevice.open 打开Mtp失败 结束扫描");
            return;
        }

        int[] storageIds = mtpDevice.getStorageIds();
        if (storageIds == null || storageIds.length == 0) {
            Log.e(TAG, "mtpDeviceScaner: 数码相机存储卷不可用 storageIds == null 结束扫描");
            return;
        }
        Log.e(TAG, "mtpDeviceScaner: 设备一共几个盘符，storageIds.length =" + storageIds.length);
        List<SameDayPicutreInfo> cameraPictureInfoList = Collections.synchronizedList(new ArrayList<>());
        for (int storageId : storageIds) {
            int[] pictureHandlesItem = mtpDevice.getObjectHandles(storageId, 0, 0);
            if (pictureHandlesItem == null) {
                Log.e(TAG, "mtpDeviceScaner: 获取当前盘符全部照片数组 storageId =" + storageId + ",pictureHandlesItem = null");
            } else {
                Log.e(TAG, "mtpDeviceScaner: 获取当前盘符全部照片数组 storageId =" + storageId + ",pictureHandlesItem=" + pictureHandlesItem.length);
                for (int i : pictureHandlesItem) {
                    if (cameraDeviceID == -1) {
                        Log.d(TAG, "mtpDeviceScaner: cameraDeviceID == -1");
                        downloadFlieListener.scannerCameraComplete(0, 0, usbDevice.getProductName());
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
                    cameraTotalPicture++;

                    if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
                        if (VariableInstance.getInstance().isFormaringCamera.formatState == 1) {
                            boolean delectResult = mtpDevice.deleteObject(i);
                            if (!delectResult) {
                                Log.e(TAG, "mtpDeviceScaner: 格式化过程中，删除照片失败，name = " + mtpObjectInfo.getName());
                            }
                            continue;
                        } else if (VariableInstance.getInstance().isFormaringCamera.formatState == 2) {
                            int systemTime = Utils.getyyMMddtringInt(System.currentTimeMillis());
                            if (systemTime != 900101 && systemTime - yymmdd < VariableInstance.FormatCameraDay) {
                                boolean delectResult = mtpDevice.deleteObject(i);
                                if (!delectResult) {
                                    Log.e(TAG, "mtpDeviceScaner: 格式化过程中，删除照片失败，name = " + mtpObjectInfo.getName());
                                }
                            }
                        }
                        continue;
                    }

                    SameDayPicutreInfo sameDayPicutreInfo = new SameDayPicutreInfo(yymmdd);
                    int index = cameraPictureInfoList.indexOf(sameDayPicutreInfo);
                    if (index > -1) {
                        sameDayPicutreInfo = cameraPictureInfoList.get(index);
                    } else {
                        cameraPictureInfoList.add(sameDayPicutreInfo);
                    }

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

        VariableInstance.getInstance().isConnectCamera = true;
        Log.d(TAG, "mtpDeviceScaner:  相机总共照片 =" + cameraTotalPicture + ",deviceName = " + usbDevice.getProductName());
        if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
            downloadFlieListener.scannerCameraComplete(0, cameraTotalPicture, usbDevice.getProductName());
            usbDeviceConnection.close();
            mtpDevice.close();
            return;
        }
        Log.e(TAG, "mtpDeviceScaner: UploadMode =" + VariableInstance.getInstance().UploadMode + ",uploadSelectIndexList =" + VariableInstance.getInstance().uploadSelectIndexList);

        List<PictureInfo> backupPictureInfoList = Collections.synchronizedList(new ArrayList<>());
        List<PictureInfo> uploadPictureInfoList = Collections.synchronizedList(new ArrayList<>());
        //1 全部下载全部上传raw，2全部下载全部上传jpg，3全部下载列表上传raw，4列表下载列表上传RAW
        for (SameDayPicutreInfo cameraPictureInfo : cameraPictureInfoList) {
            if (cameraDeviceID == -1) {
                downloadFlieListener.scannerCameraComplete(0, 0, usbDevice.getProductName());
                return;
            }
            for (int i = 0; i < cameraPictureInfo.jpgPictureInfos.size(); i++) {
                Integer integer = i + 1;
                PictureInfo jpgPictureInfo = cameraPictureInfo.jpgPictureInfos.get(i);
                if (cameraDeviceID == -1) {
                    downloadFlieListener.scannerCameraComplete(0, 0, usbDevice.getProductName());
                    return;
                }
                if (VariableInstance.getInstance().UploadMode == 1) {
                    if (checkNeedBackUp(jpgPictureInfo.pictureName)) {
                        backupPictureInfoList.add(jpgPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 2) {
                    if (checkNeedBackUp(jpgPictureInfo.pictureName)) {
                        backupPictureInfoList.add(jpgPictureInfo);
                    }
                    if (checkNeedUpload(jpgPictureInfo.pictureName, cameraPictureInfo.yearMonthDay)) {
                        uploadPictureInfoList.add(jpgPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 3) {
                    if (checkNeedBackUp(jpgPictureInfo.pictureName)) {
                        backupPictureInfoList.add(jpgPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 4) {
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1 && checkNeedBackUp(jpgPictureInfo.pictureName)) {
                        backupPictureInfoList.add(jpgPictureInfo);
                    }
                }
            }

            for (int i = 0; i < cameraPictureInfo.rowPictureInfos.size(); i++) {
                Integer integer = i + 1;
                PictureInfo rowPictureInfo = cameraPictureInfo.rowPictureInfos.get(i);

                if (cameraDeviceID == -1) {
                    Log.e(TAG, "mtpDeviceScaner: cameraDeviceID == -1");
                    downloadFlieListener.scannerCameraComplete(0, 0, usbDevice.getProductName());
                    return;
                }
                if (VariableInstance.getInstance().UploadMode == 1) {
                    if (checkNeedBackUp(rowPictureInfo.pictureName)) {
                        backupPictureInfoList.add(rowPictureInfo);
                    }
                    if (checkNeedUpload(rowPictureInfo.pictureName, cameraPictureInfo.yearMonthDay)) {
                        uploadPictureInfoList.add(rowPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 2) {
                    if (checkNeedBackUp(rowPictureInfo.pictureName)) {
                        backupPictureInfoList.add(rowPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 3) {
                    if (checkNeedBackUp(rowPictureInfo.pictureName)) {
                        backupPictureInfoList.add(rowPictureInfo);
                    }
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if ((index > -1 || i == cameraPictureInfo.rowPictureInfos.size() - 1) && checkNeedUpload(rowPictureInfo.pictureName, cameraPictureInfo.yearMonthDay)) {
                        uploadPictureInfoList.add(rowPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 4) {
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1 && checkNeedBackUp(rowPictureInfo.pictureName)) {
                        backupPictureInfoList.add(rowPictureInfo);
                    }
                    if ((index > -1 || i == cameraPictureInfo.rowPictureInfos.size() - 1) && checkNeedUpload(rowPictureInfo.pictureName, cameraPictureInfo.yearMonthDay)) {
                        uploadPictureInfoList.add(rowPictureInfo);
                    }
                }
            }
        }

        Collections.sort(backupPictureInfoList, new order());
        Collections.sort(uploadPictureInfoList, new order());
        downloadFlieListener.scannerCameraComplete(backupPictureInfoList.size(), cameraTotalPicture, usbDevice.getProductName());
        Log.e(TAG, "mtpDeviceScaner: 需要备份张数：" + backupPictureInfoList.size() + ",需要上传张数：" + uploadPictureInfoList.size());
        for (PictureInfo pictureInfo : uploadPictureInfoList) {
            if (cameraDeviceID == -1) {
                return;
            }
            mtpBackUpToRemote(mtpDevice, pictureInfo);
        }

        for (PictureInfo pictureInfo : backupPictureInfoList) {
            if (cameraDeviceID == -1) {
                return;
            }
            mtpBackUpToUSB(mtpDevice, pictureInfo);
        }
        usbDeviceConnection.close();
        mtpDevice.close();
    }


    private boolean checkNeedUpload(String name, int yymmdd) {
        if (!VariableInstance.getInstance().remoteListInit || !VariableInstance.getInstance().remoteServerAvailable || LocalProfileHelp.getInstance().remotePictureList.contains(name)) {
            return false;
        }
        int systemTime = Utils.getyyMMddtringInt(System.currentTimeMillis());
        if (systemTime == 900101) {
            return false;
        }
        if (systemTime - yymmdd > 3) {
            return false;
        }

        return true;
    }

    private boolean checkNeedBackUp(String name) {
        if (!VariableInstance.getInstance().backupListInit && !LocalProfileHelp.getInstance().initLocalUSBPictureList()) {
            return false;
        }

        if (VariableInstance.getInstance().storeUSBDeviceID == -1 || LocalProfileHelp.getInstance().usbPictureList.contains(name)) {
            return false;
        }

        return true;
    }


    private void mtpBackUpToUSB(MtpDevice mtpDevice, PictureInfo pictureInfo) {
        if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
            Log.e(TAG, "mtpBackUpToUSB: 正在格式化相机，不做处理");
            return;
        }

        Log.d(TAG, "mtpBackUpToUSB: pictureInfo =" + pictureInfo);
        try {
            String pictureSaveLocalPath = VariableInstance.getInstance().TFCardPictureDir + File.separator + pictureInfo.pictureName;
            File pictureSaveFile = new File(pictureSaveLocalPath);
            if (pictureSaveFile != null && pictureSaveFile.exists()) {
                pictureSaveFile.delete();
            }

            if (cameraDeviceID == -1) {
                Log.e(TAG, "mtpBackUpToUSB: 相机已断开，停止下载");
                stopScanerThread(5);
                return;
            }
            boolean importResult = mtpDevice.importFile(pictureInfo.mtpPictureID, pictureSaveLocalPath);

            if (!importResult) {
                Log.e(TAG, "mtpBackUpToUSB: 111 导出相机照片失败");
            }

            if (pictureSaveFile != null && pictureSaveFile.exists()) {
                String yearMonth = Utils.getyyyyMMtring(pictureInfo.pictureCreateData);
                downloadFlieListener.uploadToUSB(pictureSaveFile, yearMonth);
                pictureSaveFile.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "mtpBackUpToUSB: 图片复制出错 e =" + e);

        }
        Log.e(TAG, "mtpBackUpToUSB: end");
    }

    private void mtpBackUpToRemote(MtpDevice mtpDevice, PictureInfo pictureInfo) {

        if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
            Log.e(TAG, "mtpBackUpToRemote: 正在格式化相机，不做处理");
            return;
        }
        Log.d(TAG, "mtpBackUpToRemote: pictureInfo =" + pictureInfo);
        try {
            if (cameraDeviceID == -1) {
                Log.e(TAG, "mtpBackUpToRemote: 相机已断开，停止下载");
                stopScanerThread(5);
                return;
            }
            Utils.checkSDAvailableSize();
            String pictureSaveUploadLocalPath = VariableInstance.getInstance().TFCardUploadPictureDir + File.separator + pictureInfo.pictureName;
            File pictureUploadSaveFile = new File(pictureSaveUploadLocalPath);
            if (pictureUploadSaveFile != null && pictureUploadSaveFile.exists()) {
                pictureUploadSaveFile.delete();
            }
            boolean importResult = mtpDevice.importFile(pictureInfo.mtpPictureID, pictureSaveUploadLocalPath);
            if (!importResult) {
                Log.e(TAG, "mtpBackUpToRemote:222 导出相机照片失败");
            }
            if (pictureUploadSaveFile != null && pictureUploadSaveFile.exists()) {
                downloadFlieListener.addUploadRemoteFile(new UploadFileModel(pictureSaveUploadLocalPath));
            }
        } catch (Exception e) {
            Log.e(TAG, "mtpBackUpToRemote: 图片复制出错 e =" + e);
        }
        Log.e(TAG, "mtpBackUpToRemote: end");
    }

    private void usbDeviceScaner(UsbDevice usbDevice) {
        Log.d(TAG, "usbDeviceScaner start");
        if (usbDevice == null) {
            Log.e(TAG, "usbDeviceScaner: USB设备为空 结束扫描");
            return;
        }

        cameraDeviceID = usbDevice.getDeviceId();
        cameraTotalPicture = 0;

        if (usbManager == null) {
            usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
        }

        if (usbManager == null) {
            Log.d(TAG, "usbDeviceScaner:usbManager==null ");
            return;
        }

        UsbMassStorageDevice device = getUsbMass(usbDevice);
        if (device == null) {
            Log.e(TAG, "usbDeviceScaner: device == null 结束扫描");
            return;
        }

        try {
            device.init();
        } catch (Exception e) {
            Log.e(TAG, "usbDeviceScaner: 结束扫描 device.init() error:" + e);
            return;
        }

        if (device.getPartitions().size() <= 0) {
            Log.e(TAG, "usbDeviceScaner: " + "device.getPartitions().size() error 结束扫描");
            return;
        }
        Partition partition = device.getPartitions().get(0);
        FileSystem currentFs = partition.getFileSystem();
        UsbFile mRootFolder = currentFs.getRootDirectory();

        List<SameDayPicutreInfo> cameraPictureInfoList = Collections.synchronizedList(new ArrayList<>());

        readPicFileFromUSBFile(usbDevice, currentFs, mRootFolder, cameraPictureInfoList);

        VariableInstance.getInstance().isConnectCamera = true;
        Log.d(TAG, "usbDeviceScaner:  相机总共照片 cameraTotalPicture =" + cameraTotalPicture + ",deviceName = " + usbDevice.getProductName());


        if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
            downloadFlieListener.scannerCameraComplete(0, cameraTotalPicture, usbDevice.getProductName());
            return;
        }


        List<PictureInfo> backupPictureInfoList = Collections.synchronizedList(new ArrayList<>());
        List<PictureInfo> uploadPictureInfoList = Collections.synchronizedList(new ArrayList<>());

        //1 全部下载全部上传raw，2全部下载全部上传jpg，3全部下载列表上传raw，4列表下载列表上传RAW
        for (SameDayPicutreInfo cameraPictureInfo : cameraPictureInfoList) {
            if (cameraDeviceID == -1) {
                downloadFlieListener.scannerCameraComplete(0, 0, usbDevice.getProductName());
                return;
            }
            for (int i = 0; i < cameraPictureInfo.jpgPictureInfos.size(); i++) {
                Integer integer = i + 1;
                PictureInfo jpgPictureInfo = cameraPictureInfo.jpgPictureInfos.get(i);
                if (cameraDeviceID == -1) {
                    downloadFlieListener.scannerCameraComplete(0, 0, usbDevice.getProductName());
                    return;
                }
                if (VariableInstance.getInstance().UploadMode == 1) {
                    if (checkNeedBackUp(jpgPictureInfo.pictureName)) {
                        backupPictureInfoList.add(jpgPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 2) {
                    if (checkNeedBackUp(jpgPictureInfo.pictureName)) {
                        backupPictureInfoList.add(jpgPictureInfo);
                    }
                    if (checkNeedUpload(jpgPictureInfo.pictureName, cameraPictureInfo.yearMonthDay)) {
                        uploadPictureInfoList.add(jpgPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 3) {
                    if (checkNeedBackUp(jpgPictureInfo.pictureName)) {
                        backupPictureInfoList.add(jpgPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 4) {
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1 && checkNeedBackUp(jpgPictureInfo.pictureName)) {
                        backupPictureInfoList.add(jpgPictureInfo);
                    }
                }
            }


            for (int i = 0; i < cameraPictureInfo.rowPictureInfos.size(); i++) {
                Integer integer = i + 1;
                PictureInfo rowPictureInfo = cameraPictureInfo.rowPictureInfos.get(i);

                if (cameraDeviceID == -1) {
                    downloadFlieListener.scannerCameraComplete(0, 0, usbDevice.getProductName());
                    return;
                }
                if (VariableInstance.getInstance().UploadMode == 1) {
                    if (checkNeedBackUp(rowPictureInfo.pictureName)) {
                        backupPictureInfoList.add(rowPictureInfo);
                    }
                    if (checkNeedUpload(rowPictureInfo.pictureName, cameraPictureInfo.yearMonthDay)) {
                        uploadPictureInfoList.add(rowPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 2) {
                    if (checkNeedBackUp(rowPictureInfo.pictureName)) {
                        backupPictureInfoList.add(rowPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 3) {
                    if (checkNeedBackUp(rowPictureInfo.pictureName)) {
                        backupPictureInfoList.add(rowPictureInfo);
                    }
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if ((index > -1 || i == cameraPictureInfo.rowPictureInfos.size() - 1) && checkNeedUpload(rowPictureInfo.pictureName, cameraPictureInfo.yearMonthDay)) {
                        uploadPictureInfoList.add(rowPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 4) {
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1 && checkNeedBackUp(rowPictureInfo.pictureName)) {
                        backupPictureInfoList.add(rowPictureInfo);
                    }
                    if ((index > -1 || i == cameraPictureInfo.rowPictureInfos.size() - 1) && checkNeedUpload(rowPictureInfo.pictureName, cameraPictureInfo.yearMonthDay)) {
                        uploadPictureInfoList.add(rowPictureInfo);
                    }
                }
            }
        }

        Collections.sort(backupPictureInfoList, new order());
        Collections.sort(uploadPictureInfoList, new order());
        downloadFlieListener.scannerCameraComplete(backupPictureInfoList.size(), cameraTotalPicture, usbDevice.getProductName());
        Log.e(TAG, "usbDeviceScaner: 需要备份张数：" + backupPictureInfoList.size() + ",需要上传张数：" + uploadPictureInfoList.size());

        for (PictureInfo pictureInfo : uploadPictureInfoList) {
            if (cameraDeviceID == -1) {
                return;
            }
            usbBackUpToRemote(pictureInfo);
        }

        for (PictureInfo pictureInfo : backupPictureInfoList) {
            if (cameraDeviceID == -1) {
                return;
            }
            usbBackUpToUSB(pictureInfo);
        }
    }

    private void readPicFileFromUSBFile(UsbDevice usbDevice, FileSystem fileSystem, UsbFile usbFile, List<SameDayPicutreInfo> cameraPictureInfoList) {
        UsbFile[] usbFileList = new UsbFile[0];
        try {
            usbFileList = usbFile.listFiles();
        } catch (IOException e) {
            Log.e(TAG, "readPicFileFromUSBFile:usbFile.listFiles IOException e =" + e);
        }
        for (UsbFile usbFileItem : usbFileList) {
            if (usbFileItem.isDirectory()) {
                if (cameraDeviceID == -1) {
                    downloadFlieListener.scannerCameraComplete(0, 0, usbDevice.getProductName());
                    return;
                }
                readPicFileFromUSBFile(usbDevice, fileSystem, usbFileItem, cameraPictureInfoList);
            } else {
                if (cameraDeviceID == -1) {
                    downloadFlieListener.scannerCameraComplete(0, 0, usbDevice.getProductName());
                    return;
                }

                long createDate = usbFileItem.createdAt() - 1000L * 60 * 60 * 8;
                int yymmdd = Utils.getyyMMddtringInt(createDate);
                String name = usbFileItem.getName();
                String pictureName = yymmdd + "-" + name;
                String FileEnd = pictureName.substring(name.lastIndexOf(".") + 1).toLowerCase();

                if (!pictureFormatFile(FileEnd)) {
                    continue;
                }
                cameraTotalPicture++;


                if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
                    if (VariableInstance.getInstance().isFormaringCamera.formatState == 1) {
                        try {
                            usbFile.delete();
                        } catch (IOException e) {
                            Log.e(TAG, "readPicFileFromUSBFile: usbFile.delete IOException =" + e);
                        }
                    } else if (VariableInstance.getInstance().isFormaringCamera.formatState == 2) {
                        int systemTime = Utils.getyyMMddtringInt(System.currentTimeMillis());
                        if (systemTime == 900101 && systemTime - yymmdd < VariableInstance.FormatCameraDay) {
                            continue;
                        }
                        try {
                            usbFile.delete();
                        } catch (Throwable e) {
                            Log.e(TAG, "readPicFileFromUSBFile: usbFile.delete IOException =" + e);
                        }
                    }
                    continue;
                }

                SameDayPicutreInfo sameDayPicutreInfo = new SameDayPicutreInfo(yymmdd);
                int index = cameraPictureInfoList.indexOf(sameDayPicutreInfo);

                if (index > -1) {
                    sameDayPicutreInfo = cameraPictureInfoList.get(index);
                } else {
                    cameraPictureInfoList.add(sameDayPicutreInfo);
                }
                if (rowFormatFile(FileEnd)) {
                    PictureInfo pictureInfo = new PictureInfo(false, pictureName, createDate, 0, fileSystem, usbFileItem, false);
                    sameDayPicutreInfo.rowPictureInfos.add(pictureInfo);
                } else if (jPGFormatFile(FileEnd)) {
                    PictureInfo pictureInfo = new PictureInfo(false, pictureName, createDate, 0, fileSystem, usbFileItem, true);
                    sameDayPicutreInfo.jpgPictureInfos.add(pictureInfo);
                }
            }
        }
    }

    private void usbBackUpToUSB(PictureInfo pictureInfo) {
        Log.d(TAG, "usbBackUpToUSB: start pictureInfo =" + pictureInfo);

        if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
            Log.e(TAG, "usbBackUpToUSB: 正在格式化相机，不做处理");
            return;
        }
        if (cameraDeviceID == -1) {
            Log.e(TAG, "usbBackUpToUSB: 相机已断开，停止下载");
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
            Log.e(TAG, "usbBackUpToUSB: Exception =" + e);
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
                Log.e(TAG, "usbBackUpToUSB : 11111 Exception =" + e);
            }
        }

        if (pictureSaveLocalFile != null && pictureSaveLocalFile.exists() && pictureSaveLocalFile.length() > 0) {
            String yearMonth = Utils.getyyyyMMtring(pictureInfo.pictureCreateData);
            downloadFlieListener.uploadToUSB(pictureSaveLocalFile, yearMonth);
            pictureSaveLocalFile.delete();
        }
        Log.d(TAG, "usbBackUpToUSB: end ........................");
    }

    private void usbBackUpToRemote(PictureInfo pictureInfo) {
        Log.d(TAG, "usbBackUpToRemote: start pictureInfo =" + pictureInfo);

        if (VariableInstance.getInstance().isFormaringCamera.formatState != 0) {
            Log.e(TAG, "usbBackUpToRemote: 正在格式化相机，不做处理");
            return;
        }
        if (cameraDeviceID == -1) {
            Log.e(TAG, "usbBackUpToRemote: 相机已断开，停止下载");
            stopScanerThread(6);
            return;
        }

        FileOutputStream uploadout = null;
        InputStream uploadin = null;
        try {

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

            if (pictureUploadSaveFile != null && pictureUploadSaveFile.exists() && pictureUploadSaveFile.length() > 0) {
                downloadFlieListener.addUploadRemoteFile(new UploadFileModel(pictureSaveUploadLocalPath));
            }
        } catch (Exception e) {
            Log.e(TAG, "usbBackUpToRemote : 222222222222 Exception =" + e);
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
                Log.e(TAG, "usbBackUpToRemote : 11111 Exception =" + e);
            }
        }

        Log.d(TAG, "usbBackUpToRemote: end ...........................");

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


        void scannerCameraComplete(int needDownloadConut, int pictureCont, String deviceName);


    }
}

