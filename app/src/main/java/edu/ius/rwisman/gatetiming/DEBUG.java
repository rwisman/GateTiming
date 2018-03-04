package edu.ius.rwisman.gatetiming;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * Controls whether runtime debugging messages and WAV file generated.
 */

public final class DEBUG {
    public static boolean ON = false;
    public static boolean WAV = false;
    public static boolean REDIRECT = false;
    public static boolean TIMING = true;

    public static void redirectOut(String destination) {
        if (DEBUG.REDIRECT && DEBUG.ON)
            try {
                System.setOut(new PrintStream(new FileOutputStream(new File(Environment.getExternalStorageDirectory(), destination + "." + "txt"))));
            } catch (Exception e) {
                System.out.println("System.setOut failed " + e);
            }
    }
}
