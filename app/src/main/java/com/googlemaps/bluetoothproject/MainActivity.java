package com.googlemaps.bluetoothproject;

/*
Android Example to connect to and communicate with Bluetooth
In this exercise, the target is a Arduino Due + HC-06 (Bluetooth Module)

Ref:
- Make BlueTooth connection between Android devices
http://android-er.blogspot.com/2014/12/make-bluetooth-connection-between.html
- Bluetooth communication between Android devices
http://android-er.blogspot.com/2014/12/bluetooth-communication-between-android.html
 */
// 블루투스 지원 환경 확인 -> 연결된 블루투스 장치 정보 확인 -> 장치 선택 시 소켓 연결 -> 데이터 소켓으로 input, output가능
// 장치 선택 단계는 device 변수에 기기정보를 지정해줌으로써 생략가능
        import android.Manifest;
        import android.annotation.TargetApi;
        import android.app.Activity;
        import android.bluetooth.BluetoothAdapter;
        import android.bluetooth.BluetoothDevice;
        import android.bluetooth.BluetoothSocket;
        import android.content.Intent;
        import android.content.pm.PackageManager;
        import android.os.Build;
        import android.os.Bundle;
        import android.support.v4.app.ActivityCompat;
        //import android.support.v7.app.ActionBarActivity;
        import android.support.v4.content.ContextCompat;
        import android.view.View;
        import android.widget.AdapterView;
        import android.widget.ArrayAdapter;
        import android.widget.Button;
        import android.widget.EditText;
        import android.widget.LinearLayout;
        import android.widget.ListView;
        import android.widget.TextView;
        import android.widget.Toast;

        import java.io.IOException;
        import java.io.InputStream;
        import java.io.OutputStream;
        import java.util.ArrayList;
        import java.util.Set;
        import java.util.UUID;

public class MainActivity extends Activity {
    //지역변수를 상수화 시켜서 다른 클래스에서도 사용이 가능하게함
    private static final int REQUEST_ENABLE_BT = 1;


    /*
    BluetoothAdapter는 연결, 페어링, 검색 등 Bluetooth 연동을 위한 기본 클래스입니다.
    초기화 했을 때 인스턴스가 null 이면 Bluetooth를 지원하지 않는 핸드폰으로 연동이 불가능 합니다.

    출처: http://dev2.prompt.co.kr/59 [프람트 MoS 사업부]
*/
    BluetoothAdapter bluetoothAdapter;

    /* 블루투스장치 정보들을 ArrayList로 저장
        벡터 추가시 .add(), 제거시 .remove(), 벡터 정보를 얻고 싶을때는 .get()
       참조: http://apphappy.tistory.com/81
    */
    ArrayList<BluetoothDevice> pairedDeviceArrayList;


    //Layout Components
    TextView textInfo, textStatus, textByteCnt;
    ListView listViewPairedDevice;
    LinearLayout inputPane;
    EditText inputField;
    Button btnSend, btnClear;



    /*
     카카오톡, 주소록, 음악재생 어플 등에서 연락처나, 노래 항목들이 리스트 형식으로 나타나있는 것을 많이 본 적이 있을 것이다.
     이와 같이 나타내기 위해선 layout의 xml파일에 있는 ListView를 이용해야 된다.
     그리고 Adapter를 생성하여 이 ListView와 연결시켜야 하는데, Adapter는 한 마디로 리스트 객체와 ListView의 연결고리라고 생각하면 된다.
     리스트 객체 안에 저장된 데이터들을 우리가 볼 수 있게 ListView로 뿌려주는 역할을 한다.
     또 LIstView에서 리스트 안의 데이터를 어떤 형식으로 표현한 것인지도 결정해준다.

     */
    ArrayAdapter<BluetoothDevice> pairedDeviceAdapter;



    // 시리얼 통신을 위한 블루투스 UUID = 00001101-0000-1000-8000-00805F9B34FB
    // 그 외의 UUID 참조 http://dsnight.tistory.com/13
    private UUID myUUID;
    private final String UUID_STRING_WELL_KNOWN_SPP =
            "00001101-0000-1000-8000-00805F9B34FB";



    ThreadConnectBTdevice myThreadConnectBTdevice;
    ThreadConnected myThreadConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 참조: http://blog.dramancompany.com/2015/11/%EB%A6%AC%EB%A9%A4%EB%B2%84%EC%9D%98-%EC%95%88%EB%93%9C%EB%A1%9C%EC%9D%B4%EB%93%9C-6-0-m%EB%B2%84%EC%A0%84-%EB%8C%80%EC%9D%91%EA%B8%B0/
        // API 23(Marshmallow)이상일 경우, 위치정보,파일 읽기 쓰기, 다른 앱 위에 그리기 등에 대한 권한을 동의하는지 사용자에게 물어봐야된다.
        if(Build.VERSION.SDK_INT == Build.VERSION_CODES.M)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        //Components
        textInfo = (TextView)findViewById(R.id.info);
        textStatus = (TextView)findViewById(R.id.status);
        textByteCnt = (TextView)findViewById(R.id.textbyteCnt);
        listViewPairedDevice = (ListView)findViewById(R.id.pairedlist);
        inputPane = (LinearLayout)findViewById(R.id.inputpane);
        inputField = (EditText)findViewById(R.id.input);
        btnSend = (Button)findViewById(R.id.send);


        //버튼 클릭시 연결된 블루투스가 있을 경우, 입력한 정보를 byte형태로 넘겨준다.
        btnSend.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                if(myThreadConnected!=null){
                    byte[] bytesToSend = inputField.getText().toString().getBytes();
                    myThreadConnected.write(bytesToSend);
                    byte[] NewLine = "\n".getBytes();
                    myThreadConnected.write(NewLine);
                }
            }});

        btnClear = (Button)findViewById(R.id.clear);
        //clear
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textStatus.setText("");
                textByteCnt.setText("");
            }
        });


        //해당 api가 bluetooth 기능을 지원해주는지
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)){
            Toast.makeText(this,
                    "FEATURE_BLUETOOTH NOT support",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        //using the well-known SPP UUID(시리얼 통신을 위한 uuio)
        myUUID = UUID.fromString(UUID_STRING_WELL_KNOWN_SPP);

        //해당 기기가 블루투스를 지원해주는 지
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this,
                    "Bluetooth is not supported on this hardware platform",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        //핸드폰 기기가 블루투스를 지원해주면 text에 핸드폰 기기정보를 출력해줌
        String stInfo = bluetoothAdapter.getName() + "\n" +
                bluetoothAdapter.getAddress();
        textInfo.setText(stInfo);
    }

    @Override
    protected void onStart() {
        super.onStart();

        //Turn ON BlueTooth if it is OFF
        //액티비티가 시작됬을 때, bluetoothAdapter가 off일 경우, bluetooth를 On시켜줌 (Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);)
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        //액티비티가 On되었을 경우, setup()함수를 호출하여 블루투스 기기정보를 뿌려준 뒤에, 필요한 기기에 블루투스 연결을 도와준다.
        setup();
    }

    private void setup() {
        // 핸드폰과 연결된 기기들에 대한 정보를 BluetoothDevice 라이브러리의 생성자 형태로 저장시켜준다.
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            pairedDeviceArrayList = new ArrayList<BluetoothDevice>();

            // 해당 기기정보를 listView에 추가시켜준다.
            for (BluetoothDevice device : pairedDevices) {
                //if(device.getAddress().equals( "지정할 장치 주소")){
                pairedDeviceArrayList.add(device);
                //만약 장치의 주소를 DB에 저장해 두었다면
                //myThreadConnectBTdevice = new ThreadConnectBTdevice(device);
                //myThreadConnectBTdevice.start();
                // }
                //로 장치를 바로 연결 할 수 있다.
            }


            // 연결된 블루투스 기기의 정보들을 레이아웃의 listView로 적용시킨다. (setAdapter 기능)
            pairedDeviceAdapter = new ArrayAdapter<BluetoothDevice>(this,
                    android.R.layout.simple_list_item_1, pairedDeviceArrayList);
            listViewPairedDevice.setAdapter(pairedDeviceAdapter);


            // 원하는 블루투스 기기를 클릭하면
            listViewPairedDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                @Override
                public void onItemClick(AdapterView<?> parent, View view,
                                        int position, long id) {


            // 클릭한 position에 따른 기기의 정보를 device 변수에 저장한다.(position은 순서에 따라서 0부터 시작하는 index값이라고 생각하면 된다.
                    BluetoothDevice device =
                            (BluetoothDevice) parent.getItemAtPosition(position);
                    Toast.makeText(MainActivity.this,
                            "Name: " + device.getName() + "\n"
                                    + "Address: " + device.getAddress() + "\n"
                                    + "BondState: " + device.getBondState() + "\n"
                                    + "BluetoothClass: " + device.getBluetoothClass() + "\n"
                                    + "Class: " + device.getClass(),
                            Toast.LENGTH_LONG).show();

                    textStatus.setText("start ThreadConnectBTdevice");


             //ThreadConnectBTdevice 기능을 통해서 클릭한 해당 기기를 페어링 시켜준다.
                    myThreadConnectBTdevice = new ThreadConnectBTdevice(device);
                    myThreadConnectBTdevice.start();
                }
            });
        }
    }



    // 페어링시킨 기기를 없애고 싶은 경우
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(myThreadConnectBTdevice!=null){
            myThreadConnectBTdevice.cancel();
        }
    }


    //setResultActivity로 블루투스가 연결되었다는걸 보내주면, setup함수 호출
    //아닐 경우 activity 종료
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode==REQUEST_ENABLE_BT){
            if(resultCode == Activity.RESULT_OK){
                setup();
            }else{
                Toast.makeText(this,
                        "BlueTooth NOT enabled",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    //Called in ThreadConnectBTdevice once connect successed
    //to start ThreadConnected
    private void startThreadConnected(BluetoothSocket socket){

        myThreadConnected = new ThreadConnected(socket);
        myThreadConnected.start();
    }

    /*
    ThreadConnectBTdevice:
    Background Thread to handle BlueTooth connecting
    */
    private class ThreadConnectBTdevice extends Thread {

        private BluetoothSocket bluetoothSocket = null;
        private final BluetoothDevice bluetoothDevice;


        private ThreadConnectBTdevice(BluetoothDevice device) {
            bluetoothDevice = device;

            try {
                /*
                BluetoothDevice를 사용해서 createRfcommSocketToServiceRecord(UUID)를 호출해서 BluetoothSocket을 얻는다.
                이 호출은 BluetoothDevice에 연결하는 BluetoothSocket을 초기화한다.
                 */
                bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID);
                textStatus.setText("bluetoothSocket: \n" + bluetoothSocket);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                //블루투스에 사용할 소켓 연결
                bluetoothSocket.connect();
                success = true;
            } catch (IOException e) {
                e.printStackTrace();

                final String eMessage = e.getMessage();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        textStatus.setText("something wrong bluetoothSocket.connect(): \n" + eMessage);
                    }
                });

                try {
                    // 오류가 생길 경우 소켓을 닫는다.
                    bluetoothSocket.close();
                } catch (IOException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }

            if(success){
                //connect successful
                final String msgconnected = "connect successful:\n"
                        + "BluetoothSocket: " + bluetoothSocket + "\n"
                        + "BluetoothDevice: " + bluetoothDevice;

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        textStatus.setText("");
                        textByteCnt.setText("");
                        Toast.makeText(MainActivity.this, msgconnected, Toast.LENGTH_LONG).show();


                        // listView를 숨긴다
                        // 참조 : http://arabiannight.tistory.com/entry/339
                        listViewPairedDevice.setVisibility(View.GONE);
                        inputPane.setVisibility(View.VISIBLE);
                    }
                });

                // 블루투스와 소켓연결
                startThreadConnected(bluetoothSocket);

            }else{
                //fail
            }
        }

        public void cancel() {

            Toast.makeText(getApplicationContext(),
                    "close bluetoothSocket",
                    Toast.LENGTH_LONG).show();

            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

    }

    /*
    ThreadConnected:
    Background Thread to handle Bluetooth data communication
    after connected
     */
    //
    private class ThreadConnected extends Thread {
        private final BluetoothSocket connectedBluetoothSocket;
        //입력 받은 데이터
        private final InputStream connectedInputStream;
        //출력 받은 데이터
        private final OutputStream connectedOutputStream;

        public ThreadConnected(BluetoothSocket socket) {
            connectedBluetoothSocket = socket;
            InputStream in = null;
            OutputStream out = null;

            try {
                // input, output 데이터 받아오기
                in = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            // 전역변수에 inuput, output데이터 저장
            connectedInputStream = in;
            connectedOutputStream = out;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            String strRx = "";

            while (true) {
                try {
                    // buffer 변수안에 inputstream을 받아옴
                    // bytes 변수안에 배열의 길이를 받아옴
                    bytes = connectedInputStream.read(buffer);
                    // string 변수로 인덱스 0부터 끝 인덱스까지의 byte배열의 문자를 모아서 string으로 반환
                    final String strReceived = new String(buffer, 0, bytes);
                    final String strByteCnt = String.valueOf(bytes) + " bytes received.\n";

                    runOnUiThread(new Runnable(){

                        @Override
                        public void run() {
                            textStatus.append(strReceived);
                            textByteCnt.append(strByteCnt);
                        }});

                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();

                    final String msgConnectionLost = "Connection lost:\n"
                            + e.getMessage();
                    runOnUiThread(new Runnable(){

                        @Override
                        public void run() {
                            textStatus.setText(msgConnectionLost);
                        }});
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                // outputstream에 buffer를 쓴다
                connectedOutputStream.write(buffer);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public void cancel() {
            try {
                connectedBluetoothSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

}
