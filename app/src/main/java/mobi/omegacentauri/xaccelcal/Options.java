package mobi.omegacentauri.xaccelcal;

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
import android.view.Window;
import android.view.WindowManager;

public class Options extends PreferenceActivity {
    public static final String PREF_YZ = "yz";
    public static final String PREF_XZ = "xz";
    static final String PREFS = "preferences";

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

        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(PREFS);
        prefMgr.setSharedPreferencesMode(MODE_WORLD_READABLE);

        addPreferencesFromResource(R.xml.options);

        Window w = getWindow();
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            w.setNavigationBarColor(Color.BLACK);
        }
    }

    protected void onResume() {
        super.onResume();

        final Preference cur_xz = (Preference) findPreference("cur_xz");
        final Preference cur_yz = (Preference) findPreference("cur_yz");
        cur_xz.setSummary("");
        cur_yz.setSummary("");
        final EditTextPreference prefXZ = (EditTextPreference) findPreference(PREF_XZ);
        final EditTextPreference prefYZ = (EditTextPreference) findPreference(PREF_YZ);
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                float[] g = sensorEvent.values;
                double a = parseAngle(prefYZ.getText());
                correct(g, 1, 2, Math.cos(a), Math.sin(a));
                a = parseAngle(prefXZ.getText());
                correct(g, 0, 2, Math.cos(a), Math.sin(a));
                String xz = "";
                if (g[0] != 0 || g[2] != 0) {
                    double angle = 90 - Math.atan2(g[2], g[0]) * 180 / Math.PI;
                    xz = String.format("%.2f\u00B0", angle);
                }
                String yz = "";
                if (g[1] != 0 || g[2] != 0) {
                    double angle = 90 - Math.atan2(g[2], g[1]) * 180 / Math.PI;
                    yz = String.format("%.2f\u00B0", angle);
                }
                cur_xz.setSummary(xz);
                cur_yz.setSummary(yz);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        }, sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), SensorManager.SENSOR_DELAY_NORMAL);
    }
}