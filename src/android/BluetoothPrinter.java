package org.apache.cordova.sipkita;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Set;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

public class BluetoothPrinter extends CordovaPlugin {
	private static final String LOG_TAG = "BluetoothPrinter";
	BluetoothAdapter mBluetoothAdapter;
	BluetoothSocket mmSocket;
	BluetoothDevice mmDevice;
	OutputStream mmOutputStream;
	InputStream mmInputStream;
	Thread workerThread;
	byte[] readBuffer;
	int readBufferPosition;
	int counter;
	volatile boolean stopWorker;

	public BluetoothPrinter() {}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (action.equals("list")) {
			listBT(callbackContext);
			return true;
		} else if (action.equals("open")) {
			String name = args.getString(0);
			if (findBT(callbackContext, name)) {
				try {
					openBT(callbackContext);
				} catch (IOException e) {
					Log.e(LOG_TAG, e.getMessage());
					e.printStackTrace();
				}
			} else {
				callbackContext.error("Bluetooth Device Not Found: " + name);
			}
			return true;
		} else if (action.equals("print")) {
			try {
				String msg = args.getString(0);
				sendData(callbackContext, msg);
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;
		} else if (action.equals("close")) {
			try {
				closeBT(callbackContext);
			} catch (IOException e) {
				Log.e(LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
			return true;
		}
		return false;
	}

	void listBT(CallbackContext callbackContext) {
		BluetoothAdapter mBluetoothAdapter = null;
		String errMsg = null;
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				errMsg = "No bluetooth adapter available";
				Log.e(LOG_TAG, errMsg);
				callbackContext.error(errMsg);
				return;
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				JSONArray json = new JSONArray();
				for (BluetoothDevice device : pairedDevices) {
//					Hashtable map = new Hashtable();
//					map.put("type", device.getType());
//					map.put("address", device.getAddress());
//					map.put("name", device.getName());
//					JSONObject jObj = new JSONObject(map);
					json.put(device.getName());
				}
				callbackContext.success(json);
			} else {
				callbackContext.error("No Bluetooth Device Found");
			}
//			Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
		} catch (Exception e) {
			errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
	}

	// This will find a bluetooth printer device
	boolean findBT(CallbackContext callbackContext, String name) {
		try {
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (mBluetoothAdapter == null) {
				Log.e(LOG_TAG, "No bluetooth adapter available");
			}
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.cordova.getActivity().startActivityForResult(enableBluetooth, 0);
			}
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice device : pairedDevices) {
					// MP300 is the name of the bluetooth printer device
					if (device.getName().equalsIgnoreCase(name)) {
						mmDevice = device;
						return true;
					}
				}
			}
			Log.d(LOG_TAG, "Bluetooth Device Found: " + mmDevice.getName());
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	// Tries to open a connection to the bluetooth printer device
	boolean openBT(CallbackContext callbackContext) throws IOException {
		try {
			// Standard SerialPortService ID
			UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
			mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
			mmSocket.connect();
			mmOutputStream = mmSocket.getOutputStream();
			mmInputStream = mmSocket.getInputStream();
			beginListenForData();
//			Log.d(LOG_TAG, "Bluetooth Opened: " + mmDevice.getName());
			callbackContext.success("Bluetooth Opened: " + mmDevice.getName());
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	// After opening a connection to bluetooth printer device, 
	// we have to listen and check if a data were sent to be printed.
	void beginListenForData() {
		try {
			final Handler handler = new Handler();
			// This is the ASCII code for a newline character
			final byte delimiter = 10;
			stopWorker = false;
			readBufferPosition = 0;
			readBuffer = new byte[1024];
			workerThread = new Thread(new Runnable() {
				public void run() {
					while (!Thread.currentThread().isInterrupted() && !stopWorker) {
						try {
							int bytesAvailable = mmInputStream.available();
							if (bytesAvailable > 0) {
								byte[] packetBytes = new byte[bytesAvailable];
								mmInputStream.read(packetBytes);
								for (int i = 0; i < bytesAvailable; i++) {
									byte b = packetBytes[i];
									if (b == delimiter) {
										byte[] encodedBytes = new byte[readBufferPosition];
										System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
//										final String data = new String(encodedBytes, "US-ASCII");
//										readBufferPosition = 0;
//										handler.post(new Runnable() {
//											public void run() {
//												myLabel.setText(data);
//											}
//										});
									} else {
										readBuffer[readBufferPosition++] = b;
									}
								}
							}
						} catch (IOException ex) {
							stopWorker = true;
						}
					}
				}
			});
			workerThread.start();
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * This will send data to be printed by the bluetooth printer
	 */
	boolean sendData(CallbackContext callbackContext, String msg) throws IOException {
		try {
			// the text typed by the user
			msg += "\n";
			mmOutputStream.write(msg.getBytes());
			// tell the user data were sent
//			Log.d(LOG_TAG, "Data Sent");
			callbackContext.success("Data Sent");
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}

	// Close the connection to bluetooth printer.
	boolean closeBT(CallbackContext callbackContext) throws IOException {
		try {
			stopWorker = true;
			mmOutputStream.close();
			mmInputStream.close();
			mmSocket.close();
//			myLabel.setText("Bluetooth Closed");
			callbackContext.success("Bluetooth Closed");
			return true;
		} catch (Exception e) {
			String errMsg = e.getMessage();
			Log.e(LOG_TAG, errMsg);
			e.printStackTrace();
			callbackContext.error(errMsg);
		}
		return false;
	}
}
