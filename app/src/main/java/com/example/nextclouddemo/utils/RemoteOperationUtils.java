package com.example.nextclouddemo.utils;

import android.annotation.SuppressLint;

import com.example.nextclouddemo.ErrorName;
import com.example.nextclouddemo.LogcatHelper;
import com.example.nextclouddemo.VariableInstance;
import com.example.nextclouddemo.model.UploadFileModel;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.CreateFolderRemoteOperation;
import com.owncloud.android.lib.resources.files.FileUtils;
import com.owncloud.android.lib.resources.files.ReadFolderRemoteOperation;
import com.owncloud.android.lib.resources.files.UploadFileRemoteOperation;
import com.owncloud.android.lib.resources.files.model.RemoteFile;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class RemoteOperationUtils {
    private static final String TAG = "MainActivitylog3";
    private static int requestFailure = -1;
    private static int fileExist = 1;
    private static int noExist = 0;
    long uploadTatalTime;
    private Thread pictureWorkThread;
    public boolean pictureIsThreadStop;
    private static final String cameraDir = "CameraPicture";
    private static final String logcatDir = "Locat";
    private String userNameDir;
    private String remoteLogcatDir;
    private String remoteCameraDir;
    private String remoteCameraMonthDayDir;
    private RemoteOperationListener remoteOperationListener;
    public volatile BlockingQueue<UploadFileModel> pictureFileListCache = new LinkedBlockingQueue<>(20000);

    public RemoteOperationUtils(RemoteOperationListener remoteOperationListener) {
        this.remoteOperationListener = remoteOperationListener;
        this.pictureIsThreadStop = true;
        VariableInstance.getInstance().uploadRemorePictureNum = 0;
    }

    public boolean initRemoteDir(String userName) {

        String yearMonthFileDir = Utils.getyyyyMMString();

        userNameDir = FileUtils.PATH_SEPARATOR + userName + FileUtils.PATH_SEPARATOR;

        remoteLogcatDir = userNameDir + logcatDir + FileUtils.PATH_SEPARATOR;
        remoteCameraDir = userNameDir + cameraDir + FileUtils.PATH_SEPARATOR;

        remoteCameraMonthDayDir = remoteCameraDir + yearMonthFileDir + FileUtils.PATH_SEPARATOR;

        Log.d(TAG, "initRemoteDir: " + "\n userNameDir =" + userNameDir + "\n remoteLogcatDir =" + remoteLogcatDir + "\n remoteCameraDir =" + remoteCameraDir + "\n remoteCameraMonthDayDir =" + remoteCameraMonthDayDir);

        int result = checkFileExit(FileUtils.PATH_SEPARATOR, userNameDir);

        if (result == requestFailure) {
            VariableInstance.getInstance().errorLogNameList.add(ErrorName.远程创建客户名称路径出错);
            return false;
        }

        if (result == fileExist) {
            ReadFolderRemoteOperation refreshOperation = new ReadFolderRemoteOperation(userName);
            RemoteOperationResult remoteOperationResult = refreshOperation.execute(VariableInstance.getInstance().ownCloudClient);
            if (remoteOperationResult == null || !remoteOperationResult.isSuccess()) {
                Log.e(TAG, "initRemoteDir 获取文件列表失败: " + result);
                VariableInstance.getInstance().errorLogNameList.add(ErrorName.获取远程文件列表失败);
                return false;
            }
            boolean exictP = false;
            boolean exictL = false;
            for (Object obj : remoteOperationResult.getData()) {
                RemoteFile remoteFile = (RemoteFile) obj;
                if (remoteFile.getRemotePath().contains(cameraDir)) {
                    exictP = true;
                } else if (remoteFile.getRemotePath().contains(logcatDir)) {
                    exictL = true;
                }
            }

            boolean checkCameraPath = checkResult(exictP, remoteCameraDir, remoteCameraMonthDayDir);
            if (!checkCameraPath) {
                VariableInstance.getInstance().errorLogNameList.add(ErrorName.创建获取远程日期文件列表失败);
                return false;
            }
            if (!exictL) {
                createFilefolder(remoteLogcatDir);
            }
        } else {
            boolean createResult = createFilefolder(userNameDir);
            if (!createResult) {
                VariableInstance.getInstance().errorLogNameList.add(ErrorName.远程创建客户名称路径出错);
                return false;
            }
            createResult = createFilefolder(remoteCameraDir);
            if (!createResult) {
                VariableInstance.getInstance().errorLogNameList.add(ErrorName.创建获取远程主文件列表失败);
                return false;
            }
            createResult = createFilefolder(remoteCameraMonthDayDir);
            if (!createResult) {
                VariableInstance.getInstance().errorLogNameList.add(ErrorName.创建获取远程日期文件列表失败);
                return false;
            }
        }
        return true;
    }

    private boolean checkResult(boolean exit, String dir1, String dir2) {
        if (exit) {
            int result = checkFileExit(dir1, dir2);
            if (result == requestFailure) return false;
            if (result == fileExist) {
                return true;
            } else {
                boolean createResult = createFilefolder(dir2);
                if (!createResult) return false;
            }
        } else {
            boolean createResult = createFilefolder(dir1);
            if (!createResult) return false;
            createResult = createFilefolder(dir2);
            if (!createResult) return false;
        }
        return true;
    }

    public int checkFileExit(String remote, String dir) {
        Log.v(TAG, "checkFileExit: remote =" + remote + ",dir =" + dir);
        if (VariableInstance.getInstance().ownCloudClient == null) return requestFailure;
        ReadFolderRemoteOperation refreshOperation = new ReadFolderRemoteOperation(remote);
        RemoteOperationResult result = refreshOperation.execute(VariableInstance.getInstance().ownCloudClient);
        if (result == null || !result.isSuccess()) {
            Log.e(TAG, "checkFileExit 获取文件列表失败: " + result);
            return requestFailure;
        }
        boolean exict = false;
        for (Object obj : result.getData()) {
            RemoteFile remoteFile = (RemoteFile) obj;
            if (remoteFile.getRemotePath().equals(dir)) {
                exict = true;
                break;
            }
        }
        if (exict) {
            return fileExist;
        } else {
            return noExist;
        }
    }

    public boolean createFilefolder(String flieFolder) {
        Log.i(TAG, "createFilefolder: flieFolder =" + flieFolder);
        if (VariableInstance.getInstance().ownCloudClient == null) return false;
        CreateFolderRemoteOperation createOperation = new CreateFolderRemoteOperation(flieFolder, false);
        RemoteOperationResult result = createOperation.execute(VariableInstance.getInstance().ownCloudClient);
        if (result == null || !result.isSuccess()) {
            Log.e(TAG, "createImeiFilefolder 创建文件夹失败: " + result);
            return false;
        }
        return true;
    }

    public void addUploadRemoteFile(UploadFileModel uploadFileModel, boolean uploadFaild) {
        Log.e(TAG, "addUploadRemoteFile: uploadFaild =" + uploadFaild + ",fileListCache =" + pictureFileListCache.size() + ",uploadFileModel =" + uploadFileModel);

        if (uploadFileModel == null)
            return;
        if (!pictureFileListCache.contains(uploadFileModel)) {
            pictureFileListCache.add(uploadFileModel);
        }
        if (VariableInstance.getInstance().ownCloudClient != null && VariableInstance.getInstance().isConnectedRemote) {
            startUploadFirstLocatThread();
            startCameraPictureUploadThread();
        }
    }

    public void startCameraPictureUploadThread() {
        Log.d(TAG, "startCameraPictureUploadThread: ");
        pictureIsThreadStop = false;
        if (pictureWorkThread != null) {
            return;
        }
        uploadTatalTime = 0;
        pictureWorkThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.interrupted() && !pictureIsThreadStop) {

                    if (VariableInstance.getInstance().isFormatingUSB || VariableInstance.getInstance().isFormaringCamera) {
                        Log.e(TAG, "startCameraPictureUploadThread 正在执行格式化，直接返回，不需要上传远程服务器");
                        return;
                    }

                    UploadFileModel fileModel = null;
                    try {
                        Log.e(TAG, "run: 缓存需要上传到服务器的照片数量，fileListCache.size =" + pictureFileListCache.size());
                        List<UploadFileModel> list = new ArrayList<>(pictureFileListCache);
                        Collections.sort(list, new MyOrder());
                        pictureFileListCache = new LinkedBlockingQueue<>(list);
                        fileModel = pictureFileListCache.poll(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "startUploadThread: e =" + e);
                    }

                    if (fileModel == null) {
                        File localTpmFlie = new File(VariableInstance.getInstance().TFCardUploadPictureDir);
                        if (localTpmFlie != null && localTpmFlie.exists()) {
                            File[] files = localTpmFlie.listFiles();
                            if (files != null) {
                                for (File file : files) {
                                    UploadFileModel uploadFileModel = new UploadFileModel(file.getAbsolutePath());
                                    Log.d(TAG, "run: uploadFileModel 上一次没传完的 " + uploadFileModel);
                                    addUploadRemoteFile(fileModel, false);
                                }
                            }
                        }
                        if (pictureFileListCache.size() == 0) {
                            if (canStopPictureUploadThread()) {
                                stopUploadThread();
                                return;
                            }
                        }
                    } else {

                        long startTime = System.currentTimeMillis();
                        uploadImageFileToRemote(fileModel);
                        uploadTatalTime += (System.currentTimeMillis() - startTime);
                    }
                }
            }
        });
        pictureWorkThread.start();
    }

    boolean canStopPictureUploadThread() {
        return remoteOperationListener.canStopPictureUploadThread();
    }

    public void stopUploadThread() {
        Log.e(TAG, "stopUploadThread: ");
        if (pictureWorkThread != null) {
            pictureIsThreadStop = true;
            remoteOperationListener.allFileUploadComplete(uploadTatalTime);
            try {
                pictureWorkThread.interrupt();
                pictureWorkThread.join(100);
            } catch (Exception e) {
                Log.e(TAG, "stop: Exception = " + e);
                try {
                    pictureWorkThread.interrupt();
                } catch (Exception e1) {
                    Log.e(TAG, "stop: Exception1 = " + e1);
                }
            }
        }
        pictureWorkThread = null;
        pictureFileListCache.clear();
    }

    private void uploadImageFileToRemote(UploadFileModel fileModel) {

        if (fileModel == null) {
            return;
        }
        File file = new File(fileModel.localPath);
        if (file == null || !file.exists()) {
            Log.e(TAG, "uploadImageFileToRemote: " + fileModel.localPath + ",文件不存在");
            return;
        }

        if (VariableInstance.getInstance().ownCloudClient == null || !VariableInstance.getInstance().isConnectedRemote) {
            addUploadRemoteFile(fileModel, true);
            return;
        }

        long fileSize = 0;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            fileSize = fis.available();
            fis.close();
        } catch (Exception e) {
            Log.e(TAG, "uploadImageFileToRemote: Exception =" + e);
        }
        String fileName = file.getName();
        String remotePath = remoteCameraMonthDayDir + fileName;
        Long timeStampLong = file.lastModified() / 1000;
        String timeStamp = timeStampLong.toString();

        Log.e(TAG, "uploadImageFileToRemote: " + fileModel.localPath + ",remotePath =" + remotePath + ",file.exit =" + file.exists());

        if (VariableInstance.getInstance().isUploadToday) {
            try {
                String fileDay = fileName.substring(0, fileName.indexOf("-"));

                @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd");
                Date date = dateFormat.parse(fileDay);
                long timeDifference = Math.abs(System.currentTimeMillis() - date.getTime());
                long days = timeDifference / (32 * 60 * 60 * 1000);
                if (days > 1) {
                    Log.e(TAG, "uploadImageFileToRemote: 只上传当天的照片，当前照片不是当天的，不上传 照片路径：" + file.getAbsolutePath());
                    if (file.exists()) {
                        file.delete();
                    }
                    return;
                }
            } catch (Exception e) {

            }
        }

        long startTime = System.currentTimeMillis();
        remoteOperationListener.pictureUploadStart();
        UploadFileRemoteOperation uploadOperation = new UploadFileRemoteOperation(file.getAbsolutePath(), remotePath, "image/png", timeStamp);
        RemoteOperationResult result = uploadOperation.execute(VariableInstance.getInstance().ownCloudClient);

        if (result == null) {
            remoteOperationListener.pictureUploadEnd(false);
            Log.e(TAG, "uploadImageFileToRemote: result == null ");
            addUploadRemoteFile(fileModel, true);
            return;
        }
        boolean isSuccess = result.isSuccess();
        Log.d(TAG, "uploadImageFileToRemote: isSuccess =" + isSuccess);
        remoteOperationListener.pictureUploadEnd(isSuccess);
        if (isSuccess) {
            VariableInstance.getInstance().uploadRemorePictureNum++;
            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
            Log.e(TAG, "uploadImageFileToRemote: " + totalTime + ",fileSize =" + fileSize);
            if (totalTime != 0) {
                remoteOperationListener.updateUploadSpeed(("" + (fileSize / totalTime / 1024)));
            }
            if (file.exists()) {
                file.delete();
            }
        } else {
            addUploadRemoteFile(fileModel, true);
        }

    }

    public void startUploadLocatThread() {
        Log.e(TAG, "startUploadLocatThread: ");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    if (LogcatHelper.getInstance().mLogDumperSecond == null) {
                        return;
                    }
                    if (VariableInstance.getInstance().ownCloudClient == null) {
                        return;
                    }

                    LogcatHelper.getInstance().stopSecond();
                    Thread.sleep(1000);
                    remoteOperationListener.startUploadLogcatToUsb();

                    File file = new File(LogcatHelper.getInstance().logcatFileSecondPath);

                    if (file == null || !file.exists()) {
                        return;
                    }

                    String remotePath = remoteLogcatDir + file.getName();
                    if (remotePath.startsWith("logcat1970")) {
                        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH_mm");
                        String date = format.format(new Date(System.currentTimeMillis()));
                        remotePath = remoteCameraDir + "logcat" + date + ".txt";
                    }
                    Long timeStampLong = file.lastModified() / 1000;
                    String timeStamp = timeStampLong.toString();
                    UploadFileRemoteOperation uploadOperation = new UploadFileRemoteOperation(file.getAbsolutePath(), remotePath, "text/plain", timeStamp);
                    RemoteOperationResult result = uploadOperation.execute(VariableInstance.getInstance().ownCloudClient);

                    if (result.isSuccess()) {
                        file.delete();
                    }
                } catch (Exception e) {

                }
                Log.e(TAG, "run:  uploadLogcatComplete");
                remoteOperationListener.uploadLogcatComplete();
            }
        }).start();
    }

    public void startUploadFirstLocatThread() {
        Log.e(TAG, "startUploadFirstLocatThread: ");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (LogcatHelper.getInstance().mLogDumperFirst == null) {
                        return;
                    }
                    if (VariableInstance.getInstance().ownCloudClient == null) {
                        return;
                    }

                    LogcatHelper.getInstance().stopFirst();
                    Thread.sleep(1000);

                    File file = new File(LogcatHelper.getInstance().logcatFileFirstPath);
                    if (file == null || !file.exists()) return;

                    String remotePath = remoteLogcatDir + file.getName();
                    if (remotePath.startsWith("logcat1970")) {
                        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH_mm");
                        String date = format.format(new Date(System.currentTimeMillis()));
                        remotePath = remoteCameraDir + "logcat" + date + "AAA.txt";
                    }
                    Long timeStampLong = file.lastModified() / 1000;
                    String timeStamp = timeStampLong.toString();
                    UploadFileRemoteOperation uploadOperation = new UploadFileRemoteOperation(file.getAbsolutePath(), remotePath, "text/plain", timeStamp);
                    RemoteOperationResult result = uploadOperation.execute(VariableInstance.getInstance().ownCloudClient);

                    if (result.isSuccess()) file.delete();

                } catch (Exception e) {

                }
            }
        }).start();

    }

    public class MyOrder implements Comparator<UploadFileModel> {
        @Override
        public int compare(UploadFileModel o1, UploadFileModel o2) {
            return o2.toString().compareTo(o1.toString());
        }
    }

    public interface RemoteOperationListener {
        void allFileUploadComplete(long totalTime);

        void pictureUploadStart();

        void pictureUploadEnd(boolean uploadResult);


        void updateUploadSpeed(String speed);


        void uploadLogcatComplete();

        boolean canStopPictureUploadThread();


        void startUploadLogcatToUsb();
    }
}
