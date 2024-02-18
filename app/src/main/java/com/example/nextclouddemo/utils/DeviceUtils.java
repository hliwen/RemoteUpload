package com.example.nextclouddemo.utils;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.example.gpiotest.LedControl;
import com.example.nextclouddemo.MainActivity;
import com.example.nextclouddemo.VariableInstance;
import com.example.nextclouddemo.operation.FormatLisener;

import java.io.DataOutputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class DeviceUtils {
	private static final String TAG = "remotelog_USBDeviceUtils";

	public static String getFileExtension(String filePath) {
		if (filePath == null) {
			return "";
		}

		int lastDotIndex = filePath.lastIndexOf(".");
		if (lastDotIndex != -1 && lastDotIndex < filePath.length() - 1) {
			return filePath.substring(lastDotIndex + 1).toLowerCase();
		} else {
			return "";
		}
	}

	public static boolean fileIsPicture(String fileName) {
		String FileEnd = getFileExtension(fileName);
		if ((FileEnd.equals("nif") || FileEnd.equals("raw") || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("crw") || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2") || FileEnd.equals("cr3")) || (FileEnd.equals("jpg"))) {
			return true;
		}
		return false;
	}

	public static boolean fileIsRow(String fileName) {
		String FileEnd = getFileExtension(fileName);
		if ((FileEnd.equals("nif") || FileEnd.equals("raw") || FileEnd.equals("arw") || FileEnd.equals("nef") || FileEnd.equals("raf") || FileEnd.equals("crw") || FileEnd.equals("pef") || FileEnd.equals("rw2") || FileEnd.equals("dng") || FileEnd.equals("cr2") || FileEnd.equals("cr3"))) {
			return true;
		}
		return false;
	}

	public static boolean fileIsJPG(String fileName) {
		String FileEnd = getFileExtension(fileName);
		if ((FileEnd.equals("jpg")))
			return true;
		return false;
	}

	public static void formatCamera(Context context, FormatLisener formatLisener) {
		UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {

				}
				List<String> usbBlocks = Utils.getDeviceBlockList();
				for (String block : usbBlocks) {
					Log.e(TAG, "cameraFormat 没打开相机前 block =" + block);
				}
				LedControl.writeGpio('b', 2, 1);//打开相机端口

				try {
					Thread.sleep(15000);
				} catch (InterruptedException e) {
					Log.e(TAG, "cameraFormat11 sleep InterruptedException:" + e);
				}
				int tpye = getCameraType(context);//1:isMtpModel 2:isStorageModel

				Log.e(TAG, "cameraFormat: getCameraType =" + tpye);
				if (tpye == 1) {

					UsbDevice mtpDevice = getMTPCameraDevice(context, usbManager);
					if (mtpDevice == null) {
						if (formatLisener != null) {
							formatLisener.formatEnd(false);
						}
					} else {
						formatLisener.formatStart();
						formatMtpCameraDevice(usbManager, mtpDevice);
					}
				} else if (tpye == 2) {
					formatLisener.formatStart();
					formatStoreUSBCameraDevice(formatLisener, usbBlocks);
				} else {
					if (formatLisener != null) {
						formatLisener.formatEnd(false);
					}
					Log.e(TAG, "cameraFormat: 未知设备类型");
				}
			}
		}).start();
	}

	public static void formatStoregeUSB(FormatLisener formatLisener) {

		new Thread(new Runnable() {
			@Override
			public void run() {
				Utils.setenforce();
				boolean formatSucced = false;
				try {
					List<String> devBlock = Utils.getDeviceBlockList();
					if (devBlock.size() < 1) {
						try {
							Thread.sleep(3000);
						} catch (Exception e) {

						}
						devBlock = Utils.getDeviceBlockList();

						if (devBlock.size() < 1) {
							try {
								Thread.sleep(3000);
							} catch (Exception e) {

							}
							devBlock = Utils.getDeviceBlockList();
						}
					}
					formatLisener.formatStart();
					Log.d(TAG, "run: formatStoregeUSB sd devBlock.size  =" + devBlock.size());
					for (String block : devBlock) {
						Log.e(TAG, "formatStoregeUSB: sd block =" + block);
						DataOutputStream dataOutputStream = null;
						try {
							Process formatProcess = Runtime.getRuntime().exec("su");
							dataOutputStream = new DataOutputStream(formatProcess.getOutputStream());
							String runCommand = "busybox mkdosfs -F 32 /dev/block/" + block;
							dataOutputStream.write(runCommand.getBytes(Charset.forName("utf-8")));
							dataOutputStream.flush();

							formatSucced = true;
						} catch (Exception e) {
							Log.d(TAG, "run: formatStoregeUSB  busybox mkdosfs -F 32 Exception :" + e);
						} finally {
							try {
								if (dataOutputStream != null) {
									dataOutputStream.close();
								}
							} catch (Exception e) {
								Log.d(TAG, "run: formatStoregeUSB dataOutputStream.close Exception :" + e);
							}
						}
					}
				} catch (Exception e) {
					Log.d(TAG, "run: formatStoregeUSB Exception :" + e);
				}

				if (formatLisener != null) {
					formatLisener.formatEnd(formatSucced);
				}
			}
		}).start();
	}

	public static boolean formatMtpCameraDevice(UsbManager usbManager, UsbDevice usbDevice) {
		boolean formatSucceed = false;
		try {

			UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(usbDevice);
			if (usbDeviceConnection == null) {
				Log.e(TAG, "formatMtpCameraDevice: usbDeviceConnection == null");

				return formatSucceed;
			}
			MtpDevice mtpDevice = new MtpDevice(usbDevice);

			if (mtpDevice == null) {
				Log.e(TAG, "formatMtpCameraDevice new MtpDevice(usbDevice) == null ");
				return formatSucceed;
			}
			if (!mtpDevice.open(usbDeviceConnection)) {
				Log.e(TAG, "formatMtpCameraDevice mtpDevice.open(usbDeviceConnection)失败");
				return formatSucceed;
			}

			int[] storageIds = mtpDevice.getStorageIds();
			if (storageIds == null || storageIds.length == 0) {
				Log.e(TAG, "formatMtpCameraDevice: 数码相机存储卷不可用 storageIds == null 结束扫描");
				return formatSucceed;
			}
			Log.e(TAG, "formatMtpCameraDevice: 设备一共几个盘符，storageIds.length =" + storageIds.length);

			for (int storageId : storageIds) {
				int[] objectHandles = mtpDevice.getObjectHandles(storageId, 0, 0);
				formatSucceed = true;
				if (objectHandles != null) {
					Log.e(TAG, "formatMtpCameraDevice: 获取当前盘符全部照片数组  pictureHandlesItem=" + objectHandles.length);
					for (int handle : objectHandles) {
						MtpObjectInfo mtpObjectInfo = mtpDevice.getObjectInfo(handle);

						if (mtpObjectInfo == null) {
							Log.e(TAG, "mtpDeviceScaner:  mtpObjectInfo ==null 当前文件信息无法获取");
							continue;
						}
						String pictureName = mtpObjectInfo.getName();
						if (!fileIsPicture(pictureName)) {
							continue;
						}

						Log.e(TAG, "formatMtpCameraDevice: 删除相机照片：" + pictureName);
						boolean delectResult = mtpDevice.deleteObject(handle);
						if (!delectResult) {
							Log.e(TAG, "formatMtpCameraDevice: 格式化过程中，删除照片失败: " + pictureName);
						}
					}
				}
			}
			usbDeviceConnection.close();
			mtpDevice.close();
		} catch (Exception e) {
			Log.e(TAG, "formatMtpCameraDevice:  Exception: " + e);
		}
		return formatSucceed;
	}

	private static void formatStoreUSBCameraDevice(FormatLisener cameraFormatListener, List<String> usbBlocks) {
		boolean formatSucceed = false;
		List<String> cameraBlocks = Utils.getDeviceBlockList();
		for (String block : cameraBlocks) {
			if (usbBlocks == null || usbBlocks.contains(block)) {
				Log.e(TAG, "formatStoreUSBCameraDevice usbBlocks == null || usbBlocks.contains(block)");
				continue;
			}
			Log.e(TAG, "formatStoreUSBCameraDevice  格式化 block =" + block);
			DataOutputStream dataOutputStream = null;
			try {
				Process formatProcess = Runtime.getRuntime().exec("su");
				dataOutputStream = new DataOutputStream(formatProcess.getOutputStream());
				String runCommand = "busybox mkdosfs -F 32 /dev/block/" + block;
				dataOutputStream.write(runCommand.getBytes(Charset.forName("utf-8")));
				dataOutputStream.flush();
				formatSucceed = true;
				Log.d(TAG, "run: formatStoreUSBCameraDevice  busybox mkdosfs -F 32 block :" + block + "，完成");
			} catch (Exception e) {
				Log.d(TAG, "run: formatStoreUSBCameraDevice  busybox mkdosfs -F 32 Exception :" + e);
			} finally {
				try {
					if (dataOutputStream != null) {
						dataOutputStream.close();
					}
				} catch (Exception e) {
					Log.d(TAG, "run: formatStoreUSBCameraDevice dataOutputStream.close Exception :" + e);
				}
			}
		}
		if (cameraFormatListener != null) {
			cameraFormatListener.formatEnd(formatSucceed);
		}
	}

	public static int getCameraType(Context context) {//1:isMtpModel 2:isStorageModel
		UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		if (usbManager == null) {
			Log.d(TAG, "getCameraType:usbManager==null ");
			return 0;
		}
		HashMap<String, UsbDevice> connectedUSBDeviceList = usbManager.getDeviceList();
		if (connectedUSBDeviceList == null || connectedUSBDeviceList.size() <= 0) {
			Log.e(TAG, "getCameraType:  没有检测到有设备列表");
			return 0;
		}
		Collection<UsbDevice> usbDevices = connectedUSBDeviceList.values();
		if (usbDevices == null) {
			Log.e(TAG, "getCameraType:  没有检测到有设备接入");
			return 0;
		}
		for (UsbDevice usbDevice : usbDevices) {
			if (usbDevice == null) {
				continue;
			}

			String usbProductName = usbDevice.getProductName();

			Log.e(TAG, "getCameraType: 当前设备名称：" + usbProductName);

			if (usbProductName == null) {
				continue;
			}

			usbProductName = usbProductName.trim();

			if (isOthreDevice(usbProductName)) {
				continue;
			}

			if (isStroreUSBDevice(usbProductName)) {
				continue;
			}

			try {
				for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
					UsbInterface usbInterface = usbDevice.getInterface(i);
					if (usbInterface == null) {
						continue;
					}

					switch (usbInterface.getInterfaceClass()) {
						case UsbConstants.USB_CLASS_STILL_IMAGE:
							return 1;
						case UsbConstants.USB_CLASS_MASS_STORAGE:
							return 2;
						default:
							break;
					}
				}
			} catch (Exception e) {
			}
		}
		return 0;
	}

	public static UsbDevice getMTPCameraDevice(Context context, UsbManager usbManager) {
		UsbDevice mtpDevice = null;
		HashMap<String, UsbDevice> connectedUSBDeviceList = usbManager.getDeviceList();
		if (connectedUSBDeviceList == null || connectedUSBDeviceList.size() <= 0) {
			Log.e(TAG, "getMTPCameraDevice:  没有检测到有设备列表");
			return mtpDevice;
		}
		Collection<UsbDevice> usbDevices = connectedUSBDeviceList.values();
		if (usbDevices == null) {
			Log.e(TAG, "getMTPCameraDevice:  没有检测到有设备接入");
			return mtpDevice;
		}
		for (UsbDevice usbDevice : usbDevices) {
			if (usbDevice == null) {
				continue;
			}
			String usbProductName = usbDevice.getProductName();
			Log.e(TAG, "getCameraType: 当前设备名称：" + usbProductName);
			if (usbProductName == null) {
				continue;
			}

			if (!isCameraDevice(usbProductName)) {
				continue;
			}


			try {
				for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
					UsbInterface usbInterface = usbDevice.getInterface(i);
					if (usbInterface == null) {
						continue;
					}

					if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_STILL_IMAGE) {
						mtpDevice = usbDevice;
						break;
					}
				}
			} catch (Exception e) {
			}

			if (mtpDevice != null) {
				break;
			}
		}

		if (mtpDevice == null) {
			return mtpDevice;
		}

		int requestPermissionCount = 0;

		while (!usbManager.hasPermission(mtpDevice) && requestPermissionCount < 5) {
			requestPermissionCount++;
			try {
				Thread.sleep(1500);
				@SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(VariableInstance.GET_DEVICE_PERMISSION), 0);
				usbManager.requestPermission(mtpDevice, pendingIntent);
				Thread.sleep(1500);
			} catch (InterruptedException e) {
			}
		}

		if (usbManager.hasPermission(mtpDevice)) {
			return mtpDevice;
		} else {
			Log.e(TAG, "getMTPCameraDevice: 授权失败");
			return null;
		}
	}


	public static boolean isStroreUSBDevice(String deviceName) {
		if (deviceName == null) {
			return false;
		}
		return deviceName.contains("USB Storage");
	}

	public static boolean isOthreDevice(String deviceName) {
		if (deviceName == null) {
			return true;
		}
		if (deviceName.contains("802.11n NIC") || deviceName.contains("USB Optical Mouse") || deviceName.contains("USB Charger") || deviceName.contains("Usb Mouse") || deviceName.startsWith("EC25") || deviceName.startsWith("EG25") || deviceName.startsWith("EC20") || deviceName.startsWith("EC200T")) {
			return true;
		}
		return false;
	}

	public static boolean isCameraDevice(String deviceName) {
		if (deviceName == null) {
			return false;
		}
		deviceName = deviceName.trim();

		if (isStroreUSBDevice(deviceName) || isOthreDevice(deviceName)) {
			return false;
		}
		return true;
	}


	private static String phoneImei;

	@SuppressLint("HardwareIds")
	public static String getPhoneImei(Context context) {
		if (phoneImei != null) {
			return phoneImei;
		}

		try {
			TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			phoneImei = telephonyManager.getDeviceId();
		} catch (Exception | Error e) {
			Log.e(TAG, "getPhoneImei: Exception =" + e);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			phoneImei = "202302050000001";
		}
		Log.e(TAG, "getPhoneImei: phoneImei =" + phoneImei);
		return phoneImei;
	}


	public static boolean checkFormatFlag(Context context, FormatLisener formatLisener) {
		int type = getFormatFlag(context);//0: 不需要格式化，1：格式化U盘 2：格式化相机
		Log.e(TAG, "checkFormatFlag: 0: 不需要格式化，1：格式化U盘 2：格式化相机  type =" + type);
		if (type == 1) {
			saveFormatFlag(0, context);
			saveShowFormatResultFlag(true, context);
			formatLisener.formatStart();
			formatStoregeUSB(formatLisener);
			return true;
		} else if (type == 2) {
			saveFormatFlag(0, context);
			saveShowFormatResultFlag(true, context);
			formatLisener.formatStart();
			formatCamera(context, formatLisener);
			return true;
		}
		return false;
	}


	public static void saveShowFormatResultFlag(boolean format, Context context) {
		SharedPreferences.Editor editor = context.getSharedPreferences("Cloud", MODE_PRIVATE).edit();
		editor.putBoolean("showFormatResult", format);
		editor.apply();
	}

	public static boolean getShowFormatResultFlag(Context context) {
		SharedPreferences sharedPreferences = context.getSharedPreferences("Cloud", MODE_PRIVATE);
		return sharedPreferences.getBoolean("showFormatResult", false);
	}

	@SuppressLint("HardwareIds")
	public static String getPhoneNumber2(Context context) {
		try {
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
				SubscriptionManager subsManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
				List<SubscriptionInfo> subsList = subsManager.getActiveSubscriptionInfoList();
				if (subsList != null) {
					for (SubscriptionInfo subsInfo : subsList) {
						if (subsInfo != null) {
							return subsInfo.getIccId();
						}
					}
				}
			}
		} catch (Exception | Error e) {
		}
		return null;
	}

	public static int getFormatResultFlag(Context context) {//0: 格式化U盘成功 1：格式化U盘失败  2：格式化相机成功 3：格式化相机失败
		SharedPreferences sharedPreferences = context.getSharedPreferences("Cloud", MODE_PRIVATE);
		return sharedPreferences.getInt("formatResult", 0);
	}

	public static void saveFormatResultFlag(int format, Context context) {//0: 格式化U盘失败，1：格式化U盘成功 2：格式化相机失败 3：格式化相机失败
		SharedPreferences.Editor editor = context.getSharedPreferences("Cloud", MODE_PRIVATE).edit();
		editor.putInt("formatResult", format);
		editor.apply();
	}


	public static void saveShowFormatResultFlag(Context context, boolean format) {
		SharedPreferences.Editor editor = context.getSharedPreferences("Cloud", MODE_PRIVATE).edit();
		editor.putBoolean("showFormatResult", format);
		editor.apply();
	}

	public static void saveFormatFlag(int type, Context context) {//0: 不需要格式化，1：格式化U盘 2：格式化相机
		SharedPreferences.Editor editor = context.getSharedPreferences("Cloud", MODE_PRIVATE).edit();
		editor.putInt("formatflag", type);
		editor.apply();
	}

	public static int getFormatFlag(Context context) {//0: 不需要格式化，1：格式化U盘 2：格式化相机
		SharedPreferences sharedPreferences = context.getSharedPreferences("Cloud", MODE_PRIVATE);
		return sharedPreferences.getInt("formatflag", 0);
	}


	/**
	 * 有密码连接
	 * @param ssid
	 * @param pws
	 */
	public static void connectWifiPws(String ssid, String pws, WifiManager wifiManager) {
		wifiManager.disableNetwork(wifiManager.getConnectionInfo().getNetworkId());
		int netId = wifiManager.addNetwork(getWifiConfig(ssid, pws, true, wifiManager));
		boolean enableNetwork = wifiManager.enableNetwork(netId, true);
		Log.d(TAG, "connectWifiPws: enableNetwork =" + enableNetwork);
	}

	/**
	 * 无密码连接
	 * @param ssid
	 */
	public static void connectWifiNoPws(String ssid, WifiManager wifiManager) {
		wifiManager.disableNetwork(wifiManager.getConnectionInfo().getNetworkId());
		int netId = wifiManager.addNetwork(getWifiConfig(ssid, "", false, wifiManager));
		wifiManager.enableNetwork(netId, true);
	}

	/**
	 * wifi设置
	 * @param ssid
	 * @param pws
	 * @param isHasPws
	 */
	private static WifiConfiguration getWifiConfig(String ssid, String pws, boolean isHasPws, WifiManager wifiManager) {

		WifiConfiguration config = new WifiConfiguration();
		config.allowedAuthAlgorithms.clear();
		config.allowedGroupCiphers.clear();
		config.allowedKeyManagement.clear();
		config.allowedPairwiseCiphers.clear();
		config.allowedProtocols.clear();
		config.SSID = "\"" + ssid + "\"";

		WifiConfiguration tempConfig = isExist(ssid, wifiManager);
		if (tempConfig != null) {
			wifiManager.removeNetwork(tempConfig.networkId);
		}
		if (isHasPws) {
			config.preSharedKey = "\"" + pws + "\"";
			config.hiddenSSID = true;
			config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
			config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
			config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
			config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
			config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
			config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
			config.status = WifiConfiguration.Status.ENABLED;
		} else {
			config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
		}
		return config;
	}

	/**
	 * 得到配置好的网络连接
	 * @param ssid
	 * @return
	 */
	private static WifiConfiguration isExist(String ssid, WifiManager wifiManager) {
		@SuppressLint("MissingPermission") List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
		for (WifiConfiguration config : configs) {

			if (config.SSID.equals("\"" + ssid + "\"")) {

				return config;
			}
		}
		return null;
	}

	public static String getSignalStrength(int signalStrengthValue) {
		if (signalStrengthValue > 0) {
			return "0";
		} else if (signalStrengthValue > -55) {
			return "4";
		} else if (signalStrengthValue > -70) {
			return "3";
		} else if (signalStrengthValue > -85) {
			return "2";
		} else if (signalStrengthValue > -100) {
			return "1";
		} else {
			return "1";
		}
	}


	private static String phoneNumber;

	@SuppressLint("HardwareIds")
	public static String getPhoneNumber(Context context) {
		if (phoneNumber != null && !phoneNumber.equals("0")) {
			return phoneNumber;
		}
		try {
			TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			phoneNumber = telephonyManager.getLine1Number();
			Log.e(TAG, "getPhoneNumber:  getLine1Number number =" + phoneNumber);

			if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
				phoneNumber = telephonyManager.getSimSerialNumber();
				Log.e(TAG, "getPhoneNumber:  getSimSerialNumber number =" + phoneNumber);
			}

			if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
				phoneNumber = DeviceUtils.getPhoneNumber2(context);
				Log.e(TAG, "getPhoneNumber:  getIccId number =" + phoneNumber);
			}

			if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
				phoneNumber = "0";
			}
			Log.d(TAG, "getPhoneNumber: 卡号 =" + phoneNumber);
			return phoneNumber;
		} catch (Exception | Error e) {
			Log.e(TAG, "getPhoneNumber: Exception =" + e);
			return "0";
		}
	}


	public static void restartDevice() {
		try {
			Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
			proc.waitFor();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static boolean getDebugFlag(Context context) {
		SharedPreferences sharedPreferences = context.getSharedPreferences("Cloud", MODE_PRIVATE);
		return sharedPreferences.getBoolean("debug", false);
	}

	public static void saveDebugFlag(Context context, boolean debug) {
		SharedPreferences.Editor editor = context.getSharedPreferences("Cloud", MODE_PRIVATE).edit();
		editor.putBoolean("debug", debug);
		editor.apply();
	}
}
