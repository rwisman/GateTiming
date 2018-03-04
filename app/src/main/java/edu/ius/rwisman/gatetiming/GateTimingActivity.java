/*
 *     Copyright (C) 2017  Raymond Wisman
 * 			Indiana University SE
 * 			October 24, 2017
 *
 * 	GateTiming records, displays, and saves event times as measured by gates made of push buttons, photo resistors, etc.

	The application is designed for use in science education experiments that
		measure the time during and between gate events.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details <http://www.gnu.org/licenses/>.

 */

package edu.ius.rwisman.gatetiming;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;

import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import org.json.JSONArray;
import org.threeten.bp.Instant;

public class GateTimingActivity extends AppCompatActivity implements AudioLoss, TimingDataRemoved {

    private static int RECORDER_SAMPLERATE = 0;
	private static int FREQUENCY=0;

	private final static int MAX_NTIMINGS = 5000;
    private final static float DETECTIONLEVELPERCENTAGE = 0.4f;
    private final static double MAXIMUMAMPLITUDEPERCENTAGE = 0.9;
    private final static double BROKENCIRCUITPERCENTAGE = 0.1;
    private final static int MINIMUMVOLUME = 1;
    private final static int NTIMINGS = 2;
    private final static double ILLUMINATIONBLOCKED = 0.8;                                          // Illumination considered blocked at 80% of maximum illumination

    private final static int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private final static int REQUEST_RECORD =                 2;

    private static String GATETIMING_FOLDER = null;
    private static final String GATETIMING_FILE_EXT_CSV = ".csv";

    private static final int STARTSTATE =                   0;
    private static final int COLLECTINGSTATE =              1;
    private static final int COMPLETEDSTATE =               2;
    private static final int CALIBRATINGSTATE =             3;
    private static final int CALIBRATIONCOMPLETED =         4;
    private static final int DETECTINGSTATE =               5;
    private static final int LISTINGSTATE =                 6;

 	private Activity activity = this;

    private static int N;                                                                           // number of gates measured

    private static boolean stop = true;
    private static boolean detectionFinish = false;
    private static boolean calibrateFinish = false;
    private static boolean showList = false;
    private static boolean initialOpeningApp = true;
    private static boolean calibrated = false;
    private static boolean gatesDetected = false;
    private static boolean repeating = false;
    private static boolean ignoreAirplaneMode = false;
    private static boolean doNotShowWriteExternalStorageAgain = false;
    private static boolean writeExternalStoragePermission = false;
    private static boolean simpleListDisplay = true;

    private static double[] lows;
    private static double[] highs;
    private static long[] epochMilliseconds;

    private static int volume = MINIMUMVOLUME;
    private static double maxMagnitude=Short.MAX_VALUE;
    private static float detectionLevelPercentage = DETECTIONLEVELPERCENTAGE;
    private static double detectionLevelMaxLow;

    private int bufferSize;
    private static int currentState = CALIBRATINGSTATE;

    private static int timings=NTIMINGS;                                                            // Number of timing points (switches/gates)
    private static int detectionLevel = (int) (Short.MAX_VALUE*DETECTIONLEVELPERCENTAGE);           // Default sound level to detect lows and highs

    private static Button startbutton;                                                              // Must be static to be updated via background thread after rotation and onCreate called
    private static Button completedbutton;
    private static Toolbar toolbar;
    private static ListView listView;
    private static AboutDialog about=null;
    private static AlertDialog alertDialog=null;
    private static BaseListViewAdapter listViewAdapter=null;
    private static TableLayout calibrationLayout=null;
    private static TableLayout detectionResultsTableLayout=null;
    private static TableLayout timingResultsTableLayout=null;
    private static TableLayout simpletimingResultsTableLayout=null;
    private static ListView detectingResultsListview = null;
    private static ResultsListViewAdapter detectingResultsAdapter = null;

    private static AudioIntentReceiver audioIntentReceiver=null;
    private static Menu optionsMenu=null;
    private static Thread threadTiming = null;
    private static Thread threadCalibration = null;
    private static Thread threadDetection = null;
    private static AudioSineWaveSynchronized AudioSineWaveSynchronized = new AudioSineWaveSynchronized();

    @Override
	public void onCreate(final Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        DEBUG.redirectOut(getString(R.string.app_name));
        GATETIMING_FOLDER = this.getString(R.string.app_name);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);

        if(DEBUG.ON) System.out.println("onCreate new Deployment");

        lows = new double[MAX_NTIMINGS];
        highs = new double[MAX_NTIMINGS];
        epochMilliseconds = new long[MAX_NTIMINGS];

        if (savedInstanceState == null) {

            N = 0;

            SharedPreferences settings = this.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);

            timings = settings.getInt("timings", NTIMINGS);
            repeating = settings.getBoolean("repeating", false);
            doNotShowWriteExternalStorageAgain = settings.getBoolean("doNotShowWriteExternalStorageAgain", false);
            writeExternalStoragePermission = settings.getBoolean("writeExternalStoragePermission", false);
            simpleListDisplay = settings.getBoolean("simpleListDisplay", true);

            try {                                                                                   // Changed data type from Float to Int, ignore old Float values.
                volume = settings.getInt("volume", volume);
            } catch(Exception e) {
                if (DEBUG.ON) System.out.println("onCreate SharedPreferences volume error");
            }

            detectionLevelPercentage = settings.getFloat("detectionLevelPercentage", detectionLevelPercentage);
            detectionLevelMaxLow = (double)settings.getFloat("detectionLevelMaxLow", (float)detectionLevelMaxLow);
            detectionLevel = settings.getInt("detectionLevel", detectionLevel);;

            try {
                JSONArray jsonArrayLows = new JSONArray(settings.getString("lows", "[]"));
                JSONArray jsonArrayHighs = new JSONArray(settings.getString("highs", "[]"));
                JSONArray jsonArrayEpochMilliseconds = new JSONArray(settings.getString("epochMilliseconds", "[]"));

                for (int i = 0; i < jsonArrayLows.length(); i++) {
                    lows[i] = jsonArrayLows.getDouble(i);
                    highs[i] = jsonArrayHighs.getDouble(i);
                    epochMilliseconds[i] = jsonArrayEpochMilliseconds.getLong(i);
                }
                N = jsonArrayLows.length();
                if(DEBUG.ON) System.out.println("onCreate Restoring SharedPreferences lows:"+jsonArrayLows.toString()+" highs:"+jsonArrayHighs.toString());
            } catch (Exception e) {}

            if(DEBUG.ON) System.out.println("Restoring SharedPreferences N:"+N+" timings:"+timings+" detectionLevel:"+detectionLevel+" volume:"+volume+" currentState:"+currentState);
        }

        detectingResultsListview = (ListView) findViewById(R.id.resultslistview);

        startbutton = (Button) findViewById(R.id.startbutton);
        completedbutton  = (Button) findViewById(R.id.completedbutton);

        calibrationLayout = (TableLayout) findViewById(R.id.calibration_layout);
        detectionResultsTableLayout = (TableLayout) findViewById(R.id.detectionresults_layout);
        timingResultsTableLayout = (TableLayout) findViewById(R.id.timingResultsTableLayout);
        simpletimingResultsTableLayout = (TableLayout) findViewById(R.id.simpletimingResultsTableLayout);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        if(DEBUG.ON) {
            System.out.println("1 onCreate() Manifest.permission.RECORD_AUDIO:"+(ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)== PackageManager.PERMISSION_GRANTED));
            System.out.println("1 onCreate() Manifest.permission.WRITE_EXTERNAL_STORAGE:"+(ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED));
            System.out.println("1 onCreate() (((AudioManager) getSystemService(Context.AUDIO_SERVICE)).isWiredHeadsetOn():"+((((AudioManager) getSystemService(Context.AUDIO_SERVICE)).isWiredHeadsetOn())));
        }

        startbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {

                if (DEBUG.ON) System.out.println("onClick currentState:" + currentState);

                if (currentState != STARTSTATE) {
                    stop = true;
                    return;                                                                         // Allow only a single thread
                }

                if (!isHeadSetConnected())
                    headSetConnectionDialog();
                else
                    startTimings();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        if(DEBUG.ON) System.out.println("onSaveInstanceState detectionLevel:"+detectionLevel+" calibrated:"+calibrated+" timings:"+timings +" N:"+N+" currentState:"+currentState);
        super.onSaveInstanceState(outState);

        double [] nLows = new double[N];
        double [] nHighs = new double[N];
        long [] nEpochMilliseconds = new long[N];

        for(int i=0; i<N; i++) {
            nLows[i] = lows[i];
            nHighs[i] = highs[i];
            nEpochMilliseconds[i] = epochMilliseconds[i];
        }

        outState.putInt("timings", timings);
        outState.putDoubleArray("lows", nLows);
        outState.putDoubleArray("highs", nHighs);
        outState.putLongArray("epochMilliseconds", nEpochMilliseconds);
        outState.putInt("N", N);
        outState.putBoolean("showList", showList);
        outState.putBoolean("calibrated", calibrated);
        outState.putBoolean("gatesDetected", gatesDetected);
        outState.putBoolean("ignoreAirplaneMode", ignoreAirplaneMode);
        outState.putBoolean("repeating", repeating);
        outState.putInt("currentState", currentState);
        outState.putInt("volume", volume);
        outState.putFloat("detectionLevelPercentage", detectionLevelPercentage);
        outState.putDouble("detectionLevelMaxLow", detectionLevelMaxLow);
        outState.putInt("detectionLevel", detectionLevel);
        outState.putBoolean("doNotShowWriteExternalStorageAgain", doNotShowWriteExternalStorageAgain);
        outState.putBoolean("writeExternalStoragePermission", writeExternalStoragePermission);
        outState.putBoolean("simpleListDisplay", simpleListDisplay);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {

        super.onRestoreInstanceState(savedInstanceState);

        double [] nLows;
        double [] nHighs;
        long [] nEpochMilliseconds;

        timings = savedInstanceState.getInt("timings");
        nLows = savedInstanceState.getDoubleArray("lows");
        nHighs = savedInstanceState.getDoubleArray("highs");
        nEpochMilliseconds = savedInstanceState.getLongArray("epochMilliseconds");

        N = savedInstanceState.getInt("N");
        currentState = savedInstanceState.getInt("currentState");
        calibrated = savedInstanceState.getBoolean("calibrated");
        gatesDetected = savedInstanceState.getBoolean("gatesDetected");
        ignoreAirplaneMode = savedInstanceState.getBoolean("ignoreAirplaneMode");
        volume = savedInstanceState.getInt("volume");
        detectionLevelPercentage = savedInstanceState.getFloat("detectionLevelPercentage");
        detectionLevelMaxLow = savedInstanceState.getDouble("detectionLevelMaxLow");
        detectionLevel = savedInstanceState.getInt("detectionLevel");
        repeating = savedInstanceState.getBoolean("repeating");
        doNotShowWriteExternalStorageAgain = savedInstanceState.getBoolean("doNotShowWriteExternalStorageAgain");
        writeExternalStoragePermission = savedInstanceState.getBoolean("writeExternalStoragePermission");
        simpleListDisplay = savedInstanceState.getBoolean("simpleListDisplay");

        for(int i=0; i<N; i++) {
            lows[i] = nLows[i];
            highs[i] = nHighs[i];
            epochMilliseconds[i] = nEpochMilliseconds[i];
        }

        if(DEBUG.ON) System.out.println("onRestoreInstanceState detectionLevel:"+detectionLevel+" calibrated:"+calibrated+" timings:"+timings +" N:"+N+" currentState:"+currentState+" doNotShowWriteExternalStorageAgain:"+doNotShowWriteExternalStorageAgain);
    }

    @Override
    public void onStart() {

        super.onStart();

        if(DEBUG.ON)
            System.out.println("onStart currentState:"+currentState+" calibrated:"+calibrated+" showList:"+showList);
    }

    @Override
    public void onDestroy() {

        super.onDestroy();

        if(DEBUG.ON)
            System.out.println("onDestroy currentState:"+currentState);

        try {
            if(audioIntentReceiver != null)
                unregisterReceiver(audioIntentReceiver);
        }
        catch (IllegalArgumentException e) {}

        if(about != null)
            about.dismiss();
        if(alertDialog != null)
            alertDialog.dismiss();
    }

    @Override
    protected void onStop() {

        super.onStop();

        if(DEBUG.ON) System.out.println("onStop Saving SharedPreferences N:"+N+" detectionLevelPerCentage timings:"+timings+" currentState:"+currentState+" doNotShowWriteExternalStorageAgain:"+doNotShowWriteExternalStorageAgain);

        SharedPreferences settings = this.getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = settings.edit();                                          // Editor object to make preference changes.
        editor.putBoolean("doNotShowWriteExternalStorageAgain", doNotShowWriteExternalStorageAgain);
        editor.putInt("timings", timings);
        editor.putInt("volume", volume);
        editor.putFloat("detectionLevelPercentage", detectionLevelPercentage);
        editor.putFloat("detectionLevelMaxLow", (float)detectionLevelMaxLow);
        editor.putInt("detectionLevel", detectionLevel);
        editor.putBoolean("calibrated", calibrated);
        editor.putBoolean("repeating", repeating);
        editor.putBoolean("simpleListDisplay", simpleListDisplay);

        JSONArray jsonArrayLows = new JSONArray();
        JSONArray jsonArrayHighs = new JSONArray();
        JSONArray jsonArrayEpochMilliseconds = new JSONArray();

        try {
            for(int i=0; i<N; i++) {
                jsonArrayLows.put(lows[i]);
                jsonArrayHighs.put(highs[i]);
                jsonArrayEpochMilliseconds.put(epochMilliseconds[i]);
            }
        }
        catch(Exception e) {}
        editor.putString("lows", jsonArrayLows.toString());
        editor.putString("highs", jsonArrayHighs.toString());
        editor.putString("epochMilliseconds", jsonArrayEpochMilliseconds.toString());
        editor.commit();

        if(DEBUG.ON) System.out.println("onStop Saving SharedPreferences lows:"+jsonArrayLows.toString()+" highs:"+jsonArrayHighs.toString());
    }

    @Override
    public void onPause() {
        super.onPause();

        initialOpeningApp = false;

        if(DEBUG.ON) System.out.println("onPause isFinishing():"+isFinishing ());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG.ON)
            System.out.println("onResume currentState:" + currentState + " calibrated:" + calibrated + " showList:" + showList+
                    " doNotShowWriteExternalStorageAgain:"+doNotShowWriteExternalStorageAgain);

        stop = false;
        detectionFinish = false;

        checkPermissions();
    }

    private void continueOnResume() {
        if(!ignoreAirplaneMode && Settings.System.getInt(this.getApplicationContext().getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) == 0) {     // Airplane mode OFF
            LayoutInflater li = LayoutInflater.from(activity);
            View promptsView = li.inflate(R.layout.airplanemode, null);

            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

            alertDialogBuilder.setView(promptsView);

            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    Intent airPlaneModeIntent = new Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS);
                                    airPlaneModeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    activity.getApplicationContext().startActivity(airPlaneModeIntent);
                                    dialog.dismiss();
                                    dialog.cancel();
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    ignoreAirplaneMode=true;
                                    dialog.dismiss();
                                    dialog.cancel();
                                }
                            });

            runOnUiThread(new Runnable() {
                public void run() {
                    alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                }
            });
        }

        TextView tv = (TextView) findViewById(R.id.calibrategates);
        tv.setText(getString(R.string.calibrate)+" "+timings);

        tv = (TextView) findViewById(R.id.detectingGates);
        tv.setText(getString(R.string.detectingGates)+" "+timings);

        toggleVisibility(currentState);

        switch (currentState) {
            case CALIBRATINGSTATE :
                if(initialOpeningApp || !gatesDetected)
                    calibrate();                                                                      // Already running
                break;
            case CALIBRATIONCOMPLETED :
                CalibrationResultsView.resetView(findViewById(R.id.calibration_volume).getRootView());
                break;
            case DETECTINGSTATE :
                initializeResultsListViewAdapter(DETECTINGSTATE);
                DetectionResultsView.resetView(findViewById(R.id.detectionResults).getRootView());
                break;
            case COLLECTINGSTATE :
                initializelistViewAdapter();
                break;                                                                              // Running in background
            case LISTINGSTATE :
                if(showList)
                    showList();
                break;
            default:
        }

        if (DEBUG.ON)
            System.out.println("onResume currentState:" + currentState + " listViewAdapter:" + listViewAdapter);

        if((currentState == COLLECTINGSTATE) && listViewAdapter != null)
            listViewAdapter.notifyDataSetChanged();

        if(DEBUG.ON)
            System.out.println(AudioRecorder.getStatus());
    }

    private void setupAudio() {

        AudioRecorder.initialize();
        RECORDER_SAMPLERATE = AudioRecorder.getRECORDER_SAMPLERATE();
        FREQUENCY = RECORDER_SAMPLERATE/10;                                                         // For 10 samples/wave to ensure peak finding
        bufferSize = AudioRecorder.getBufferSize();

        if(DEBUG.ON) System.out.println("setupAudio() AudioRecorder.getRECORDER_SAMPLERATE():"+AudioRecorder.getRECORDER_SAMPLERATE()+" AudioRecorder.getSampleRate():"+AudioRecorder.recorderSampleRate());

        AudioSineWave.initialize(GateTimingActivity.this, this.getApplicationContext(), RECORDER_SAMPLERATE,FREQUENCY,4,10);

        try {
            if(audioIntentReceiver != null)
                unregisterReceiver(audioIntentReceiver);
        }
        catch (IllegalArgumentException e) {}

        audioIntentReceiver = new AudioIntentReceiver(GateTimingActivity.this, this.getApplicationContext(), RECORDER_SAMPLERATE, FREQUENCY, 4, 10);
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(audioIntentReceiver, filter);

        if(DEBUG.ON) System.out.println("setupAudio() AudioSineWave.audioSampleRate():"+AudioSineWave.audioSampleRate());
    }

    private void checkPermissions() {
        checkRecordPermission();                                                                    // Either receives permission or terminates activity.
    }

    private void checkWritePermission() {
        PermissionUtil.checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                new PermissionUtil.PermissionAskListener() {
                    @Override
                    public void onNeedPermission() {
                        if(DEBUG.ON)
                            System.out.println("GateTimingActivity  "+Manifest.permission.WRITE_EXTERNAL_STORAGE+" onNeedPermission()");
                        ActivityCompat.requestPermissions( activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE);
                    }
                    @Override
                    public void onPermissionPreviouslyDenied() {
                        if(DEBUG.ON)
                            System.out.println("GateTimingActivity  "+Manifest.permission.WRITE_EXTERNAL_STORAGE+" onPermissionPreviouslyDenied()");
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
                        alertDialogBuilder
                                .setCancelable(false)
                                .setIcon(R.drawable.ic_action_folder)
                                .setTitle(R.string.write_external_storage_rationale_title)
                                .setMessage(R.string.write_external_storage_rationale_text)
                                .setPositiveButton(getString(R.string.ok),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.dismiss();
                                                dialog.cancel();

                                                ActivityCompat.requestPermissions( activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE);
                                            }
                                        });
                        alertDialog = alertDialogBuilder.create();
                        alertDialog.show();
                        //show a dialog explaining permission and then request permission
                    }
                    @Override
                    public void onPermissionDisabled() {
                        if(DEBUG.ON)
                            System.out.println("GateTimingActivity onPermissionDisabled()");
                        if(!doNotShowWriteExternalStorageAgain) {                                   // Because of asynchronous AlertDialog, need duplicate "Continue without file access"
                            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
                            alertDialogBuilder
                                    .setCancelable(false)
                                    .setIcon(R.drawable.ic_action_folder)
                                    .setTitle(R.string.write_external_storage_rationale_title)
                                    .setMessage(R.string.write_external_storage_disabled_text)
                                    .setPositiveButton(getString(R.string.ok),
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    dialog.dismiss();
                                                    dialog.cancel();

                                                    doNotShowWriteExternalStorageAgain = true;
                                                    writeExternalStoragePermission = false;
                                                    setupAudio();                                   // Continue without file access
                                                    continueOnResume();
                                                }
                                            });

                            alertDialog = alertDialogBuilder.create();
                            alertDialog.show();
                        }
                        else {
                            setupAudio();                                                           // Continue without file access
                            continueOnResume();
                        }
                    }
                    @Override
                    public void onPermissionGranted() {
                        if(DEBUG.ON)
                            System.out.println("GateTimingActivity  "+Manifest.permission.WRITE_EXTERNAL_STORAGE+" onPermissionGranted()");
                        writeExternalStoragePermission = true;
                        doNotShowWriteExternalStorageAgain = false;                                 // In case user changes permission externally

                        setupAudio();
                        continueOnResume();
                    }
                });
    }

    private void checkRecordPermission() {
        PermissionUtil.checkPermission(this, Manifest.permission.RECORD_AUDIO,
                new PermissionUtil.PermissionAskListener() {
                    @Override
                    public void onNeedPermission() {
                        if(DEBUG.ON)
                            System.out.println("GateTimingActivity  "+Manifest.permission.RECORD_AUDIO+" onNeedPermission()");
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
                        alertDialogBuilder
                                .setCancelable(false)
                                .setIcon(R.drawable.ic_mic_off_white_24dp)
                                .setTitle(R.string.record_audio_rationale_title)
                                .setMessage(R.string.record_audio_rationale_text)
                                .setPositiveButton(getString(R.string.ok),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.dismiss();
                                                dialog.cancel();

                                                ActivityCompat.requestPermissions( activity, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD);
                                            }
                                        });
                        alertDialog = alertDialogBuilder.create();
                        alertDialog.show();
                    }
                    @Override
                    public void onPermissionPreviouslyDenied() {
                        if(DEBUG.ON)
                            System.out.println("GateTimingActivity  "+Manifest.permission.RECORD_AUDIO+" onPermissionPreviouslyDenied()");

                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
                        alertDialogBuilder
                                .setCancelable(false)
                                .setIcon(R.drawable.ic_mic_off_white_24dp)
                                .setTitle(R.string.record_audio_rationale_title)
                                .setMessage(R.string.record_audio_rationale_text)
                                .setPositiveButton(getString(R.string.ok),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.dismiss();
                                                dialog.cancel();

                                                ActivityCompat.requestPermissions( activity, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD);
                                            }
                                        });
                        alertDialog = alertDialogBuilder.create();
                        alertDialog.show();
                    }

                    @Override
                    public void onPermissionDisabled() {
                        if (DEBUG.ON)
                            System.out.println("GateTimingActivity onPermissionDisabled()");

                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
                        alertDialogBuilder
                                .setCancelable(false)
                                .setIcon(R.drawable.ic_mic_off_white_24dp)
                                .setTitle(R.string.is_terminating)
                                .setMessage(R.string.record_audio_disabled_text)
                                .setPositiveButton(getString(R.string.ok),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.dismiss();
                                                dialog.cancel();
                                                finish();
                                            }
                                        });

                        alertDialog = alertDialogBuilder.create();
                        alertDialog.show();
                    }

                    @Override
                    public void onPermissionGranted() {
                        if(DEBUG.ON)
                            System.out.println("GateTimingActivity " +Manifest.permission.RECORD_AUDIO+" onPermissionGranted()");

                        checkWritePermission();
                    }
                });
    }

    public void audioLoss() {
        if(DEBUG.ON)
            System.out.println("audioLoss called");

        stop = true;
    }

    public void timingDataRemoved(int count, int at) {
        N = N - count;                                                                              // Reduce count by number of real timings in a run
        for(int i=at; i<N; i++) {                                                                   // Shift all timings following those removed for the run to overwrite
            lows[i] = lows[i+timings];
            highs[i] = highs[i+timings];
            epochMilliseconds[i] = epochMilliseconds[i+timings];
        }
    }
    private void closeListing() {
            showList = false;
            currentState = STARTSTATE;
            toggleVisibility(currentState);

            if(DEBUG.ON) System.out.println("1 onKeyDown before removeAverages listViewAdapter.getCount():"+listViewAdapter.getCount());
            listViewAdapter.removeAverages();                                                           // Remove averages from list, if any
            if(DEBUG.ON) System.out.println("2 onKeyDown after removeAverages listViewAdapter.getCount():"+listViewAdapter.getCount());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK)
            if(currentState == LISTINGSTATE)
                closeListing();
            else if(currentState == COLLECTINGSTATE)
                stop=true;
        return true;
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if(DEBUG.ON) System.out.println("onCreateOptionsMenu repeating:"+repeating);
        getMenuInflater().inflate(R.menu.menu_main, menu);

        optionsMenu = menu;

        MenuItem item = optionsMenu.findItem(R.id.action_repeat);
        if(repeating)
            item.setIcon(R.drawable.ic_action_repeat_one);
        else
            item.setIcon(R.drawable.ic_action_repeat);

        toggleVisibility(currentState);

        return true;
	}

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        if(DEBUG.ON) System.out.println("onPrepareOptionsMenu");
        if(simpleListDisplay)
            menu.findItem(R.id.action_toggletimingdisplay).setTitle(R.string.timingdetailed);
        else
            menu.findItem(R.id.action_toggletimingdisplay).setTitle(R.string.timingsimple);
        return true;
    }

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		int id = item.getItemId();

        if (id == R.id.action_repeat) {
            repeating = !repeating;
            System.out.println("repeating:"+repeating+" isVisible:"+item.getIcon().isVisible());

            if(repeating) {
                item.setIcon(R.drawable.ic_action_repeat_one);
                item.getIcon().setAlpha(255);
            }
            else {
                item.setIcon(R.drawable.ic_action_repeat);
                item.getIcon().setAlpha(255);
            }
            return true;
        }

        if (id == R.id.action_toggletimingdisplay) {
            simpleListDisplay = !simpleListDisplay;
            System.out.println("simpleListDisplay:"+simpleListDisplay);

            if(simpleListDisplay)
                item.setTitle(R.string.timingdetailed);
            else
                item.setTitle(R.string.timingsimple);

            if(showList)
                showList();
            else if(currentState == COLLECTINGSTATE) {
                toggleVisibility(currentState);
                initializelistViewAdapter();
            }

            return true;
        }

        if (id == R.id.action_stop) {                                                               // Toggles between stop and start
            if(DEBUG.ON) System.out.println("R.id.action_stop stop:"+stop+" currentState:"+currentState);
            if(currentState == LISTINGSTATE || currentState == STARTSTATE) {                        // Because toggle pause/play button, play only in these states, otherwise act as STOP
                if(currentState == LISTINGSTATE)
                    closeListing();
                currentState = STARTSTATE;
                toggleVisibility(currentState);
                stop = false;
                startTimings();
            }
            else
                stop = true;
            return true;
        }

        if (id == R.id.action_calibrate) {
            if (DEBUG.ON) System.out.println("R.id.action_calibrate currentState:" + currentState);
            calibrated = false;
            calibrate();
            return true;
        }

        if (id == R.id.action_delete) {
            if(listViewAdapter == null)
                initializelistViewAdapter();

            if(listViewAdapter.getCount()==0) {                          // Nothing to do
                Toast.makeText(GateTimingActivity.this, getString(R.string.nothingtodo_label), Toast.LENGTH_SHORT).show();
                return false;
            }

            LayoutInflater li = LayoutInflater.from(activity);
            View promptsView = li.inflate(R.layout.delete, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

            alertDialogBuilder.setView(promptsView);

            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    N = 0;
                                    stop = true;
                                    listViewAdapter.clear();
                                    listViewAdapter.notifyDataSetChanged();

                                    Toast.makeText(activity, getString(R.string.deleted), Toast.LENGTH_SHORT).show();
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                }
                            });

            alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return true;
        }

        if (id == R.id.action_restore) {
            LayoutInflater li = LayoutInflater.from(activity);
            View promptsView = li.inflate(R.layout.restore, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
            alertDialogBuilder.setView(promptsView);
            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    calibrated = false;
                                    maxMagnitude=Short.MAX_VALUE;
                                    detectionLevelPercentage = DETECTIONLEVELPERCENTAGE;
                                    currentState = STARTSTATE;
                                    detectionLevel = (int) (Short.MAX_VALUE*DETECTIONLEVELPERCENTAGE);           // Default sound level to detect lows and highs
                                    repeating = false;
                                    doNotShowWriteExternalStorageAgain = false;
                                    writeExternalStoragePermission = false;
                                    invalidateOptionsMenu();
                                    dialog.dismiss();
                                    dialog.cancel();
                                    calibrate();
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.dismiss();
                                    dialog.cancel();
                                }
                            });

            alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return true;
        }

        if (id == R.id.action_timings) {

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
            alertDialogBuilder
                    .setCancelable(false)
                    .setTitle(R.string.changeNumberofGates)
                    .setMessage(R.string.changeNumberofGates_text)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    final LayoutInflater li = LayoutInflater.from(activity);
                                    View promptsView = li.inflate(R.layout.timings, null);

                                    final NumberPicker samplePicker = (NumberPicker) promptsView.findViewById(R.id.timingspicker);
                                    samplePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
                                    samplePicker.setMaxValue(MAX_NTIMINGS);
                                    samplePicker.setMinValue(1);
                                    samplePicker.setValue(timings);
                                    samplePicker.setWrapSelectorWheel(true);
                                    samplePicker.setOnValueChangedListener( new NumberPicker.OnValueChangeListener() {
                                        @Override
                                        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                                            if(timings != newVal) {
                                                timings = newVal;
                                                N = 0;                                              // Starting new list
                                                if (listViewAdapter != null && oldVal != newVal) {
                                                    listViewAdapter.clear();
                                                    listViewAdapter.setGates(timings);
                                                    listViewAdapter.notifyDataSetChanged();
                                                }
                                                TextView tv = (TextView) findViewById(R.id.detectingGates);
                                                tv.setText(getString(R.string.detectingGates)+" "+timings);
                                                tv = (TextView) findViewById(R.id.calibrategates);
                                                tv.setText(getString(R.string.calibrating)+" "+timings);
                                            }
                                        }
                                    });
                                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                                    builder.setView(promptsView);
                                    builder.create();
                                    builder.show();

                                    dialog.cancel();
                                    dialog.dismiss();
                                    if (DEBUG.ON)
                                        System.out.println("NumberPicker currentState:"+currentState);
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                    dialog.dismiss();
                                }
                            });

            alertDialog = alertDialogBuilder.create();
            alertDialog.show();

            return true;
        }

        if (id == R.id.action_view_list) {
            if (DEBUG.ON) System.out.println("R.id.action_view_list currentState:" + currentState);

            if(N==0) {                                                                              // Nothing to do
                Toast.makeText(GateTimingActivity.this, getString(R.string.nothingtodo_label), Toast.LENGTH_SHORT).show();
                return false;
            }

            showList();
            return true;
        }

        if (id == R.id.action_folder) {

            if(N==0) {                                                                              // Nothing to do
                Toast.makeText(GateTimingActivity.this, getString(R.string.nothingtodo_label), Toast.LENGTH_LONG).show();
                return false;
            }

            if(!writeExternalStoragePermission) {
                Toast.makeText(GateTimingActivity.this, getString(R.string.writeExternalStorage_no_permission), Toast.LENGTH_LONG).show();
                return false;
            }

            LayoutInflater li = LayoutInflater.from(activity);
            View promptsView = li.inflate(R.layout.filename, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

            alertDialogBuilder.setView(promptsView);

            final EditText userInput = (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);
            userInput.setText(getFilename(GATETIMING_FILE_EXT_CSV), TextView.BufferType.EDITABLE);

            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    saveTiming(userInput.getText().toString());
                                    dialog.dismiss();
                                    dialog.cancel();
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                    dialog.dismiss();
                                }
                            });

            alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return true;
        }

        if (id == R.id.action_sounddetect) {
            LayoutInflater li = LayoutInflater.from(activity);
            View promptsView = li.inflate(R.layout.sounddetect, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

            alertDialogBuilder.setView(promptsView);

            final TextView detectionLevelTextView = (TextView) promptsView.findViewById(R.id.soundLevel);

            final SeekBar detectionLevelBar = (SeekBar) promptsView.findViewById(R.id.soundLevelSeekBar);
            detectionLevelBar.setProgress((int)Math.round(detectionLevelPercentage*100));
            detectionLevelTextView.setText(detectionLevelBar.getProgress()+"%");

            if(DEBUG.ON) System.out.println("0 R.id.action_sounddetect detectionLevelPercentage:"+detectionLevelPercentage+" maxMagnitude:"+maxMagnitude+" (int)(((double)detectionLevel/maxMagnitude)*100):"+(int)(((double)detectionLevel/maxMagnitude)*100));
            detectionLevelBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    detectionLevelTextView.setText(String.valueOf(progress)+"%");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    if(detectionLevelBar.getProgress() > 0)
                                        detectionLevel = (int)Math.round(maxMagnitude*(detectionLevelBar.getProgress()/100.0));
                                        detectionLevelPercentage = detectionLevelBar.getProgress()/100.0f;
                                    if(DEBUG.ON) System.out.println("0 onClick detectionLevel:"+detectionLevel+" (int)(((double)detectionLevel/maxMagnitude)*100)"+(int)(((double)detectionLevel/maxMagnitude)*100));
                                    dialog.dismiss();
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                    dialog.dismiss();
                                }
                            });

            alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return true;
        }

        if (id == R.id.action_about) {
            about = new AboutDialog(activity);
            about.setTitle(getString(R.string.about) + (String) this.getString(R.string.app_name));
            about.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
	}

    private void startTimings() {

        initializelistViewAdapter();

        threadTiming = new Thread(new Runnable() {                                                  // Start background thread that runs whether app has focus or not
            public void run() {
                timeGates();
            }
        }, "GateTiming threadTiming");

        threadTiming.start();
    }

    private void timeGates() {

        if(DEBUG.ON) System.out.println("0 timeGates started currentState:"+currentState+" detectionLevel:"+detectionLevel+" volume:"+volume+" calibrated:"+calibrated+" timings:"+timings+" N:"+N+" listViewAdapter.getCount():"+listViewAdapter.getCount()+" stop:"+stop);

        currentState = COLLECTINGSTATE;
        toggleVisibility(currentState);

        stop = false;
        showList = false;

        headSetConnectionDialog();

        AudioSineWaveSynchronized.start();
        AudioSineWave.setVolume(volume);                                                            // Restore volume in case user changed

        class Detect {
            int nextUsed = 0;                                                                       // Next byte in buffer to use, 2 bytes per sample
            int readSize = 0;                                                                       // Numbers of bytes currently read into buffer, 2 bytes per sample
            DataOutputStream output;
            byte[] data;
            long n=0;
            long low, high;

            private Detect(DataOutputStream output, byte[] data) {
                this.output=output;
                this.data=data;
            }

            // Detects high in an audio stream by identifying a magnitude above the designated sound level for a high
            // output - defines an output file for writing the raw data, used for debugging
            // data - audio data array
            // n - current sample number in data stream
            // returns the initial sample number of the high signal

            long high() {
                if(stop) return 0;

                boolean found = false;
                double x=0;                                                                         // Sample value

                while(!found && !stop) {

                    if(nextUsed >= readSize) {
                        readSize = AudioRecorder.read(data);
                        nextUsed = 0;

                        if (DEBUG.WAV)
                            try {
                                output.write(data, 0, readSize);
                            } catch (IOException e) {
                            }
                    }

                    if(readSize > 0)
                        for (int i = nextUsed; i < readSize && !found; i = i + 2) {
                            x = (data[i] & 0xFF) | ((data[i+1] & 0xFF) << 8);                       // Little endian
                            x = x <= 32767 ? x : x - 65535;

                            nextUsed = i+2;

                            if (x > detectionLevel)                                                 // Detect high
                                found = true;
                            n++;
                        }
                }
                return n;
            }

            // Detects low by identifying a string of length k absolute magnitudes below the designated sound level for a high

            // output - defines an output file for writing the raw data, used for debugging
            // data - audio data array
            // n - current sample number in data stream
            // twoSineWaves - 2 sine wave period of absolute magnitudes below the designated sound level for a high

            // returns the initial sample number of the low signal

            long low() {
                if(stop) return 0;

                boolean found = false;
                boolean firstLow = true;
                boolean isaPeak=false;
                long lows=0;
                long lowStart = 0;
                double x=0, x0 = 0, x1 = 0, x2 = 0;;
                int twoSineWaves = (int)(RECORDER_SAMPLERATE/FREQUENCY)*2;                          // Number of samples required to indicate a low, 2 cycles of generated sine wave
                final int numberPeaks = 5;

                while(!found && !stop) {
                    if (nextUsed >= readSize && !stop) {
                        readSize = AudioRecorder.read(data);

                        nextUsed = 0;

                        if (DEBUG.WAV)
                            try {
                                output.write(data, 0, readSize);
                            } catch (IOException e) {}
                    }

                    if (!stop && readSize > 0) {
                        for (int i = nextUsed; i < readSize && !found; i = i + 2) {
                            x = (data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8); // Little endian
                            x = x <= 32767 ? x : x - 65535;

                            isaPeak = x0 < x1 && x1 > x2 && x0 > 0 && x1 > 0 && x2 > 0;             // x0 < x1 && x1 > x2 true when x1 is a peak

                            nextUsed = i + 2;

                            if (isaPeak)
                                if (x1 < detectionLevel && x1 >= detectionLevelMaxLow) {
                                    if (firstLow)
                                        lowStart = n;
                                    firstLow = false;
                                    lows++;
                                } else {
                                    firstLow = true;
                                    lows = 0;
                                    lowStart = n;
                                }

                            found = lows >= numberPeaks;
                            n++;
                            x0 = x1;
                            x1 = x2;
                            x2 = x;
                        }
                    }
                    if (found) {
                        nextUsed = nextUsed - twoSineWaves * 2;                                     // Back up data pointer two sine wave cycle to byte at start of low sample detected (twoSineWaves is in samples, 2 bytes/sample)
                        nextUsed = nextUsed > 0 ? nextUsed : 0;                                     // When negative, back up data pointer to start of current data buffer
                        n = n - twoSineWaves;                                                       // n, the current sample, backed up to start of low
                    }
                }

                if(DEBUG.ON) System.out.println("timeGates() lowStart: "+lowStart+" n:"+n);
                return lowStart;
            }
        }

        DataOutputStream output = null;
        byte[] data = new byte[bufferSize * 2];
        int readSize=0;                                                                             // Starting new batch
        double low, high;

       if(DEBUG.WAV)
            try {
                output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(getFile("raw"))));
            } catch (IOException e) {}

        Detect detect = new Detect(output, data);

        while((readSize = AudioRecorder.read(data)) == 0 && !stop) {                                // Read initial buffer in case recording just started (will have garbage)
            if (DEBUG.ON) System.out.println("timeGates readSize:" + readSize);
        }
/*
        if (DEBUG.WAV)
            try {
                output.write(data, 0, readSize);
            } catch (IOException e) {}
*/

        detect.high();                                                                              // Ensure input is high before starting

        if (DEBUG.ON)
            System.out.println("timeGates() detectionLevel:"+detectionLevel+" detectionLevelMaxLow:"+detectionLevelMaxLow);

        do {
            for(int i=1; i<=timings && !stop; i++) {

                low = detect.low() / (double) RECORDER_SAMPLERATE;                                  // Detect next low, store to intermediate low as N determined before call but can change during call!!
                lows[N] = low;
                epochMilliseconds[N] = System.currentTimeMillis();
                if (DEBUG.ON)
                    System.out.println(i + "timeGates() Low detected: " + lows[N] + "s stop:" + stop + " N:" + N + " timings:" + timings);

                high = detect.high() / (double) RECORDER_SAMPLERATE;                                // Detect next high, store to intermediate low as N determined before call but can change during call!!
                highs[N] = high;
                if (DEBUG.ON)
                    System.out.println(i + "timeGates() High detected: " + highs[N] + "s repeating:" + repeating);

                if(!stop ) {
                    N++;

                    runOnUiThread(new Runnable() {
                        public void run() {
                            listViewAdapter.addItem(lows, highs, epochMilliseconds);                // UI thread update list while timing thread continues to monitor circuit signal
                        }
                    });
                }

                stop = N == MAX_NTIMINGS || stop;                                                   // Test if maximum timings exceeded or stop from user
            }
        } while( repeating && !stop );

        AudioSineWaveSynchronized.stop();

        if (DEBUG.WAV)
            try {
                readSize = AudioRecorder.read(data);                                                // Read extra for examination in WAV file
                output.write(data, 0, readSize);
            } catch (IOException e) {}

        if (DEBUG.WAV) {
            if (output != null) {
                try {
                    output.flush();
                } catch (IOException e) {
                } finally {
                    try {
                        output.close();
                    } catch (IOException e) {}
                }
            }
            try {
                rawToWave(getFile("raw"), getFile("wav"));
            } catch (Exception e) {}
        }

        if(N == MAX_NTIMINGS)                                                                       // If maximum timings exceeded display error message
            runOnUiThread(new Runnable() {
                public void run() {
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
                    alertDialogBuilder
                            .setCancelable(false)
                            .setTitle(R.string.timingsExceeded)
                            .setMessage(R.string.timingsExceeded_text)
                            .setPositiveButton(getString(R.string.ok),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,int id) {
                                            dialog.dismiss();
                                        }
                                    });

                    alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                }
            });

        if(stop)
            for (int i = 0; i < N % timings; i++)                                                   // Remove last partial run measures
                listViewAdapter.deleteLastItem();

        N = N - N % timings;

        currentState = COMPLETEDSTATE;
        toggleVisibility(currentState);

        try { Thread.sleep(1000); }
        catch(Exception e) {}

        currentState = STARTSTATE;
        toggleVisibility(currentState);
    }

    private void initializeResultsListViewAdapter(int state) {
        if (DEBUG.ON)
            System.out.println("initializeResultsListViewAdapter() maxMagnitude:" + maxMagnitude);

        detectingResultsAdapter = new ResultsListViewAdapter(activity, maxMagnitude, timings, detectingResultsListview );

        detectingResultsListview.setAdapter(detectingResultsAdapter);

        if (state != DETECTINGSTATE)
            detectingResultsAdapter.clear();

        detectingResultsAdapter.notifyDataSetChanged();
    }

    private void initializelistViewAdapter() {
        if(DEBUG.ON) System.out.println("initializelistViewAdapter() N:"+N+" listViewAdapter:"+listViewAdapter+" listView:"+listView);

        if(simpleListDisplay) {
            listView = (ListView) findViewById(R.id.simplelistview);
            listViewAdapter = new SimpleListViewAdapter(GateTimingActivity.this, timings, listView, N, lows, highs, epochMilliseconds);
        }
        else {
            listView = (ListView) findViewById(R.id.listview);
            listViewAdapter = new ListViewAdapter(GateTimingActivity.this, timings, listView, N, lows, highs, epochMilliseconds);
        }

        if(DEBUG.ON) System.out.println("initializelistViewAdapter() N:"+N+" listViewAdapter.getCount():"+listViewAdapter.getCount()+" listView:"+listView);

        listView.setAdapter(listViewAdapter);
        listView.setOnItemClickListener(listViewAdapter);
        listViewAdapter.notifyDataSetChanged();
    }

    private void showList() {
        currentState = LISTINGSTATE;
        toggleVisibility(currentState);

        showList = true;

        initializelistViewAdapter();

        listViewAdapter.addAverages(lows, highs);
    }

    private boolean isHeadSetConnected() {
        return (((AudioManager) getSystemService(Context.AUDIO_SERVICE)).isWiredHeadsetOn());
    }

    private void headSetConnectionDialog() {
        if (!isHeadSetConnected()) {                                                                // Headset !plugged in
            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
            alertDialogBuilder
                    .setCancelable(false)
                    .setIcon(R.drawable.ic_headset_white_24dp)
                    .setTitle(R.string.connectHeadSetFailed)
                    .setMessage(R.string.connectHeadSetFailed_text)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                    dialog.cancel();
                                    stop = true;
                                    headSetConnectionDialog();
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                    dialog.cancel();
                                    stop = true;
                                }
                            });
            runOnUiThread(new Runnable() {
                public void run() {
                    alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                }
            });
        }
    }

    public void sortRun(View view) {
        listViewAdapter.sortRun(view);
    }

    public void sortTime(View view) {
        listViewAdapter.sortTime(view);
    }

    public void calibrationRetry(View view) {
        if (DEBUG.ON)
            System.out.println("calibrationRetry detectionLevel:"+detectionLevel+" calibrated:"+calibrated+" currentState:"+currentState+" volume:"+volume+"AudioSineWave.getVolume():"+AudioSineWave.getVolume()+ " AudioSineWave.isStarted():"+AudioSineWave.isStarted());

        stop = true;
        gatesDetected = false;
        calibrated = false;
        calibrateFinish = true;
        detectionFinish = true;
        AudioSineWaveSynchronized.stop();

        new Thread(new Runnable() {                                                                 // Wait for detection thread to finish before starting calibration
            public void run() {
                while(threadCalibration.getState() == Thread.State.RUNNABLE) {
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {}
                }
                calibrate();
            }
        }).start();
    }

    public void calibrationOK(View view) {
        if (DEBUG.ON)
            System.out.println("calibrationOK detectionLevel:"+detectionLevel+" calibrated:"+calibrated+" currentState:"+currentState+" volume:"+volume+"AudioSineWave.getVolume():"+AudioSineWave.getVolume()+ " AudioSineWave.isStarted():"+AudioSineWave.isStarted());

        stop = true;
        calibrated = true;
        gatesDetected = false;
        calibrateFinish = true;
        AudioSineWaveSynchronized.stop();
        setDetectionLevel();
    }

    public void detectionLevelRetry(View view) {
        if (DEBUG.ON)
            System.out.println("0 detectionLevelRetry detectionLevel:"+detectionLevel+" calibrated:"+calibrated+" currentState:"+currentState+" volume:"+volume+ " AudioSineWave.isStarted():"+AudioSineWave.isStarted()+" threadDetection.getState() == Thread.State.RUNNABLE:"+(threadDetection.getState() == Thread.State.RUNNABLE));

        stop = true;
        gatesDetected = false;
        detectionFinish = true;
//        timings = oldTimings;
        AudioSineWaveSynchronized.stop();

        new Thread(new Runnable() {                                                                 // Wait for detection thread to finish before starting calibration
            public void run() {
                while(threadDetection.getState() == Thread.State.RUNNABLE) {
                    try {
                        Thread.sleep(500);
                     } catch (Exception e) {}
                }
                calibrate();
            }
        }).start();
    }

    public void detectionLevelOK(View view) {
        stop = true;
        gatesDetected = true;
        detectionFinish = true;
        currentState = STARTSTATE;
        toggleVisibility(currentState);
        AudioSineWaveSynchronized.stop();
        if (DEBUG.ON)
            System.out.println("detectionLevelOK detectionLevel:"+detectionLevel+" calibrated:"+calibrated+" currentState:"+currentState+" volume:"+volume+"AudioSineWave.getVolume():"+AudioSineWave.getVolume()+ " AudioSineWave.isStarted():"+AudioSineWave.isStarted());
    }

    private void setDetectionLevel() {

        if (DEBUG.ON)
            System.out.println("0 setDetectionlevel detectionLevel:"+detectionLevel+" currentState:"+currentState+" volume:"+volume+"AudioSineWave.getVolume():"+AudioSineWave.getVolume()+ " AudioSineWave.isStarted():"+AudioSineWave.isStarted());

        AudioSineWaveSynchronized.start();
        AudioSineWave.setVolume(volume);

        initializeResultsListViewAdapter(currentState);

        stop = false;
        detectionFinish = false;

        runOnUiThread(new Runnable() {
            public void run() {
                TextView tv = (TextView) findViewById(R.id.detectingGates);
                tv.setText(getString(R.string.detectingGates) + " " + timings);

                tv = (TextView) findViewById(R.id.detectionResults);
                tv.setVisibility(View.INVISIBLE);
            }
        });

        currentState = DETECTINGSTATE;
        toggleVisibility(currentState);

        headSetConnectionDialog();

        if (!stop)
            threadDetection = new Thread(new Runnable() {                                         // Start background thread that runs whether app has focus or not
                public void run() {
                    DataOutputStream output = null;

                    final int numberPeaks = 5;
                    byte[] data = new byte[bufferSize * 2];
                    int x, x0 = 0, x1 = 0, x2 = 0;
                    double lowStart = ILLUMINATIONBLOCKED * maxMagnitude;                           // Illumination considered blocked when % of calculated maximum
                    double maxLow = Integer.MAX_VALUE;                                              // Maximum low over a low magnitude interval of 5 sine waves
                    double overAllMaxLow = -maxLow;
                    double sumX1 = 0;
                    int readSize;
                    int lowPeaks = 0;
                    int highPeaks = 0;
                    int time = 0;
                    int n = 0;

                    boolean foundLow = false;
                    boolean foundHigh = false;
                    boolean foundAllGates = false;
                    boolean fiveLowPeaksFound = false;
                    boolean isaPeak = false;
                    boolean readSize0 = false;

                    currentState = DETECTINGSTATE;
                    toggleVisibility(currentState);

                    if (DEBUG.WAV)
                        try {
                            output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(getFile("raw"))));
                        } catch (IOException e) {
                        }

                    do {
                        readSize = AudioRecorder.read(data);

                        if (DEBUG.WAV)
                            try {
                                output.write(data, 0, readSize);
                            } catch (IOException e) {
                            if(DEBUG.ON)
                                System.out.println("0.5 setDetectionLevel Exception: "+e);
                            }
                        time = time + readSize / 2;
                    }
                    while (time < RECORDER_SAMPLERATE && !detectionFinish);                         // Throw away initial 1s input to allow volume to settle

                    while (!foundAllGates && !detectionFinish && !stop && !readSize0 && isHeadSetConnected()) {

/*
                        if (DEBUG.ON)
                            System.out.println("1 setDectionLevel() - overAllMaxLow:" + overAllMaxLow + " maxMagnitude:"+maxMagnitude+" time:" + ((double) time / RECORDER_SAMPLERATE) + " lowStart:" + lowStart+" stop:"+stop+" detectionFinish:"+detectionFinish);
*/

                        foundHigh = false;
                        foundLow = false;
                        lowPeaks = 0;
                        highPeaks = 0;
                        sumX1 = 0;
                        fiveLowPeaksFound = false;

                        readSize = AudioRecorder.read(data);
                        readSize0 = readSize == 0;

                        if (DEBUG.WAV)
                            try {
                                output.write(data, 0, readSize);
                            } catch (IOException e) {
                            }

                                                                                                    // Find lowest peak amplitude below lowStart
                        for (int i = 0; i < readSize && !detectionFinish && !foundHigh && !stop; i = i + 2) {
                            x = (data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8);                     // Little endian
                            x = x <= 32767 ? x : x - 65535;

                            isaPeak = x0 < x1 && x1 > x2 && x0 > 0 && x1 > 0 && x2 > 0;             // x0 < x1 && x1 > x2 true when x1 is a peak

                            if (!foundLow) {
                                if (x > lowStart)
                                    lowPeaks = 0;

                                if (isaPeak && x1 < lowStart) {                                     // x1 < lowStart restricts to only low amplitudes
                                    sumX1 = sumX1 + x1;                                             // x0 > 0 && x1 > 0 && x2 > 0 eliminates negatives and noise values.
                                    lowPeaks++;

                                    if (lowPeaks == numberPeaks) {
                                        if (maxLow > (double) sumX1 / lowPeaks)                     // Smooth maxLow by averaging
                                            maxLow = (double) sumX1 / lowPeaks;
                                        fiveLowPeaksFound = true;
                                        lowPeaks = 0;
                                        sumX1 = 0;
                                    }
                                } else if (fiveLowPeaksFound && x > lowStart) {                     // Assume highs are to be found
                                    foundLow = true;
                                }

                            } else if (isaPeak && x1 > lowStart) {                                  // x0 < x1 && x1 > x2 true when x1 is a peak, x1 > lowStart restricts to only high amplitudes
                                highPeaks++;
                                foundHigh = highPeaks >= numberPeaks;                               // True when specified string length number of peaks > lowStart found, following fountLow == true
                                if (DEBUG.ON)
                                    if (foundHigh)
                                        System.out.println("2 setDectionLevel() - foundHigh:" + foundHigh + " x:" + x + " time:" + ((double) time / RECORDER_SAMPLERATE));
                            }
                            x0 = x1;
                            x1 = x2;
                            x2 = x;
                            time++;
                        }

                        if (!stop || !detectionFinish) {
                            if (foundHigh) {                                                        // Detected a gate
                                n++;
                                if(maxLow > overAllMaxLow)                                          // Find max of low peaks
                                    overAllMaxLow = maxLow;

                                if (DEBUG.ON)
                                    System.out.println("3 setDectionLevel() - maxLow:" + maxLow);

                                final GateLevel gateLevel = new GateLevel(n, maxLow);
                                maxLow = Integer.MAX_VALUE;

                                final int nFinal = n;

                                detectionLevelPercentage = (float) (((overAllMaxLow + maxMagnitude) / 2) / maxMagnitude);                  // Average
                                detectionLevel = (int) (maxMagnitude * detectionLevelPercentage);
                                detectionLevelMaxLow = overAllMaxLow;

                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        if (DEBUG.ON)
                                            System.out.println("4 setDectionLevel() - n:"+nFinal+" adapter:"+detectingResultsAdapter);

                                            detectingResultsAdapter.update(gateLevel);
                                    }
                                });
                            }
                        }
                        foundAllGates = n == timings;
                    }

                    AudioSineWaveSynchronized.stop();

                    if (DEBUG.ON)
                        System.out.println("5 setDectionLevel() - overAllMaxLow:" + overAllMaxLow + " maxMagnitude:"+maxMagnitude+" time:" + ((double) time / RECORDER_SAMPLERATE) + " lowStart:" + lowStart+" stop:"+stop+" detectionFinish:"+detectionFinish);

                    if (DEBUG.WAV) {
                        if (output != null) {
                            try {
                                output.flush();
                            } catch (IOException e) {
                            } finally {
                                try {
                                    output.close();
                                } catch (IOException e) {
                                }
                            }
                        }
                        try {
                            rawToWave(getFile("raw"), getFile("wav"));
                        } catch (Exception e) {
                        }
                    }

                    if (DEBUG.ON)
                        System.out.println("6 setDetectionlevel - AudioSineWave.getVolume():" + AudioSineWave.getVolume() + " foundLow:" + foundLow + " foundHigh:" + foundHigh + " detectionLevelPercentage:" + detectionLevelPercentage + " detectionLevel:" + detectionLevel);

                    if (!detectionFinish) {
                        ArrayList<GateLevel> gateList = detectingResultsAdapter.getList();
                        boolean retry = false;

                        for (int i = 0; i < gateList.size(); i++) {
                            int ratingLevel = 100 - (int) (gateList.get(i).getLevel() / maxMagnitude * 100);
                            retry = ratingLevel < 75 || retry;

                            if (DEBUG.ON)
                                System.out.println("7 setDetectionlevel gateList(:" + i + ")=" + gateList.get(i).getLevel() + " ratingLevel:" + ratingLevel + " retry:" + retry);
                        }

                        final TextView tv = (TextView) findViewById(R.id.detectionResults);
                        final String detectionResults = retry ? getString(R.string.detectionresults_fail) : getString(R.string.detectionresults_ok);

                        runOnUiThread(new Runnable() {
                            public void run() {
                                tv.setVisibility(View.VISIBLE);
                                DetectionResultsView.setView(findViewById(R.id.detectionResults).getRootView(), detectionResults);
                            }
                        });

                        detectionLevelPercentage = (float) (((overAllMaxLow + maxMagnitude) / 2) / maxMagnitude);                  // Average
                        detectionLevel = (int) (maxMagnitude * detectionLevelPercentage);
                        detectionLevelMaxLow = overAllMaxLow;
                    }

                    if (DEBUG.ON)
                        System.out.println("8 setDetectionlevel - AudioSineWave.getVolume():" + AudioSineWave.getVolume() + " detectionLevelPercentage:" + detectionLevelPercentage + " detectionLevel:" + detectionLevel+" stop:"+stop+" detectionFinish:"+detectionFinish);
                }

            }, "GateTiming threadDetection");
        threadDetection.start();
    }

    public void calibrate() {

        if (DEBUG.ON)
            System.out.println("0 calibrate() calibrated:" + calibrated + " volume:"+volume+" currentState:" + currentState + " detectionLevel:" + detectionLevel+" stop:"+stop+" AudioSineWave.isStarted():"+AudioSineWave.isStarted());

        stop = false;
        calibrated = false;
        detectionFinish = true;                                                                     // Just in case Detection thread running
        calibrateFinish = false;

        headSetConnectionDialog();

        runOnUiThread(new Runnable() {
            public void run() {
                TextView tv = (TextView) findViewById(R.id.calibrategates);
                tv.setText(getString(R.string.calibrate) + " " + timings);

                tv = (TextView) findViewById(R.id.calibrationResults);
                tv.setVisibility(View.INVISIBLE);

                Button b = (Button) findViewById(R.id.retryCalibration);
                b.setEnabled(false);
                b = (Button) findViewById(R.id.okCalibration);
                b.setEnabled(false);
            }
        });

        currentState = CALIBRATINGSTATE;
        toggleVisibility(currentState);

        if (DEBUG.ON)
            System.out.println("0.5 calibrate() calibrated:" + calibrated + " volume:"+volume+" currentState:" + currentState + " detectionLevel:" + detectionLevel+" stop:"+stop+" AudioSineWave.isStarted():"+AudioSineWave.isStarted());

        threadCalibration = new Thread(new Runnable() {                                             // Start background thread that runs whether app has focus or not
            public void run() {

                maxMagnitude = 0;
                AudioSineWaveSynchronized.start();
                AudioSineWave.setVolume(MINIMUMVOLUME);                                             // Start at lowest volume and raise until amplitude is near maximum (15)

                if (DEBUG.ON)
                    System.out.println("1 calibrate() calibrated:" + calibrated + " volume:"+volume+" currentState:" + currentState + " maxMagnitude:"+maxMagnitude+" AudioSineWaveSynchronized.start() "+" AudioSineWave.isStarted():"+AudioSineWave.isStarted());

                DataOutputStream output = null;
                byte[] data = new byte[bufferSize * 2];
                int x;
                int readSize = 0;
                boolean volumeCalibrated = false;
                int time = 0;

                maxMagnitude = 0;                                                                   // State variable

                if (DEBUG.WAV)
                    try {
                        output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(getFile("raw"))));
                    } catch (IOException e) {
                    }

                do {
                    readSize = AudioRecorder.read(data);

                    if (DEBUG.WAV)
                        try {
                            output.write(data, 0, readSize);
                        } catch (IOException e) {
                            if(DEBUG.ON)
                                System.out.println("calibrate() Exception: "+e);
                        }
                    time = time + readSize / 2;
                }
                while (time < RECORDER_SAMPLERATE && !calibrateFinish && !stop);                    // Throw away initial input to allow volume to settle to above setting

                AudioSineWave.setVolume(MINIMUMVOLUME);                                             // Start at lowest volume and raise until amplitude is near maximum (15)

                if (DEBUG.ON)
                    System.out.println("2 calibrate() calibrated:" + calibrated + " volume:"+volume+" currentState:" + currentState + " maxMagnitude:"+maxMagnitude+" AudioSineWaveSynchronized.start() "+" AudioSineWave.isStarted():"+AudioSineWave.isStarted());

                do {
                    readSize = AudioRecorder.read(data);

                    if (DEBUG.WAV)
                        try {
                            output.write(data, 0, readSize);
                        } catch (IOException e) {
                        }

                    for (int i = 0; i < readSize && !stop && !calibrateFinish; i = i + 2) {
                        x = (data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8);                         // Little endian
                        x = x <= 32767 ? x : x - 65535;

                        if (x > maxMagnitude)
                            maxMagnitude = x;
                    }
                                                                                                    // Raise volume until % of maximum amplitude
                    volumeCalibrated = (maxMagnitude <= Short.MAX_VALUE) && (maxMagnitude >= Short.MAX_VALUE * MAXIMUMAMPLITUDEPERCENTAGE);

                    if (DEBUG.ON)
                        System.out.println("3 calibrate() calibrated:" + calibrated + " volume:"+volume+" currentState:" + currentState + " maxMagnitude:"+maxMagnitude+" AudioSineWaveSynchronized.start() "+" AudioSineWave.isStarted():"+AudioSineWave.isStarted());

                    if (!volumeCalibrated)
                        AudioSineWave.raiseVolume();

                    if (DEBUG.ON)
                        System.out.println("4 calibrate() volumeCalibrated: " + volumeCalibrated + " AudioSineWave.getVolume():" + AudioSineWave.getVolume()+ " AudioSineWave.getMaxVolume():" + AudioSineWave.getMaxVolume() + " maxMagnitude:" + maxMagnitude + " readSize:" + readSize+
                                        " Math.round((((double)AudioSineWave.getVolume())/AudioSineWave.getMaxVolume()) * 100))"+String.format("%d", Math.round((((double)AudioSineWave.getVolume())/AudioSineWave.getMaxVolume()) * 100)));

                    final int volumeCurrent = AudioSineWave.getVolume();
                    final int levelCurrent = (int)(((double)volumeCurrent/AudioSineWave.getMaxVolume())*100);
                    final int powerCurrent = (int)((maxMagnitude/Short.MAX_VALUE)*100);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            CalibrationResultsView.setView(findViewById(R.id.calibration_volume).getRootView(), levelCurrent, powerCurrent, null);
                        }
                    });
                }
                while (!volumeCalibrated && AudioSineWave.getVolume() < AudioSineWave.getMaxVolume() && !stop && !calibrateFinish);

                if (DEBUG.WAV) {
                    if (output != null) {
                        try {
                            output.flush();
                        } catch (IOException e) {
                        } finally {
                            try {
                                output.close();
                            } catch (IOException e) {
                            }
                        }
                    }
                    try {
                        rawToWave(getFile("raw"), getFile("wav"));
                    } catch (Exception e) {
                    }
                }

                if (DEBUG.ON) System.out.flush();

                final boolean volumeCalibratedFinal = volumeCalibrated;
                final int levelCurrent = (int)(((double)AudioSineWave.getVolume()/AudioSineWave.getMaxVolume())*100);
                final int powerCurrent = (int)((maxMagnitude/Short.MAX_VALUE)*100);

                currentState = CALIBRATIONCOMPLETED;
                toggleVisibility(currentState);
                
                if (!calibrateFinish)
                    runOnUiThread(new Runnable() {
                        public void run() {

                            final TextView tv = (TextView) findViewById(R.id.calibrationResults);
                            String calibrationResults;

                            if (maxMagnitude < BROKENCIRCUITPERCENTAGE * Short.MAX_VALUE)
                                calibrationResults = getString(R.string.calibrationresults_nocircuit);
                            else
                                calibrationResults = powerCurrent < 85 ? getString(R.string.calibrationresults_fail) : getString(R.string.calibrationresults_ok);

                            tv.setVisibility(View.VISIBLE);
                            CalibrationResultsView.addResults(calibrationResults);

                            if (volumeCalibratedFinal && !stop && isHeadSetConnected()) {
                                if (DEBUG.ON)
                                    System.out.println("6 calibrate() success volume: " + volume + " AudioSineWave.getVolume():" + AudioSineWave.getVolume()+" levelCurrent:"+levelCurrent+" powerCurrent:"+powerCurrent);
                                volume = AudioSineWave.getVolume();
                            }
                        }
                    });
                AudioSineWaveSynchronized.stop();

                runOnUiThread(new Runnable() {
                    public void run() {
                        Button b = (Button) findViewById(R.id.retryCalibration);
                        b.setEnabled(true);
                        b = (Button) findViewById(R.id.okCalibration);
                        b.setEnabled(true);
                    }
                });
            }
        }, "GateTiming threadCalibration");
        threadCalibration.start();
    }

    private void toggleVisibility(final int visible) {
        if(DEBUG.ON) System.out.println("toggleVisibility() currentState:"+currentState+" visible:"+visible);
        runOnUiThread( new Runnable() {
            public void run() {
                MenuItem item;

                timingResultsTableLayout.setVisibility(View.GONE);
                simpletimingResultsTableLayout.setVisibility(View.GONE);
                startbutton.setVisibility(View.GONE);
                completedbutton.setVisibility(View.GONE);
                calibrationLayout.setVisibility(View.GONE);
                detectionResultsTableLayout.setVisibility(View.GONE);

                switch(visible) {
                    case STARTSTATE : {
                        startbutton.setVisibility(View.VISIBLE);
                        if(optionsMenu != null) {                                                   // ignore until optionsMenu != null when Menu is displayed.
                            item = optionsMenu.findItem(R.id.action_calibrate);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_view_list);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_stop);
                            item.setIcon(R.drawable.ic_play_arrow_white_24dp);                      // Toggle to start timings
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_timings);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_delete);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_repeat);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                        }
                        break;
                    }
                    case COLLECTINGSTATE : {
                        if(simpleListDisplay)
                            simpletimingResultsTableLayout.setVisibility(View.VISIBLE);
                        else
                            timingResultsTableLayout.setVisibility(View.VISIBLE);
                        if(optionsMenu != null) {
                            item = optionsMenu.findItem(R.id.action_calibrate);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_view_list);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_stop);
                            item.setIcon(R.drawable.ic_pause_white_24dp);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_timings);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_delete);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_repeat);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                        }
                        break;
                    }
                    case COMPLETEDSTATE : {
                        completedbutton.setVisibility(View.VISIBLE);
                        break;
                    }
                    case CALIBRATINGSTATE :
                    case CALIBRATIONCOMPLETED : {
                        calibrationLayout.setVisibility(View.VISIBLE);
                        if(optionsMenu != null) {
                            item = optionsMenu.findItem(R.id.action_calibrate);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_view_list);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_stop);
                            item.setIcon(R.drawable.ic_pause_white_24dp);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_timings);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_delete);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_repeat);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                        }
                        break;
                    }

                    case DETECTINGSTATE: {
                        detectionResultsTableLayout.setVisibility(View.VISIBLE);
                        TextView tv = (TextView) findViewById(R.id.detectingGates);
                        tv.setText(getString(R.string.detectingGates)+" "+timings);

                        if(optionsMenu != null) {
                            item = optionsMenu.findItem(R.id.action_calibrate);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_view_list);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_stop);
                            item.setIcon(R.drawable.ic_pause_white_24dp);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_timings);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_delete);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_repeat);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                        }
                        break;
                    }

                    case LISTINGSTATE : {
                        if(simpleListDisplay)
                            simpletimingResultsTableLayout.setVisibility(View.VISIBLE);
                        else
                            timingResultsTableLayout.setVisibility(View.VISIBLE);
                        if(optionsMenu != null){
                            item = optionsMenu.findItem(R.id.action_calibrate);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_view_list);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_stop);
                            item.setIcon(R.drawable.ic_play_arrow_white_24dp);                      // Toggle to start timings
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_timings);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_delete);
                            item.setEnabled(true);
                            item.getIcon().setAlpha(255);
                            item = optionsMenu.findItem(R.id.action_repeat);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);
                            item = optionsMenu.findItem(R.id.action_repeat);
                            item.setEnabled(false);
                            item.getIcon().setAlpha(130);                        }
                        break;
                    }
                }
            }
        });
    }

    private String getFilename(String ext) {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, GATETIMING_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }
        return (file.getAbsolutePath() + "/" + System.currentTimeMillis() + ext);
    }

    private void rawToWave(final File rawFile, final File waveFile) throws IOException {

        int read;
        byte[] rawData = new byte[(int) rawFile.length()];
        DataInputStream input = null;
        try {
            input = new DataInputStream(new FileInputStream(rawFile));
            read = input.read(rawData);
        } finally {
            if (input != null) {
                input.close();
                rawFile.delete();
            }
        }

		DataOutputStream output = null;
		try {
			output = new DataOutputStream(new FileOutputStream(waveFile));
			// WAVE header
			// see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
			writeString(output, "RIFF"); // chunk id
			writeInt(output, 36 + rawData.length); // chunk size
			writeString(output, "WAVE"); // format
			writeString(output, "fmt "); // subchunk 1 id
			writeInt(output, 16); // subchunk 1 size
			writeShort(output, (short) 1); // audio format (1 = PCM)
			writeShort(output, (short) 1); // number of channels
			writeInt(output, RECORDER_SAMPLERATE); // sample rate
			writeInt(output, RECORDER_SAMPLERATE * 2); // byte rate
			writeShort(output, (short) 2); // block align
			writeShort(output, (short) 16); // bits per sample
			writeString(output, "data"); // subchunk 2 id
			writeInt(output, rawData.length); // subchunk 2 size

            output.write(rawData, 0, read);

		} finally {
			if (output != null) {
				output.close();
			}
		}
	}

	private File getFile(final String suffix) {
		return new File(Environment.getExternalStorageDirectory(), GATETIMING_FOLDER + "." + suffix);
	}

    private void saveTiming(String outFilename) {
        PrintStream out = null;
        String [] columns = null;

        if(listViewAdapter == null) {
            if(simpleListDisplay)
                listViewAdapter = new SimpleListViewAdapter(GateTimingActivity.this, timings, listView, N, lows, highs, epochMilliseconds);
            else
                listViewAdapter = new ListViewAdapter(GateTimingActivity.this, timings, listView, N, lows, highs, epochMilliseconds);
            listViewAdapter.addAverages(lows, highs);
        }

        try {
            out = new PrintStream(new FileOutputStream(outFilename));

            if (simpleListDisplay) {
                columns = new String[3];

                columns[0] = (String) getString(R.string.datetime_label);
                columns[1] = (String) getString(R.string.run);
                columns[2] = (String) getString(R.string.time);
            }
            else {
                columns = new String[7];
                columns[0] = (String) getString(R.string.datetime_label);
                columns[1] = (String) getString(R.string.elapsed_label);
                columns[2] = (String) getString(R.string.gate_label);
                columns[3] = (String) getString(R.string.enter_label);
                columns[4] = (String) getString(R.string.difference_label);
                columns[5] = (String) getString(R.string.exit_label);
                columns[6] = (String) getString(R.string.difference_label);
            };

            for (int i = 0; i < columns.length - 1; i++)                                            // Print headings
                out.print(columns[i] + ",");
            out.println(columns[columns.length - 1]);

            System.out.println(columns);

            for(int n = 0; n<listViewAdapter.getCount(); n++) {
                columns = (String[]) listViewAdapter.getItem(n);

                if(!simpleListDisplay)
                    out.print(Instant.ofEpochMilli(epochMilliseconds[n]).toString()+",");           // Add date/time to detailed list
                for (int i = 0; i < columns.length - 1; i++)
                    out.print(columns[i] + ",");
                out.println(columns[columns.length - 1]);
            }

            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeInt(final DataOutputStream output, final int value) throws IOException {
		output.write(value >> 0);
		output.write(value >> 8);
		output.write(value >> 16);
		output.write(value >> 24);
	}

	private void writeShort(final DataOutputStream output, final short value) throws IOException {
		output.write(value >> 0);
		output.write(value >> 8);
	}

	private void writeString(final DataOutputStream output, final String value) throws IOException {
		for (int i = 0; i < value.length(); i++) {
			output.write(value.charAt(i));
		}
	}
}