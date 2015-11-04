package com.eeg.aceapplication;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ace.project.complex.Complex;
import com.ace.project.signalprocess.eeg.concentration.Concentration;
import com.ace.project.signalprocess.eeg.power.EegPower;
import com.ace.project.signalprocess.filter.Window;
import com.ace.project.signalprocess.transform.FFT;
import com.interaxon.libmuse.Battery;
import com.interaxon.libmuse.ConnectionState;
import com.interaxon.libmuse.Eeg;
import com.interaxon.libmuse.LibMuseVersion;
import com.interaxon.libmuse.Muse;
import com.interaxon.libmuse.MuseArtifactPacket;
import com.interaxon.libmuse.MuseConnectionListener;
import com.interaxon.libmuse.MuseConnectionPacket;
import com.interaxon.libmuse.MuseDataListener;
import com.interaxon.libmuse.MuseDataPacket;
import com.interaxon.libmuse.MuseDataPacketType;
import com.interaxon.libmuse.MuseFileWriter;
import com.interaxon.libmuse.MuseFileWriterFactory;
import com.interaxon.libmuse.MuseManager;
import com.interaxon.libmuse.MusePreset;
import com.interaxon.libmuse.MuseVersion;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;


/**
 * SAMPLING RATE
 * EEG : 220Hz
 * Accelerometer : 50Hz
 */

public class MainActivity extends Activity implements View.OnClickListener {
    /**
     * Connection listener updates UI with new connection status
     */
    class ConnectionListener extends MuseConnectionListener {

        final WeakReference<Activity> activityRef;

        ConnectionListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(MuseConnectionPacket p) {
            final ConnectionState current = p.getCurrentConnectionState();
            final String status = p.getPreviousConnectionState().toString() +
                    " -> " + current;
            final String full = "Muse " + p.getSource().getMacAddress() +
                    " " + status;
            Log.i("Muse Headband", full);
            Activity activity = activityRef.get();

            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView statusText =
                                (TextView) findViewById(R.id.con_status);
                        statusText.setText(status);
                        TextView museVersionText =
                                (TextView) findViewById(R.id.version);
                        if (current == ConnectionState.CONNECTED) {
                            MuseVersion museVersion = muse.getMuseVersion();
                            String version = museVersion.getFirmwareType() +
                                    " - " + museVersion.getFirmwareVersion() +
                                    " - " + Integer.toString(
                                    museVersion.getProtocolVersion());
                            museVersionText.setText(version);
                        } else {
                            museVersionText.setText(R.string.undefined);
                        }
                    }
                });
            }
        }
    }

    class DataListener extends MuseDataListener {

        final WeakReference<Activity> activityRef;
        private MuseFileWriter fileWriter;

        DataListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseDataPacket(MuseDataPacket p) {
            switch (p.getPacketType()) {
                case EEG:
                    if(appStatus == AppStatus.READING) {
                        getEeg(p.getValues());
                    }
                    else if(appStatus == AppStatus.PLAYING) {

                    }
                    break;
                case BATTERY:
                    updateBattery(p.getValues());
                    fileWriter.addDataPacket(1, p);

                    if (fileWriter.getBufferedMessagesSize() > 8096)
                        fileWriter.flush();
                    break;
                case HORSESHOE:
                    updateHorseshoe(p.getValues());
                default:
                    break;
            }
        }

        @Override
        public void receiveMuseArtifactPacket(MuseArtifactPacket p) {
            if (p.getHeadbandOn() && p.getBlink()) {
                Log.i("Artifacts", "blink");
            }
        }

        private void getEeg(final ArrayList<Double> data) {
            queueFP1.add(data.get(Eeg.FP1.ordinal()));
            queueFP2.add(data.get(Eeg.FP2.ordinal()));
            numOfQueueData++;
        }

        private void updateEeg(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {

                final Handler handlerEEG = new Handler();
                new Thread(new Runnable() {
                    @Override
                    public void run() {


                        if (eegIdx < SAMPLERATE_EEG - 1) {
                            double leftForehead = data.get(Eeg.FP1.ordinal());
                            double rightForehead = data.get(Eeg.FP2.ordinal());

                            eegDataFP1[eegIdx] = leftForehead;
                            eegDataFP2[eegIdx] = rightForehead;

                            eegIdx++;
                        }

                        else if (eegIdx == SAMPLERATE_EEG - 1) {

                            eegFFT = FFT.fft(Window.HammingWindow220(eegDataFP1), false);

                            power1 = EegPower.calcTheta(eegFFT, SAMPLERATE_EEG);
                            power2 = EegPower.calcAlpha(eegFFT, SAMPLERATE_EEG);
                            power3 = EegPower.calcBeta(eegFFT, SAMPLERATE_EEG);
                            power4 = EegPower.calcGamma(eegFFT, SAMPLERATE_EEG);
                            concentration = Concentration.calcConcentration(eegFFT, SAMPLERATE_EEG);


                            eegIdx = 0;
                            lastX++;

                            if(btService.getState() == BluetoothService.STATE_CONNECTED) {
                                byte[] ch = new byte[]{(byte)0x0A, (byte)0xAB};
                                btService.write(ch);
                            }

                            handlerEEG.post(new Runnable() {
                                @Override
                                public void run() {
                                    TextView theta = (TextView) findViewById(R.id.theta);
                                    TextView alpha = (TextView) findViewById(R.id.alpha);
                                    TextView beta = (TextView) findViewById(R.id.beta);
                                    TextView gamma = (TextView) findViewById(R.id.gamma);


                                    theta.setText(String.format(
                                            "%6.2f", power1));
                                    alpha.setText(String.format(
                                            "%6.2f", power2));
                                    beta.setText(String.format(
                                            "%6.2f", power3));
                                    gamma.setText(String.format(
                                            "%6.2f", power4));

                                    addEntry(seriesTheta, power1); // draw graph
                                    addEntry(seriesAlpha, power2);
                                    addEntry(seriesBeta, power3);
                                    addEntry(seriesGamma, power4);
                                    addEntry(seriesConcentration, 50 * concentration);
                                }

                            });
                        }
                    }
                }).start();

                /*
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        TextView theta = (TextView) findViewById(R.id.theta);
                        TextView alpha = (TextView) findViewById(R.id.alpha);
                        TextView beta = (TextView) findViewById(R.id.beta);
                        TextView gamma = (TextView) findViewById(R.id.gamma);

                        if (eegIdx < SAMPLERATE_EEG - 1) {

                            double leftEar = data.get(Eeg.TP9.ordinal());
                            double rightEar = data.get(Eeg.TP10.ordinal());
                            double leftForehead = data.get(Eeg.FP1.ordinal());
                            double rightForehead = data.get(Eeg.FP2.ordinal());

                            eegData1[eegIdx] = leftForehead;
                            eegData2[eegIdx] = rightForehead;
                            eegData3[eegIdx] = leftEar;
                            eegData4[eegIdx] = rightEar;

                            eegIdx++;
                        }

                        if (eegIdx == SAMPLERATE_EEG - 1) {

                            eegFFT = FFT.fft(Window.HammingWindow220Hz(eegData1), false);

                            power1 = EegPower.calcTheta(eegFFT, SAMPLERATE_EEG);
                            power2 = EegPower.calcAlpha(eegFFT, SAMPLERATE_EEG);
                            power3 = EegPower.calcBeta(eegFFT, SAMPLERATE_EEG);
                            power4 = EegPower.calcGamma(eegFFT, SAMPLERATE_EEG);
                            concentration = Concentration.calcConcentration(eegFFT, SAMPLERATE_EEG);


                            theta.setText(String.format(
                                    "%6.2f", power1));
                            alpha.setText(String.format(
                                    "%6.2f", power2));
                            beta.setText(String.format(
                                    "%6.2f", power3));
                            gamma.setText(String.format(
                                    "%6.2f", power4));

                            addEntry(seriesTheta, power1); // draw graph
                            addEntry(seriesAlpha, power2);
                            addEntry(seriesBeta, power3);
                            addEntry(seriesGamma, power4);
                            addEntry(seriesConcentration, 50 * concentration);

                            eegIdx = 0;
                            lastX++;

                            if(btService.getState() == BluetoothService.STATE_CONNECTED)
                            {
                                byte[] ch = new byte[]{(byte)0x0A, (byte)0xAB};
                                btService.write(ch);
                            }
                        }
                    }
                });*/

            }
        }

        private void updateBattery(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView batterybox = (TextView) findViewById(R.id.con_battery);
                        batterybox.setText(String.format("%6.2f", data.get(Battery
                                .CHARGE_PERCENTAGE_REMAINING
                                .ordinal())) + "%");

                    }
                });
            }
        }

        private void updateHorseshoe(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView horseshoe0 = (TextView) findViewById(R.id.horseshoe0);
                        TextView horseshoe1 = (TextView) findViewById(R.id.horseshoe1);
                        TextView horseshoe2 = (TextView) findViewById(R.id.horseshoe2);
                        TextView horseshoe3 = (TextView) findViewById(R.id.horseshoe3);

                        if (data.get(Eeg.TP9.ordinal()) <= 3.0)
                            horseshoe0.setText("FIT");
                        else
                            horseshoe0.setText("X");

                        if (data.get(Eeg.FP1.ordinal()) <= 3.0)
                            horseshoe1.setText("FIT");
                        else
                            horseshoe1.setText("X");

                        if (data.get(Eeg.FP2.ordinal()) <= 3.0)
                            horseshoe2.setText("FIT");
                        else
                            horseshoe2.setText("X");

                        if (data.get(Eeg.TP10.ordinal()) <= 3.0)
                            horseshoe3.setText("FIT");
                        else horseshoe3.setText("X");

                    }
                });
            }
        }

        public void setFileWriter(MuseFileWriter fileWriter) {
            this.fileWriter = fileWriter;
        }
    }

    private static final String TAG = "Main";

    // Basic
    private Muse muse = null;
    private ConnectionListener connectionListener = null;
    private DataListener dataListener = null;
    private boolean dataTransmission = true;
    private MuseFileWriter fileWriter = null;

    // Bluetooth
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private BluetoothService btService = null;
    private BluetoothAdapter mBluetoothAdapter = null;



    private enum AppStatus {
        STOP, READING, CALCULATING, MEASURED, PLAYING
    }
    AppStatus appStatus;


    // EEG power
    private static final int SAMPLERATE_EEG = 220;
    private int eegIdx = 0;
    double power1, power2, power3, power4, concentration;

    private Queue<Double> queueFP1 = new LinkedList<Double>();
    private Queue<Double> queueFP2 = new LinkedList<Double>();
    private int numOfQueueData = 0;

    private double[] eegDataFP1 = new double[SAMPLERATE_EEG * 60];
    private double[] eegDataFP2 = new double[SAMPLERATE_EEG * 60];
    private double result = 0;
    private int numOfMeasuredData = 0;
    private Complex[] eegFFT;

    private double measured_f1 = 0;
    private double measured_f2 = 0;
    private double measured_f3 = 0;
    private double measured_f4 = 0;


    // Graph
    private LineGraphSeries<DataPoint> seriesTheta;
    private LineGraphSeries<DataPoint> seriesAlpha;
    private LineGraphSeries<DataPoint> seriesBeta;
    private LineGraphSeries<DataPoint> seriesGamma;
    private LineGraphSeries<DataPoint> seriesConcentration;
    private LineGraphSeries<DataPoint> seriesFreq;

    private int lastX = 0;
    private int graphLastXValue = 5;
    GraphView graph;
    Viewport viewport;



    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }

    };


    public MainActivity() {
        // Create listeners and pass reference to activity to them
        WeakReference<Activity> weakActivity =
                new WeakReference<Activity>(this);

        connectionListener = new ConnectionListener(weakActivity);
        dataListener = new DataListener(weakActivity);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button refreshButton = (Button) findViewById(R.id.refresh);
        refreshButton.setOnClickListener(this);
        Button connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(this);
        Button disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(this);
        Button pauseButton = (Button) findViewById(R.id.pause);
        pauseButton.setOnClickListener(this);

        Button measureStartButton = (Button) findViewById(R.id.measure_start);
        measureStartButton.setOnClickListener(this);
        Button gameStartButton = (Button) findViewById(R.id.game_start);
        gameStartButton.setOnClickListener(this);
        Button gameStopButton = (Button) findViewById(R.id.game_stop);
        gameStopButton.setOnClickListener(this);

        CheckBox check0 = (CheckBox) findViewById(R.id.check0);
        check0.setOnClickListener(this);


        if(btService == null) {
            btService = new BluetoothService(this, mHandler);
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        fileWriter = MuseFileWriterFactory.getMuseFileWriter(new File(
                getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "testlibmusefile.muse"));
        Log.i("Muse Headband", "libmuse version=" + LibMuseVersion.SDK_VERSION);
        fileWriter.addAnnotationString(1, "MainActivity onCreate");
        dataListener.setFileWriter(fileWriter);


        appStatus = AppStatus.STOP;

        graph = (GraphView) findViewById(R.id.graph);

        viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setScrollable(false);
        viewport.setMinX(0);
        viewport.setMaxY(100);
        viewport.setMinY(0);
        viewport.setXAxisBoundsManual(true);
        viewport.setMaxX(40);
        viewport.setMinX(0);

        seriesTheta = new LineGraphSeries<DataPoint>(new DataPoint[]{});
        seriesAlpha = new LineGraphSeries<DataPoint>(new DataPoint[]{});
        seriesBeta = new LineGraphSeries<DataPoint>(new DataPoint[]{});
        seriesGamma = new LineGraphSeries<DataPoint>(new DataPoint[]{});
        seriesConcentration = new LineGraphSeries<DataPoint>(new DataPoint[]{});
        seriesFreq = new LineGraphSeries<DataPoint>(new DataPoint[]{});


        graph.addSeries(seriesTheta);
        graph.addSeries(seriesAlpha);
        graph.addSeries(seriesBeta);
        graph.addSeries(seriesGamma);
        graph.addSeries(seriesConcentration);
        seriesTheta.setColor(Color.parseColor("#b98e8e"));
        seriesAlpha.setColor(Color.parseColor("#0024ff"));
        seriesBeta.setColor(Color.parseColor("#00ff0c"));
        seriesGamma.setColor(Color.parseColor("#8400ff"));
        seriesConcentration.setColor(Color.parseColor("#ff0000"));


        seriesTheta.setTitle("Theta");
        seriesAlpha.setTitle("Alpha");
        seriesBeta.setTitle("Beta");
        seriesGamma.setTitle("Gamma");
        seriesConcentration.setTitle("Concentration");

        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

    }

    private void addEntry(LineGraphSeries<DataPoint> series, double data) {
        if (lastX < 40)
            series.appendData(new DataPoint(lastX, data), false, 50);
        else
            series.appendData(new DataPoint(lastX, data), true, 50);
    }

    @Override
    public void onClick(View v) {
        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
        if (v.getId() == R.id.refresh) {
            MuseManager.refreshPairedMuses();
            List<Muse> pairedMuses = MuseManager.getPairedMuses();
            List<String> spinnerItems = new ArrayList<String>();
            for (Muse m : pairedMuses) {
                String dev_id = m.getName() + "-" + m.getMacAddress();
                Log.i("Muse Headband", dev_id);
                spinnerItems.add(dev_id);
            }
            ArrayAdapter<String> adapterArray = new ArrayAdapter<String>(
                    this, android.R.layout.simple_spinner_item, spinnerItems);
            musesSpinner.setAdapter(adapterArray);


        } else if (v.getId() == R.id.connect) {
            List<Muse> pairedMuses = MuseManager.getPairedMuses();
            if (pairedMuses.size() < 1 ||
                    musesSpinner.getAdapter().getCount() < 1) {
                Log.w("Muse Headband", "There is nothing to connect to");
            } else {
                muse = pairedMuses.get(musesSpinner.getSelectedItemPosition());
                ConnectionState state = muse.getConnectionState();
                if (state == ConnectionState.CONNECTED ||
                        state == ConnectionState.CONNECTING) {
                    Log.w("Muse Headband", "doesn't make sense to connect second time to the same muse");
                    return;
                }
                configure_library();
                fileWriter.open();
                fileWriter.addAnnotationString(1, "Connect clicked");

                try {
                    muse.runAsynchronously();
                } catch (Exception e) {
                    Log.e("Muse Headband", e.toString());
                }
            }
        } else if (v.getId() == R.id.disconnect) {
            if (muse != null) {

                muse.disconnect(true);
                fileWriter.addAnnotationString(1, "Disconnect clicked");
                fileWriter.flush();
                fileWriter.close();
            }
        } else if (v.getId() == R.id.pause) {
            dataTransmission = !dataTransmission;
            if (muse != null) {
                muse.enableDataTransmission(dataTransmission);
            }
        } else if (v.getId() == R.id.measure_start) {
            ConnectionState state = muse.getConnectionState();

            if(state == ConnectionState.CONNECTED) {
                appStatus = AppStatus.READING;
                final Handler handler_calc = new Handler();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while(appStatus == AppStatus.READING) {
                            if(numOfMeasuredData >= 13200) {
                                appStatus = AppStatus.CALCULATING;
                                break;
                            }
                        }

                        double f2_sum1 = 0;
                        double f3_sum1 = 0;
                        double f4_sum1 = 0;

                        double f1_sum2 = 0;
                        double f2_sum2 = 0;
                        double f3_sum2 = 0;
                        double f4_sum2 = 0;

                        for(int i = 0; i < 12; i++) {
                            Complex[] eegFFTFP1;
                            Complex[] eegFFTFP2;

                            for(int j = 0; j < 1100; j++) {
                                eegDataFP1[j] = queueFP1.remove();
                                eegDataFP2[j] = queueFP2.remove();
                            }

                            eegFFTFP1 = FFT.fft(Window.HammingWindow1100(eegDataFP1), false);
                            eegFFTFP2 = FFT.fft(Window.HammingWindow1100(eegDataFP2), false);

                            double SMR1 = EegPower.calcSMR(eegFFTFP1, SAMPLERATE_EEG);
                            double Gamma1 = EegPower.calcGamma(eegFFTFP1, SAMPLERATE_EEG);
                            double HighBeta1 = EegPower.calcHighBeta(eegFFTFP1, SAMPLERATE_EEG);
                            double MidBeta1 = EegPower.calcMidBeta(eegFFTFP1, SAMPLERATE_EEG);

                            double SMR2 = EegPower.calcSMR(eegFFTFP2, SAMPLERATE_EEG);
                            double Theta2 = EegPower.calcTheta(eegFFTFP2, SAMPLERATE_EEG);
                            double Gamma2 = EegPower.calcGamma(eegFFTFP2, SAMPLERATE_EEG);
                            double HighBeta2 = EegPower.calcHighBeta(eegFFTFP2, SAMPLERATE_EEG);
                            double MidBeta2 = EegPower.calcMidBeta(eegFFTFP2, SAMPLERATE_EEG);

                            f2_sum1 += SMR1 / HighBeta1;
                            f3_sum1 += SMR1 / MidBeta1;
                            f4_sum1 += SMR1 / Gamma1;

                            f1_sum2 += Theta2 / Gamma2;
                            f2_sum2 += SMR2 / HighBeta2;
                            f3_sum2 += SMR2 / MidBeta2;
                            f4_sum2 += SMR2 / Gamma2;


                        }

                        measured_f1 = f1_sum2;
                        measured_f2 = (f2_sum1 + f2_sum2) / 2;
                        measured_f3 = (f3_sum1 + f3_sum2) / 2;
                        measured_f4 = (f4_sum1 + f4_sum2) / 2;

                        appStatus = AppStatus.MEASURED;
                        handler_calc.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Measuring Completed", Toast.LENGTH_LONG).show();
                                Toast.makeText(getApplicationContext(), "f1 = " + measured_f1, Toast.LENGTH_LONG).show();
                                Toast.makeText(getApplicationContext(), "f2 = " + measured_f2, Toast.LENGTH_LONG).show();
                                Toast.makeText(getApplicationContext(), "f3 = " + measured_f3, Toast.LENGTH_LONG).show();
                                Toast.makeText(getApplicationContext(), "f4 = " + measured_f4, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }).start();
            }
            else
                Toast.makeText(getApplicationContext(), "Not connected with MUSE!", Toast.LENGTH_LONG).show();
        } else if(v.getId() == R.id.game_start) {
            ConnectionState state = muse.getConnectionState();

            if(state == ConnectionState.CONNECTED && appStatus == AppStatus.MEASURED) {
                // 게임 시작!
            }
            else {
                // ㄴㄴ
            }
        } else if(v.getId() == R.id.game_stop) {
            if(appStatus == AppStatus.PLAYING) {
                // 게임 중지
            }
            else {
                //ㄴㄴ
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult " + resultCode);

        switch (requestCode) {

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {

                } else {

                    Log.d(TAG, "Bluetooth is not enabled");
                }
                break;
            case REQUEST_CONNECT_DEVICE:
                if(resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "REQUEST_CONNECT_DEVICE");
                    connectDevice(data, false);
                }
                break;
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        Log.d("TEST", address);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        btService.connect(device, secure);
    }



    private void configure_library() {
        muse.registerConnectionListener(connectionListener);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.ACCELEROMETER);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.EEG);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.HORSESHOE);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.ARTIFACTS);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.BATTERY);
        muse.setPreset(MusePreset.PRESET_14);
        muse.enableDataTransmission(dataTransmission);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}