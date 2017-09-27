package com.example.android.TRY_US;
import java.security.PrivateKey;
import java.util.*;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.example.android.TRY_US.R;
import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class informOfDangerousActivity extends Activity {
    boolean start = false;
    boolean dangerousflag = false;
    private ListView lv;
    SharedPreferences prefs = getSharedPreferences("SaveData", Context.MODE_PRIVATE);

    // サンプリングレート
    int SAMPLING_RATE = 44100;
    // FFTのポイント数
    int FFT_SIZE = 4096;
    private String filename = "data1";
    ArrayList<Float> list = new ArrayList<Float>();
    ArrayList<Float> listrealtime = new ArrayList<Float>();
    // デシベルベースラインの設定
    float dB_baseline = (float)(Math.pow(2, 15) * FFT_SIZE * Math.sqrt(2));

    // 分解能の計算
    float resol = ((SAMPLING_RATE / (float) FFT_SIZE));
    AudioRecord audioRec = null;
    boolean bIsRecording = false;
    int bufSize;
    Thread fft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inform_dangerous);
        bufSize = AudioRecord.getMinBufferSize(SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        final ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        adapter.add("data1");
        adapter.add("data2");
        adapter.add("data3");
        adapter.add("data4");
        adapter.add("data5");

        lv = (ListView) findViewById(R.id.listview);
        lv.setAdapter(adapter);

        //リスト項目が選択された時のイベントを追加
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                filename = ("data" + position + ".txt");
                String msg = "data" + (position+1) + "を選択しました。音声登録を行う場合検知音追加ボタンを押してください";
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();

            }
        });

        Button returnbtn = (Button) findViewById(R.id.return_button);
        returnbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bIsRecording = false;
                Intent intent = new Intent(getApplication(), StartActivity.class);
                startActivity(intent);
            }
        });

        Button checkbtn = (Button) findViewById(R.id.checkbutton);
        checkbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                for(int i = 0; i < list.size(); i++) {
                    Log.d("CHECK",list.get(i).toString());
                }
            }
        });

        Button add_dangerous_sound = (Button) findViewById(R.id.add_dangerous_sound);
        add_dangerous_sound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bIsRecording == true) {
                    //offにする
                    String msg = "録音終了します。";
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                    start = false;
                    bIsRecording = false;
                }
                else if (bIsRecording == false) {
                    //onにする
                    String msg = "録音開始します。";
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                    start = false;
                    StartRecording();
                }
            }
        });



        Button start_btn = (Button) findViewById(R.id.start_btn);
        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(start == false) {
                    //周囲音録音開始
                    start = true;

                    StartRecording();
                }
                else if(start == true){
                    bIsRecording = false;
                    start = false;

                }
            }
        });
    }

    protected void StartRecording() {
        // AudioRecordの作成
        audioRec = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufSize * 2);
        audioRec.startRecording();
        bIsRecording = true;

        //フーリエ解析スレッド
        fft = new Thread(new Runnable() {
            @Override
            public void run() {
                byte buf[] = new byte[bufSize * 2];
                while (bIsRecording) {
                    audioRec.read(buf, 0, buf.length);

                    //エンディアン変換
                    ByteBuffer bf = ByteBuffer.wrap(buf);
                    bf.order(ByteOrder.LITTLE_ENDIAN);
                    short[] s = new short[(int) bufSize];
                    for (int i = bf.position(); i < bf.capacity() / 2; i++) {
                        s[i] = bf.getShort();
                    }

                    //FFTクラスの作成と値の引き渡し
                    FFT fft4g = new FFT(FFT_SIZE);
                    double[] FFTdata = new double[FFT_SIZE];
                    for (int i = 0; i < FFT_SIZE / 2; i++) {
                        FFTdata[i] = (double) s[i];
                    }
                    fft4g.rdft(1, FFTdata);

                    //デシベルの計算
                    float[] dbfs = new float[FFT_SIZE / 2];
                    float max_db = (float)-120d;
                    int max_i = 0;
                    for (int i = 0; i < FFT_SIZE; i += 2) {
                        dbfs[i / 2] = (int) (20 * Math.log10(Math.sqrt(Math
                                .pow(FFTdata[i], 2)
                                + Math.pow(FFTdata[i + 1], 2)) / dB_baseline));
                        if (max_db < dbfs[i / 2]) {
                            max_db = dbfs[i / 2];
                            max_i = i / 2;
                        }
                    }

                    //音量が最大の周波数と，その音量を表示
                    Log.d("fft","周波数："+ resol * max_i+" [Hz] 音量：" +  max_db+" [dB]");
                    if(start == false) {
                        list.add(resol * max_i);
                    }
                    else if(start == true){

                        listrealtime.add(resol * max_i);
                        if(list.size() < listrealtime.size()) {
                            CompareArray();
                            if(dangerousflag == false) {
                                listrealtime.remove(0);
                            }
                        }
                    }
                }
                // 録音停止
                audioRec.stop();
                audioRec.release();
            }
        });
        //スレッドのスタート
        fft.start();
    }

    private boolean CompareArray(){
        float total = 0;
        for (int i = 0; i < list.size(); i++) {
            if(list.get(i)  > 0) {
                total += (((listrealtime.get(i) - list.get(i)) / list.get(i)) * 100);
            }
        }
        dangerousflag = false;
        if(-10 <= total/list.size() && total/list.size() <= 10){
            //スマホを振動させて特定音検知を通知する
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(1000);
            listrealtime.clear();
            bIsRecording = false;

            dangerousflag = true;
        }
        return dangerousflag;
    }
    private void FileRead(){//ファイルからの読出し

    }

    public void save(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Gson gson = new Gson();
        prefs.edit().putString(filename, gson.toJson(this)).apply();
    }

    private void FileWrite(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences();
        Gson gson = new Gson();
        prefs.edit().putString(filename,gson.toJson(list)).apply();


    }


}