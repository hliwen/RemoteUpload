package com.example.nextclouddemo.operation;

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
import android.os.Handler;
import android.os.Message;

import com.example.nextclouddemo.MyApplication;
import com.example.nextclouddemo.VariableInstance;
import com.example.nextclouddemo.utils.DeviceUtils;
import com.example.nextclouddemo.utils.Log;
import com.example.nextclouddemo.utils.PictureDateInfo;
import com.example.nextclouddemo.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
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

public class ScannerCamera extends BroadcastReceiver {
    private static final String TAG = "remotelog_ReceiverCamera";
    private UsbManager usbManager;
    private DeviceScannerListener deviceScannerListener;
    private ExecutorService scannerThreadExecutor;

    public String errorMessage = "";
    private UsbDevice operatingDevice;
    public boolean isOperatingDevice;

    public int deviceTotalPicture;
    public int backRemoteTotalPicture;
    public int backupUSBTotalPicture;

    private boolean isMtpModel = false;
    public boolean isConnectCamera;

    private MyHandler mHandler;

    public ScannerCamera(Context context, DeviceScannerListener downloadFlieListener) {
        mHandler = new MyHandler(ScannerCamera.this);
        this.deviceScannerListener = downloadFlieListener;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        Utils.makeDir(VariableInstance.getInstance().TFCardPictureDir);
        Utils.makeDir(VariableInstance.getInstance().TFCardUploadPictureDir);

        if (usbManager == null) {
            errorMessage = "系统异常 usbManager==null";
        }
    }


    class InfloList {
        List<PictureInfo> backupUSBPictureInfoList;
        List<PictureInfo> backRemotePictureInfoList;
    }

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
            if (this == o)
                return true;
            if (!(o instanceof SameDayPicutreInfo))
                return false;
            SameDayPicutreInfo that = (SameDayPicutreInfo) o;
            return yearMonthDay == that.yearMonthDay && Objects.equals(yearMonthDay, that.yearMonthDay);
        }
    }

    class PictureInfo {
        public boolean mtpModel;
        public String pictureName;

        public int mtpPictureID;
        public UsbFile cameraUsbFile;
        public FileSystem cameraUsbFileSystem;
        public boolean isJpg;

        public PictureInfo(boolean mtpModel, String pictureName, int mtpPictureID, FileSystem usbFileSystem, UsbFile usbFile, boolean isJpg) {
            this.mtpModel = mtpModel;
            this.pictureName = pictureName;
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
            if (this == o)
                return true;
            if (!(o instanceof PictureInfo))
                return false;
            PictureInfo that = (PictureInfo) o;
            return isJpg == that.isJpg && Objects.equals(pictureName, that.pictureName);
        }
    }

    public class DescendingOrder implements Comparator<PictureInfo> {//降序

        @Override
        public int compare(PictureInfo o1, PictureInfo o2) {
            return o2.toString().compareTo(o1.toString());
        }
    }

    public class AscendingOrderSameData implements Comparator<SameDayPicutreInfo> {//升序

        @Override
        public int compare(SameDayPicutreInfo o1, SameDayPicutreInfo o2) {
            if (o2.yearMonthDay > o1.yearMonthDay) {
                return -1;
            } else if (o2.yearMonthDay == o1.yearMonthDay) {
                return 0;
            } else {
                return 1;
            }
        }
    }


    private static class MyHandler extends Handler {
        private final WeakReference<ScannerCamera> weakReference;

        MyHandler(ScannerCamera object) {
            weakReference = new WeakReference<>(object);
        }

        @Override
        public void handleMessage(Message msg) {
            ScannerCamera device = weakReference.get();
            if (device == null || device.mHandler == null) {
                return;
            }
            switch (msg.what) {
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED: {
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice == null) {
                    Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ",usbDevice == null");
                    return;
                }
                Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                if (DeviceUtils.isCameraDevice(usbDevice.getProductName())) {
                    if (deviceScannerListener.networkIniting()) {
                        Log.e(TAG, "设备接入: 正在初始化网络，不需要执行扫描相机");
                    } else {
                        startScannerDevice();
                    }
                }
            }
            break;
            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (usbDevice == null) {
                    Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ",usbDevice == null");
                    return;
                }
                Log.e(TAG, "ReceiverCamera onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                if (operatingDevice != null && usbDevice.equals(operatingDevice)) {
                    detachedDevice();
                }
                break;
            default:
                break;
        }
    }


    public void detachedDevice() {
        Log.e(TAG, "相机detachedDevice: isOperatingDevice =" + isOperatingDevice + ",operatingDevice =" + operatingDevice);

        if (isOperatingDevice) {
            errorMessage = "detachedDevice 设备断开";
            isOperatingDevice = false;
            deviceScannerListener.endScannerDevice(false);
        }


        if (operatingDevice != null) {
            try {
                UsbMassStorageDevice usbMassStorageDevice = getUsbMass(operatingDevice);
                if (usbMassStorageDevice != null) {
                    usbMassStorageDevice.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "usbDissConnect 设备断开异常 e =" + e);
            }
        }

        operatingDevice = null;
        isOperatingDevice = false;

        try {
            if (scannerThreadExecutor != null) {
                scannerThreadExecutor.shutdownNow();
            }
        } catch (Exception e) {

        }
        scannerThreadExecutor = null;
    }


    public void startScannerDevice() {
        detachedDevice();
        backRemoteTotalPicture = 0;
        backupUSBTotalPicture = 0;
        deviceTotalPicture = 0;
        isOperatingDevice = true;
        deviceScannerListener.startScannerDevice();
        errorMessage = "";

        if (usbManager == null) {
            Log.e(TAG, "startScannerDevice: 系统异常 usbManager==null");
            errorMessage = "系统异常 usbManager==null";
            isOperatingDevice = false;
            deviceScannerListener.endScannerDevice(false);
            return;
        }
        HashMap<String, UsbDevice> deviceMap = usbManager.getDeviceList();
        Collection<UsbDevice> usbDeviceList = null;
        if (deviceMap != null) {
            usbDeviceList = deviceMap.values();
        }
        if (deviceMap == null || deviceMap.size() <= 0 || usbDeviceList == null) {
            errorMessage = "没有检测到有设备列表";
            isOperatingDevice = false;
            deviceScannerListener.endScannerDevice(false);
            return;
        }

        isMtpModel = false;


        for (UsbDevice usbDevice : usbDeviceList) {
            if (usbDevice == null) {
                continue;
            }
            String usbProductName = usbDevice.getProductName();
            Log.e(TAG, "startScannerDevice: 当前设备名称：" + usbProductName);
            if (usbProductName == null || !DeviceUtils.isCameraDevice(usbProductName)) {
                continue;
            }

            int interfaceCount = usbDevice.getInterfaceCount();
            List<UsbInterface> usbInterfaceList = new ArrayList<>();
            for (int i = 0; i < interfaceCount; i++) {
                UsbInterface usbInterface = usbDevice.getInterface(i);
                if (usbInterface != null) {
                    usbInterfaceList.add(usbDevice.getInterface(i));
                }
            }
            for (UsbInterface usbInterface : usbInterfaceList) {
                if (UsbConstants.USB_CLASS_STILL_IMAGE == usbInterface.getInterfaceClass()) {
                    isMtpModel = true;
                    operatingDevice = usbDevice;
                    break;
                } else if (UsbConstants.USB_CLASS_MASS_STORAGE == usbInterface.getInterfaceClass()) {
                    isMtpModel = false;
                    operatingDevice = usbDevice;
                    break;
                }
            }
            if (operatingDevice != null) {
                break;
            }
        }

        if (operatingDevice == null) {
            Log.e(TAG, "startScannerDevice: 没有找到相机");
            errorMessage = "没有找到相机";
            isOperatingDevice = false;
            deviceScannerListener.endScannerDevice(false);
            return;
        }
        Log.e(TAG, "startScannerDevice: 找到相机 :" + operatingDevice.getProductName() + ",isMtpModel=" + isMtpModel);

        scannerThreadExecutor = Executors.newSingleThreadExecutor();
        scannerThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    int requestPermissionCount = 0;
                    while (isOperatingDevice && !usbManager.hasPermission(operatingDevice) && requestPermissionCount < 10) {
                        requestPermissionCount++;
                        try {
                            Thread.sleep(2000);
                            @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(MyApplication.getContext(), 0, new Intent(VariableInstance.GET_DEVICE_PERMISSION), 0);
                            usbManager.requestPermission(operatingDevice, pendingIntent);
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                        }
                    }

                    if (!isOperatingDevice) {
                        Log.e(TAG, "startScannerDevice: 操作相机异常结束");
                        errorMessage = "操作相机异常结束";
                        isOperatingDevice = false;
                        deviceScannerListener.endScannerDevice(false);
                        return;
                    }

                    if (usbManager.hasPermission(operatingDevice)) {
                        waitingInitBackList();
                        boolean scannerResult;
                        if (isMtpModel) {
                            scannerResult = mtpDeviceScanner(operatingDevice);
                        } else {
                            scannerResult = usbDeviceScanner(operatingDevice);
                        }
                        isOperatingDevice = false;
                        if (scannerResult) {
                            isConnectCamera = true;
                        }
                        deviceScannerListener.endScannerDevice(scannerResult);
                    } else {
                        Log.e(TAG, "startScannerDevice: 授权失败");
                        errorMessage = "多次授权失败";
                        isOperatingDevice = false;
                        deviceScannerListener.endScannerDevice(false);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "startScannerDevice Exception: " + e);
                    errorMessage = "扫描异常结束：" + e;
                    isOperatingDevice = false;
                    deviceScannerListener.endScannerDevice(false);
                }

            }
        });
    }


    private void waitingInitBackList() {//TODO

        int waitingRemoteListCount = 0;
        int waitingBackupListCount = 0;


    }

    private boolean mtpDeviceScanner(UsbDevice usbDevice) {
        Log.d(TAG, "mtpDeviceScanner mtp模式相机开始扫描................................");
        if (usbDevice == null || usbManager == null) {
            if (usbDevice == null) {
                Log.e(TAG, "mtpDeviceScanner usbDevice == null ");
                errorMessage = "mtpDeviceScanner usbDevice == null";
            } else if (usbManager == null) {
                Log.e(TAG, "mtpDeviceScanner usbManager == null ");
                errorMessage = "mtpDeviceScanner usbManager == null";
            }
            return false;
        }

        UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(usbDevice);
        if (usbDeviceConnection == null) {
            Log.e(TAG, "mtpDeviceScanner usbDeviceConnection == null ");
            errorMessage = "打开相机失败";
            return false;
        }

        MtpDevice mtpDevice = new MtpDevice(usbDevice);

        if (mtpDevice == null) {
            Log.e(TAG, "mtpDeviceScanner mtpDevice == null ");
            errorMessage = "mtp设备构建失败";
            return false;
        }
        if (!mtpDevice.open(usbDeviceConnection)) {
            Log.e(TAG, "mtpDevice.open(usbDeviceConnection) == false ");
            errorMessage = "数码相机打开失败";
            return false;
        }


        int[] storageIds = mtpDevice.getStorageIds();
        if (storageIds == null || storageIds.length == 0) {
            Log.e(TAG, "mtpDeviceScanner: 数码相机存储卷不可用 storageIds == null 结束扫描");
            errorMessage = "数码相机存储卷不可用";
            return false;
        }

        Log.e(TAG, "mtpDeviceScanner: 设备一共几个盘符，storageIds.length =" + storageIds.length);
        List<SameDayPicutreInfo> sameDayPicutreInfoList = Collections.synchronizedList(new ArrayList<>());

        for (int storageId : storageIds) {
            if (!isOperatingDevice) {
                errorMessage = "操作相机异常结束 1";
                return false;
            }
            int[] objectHandles = mtpDevice.getObjectHandles(storageId, 0, 0);
            if (objectHandles == null) {
                Log.e(TAG, "mtpDeviceScanner: 获取当前句柄失败： storageId =" + storageId);
            } else {
                for (int handle : objectHandles) {
                    if (!isOperatingDevice) {
                        errorMessage = "操作相机异常结束 2";
                        return false;
                    }
                    MtpObjectInfo mtpObjectInfo = mtpDevice.getObjectInfo(handle);
                    if (mtpObjectInfo == null) {
                        Log.e(TAG, "mtpDeviceScanner:  mtpObjectInfo ==null 当前文件信息无法获取");
                        continue;
                    }
                    String name = mtpObjectInfo.getName();
                    if (!DeviceUtils.fileIsPicture(name)) {
                        continue;
                    }

                    long createDate = mtpObjectInfo.getDateCreated() - 1000L * 60 * 60 * 8;
                    int yymmdd = Utils.getyyMMddtringInt(createDate);
                    String pictureName = createDate + "-" + name;

                    SameDayPicutreInfo sameDayPicutreInfo = new SameDayPicutreInfo(yymmdd);

                    int index = sameDayPicutreInfoList.indexOf(sameDayPicutreInfo);
                    if (index == -1) {
                        sameDayPicutreInfoList.add(sameDayPicutreInfo);
                    } else {
                        sameDayPicutreInfo = sameDayPicutreInfoList.get(index);
                    }

                    if (DeviceUtils.fileIsRow(name)) {
                        PictureInfo pictureInfo = new PictureInfo(true, pictureName, handle, null, null, false);
                        if (!sameDayPicutreInfo.rowPictureInfos.contains(pictureInfo)) {
                            sameDayPicutreInfo.rowPictureInfos.add(pictureInfo);
                            deviceTotalPicture++;
                        }
                    } else if (DeviceUtils.fileIsJPG(name)) {
                        PictureInfo pictureInfo = new PictureInfo(true, pictureName, handle, null, null, true);
                        if (!sameDayPicutreInfo.jpgPictureInfos.contains(pictureInfo)) {
                            sameDayPicutreInfo.jpgPictureInfos.add(pictureInfo);
                            deviceTotalPicture++;
                        }
                    }
                }
            }
        }
        Log.d(TAG, "mtpDeviceScanner: 相机扫描完成， 相机总共照片 =" + deviceTotalPicture);
        InfloList infloList = getPictureSortList(sameDayPicutreInfoList);

        if (infloList == null) {
            errorMessage = "未找到需要上传和下载的新照片 infloList == null";
            return false;
        }

        backRemoteTotalPicture = infloList.backRemotePictureInfoList.size();
        backupUSBTotalPicture = infloList.backupUSBPictureInfoList.size();

        deviceScannerListener.scannerCameraComplete(backupUSBTotalPicture, backRemoteTotalPicture, deviceTotalPicture, usbDevice.getProductName());


        for (PictureInfo pictureInfo : infloList.backRemotePictureInfoList) {
            if (!isOperatingDevice) {
                errorMessage = "操作相机异常结束 3";
                return false;
            }
            mtpBackUpToRemote(mtpDevice, pictureInfo);
        }

        for (PictureInfo pictureInfo : infloList.backupUSBPictureInfoList) {
            if (!isOperatingDevice) {
                errorMessage = "操作相机异常结束 4";
                return false;
            }

            mtpBackUpToUSB(mtpDevice, pictureInfo);
        }
        usbDeviceConnection.close();
        mtpDevice.close();
        return true;
    }

    private void mtpBackUpToUSB(MtpDevice mtpDevice, PictureInfo pictureInfo) {
        Log.d(TAG, "mtpBackUpToUSB: pictureInfo =" + pictureInfo);
        try {
            String filePath = VariableInstance.getInstance().TFCardPictureDir + File.separator + pictureInfo.pictureName;
            File file = new File(filePath);
            if (file != null && file.exists()) {
                file.delete();
            }
            boolean importResult = mtpDevice.importFile(pictureInfo.mtpPictureID, filePath);
            if (!importResult) {
                Log.e(TAG, "mtpBackUpToUSB: 111 导出相机照片失败");
            }

            if (file != null && file.exists()) {
                deviceScannerListener.uploadToUSB(filePath);
                file.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "mtpBackUpToUSB: 图片复制出错 e =" + e);
        }
    }

    private void mtpBackUpToRemote(MtpDevice mtpDevice, PictureInfo pictureInfo) {
        Log.d(TAG, "mtpBackUpToRemote: pictureInfo =" + pictureInfo);
        try {
            Utils.checkSDAvailableSize();
            String filePath = VariableInstance.getInstance().TFCardUploadPictureDir + File.separator + pictureInfo.pictureName;
            File file = new File(filePath);
            if (file != null && file.exists()) {
                file.delete();
            }
            boolean importResult = mtpDevice.importFile(pictureInfo.mtpPictureID, filePath);
            if (!importResult) {
                Log.e(TAG, "mtpBackUpToRemote:222 导出相机照片失败");
            }
            if (file != null && file.exists()) {
                deviceScannerListener.addUploadRemoteFile(filePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "mtpBackUpToRemote: 图片复制出错 e =" + e);
        }
    }

    private boolean usbDeviceScanner(UsbDevice usbDevice) {
        Log.d(TAG, "usbDeviceScanner start");
        if (usbDevice == null || usbManager == null) {
            if (usbDevice == null) {
                Log.e(TAG, "usbDeviceScanner usbDevice == null ");
                errorMessage = "usbDeviceScanner usbDevice == null";
            } else if (usbManager == null) {
                Log.e(TAG, "usbDeviceScanner usbManager == null ");
                errorMessage = "usbDeviceScanner usbManager == null";
            }
            return false;
        }

        UsbMassStorageDevice device = getUsbMass(usbDevice);
        if (device == null) {
            Log.e(TAG, "usbDeviceScanner: USBDevice 转换成UsbMassStorageDevice 对象 失败");
            errorMessage = "USBDevice 转换成UsbMassStorageDevice 对象 失败";
            return false;
        }

        try {
            device.init();
        } catch (Exception e) {
            Log.e(TAG, "usbDeviceScanner: 结束扫描 device.init() error:" + e);
            errorMessage = " device.init() error:" + e;
            return false;
        }

        if (device.getPartitions().size() <= 0) {
            Log.e(TAG, "usbDeviceScanner: " + "device.getPartitions().size() error 结束扫描");
            errorMessage = "device.getPartitions().size() error ";
            return false;
        }
        Partition partition = device.getPartitions().get(0);
        FileSystem currentFs = partition.getFileSystem();
        UsbFile mRootFolder = currentFs.getRootDirectory();

        List<SameDayPicutreInfo> sameDayPicutreInfoList = Collections.synchronizedList(new ArrayList<>());

        readPicFileFromUSBFile(currentFs, mRootFolder, sameDayPicutreInfoList);
        Log.d(TAG, "usbDeviceScanner: 相机扫描完成， 相机总共照片 =" + deviceTotalPicture);

        InfloList infloList = getPictureSortList(sameDayPicutreInfoList);
        if (infloList == null) {
            errorMessage = "未找到需要上传和下载的新照片 infloList == null";
            return false;
        }
        backRemoteTotalPicture = infloList.backRemotePictureInfoList.size();
        backupUSBTotalPicture = infloList.backupUSBPictureInfoList.size();
        deviceScannerListener.scannerCameraComplete(backupUSBTotalPicture, backRemoteTotalPicture, deviceTotalPicture, usbDevice.getProductName());

        for (PictureInfo pictureInfo : infloList.backRemotePictureInfoList) {
            if (!isOperatingDevice) {
                errorMessage = "操作相机异常结束 3";
                return false;
            }
            usbBackUpToRemote(pictureInfo);
        }

        for (PictureInfo pictureInfo : infloList.backupUSBPictureInfoList) {
            if (!isOperatingDevice) {
                errorMessage = "操作相机异常结束 4";
                return false;
            }
            usbBackUpToUSB(pictureInfo);
        }
        return true;
    }

    private void readPicFileFromUSBFile(FileSystem fileSystem, UsbFile usbFile, List<SameDayPicutreInfo> cameraPictureInfoList) {
        UsbFile[] usbFileList = new UsbFile[0];
        try {
            usbFileList = usbFile.listFiles();
        } catch (IOException e) {
            Log.e(TAG, "readPicFileFromUSBFile:usbFile.listFiles IOException e =" + e);
        }

        if (usbFileList == null || usbFileList.length == 0) {
            return;
        }


        for (UsbFile usbFileItem : usbFileList) {
            if (usbFileItem.isDirectory()) {
                if (!isOperatingDevice) {
                    errorMessage = "操作相机异常结束 5";
                    return;
                }
                readPicFileFromUSBFile(fileSystem, usbFileItem, cameraPictureInfoList);
            } else {
                if (!isOperatingDevice) {
                    errorMessage = "操作相机异常结束 5";
                    return;
                }

                String name = usbFileItem.getName();
                if (!DeviceUtils.fileIsPicture(name)) {
                    continue;
                }

                long createDate = usbFileItem.createdAt() - 1000L * 60 * 60 * 8;
                int yymmdd = Utils.getyyMMddtringInt(createDate);
                String pictureName = createDate + "-" + name;


                SameDayPicutreInfo sameDayPicutreInfo = new SameDayPicutreInfo(yymmdd);
                int index = cameraPictureInfoList.indexOf(sameDayPicutreInfo);

                if (index == -1) {
                    cameraPictureInfoList.add(sameDayPicutreInfo);
                } else {
                    sameDayPicutreInfo = cameraPictureInfoList.get(index);
                }
                if (DeviceUtils.fileIsRow(name)) {
                    PictureInfo pictureInfo = new PictureInfo(false, pictureName, 0, fileSystem, usbFileItem, false);
                    if (!sameDayPicutreInfo.rowPictureInfos.contains(pictureInfo)) {
                        sameDayPicutreInfo.rowPictureInfos.add(pictureInfo);
                        deviceTotalPicture++;
                    }
                } else if (DeviceUtils.fileIsJPG(name)) {
                    PictureInfo pictureInfo = new PictureInfo(false, pictureName, 0, fileSystem, usbFileItem, true);
                    if (!sameDayPicutreInfo.jpgPictureInfos.contains(pictureInfo)) {
                        sameDayPicutreInfo.jpgPictureInfos.add(pictureInfo);
                        deviceTotalPicture++;
                    }
                }
            }
        }
    }

    private void usbBackUpToUSB(PictureInfo pictureInfo) {
        Log.d(TAG, "usbBackUpToUSB: start pictureInfo =" + pictureInfo);

        if (!isOperatingDevice) {
            Log.e(TAG, "usbBackUpToUSB: 相机已断开，停止下载");
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
            deviceScannerListener.uploadToUSB(pictureSavePath);
            pictureSaveLocalFile.delete();
        }

    }

    private void usbBackUpToRemote(PictureInfo pictureInfo) {
        Log.d(TAG, "usbBackUpToRemote: start pictureInfo =" + pictureInfo);

        if (!isOperatingDevice) {
            Log.e(TAG, "usbBackUpToRemote: 相机已断开，停止下载");
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
                deviceScannerListener.addUploadRemoteFile(pictureSaveUploadLocalPath);
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
    }


    private InfloList getPictureSortList(List<SameDayPicutreInfo> sameDayPicutreInfoList) {

        if (!isOperatingDevice) {
            return null;
        }
        Collections.sort(sameDayPicutreInfoList, new AscendingOrderSameData());

        for (SameDayPicutreInfo sameDayPicutreInfo : sameDayPicutreInfoList) {
            Log.e(TAG, "getPictureSortList: yearMonthDay = " + sameDayPicutreInfo.yearMonthDay + ", rowPictureInfos = " + (sameDayPicutreInfo.rowPictureInfos == null ? "0" : sameDayPicutreInfo.rowPictureInfos.size()) + ", jpgPictureInfos = " + (sameDayPicutreInfo.jpgPictureInfos == null ? "0" : sameDayPicutreInfo.jpgPictureInfos.size()));
        }


        InfloList infloList = new InfloList();
        infloList.backupUSBPictureInfoList = Collections.synchronizedList(new ArrayList<>());
        infloList.backRemotePictureInfoList = Collections.synchronizedList(new ArrayList<>());

        //  1 全部下载全部上传raw，2全部下载全部上传jpg，3全部下载列表上传raw,jpg，4列表下载列表上传RAW
        for (SameDayPicutreInfo cameraPictureInfo : sameDayPicutreInfoList) {
            if (!isOperatingDevice) {
                return null;
            }
            for (int i = 0; i < cameraPictureInfo.jpgPictureInfos.size(); i++) {
                Integer integer = i + 1;
                PictureInfo jpgPictureInfo = cameraPictureInfo.jpgPictureInfos.get(i);
                if (!isOperatingDevice) {
                    return null;
                }

                PictureDateInfo pictureDataInfo = new PictureDateInfo(jpgPictureInfo.pictureName);

                if (VariableInstance.getInstance().UploadMode == 1) {
                    if (checkNeedBackupToUSB(pictureDataInfo.showName)) {
                        infloList.backupUSBPictureInfoList.add(jpgPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 2) {
                    if (checkNeedBackupToUSB(pictureDataInfo.showName)) {
                        infloList.backupUSBPictureInfoList.add(jpgPictureInfo);
                    }
                    if (checkNeedBackupToRemote(pictureDataInfo.showName, cameraPictureInfo.yearMonthDay)) {
                        infloList.backRemotePictureInfoList.add(jpgPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 3) {
                    if (checkNeedBackupToUSB(pictureDataInfo.showName)) {
                        infloList.backupUSBPictureInfoList.add(jpgPictureInfo);
                    }

                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if ((index > -1 || i == cameraPictureInfo.jpgPictureInfos.size() - 1) && checkNeedBackupToRemote(pictureDataInfo.showName, cameraPictureInfo.yearMonthDay)) {
                        infloList.backRemotePictureInfoList.add(jpgPictureInfo);
                    }


                } else if (VariableInstance.getInstance().UploadMode == 4) {
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1 && checkNeedBackupToUSB(pictureDataInfo.showName)) {
                        infloList.backupUSBPictureInfoList.add(jpgPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 5) {

                    if (checkNeedBackupToUSB(pictureDataInfo.showName)) {
                        infloList.backupUSBPictureInfoList.add(jpgPictureInfo);
                    }
                    if (checkNeedBackupToRemote(pictureDataInfo.showName, cameraPictureInfo.yearMonthDay)) {
                        infloList.backRemotePictureInfoList.add(jpgPictureInfo);
                    }
                }
            }

            for (int i = 0; i < cameraPictureInfo.rowPictureInfos.size(); i++) {
                Integer integer = i + 1;
                PictureInfo rowPictureInfo = cameraPictureInfo.rowPictureInfos.get(i);

                if (!isOperatingDevice) {
                    return null;
                }

                PictureDateInfo pictureDataInfo = new PictureDateInfo(rowPictureInfo.pictureName);

                if (VariableInstance.getInstance().UploadMode == 1) {
                    if (checkNeedBackupToUSB(pictureDataInfo.showName)) {
                        infloList.backupUSBPictureInfoList.add(rowPictureInfo);
                    }
                    if (checkNeedBackupToRemote(pictureDataInfo.showName, cameraPictureInfo.yearMonthDay)) {
                        infloList.backRemotePictureInfoList.add(rowPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 2) {
                    if (checkNeedBackupToUSB(pictureDataInfo.showName)) {
                        infloList.backupUSBPictureInfoList.add(rowPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 3) {
                    if (checkNeedBackupToUSB(pictureDataInfo.showName)) {
                        infloList.backupUSBPictureInfoList.add(rowPictureInfo);
                    }
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if ((index > -1 || i == cameraPictureInfo.rowPictureInfos.size() - 1) && checkNeedBackupToRemote(pictureDataInfo.showName, cameraPictureInfo.yearMonthDay)) {
                        infloList.backRemotePictureInfoList.add(rowPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 4) {
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1 && checkNeedBackupToUSB(pictureDataInfo.showName)) {
                        infloList.backupUSBPictureInfoList.add(rowPictureInfo);
                    }
                    if ((index > -1 || i == cameraPictureInfo.rowPictureInfos.size() - 1) && checkNeedBackupToRemote(pictureDataInfo.showName, cameraPictureInfo.yearMonthDay)) {
                        infloList.backRemotePictureInfoList.add(rowPictureInfo);
                    }
                } else if (VariableInstance.getInstance().UploadMode == 5) {
                    if (checkNeedBackupToUSB(pictureDataInfo.showName)) {
                        infloList.backupUSBPictureInfoList.add(rowPictureInfo);
                    }
                    if (checkNeedBackupToRemote(pictureDataInfo.showName, cameraPictureInfo.yearMonthDay)) {
                        infloList.backRemotePictureInfoList.add(rowPictureInfo);
                    }
                }
            }
        }
        Collections.sort(infloList.backupUSBPictureInfoList, new DescendingOrder());
        Collections.sort(infloList.backRemotePictureInfoList, new DescendingOrder());
        return infloList;
    }

    private boolean checkNeedBackupToRemote(String name, int yymmdd) {
        if (LocalProfileHelp.getInstance().remotePictureList.contains(name)) {
            return false;
        }
        int systemTime = Utils.getyyMMddtringInt(System.currentTimeMillis());
        if (systemTime == 900101) {
            return false;
        }

        if (VariableInstance.getInstance().isUploadToday && Utils.isMoreThanThreeDate(yymmdd + "")) {
            return false;
        }

        return true;
    }

    private boolean checkNeedBackupToUSB(String name) {
        if (LocalProfileHelp.getInstance().usbPictureList.contains(name) && !deviceScannerListener.usbInitComplete()) {
            return false;
        }
        return true;
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


    public interface DeviceScannerListener {
        boolean networkIniting();

        void startScannerDevice();

        void endScannerDevice(boolean isSuccess);

        void addUploadRemoteFile(String uploadFileModel);

        boolean usbInitComplete();

        boolean uploadToUSB(String localFilePath);

        void scannerCameraComplete(int needDownloadConut, int needUpload, int pictureCont, String deviceName);

    }
}



