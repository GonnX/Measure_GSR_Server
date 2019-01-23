package com.example.luolab.acquisition_platform;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.StrictMode;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadLisener;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Inflater;

public class GsrView extends Fragment{

    private final int SerialDataSize = 420;

    private File f;
    private String FilePath = null;

    private Button startBtn;
    private Button backBtn;
    private Button nextBtn;
    private Button setSampleRateBtn;
    private Button setUsrInfoBtn;

    private ImageView acupointImage;
    private View gsrView;

    private Object[] imageResource;

    private boolean startFlag;

    private int counter = 0;

    private String[] sampleRate_Item;
    private String selectSampleRate;
    private String[] acupointName;

    private Handler Graph_Handle;
    private Handler measureAcupoint_Handler;
    private Handler TimeDialog_Timer_Handler;
    private Handler mHandler;
    private Handler SerialmHandler;
    private Handler Update_GsrValue;

    private Handler Update_UsrInfo_Text;

    private boolean SerialUIFlag = false;

//    private AlertDialog.Builder TimeDialog_Builder;
//    private AlertDialog TimeDialog;

    private AlertDialog.Builder UsrInfoDialog_Builder;
    private AlertDialog UsrInfoDialog;

    private TimerTask timerTask_updateImage = null;
    private Timer timer_updateImage = null;

    private CountDownTimer TimeDialog_Timer = null;

    private View dialogView;

    private GraphView G_Graph;
    private LineGraphSeries<DataPoint> G_Series;

    private int mXPoint;
    private boolean FileFlag = false;

    private int[] TempSize = new int[2];
    private int SizeIndex = 0;

    private byte[] SerialData_Queue = new byte[SerialDataSize];
    private int Queue_Index_Rear = 0;
    private int Queue_Index_Front = 0;

    private File dataPointsFile;
    private File fftOutFile;
    private FileWriter[] fileWriter;
    private BufferedWriter[] bw;

    private TextView[] UsrInfo = new TextView[6];

    private TextView UsrInfo_DisplayText;

    private Calendar c;
    private SimpleDateFormat dateformat;

    private TextView GsrValue_tv;
    private double MeanGsrValue = 0.0;
    private double TempMeanGsrValue = 0.0;
    private double TestTemp = 0.0;

    private MyDBHelper myDBHelper;

    private Spinner mySpinner;
    private ArrayAdapter<String> usrInfo_Adapter;
    private ArrayList<String> usrInfo_Array;

    private String SpinnerSelected;

    private boolean SerialFlag = false;

    private LayoutInflater G_Inflater;

    private int[] FinalData;

    private String Get_Uri = "https://lens.csie.ncku.edu.tw/~Platform/getDataFromDB.php";
    private String Insert_Uri = "https://lens.csie.ncku.edu.tw/~Platform/insertDataToDB.php";
    private String Get_Query_Command = "SELECT * FROM PPG";
    private String Get_Query_Command_GSR = "SELECT * FROM GSR";
    private String Get_Query_Command_Guan = "SELECT * FROM guan";
    private String Insert_Query_Command = "INSERT INTO PPG (name,age,birthday,height,weight,doctor)VALUES";
    private String Insert_Query_Command_GSR = "INSERT INTO GSR (name,age,birthday,height,weight,doctor)VALUES";
    private String Insert_Query_Command_Guan = "INSERT INTO guan (name,age,birthday,height,weight,doctor)VALUES";
    private String Update_Command = "UPDATE PPG SET ";
    private String Update_Command_GSR = "UPDATE GSR SET ";
    private String Update_Command_Guan = "UPDATE guan SET ";

    private boolean UsrInfo_text_Enable = false;

    public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";
    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            SetChoice(arg0,arg0.length,G_Inflater);
        }
    };
    /*
    private View.OnTouchListener spinnerOnTouch = new View.OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                UsrInfo_DisplayText.setText("如有輸入過，請選擇姓名");
            }
            return false;
        }
    };
    */

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback);
                            SerialFlag = true;
                            setEnabledUi(2);
                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                onClickStart();
            }
        };
    };
    private void onClickStart(){
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == 0x2341)//Arduino Vendor ID
                {
                    PendingIntent pi = PendingIntent.getBroadcast(G_Inflater.getContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }
    }
    private void updateDB()
    {
        usrInfo_Array.clear();

        String result = GetDB(Get_Query_Command_GSR,Get_Uri);
        JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(result);
            for(int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonData = jsonArray.getJSONObject(i);
                usrInfo_Array.add(jsonData.getString("name"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.M)
    public View onCreateView(final LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {

        gsrView = inflater.inflate(R.layout.gsr, container, false);

        G_Graph = gsrView.findViewById(R.id.data_chart);

        ResetGraph();

        G_Inflater = inflater;

        Update_UsrInfo_Text = new Handler();

        usbManager = (UsbManager) inflater.getContext().getSystemService(inflater.getContext().USB_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        inflater.getContext().registerReceiver(broadcastReceiver, filter);


        acupointImage = gsrView.findViewById(R.id.Gsr_ImageView);

        imageResource = new Object[]{R.drawable.one,R.drawable.two,R.drawable.three,
                                     R.drawable.four,R.drawable.five,R.drawable.six,
                                     R.drawable.seven,R.drawable.eight,R.drawable.nine,
                                     R.drawable.ten,R.drawable.eleven,R.drawable.twelve};

        GsrValue_tv = gsrView.findViewById(R.id.GsrAvg_tv);

        mHandler = new Handler();
        measureAcupoint_Handler = new Handler();
        TimeDialog_Timer_Handler = new Handler();
        Update_GsrValue = new Handler();
        Graph_Handle = new Handler();

        FinalData = new int[200];

        dialogView = View.inflate(inflater.getContext(),R.layout.user_info,null);

        UsrInfoDialog_Builder = new AlertDialog.Builder((Activity)inflater.getContext())
                .setTitle("CreatUsrInfo")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                UsrInfo[0] = UsrInfoDialog.findViewById(R.id.Name_tv);
                UsrInfo[1] = UsrInfoDialog.findViewById(R.id.Age_tv);
                UsrInfo[2] = UsrInfoDialog.findViewById(R.id.Brithday_tv);
                UsrInfo[3] = UsrInfoDialog.findViewById(R.id.Height_tv);
                UsrInfo[4] = UsrInfoDialog.findViewById(R.id.Weight_tv);
                UsrInfo[5] = UsrInfoDialog.findViewById(R.id.doctor_Name_tv);

                if(UsrInfo[0].getText().toString().equals("") || UsrInfo[1].getText().toString().equals("") || UsrInfo[2].getText().toString().equals("") ||
                    UsrInfo[3].getText().toString().equals("") || UsrInfo[4].getText().toString().equals("") || UsrInfo[5].getText().toString().equals(""))
                {
                    Toast.makeText(inflater.getContext(),"請勿空白，確實填寫",Toast.LENGTH_SHORT).show();
                }
                else {
                    boolean flag = false;
                    String result = GetDB(Get_Query_Command_GSR,Get_Uri);
                    JSONArray jsonArray = null;
                    try {
                        jsonArray = new JSONArray(result);
                        for(int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonData = jsonArray.getJSONObject(i);
                            if(UsrInfo[0].getText().toString().equals(jsonData.getString("name"))){
                                flag = true;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if(flag == false) {
                        GetDB(Insert_Query_Command +
                                "('" + UsrInfo[0].getText().toString() + "','"
                                + UsrInfo[1].getText().toString() + "','"
                                + UsrInfo[2].getText().toString() + "','"
                                + UsrInfo[3].getText().toString() + "','"
                                + UsrInfo[4].getText().toString() + "','"
                                + UsrInfo[5].getText().toString() + "')", Insert_Uri);
                        GetDB(Insert_Query_Command_GSR +
                                "('" + UsrInfo[0].getText().toString() + "','"
                                + UsrInfo[1].getText().toString() + "','"
                                + UsrInfo[2].getText().toString() + "','"
                                + UsrInfo[3].getText().toString() + "','"
                                + UsrInfo[4].getText().toString() + "','"
                                + UsrInfo[5].getText().toString() + "')", Insert_Uri);
                        GetDB(Insert_Query_Command_Guan +
                                "('" + UsrInfo[0].getText().toString() + "','"
                                + UsrInfo[1].getText().toString() + "','"
                                + UsrInfo[2].getText().toString() + "','"
                                + UsrInfo[3].getText().toString() + "','"
                                + UsrInfo[4].getText().toString() + "','"
                                + UsrInfo[5].getText().toString() + "')", Insert_Uri);
                        usrInfo_Array.add(UsrInfo[0].getText().toString());
                    }

                    usrInfo_Array.add(UsrInfo[0].getText().toString());
                    Toast.makeText(inflater.getContext(),"設定完成",Toast.LENGTH_SHORT).show();
                    SerialUIFlag = true;
                    //setEnabledUi(2);
                }
            }
        });
        UsrInfoDialog = UsrInfoDialog_Builder.create();
        UsrInfoDialog.setView(dialogView);

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build());

        usrInfo_Array = new ArrayList<String>();

        String result = GetDB(Get_Query_Command_GSR,Get_Uri);

        JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(result);
            for(int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonData = jsonArray.getJSONObject(i);
                usrInfo_Array.add(jsonData.getString("name"));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        usrInfo_Adapter = new ArrayAdapter<String>(inflater.getContext(),R.layout.usr_spinner,R.id.spinner_tv,usrInfo_Array);

        mySpinner = dialogView.findViewById(R.id.usrSpinner);
        UsrInfo_DisplayText = dialogView.findViewById(R.id.Display_Text);
        mySpinner.setAdapter(usrInfo_Adapter);
        //mySpinner.setOnTouchListener(spinnerOnTouch);

        mySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SpinnerSelected = parent.getSelectedItem().toString();

                ArrayList<String> Name = new ArrayList<>();
                ArrayList<String> Age = new ArrayList<>();
                ArrayList<String> Birthday = new ArrayList<>();
                ArrayList<String> Height = new ArrayList<>();
                ArrayList<String> Weight = new ArrayList<>();
                ArrayList<String> Doctor = new ArrayList<>();

                String result = GetDB(Get_Query_Command_GSR,Get_Uri);
                JSONArray jsonArray = null;
                try {
                    jsonArray = new JSONArray(result);
                    for(int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonData = jsonArray.getJSONObject(i);
                        Name.add(jsonData.getString("name"));
                        Age.add(jsonData.getString("age"));
                        Birthday.add(jsonData.getString("birthday"));
                        Height.add(jsonData.getString("height"));
                        Weight.add(jsonData.getString("weight"));
                        Doctor.add(jsonData.getString("doctor"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                UsrInfo[0] = UsrInfoDialog.findViewById(R.id.Name_tv);
                UsrInfo[1] = UsrInfoDialog.findViewById(R.id.Age_tv);
                UsrInfo[2] = UsrInfoDialog.findViewById(R.id.Brithday_tv);
                UsrInfo[3] = UsrInfoDialog.findViewById(R.id.Height_tv);
                UsrInfo[4] = UsrInfoDialog.findViewById(R.id.Weight_tv);
                UsrInfo[5] = UsrInfoDialog.findViewById(R.id.doctor_Name_tv);

                for(int i = 0 ; i < Name.size() ;i++){
                    if(Name.get(i).equals(SpinnerSelected)){
                        UsrInfo[0].setText(Name.get(i));
                        UsrInfo[1].setText(Age.get(i));
                        UsrInfo[2].setText(Birthday.get(i));
                        UsrInfo[3].setText(Height.get(i));
                        UsrInfo[4].setText(Weight.get(i));
                        UsrInfo[5].setText(Doctor.get(i));
                        break;
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

//        TimeDialog_Builder = new AlertDialog.Builder((Activity)inflater.getContext());
//        TimeDialog = TimeDialog_Builder.create();
//        TimeDialog.setView(dialogView);
//        TimeDialog.getWindow().setGravity(Gravity.BOTTOM);

        startFlag = false;

        acupointName = new String[]{"太淵","大陵","神門","陽谷","陽池","陽谿","束骨","太沖","衝陽","太白","邱墟","大鍾"};
        sampleRate_Item = new String[]{"50","100","200"};
        selectSampleRate = null;

        fileWriter = new FileWriter[12];
        bw = new BufferedWriter[12];

        for(int i = 0 ; i < fileWriter.length ; i++) {
            fileWriter[i] = null;
            bw[i] = null;
        }

        dateformat  = new SimpleDateFormat("yyyyMMddHHmmss");

        FilePath = String.valueOf(inflater.getContext().getExternalFilesDir(null)) + "/GSR";
        f = new File(String.valueOf(FilePath));
        f.mkdir();

        //CheckFileExists(inflater);

        startBtn = gsrView.findViewById(R.id.start_btn);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.M)
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onClick(View view) {
                ResetGraph();
                CheckWrite(inflater);
                StartBtn_Click(inflater);
//                for(int i = 0 ; i < bw.length ; i++)
//                {
//                    if(bw[i] != null){
//                        try {
//                            bw[i].close();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }
//
//                    bw[i] = new BufferedWriter(fileWriter[i]);
//
//                    try {
//                        bw[i].newLine();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//                timer_updateImage = new Timer();
//
//                timerTask_updateImage = new TimerTask() {
//                    @Override
//                    public void run() {
//
//                        UpdateAcupointImage();
//                        DialogShow(inflater);
//                    }
//                };
//                timer_updateImage.schedule(timerTask_updateImage,0,4000);
            }
        });

        nextBtn = gsrView.findViewById(R.id.next_btn);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NextBtn_Click(inflater);
            }
        });

        backBtn = gsrView.findViewById(R.id.back_btn);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BackBtn_Click(inflater);
            }
        });

        setSampleRateBtn = gsrView.findViewById(R.id.setSampleRate_btn);
        setSampleRateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                new AlertDialog.Builder((Activity)inflater.getContext()).setTitle(R.string.dialog_title)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                /*if(selectSampleRate != null && SerialFlag != false) {
                                    setEnabledUi(0);
                                    Toast.makeText(inflater.getContext(),"等待連接",Toast.LENGTH_SHORT).show();
                                }*/
                            }
                        })
                        .setSingleChoiceItems(sampleRate_Item, 2, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                selectSampleRate = sampleRate_Item[which];
                                //Toast.makeText(inflater.getContext(),selectSampleRate,Toast.LENGTH_SHORT).show();
                            }
                        })
                        .create()
                        .show();
            }
        });

        selectSampleRate = "200";

        setUsrInfoBtn = gsrView.findViewById(R.id.setUsrInfo_btn);
        setUsrInfoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateDB();
                UsrInfoDialog.show();
            }
        });

        setEnabledUi(1);

        UpdateGsrValue(inflater);
        onClickStart();
        updateDB();
        setSampleRateBtn.setEnabled(true);
        //nextBtn.setEnabled(true);
        //backBtn.setEnabled(true);

        SerialmHandler = new Handler();
        SerialH();

        return gsrView;
    }
    private void SerialH()
    {
        SerialmHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(selectSampleRate != null && SerialFlag != false && SerialUIFlag != false) {
                    setEnabledUi(0);
                }
                else {
                    if(SerialUIFlag != false)
                        Toast.makeText(G_Inflater.getContext(),"等待感測器連接",Toast.LENGTH_SHORT).show();
                    SerialmHandler.postDelayed(this, 2000);
                }
            }
        },2000);

    }
    private String GetDB(String Query_Command,String uri)
    {
        String result = null;
        try {
            result = DBConnector.executeQuery(Query_Command,uri);
                /*
                    SQL 結果有多筆資料時使用JSONArray
                    只有一筆資料時直接建立JSONObject物件
                    JSONObject jsonData = new JSONObject(result);
                */
//            JSONArray jsonArray = new JSONArray(result);
//            for(int i = 0; i < jsonArray.length(); i++) {
//                JSONObject jsonData = jsonArray.getJSONObject(i);
//
//                usrInfo_Array.add(jsonData.getString("name"));
//            }
        } catch(Exception e) {
        }
        return result;
    }
//    private void CheckFileExists(LayoutInflater inflater)
//    {
//        File f;
//        for(int i = 0 ; i < acupointName.length ; i++) {
//            f = new File(inflater.getContext().getExternalFilesDir(null) + "/" + acupointName[i] + ".txt");
//            if(f.exists()){
//                f.delete();
//            }
//            f = null;
//        }
//    }
    private void UpdateGsrValue(final LayoutInflater inflater)
    {
        Update_GsrValue.post(new Runnable() {
            @Override
            public void run() {
                if((int)TempMeanGsrValue > 508 && (int)TempMeanGsrValue < 513)
                {
                    Toast.makeText(inflater.getContext(),"尚未接觸皮膚量測，請接觸皮膚重新量測",Toast.LENGTH_SHORT).show();
                    TempMeanGsrValue = 0.0;
                }
                GsrValue_tv.setText(new String(String.valueOf(String.format("%.2f",MeanGsrValue)) + "M"));
                Update_GsrValue.post(this);
            }
        });
    }
    private void UpdateGraph(final int size, final LayoutInflater inflater){
        G_Graph.post(new Runnable() {
            @Override
            public void run() {
                AppedSeriesData(size,inflater);
                G_Graph.getViewport().setMaxX(mXPoint);
                G_Graph.getViewport().setMinX(0);
                //G_Graph.getViewport().setMinX(mXPoint - 1);
                //mXPoint += 1;
                //G_Graph.postDelayed(this,50);
            }
        });
    }
    private void AppedSeriesData(int size,LayoutInflater inflater)
    {
        if(size % 2 != 0){
            TempSize[SizeIndex] = size;
        }
        if(SizeIndex > 0 && (TempSize[0] + size) % 2 != 0){
            for(int i = 0; i < (size + TempSize[0] - 1) / 2; i++) {
                int data = (int)(PopSerialData() << 8);
                int data2 =  (int)(PopSerialData());

                if(data2 < 0)
                    data2 += 256;

                G_Series.appendData(new DataPoint(mXPoint++,data + data2), true, 400);
            }
            SizeIndex = 0;
            TempSize[0] = 1;
            TempSize[1] = 0;
        }
        else if(SizeIndex > 0 && (TempSize[0] + size) % 2 == 0){
            for(int i = 0; i < (size + TempSize[0]) / 2; i++) {
                int data = (int)(PopSerialData() << 8);
                int data2 =  (int)(PopSerialData());

                if(data2 < 0)
                    data2 += 256;

                G_Series.appendData(new DataPoint(mXPoint++,data + data2), true, 400);
            }
            SizeIndex = -1;
            TempSize[0] = 0;
            TempSize[1] = 0;
        }
        else if(size % 2 == 0){
            for(int i = 0; i < size / 2; i++) {
                int data = (int)(PopSerialData() << 8);
                int data2 =  (int)(PopSerialData());

                if(data2 < 0)
                    data2 += 256;

                G_Series.appendData(new DataPoint(mXPoint++,data + data2), true, 400);
            }
        }
        if(mXPoint == Integer.parseInt(selectSampleRate)) {
            FileFlag = true;
            ((Vibrator) inflater.getContext().getSystemService(Service.VIBRATOR_SERVICE)).vibrate(new long[]{0,500}, -1);
        }
        SizeIndex++;
    }
    private void SetFileHeader(BufferedWriter bw)
    {
        try {
            bw.write("量測時間 : " + dateformat.format(c.getTime()));
            bw.newLine();
            bw.newLine();
            bw.write("年齡 : " + UsrInfo[1].getText());
            bw.newLine();
            bw.newLine();
            bw.write("生日 : " + UsrInfo[2].getText());
            bw.newLine();
            bw.newLine();
            bw.write("身高 : " + UsrInfo[3].getText());
            bw.newLine();
            bw.newLine();
            bw.write("體重 : " + UsrInfo[4].getText());
            bw.newLine();
            bw.newLine();
            bw.write("SampleRate = " + selectSampleRate);
            bw.newLine();
            bw.newLine();
            bw.write("訊號 : ");
            bw.newLine();
            bw.newLine();
            bw.write("[ ");
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    private void CheckWrite(final LayoutInflater inflater){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if(FileFlag == true && mXPoint != 0){
                    try{
                        int cnt = 0;
                        mXPoint = 0;
                        MeanGsrValue = 0.0;
                        //serialPort.close();
                        c = Calendar.getInstance();
                        //fileWriter[counter] = new FileWriter(FilePath + "/" + dateformat.format(c.getTime()) + UsrInfo[0].getText() + "_" + acupointName[counter] + ".txt",false);
                        fileWriter[counter] = new FileWriter(FilePath + "/" + UsrInfo[0].getText() + "_" + acupointName[counter] + ".txt",false);
                        bw[counter] = new BufferedWriter(fileWriter[counter]);
                        SetFileHeader(bw[counter]);

                        for(int i = 0 ; i < Integer.parseInt(selectSampleRate) * 2 - 1 ; i+=2){
                            int data = (int)(SerialData_Queue[i] << 8);
                            int data2 =  (int)(SerialData_Queue[i + 1]);

                            if(data2 < 0)
                                data2 += 256;

                            MeanGsrValue = MeanGsrValue + (data + data2);

                            FinalData[cnt] = (data + data2);
                            cnt = cnt + 1;

                            bw[counter].write(new String(String.valueOf(data + data2)) + " , ");
                        }

                        MeanGsrValue = MeanGsrValue / Double.parseDouble(selectSampleRate);
                        TestTemp = MeanGsrValue;
                        TempMeanGsrValue = MeanGsrValue;
                        MeanGsrValue = (1024+2*MeanGsrValue)/(512-MeanGsrValue)/10;
                        bw[counter].write(" ]");
                        bw[counter].close();


                        String result = GetDB(Get_Query_Command_GSR,Get_Uri);
                        JSONArray jsonArray = null;

                        int id = 0;

                        try {
                            jsonArray = new JSONArray(result);
                            for(int i = 0; i < jsonArray.length(); i++) {
                                JSONObject jsonData = jsonArray.getJSONObject(i);
                                if(UsrInfo[0].getText().toString().equals(jsonData.getString("name"))){
                                    id = Integer.parseInt(jsonData.getString("id"));
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        //+ "Avg='" + MeanGsrValue + "',"
                        //+ "value" + counter + "='" + Arrays.toString(FinalData) + "'"
                        //+ "value" + counter + "='" + Arrays.toString(FinalData) + "'"
                        GetDB(Update_Command_GSR + "name='" + UsrInfo[0].getText().toString() + "',"
                                + "age='" + UsrInfo[1].getText().toString() + "',"
                                + "birthday='" + UsrInfo[2].getText().toString() + "',"
                                + "height='" + UsrInfo[3].getText().toString() + "',"
                                + "weight='" + UsrInfo[4].getText().toString() + "',"
                                + "time='" + dateformat.format(c.getTime()) + "',"
                                + "samplerate='30',"
                                + "Avg='" + MeanGsrValue + "',"
                                + "value" + counter + "='" + MeanGsrValue + "M',"
                                + "doctor='" + UsrInfo[5].getText().toString()
                                + "' WHERE id=" + id, Insert_Uri);

                        result = GetDB(Get_Query_Command_Guan,Get_Uri);
                        jsonArray = null;

                        id = 0;

                        try {
                            jsonArray = new JSONArray(result);
                            for(int i = 0; i < jsonArray.length(); i++) {
                                JSONObject jsonData = jsonArray.getJSONObject(i);
                                if(UsrInfo[0].getText().toString().equals(jsonData.getString("name"))){
                                    id = Integer.parseInt(jsonData.getString("id"));
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        GetDB(Update_Command_Guan + "name='" + UsrInfo[0].getText().toString() + "',"
                                + "age='" + UsrInfo[1].getText().toString() + "',"
                                + "birthday='" + UsrInfo[2].getText().toString() + "',"
                                + "height='" + UsrInfo[3].getText().toString() + "',"
                                + "weight='" + UsrInfo[4].getText().toString() + "',"
                                + "doctor='" + UsrInfo[5].getText().toString()
                                + "' WHERE id=" + id,Insert_Uri);

                        /*
                        result = GetDB(Get_Query_Command,Get_Uri);
                        jsonArray = null;

                        id = 0;

                        try {
                            jsonArray = new JSONArray(result);
                            for(int i = 0; i < jsonArray.length(); i++) {
                                JSONObject jsonData = jsonArray.getJSONObject(i);
                                if(UsrInfo[0].getText().toString().equals(jsonData.getString("name"))){
                                    id = Integer.parseInt(jsonData.getString("id"));
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        GetDB(Update_Command + "name='" + UsrInfo[0].getText().toString() + "',"
                                + "age='" + UsrInfo[1].getText().toString() + "',"
                                + "birthday='" + UsrInfo[2].getText().toString() + "',"
                                + "height='" + UsrInfo[3].getText().toString() + "',"
                                + "weight='" + UsrInfo[4].getText().toString() + "',"
                                + "doctor='" + UsrInfo[5].getText().toString()
                                + "' WHERE id=" + id,Insert_Uri);
                        */
                        GetDB("UPDATE whichid SET which='" + id + "' WHERE id=1",Insert_Uri);

                        FileFlag = false;

                    }catch(IOException e){
                        e.printStackTrace();
                    }
                }
                mHandler.post(this);
            }
        });
    }
    private void ResetGraph()
    {
        G_Graph.getViewport().setMaxX(5);
        G_Graph.getViewport().setMaxY(1023);
        G_Graph.getViewport().setYAxisBoundsManual(true);

        G_Graph.getViewport().setMinX(0);
        G_Graph.getGridLabelRenderer().setHighlightZeroLines(false);
//        G_Graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.HORIZONTAL);
//        G_Graph.getGridLabelRenderer().setNumVerticalLabels(3);
//        G_Graph.getGridLabelRenderer().setPadding(15);
        G_Graph.getViewport().setXAxisBoundsManual(true);

        G_Graph.getGridLabelRenderer().reloadStyles();

        G_Graph.removeAllSeries();
        G_Series = new LineGraphSeries<DataPoint>();
        G_Graph.addSeries(G_Series);

        Queue_Index_Rear = 0;
        Queue_Index_Front = 0;

        for(int i = 0 ; i < SerialData_Queue.length ; i++)
            SerialData_Queue[i] = 0;

        mXPoint = 0;

        TempSize[0] = 0;
        TempSize[1] = 0;
        SizeIndex = 0;
    }
    private void UpdateAcupointImage(final int state, final int index){
        measureAcupoint_Handler.post(new Runnable() {
            @Override
            public void run() {
                switch(state) {
                    case 1:
                        acupointImage.setBackgroundResource((Integer) imageResource[index]);
                        break;
                    case 2:
                        acupointImage.setBackgroundResource((Integer) imageResource[index]);
                        break;
                    default:
                        break;
                }
            }
        });
    }
//    private void UpdateAcupointImage(){
//
//        measureAcupoint_Handler.post(new Runnable() {
//            @Override
//            public void run() {
//                acupointImage.setBackgroundResource((Integer) imageResource[counter++]);
//                measureAcupoint_Handler.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        byte[] buf = new byte[1];
//
//                        if(selectSampleRate.equals(sampleRate_Item[0]))
//                            buf[0] = 0x30;
//                        else if(selectSampleRate.equals(sampleRate_Item[1]))
//                            buf[0] = 0x31;
//                        else
//                            buf[0] = 0x32;
//
//                        mPhysicaloid.write(buf, buf.length);
//                    }
//                },4000);
//                if(counter == 6) {
//                    counter = 0;
//                    timer_updateImage.cancel();
//                    timer_updateImage = null;
//
//                    timerTask_updateImage.cancel();
//                    timerTask_updateImage = null;
//                }
//            }
//        });
//    }
//    private void DialogShow(final LayoutInflater inflater){
//        TimeDialog_Timer_Handler.post(new Runnable() {
//            @Override
//            public void run() {
//                TimeDialog_Timer = new CountDownTimer(4000,1000){
//                    @Override
//                    public void onTick(final long millisUntilFinished) {
//                        TimeDialog_Timer_Handler.post(new Runnable() {
//                            @Override
//                            public void run() {
//                                TextView tv = TimeDialog.findViewById(R.id.dialog_tv);
//                                tv.setText(new String(String.valueOf(millisUntilFinished / 1000)));
//                            }
//                        });
//                        TimeDialog.show();
//                    }
//                    @Override
//                    public void onFinish() {
//                        TimeDialog.dismiss();
//                    }
//                };
//                TimeDialog_Timer.start();
//            }
//        });
//    }
    private void setEnabledUi(int state){
        if(state == 0){
            startBtn.setEnabled(true);
            nextBtn.setEnabled(true);
            backBtn.setEnabled(true);
            setUsrInfoBtn.setEnabled(true);
            setSampleRateBtn.setEnabled(true);
        }
        else if(state == 1){
            startBtn.setEnabled(false);
            backBtn.setEnabled(false);
            nextBtn.setEnabled(false);
            setSampleRateBtn.setEnabled(false);
            setUsrInfoBtn.setEnabled(true);
        }
        else if(state == 2){
            startBtn.setEnabled(false);
            backBtn.setEnabled(false);
            nextBtn.setEnabled(false);
            setSampleRateBtn.setEnabled(true);
            setUsrInfoBtn.setEnabled(true);
        }
    }
    private void setSerialOpen(final LayoutInflater inflater) {

            Toast.makeText(inflater.getContext(), "open", Toast.LENGTH_SHORT).show();
//            mPhysicaloid.addReadListener(new ReadLisener() {
//                @Override
//                public void onRead(int size) {
//                    //Toast.makeText(inflater.getContext(), "Read", Toast.LENGTH_SHORT).show();
//                    byte[] buf = new byte[size];
//                    mPhysicaloid.read(buf, size);
//                    tvAppend(tvRead, new String(buf));
//                    SetChoice(buf,size,inflater);
//                }
//            });
    }

    private boolean isEmpty()
    {
        return Queue_Index_Front == Queue_Index_Rear;
    }
    private void PushSerialData(byte[] data,int size)
    {
        for(int i = 0 ; i < size ; i++) {
            if(Queue_Index_Front == (SerialDataSize - 1))
                Queue_Index_Front = 0;
            SerialData_Queue[Queue_Index_Rear++] = data[i];
        }
    }
    private byte PopSerialData()
    {
        if(Queue_Index_Front == (SerialDataSize - 1))
            Queue_Index_Front = 0;
        return SerialData_Queue[Queue_Index_Front++];
    }
    private void SetChoice(byte[] buf,int size,LayoutInflater inflater){
//        if(size == 1){
//            if(buf[0] == 'B')
//                BackBtn_Click(inflater);
//            else if(buf[0] == 'S') {
//                ResetGraph();
//                StartBtn_Click(inflater);
//            }
//            else if(buf[0] == 'N')
//                NextBtn_Click(inflater);
//            else {
//                PushSerialData(buf, size);
//                UpdateGraph(size,inflater);
//            }
//        }
//        else {
            PushSerialData(buf,size);
            UpdateGraph(size,inflater);
        //}
    }
    private void StartBtn_Click(LayoutInflater inflater){
        ((Vibrator) inflater.getContext().getSystemService(Service.VIBRATOR_SERVICE)).vibrate(new long[]{0,50}, -1);
        byte[] Data = new byte[1];

        if(selectSampleRate.equals(sampleRate_Item[0]))
            Data[0] = 0x30;
        else if(selectSampleRate.equals(sampleRate_Item[1]))
            Data[0] = 0x31;
        else
            Data[0] = 0x32;

        serialPort.write(Data);
    }
    private void NextBtn_Click(final LayoutInflater inflater){
        ((Vibrator) inflater.getContext().getSystemService(Service.VIBRATOR_SERVICE)).vibrate(new long[]{0,50}, -1);
        // 改成12張圖片
        /*
        if(counter == 11) {
            UpdateAcupointImage(1, counter);
            new AlertDialog.Builder((Activity)inflater.getContext()).setMessage("已量完，已是最後一個量測點" + '\n' + '\n' + "如需量測別的受測者" + '\n' + "請按setUsrInfo更改")
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create()
                    .show();
        }
        else
            UpdateAcupointImage(1,++counter);
        */
        if(counter == 11) {
            UpdateAcupointImage(1, counter);
            new AlertDialog.Builder((Activity)inflater.getContext()).setMessage("已量完畢，" + '\n' + '\n' + "如需量測別的受測者" + '\n' + "請按setUsrInfo更改")
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create()
                    .show();
        }
        else if(counter == 10) {
            UpdateAcupointImage(1, ++counter);
            nextBtn.setText("UPLOAD");
        }
        else {
            UpdateAcupointImage(1, ++counter);
        }
    }
    private void BackBtn_Click(LayoutInflater inflater){
        ((Vibrator) inflater.getContext().getSystemService(Service.VIBRATOR_SERVICE)).vibrate(new long[]{0,50}, -1);
        if(counter == 0)
            UpdateAcupointImage(2,counter);
        else {
            UpdateAcupointImage(2, --counter);
            nextBtn.setText("NEXT");
        }
    }
}