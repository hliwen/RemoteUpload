package com.example.nextclouddemo;

import android.annotation.SuppressLint;


import com.example.nextclouddemo.utils.Log;
import com.example.nextclouddemo.utils.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogcatHelper {
    private static final String TAG = "remotelog_LogcatHelper";
    private static LogcatHelper INSTANCE = null;

    public LogDumper mLogDumperTest = null;
    public LogDumper mLogDumperMain = null;
    private int mPId;

    private String logcatFileTestPath;
    private String logcatFileMainPath;


    private LogcatHelper() {
        init();
        mPId = android.os.Process.myPid();
    }

    /**
     * 初始化目录
     */
    public void init() {
        File file = new File(VariableInstance.getInstance().LogcatDir);
        if (!file.exists()) {
            file.mkdirs();
        }

        try {
            if (file.listFiles().length > 10) {
                Utils.resetDir(VariableInstance.getInstance().LogcatDir);
            }
        } catch (Exception e) {

        }
    }

    public static LogcatHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LogcatHelper();
        }
        return INSTANCE;
    }


    public void start() {
        String testName = "logcat" + getFileName() + "_test.txt";
        String mainName = "logcat" + getFileName() + ".txt";

        logcatFileTestPath = VariableInstance.getInstance().LogcatDir + "/" + testName;
        logcatFileMainPath = VariableInstance.getInstance().LogcatDir + "/" + mainName;

        Log.d(TAG, "startLogHelper: \n logcatFileTestPath =" + logcatFileTestPath + "\n logcatFileMainPath =" + logcatFileMainPath);

        if (mLogDumperTest == null) {
            mLogDumperTest = new LogDumper(String.valueOf(mPId), logcatFileTestPath);
        }

        if (mLogDumperMain == null) {
            mLogDumperMain = new LogDumper(String.valueOf(mPId), logcatFileMainPath);
        }

        mLogDumperTest.start();
        mLogDumperMain.start();
    }


    public void stopTestLogcat() {
        Log.e(TAG, "stopTestLogcat: logcatFileTestPath =" + logcatFileTestPath);
        if (mLogDumperTest != null) {

            mLogDumperTest.stopLogs();
            mLogDumperTest = null;
        }
    }

    public void stopTestLogcatRename(){
        try {
            File testLogcatFile = new File(logcatFileTestPath);
            if (testLogcatFile != null && testLogcatFile.exists()) {
                String fileName = testLogcatFile.getName();
                int lastIndex = fileName.lastIndexOf(".");
                if (lastIndex != -1) {
                    fileName = fileName.substring(0, lastIndex);
                }
                if (fileName.trim().contains("logcat1970")) {
                    Log.e(TAG, "stopTestLogcat: 日志开始时1970，需要重命名");
                    fileName = "logcat" + getFileName() + "_test";
                    if (!fileName.trim().contains("logcat1970")) {
                        Log.e(TAG, "stopTestLogcat: 已经同步到网络时间，重命名日志文件");
                        logcatFileTestPath = VariableInstance.getInstance().LogcatDir + "/" + fileName + ".txt";
                        File file = new File(logcatFileTestPath);
                        testLogcatFile.renameTo(file);
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    public void stopMainLogcat() {
        Log.e(TAG, "stopMainLogcat: logcatFileMainPath =" + logcatFileMainPath);
        if (mLogDumperMain != null) {
            mLogDumperMain.stopLogs();
            mLogDumperMain = null;
        }
    }

    public void stopMainLogcatRename(){
        try {
            File mainLogcatFile = new File(logcatFileMainPath);
            if (mainLogcatFile != null && mainLogcatFile.exists()) {
                String fileName = mainLogcatFile.getName();
                int lastIndex = fileName.lastIndexOf(".");
                if (lastIndex != -1) {
                    fileName = fileName.substring(0, lastIndex);
                }
                if (fileName.trim().contains("logcat1970")) {
                    Log.e(TAG, "stopMainLogcat: 日志开始时1970，需要重命名");
                    fileName = "logcat" + getFileName();
                    if (!fileName.trim().contains("logcat1970")) {
                        Log.e(TAG, "stopMainLogcat: 已经同步到网络时间，重命名日志文件");
                        logcatFileMainPath = VariableInstance.getInstance().LogcatDir + "/" + fileName + ".txt";
                        File file = new File(logcatFileMainPath);
                        mainLogcatFile.renameTo(file);
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    public String getTestLogcatPath() {
        Log.e(TAG, "getTestLogcatPath: logcatFileTestPath =" + logcatFileTestPath);
        return logcatFileTestPath;
    }

    public String getMainLogcatPath() {
        Log.e(TAG, "getMainLogcatPath: logcatFileMainPath =" + logcatFileMainPath);
        return logcatFileMainPath;
    }

    public String getFileName() {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH_mm");
        String date = format.format(new Date(System.currentTimeMillis()));
        return date;
    }

    private class LogDumper extends Thread {
        private Process logcatProc;
        private BufferedReader mReader = null;
        private boolean mRunning = true;
        String cmds = null;
        private String mPID;
        private FileOutputStream out = null;

        public LogDumper(String pid, String filePath) {
            mPID = pid;
            try {
                File file = new File(filePath);
                out = new FileOutputStream(file);
            } catch (FileNotFoundException e) {

            }

            cmds = "logcat  -s remotelog | *:e | grep \"(" + mPID + ")\"";
        }

        public void stopLogs() {
            mRunning = false;
        }

        @Override
        public void run() {
            try {
                logcatProc = Runtime.getRuntime().exec(cmds);
                mReader = new BufferedReader(new InputStreamReader(logcatProc.getInputStream()), 1024);
                String line = null;
                while (mRunning && (line = mReader.readLine()) != null) {
                    if (!mRunning) {
                        break;
                    }
                    if (line.length() == 0) {
                        continue;
                    }
                    if (out != null && line.contains(mPID)) {
                        out.write((line + "\n").getBytes());
                    }
                }

            } catch (IOException e) {

            } finally {
                if (logcatProc != null) {
                    logcatProc.destroy();
                    logcatProc = null;
                }
                if (mReader != null) {
                    try {
                        mReader.close();
                        mReader = null;
                    } catch (IOException e) {

                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {

                    }
                    out = null;
                }
            }
        }
    }

}