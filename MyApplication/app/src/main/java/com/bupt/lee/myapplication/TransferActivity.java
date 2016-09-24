package com.bupt.lee.myapplication;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.widget.Button;
public class TransferActivity extends Activity {
    private final static String TAG = TransferActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private String mDeviceName, mDeviceAddress;
    private Button transferButton;
    private Button transferButton2;
    private Button transferButton3;



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);
        Intent intent1=getIntent();
        mDeviceName = intent1.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent1.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        transferButton=(Button) findViewById(R.id.device_control);
        transferButton2=(Button) findViewById(R.id.face_detection);
        transferButton3=(Button) findViewById(R.id.voice_control);
        transferButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), DeviceControlActivity.class);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, mDeviceName);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
                startActivity(intent);
            }
        });
        transferButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), FaceDetectionActivity.class);
                intent.putExtra(FaceDetectionActivity.EXTRAS_DEVICE_NAME, mDeviceName);
                intent.putExtra(FaceDetectionActivity.EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
                startActivity(intent);
            }
        });
        transferButton3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), VoiceDetectionActivity.class);
                intent.putExtra(VoiceDetectionActivity.EXTRAS_DEVICE_NAME, mDeviceName);
                intent.putExtra(VoiceDetectionActivity.EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
                startActivity(intent);
            }
        });
    }
}