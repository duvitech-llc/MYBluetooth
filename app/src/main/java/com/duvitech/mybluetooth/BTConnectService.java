package com.duvitech.mybluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BTConnectService extends Service {
    private static final String LOG_TAG = "BTConnectService";
    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private Set<BluetoothDevice> pairedDevices = null;
    private static boolean bConnected = false;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;
    private InputStream inStream = null;

    final byte delimiter = 13; //This is the ASCII code for a newline character

    byte[] readBuffer;
    int readBufferPosition;
    int counter;

    // Well known SPP UUID
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static String bt_address = "00:00:00:00:00:00";
    private static String my_address = "00:00:00:00:00:00";

    private static boolean mDiscoverReceiverEnabled = false;
    private final BroadcastReceiver mDiscoveredDeviceReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView



                if(device.getName().compareTo("OBDII") == 0 )
                {
                    mBluetoothAdapter.cancelDiscovery();
                    Log.i(LOG_TAG,"Found ODBII Device!!!");
                    // save address
                    bt_address = device.getAddress();

                    BluetoothDevice btd = mBluetoothAdapter.getRemoteDevice(bt_address);

                    Log.i(LOG_TAG,"ODBII Device Address: " + bt_address);
                    // connect and remove this receiver
                    try {
                        btSocket = btd.createInsecureRfcommSocketToServiceRecord(MY_UUID);

                    } catch (IOException e) {
                        Log.e("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
                    }

                    unregisterReceiver(mDiscoveredDeviceReceiver);
                    mDiscoverReceiverEnabled = false;
                    Log.d(LOG_TAG, "My Address: " + my_address);
                    Log.d(LOG_TAG, "...Connecting to Remote...");
                    try {
                        btSocket.connect();
                        outStream = btSocket.getOutputStream();
                        inStream = btSocket.getInputStream();
                        bConnected = true;
                        Log.d(LOG_TAG, "...Connection established and data link opened...");
                    } catch (IOException e) {
                        try {
                            btSocket.close();
                            bConnected = false;
                        } catch (IOException e2) {
                            Log.e("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
                        }
                    }

                    if(bConnected) {
                        sendData("ATZ\r");

                        try {
                            int bytesAvailable = inStream.available();
                            if(bytesAvailable > 0 )
                            {
                                //we have data
                                byte[] packetBytes = new byte[bytesAvailable];
                                inStream.read(packetBytes);
                                for(int i=0;i<bytesAvailable;i++)
                                {
                                    byte b = packetBytes[i];
                                    if(b == delimiter) {
                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                        final String data = new String(encodedBytes, "US-ASCII");
                                        readBufferPosition = 0;
                                        Log.d(LOG_TAG,"ODBII Resp: " + data);
                                    }
                                    else
                                    {
                                        readBuffer[readBufferPosition++] = b;
                                    }

                                }

                                if(readBufferPosition>0) {
                                    byte[] encodedBytes = new byte[readBufferPosition];

                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;
                                    Log.d(LOG_TAG, "ODBII Resp: " + data);
                                }
                            }
                        }
                        catch(IOException ex)
                        {
                            Log.d("FATAL ERROR", ex.getMessage());
                        }
                    }
                }
            }
        }
    };


    private static boolean mReceiverEnabled = false;
    private BroadcastReceiver mReceiver  = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.i(LOG_TAG,"Bluetooth off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.i(LOG_TAG,"Turning Bluetooth off...");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.i(LOG_TAG,"Bluetooth on");

                        // get paired devices
                        pairedDevices = mBluetoothAdapter.getBondedDevices();
                        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                        registerReceiver(mDiscoveredDeviceReceiver, filter); // Don't forget to unregister during onDestroy
                        mDiscoverReceiverEnabled = true;
                        mBluetoothAdapter.startDiscovery();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.i(LOG_TAG,"Turning Bluetooth on...");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.i(LOG_TAG, "Bluetooth connecting...");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.i(LOG_TAG, "Bluetooth connected...");
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTING:
                        Log.i(LOG_TAG, "Bluetooth disconnecting...");
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTED:
                        Log.i(LOG_TAG, "Bluetooth disconnected...");
                        break;
                }
            }
        }
    };


    private void sendData(String message) {
        byte[] msgBuffer = message.getBytes();

        Log.d(LOG_TAG, "...Sending data: " + message + "...");

        try {
            outStream.write(msgBuffer);
            outStream.flush();

            Thread.sleep(100);
        } catch (IOException e) {
            String msg = "In onResume() and an exception occurred during write: " + e.getMessage();
            if (bt_address.equals("00:00:00:00:00:00"))
                msg = msg + ".\n\nThe bt_address is 00:00:00:00:00:00 which is incorrect";
            msg = msg +  ".\n\nCheck that the SPP UUID: " + MY_UUID.toString() + " exists on server.\n\n";

            Log.e("Fatal Error", msg);
        }catch (InterruptedException iex)
        {
            Log.d(LOG_TAG, iex.getMessage());
        }
    }

    public BTConnectService() {
        readBufferPosition = 0;
        readBuffer = new byte[1024];
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.i(LOG_TAG, "BT Service Started");

        if(mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            // get paired devices
            pairedDevices = mBluetoothAdapter.getBondedDevices();

            // try paired devices first

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mDiscoveredDeviceReceiver, filter); // Don't forget to unregister during onDestroy
            mDiscoverReceiverEnabled = true;
            mBluetoothAdapter.startDiscovery();
        }

        if(mBluetoothAdapter != null)
        {
            my_address = mBluetoothAdapter.getAddress();

            // setup broadcast receiver
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mReceiver, filter);
            mReceiverEnabled = true;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        if(bConnected)
        {
            if (outStream != null) {
                try {
                    outStream.flush();
                } catch (IOException e) {
                    Log.e("Fatal Error", "In onPause() and failed to flush output stream: " + e.getMessage() + ".");
                }
            }

            if(btSocket != null) {
                try {
                    btSocket.close();
                } catch (IOException e2) {
                    Log.e("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
                }
            }
        }

        if(mReceiverEnabled)
            unregisterReceiver(mReceiver);
        if(mDiscoverReceiverEnabled)
            unregisterReceiver(mDiscoveredDeviceReceiver);
        Log.i(LOG_TAG, "BT Service Stopped");
    }
}
