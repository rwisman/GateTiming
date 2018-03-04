package edu.ius.rwisman.gatetiming;

/**
 * Created by rwisman on 12/25/17.
 */

public class TimingData {
    double low;
    double high;
    long epochMilliseconds;

    public TimingData(double low, double high, long epochMilliseconds) {
        this.low = low;
        this.high = high;
        this.epochMilliseconds = epochMilliseconds;
    }
}