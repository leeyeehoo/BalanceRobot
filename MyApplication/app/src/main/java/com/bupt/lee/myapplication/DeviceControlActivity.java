/*
 * Copyright (C) 2015 Iasc CHEN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bupt.lee.myapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;

import com.bupt.lee.myapplication.ble.BluetoothLeService;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {

    private SensorManager sensorMgr;//
    Sensor sensor;//
    TextView textX = null;
    TextView textY = null;
    TextView textZ = null;

    private float x, y, z;//
    private int recentdata=0;
    private int recentturn=0;


    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yy,MM,dd,HH,mm,ss");

    public static int BLE_MSG_SEND_INTERVAL = 100;
    public static int BLE_MSG_BUFFER_LEN = 18;

    private Button sendButton;
    private EditText msgEdit;
    private TextView isSerial, mConnectionState, returnText;
    private ImageView infoButton;

    private String mDeviceName, mDeviceAddress;

    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic characteristicTX, characteristicRX;
    private boolean mConnected = false, characteristicReady = false;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                BluetoothGattService gattService = mBluetoothLeService.getSoftSerialService();
                if (gattService == null) {
                    Toast.makeText(DeviceControlActivity.this, getString(R.string.without_service), Toast.LENGTH_SHORT).show();
                    return;
                }

                if(mDeviceName.startsWith("Microduino")) {
                    characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_MD_RX_TX);
                }else if(mDeviceName.startsWith("EtOH")) {
                    characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_ETOH_RX_TX);
                }
                characteristicRX = characteristicTX;

                if (characteristicTX != null) {
                    mBluetoothLeService.setCharacteristicNotification(characteristicTX, true);

                    isSerial.setText("Serial ready");
                    updateReadyState(R.string.ready);
                    sendMessage("1");//初始化PID
                } else {
                    isSerial.setText("Serial can't be found");
                }

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);



        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        // is serial present?
        isSerial = (TextView) findViewById(R.id.isSerial);

        msgEdit = (EditText) findViewById(R.id.input);
        returnText = (TextView) findViewById(R.id.returnText);



        infoButton = (ImageView) findViewById(R.id.infoImage);
        infoButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                iascDialog();
            }
        });


        sendButton = (Button) findViewById(R.id.sendButton);

        sendButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (characteristicReady) {
                    StringBuffer sb = new StringBuffer();
                    sb.append(msgEdit.getText()).append("\n");
                    sendMessage(sb.toString());
                }
            }

        });


        sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        textX = (TextView) findViewById(R.id.textx);
        textY = (TextView) findViewById(R.id.texty);
        textZ = (TextView) findViewById(R.id.textz);

        SensorEventListener lsn = new SensorEventListener() {
            public void onSensorChanged(SensorEvent e) {
                x = e.values[SensorManager.DATA_X];
                y = e.values[SensorManager.DATA_Y];
                z = e.values[SensorManager.DATA_Z];
                textX.setText("x=" + (int) x);
                textY.setText("y=" + (int) y);
                textZ.setText("z=" + (int) z);
                int ix=(int)x/2;
                int iy=(int)y/2;
                if (    ix==3&&recentdata==4||ix==3&&recentdata==2||ix==2&&recentdata==1||ix==2&&recentdata==3||
                        ix==0&&recentdata==1||ix==0&&recentdata==-1||
                        ix==-3&&recentdata==-4||ix==-3&&recentdata==-2||ix==-2&&recentdata==-1||ix==-2&&recentdata==-3
                        ) {
                    if (ix == 3) {
                        sendMessage("wad\n");
                    }
                    if (ix == 2) {
                        sendMessage("w\n");
                    }
                    if(ix==0){
                        sendMessage("stop\n");
                    }
                    if(ix==-2){
                        sendMessage("s\n");
                    }
                    if(ix==-3){
                        sendMessage("sad\n");
                    }
                }

                if( iy==3&&recentturn==4||iy==3&&recentturn==2||iy==2&&recentturn==1||iy==2&&recentturn==3||
                        iy==0&&recentturn==1||iy==0&&recentturn==-1||
                        iy==-3&&recentturn==-4||iy==-3&&recentturn==-2||iy==-2&&recentturn==-1||iy==-2&&recentturn==-3
                        ){

                    if(iy==3){
                        sendMessage("aad\n");
                    }
                    if(iy==2){
                        sendMessage("a\n");
                    }
                    if(iy==0){
                        sendMessage("stopturn\n");
                    }
                    if(iy==-2){
                        sendMessage("d\n");
                    }
                    if(iy==-3){
                        sendMessage("dad\n");
                    }
                }
                recentdata=ix;
                recentturn=iy;
            }
            public void onAccuracyChanged(Sensor s, int accuracy) {
            }
        };
        // 注册listener，第三个参数是检测的精确度
        sensorMgr.registerListener(lsn, sensor, SensorManager.SENSOR_DELAY_GAME);
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void updateReadyState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                wait_ble(2000);

                characteristicReady = true;

                sendButton.setEnabled(characteristicReady);

                //Toast.makeText(DeviceControlActivity.this, getString(resourceId), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            //You can add some code here
            Log.v(TAG, "BLE Return Data : " + data);
            String old = returnText.getText() + "\n";
            returnText.setText(old + data);
        }
    }

    public void wait_ble(int i) {
        try {
            Thread.sleep(i);
        } catch (Exception e) {
            // ignore
        }
    }

    private void sendMessage(String msg) {
        int msglen = msg.length();
        Log.d(TAG, "Sending Result=" + msglen + ":" + msg);

        if (characteristicReady && (mBluetoothLeService != null)
                && (characteristicTX != null) && (characteristicRX != null)) {

            for (int offset = 0; offset < msglen; offset += BLE_MSG_BUFFER_LEN) {
                characteristicTX.setValue(msg.substring(offset, Math.min(offset + BLE_MSG_BUFFER_LEN, msglen)));
                mBluetoothLeService.writeCharacteristic(characteristicTX);
                wait_ble(BLE_MSG_SEND_INTERVAL);
            }
        } else {
            Toast.makeText(DeviceControlActivity.this, getString(R.string.disconnected), Toast.LENGTH_SHORT).show();
        }
    }

    private void iascDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.iasc_dialog,
                (ViewGroup) findViewById(R.id.dialog));
        new AlertDialog.Builder(this).setView(layout)
                .setPositiveButton("OK", null).show();
    }
}