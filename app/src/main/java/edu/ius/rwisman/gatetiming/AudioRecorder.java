package edu.ius.rwisman.gatetiming;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * Created by Ray Wisman on 11/12/17.
 */

class AudioRecorder {
    private final static int[] mSampleRates = new int[] { 44100, 22050, 11025, 8000 };

    static AudioRecord mRecorder=null;
    static int RECORDER_SAMPLERATE;
    static int bufferSize;
    static byte [] data = null;

    public static void initialize() {
        if(mRecorder == null) {
            mRecorder = findAudioRecord();

            mRecorder.startRecording();
        }
    }

    public static int recorderSampleRate() {
        return mRecorder.getSampleRate();
    }

    public static int getRECORDER_SAMPLERATE() {
        return RECORDER_SAMPLERATE;
    }

    public static AudioRecord getRecorder() {
        return mRecorder;
    }

    public static void finished() {
        try {
            if (mRecorder != null) {
                mRecorder.stop();
                mRecorder.release();
                mRecorder = null;
            }
        } catch (IllegalStateException ise) {
  //          if(DEBUG.ON) System.out.println("AudioRecorder finished IllegalStateException");
        }
    }
    public static int getBufferSize() {
        return bufferSize;
    }

    public static byte [] getData() {
        return data;
    }

    public static int read(byte data[]) {
        try {
            switch(AudioRecorder.getRecorder().getRecordingState()) {
                case 1:
                    while (AudioRecorder.getRecorder().getRecordingState() == 1) {
                        AudioRecorder.getRecorder().startRecording();
                    }
                    return mRecorder.read(data, 0, data.length);
                case 3:
                    return mRecorder.read(data, 0, data.length);
            }
        } catch (IllegalStateException ise) {
            if(DEBUG.ON) System.out.println("AudioRecorder read IllegalStateException");
        }
        catch (Exception e) {
            if(DEBUG.ON) System.out.println("AudioRecorder read Exception:"+e);
            initialize();
        }
        return 0;
    }

    static private AudioRecord findAudioRecord() {
        if(DEBUG.ON) System.out.println("AudioRecorder findAudioRecord()");

        for (int rate : mSampleRates) {
            try {
                bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

                if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                    // check if we can instantiate and have a success
                    AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, bufferSize);

                    RECORDER_SAMPLERATE = rate;

                    if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                        bufferSize = bufferSize * 2;                                           // Just a little bigger just in case
                        return recorder;
                    }
                }
            } catch (Exception e) {
                if(DEBUG.ON) System.out.println("AudioRecorder findAudioRecord() rate:"+rate+" bufferSize:"+bufferSize+" error:"+e);
            }
        }
        return null;
    }

    public static String getStatus() {
        if(DEBUG.ON) return "AudioRecord"+
                "\ngetAudioFormat():" + mRecorder.getAudioFormat() +
//                "\ngetAudioSessionId():"+mRecorder.getAudioSessionId()+
                "\ngetAudioSource():"+mRecorder.getAudioSource()+
//                "\ngetBufferSizeInFrames():"+mRecorder.getBufferSizeInFrames()+
                "\ngetChannelConfiguration():"+mRecorder.getChannelConfiguration()+
                "\ngetChannelCount():"+mRecorder.getChannelCount()+
//                "\ngetFormat():"+mRecorder.getFormat()+
                "\ngetMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat):"+mRecorder.getMinBufferSize(RECORDER_SAMPLERATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)+
                "\ngetNotificationMarkerPosition():"+mRecorder.getNotificationMarkerPosition()+
                "\ngetPositionNotificationPeriod():"+mRecorder.getPositionNotificationPeriod()+
///                "\ngetPreferredDevice():"+mRecorder.getPreferredDevice()+
                "\ngetRecordingState():"+mRecorder.getRecordingState()+
//                "\ngetRoutedDevice():"+mRecorder.getRoutedDevice()+
                "\ngetSampleRate():"+mRecorder.getSampleRate()+
                "\ngetState():"+mRecorder.getState();
        return "";
    }
}
