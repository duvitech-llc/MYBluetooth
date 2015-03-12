package com.duvitech.mybluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.UUID;

public class BTConnectService extends Service {

    private DongleListener _listener = null;

    // class used for the client binder
    public class BTConnectBinder extends Binder {
        BTConnectService getService(){
            //return this instance of Myservice to clients so that they can call public methods
            return BTConnectService.this;
        }
    }

    private static final String LOG_TAG = "BTConnectService";
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private Set<BluetoothDevice> pairedDevices = null;
    private static boolean bConnected = false;
    private BluetoothSocket btSocket = null;
    private DataOutputStream  outStream = null;
    private InputStream inStream = null;

    private final IBinder mBinder = new BTConnectBinder();

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
                    if(_listener != null)
                        _listener.foundDongle(bt_address);

                    BluetoothDevice btd = mBluetoothAdapter.getRemoteDevice(bt_address);

                    Log.i(LOG_TAG,"ODBII Device Address: " + bt_address);
                    // connect and remove this receiver
                    try {
                        btSocket = btd.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                        //btSocket = btd.createRfcommSocketToServiceRecord(MY_UUID);

                    } catch (IOException e) {
                        Log.e("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
                    }

                    unregisterReceiver(mDiscoveredDeviceReceiver);
                    mDiscoverReceiverEnabled = false;
                    Log.d(LOG_TAG, "My Address: " + my_address);
                    Log.d(LOG_TAG, "...Connecting to Remote...");
                    try {
                        outStream = new DataOutputStream(btSocket.getOutputStream());
                        inStream = btSocket.getInputStream();

                        btSocket.connect();
                        String resp = "";
                        //resp = sendData("", "");
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

                        initDongle();
/*
                        resp = sendData("0105\r", "41");
                        if(resp.contains("41 05")) {
                            String[] temp = resp.split("\r");
                            for(int x=0; x< temp.length; x++) {
                                if (temp[x].contains("41")) {
                                    Log.d(LOG_TAG, temp[x]);
                                    break;
                                }
                            }
                        }
                        else
                            Log.d(LOG_TAG, "Resp: " + resp);

*/
                    }
                }
            }
        }
    };

    private void initDongle(){
        try{
            String resp = "";

            resp = sendData("AT Z\r", "ELM327");
            if(resp.contains("ELM327"))
                Log.d(LOG_TAG, "Reset Device");
            else
                Log.d(LOG_TAG, "Resp: " + resp);

        }catch (Exception ex){
            Log.e(LOG_TAG, ex.getMessage());
        }

    }

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


    private static String ReadStream(InputStream s){
        String response = "No Data";
        byte[] sBuffer = new byte[1024];
        Arrays.fill(sBuffer, (byte) 0);
        int pos = 0;

        // start input stream listener
        try{
            Thread.sleep(250);
            int ch = 0x3E;
            do{
                ch = s.read();
                sBuffer[pos] = (byte)ch;
                pos++;
            }while(ch != 0x3E);


            final String data = new String(sBuffer, "US-ASCII");
            return data;

        }catch (IOException ioe){
            Log.w(LOG_TAG,ioe.getMessage());
        } catch (InterruptedException e) {
            Log.w(LOG_TAG,e.getMessage());
        }

        return response;
    }
    private String sendData(String message, String expected){
        return sendData(message, expected, false);

    }

    private String sendData(String message, String expected, boolean bTrim) {

        try {
            if(!message.isEmpty()) {
                Log.d(LOG_TAG, "Sending CMD: " + message);
                byte[] msgBuffer = message.getBytes();
                outStream.write(msgBuffer);
                outStream.flush();

                Thread.sleep(250);
            }

            ArrayList sBuffer = new ArrayList();

            int ch;
            do {
                ch = inStream.read();

                if (ch == 0x3E) {
                    // check if buffer has response
                    String temp = "";
                    for(int x=0; x< sBuffer.size(); x++)
                        temp += ((char) sBuffer.get(x));

                    Log.i(LOG_TAG,"DATA: " + temp);

                    if(expected.isEmpty())
                        return temp;
                    if(temp.contains(expected)) {
                        if(bTrim)
                        {
                            String[] ts = temp.split("\r");
                            for(int z=0; z<ts.length; z++)
                                if(ts[z].contains(expected))
                                    return ts[z];
                        }
                        else
                            return temp;
                    }
                    else {

                        sBuffer.add((char)ch);
                        ch = -1;
                    }


                }
                else{
                    sBuffer.add((char)ch);
                }

            }while(ch != 0x3E);


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

        return "";
    }

    public BTConnectService() {
        readBufferPosition = 0;
        readBuffer = new byte[1024];
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
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

    public String getBTDongleAddress()
    {
        return bt_address;
    }

    public String sendReceive(String cmd, String exp)
    {
        return sendData(cmd,exp,true);
    }

    public boolean connectDongle(){
        if(bt_address.compareTo("00:00:00:00:00:00") == 0)
        {
            //do discovery
        }
        else
        {
            try {
                if (btSocket.isConnected())
                    btSocket.close();


                BluetoothDevice btd = mBluetoothAdapter.getRemoteDevice(bt_address);
                btSocket = btd.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                btSocket.connect();
                outStream = new DataOutputStream(btSocket.getOutputStream());
                inStream = btSocket.getInputStream();
                initDongle();

            }catch (IOException ioe){
                Log.e(LOG_TAG,ioe.getMessage());
            }

        }

        return false;
    }

    public void addListener(DongleListener d){
        _listener = d;
    }

    public void clearListener(){
        _listener = null;
    }

    public void flushStream(){
        try {
            outStream.flush();
        }catch (IOException ioe){
            Log.e(LOG_TAG,ioe.getMessage());
        }
    }
}
