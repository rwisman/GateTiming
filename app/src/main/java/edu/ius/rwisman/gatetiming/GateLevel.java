package edu.ius.rwisman.gatetiming;

/**
 * Created by rwisman on 1/15/18.
 */

public class GateLevel {
    int gate;
    double level;

    public GateLevel(int gate, double level) {
        this.gate = gate;
        this.level = level;
    }

    public int getGate() {
        return gate;
    }

    public double getLevel() {
        return level;
    }
}
