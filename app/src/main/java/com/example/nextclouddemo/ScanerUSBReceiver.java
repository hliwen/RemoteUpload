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
import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.github.mjdev.libaums.partition.Partition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanerUSBReceiver extends BroadcastReceiver {
    private static final String TAG = "MainActivitylog2";
    public static final String CHECK_PERMISSION = "CHECK_PERMISSION";

    private ExecutorService scanerThreadExecutor;

    private UsbDevice usbDevice;
    private UsbManager usbManager;
    private String tfCardPictureDir;
    private String tfCardUploadPictureDir;

    private ScanerUSBListener downloadFlieListener;
    private ArrayList<SameDayPicutreInfo> pictureInfoList;
    private int cameraTotalPicture;

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
            if (rhs.pictureCreateData > lhs.pictureCreateData)
                return -1;
            else if (rhs.pictureCreateData == lhs.pictureCreateData)
                return 0;
            return 1;
        }
    }

    public class SameOrder implements Comparator<SameDayPicutreInfo> {
        @Override
        public int compare(SameDayPicutreInfo lhs, SameDayPicutreInfo rhs) {
            if (rhs.yearMonthDay > lhs.yearMonthDay)
                return -1;
            else if (rhs.yearMonthDay == lhs.yearMonthDay)
                return 0;
            return 1;
        }
    }

    public ScanerUSBReceiver(Context context, ScanerUSBListener downloadFlieListener) {
        this.downloadFlieListener = downloadFlieListener;
        this.tfCardPictureDir = VariableInstance.getInstance().TFCardPictureDir;
        this.tfCardUploadPictureDir = VariableInstance.getInstance().TFCardUploadPictureDir;


        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        pictureInfoList = new ArrayList<>();

        Utils.makeDir(tfCardPictureDir);
        Utils.makeDir(tfCardUploadPictureDir);

        Log.d(TAG, "USBMTPReceiver:" +
                " \n tfcardpicturedir =" + tfCardPictureDir +
                " \n tfcarduploadpicturedir =" + tfCardUploadPictureDir
        );

    }


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED: {
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.e(TAG, "onReceive: ACTION_USB_DEVICE_ATTACHED ");
                if (usbDevice == null || usbDevice.getDeviceId() == VariableInstance.getInstance().storeUSBDeviceID) {
                    Log.e(TAG, "ACTION_USB_DEVICE_ATTACHED: usbDevice =" + usbDevice);
                    return;
                }

                this.usbDevice = usbDevice;
                try {
                    UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                    if (usbManager.hasPermission(usbDevice)) {
                        Log.e(TAG, "onReceive: hasPermission");
                        checkConnectedDevice(usbDevice);
                    } else {
                        @SuppressLint("UnspecifiedImmutableFlag")
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, new Intent(CHECK_PERMISSION), 0);
                        usbManager.requestPermission(usbDevice, pendingIntent);
                        Log.e(TAG, "onReceive: no hasPermission");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "ACTION_USB_DEVICE_ATTACHED: Exception =" + e);
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

                Log.e(TAG, "onReceive:断开USB设备 mUSBDevice id = " + usbDevice.getDeviceId() + ",storeUSBDeviceID =" + VariableInstance.getInstance().storeUSBDeviceID);
                if (usbDevice.getDeviceId() == VariableInstance.getInstance().storeUSBDeviceID) {
                    return;
                } else {
                    this.usbDevice = null;
                    downloadFlieListener.cameraUSBDetached();
                    pictureInfoList.clear();
                    stopScanerThread();
                }
            }
            break;
            case CHECK_PERMISSION:
                checkConnectedDevice(this.usbDevice);
                break;

            default:
                break;
        }
    }

    private void stopScanerThread() {
        Log.d(TAG, "stopScanerThread: ");
        try {
            if (scanerThreadExecutor != null)
                scanerThreadExecutor.shutdown();
        } catch (Exception e) {

        }
        scanerThreadExecutor = null;
    }

    public void storeUSBDetached() {
        pictureInfoList.clear();
        stopScanerThread();
    }


    public void formatCamera() {
        try {
            stopScanerThread();
            pictureInfoList.clear();
            downloadFlieListener.downloadComplete();
        } catch (Exception e) {

        }
    }


    private void checkConnectedDevice(UsbDevice device) {
        Log.e(TAG, "checkConnectedDevice:  ");
        stopScanerThread();
        scanerThreadExecutor = Executors.newSingleThreadExecutor();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "checkConnectedDevice: 开始检查USB连接设备");

                if (usbManager == null)
                    usbManager = (UsbManager) MyApplication.getContext().getSystemService(Context.USB_SERVICE);
                if (usbManager == null) {
                    Log.e(TAG, "run: usbManager==null");
                    return;
                }

                try {
                    if (device == null)
                        return;

                    for (int i = 0; i < device.getInterfaceCount(); i++) {
                        if (device == null)
                            return;
                        UsbInterface usbInterface = device.getInterface(i);
                        if (usbInterface == null)
                            continue;

                        switch (usbInterface.getInterfaceClass()) {
                            case UsbConstants.USB_CLASS_STILL_IMAGE:
                                downloadFlieListener.startDownload();
                                mtpDeviceScaner(device);
                                downloadFlieListener.downloadComplete();
                                break;
                            case UsbConstants.USB_CLASS_MASS_STORAGE:
                                downloadFlieListener.startDownload();
                                usbDeviceScaner(device);
                                downloadFlieListener.downloadComplete();
                                break;
                            default:
                                break;
                        }

                    }
                } catch (Exception e) {
                }

            }
        };
        scanerThreadExecutor.execute(runnable);
    }


    private void mtpDeviceScaner(UsbDevice usbDevice) {
        Log.d(TAG, "mtpDeviceScaner start");
        UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(usbDevice);
        if (usbDeviceConnection == null) {
            Log.e(TAG, "mtpDeviceScaner: usbDeviceConnection == null 结束扫描");
            return;
        }
        MtpDevice mtpDevice = new MtpDevice(usbDevice);
        if (mtpDevice == null || !mtpDevice.open(usbDeviceConnection)) {
            Log.e(TAG, "mtpDeviceScaner 数码相机打开失败 mtpDevice == null || !mtpDevice.open(usbDeviceConnection) 结束扫描");
            return;
        }
        int[] storageIds = mtpDevice.getStorageIds();
        if (storageIds == null) {
            Log.e(TAG, "mtpDeviceScaner: 数码相机存储卷不可用 storageIds == null 结束扫描");
            return;
        }

        pictureInfoList.clear();
        cameraTotalPicture = 0;

        for (int storageId : storageIds) {
            int[] pictureHandlesItem = mtpDevice.getObjectHandles(storageId, 0, 0);
            if (pictureHandlesItem != null)
                for (int i : pictureHandlesItem) {
                    MtpObjectInfo mtpObjectInfo = mtpDevice.getObjectInfo(i);

                    if (mtpObjectInfo == null) {
                        continue;
                    }
                    long createDate = mtpObjectInfo.getDateCreated() - 1000L * 60 * 60 * 8;
                    int yymmdd = Utils.getyyMMddtringInt(createDate);
                    String pictureName = yymmdd + "-" + mtpObjectInfo.getName();
                    String FileEnd = pictureName.substring(pictureName.lastIndexOf(".") + 1).toLowerCase();

                    if (!pictureFormatFile(FileEnd))
                        continue;

                    cameraTotalPicture++;

                    if (VariableInstance.getInstance().formarCamera) {
                        mtpDevice.deleteObject(i);
                        continue;
                    }

                    SameDayPicutreInfo sameDayPicutreInfo = new SameDayPicutreInfo(yymmdd);
                    int index = pictureInfoList.indexOf(sameDayPicutreInfo);
                    if (index > -1) {
                        sameDayPicutreInfo = pictureInfoList.get(index);
                    } else {
                        pictureInfoList.add(sameDayPicutreInfo);
                    }

                    if (!VariableInstance.getInstance().usbFileNameList.contains(pictureName)) {
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

        Collections.sort(pictureInfoList, new SameOrder());

        int pictureCount = 0;
        for (SameDayPicutreInfo sameDayPicutreInfo : pictureInfoList) {
            Collections.sort(sameDayPicutreInfo.jpgPictureInfos, new order());
            Collections.sort(sameDayPicutreInfo.rowPictureInfos, new order());
            pictureCount += sameDayPicutreInfo.jpgPictureInfos.size();
            pictureCount += sameDayPicutreInfo.rowPictureInfos.size();
        }


        Log.d(TAG, "mtpDeviceScaner: 扫描到需要下载图片 ：" + pictureCount + ",相机总共照片 cameraTotalPicture =" + cameraTotalPicture);
        downloadFlieListener.downloadUpanCount(pictureCount);
        downloadFlieListener.scanCameraComplete(cameraTotalPicture);

        for (SameDayPicutreInfo pictureItem : pictureInfoList) {
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

                for (PictureInfo pictureInfo : pictureItem.jpgPictureInfos) {
                    downloadMTPCameraPictureToTFCard(mtpDevice, pictureInfo, false);
                }
            } else if (VariableInstance.getInstance().UploadMode == 4) {
                for (int i = 0; i < pictureItem.rowPictureInfos.size(); i++) {
                    Integer integer = i + 1;
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1) {
                        downloadMTPCameraPictureToTFCard(mtpDevice, pictureItem.rowPictureInfos.get(i), true);
                    }
                }
            }
        }


        usbDeviceConnection.close();
        mtpDevice.close();
    }


    private void downloadMTPCameraPictureToTFCard(MtpDevice mtpDevice, PictureInfo pictureInfo, boolean needUpload) {
        Log.d(TAG, "downloadMTPCameraPictureToTFCard: pictureInfo =" + pictureInfo + ",needUpload =" + needUpload);
        try {
            String pictureSaveLocalPath = tfCardPictureDir + File.separator + pictureInfo.pictureName;
            File pictureSaveFile = new File(pictureSaveLocalPath);
            if (pictureSaveFile != null && pictureSaveFile.exists())
                pictureSaveFile.delete();

            mtpDevice.importFile(pictureInfo.mtpPictureID, pictureSaveLocalPath);

            if (pictureSaveFile != null && pictureSaveFile.exists()) {

                String yearMonth = Utils.getyyyyMMtring(pictureInfo.pictureCreateData);
                boolean uploadSucceed = downloadFlieListener.uploadToUSB(pictureSaveFile, yearMonth);
                pictureSaveFile.delete();

                if (uploadSucceed) {
                    if (needUpload) {
                        String pictureSaveUploadLocalPath = tfCardUploadPictureDir + File.separator + pictureInfo.pictureName;
                        File pictureUploadSaveFile = new File(pictureSaveUploadLocalPath);
                        if (pictureUploadSaveFile != null && pictureUploadSaveFile.exists())
                            pictureUploadSaveFile.delete();
                        mtpDevice.importFile(pictureInfo.mtpPictureID, pictureSaveUploadLocalPath);
                        if (pictureUploadSaveFile != null && pictureUploadSaveFile.exists())
                            downloadFlieListener.addUploadRemoteFile(new UploadFileModel(pictureSaveUploadLocalPath));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "downloadMTPCameraPictureToTFCard: 图片复制出错 e =" + e);
        }
        Log.e(TAG, "downloadMTPCameraPictureToTFCard: end");
    }


    private void usbDeviceScaner(UsbDevice usbDevice) {
        Log.d(TAG, "usbDeviceScaner start");
        if (usbDevice == null) {
            Log.e(TAG, "usbDeviceScaner: USB设备为空 结束扫描");
            return;
        }
        if (usbDevice.getDeviceId() == VariableInstance.getInstance().storeUSBDeviceID) {
            Log.e(TAG, "usbDeviceScaner 检测到当前的U盘设备为上传usb, 结束扫描");
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

        Log.d(TAG, "usbDeviceScaner: 扫描到需要下载图片 ：" + pictureCount + ",相机总共照片 cameraTotalPicture =" + cameraTotalPicture);
        downloadFlieListener.downloadUpanCount(pictureCount);
        downloadFlieListener.scanCameraComplete(cameraTotalPicture);

        for (SameDayPicutreInfo pictureItem : pictureInfoList) {
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
                for (PictureInfo pictureInfo : pictureItem.jpgPictureInfos) {
                    downloadUSBCameraPictureToTFCard(pictureInfo, false);
                }
            } else if (VariableInstance.getInstance().UploadMode == 4) {
                for (int i = 0; i < pictureItem.rowPictureInfos.size(); i++) {
                    Integer integer = i + 1;
                    int index = VariableInstance.getInstance().uploadSelectIndexList.indexOf(integer);
                    if (index > -1) {
                        downloadUSBCameraPictureToTFCard(pictureItem.rowPictureInfos.get(i), true);
                    }
                }
            }
        }
    }

    private void readPicFileFromUSBFile(FileSystem fileSystem, UsbFile usbFile) {
        try {
            UsbFile[] usbFileList = usbFile.listFiles();
            for (UsbFile usbFileItem : usbFileList) {
                if (usbFileItem.isDirectory()) {
                    readPicFileFromUSBFile(fileSystem, usbFileItem);
                } else {
                    //获取文件后缀
                    long createDate = usbFileItem.createdAt() - 1000L * 60 * 60 * 8;
                    int yymmdd = Utils.getyyMMddtringInt(createDate);

                    String fileName = yymmdd + "-" + usbFileItem.getName();
                    String FileEnd = fileName.substring(usbFileItem.getName().lastIndexOf(".") + 1).toLowerCase();

                    if (!pictureFormatFile(FileEnd))
                        continue;
                    cameraTotalPicture++;

                    if (VariableInstance.getInstance().formarCamera) {
                        usbFile.delete();
                        continue;
                    }

                    SameDayPicutreInfo sameDayPicutreInfo = new SameDayPicutreInfo(yymmdd);
                    int index = pictureInfoList.indexOf(sameDayPicutreInfo);
                    if (index > -1) {
                        sameDayPicutreInfo = pictureInfoList.get(index);
                    } else {
                        pictureInfoList.add(sameDayPicutreInfo);
                    }

                    if (!VariableInstance.getInstance().usbFileNameList.contains(fileName)) {
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
        } catch (IOException e) {
            Log.e(TAG, "readPicFileFromUSBFile: 遍历USB文件异常 e:" + e);
        }
    }


    private void downloadUSBCameraPictureToTFCard(PictureInfo pictureInfo, boolean needUpload) {
        Log.d(TAG, "downloadUSBCameraPictureToTFCard: pictureInfo =" + pictureInfo + ",needUpload =" + needUpload);

        FileOutputStream out = null;
        InputStream in = null;
        String pictureSavePath = null;
        File pictureSaveLocalFile = null;

        try {
            pictureSavePath = tfCardPictureDir + File.separator + pictureInfo.pictureName;
            pictureSaveLocalFile = new File(pictureSavePath);

            if (pictureSaveLocalFile.exists())
                pictureSaveLocalFile.delete();
            out = new FileOutputStream(pictureSavePath);
            in = new UsbFileInputStream(pictureInfo.cameraUsbFile);
            int bytesRead = 0;
            byte[] buffer = new byte[pictureInfo.cameraUsbFileSystem.getChunkSize()];//作者的推荐写法是currentFs.getChunkSize()为buffer长度
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
                        String pictureSaveUploadLocalPath = tfCardUploadPictureDir + File.separator + pictureInfo.pictureName;
                        File pictureUploadSaveFile = new File(pictureSaveUploadLocalPath);
                        if (pictureUploadSaveFile.exists())
                            pictureUploadSaveFile.delete();

                        uploadout = new FileOutputStream(pictureSaveUploadLocalPath);
                        uploadin = new UsbFileInputStream(pictureInfo.cameraUsbFile);
                        int bytesRead = 0;
                        byte[] buffer = new byte[pictureInfo.cameraUsbFileSystem.getChunkSize()];//作者的推荐写法是currentFs.getChunkSize()为buffer长度
                        while ((bytesRead = uploadin.read(buffer)) != -1) {
                            uploadout.write(buffer, 0, bytesRead);
                        }

                        if (pictureUploadSaveFile != null && pictureUploadSaveFile.exists())
                            downloadFlieListener.addUploadRemoteFile(new UploadFileModel(pictureSaveUploadLocalPath));
                    }
                }
            }
        } catch (Exception e) {
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
        if ((FileEnd.equals("nif") || FileEnd.equals("crw") || FileEnd.equals("raw")
                || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("crw")
                || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2")) || (FileEnd.equals("jpg")))
            return true;
        return false;
    }

    private boolean rowFormatFile(String FileEnd) {
        if ((FileEnd.equals("nif") || FileEnd.equals("crw") || FileEnd.equals("raw")
                || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("crw")
                || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2")))
            return true;
        return false;
    }

    private boolean jPGFormatFile(String FileEnd) {
        if ((FileEnd.equals("jpg")))
            return true;
        return false;
    }


    public interface ScanerUSBListener {
        void addUploadRemoteFile(UploadFileModel uploadFileModel);

        void startDownload();

        void downloadComplete();

        void downloadUpanCount(int size);

        boolean uploadToUSB(File localFile, String yearMonth);

        void cameraUSBDetached();

        void scanCameraComplete(int pictureCont);
    }


}

