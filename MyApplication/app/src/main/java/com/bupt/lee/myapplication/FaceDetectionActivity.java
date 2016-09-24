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
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.bupt.lee.myapplication.ble.BluetoothLeService;
import com.bupt.lee.myapplication.camera.CameraInterface;
import com.bupt.lee.myapplication.camera.preview.CameraSurfaceView;
import com.bupt.lee.myapplication.mode.GoogleFaceDetect;
import com.bupt.lee.myapplication.ui.FaceView;
import com.bupt.lee.myapplication.util.DisplayUtil;
import com.bupt.lee.myapplication.util.EventUtil;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class FaceDetectionActivity extends Activity {

    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";



    public static int BLE_MSG_SEND_INTERVAL = 100;
    public static int BLE_MSG_BUFFER_LEN = 18;

    private TextView isSerial, mConnectionState;
    int x=0;
    TextView TVL=null;
    TextView TVR=null;
    CameraSurfaceView surfaceView = null;
    ImageButton shutterBtn;
    ImageButton switchBtn;
    FaceView faceView;
    float previewRate = -1f;
    private MainHandler mMainHandler = null;
    GoogleFaceDetect googleFaceDetect = null;

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
                    Toast.makeText(FaceDetectionActivity.this, getString(R.string.without_service), Toast.LENGTH_SHORT).show();
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
                    sendMessage("2");//初始化PID
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
        setContentView(R.layout.activity_face_detecion);
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        // Sets up UI references.
        // is serial present?
        isSerial = (TextView) findViewById(R.id.isSerial);
        initUI();
        initViewParams();
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        mMainHandler = new MainHandler();
        googleFaceDetect = new GoogleFaceDetect(getApplicationContext(), mMainHandler);
        shutterBtn.setOnClickListener(new BtnListeners());
        switchBtn.setOnClickListener(new BtnListeners());
        mMainHandler.sendEmptyMessageDelayed(EventUtil.CAMERA_HAS_STARTED_PREVIEW, 1500);
        sendMessage("2");//初始化PID

    }
    private void initUI(){
        surfaceView = (CameraSurfaceView)findViewById(R.id.camera_surfaceview);
        shutterBtn = (ImageButton)findViewById(R.id.btn_shutter);
        switchBtn = (ImageButton)findViewById(R.id.btn_switch);
        faceView = (FaceView)findViewById(R.id.face_view);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        isSerial = (TextView) findViewById(R.id.isSerial);
        TVL=(TextView)findViewById(R.id.textView);
        TVR=(TextView)findViewById(R.id.textView2);

    }
    private void initViewParams(){
        LayoutParams params = surfaceView.getLayoutParams();
        Point p = DisplayUtil.getScreenMetrics(this);
        params.width = p.x;
        params.height = p.y;
        previewRate = DisplayUtil.getScreenRate(this); //Ĭ��ȫ���ı���Ԥ��
        surfaceView.setLayoutParams(params);
    }
    private class BtnListeners implements OnClickListener{

        @Override
        public void onClick(View v) {
            // TODO Auto-generated method stub
            switch(v.getId()){
                case R.id.btn_shutter:
                    takePicture();
                    break;
                case R.id.btn_switch:
                    switchCamera();
                    break;
                default:break;
            }
        }
    }
    private  class MainHandler extends Handler{

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            switch (msg.what){
                case EventUtil.UPDATE_FACE_RECT:
                    Face[] faces = (Face[]) msg.obj;
                    faceView.setFaces(faces);

                    sendSignal(x);
                    break;
                case EventUtil.CAMERA_HAS_STARTED_PREVIEW:
                    startGoogleFaceDetect();
                    break;
            }
            super.handleMessage(msg);
        }
    }
    private void takePicture(){
        CameraInterface.getInstance().doTakePicture();
        mMainHandler.sendEmptyMessageDelayed(EventUtil.CAMERA_HAS_STARTED_PREVIEW, 1500);
    }
    private void switchCamera(){
        stopGoogleFaceDetect();
        int newId = (CameraInterface.getInstance().getCameraId() + 1)%2;
        CameraInterface.getInstance().doStopCamera();
        CameraInterface.getInstance().doOpenCamera(null, newId);
        CameraInterface.getInstance().doStartPreview(surfaceView.getSurfaceHolder(), previewRate);
        mMainHandler.sendEmptyMessageDelayed(EventUtil.CAMERA_HAS_STARTED_PREVIEW, 1500);
//		startGoogleFaceDetect();

    }
    private void startGoogleFaceDetect(){
        Camera.Parameters params = CameraInterface.getInstance().getCameraParams();
        if(params.getMaxNumDetectedFaces() > 0){
            if(faceView != null){
                faceView.clearFaces();
                faceView.setVisibility(View.VISIBLE);
            }
            CameraInterface.getInstance().getCameraDevice().setFaceDetectionListener(googleFaceDetect);
            CameraInterface.getInstance().getCameraDevice().startFaceDetection();
        }
    }
    private void stopGoogleFaceDetect(){
        Camera.Parameters params = CameraInterface.getInstance().getCameraParams();
        if(params.getMaxNumDetectedFaces() > 0){
            CameraInterface.getInstance().getCameraDevice().setFaceDetectionListener(null);
            CameraInterface.getInstance().getCameraDevice().stopFaceDetection();
            faceView.clearFaces();
        }
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
            Toast.makeText(FaceDetectionActivity.this, getString(R.string.disconnected), Toast.LENGTH_SHORT).show();
        }
    }
    private void sendSignal(int i){
        int k=faceView.getSetX()/100;
        if(i!=k)
        {
            x=k;
            TVL.setText("X="+k);
           if(x<8){
               sendMessage("a\n");
           }
            if(x<14&&x>=8){
                sendMessage("stopturn\n");
            }
            if(x>=14){
                sendMessage("d\n");
            }
            if(x==0){sendMessage("stopturn\n");}
        }
    }
    private void updateReadyState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                wait_ble(2000);

                characteristicReady = true;


                //Toast.makeText(DeviceControlActivity.this, getString(resourceId), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
