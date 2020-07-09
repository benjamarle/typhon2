package com.github.axet.bookreader.widgets;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.preference.SwitchPreferenceCompat;
import android.util.AttributeSet;
import android.util.Log;

import com.github.axet.bookreader.R;

import java.util.HashMap;
import java.util.Map;

public class RotatePreferenceCompat extends SwitchPreferenceCompat {
    public static final String TAG = RotatePreferenceCompat.class.getSimpleName();

    public static boolean PHONES_ONLY = true; // hide option for tablets

    public static Map<Activity, ContentObserver> map = new HashMap<>();
    public static Handler handler = new Handler(Looper.getMainLooper());

    public static boolean isEnabled(Context context) {
        return !PHONES_ONLY || !context.getResources().getBoolean(R.bool.is_tablet);
    }

    public static void setRequestedOrientationDefault(Activity a) {
        a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    public static void setRequestedOrientationLock(Activity a) { // SCREEN_ORIENTATION_NOSENSOR, nor SCREEN_ORIENTATION_LOCKED working
        if (a.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        else
            a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    public static void onCreate(final Activity a, final String key) {
        ContentResolver resolver = a.getContentResolver();
        ContentObserver o = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                onResume(a, key);
            }
        };
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), true, o);
        map.put(a, o);
        onResume(a, key);
    }

    public static void onDestroy(final Activity a) {
        ContentResolver resolver = a.getContentResolver();
        resolver.unregisterContentObserver(map.remove(a));
    }

    public static void onResume(Activity a, String key) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(a);
        boolean user = shared.getBoolean(key, false);
        boolean system = false;
        try {
            system = Settings.System.getInt(a.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) == 1;
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Unable to read settings", e);
        }
        if (isEnabled(a)) { // tables has no user option to disable rotate
            if (system && !user)
                setRequestedOrientationLock(a);
            else if (!system && user)
                a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            else // system && user || !system && !user
                setRequestedOrientationDefault(a);
        } else {
            setRequestedOrientationDefault(a);
        }
    }

    @TargetApi(21)
    public RotatePreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    @TargetApi(21)
    public RotatePreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public RotatePreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public RotatePreferenceCompat(Context context) {
        super(context);
        create();
    }

    public void create() {
    }

    public void onResume() {
        if (!isEnabled(getContext()))
            setVisible(false);
    }
}
