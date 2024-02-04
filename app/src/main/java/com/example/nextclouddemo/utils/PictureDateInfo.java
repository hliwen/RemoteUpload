package com.example.nextclouddemo.utils;

public class PictureDateInfo {
    public String dataString;
    public String nameString;
    public long pictureCreateData;
    public int yyMMdd;
    public String yyyyMM;
    public String showName;
    public String hhmm;

    public PictureDateInfo(String logcalFileName) {
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
        try {
            pictureCreateData = Long.parseLong(dataString);
        } catch (Exception e) {
            pictureCreateData = System.currentTimeMillis();
        }
        yyMMdd = Utils.getyyMMddtringInt(pictureCreateData);
        yyyyMM = Utils.getyyyyMMtring(pictureCreateData);
        showName = yyMMdd + "-" + nameString;
        hhmm = Utils.getHHmmString(pictureCreateData);
    }
}
