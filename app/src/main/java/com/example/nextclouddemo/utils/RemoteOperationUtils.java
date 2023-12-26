package com.example.nextclouddemo.utils;

import android.annotation.SuppressLint;

import com.example.nextclouddemo.LogcatHelper;
import com.example.nextclouddemo.VariableInstance;
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
    private static final String TAG = "remotelog_RemoteOperationUtils";
    private static int requestFailure = -1;
    private static int fileExist = 1;
    private static int noExist = 0;
    long uploadTatalTime;
    private Thread pictureWorkThread;
    public boolean pictureIsThreadStop;
    public static final String cameraDir = "CameraPicture";
    private static final String logcatDir = "Locat";
    private String userNameDir;
    private String remoteLogcatDir;
    private String remoteCameraDir;
    private String remoteCameraMonthDayDir;
    private RemoteOperationListener remoteOperationListener;

    private List<String> remotePictureDirList;

    public volatile BlockingQueue<String> pictureFileListCache = new LinkedBlockingQueue<>(20000);

    public RemoteOperationUtils(RemoteOperationListener remoteOperationListener) {
        this.remoteOperationListener = remoteOperationListener;
        this.pictureIsThreadStop = true;
        VariableInstance.getInstance().uploadRemorePictureNum = 0;
    }

    public boolean initRemoteDir(String deveceName) {

        String yearMonthFileDir = Utils.getyyyyMMString();

        userNameDir = FileUtils.PATH_SEPARATOR + deveceName + FileUtils.PATH_SEPARATOR;

        remoteLogcatDir = userNameDir + logcatDir + FileUtils.PATH_SEPARATOR;
        remoteCameraDir = userNameDir + cameraDir + FileUtils.PATH_SEPARATOR;

        remoteCameraMonthDayDir = remoteCameraDir + yearMonthFileDir + FileUtils.PATH_SEPARATOR;

        Log.d(TAG, "initRemoteDir: " + "\n userNameDir =" + userNameDir + "\n remoteLogcatDir =" + remoteLogcatDir + "\n remoteCameraDir =" + remoteCameraDir + "\n remoteCameraMonthDayDir =" + remoteCameraMonthDayDir);

        int result = checkFileExit(FileUtils.PATH_SEPARATOR, userNameDir);


        if (result == requestFailure) {
            Log.d(TAG, "initRemoteDir: 远程创建客户名称路径出错");
            return false;
        }

        if (result == fileExist) {
            ReadFolderRemoteOperation refreshOperation = new ReadFolderRemoteOperation(deveceName);
            RemoteOperationResult remoteOperationResult = refreshOperation.execute(VariableInstance.getInstance().ownCloudClient);
            if (remoteOperationResult == null || !remoteOperationResult.isSuccess()) {
                Log.e(TAG, "initRemoteDir 获取文件列表失败: " + result);


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

                Log.d(TAG, "initRemoteDir: 创建获取远程日期文件列表失败");
                return false;
            }
            if (!exictL) {
                createFilefolder(remoteLogcatDir);
            }
        } else {
            boolean createResult = createFilefolder(userNameDir);
            if (!createResult) {
                Log.d(TAG, "initRemoteDir: 远程创建客户名称路径出错");

                return false;
            }
            createResult = createFilefolder(remoteCameraDir);
            if (!createResult) {
                Log.d(TAG, "initRemoteDir: 创建获取远程主文件列表失败");
                return false;
            }
            createResult = createFilefolder(yearMonthFileDir);
            if (!createResult) {
                Log.d(TAG, "initRemoteDir: ");
                return false;
            }
        }

        if (remotePictureDirList == null) {
            remotePictureDirList = new ArrayList<>();
        }

        if (yearMonthFileDir != null && !remotePictureDirList.contains(yearMonthFileDir)) {
            remotePictureDirList.add(yearMonthFileDir);
        }

        getRemoteAllPictureDir(deveceName);

        if (!LocalProfileHelp.getInstance().initLocalRemotePictureList()) {
            LocalProfileHelp.getInstance().createRemotePictureList(deveceName);
        }
        return true;
    }


    private void getRemoteAllPictureDir(String userName) {
        if (remotePictureDirList == null) {
            remotePictureDirList = new ArrayList<>();
        }

        ReadFolderRemoteOperation dateReadFolderRemoteOperation = new ReadFolderRemoteOperation(userName + "/" + RemoteOperationUtils.cameraDir);
        RemoteOperationResult dateRemoteOperationResult = dateReadFolderRemoteOperation.execute(VariableInstance.getInstance().ownCloudClient);
        if (dateRemoteOperationResult == null || !dateRemoteOperationResult.isSuccess()) {
            Log.e(TAG, "getRemoteAllPictureDir: dateRemoteOperationResult =" + dateRemoteOperationResult);
            return;
        }

        for (Object obj : dateRemoteOperationResult.getData()) {
            RemoteFile pictureDir = (RemoteFile) obj;
            String remotePath = pictureDir.getRemotePath();

            try {
                remotePath = remotePath.substring(remoteCameraDir.length(), remotePath.length() - 1);
                if (remotePath == null || remotePath.trim().isEmpty()) {
                    continue;
                }

                if (!remotePictureDirList.contains(remotePath)) {
                    remotePictureDirList.add(remotePath);
                }
            } catch (Exception e) {

            }
        }
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

    public void addUploadRemoteFile(String uploadFileModel, boolean uploadFaild) {
        Log.e(TAG, "addUploadRemoteFile: uploadFaild =" + uploadFaild + ",fileListCache =" + pictureFileListCache.size() + ",uploadFileModel =" + uploadFileModel);

        if (uploadFileModel == null) return;

        if (!pictureFileListCache.contains(uploadFileModel)) {
            pictureFileListCache.add(uploadFileModel);
        }
        if (VariableInstance.getInstance().ownCloudClient != null && VariableInstance.getInstance().remoteServerAvailable) {
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

                    String fileModel = null;
                    try {
                        Log.e(TAG, "run: 缓存需要上传到服务器的照片数量，fileListCache.size =" + pictureFileListCache.size());
                        List<String> list = new ArrayList<>(pictureFileListCache);
                        Collections.sort(list, new MyOrder());
                        pictureFileListCache = new LinkedBlockingQueue<>(list);
                        fileModel = pictureFileListCache.poll(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        Log.e(TAG, "startUploadThread: e =" + e);
                    }

                    if (fileModel == null) {
                        File localTpmFlie = new File(VariableInstance.getInstance().TFCardUploadPictureDir);
                        if (localTpmFlie != null && localTpmFlie.exists()) {
                            File[] files = localTpmFlie.listFiles();
                            if (files != null) {
                                for (File file : files) {
                                    String uploadFileModel = file.getAbsolutePath();
                                    Log.d(TAG, "run: uploadFileModel 上一次没传完的 " + uploadFileModel);
                                    addUploadRemoteFile(uploadFileModel, false);
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
            if (remoteOperationListener != null)
                remoteOperationListener.allFileUploadComplete(uploadTatalTime);
            try {
                pictureWorkThread.interrupt();
                pictureWorkThread.join(100);
            } catch (Exception e) {
                try {
                    pictureWorkThread.interrupt();
                } catch (Exception e1) {
                    Log.e(TAG, "stopUploadThread: Exception1 = " + e1);
                }
            }
        }
        pictureWorkThread = null;
        if (pictureFileListCache != null) {
            pictureFileListCache.clear();
        }
    }

    private void uploadImageFileToRemote(String fileLocalPath) {

        if (fileLocalPath == null) {
            return;
        }
        File file = new File(fileLocalPath);
        if (file == null || !file.exists()) {
            Log.e(TAG, "uploadImageFileToRemote: " + fileLocalPath + ",文件不存在");
            return;
        }

        if (VariableInstance.getInstance().ownCloudClient == null || !VariableInstance.getInstance().remoteServerAvailable) {
            addUploadRemoteFile(fileLocalPath, true);
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
        String logcalFileName = file.getName();

        PictureDataInfo pictureDataInfo = new PictureDataInfo(logcalFileName);

        if (VariableInstance.getInstance().isUploadToday) {
            try {

                int systemTime = Utils.getyyMMddtringInt(System.currentTimeMillis());
                if (systemTime == 900101) {
                    if (file.exists()) {
                        file.delete();
                    }
                    return;
                }
                if (Utils.isBigThreeDate(pictureDataInfo.pictureCreateData)) {
                    Log.e(TAG, "uploadImageFileToRemote: 只上传最近3天的照片，当前照片不是最近3天不上传， 照片路径：" + file.getAbsolutePath());
                    if (file.exists()) {
                        file.delete();
                    }
                    return;
                }
            } catch (Exception e) {

            }
        }
        String remoteDir = remoteCameraMonthDayDir;
        try {

            if (VariableInstance.getInstance().UploadMode == 5) {
                Log.e(TAG, "uploadImageFileToRemote: hhmm =" + pictureDataInfo.hhmm);
                if (remotePictureDirList.contains(pictureDataInfo.hhmm)) {
                    remoteDir = remoteCameraDir + pictureDataInfo.hhmm + FileUtils.PATH_SEPARATOR;
                } else {
                    boolean createResult = createFilefolder(remoteCameraDir + pictureDataInfo.hhmm + FileUtils.PATH_SEPARATOR);
                    if (createResult) {
                        Log.e(TAG, "uploadImageFileToRemote: 远程不存在" + pictureDataInfo.hhmm + "文件夹，创建文件夹成功");
                        remotePictureDirList.add(pictureDataInfo.hhmm);
                        remoteDir = remoteCameraDir +pictureDataInfo. hhmm + FileUtils.PATH_SEPARATOR;
                    } else {
                        Log.e(TAG, "uploadImageFileToRemote: 远程不存在" + pictureDataInfo.hhmm + "文件夹，创建文件夹失败");
                    }
                }
            } else {
                Log.e(TAG, "uploadImageFileToRemote: monthDayDir =" +pictureDataInfo. yearMonth);
                if (remotePictureDirList.contains(pictureDataInfo.yearMonth)) {
                    remoteDir = remoteCameraDir + pictureDataInfo.yearMonth + FileUtils.PATH_SEPARATOR;
                } else {
                    boolean createResult = createFilefolder(remoteCameraDir +pictureDataInfo. yearMonth + FileUtils.PATH_SEPARATOR);
                    if (createResult) {
                        Log.e(TAG, "uploadImageFileToRemote: 远程不存在" + pictureDataInfo.yearMonth + "文件夹，创建文件夹成功");
                        remotePictureDirList.add(pictureDataInfo.yearMonth);
                        remoteDir = remoteCameraDir + pictureDataInfo.yearMonth + FileUtils.PATH_SEPARATOR;
                    } else {
                        Log.e(TAG, "uploadImageFileToRemote: 远程不存在" + pictureDataInfo.yearMonth + "文件夹，创建文件夹失败");
                    }
                }
            }


        } catch (Exception e) {

        }

        String remotePath = remoteDir + pictureDataInfo.showName;
        Long timeStampLong = file.lastModified() / 1000;
        String timeStamp = timeStampLong.toString();

        Log.e(TAG, "uploadImageFileToRemote: " + fileLocalPath + ",remotePath =" + remotePath + ",file.exit =" + file.exists());

        long startTime = System.currentTimeMillis();
        remoteOperationListener.pictureUploadStart();
        UploadFileRemoteOperation uploadOperation = new UploadFileRemoteOperation(file.getAbsolutePath(), remotePath, "image/png", timeStamp);
        RemoteOperationResult result = uploadOperation.execute(VariableInstance.getInstance().ownCloudClient);

        if (result == null) {
            remoteOperationListener.pictureUploadEnd(false);
            Log.e(TAG, "uploadImageFileToRemote: result == null ");
            addUploadRemoteFile(fileLocalPath, true);
            return;
        }
        boolean isSuccess = result.isSuccess();
        Log.d(TAG, "uploadImageFileToRemote: isSuccess =" + isSuccess + ",result = " + result.getLogMessage());
        remoteOperationListener.pictureUploadEnd(isSuccess);
        if (isSuccess) {
            LocalProfileHelp.getInstance().addLocalRemotePictureList(pictureDataInfo.showName);
            VariableInstance.getInstance().uploadRemorePictureNum++;
            long totalTime = (System.currentTimeMillis() - startTime) / 1000;
            Log.e(TAG, "uploadImageFileToRemote: 单张用时" + totalTime + "S,fileSize =" + fileSize);
            if (totalTime != 0) {
                remoteOperationListener.updateUploadSpeed(("" + (fileSize / totalTime / 1024)));
            }
            if (file.exists()) {
                file.delete();
            }
        } else {
            if (uploadFaildPath == null) {
                uploadFaildCount = 0;
                uploadFaildPath = fileLocalPath;
            }

            if (fileLocalPath.equals(uploadFaildPath)) {
                uploadFaildCount++;
            }

            if (uploadFaildCount > 5) {
                uploadFaildPath = null;
                uploadFaildCount = 0;

                if (file.exists()) {
                    file.delete();
                }
            } else {
                addUploadRemoteFile(fileLocalPath, true);
            }
        }

    }

    private String uploadFaildPath;
    private int uploadFaildCount;

    public void startUploadMainLocatThread(boolean delect) {
        Log.e(TAG, "startUploadMainLocatThread: ");

        remoteOperationListener.uploadLogcatComplete();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    if (LogcatHelper.getInstance().mLogDumperMain == null || VariableInstance.getInstance().ownCloudClient == null) {
                        Log.e(TAG, "startUploadMainLocatThread: = null ");
                        if (delect) {
                            remoteOperationListener.uploadLogcatComplete();
                        }
                        return;
                    }

                    Log.e(TAG, "startUploadMainLocatThread: delect =" + delect);
                    if (delect) {
                        LogcatHelper.getInstance().stopMainLogcat();
                        try {
                            Thread.sleep(1000);
                            LogcatHelper.getInstance().stopMainLogcatRename();
                        } catch (Exception e) {
                        }
                        remoteOperationListener.startUploadLogcatToUsb();
                    }

                    String path = LogcatHelper.getInstance().getMainLogcatPath();
                    File mainLogFile = new File(path);

                    if (mainLogFile == null || !mainLogFile.exists()) {
                        Log.e(TAG, "startUploadMainLocatThread 日志文件不存在：" + path);
                        if (delect) {
                            remoteOperationListener.uploadLogcatComplete();
                        }
                        return;
                    }

                    String fileName = mainLogFile.getName();

                    int lastIndex = fileName.lastIndexOf(".");
                    if (lastIndex != -1) {
                        fileName = fileName.substring(0, lastIndex);
                    }

                    if (fileName.trim().contains("logcat1970")) {
                        Log.e(TAG, "run: 日志开始时1970，需要重命名");
                        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH_mm");
                        String date = format.format(new Date(System.currentTimeMillis()));
                        fileName = "logcat" + date;
                    }
                    Long timeStampLong = System.currentTimeMillis() / 1000;
                    String timeStamp = timeStampLong.toString();

                    String remotePath = remoteLogcatDir + fileName + ".txt";
                    if (!delect) {
                        remotePath = remoteLogcatDir + fileName + "_" + System.currentTimeMillis() + ".txt";
                    }

                    Log.e(TAG, "run: startUploadMainLocatThread = " + remotePath);
                    UploadFileRemoteOperation uploadOperation = new UploadFileRemoteOperation(mainLogFile.getAbsolutePath(), remotePath, "text/plain", timeStamp);
                    RemoteOperationResult result = uploadOperation.execute(VariableInstance.getInstance().ownCloudClient);

                    Log.e(TAG, "run: startUploadMainLocatThread result = " + result);

                } catch (Exception e) {

                }
                if (delect) {
                    Log.e(TAG, "run:  startUploadMainLocatThread uploadLogcatComplete");
                    remoteOperationListener.uploadLogcatComplete();
                }

            }
        }).start();
    }


    public class MyOrder implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
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
