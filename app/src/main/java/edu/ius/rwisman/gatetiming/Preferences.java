package edu.ius.rwisman.gatetiming;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by rwisman on 1/6/18.
 */

public class Preferences {
    public static void firstTimeAskingPermission(AppCompatActivity context, String permission, boolean isFirstTime){
        SharedPreferences preferences = context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE);
        preferences.edit().putBoolean(permission, isFirstTime).apply();
    }

    public static boolean isFirstTimeAskingPermission(AppCompatActivity context, String permission){
        return context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE).getBoolean(permission, true);
    }

    public static void setPreference(AppCompatActivity context, String preference, boolean setting){
        SharedPreferences preferences = context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE);
        preferences.edit().putBoolean(preference, setting).apply();
    }

    public static boolean getPreference(AppCompatActivity context, String preference){
        return context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE).getBoolean(preference, false);
    }
}
