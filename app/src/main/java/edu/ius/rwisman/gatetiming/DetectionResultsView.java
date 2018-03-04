package edu.ius.rwisman.gatetiming;

import android.view.View;
import android.widget.TextView;

/**
 * Created by rwisman on 1/23/18.
 */

public class DetectionResultsView {
    static String RESULTS;
    static View VIEW=null;

    static public void setView(View view, String results) {
        VIEW = view;
        RESULTS = results;
        resetView(VIEW);
    }

    static public void resetView(View VIEW) {
        if(VIEW==null) return;

//        if(DEBUG.ON)
//            System.out.println("DetectionResultsView resetView() results:"+RESULTS);

        TextView tv = (TextView) VIEW.findViewById(R.id.detectionResults);
        tv.setText(RESULTS);
    }

    static public void addResults(String results) {
        RESULTS = results;
        resetView(VIEW);
    }
}
