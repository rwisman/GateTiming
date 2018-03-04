package edu.ius.rwisman.gatetiming;

/**
 * Created by rwisman on 1/19/18.
 */

public class AudioSineWaveSynchronized {
    private boolean started = false;

    public synchronized void start() {
        while (AudioSineWave.isStarted())
            try { wait(); }
            catch (Exception e) { }

        AudioSineWave.start();
        notifyAll();
    }

    public synchronized void stop() {
        AudioSineWave.stop();
        notifyAll();
    }
}
