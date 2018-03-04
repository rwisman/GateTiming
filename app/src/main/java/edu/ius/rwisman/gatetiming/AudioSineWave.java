/*
 *     Copyright (C) 2017  Raymond Wisman
 * 			Indiana University SE
 * 			April 7, 2017
 *
 * 	AudioSineWave produces a sine wave on audio.

    Credits: http://stackoverflow.com/questions/20889627/playing-repeated-audiotrack-in-android-activity

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details <http://www.gnu.org/licenses/>.

 */

    /*
        Static class data is not reclaimed or altered when Activity onDestroy method is called.
	    Each static class variable is normally created only once and
        maintains its binding while the Activity object is destroyed and recreated (e.g. after screen rotation).
     */

package edu.ius.rwisman.gatetiming;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.widget.Toast;

class AudioSineWave {                                                                               // Some phones require more than a single wave in buffer (e.g. Nexus 5)
    static AudioTrack audioTrack=null;
    static AudioManager audioManager=null;
    static byte [] sineWave=null;
    static int wavesSAMPLES;
    static Context context=null;
    static GateTimingActivity ACTIVITY = null;
    static AudioManager.OnAudioFocusChangeListener listener=null;
    static boolean started = false;

    public static void initialize(final GateTimingActivity activity, final Context CONTEXT, final int RECORDER_SAMPLERATE,             // AudioSineWave.initialize(activity,44100,2000,4,10);
                             final int FREQUENCY, final int LOOPCOUNT, final int waves) {
        final int SAMPLES = RECORDER_SAMPLERATE/FREQUENCY;
        wavesSAMPLES = waves*SAMPLES;
        context = CONTEXT;
        ACTIVITY = activity;

        if(sineWave == null)
            sineWave = sineWave(SAMPLES, waves);

        if(audioManager == null) {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            ACTIVITY.setVolumeControlStream(AudioManager.STREAM_MUSIC);

            listener = new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if(DEBUG.ON) System.out.println("AudioSineWave AUDIOFOCUS_GAIN");
//                            start();
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                            if(DEBUG.ON) System.out.println("AudioSineWave AUDIOFOCUS_GAIN_TRANSIENT");
                            // You have audio focus for a short time
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                            if(DEBUG.ON) System.out.println("AudioSineWave AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS:
                            if(DEBUG.ON) System.out.println("AudioSineWave AUDIOFOCUS_LOSS");

                            ACTIVITY.runOnUiThread(new Runnable() {
                                                       public void run() {
                                                           Toast.makeText(ACTIVITY, R.string.audioLoss, Toast.LENGTH_LONG).show();
                                                       }
                                                   });

                            stop();
                            audioFocusLoss(ACTIVITY);
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            if(DEBUG.ON) System.out.println("AudioSineWave AUDIOFOCUS_LOSS_TRANSIENT");
                            // Temporary loss of audio focus - expect to get it back - you can keep your resources around
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            if(DEBUG.ON) System.out.println("AudioSineWave AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                            // Lower the volume
                            break;
                    }
                }

            };

            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);         // Request permanent focus

            if (DEBUG.ON) {
                int result = audioManager.requestAudioFocus(listener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);

                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                    System.out.println("AudioSineWave AUDIOFOCUS_REQUEST_GRANTED");
                if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                    System.out.println("AudioSineWave AUDIOFOCUS_REQUEST_FAILED");
            }
        }

        if(audioTrack == null) {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, RECORDER_SAMPLERATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, waves * SAMPLES * 2, AudioTrack.MODE_STATIC);
            audioTrack.write(sineWave, 0, sineWave.length);
        }
    }

    public static int audioSampleRate() {
        return audioTrack.getSampleRate();
    }

    public static boolean isVolumeFixed() {
        int volume = getVolume();

        raiseVolume();
        if(getVolume() != volume) {
            lowerVolume();
            return false;
        }
        lowerVolume();
        if(getVolume() != volume) {
            raiseVolume();
            return false;
        }
        return true;
    }

    public static void setVolume( final double VOLUME) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                (int)(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*VOLUME),			// Set to fractional max volume
                AudioManager.MODE_NORMAL);
    }

    public static boolean setVolume( final int VOLUME) {                                            // Return false if setting volume fails

        if(DEBUG.ON) System.out.println("AudioSineWave getVolume():"+getVolume()+" started:"+started);
        if(isVolumeFixed() || VOLUME > getMaxVolume()) return false;

        while(getVolume() > VOLUME)                                                                 // Lower to requested
            lowerVolume();

        while(getVolume() < VOLUME)
            raiseVolume();

        return true;
    }

    public static void raiseVolume() {
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
        return;
    }

    public static void lowerVolume() {
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
        return;
    }

    public static int getVolume() {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public static int getMaxVolume() {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    public static boolean isStarted() {
        return started;
    }

    public static void start() {                                                                                                // Call before setVolume()

        if(!(((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).isWiredHeadsetOn())) return;                      // Do not generate sound if no headset jack attached

        int result = audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);         // Request permanent focus

        if (DEBUG.ON)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                System.out.println("AudioSineWave AUDIOFOCUS_REQUEST_GRANTED");
            else if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                System.out.println("AudioSineWave AUDIOFOCUS_REQUEST_FAILED");

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioTrack.setLoopPoints(0, wavesSAMPLES , -1);
            audioTrack.reloadStaticData();
            audioTrack.play();
            started = true;
        }
    }

    public static void stop() {
        started = false;
        audioTrack.stop();
        audioManager.abandonAudioFocus(listener);
    }

    public static void focus() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        AudioManager.OnAudioFocusChangeListener listener = new  AudioManager.OnAudioFocusChangeListener(){
            @Override
            public void onAudioFocusChange(int i) {
                if(DEBUG.ON) System.out.println("AudioSineWave FOCUS CHANGED:"+i);
            }
        };

        am.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN );
    }

    private static byte[] sineWave(int samplesPerWave, int waves) {
        int samples = samplesPerWave*waves;
        final double sample[] = new double[samples];
        final byte sineWave[] = new byte[2 * samples];

        double x;
        double xStep = (waves * 2 * Math.PI)/samples;
        int i;

        for (x=0.0, i=0; waves * 2 * Math.PI - x > 0.0001; x=x+xStep, ++i)
            sample[i] = Math.sin(x);

        // Convert normalized samples to 16 bit PCM data
        int idx = 0;
        for (double dVal : sample) {
            short val = (short) (dVal * 32767);
            sineWave[idx++] = (byte) (val & 0x00ff);
            sineWave[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        return sineWave;
    }

    static public void audioFocusLoss(final AudioLoss callback) {                                         //
        callback.audioLoss();
    }
}

