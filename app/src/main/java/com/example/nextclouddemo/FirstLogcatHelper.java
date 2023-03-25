package com.example.nextclouddemo;

import android.annotation.SuppressLint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FirstLogcatHelper {
    private static FirstLogcatHelper INSTANCE = null;

    public LogDumper mLogDumper = null;
    private int mPId;

    public String logcatFilePath;

    /**
     * 初始化目录
     */
    public void init() {

        File file = new File(VariableInstance.getInstance().LogcatDir);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    public static FirstLogcatHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FirstLogcatHelper();
        }
        return INSTANCE;
    }

    private FirstLogcatHelper() {
        init();
        mPId = android.os.Process.myPid();
    }

    public void start() {
        if (mLogDumper == null)
            mLogDumper = new LogDumper(String.valueOf(mPId), VariableInstance.getInstance().LogcatDir);
        mLogDumper.start();
    }

    public void stop() {
        if (mLogDumper != null) {
            mLogDumper.stopLogs();
            mLogDumper = null;
        }
    }

    private class LogDumper extends Thread {
        private Process logcatProc;
        private BufferedReader mReader = null;
        private boolean mRunning = true;
        String cmds = null;
        private String mPID;
        private FileOutputStream out = null;

        public LogDumper(String pid, String dir) {
            mPID = pid;
            try {
                File file = new File(dir, "logcat" + getFileName() + "AAA.txt");
                logcatFilePath = file.getAbsolutePath();
                out = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
            }
            cmds = "logcat  -s MainActivitylog | *:e | grep \"(" + mPID + ")\"";//打印所有日志信息

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