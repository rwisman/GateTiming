package edu.ius.rwisman.gatetiming;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

/**
 * Created by rwisman on 1/21/18.
 */

public class CalibrationResultsView {

    static int VOLUMELEVEL=0;
    static int POWER=0;
    static String RESULTS;
    static View VIEW=null;

    static public void setView(View view, int volumelevel, int power, String results) {
        VIEW = view;
        VOLUMELEVEL = volumelevel;
        POWER = power;
        RESULTS = results;
        resetView(VIEW);
    }

    static public void resetView(View VIEW) {
        if(VIEW==null) return;

//        if(DEBUG.ON)
//            System.out.println("CalibrationResultsView resetView() VOLUME:"+VOLUME+" VIEW:"+VIEW);

        TextView tv = (TextView) VIEW.findViewById(R.id.calibration_volume);
        tv.setText(VOLUMELEVEL + "%");

        tv = (TextView) VIEW.findViewById(R.id.calibration_power);
        tv.setText(POWER + "%");

        tv = (TextView)VIEW.findViewById(R.id.calibration_rating);
        TextView tv2 = (TextView) VIEW.findViewById(R.id.calibration_color);

        if (POWER < 70) {
            tv.setText(R.string.fail);
            tv2.setBackgroundColor(Color.RED);
        }
        else if (POWER < 85) {
            tv.setText(R.string.poor);
            tv2.setBackgroundColor(Color.YELLOW);
        }
        else {
            tv.setText(R.string.good);
            tv2.setBackgroundColor(Color.GREEN);
        }

        tv = (TextView) VIEW.findViewById(R.id.calibrationResults);
        tv.setText(RESULTS);
    }

    static public void addResults(String results) {
        RESULTS = results;
        resetView(VIEW);
    }
}
