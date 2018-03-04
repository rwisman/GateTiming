package edu.ius.rwisman.gatetiming;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by rwisman on 12/14/17.
 */

public class SplashActivity extends AppCompatActivity {

    Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(Preferences.getPreference(this, "doNotShowAgain")) {
            intent = new Intent(this, GateTimingActivity.class);
        }
        else {
            intent=new Intent(this, SetupActivity.class);
        }
        startActivity(intent);
        finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG.ON)
            System.out.println("onResume showAgain:"+ Preferences.getPreference(this, "doNotShowAgain"));
    }
}