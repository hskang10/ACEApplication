package com.eeg.aceapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
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
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ace.project.complex.Complex;
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

public class MainActivity extends Activity implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {
    /**
     * Connection listener updates UI with new connection status
     */

    class MeasureBasePowerTask extends AsyncTask<Void, Void, Void> {

        ProgressDialog dialog = new ProgressDialog(MainActivity.this);

        int progress = 0;

        @Override
        protected void onPreExecute() {
            appStatus = AppStatus.MEASURING;
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setCancelable(false);
            dialog.setMessage("측정중입니다");
            dialog.show();
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {

            while (appStatus == AppStatus.MEASURING) {
                progress = numOfQueueData / 132;
                dialog.setProgress(progress);
                if (numOfQueueData >= 13200) {
                    break;
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            dialog.dismiss();
            calculateBasePowerTask = new CalculateBasePowerTask();
            calculateBasePowerTask.execute();
            super.onPostExecute(aVoid);
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, "Cancelled");
            appStatus = AppStatus.STOP;
            queueFP1.removeAll(queueFP1);
            queueFP2.removeAll(queueFP2);
            numOfQueueData = 0;
            super.onCancelled();
        }
    }

    class CalculateBasePowerTask extends AsyncTask<Void, Void, Void> {

        ProgressDialog dialog = new ProgressDialog(MainActivity.this);
        int progress = 0;

        @Override
        protected void onPreExecute() {
            appStatus = AppStatus.CALCULATING;
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setCancelable(false);
            dialog.setMessage("계산중입니다");
            dialog.show();
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            double f2_sum1 = 0;
            double f3_sum1 = 0;
            double f4_sum1 = 0;

            double f1_sum2 = 0;
            double f2_sum2 = 0;
            double f3_sum2 = 0;
            double f4_sum2 = 0;

            for (int i = 0; i < 12; i++) {
                Complex[] eegFFTFP1;
                Complex[] eegFFTFP2;

                for (int j = 0; j < 1100; j++) {
                    eegDataFP1[j] = queueFP1.remove();
                    eegDataFP2[j] = queueFP2.remove();
                    numOfQueueData--;
                    progress++;

                    dialog.setProgress(progress / 132);
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

            measured_f1 = f1_sum2 / 12;
            measured_f2 = (f2_sum1 + f2_sum2) / 2 / 12;
            measured_f3 = (f3_sum1 + f3_sum2) / 2 / 12;
            measured_f4 = (f4_sum1 + f4_sum2) / 2 / 12;

            return null;
    }

        @Override
        protected void onPostExecute(Void aVoid) {
            dialog.dismiss();
            appStatus = AppStatus.MEASURED;
            super.onPostExecute(aVoid);
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, "Cancelled");
            appStatus = AppStatus.STOP;
            queueFP1.removeAll(queueFP1);
            queueFP2.removeAll(queueFP2);
            numOfQueueData = 0;
            super.onCancelled();
        }
    }

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
                    if (appStatus == AppStatus.MEASURING) {
                        getEeg(p.getValues());
                    }
                    else if(appStatus == AppStatus.PLAYING) {
                        gamming(p.getValues());
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

        private void gamming(final ArrayList<Double> data) {
            Activity activity = activityRef.get();


            if (activity != null) {

                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        if (eegIdx < SAMPLERATE_EEG - 1) {
                            double leftForehead = data.get(Eeg.FP1.ordinal());
                            double rightForehead = data.get(Eeg.FP2.ordinal());

                            eegData1[eegIdx] = leftForehead;
                            eegData2[eegIdx] = rightForehead;

                            eegIdx++;
                        } else if (eegIdx >= SAMPLERATE_EEG - 1) {
                            eegIdx = 0;

                            eegFFT1 = FFT.fft(Window.HammingWindow220(eegData1), false);
                            eegFFT2 = FFT.fft(Window.HammingWindow220(eegData2), false);

                            switch(powerSelect) {
                                case POWER1:
                                    double f1 = EegPower.calcTheta(eegFFT2, SAMPLERATE_EEG) / EegPower.calcGamma(eegFFT2, SAMPLERATE_EEG);
                                    power = f1 / measured_f1;
                                    break;
                                case POWER2:
                                    double f2 = ((EegPower.calcSMR(eegFFT1, SAMPLERATE_EEG) / EegPower.calcHighBeta(eegFFT1, SAMPLERATE_EEG)) +
                                            (EegPower.calcSMR(eegFFT2, SAMPLERATE_EEG) / EegPower.calcHighBeta(eegFFT2, SAMPLERATE_EEG)))/2;
                                    power = f2 / measured_f2;
                                    break;
                                case POWER3:
                                    double f3 = ((EegPower.calcSMR(eegFFT1, SAMPLERATE_EEG) / EegPower.calcMidBeta(eegFFT1, SAMPLERATE_EEG)) +
                                            (EegPower.calcSMR(eegFFT2, SAMPLERATE_EEG) / EegPower.calcMidBeta(eegFFT2, SAMPLERATE_EEG)))/2;
                                    power = f3 / measured_f3;
                                    break;
                                case POWER4:
                                    double f4 = ((EegPower.calcSMR(eegFFT1, SAMPLERATE_EEG) / EegPower.calcGamma(eegFFT1, SAMPLERATE_EEG)) +
                                            (EegPower.calcSMR(eegFFT2, SAMPLERATE_EEG) / EegPower.calcGamma(eegFFT2, SAMPLERATE_EEG)))/2;
                                    power = f4 / measured_f4;
                                    break;

                            }

                            Log.d(TAG, "Calculated 1sec");

                            handler_EEG.post(new Runnable() {
                                @Override
                                public void run() {
                                        TextView ratio = (TextView) findViewById(R.id.power_ratio);
                                        ratio.setText(String.format(
                                                "%6.2f", power));

                                        addEntry(seriesConcentration, power);

                                        lastX++;

                                        Log.i(TAG, "lastX = " + lastX);
                                }
                            });


                            if(btService.getState() == BluetoothService.STATE_CONNECTED) {

                                if (power > 1.25) {
                                    byte[] ch = {'A'};
                                    btService.write(ch);
                                } else if (power > 1.2) {
                                    byte[] ch = {'B'};
                                    btService.write(ch);
                                } else if (power > 1.15) {
                                    byte[] ch = {'C'};
                                    btService.write(ch);
                                } else if (power > 1.1) {
                                    byte[] ch = {'D'};
                                    btService.write(ch);
                                } else if (power > 1.05) {
                                    byte[] ch = {'E'};
                                    btService.write(ch);
                                } else {
                                    byte[] ch = {'F'};
                                    btService.write(ch);
                                }
                            }
                        }
                    }
                }).start();
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
                            horseshoe0.setText("O");
                        else
                            horseshoe0.setText("X");

                        if (data.get(Eeg.FP1.ordinal()) <= 3.0)
                            horseshoe1.setText("O");
                        else
                            horseshoe1.setText("X");

                        if (data.get(Eeg.FP2.ordinal()) <= 3.0)
                            horseshoe2.setText("O");
                        else
                            horseshoe2.setText("X");

                        if (data.get(Eeg.TP10.ordinal()) <= 3.0)
                            horseshoe3.setText("O");
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

    // program
    private MeasureBasePowerTask measureBasePowerTask;
    private CalculateBasePowerTask calculateBasePowerTask;

    // Program state
    private enum AppStatus {
        STOP, MEASURING, CALCULATING, MEASURED, PLAYING
    }

    AppStatus appStatus;

    private enum PowerSelect {
        POWER1, POWER2, POWER3, POWER4
    }

    PowerSelect powerSelect = PowerSelect.POWER1;


    // EEG power
    private static final int SAMPLERATE_EEG = 220;
    private int eegIdx = 0;
    double power;

    private Queue<Double> queueFP1 = new LinkedList<Double>();
    private Queue<Double> queueFP2 = new LinkedList<Double>();
    private int numOfQueueData = 0;

    private double[] eegData1 = new double[SAMPLERATE_EEG];
    private double[] eegData2 = new double[SAMPLERATE_EEG];

    private double[] eegDataFP1 = new double[SAMPLERATE_EEG * 60];
    private double[] eegDataFP2 = new double[SAMPLERATE_EEG * 60];
    private double result = 0;
    private Complex[] eegFFT1;
    private Complex[] eegFFT2;

    private double measured_f1 = 0;
    private double measured_f2 = 0;
    private double measured_f3 = 0;
    private double measured_f4 = 0;

    final Handler handler_EEG = new Handler();


    // Graph
    private LineGraphSeries<DataPoint> seriesConcentration;

    private int lastX = 0;
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
        Button resetGraphButton = (Button) findViewById(R.id.graph_reset);
        resetGraphButton.setOnClickListener(this);

        Button measureStartButton = (Button) findViewById(R.id.measure_start);
        measureStartButton.setOnClickListener(this);
        Button gameStartButton = (Button) findViewById(R.id.game_start);
        gameStartButton.setOnClickListener(this);
        Button gameStopButton = (Button) findViewById(R.id.game_stop);
        gameStopButton.setOnClickListener(this);

        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGroup);
        radioGroup.setOnCheckedChangeListener(this);



        if (btService == null) {
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
        viewport.setMaxY(2);
        viewport.setMinY(0);
        viewport.setXAxisBoundsManual(true);
        viewport.setMaxX(120);
        viewport.setMinX(0);

        seriesConcentration = new LineGraphSeries<DataPoint>(new DataPoint[]{});

        graph.addSeries(seriesConcentration);
        seriesConcentration.setColor(Color.parseColor("#ff0000"));

        seriesConcentration.setTitle("집중도");

        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

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
        } else if (v.getId() == R.id.graph_reset) {
            resetGraph();

        } else if (v.getId() == R.id.measure_start) {
            if(appStatus == AppStatus.STOP) {
                if (muse == null)
                    Toast.makeText(getApplicationContext(), "연결된 뮤즈가 없습니다.", Toast.LENGTH_LONG).show();
                else {
                    ConnectionState state = muse.getConnectionState();

                    if (state == ConnectionState.CONNECTED) {

                        Toast.makeText(getApplicationContext(), "측정을 시작합니다", Toast.LENGTH_LONG).show();
                        measureBasePowerTask = new MeasureBasePowerTask();
                        measureBasePowerTask.execute();
                        Log.d(TAG, "측정시작");
                    } else
                        Toast.makeText(getApplicationContext(), "연결된 뮤즈가 없습니다.", Toast.LENGTH_LONG).show();
                }
            } else if(appStatus == AppStatus.MEASURED) {
                AlertDialog.Builder dialogbuild = new AlertDialog.Builder(this);
                dialogbuild.setMessage("다시 측정하시겠습니까?").setCancelable(false).setPositiveButton("네", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                numOfQueueData = 0;
                                measureBasePowerTask = new MeasureBasePowerTask();
                                measureBasePowerTask.execute();
                            }
                        }
                    ).setNegativeButton("아니오", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                AlertDialog dialog = dialogbuild.create();
                dialog.setTitle("");
                dialog.show();
            }

        } else if (v.getId() == R.id.game_start) {
            if(muse == null) {
                Toast.makeText(getApplicationContext(), "연결된 뮤즈가 없습니다", Toast.LENGTH_LONG).show();
            } else {
                ConnectionState state = muse.getConnectionState();

                if (state == ConnectionState.CONNECTED && appStatus == AppStatus.MEASURED) {
                    appStatus = AppStatus.PLAYING;
                    Toast.makeText(getApplicationContext(), "게임을 시작합니다", Toast.LENGTH_LONG).show();
                } else if (appStatus == AppStatus.PLAYING) {
                    Toast.makeText(getApplicationContext(), "이미 실행중입니다.", Toast.LENGTH_LONG).show();
                } else if (appStatus != AppStatus.MEASURED) {
                    Toast.makeText(getApplicationContext(), "기본 측정을 완료하지 않았습니다.", Toast.LENGTH_LONG).show();
                }
            }

        } else if (v.getId() == R.id.game_stop) {
            if (appStatus == AppStatus.PLAYING) {
                appStatus = AppStatus.MEASURED;
            } else {
                //ㄴㄴ
            }
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

        eegIdx = 0;
        switch(checkedId) {
            case R.id.radioButton1:
                powerSelect = PowerSelect.POWER1;
                resetGraph();
                break;
            case R.id.radioButton2:
                powerSelect = PowerSelect.POWER2;
                resetGraph();
                break;
            case R.id.radioButton3:
                powerSelect = PowerSelect.POWER3;
                resetGraph();
                break;
            case R.id.radioButton4:
                powerSelect = PowerSelect.POWER4;
                resetGraph();
                break;
        }
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
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "REQUEST_CONNECT_DEVICE");
                    connectDevice(data, false);
                }
                break;
        }
    }

    private void addEntry(LineGraphSeries<DataPoint> series, double data) {
        if (lastX < 120)
            series.appendData(new DataPoint(lastX, data), false, 150);
        else
            series.appendData(new DataPoint(lastX, data), true, 150);
    }

    private void resetGraph() {
        DataPoint[] point = {};
        seriesConcentration.resetData(point);
        lastX = 0;
        viewport.setMinX(0);
        viewport.setMaxX(120);
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

}