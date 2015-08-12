package com.eeg.aceapplication;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
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
import com.ace.project.signalprocess.power.SignalPower;
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
import java.util.List;


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
                    updateEeg(p.getValues());
                    break;
                case ACCELEROMETER:
                    updateAccelerometer(p.getValues());
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

        private void updateAccelerometer(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                //    Runnable task = () -> {};

                //    activity.runOnUiThread(task);
            }
        }

        private void updateEeg(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
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

                            eegData[eegIdx] = (leftForehead + rightForehead + rightEar + leftEar) / 4;

                            eegIdx++;
                        }

                        if (eegIdx == SAMPLERATE_EEG - 1) {


                            eegFFT = FFT.fft(Window.HammingWindow220Hz(eegData), false);

                            power1 = EegPower.calcTheta(eegFFT, SAMPLERATE_EEG);
                            power2 = EegPower.calcAlpha(eegFFT, SAMPLERATE_EEG);
                            power3 = EegPower.calcBeta(eegFFT, SAMPLERATE_EEG);
                            power4 = EegPower.calcGamma(eegFFT, SAMPLERATE_EEG);
                            concentration = Concentration.calcConcentration(eegFFT, SAMPLERATE_EEG);
//                            hurst = Hurst.calHurst(eegData);


                            theta.setText("Theta " + String.format(
                                    "%6.2f", power1));
                            alpha.setText("Alpha " + String.format(
                                    "%6.2f", power2));
                            beta.setText("Beta " + String.format(
                                    "%6.2f", power3));
                            gamma.setText("Gamma " + String.format(
                                    "%6.2f", power4));

                            addEntry(seriesTheta, power1); // draw graph
                            addEntry(seriesAlpha, power2);
                            addEntry(seriesBeta, power3);
                            addEntry(seriesGamma, power4);
                            addEntry(seriesConcentration, 50 * concentration);
//                            addEntry(seriesHurst, 20 * hurst);

                            double[] freqpower = SignalPower.calcPower(eegFFT);
                            updateFreq(seriesFreq, freqpower);

                            eegIdx = 0;
                            lastX++;
                        }
                    }
                });

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
                        else
                            horseshoe3.setText("X");

//                        horseshoe3.setText(String.format("%6.2f", data.get(Eeg.TP10.ordinal())));

                    }
                });
            }
        }

        public void setFileWriter(MuseFileWriter fileWriter) {
            this.fileWriter = fileWriter;
        }
    }

    private Muse muse = null;
    private ConnectionListener connectionListener = null;
    private DataListener dataListener = null;
    private boolean dataTransmission = true;
    private MuseFileWriter fileWriter = null;

    private static final int SAMPLERATE_EEG = 220;
    private static final int SAMPLERATE_2 = 256;
    private static final int SAMPLERATE_ACC = 50;

    // ArrayList that contains eegData
    private double[] eegData = new double[SAMPLERATE_EEG];
    private Complex[] eegFFT;

    double power1, power2, power3, power4, concentration;

    private int eegIdx = 0;


    GraphView graph;
    GraphView graphFreq;

    Viewport viewport;
    Viewport viewportfreq;

    private LineGraphSeries<DataPoint> seriesTheta;
    private LineGraphSeries<DataPoint> seriesAlpha;
    private LineGraphSeries<DataPoint> seriesBeta;
    private LineGraphSeries<DataPoint> seriesGamma;
    private LineGraphSeries<DataPoint> seriesConcentration;
    private LineGraphSeries<DataPoint> seriesFreq;

    private int lastX = 0;

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

        Button resetButton = (Button) findViewById(R.id.reset);
        resetButton.setOnClickListener(this);

        CheckBox legendCheckBox = (CheckBox) findViewById(R.id.check_legend);
        legendCheckBox.setOnClickListener(this);

        fileWriter = MuseFileWriterFactory.getMuseFileWriter(new File(
                getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "testlibmusefile.muse"));
        Log.i("Muse Headband", "libmuse version=" + LibMuseVersion.SDK_VERSION);
        fileWriter.addAnnotationString(1, "MainActivity onCreate");
        dataListener.setFileWriter(fileWriter);


        graph = (GraphView) findViewById(R.id.graph);
        graphFreq = (GraphView) findViewById(R.id.graphfreq);

        viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setScrollable(false);
        viewport.setMinX(0);
        viewport.setMaxY(100);
        viewport.setMinY(0);
        viewport.setXAxisBoundsManual(true);
        viewport.setMaxX(40);

        viewportfreq = graphFreq.getViewport();
        viewportfreq.setYAxisBoundsManual(true);
        viewportfreq.setScrollable(false);
        viewportfreq.setMinX(0);
        viewportfreq.setMaxY(10);
        viewportfreq.setMinY(0);
        viewportfreq.setXAxisBoundsManual(true);
        viewportfreq.setMaxX(50);
        viewportfreq.setMinX(0);


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
        graphFreq.addSeries(seriesFreq);
        seriesTheta.setColor(Color.parseColor("#b98e8e"));
        seriesAlpha.setColor(Color.parseColor("#0024ff"));
        seriesBeta.setColor(Color.parseColor("#00ff0c"));
        seriesGamma.setColor(Color.parseColor("#8400ff"));
        seriesConcentration.setColor(Color.parseColor("#ff0000"));

        seriesFreq.setColor(Color.parseColor("#020202"));


        seriesTheta.setTitle("Theta");
        seriesAlpha.setTitle("Alpha");
        seriesBeta.setTitle("Beta");
        seriesGamma.setTitle("Gamma");
        seriesConcentration.setTitle("Concentration");

        graph.getLegendRenderer().setVisible(false);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

    }

    private void addEntry(LineGraphSeries<DataPoint> series, double data) {

        double dat = data;

        if (dat > 100)
            dat = 100;


        if (lastX < 40)
            series.appendData(new DataPoint(lastX, dat), false, 50);
        else
            series.appendData(new DataPoint(lastX, dat), true, 50);
    }

    private void resetEntry(LineGraphSeries<DataPoint> series) {
        lastX = 0;
        series.resetData(new DataPoint[]{});
        viewport.scrollToEnd();
        viewport.setMinX(0);
        viewport.setMaxX(40);
    }

    private void updateFreq(LineGraphSeries<DataPoint> series, double[] power) {
        DataPoint[] data = new DataPoint[50];
        for (int i = 0; i < 50; i++) {
            double pow = power[i];
            if (pow > 10)
                pow = 10;

            data[i] = new DataPoint(i, pow);
        }
        series.resetData(data);
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

            Toast.makeText(getApplicationContext(), "REFRESH", Toast.LENGTH_LONG).show();


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
        } else if (v.getId() == R.id.check_legend) {
            CheckBox check = (CheckBox) findViewById(R.id.check_legend);

            if (check.isChecked())
                graph.getLegendRenderer().setVisible(true);
            else
                graph.getLegendRenderer().setVisible(false);
        } else if (v.getId() == R.id.reset) {
            resetEntry(seriesTheta);
            resetEntry(seriesAlpha);
            resetEntry(seriesBeta);
            resetEntry(seriesGamma);
            resetEntry(seriesConcentration);
            resetEntry(seriesFreq);
        }
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
