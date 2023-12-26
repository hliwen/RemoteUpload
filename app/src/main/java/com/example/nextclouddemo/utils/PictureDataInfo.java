package com.example.nextclouddemo.utils;

public class PictureDataInfo {
    public String dataString;
    public String nameString;
    public long pictureCreateData;
    public int yyMMdd;
    public String yearMonth;
    public String showName;
    public String hhmm;

    public PictureDataInfo(String logcalFileName) {
        try {
            dataString = logcalFileName.substring(0, logcalFileName.indexOf("-"));
        } catch (Exception e) {
            dataString = System.currentTimeMillis() + "";
        }
        try {
            nameString = logcalFileName.substring(logcalFileName.indexOf("-") + 1);
        } catch (Exception e) {
            nameString = logcalFileName;
        }
        pictureCreateData = Long.parseLong(dataString);
        yyMMdd = Utils.getyyMMddtringInt(pictureCreateData);
        yearMonth = Utils.getyyyyMMtring(pictureCreateData);
        showName = yyMMdd + "-" + nameString;
        hhmm = Utils.getHHmmString(pictureCreateData);
    }
}
