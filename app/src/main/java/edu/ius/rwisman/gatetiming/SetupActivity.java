package edu.ius.rwisman.gatetiming;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ViewFlipper;

/**
 * Created by rwisman on 1/5/18.
 *
 * Performs 4 tests and provides user opportunity to enable or correct each prior to starting GateTiming app.
 *
 * Tests performed on:
 * 1) Headset circuit connected
 * 2) Airplane mode ON
 * 3) RECORD permission
 * 4) WRITE Storage permission
 */

public class SetupActivity extends AppCompatActivity {

    private final static int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private final static int REQUEST_RECORD =                 2;

    CheckBox doNotShowAgainCheckBox;
    ViewFlipper viewFlipper;

    boolean doNotShowAgain = false;
    boolean setupStartClicked = false;
    boolean firstTimeRecordRequest = true;
    boolean firstTimeStorageRequest = true;

    final int HEADSET =     0;
    final int AIRPLANE =    1;
    final int RECORD =      2;
    final int STORAGE =     3;

    boolean [] testResults = {false, false, false, false};
    boolean [] ignoreResults = {false, false, false, false};

    @Override
    public void onCreate(final Bundle savedInstanceState) {

        if (DEBUG.ON) System.out.println("SetupActivity onCreate");

        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.setup);

        viewFlipper = (ViewFlipper) findViewById(R.id.view_flipper);

        ((CheckBox) findViewById(R.id.check_noshow)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (DEBUG.ON)
                    System.out.println("SetupActivity doNotShowAgainCheckBox.onCheckedChanged isChecked:" + isChecked);
                doNotShowAgain = isChecked;
            }
        });

        testResults[HEADSET] = headsetConnection();
        testResults[AIRPLANE] = airplaneMode();
        testResults[RECORD] = recordPermission();
        testResults[STORAGE] = storagePermission();

        if(testResults[HEADSET]) ((CheckBox) findViewById(R.id.check_headset)).setVisibility(View.INVISIBLE);
        if(testResults[AIRPLANE]) ((CheckBox) findViewById(R.id.check_airplane)).setVisibility(View.INVISIBLE);
        if(testResults[RECORD]) ((CheckBox) findViewById(R.id.check_record)).setVisibility(View.INVISIBLE);
        if(testResults[STORAGE]) ((CheckBox) findViewById(R.id.check_storage)).setVisibility(View.INVISIBLE);

        boolean allDone = true;
        for(int i=HEADSET; i<=STORAGE;i++)
            allDone = allDone && testResults[i];

        if(allDone)
            closesetup(null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        if(DEBUG.ON) System.out.println("Setup onSaveInstanceState setupStartClicked:"+setupStartClicked);
        super.onSaveInstanceState(outState);

        outState.putBoolean("setupStartClicked", setupStartClicked);
        outState.putBoolean("firstTimeRecordRequest", firstTimeRecordRequest);
        outState.putBoolean("firstTimeStorageRequest", firstTimeStorageRequest);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {

        super.onRestoreInstanceState(savedInstanceState);

        setupStartClicked = savedInstanceState.getBoolean("setupStartClicked");
        firstTimeRecordRequest = savedInstanceState.getBoolean("firstTimeRecordRequest");
        firstTimeStorageRequest = savedInstanceState.getBoolean("firstTimeStorageRequest");

        if(DEBUG.ON) System.out.println("Setup onRestoreInstanceState setupStartClicked:"+setupStartClicked);
    }

    @Override
    public void onResume() {
        if (DEBUG.ON) System.out.println("SetupActivity onResume");
        super.onResume();

        if(setupStartClicked) {
            ((Button) findViewById(R.id.start)).setVisibility(View.INVISIBLE);
            checkTestResults();
        }
        else
            viewFlipper.setDisplayedChild(viewFlipper.indexOfChild(findViewById(R.id.scrollviewSTART)));
    }

    private void checkTestResults() {

        testResults[HEADSET] = headsetConnection();
        testResults[AIRPLANE] = airplaneMode();
        testResults[RECORD] = recordPermission();
        testResults[STORAGE] = storagePermission();

        int test;
        for(test=HEADSET; test <= STORAGE && (testResults[test] || ignoreResults[test]); test++);   // Find first problem

        if(test <= STORAGE)                                                                         // Problem found with one of the 4 tests
            viewFlipper.setDisplayedChild(test);
        else {                                                                                      // All fixed or ignored
            viewFlipper.setDisplayedChild(viewFlipper.indexOfChild(findViewById(R.id.scrollviewFINISHED)));

            ((CheckBox) findViewById(R.id.check_headset_finish)).setChecked(testResults[HEADSET]);
            ((CheckBox) findViewById(R.id.check_airplane_finish)).setChecked(testResults[AIRPLANE]);
            ((CheckBox) findViewById(R.id.check_record_finish)).setChecked(testResults[RECORD]);
            ((CheckBox) findViewById(R.id.check_storage_finish)).setChecked(testResults[STORAGE]);

            boolean allDone = true;
            for(int i=HEADSET; i<=STORAGE;i++)
                allDone = allDone && testResults[i];

            if(!allDone)
                ((Button) findViewById(R.id.start)).setVisibility(View.VISIBLE);
        }
    }

    public void start(View view) {
        if (DEBUG.ON) System.out.println("SetupActivity START Button.onClick");

        if(setupStartClicked) {                                                                     // Have finished but start again
            for(int i=HEADSET; i<=STORAGE;i++)
                ignoreResults[i] = false;
        }
        ((Button) findViewById(R.id.start)).setVisibility(View.INVISIBLE);
        setupStartClicked=true;
        checkTestResults();
    }

    private boolean headsetConnection() {
        return ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).isWiredHeadsetOn();
    }
    private boolean airplaneMode() {
        return Settings.System.getInt(this.getApplicationContext().getContentResolver(), android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private boolean recordPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            return true;
        else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            if (DEBUG.ON)
                System.out.println("SetupActivity recordPermission()" + "onPermissionRationale()");
        } else if (firstTimeRecordRequest) {
            if (DEBUG.ON)
                System.out.println("SetupActivity " + "firstTime");
        } else {
            if (DEBUG.ON)
                System.out.println("SetupActivity recordPermission()" + "onPermissionDisabled()");
            ((TextView) findViewById(R.id.record_title)).setText(R.string.record_audio_disabled_title);
            ((Button) findViewById(R.id.record_retry_button)).setVisibility(View.INVISIBLE);
            ((TextView) findViewById(R.id.record_text)).setText(R.string.record_audio_disabled_text);
        }
        return false;
    }

    private boolean storagePermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            return true;
        else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            if (DEBUG.ON)
                System.out.println("SetupActivity storagePermission() " + "onPermissionRationale()");
            return false;
        } else if (firstTimeStorageRequest) {
            if (DEBUG.ON)
                System.out.println("SetupActivity storagePermission() " + "firstTime");
        } else {
            if (DEBUG.ON)
                System.out.println("SetupActivity storagePermission() " + "onPermissionDisabled()");
            ((TextView) findViewById(R.id.storage_title)).setText(R.string.write_external_storage_disabled_title);
            ((Button) findViewById(R.id.storage_retry_button)).setVisibility(View.INVISIBLE);
            ((TextView) findViewById(R.id.storage_text)).setText(R.string.write_external_storage_disabled_text);
        }
        return false;
    }

    public void ignoreHeadsetConnection(View view)  {
        ignoreResults[HEADSET] = true;
        checkTestResults();
    }
    public void ignoreAirplaneMode(View view)       {
        ignoreResults[AIRPLANE] = true;
        checkTestResults();
    }
    public void ignoreRecordPermission(View view)   {
        ignoreResults[RECORD] = true;
        checkTestResults();
    }
    public void ignoreStoragePermission(View view)  {
        ignoreResults[STORAGE] = true;
        checkTestResults();
    }
    public void retryHeadsetConnection(View view) {
        testResults[HEADSET] = headsetConnection();
        checkTestResults();
    }
    public void retryAirplaneMode(View view) {                                                        // Results delayed
        if (DEBUG.ON) System.out.println("SetupActivity retryAirplaneMode");
        Intent airPlaneModeIntent = new Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        airPlaneModeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getApplicationContext().startActivity(airPlaneModeIntent);
    }
    public void retryRecordPermission(final View view) {
        PermissionUtil.checkPermission(this, Manifest.permission.RECORD_AUDIO,
                new PermissionUtil.PermissionAskListener() {
                    @Override
                    public void onNeedPermission() {
                        firstTimeRecordRequest = false;
                        if(DEBUG.ON)
                            System.out.println("SetupActivity "+Manifest.permission.RECORD_AUDIO+" onNeedPermission()");
                        ActivityCompat.requestPermissions( SetupActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD);
                    }
                    @Override
                    public void onPermissionPreviouslyDenied() {
                        if(DEBUG.ON)
                            System.out.println("SetupActivity "+Manifest.permission.RECORD_AUDIO+" onPermissionPreviouslyDenied()");
                        ActivityCompat.requestPermissions( SetupActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD);
                    }
                    @Override
                    public void onPermissionDisabled() {
                        if (DEBUG.ON)
                            System.out.println("SetupActivity "+"onPermissionDisabled()");
                        testResults[RECORD] = false;
                        ((TextView)findViewById(R.id.record_title)).setText(R.string.record_audio_disabled_title);
                        ((Button)findViewById(R.id.record_retry_button)).setVisibility(View.INVISIBLE);
                        ((TextView)findViewById(R.id.record_text)).setText(R.string.record_audio_disabled_text);
                        checkTestResults();
                    }

                    @Override
                    public void onPermissionGranted() {
                        if(DEBUG.ON)
                            System.out.println("SetupActivity "+Manifest.permission.RECORD_AUDIO+" onPermissionGranted()");
                        testResults[RECORD] = true;
                        checkTestResults();
                    }
                });
    }

    public void retryStoragePermission(View view) {
        PermissionUtil.checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                new PermissionUtil.PermissionAskListener() {
                    @Override
                    public void onNeedPermission() {
                        firstTimeStorageRequest = false;
                        if(DEBUG.ON)
                            System.out.println("SetupActivity "+Manifest.permission.WRITE_EXTERNAL_STORAGE+" onNeedPermission()");
                        ActivityCompat.requestPermissions( SetupActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE);
                    }
                    @Override
                    public void onPermissionPreviouslyDenied() {
                        if(DEBUG.ON)
                            System.out.println("SetupActivity "+Manifest.permission.WRITE_EXTERNAL_STORAGE+" onPermissionPreviouslyDenied()");
                        ActivityCompat.requestPermissions( SetupActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE);
                    }
                    @Override
                    public void onPermissionDisabled() {
                        if (DEBUG.ON)
                            System.out.println("SetupActivity "+"onPermissionDisabled()");
                        testResults[STORAGE] = false;
                        ((TextView)findViewById(R.id.storage_title)).setText(R.string.write_external_storage_disabled_title);
                        ((Button)findViewById(R.id.storage_retry_button)).setVisibility(View.INVISIBLE);
                        ((TextView)findViewById(R.id.storage_text)).setText(R.string.write_external_storage_disabled_text);
                        checkTestResults();
                    }

                    @Override
                    public void onPermissionGranted() {
                        if(DEBUG.ON)
                            System.out.println("SetupActivity "+Manifest.permission.WRITE_EXTERNAL_STORAGE+" onPermissionGranted()");
                        testResults[STORAGE] = true;
                        checkTestResults();
                    }
                });
    }

    public void closesetup(View v) {
        if (DEBUG.ON) System.out.println("SetupActivity closesetup Button.onClick");
        Preferences.setPreference(SetupActivity.this, "doNotShowAgain", doNotShowAgain);

        Intent intent = new Intent(SetupActivity.this, GateTimingActivity.class);
        startActivity(intent);
        finish();
    }
}