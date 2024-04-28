package mobi.omegacentauri.xaccelcal;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import de.robv.android.xposed.XposedBridge;

public class Options extends PreferenceActivity {
    public static final String PREF_YZ = "yz";
    public static final String PREF_XZ = "xz";
    static final String PREFS = "preferences";
    boolean killProcess = false;

    public static void correct(float[] data, int i, int j, double cosine, double sine) {
        float y = (float) (data[i] * cosine + data[j] * sine);
        data[j] = (float) (-data[i] * sine + data[j] * cosine);
        data[i] = y;
    }

    public static double parseAngle(String string) {
        try {
            return Double.parseDouble(string) * (Math.PI / 180);
        }
        catch(NumberFormatException e) {
            return 0;
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Window w = getWindow();
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            w.setNavigationBarColor(Color.BLACK);
        }
    }

    private void mustExit() {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Activate in Xposed Manager");
        alertDialog.setMessage("Before you can use the " + getApplicationInfo().nonLocalizedLabel + " module, you need to activate it in your Xposed Manager.");
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Exit",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Options.this.finish();
                    } });
        alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                Options.this.finish();
            } });
        alertDialog.show();
    }

    public void onDestroy() {
        super.onDestroy();
        Log.v("xaccelcal", "killing");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    protected void onResume() {
        super.onResume();

        if (killProcess)
            return;

        setPreferenceScreen(null);

        try {
            /*
            Make sure you have
            <meta-data android:name="xposedminversion"
            android:value="93" />
            <meta-data android:name="xposedsharedprefs"
            android:value="true"/>
            in AndroidManifest.xml
             */
            PreferenceManager prefMgr = getPreferenceManager();
            prefMgr.setSharedPreferencesName(PREFS);
            prefMgr.setSharedPreferencesMode(MODE_WORLD_READABLE);
            addPreferencesFromResource(R.xml.options);
            killProcess = false;
        }
        catch(SecurityException e) {
            Log.e("xaccelcal", "cannot make prefs world readable");
            killProcess = true;
            mustExit();
            return;
        }


        Preference autocalibrate = findPreference("autocalibrate");
        autocalibrate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent(Options.this, Calibrator.class);
                Options.this.startActivity(i);
                return true;
            }
        });

        final Preference cur_xz = (Preference) findPreference("cur_xz");
        final Preference cur_yz = (Preference) findPreference("cur_yz");
        cur_xz.setSummary("");
        cur_yz.setSummary("");
        final Preference cor_xz = (Preference) findPreference("cor_xz");
        final Preference cor_yz = (Preference) findPreference("cor_yz");
        cor_xz.setSummary("");
        cor_yz.setSummary("");
        final EditTextPreference prefXZ = (EditTextPreference) findPreference(PREF_XZ);
        final EditTextPreference prefYZ = (EditTextPreference) findPreference(PREF_YZ);
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                float[] g = sensorEvent.values;
                showAngle(cur_xz, g, 0, 2);
                showAngle(cur_yz, g, 1, 2);
                double a = parseAngle(prefYZ.getText());
                correct(g, 1, 2, Math.cos(a), Math.sin(a));
                a = parseAngle(prefXZ.getText());
                correct(g, 0, 2, Math.cos(a), Math.sin(a));
                showAngle(cor_xz, g, 0, 2);
                showAngle(cor_yz, g, 1, 2);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        }, sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), SensorManager.SENSOR_DELAY_NORMAL);
    }

    static void showAngle(Preference anglePref, float[] g, int i, int j) {
        try {
            double angle = 90.-Math.atan2(g[j], g[i]) * (180/Math.PI);
            anglePref.setSummary(String.format("%.2f\u00B0", angle));
        }
        catch (Exception e) {
            anglePref.setSummary("");
        }
    }
}