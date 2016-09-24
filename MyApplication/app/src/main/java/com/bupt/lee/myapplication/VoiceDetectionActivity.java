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
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.gson.Gson;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.bupt.lee.myapplication.bean.*;
import com.bupt.lee.myapplication.ble.*;
import com.bupt.lee.myapplication.service.*;
import com.bupt.lee.myapplication.bean.VoiceBean.WS;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class VoiceDetectionActivity extends Activity  {
    private final static String TAG = VoiceDetectionActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static int BLE_MSG_SEND_INTERVAL = 100;
    public static int BLE_MSG_BUFFER_LEN = 18;

    private TextView isSerial, mConnectionState;
    private String mDeviceName, mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic characteristicTX, characteristicRX;
    private boolean mConnected = false, characteristicReady = false;
    private StringBuffer mBuffer;
    private ListView lvList;
    private String mAnswer;
    private HttpData httpData;
    private ArrayList<TalkBean> mList = new ArrayList<TalkBean>();
    private MyAdapter mAdapter;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;
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
                    Toast.makeText(VoiceDetectionActivity.this, getString(R.string.without_service), Toast.LENGTH_SHORT).show();
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
                if(intent.getStringExtra(mBluetoothLeService.EXTRA_DATA)!=null){
                    startVibrate();
                }
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.acticity_voice_detection);
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        isSerial = (TextView) findViewById(R.id.isSerial);

        //将“12345678”替换成您申请的 APPID，申请地址：http://www.xfyun.cn
        // 请勿在“=”与 appid 之间添加任务空字符或者转义符
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=57b9a202");

        lvList = (ListView) findViewById(R.id.lv_list);
        mAdapter = new MyAdapter();
        lvList.setAdapter(mAdapter);
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        sendMessage("2");//初始化PID

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


                //Toast.makeText(DeviceControlActivity.this, getString(resourceId), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(VoiceDetectionActivity.this, getString(R.string.disconnected), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 开始语音识别
     *
     * @param view
     */
    public void startVibrate(){
        //1.创建RecognizerDialog对象
        RecognizerDialog mDialog = new RecognizerDialog(this, null);
        //2.设置accent、language等参数
        mDialog.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mDialog.setParameter(SpeechConstant.ACCENT, "mandarin");
        //若要将UI控件用于语义理解，必须添加以下参数设置，设置之后onResult回调返回将是语义理解
        //结果
        // mDialog.setParameter("asr_sch", "1");
        // mDialog.setParameter("nlp_version", "2.0");

        mBuffer = new StringBuffer();

        //3.设置回调接口
        mDialog.setListener(new MyRecognizerDialogListener());
        //4.显示dialog，接收语音输入
        mDialog.show();
    }
    public void startListen(View view) {
        //1.创建RecognizerDialog对象
        RecognizerDialog mDialog = new RecognizerDialog(this, null);
        //2.设置accent、language等参数
        mDialog.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mDialog.setParameter(SpeechConstant.ACCENT, "mandarin");
        //若要将UI控件用于语义理解，必须添加以下参数设置，设置之后onResult回调返回将是语义理解
        //结果
        // mDialog.setParameter("asr_sch", "1");
        // mDialog.setParameter("nlp_version", "2.0");

        mBuffer = new StringBuffer();

        //3.设置回调接口
        mDialog.setListener(new MyRecognizerDialogListener());
        //4.显示dialog，接收语音输入
        mDialog.show();

    }

    /**
     * 语音合成
     *
     * @param view
     */
    public void startSpeak(String content) {
        //1.创建 SpeechSynthesizer 对象, 第二个参数：本地合成时传 InitListener
        SpeechSynthesizer mTts = SpeechSynthesizer
                .createSynthesizer(this, null);
        //2.合成参数设置，详见《科大讯飞MSC API手册(Android)》SpeechSynthesizer 类
        //设置发音人（更多在线发音人，用户可参见 附录12.2
        mTts.setParameter(SpeechConstant.VOICE_NAME, "xiaoyan"); //设置发音人
        mTts.setParameter(SpeechConstant.SPEED, "50");//设置语速
        mTts.setParameter(SpeechConstant.VOLUME, "80");//设置音量，范围 0~100
        mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD); //设置云端
        //设置合成音频保存位置（可自定义保存位置），保存在“./sdcard/iflytek.pcm”
        //保存在 SD 卡需要在 AndroidManifest.xml 添加写 SD 卡权限
        //仅支持保存为 pcm 和 wav 格式，如果不需要保存合成音频，注释该行代码
        mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, "./sdcard/iflytek.pcm");
        //3.开始合成
        mTts.startSpeaking(content, null);
    }

    /**
     * 解析json
     */



    protected String parseData(String json) {
        //Gson google
        Gson gson = new Gson();
        VoiceBean voiceBean = gson.fromJson(json, VoiceBean.class);

        StringBuffer sb = new StringBuffer();

        ArrayList<WS> ws = voiceBean.ws;
        for (WS w : ws) {
            String word = w.cw.get(0).w;
            sb.append(word);
        }

        return sb.toString();
    }



    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.bupt.lee.myapplication/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.bupt.lee.myapplication/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    class MyAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mList.size();
        }

        @Override
        public TalkBean getItem(int position) {
            return mList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = View.inflate(getApplicationContext(),
                        R.layout.list_item, null);

                holder = new ViewHolder();
                holder.tvAsk = (TextView) convertView.findViewById(R.id.tv_ask);
                holder.tvAnswer = (TextView) convertView
                        .findViewById(R.id.tv_answer);
                holder.ivPic = (ImageView) convertView
                        .findViewById(R.id.iv_pic);
                holder.llAnswer = (LinearLayout) convertView
                        .findViewById(R.id.ll_answer);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            TalkBean item = getItem(position);

            if (item.isAsk) {
                //提问
                holder.tvAsk.setVisibility(View.VISIBLE);
                holder.llAnswer.setVisibility(View.GONE);

                holder.tvAsk.setText(item.content);
            } else {
                //回答
                holder.tvAsk.setVisibility(View.GONE);
                holder.llAnswer.setVisibility(View.VISIBLE);

                holder.tvAnswer.setText(item.content);

                //图片
                if (item.imageId > 0) {
                    holder.ivPic.setVisibility(View.VISIBLE);
                    holder.ivPic.setImageResource(item.imageId);
                } else {
                    holder.ivPic.setVisibility(View.GONE);
                }
            }

            return convertView;
        }

    }


    static class ViewHolder {
        public TextView tvAsk;

        public TextView tvAnswer;
        public ImageView ivPic;
        public LinearLayout llAnswer;
    }
    class  MyRecognizerDialogListener implements HttpGetDataListener, RecognizerDialogListener {
        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String result = results.getResultString();
            //System.out.println("识别结果:" + result);
            String parseData = parseData(result);
            //System.out.println("解析结果:" + parseData);
            //System.out.println("isLast:" + isLast);
            mBuffer.append(parseData);
            if (isLast) {
                //会话结束
                String askContent = mBuffer.toString();
                httpData = (HttpData) new HttpData("http://www.tuling123.com/openapi/api?key=597d67c16a5a4cbdbac29503a57b9318&info=" + askContent, this).execute();
                System.out.println("最终结果:" + askContent);

                //初始化提问对象
                TalkBean askBean = new TalkBean(askContent, -1, true);
                mList.add(askBean);
                if(askContent.contains("前进")||askContent.contains("向前")){sendMessage("w\n");}
                if(askContent.contains("后退")||askContent.contains("向后")){sendMessage("s\n");}
                if(askContent.contains("向左")||askContent.contains("左转")){sendMessage("a\n");}
                if(askContent.contains("向右")||askContent.contains("右转")){sendMessage("d\n");}
                if(askContent.contains("跳舞")||askContent.contains("跳支舞")||askContent.contains("跳个舞")){sendMessage("dance\n");}
                if(askContent.contains("转圈")||askContent.contains("绕圈")){sendMessage("round\n");}
                //初始化回答对象
                    /*String answer = "没听清";
					int imageId = -1;
					if (askContent.contains("你好")) {
						answer = "你好呀!";
					} else if (askContent.contains("你是谁")) {
						answer = "我是你的小助手!";
					} else if (askContent.contains("美女")) {
						//随机回答
						int i = (int) (Math.random() * mAnswers.length);//0,1,2,3
						answer = mAnswers[i];

						int j = (int) (Math.random() * mPics.length);//0,1,2,3
						imageId = mPics[j];
					} else if (askContent.contains("天王盖地虎")) {
						answer = "小鸡炖蘑菇";
						imageId = R.drawable.m;
					} else if(askContent.contains("明天天气")){
						answer = "会很热的";
					} else if(askContent.contains("修") && askContent.contains("空调")){
						answer = "正在为您下单";
					}
*/

            }
        }

        @Override
        public void onError(SpeechError speechError) {

        }
        @Override
        public void getDataUrl(String data) {
            parseText(data);
        }
        public void parseText(String str) {
            try {
                JSONObject jb = new JSONObject(str);
                 System.out.println(jb.getString("code"));
                 System.out.println(jb.getString("text"));
                mAnswer = jb.getString("text");
                TalkBean answerBean = new TalkBean(mAnswer, -1, false);
                mList.add(answerBean);

                //刷新listview
                mAdapter.notifyDataSetChanged();

                //让listview自动显示最后一个条目
                lvList.setSelection(mList.size() - 1);

                startSpeak(mAnswer);
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
