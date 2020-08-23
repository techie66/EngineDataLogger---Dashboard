package net.xtlive.EDL.Dashboard;

import android.annotation.SuppressLint;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.xtlive.EDL.AppBuffer.Bike;

import static android.os.SystemClock.elapsedRealtime;


public class MainActivityViewModel extends ViewModel {
    private static final long BT_TIMEOUT = 2000;
    private String TAG = MainActivityViewModel.class.getSimpleName();
    private BluetoothAdapter mBTAdapter;
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path
    private MutableLiveData<String> mBTStatus;
    private MutableLiveData<Integer> mRPM;
    private MutableLiveData<Float> mSpeed;
    private MutableLiveData<Float> mVoltage;
    private MutableLiveData<Float> mOilTemp;
    private MutableLiveData<Double> modometer;
    private MutableLiveData<Double> mTrip;
    private MutableLiveData<Boolean> mBlinkLeft;
    private MutableLiveData<Boolean> mBlinkRight;
    private MutableLiveData<Float> mLambda;
    private MutableLiveData<String> mGear;
    private MutableLiveData<Float> mOilPres;
    private MutableLiveData<BluetoothDevice> mBLEDevice;
    private MutableLiveData<BluetoothGatt> mBLEGatt;
    private Handler mHandler; // Our main handler that will receive callback notifications

    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data

    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status

    @SuppressLint("HandlerLeak")
    public MainActivityViewModel() {
        super();
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio
        mBTStatus = new MutableLiveData<>();
        mBLEDevice = new MutableLiveData<>();
        mBLEGatt = new MutableLiveData<>();
        mRPM = new MutableLiveData<>();
        mSpeed = new MutableLiveData<>();
        mVoltage = new MutableLiveData<>();
        mOilTemp = new MutableLiveData<>();
        modometer = new MutableLiveData<>();
        mBlinkLeft = new MutableLiveData<>();
        mBlinkRight = new MutableLiveData<>();
        mTrip = new MutableLiveData<>();
        mLambda = new MutableLiveData<>();
        mGear = new MutableLiveData<>();
        mOilPres = new MutableLiveData<>();

        mHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == MESSAGE_READ){
                    Bike bikeObj = null;
                    try {
                        bikeObj = (Bike) msg.obj;
                    } catch (ClassCastException e) {
                        e.printStackTrace();
                    }
                    mRPM.setValue(bikeObj.rpm());
                    mSpeed.setValue(bikeObj.speed());
                    mVoltage.setValue(17-bikeObj.batteryvoltage());
                    mOilTemp.setValue(bikeObj.oilTemp());
                    modometer.setValue(bikeObj.odometer() / 100.0);
                    mBlinkLeft.setValue(bikeObj.blinkLeft());
                    mBlinkRight.setValue(bikeObj.blinkRight());
                    mTrip.setValue((bikeObj.odometer() - bikeObj.trip()) / 100.0);
                    mLambda.setValue((float) (bikeObj.lambda()/1000.0));
                    mGear.setValue(bikeObj.gear());
                    mOilPres.setValue(bikeObj.oilPres());
                }

                if(msg.what == CONNECTING_STATUS){
                    if(msg.arg1 == 1)
                        mBTStatus.setValue("Connected to Device: " + (String)(msg.obj));
                    else
                        mBTStatus.setValue("Connection Failed");
                }
            }
        };
    }

    LiveData<String> getStatus() { return mBTStatus; }
    LiveData<Integer> getRPM() { return mRPM; }
    LiveData<Float> getSpeed() { return mSpeed; }
    LiveData<Float> getVoltage() { return mVoltage; }
    LiveData<Float> getOilTemp() { return mOilTemp; }
    LiveData<Double> getOdometer() { return modometer; }
    LiveData<Boolean> getmBlinkRight() { return mBlinkRight; }
    LiveData<Boolean> getmBlinkLeft() { return mBlinkLeft; }
    LiveData<Double> getmTrip() { return mTrip; }
    LiveData<Float> getLambda() { return mLambda; }
    LiveData<String> getGear() {
        return mGear;
    }
    LiveData<Float> getOilPres() {
        return mOilPres;
    }
    LiveData<BluetoothDevice> getmBLEDevice() { return mBLEDevice;}
    MutableLiveData<BluetoothGatt> getmBLEGatt() {return mBLEGatt;}


    void connectDevice(String info) {
        if(!mBTAdapter.isEnabled()) {
            // TODO Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            return;
        }

        mBTStatus.setValue("Connecting...");
        // Get the device MAC address, which is the last 17 chars in the View
        //String info = ((TextView) v).getText().toString();
        final String address = info.substring(info.length() - 17);
        final String name = info.substring(0,info.length() - 17);

        // Spawn a new thread to avoid blocking the GUI one
        new Thread()
        {
            public void run() {
                boolean fail = false;

                BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                try {
                    mBTSocket = createBluetoothSocket(device);
                } catch (IOException e) {
                    fail = true;
                    // TODO Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                }
                // Establish the Bluetooth socket connection.
                try {
                    mBTSocket.connect();
                } catch (IOException e) {
                    try {
                        fail = true;
                        mBTSocket.close();
                        mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                .sendToTarget();
                    } catch (IOException e2) {
                        //insert code to deal with this
                        //TODO Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                }
                if(!fail) {
                    mConnectedThread = new ConnectedThread(mBTSocket);
                    mConnectedThread.start();

                    mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                           .sendToTarget();
                }
            }
        }.start();
    }

    void sendByte(String c) {
        if(mConnectedThread != null) //First check to make sure thread created
            mConnectedThread.write(c);
    }

    boolean btAvailable() {
        return mBTAdapter != null;
    }
    boolean btEnabled() {
        return mBTAdapter.isEnabled();
    }

    public boolean isConnected() {
        return mBTSocket != null && mBTSocket.isConnected();
    }

    public void resetTrip() {
        if(mConnectedThread != null) //First check to make sure thread created
            mConnectedThread.write("TRPRST");
    }

    public void toggleO2_on() {
        if(mConnectedThread != null) //First check to make sure thread created
            mConnectedThread.write("O2MANON");
    }

    public void toggleO2_off() {
        if(mConnectedThread != null) //First check to make sure thread created
            mConnectedThread.write("O2MANOFF");
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer;  // buffer store for the stream
            int bytes; // bytes returned from read()
            long currentMillis, lastMillis = elapsedRealtime();
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                currentMillis = elapsedRealtime();
                if (lastMillis + BT_TIMEOUT < currentMillis) {
                    cancel();
                }
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if(bytes != 0) {
                        lastMillis = currentMillis;
                        buffer = new byte[1024];
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = Math.min(bytes, 1024);
                        bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                        //Integer myInt = new DataInputStream(new BufferedInputStream(mmInStream)).readInt();
                        //buffer = myInt.toString().getBytes();
                        //bytes = buffer.length;
                        java.nio.ByteBuffer bikebuf = java.nio.ByteBuffer.wrap(buffer);
                        Bike bikeobj = Bike.getRootAsBike(bikebuf);
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, bikeobj)
                                .sendToTarget(); // Send the obtained bytes to the UI activity
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                catch (ArrayIndexOutOfBoundsException e) {
                    e.printStackTrace();
                    break;
                }
                catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocket", new Class[]{int.class});
            return (BluetoothSocket) m.invoke(device, 1);
        }
        catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e);
        }
        return null;
    }

    /* BLE Stuff

     */
    private List<String> BLEEntryList = new ArrayList<String>();
    private List<String> BLEValueList = new ArrayList<String>();
    @RequiresApi(api = Build.VERSION_CODES.M)
    void BLEscan() {
        bluetoothLeScanner = mBTAdapter.getBluetoothLeScanner();
        scanLeDevice();
    }

    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning;
    private Handler handler = new Handler();

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void scanLeDevice () {
        if (!scanning) {
            // Stops scanning after a predefined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            scanning = true;
            UUID BLP_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
            UUID[] serviceUUIDs = new UUID[]{BLP_SERVICE_UUID};
            List<ScanFilter> filters = null;
            if(serviceUUIDs != null) {
                filters = new ArrayList<>();
                for (UUID serviceUUID : serviceUUIDs) {
                    ScanFilter filter = new ScanFilter.Builder()
                            .setServiceUuid(new ParcelUuid(serviceUUID))
                            .build();
                    filters.add(filter);
                }
            }
            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    .setReportDelay(0L)
                    .build();
            bluetoothLeScanner.startScan(filters,scanSettings,leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    // Device scan callback.
    @RequiresApi(api = Build.VERSION_CODES.M)
    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    //result.getDevice();
                    String EntryName = "N/A";
                    if (result.getDevice().getName() != null) {
                        EntryName = result.getDevice().getName();
                    }
                    /*if (!BLEValueList.contains(result.getDevice().getAddress())) {
                        BLEEntryList.add(EntryName);
                        BLEValueList.add(result.getDevice().getAddress());
                        final CharSequence[] entries = BLEEntryList.toArray(new CharSequence[0]);
                        KeylessList.setEntries(entries);
                        final CharSequence[] values = BLEValueList.toArray(new CharSequence[0]);
                        KeylessList.setEntryValues(values);
                    }
                    */
                    //result.getDevice().connectGatt(,false, bluetoothGattCallback);

                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                    mBLEDevice.setValue(result.getDevice());
                }
            };
}

