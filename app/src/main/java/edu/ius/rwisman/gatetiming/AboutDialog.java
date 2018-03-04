package edu.ius.rwisman.gatetiming;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

public class AboutDialog extends Dialog {

    private static Context mContext = null;

    public AboutDialog(Context context) {
        super(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setCanceledOnTouchOutside(true);
        setCancelable(true);
        setContentView(R.layout.about);
/*      TextView tv = (TextView)findViewById(R.id.about_text);

        tv.setText(readRawTextFile(R.raw.legal));
        tv = (TextView)findViewById(R.id.about_text);
        tv.setText(Html.fromHtml(readRawTextFile(R.raw.about),  FROM_HTML_MODE_LEGACY));
        tv.setLinkTextColor(Color.BLUE);
        Linkify.addLinks(tv, Linkify.ALL);
        */
    }
}
