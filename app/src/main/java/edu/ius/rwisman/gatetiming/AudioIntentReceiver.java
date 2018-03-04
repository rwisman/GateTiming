package edu.ius.rwisman.gatetiming;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;

class AudioIntentReceiver extends BroadcastReceiver {
    GateTimingActivity ACTIVITY=null;
    Context CONTEXT;
    int FREQUENCY;
    int RECORDER_SAMPLERATE;
    int LOOPCOUNT;
    int waves;
    private AlertDialog alertDialog=null;

    public AudioIntentReceiver(GateTimingActivity activity, Context context, int RECORDER_SAMPLERATE, int FREQUENCY, int LOOPCOUNT, int waves){
        this.RECORDER_SAMPLERATE=RECORDER_SAMPLERATE;
        this.CONTEXT=context;
        this.ACTIVITY = activity;
        this.FREQUENCY=FREQUENCY;
        this.LOOPCOUNT=LOOPCOUNT;
        this.waves=waves;
//        AudioSineWave.initialize(CONTEXT, RECORDER_SAMPLERATE,FREQUENCY,LOOPCOUNT,waves);
    }

    public AudioIntentReceiver(Context context){
        this.CONTEXT=context;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(DEBUG.ON) System.out.println("AudioIntentReceiver onReceive() intent.getAction():"+intent.getAction());
        if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
            int state = intent.getIntExtra("state", -1);
            switch (state) {
                case 0: 								// Headset unplugged
                    headsetUnplugged(ACTIVITY);
                    AudioSineWave.stop();
                    if(DEBUG.ON) System.out.println("AudioIntentReceiver onReceive() headSet unplugged state:"+state);
                    testHeadSetConnectionDialog();
                    break;
                case 1: 								// Headset is plugged
                    AudioSineWave.initialize(ACTIVITY, context,RECORDER_SAMPLERATE,FREQUENCY,4,10);
                    if(DEBUG.ON) System.out.println("AudioIntentReceiver onReceive() headSet plugged state:"+state);
                    break;
                default: 								// Unknown headset state
            }
        }
    }

    private boolean isHeadSetConnected() {
        return (((AudioManager) CONTEXT.getSystemService(Context.AUDIO_SERVICE)).isWiredHeadsetOn());
    }

    private void testHeadSetConnectionDialog() {
        if (!isHeadSetConnected()) {                                                                // Headset !plugged in
            ACTIVITY.runOnUiThread(new Runnable() {
                public void run() {
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ACTIVITY);
                    alertDialogBuilder
                            .setCancelable(false)
                            .setIcon(R.drawable.ic_headset_white_24dp)
                            .setTitle(R.string.connectHeadSetFailed)
                            .setMessage(R.string.connectHeadSetFailed_text)
                            .setPositiveButton(ACTIVITY.getString(R.string.ok),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.dismiss();
                                            dialog.cancel();
                                            testHeadSetConnectionDialog();
                                        }
                                    })
                            .setNegativeButton(ACTIVITY.getString(R.string.cancel),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.dismiss();
                                            dialog.cancel();
                                        }
                                    });
                    alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                }
            });
        }
    }

    private void headsetUnplugged(final AudioLoss callback) {
        callback.audioLoss();
    }
}