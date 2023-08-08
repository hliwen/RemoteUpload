package com.example.nextclouddemo;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogcatHelper {
    private static final String TAG = "MainActivitylog";
    private static LogcatHelper INSTANCE = null;

    public LogDumper mLogDumperFirst = null;
    public LogDumper mLogDumperSecond = null;
    private int mPId;

    public String logcatFileFirstPath;
    public String logcatFileSecondPath;

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
    }

    public static LogcatHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LogcatHelper();
        }
        return INSTANCE;
    }


    public void start() {

        String logcatName = getFileName();

        String first = "logcat" + logcatName + "_AAA.txt";
        String second = "logcat" + logcatName + ".txt";

        logcatFileFirstPath = VariableInstance.getInstance().LogcatDir + "/" + first;
        logcatFileSecondPath = VariableInstance.getInstance().LogcatDir + "/" + second;

        if (mLogDumperFirst == null) {
            mLogDumperFirst = new LogDumper(String.valueOf(mPId), logcatFileFirstPath);
        }

        if (mLogDumperSecond == null) {
            mLogDumperSecond = new LogDumper(String.valueOf(mPId), logcatFileSecondPath);
        }

        mLogDumperFirst.start();
        mLogDumperSecond.start();
    }

    public void stopFirst() {
        if (mLogDumperFirst != null) {
            try {
                for (String error : VariableInstance.getInstance().errorLogNameList) {
                    Log.d(TAG, "stopSecond: error = " + error);
                }
            } catch (Exception e) {
            }
            mLogDumperFirst.stopLogs();
            mLogDumperFirst = null;
        }
    }

    public void stopSecond() {
        if (mLogDumperSecond != null) {
            try {
                for (String error : VariableInstance.getInstance().errorLogNameList) {
                    Log.d(TAG, "stopSecond: error = " + error);
                }
            } catch (Exception e) {
            }

            mLogDumperSecond.stopLogs();
            mLogDumperSecond = null;
        }
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

            cmds = "logcat  -s MainActivitylog | *:e | grep \"(" + mPID + ")\"";
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

    public String getFileName() {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH_mm");
        String date = format.format(new Date(System.currentTimeMillis()));
        return date;
    }
}