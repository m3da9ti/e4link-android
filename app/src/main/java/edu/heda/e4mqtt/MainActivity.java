package edu.heda.e4mqtt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.EmpaticaDevice;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;
import com.google.gson.GsonBuilder;

//mqtt Android 12
//import org.eclipse.paho.android.service.MqttAndroidClient;
import info.mqtt.android.service.Ack;
import info.mqtt.android.service.MqttAndroidClient;

import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 1;
    private static final int REQUEST_PERMISSION_ACCESS_BLE = 2;
    // hardcode default API key
    private static final String EMPATICA_API_KEY = "62c769b692aa4c61aa7fc4ec28cdbab4";
    private EmpaDeviceManager deviceManager = null;
    private TextView accel_xLabel;
    private TextView accel_yLabel;
    private TextView accel_zLabel;
    private TextView bvpLabel;
    private TextView edaLabel;
    private TextView ibiLabel;
    private TextView temperatureLabel;
    private TextView batteryLabel;
    private TextView statusLabel;
    private TextView deviceNameLabel;
    private LinearLayout dataCnt;
    private String deviceName;
    private String apiKey;
    private String serverAddress;
    // name of the run/experiment, for now we set it to current ts
    private String run;

    private MqttAndroidClient mqttClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);
        dataCnt = (LinearLayout) findViewById(R.id.dataArea);
        accel_xLabel = (TextView) findViewById(R.id.accel_x);
        accel_yLabel = (TextView) findViewById(R.id.accel_y);
        accel_zLabel = (TextView) findViewById(R.id.accel_z);
        bvpLabel = (TextView) findViewById(R.id.bvp);
        edaLabel = (TextView) findViewById(R.id.eda);
        ibiLabel = (TextView) findViewById(R.id.ibi);
        temperatureLabel = (TextView) findViewById(R.id.temperature);
        batteryLabel = (TextView) findViewById(R.id.battery);
        deviceNameLabel = (TextView) findViewById(R.id.deviceName);

        final Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(v -> {
            if (deviceManager != null) {
                deviceManager.disconnect();
            }
        });

        //setup configure button
        final Button configureButton = findViewById(R.id.configureButton);

        configureButton.setOnClickListener(v -> {
            // custom dialog
            final Dialog dialog = new Dialog(MainActivity.this);
            dialog.setContentView(R.layout.activity_collect_apikey);
            dialog.setTitle("Settings");

            EditText apiKeyInput = (EditText)dialog.findViewById(R.id.api_key_edit);
            EditText serverAddressInput = (EditText)dialog.findViewById(R.id.server_address_edit);
            Button doneButton = (Button) dialog.findViewById(R.id.api_key_submit);
            apiKeyInput.setText(EMPATICA_API_KEY);
            serverAddressInput.setText("192.168.1.4");
            doneButton.setOnClickListener(vv -> {
                apiKey = apiKeyInput.getText().toString();
                serverAddress = serverAddressInput.getText().toString();
                Log.d(TAG, ">>> apiKey:" + apiKey + " server: " + serverAddress);
//                apiInterface = ApiConnector.connect(serverAddress);
                initEmpaticaDeviceManager();

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMddyyyyHHmm")
                        .withZone(ZoneId.systemDefault());
                run = String.format("series-%s", formatter.format(Instant.now()));

                String clientId = MqttClient.generateClientId();
                //Android 12
                //mqttClient = new MqttAndroidClient(this.getApplicationContext(), "tcp://" + serverAddress + ":1883", clientId);
                mqttClient = new MqttAndroidClient(this.getApplicationContext(), "tcp://" + serverAddress + ":1883", clientId, Ack.AUTO_ACK);
                Log.d(TAG, "@@@@@@ Connected to mqtt " + mqttClient);
                mqttConnect();

                dialog.dismiss();
            });
            dialog.show();
        });

        //to avoid popup
        apiKey = EMPATICA_API_KEY;
        initEmpaticaDeviceManager();
    }

    private void mqttConnect() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        //android 12 mqtt
//        try {
            mqttClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttClient.setBufferOpts(disconnectedBufferOptions);

                    Log.e(TAG, "@@@@ >>>>  Successfully connect to mqtt server at: " + serverAddress);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                    Log.e(TAG, "@@@@ Failed to connect to mqtt server at: " + serverAddress, e);
                }
            });

// android 12 mqtt
//        } catch (MqttException ex){
//            ex.printStackTrace();
//        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_ACCESS_COARSE_LOCATION) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted, yay!
                initEmpaticaDeviceManager();
            } else {
                // Permission denied, boo!
                final boolean needRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION);
                new AlertDialog.Builder(this)
                        .setTitle("Permission required")
                        .setMessage("Without this permission bluetooth low energy devices cannot be found, allow it in order to connect to the device.")
                        .setPositiveButton("Retry", (dialog, which) -> {
                            // try again
                            if (needRationale) {
                                // the "never ask again" flash is not set, try again with permission request
                                initEmpaticaDeviceManager();
                            } else {
                                // the "never ask again" flag is set so the permission requests is disabled, try open app settings to enable the permission
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Exit application", (dialog, which) -> {
                            // without permission exit is the only way
                            finish();
                        })
                        .show();
            }
        }
    }

    private void initEmpaticaDeviceManager() {

//        if (Build.VERSION.SDK_INT >= 31) {
            // Android 12 (S)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Need permissions...");
                this.requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_PERMISSION_ACCESS_BLE);
            } else {
                // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
                deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);

                // Initialize the Device Manager using your API key. You need to have Internet access at this point.
                deviceManager.authenticateWithAPIKey(apiKey);
            }
//        }
//        } else {
//            // Android 6 (API level 23) now require ACCESS_COARSE_LOCATION permission to use BLE
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
//            } else {
//                // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
//                deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);
//
//                // Initialize the Device Manager using your API key. You need to have Internet access at this point.
//                deviceManager.authenticateWithAPIKey(apiKey);
//            }
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceManager != null) {
            deviceManager.cleanUp();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (deviceManager != null) {
            deviceManager.stopScanning();
        }
    }

    @Override
    public void didDiscoverDevice(EmpaticaDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php
        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);
                // Store device name
                this.deviceName = deviceName;
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed + " - ConnectionNotAllowedException", e);
            }
        }
    }

    @Override
    public void didFailedScanning(int errorCode) {
        /*
         A system error occurred while scanning.
         @see https://developer.android.com/reference/android/bluetooth/le/ScanCallback
        */
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                Log.e(TAG,"Scan failed: a BLE scan with the same settings is already started by the app");
                break;
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                Log.e(TAG,"Scan failed: app cannot be registered");
                break;
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                Log.e(TAG,"Scan failed: power optimized scan feature is not supported");
                break;
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                Log.e(TAG,"Scan failed: internal error");
                break;
            default:
                Log.e(TAG,"Scan failed with unknown error (errorCode=" + errorCode + ")");
                break;
        }
    }

    @Override
    public void didRequestEnableBluetooth() {
        // Request the user to enable Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        try {
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } catch(SecurityException e){
            Log.e(TAG, "Exception:" + e);
        }
    }

    @Override
    public void bluetoothStateChanged() {
        // E4link detected a bluetooth adapter change
        // Check bluetooth adapter and update your UI accordingly.
        boolean isBluetoothOn = BluetoothAdapter.getDefaultAdapter().isEnabled();
        Log.i(TAG, "Bluetooth State Changed: " + isBluetoothOn);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user chose not to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // You should deal with this
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {
        didUpdateOnWristStatus(status);
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        // Update the UI
        updateLabel(statusLabel, status.name());

        // The device manager is ready for use
        if (status == EmpaStatus.READY) {
            updateLabel(statusLabel, status.name() + " - Turn on your device");
            // Start scanning
            try {
                deviceManager.startScanning();
            } catch (NullPointerException ex) {
                Log.e(TAG, "Empatica internally did not initialize");
            }
            // The device manager has established a connection
            hide();
        } else if (status == EmpaStatus.CONNECTED) {
            show();
            // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {
            updateLabel(deviceNameLabel, "");
            hide();
        }
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        final String data = String.format("%.0f %%", battery * 100);
        updateLabel(batteryLabel, data);
    }

    private void sendDataToServer(EventData event) {
        Log.d(TAG, "Sending " + event);

        String message = new GsonBuilder().create().toJson(event, EventData.class);
//        try{
            mqttClient.publish("e4", message.getBytes(), 0, false);
//        }catch (MqttException e){
//            e.printStackTrace();
//        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        updateLabel(accel_xLabel, "" + x);
        updateLabel(accel_yLabel, "" + y);
        updateLabel(accel_zLabel, "" + z);

        Log.d(TAG, "Calling server with acc:");
        EventData event = new EventData();
        event.device = this.deviceName;
        event.run = this.run;
        event.type = "acc";
        event.timestamp = new Double(timestamp*1000).longValue();
        event.x = x;
        event.y = y;
        event.z = z;
        sendDataToServer(event);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        updateLabel(bvpLabel, "" + bvp);

        Log.d(TAG, "Calling server with bvp:");
        EventData event = new EventData();
        event.device = this.deviceName;
        event.run = this.run;
        event.timestamp = new Double(timestamp*1000).longValue();
        event.value = bvp;
        event.type = "bvp";
        sendDataToServer(event);
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        //EDI == GSR
        updateLabel(edaLabel, "" + gsr);

        Log.d(TAG, "Calling server with gsr:" + gsr + ":" + timestamp);
        EventData event = new EventData();
        event.device = this.deviceName;
        event.run = this.run;
        event.timestamp = new Double(timestamp*1000).longValue();
        event.value = gsr;
        event.type = "gsr";
        sendDataToServer(event);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        updateLabel(ibiLabel, "" + ibi);

//        Log.d(TAG, "Calling server with ibi");
        EventData event = new EventData();
        event.device = this.deviceName;
        event.run = this.run;
        event.timestamp = new Double(timestamp*1000).longValue();
        event.value = ibi;
        event.type = "ibi";
        sendDataToServer(event);
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        updateLabel(temperatureLabel, "" + temp);

//        Log.d(TAG, "Calling server with temp");
        EventData event = new EventData();
        event.device = this.deviceName;
        event.run = this.run;
        event.value = temp;
        event.timestamp = new Double(timestamp*1000).longValue();
        event.type = "temp";
        sendDataToServer(event);
    }

    // Update a label with some text, making sure this is run in the UI thread
    private void updateLabel(final TextView label, final String text) {
        runOnUiThread(() -> label.setText(text));
    }

    @Override
    public void didReceiveTag(double timestamp) {
//        Log.d(TAG, "Calling server with tag:");
        EventData event = new EventData();
        event.device = this.deviceName;
        event.run = this.run;
        event.timestamp = new Double(timestamp*1000).longValue();
        event.value = timestamp;
        event.type = "tag";
//        sendDataToServer(event);
    }

    @Override
    public void didEstablishConnection() {
        show();
    }

    @Override
    public void didUpdateOnWristStatus(@EmpaSensorStatus final int status) {
        runOnUiThread(() -> {
            if (status == EmpaSensorStatus.ON_WRIST) {
                ((TextView) findViewById(R.id.wrist_status_label)).setText("ON WRIST");
            } else {
                ((TextView) findViewById(R.id.wrist_status_label)).setText("NOT ON WRIST");
            }
        });
    }

    void show() {
        runOnUiThread(() -> dataCnt.setVisibility(View.VISIBLE));
    }

    void hide() {
        runOnUiThread(() -> dataCnt.setVisibility(View.INVISIBLE));
    }
}
