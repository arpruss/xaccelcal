package mobi.omegacentauri.xaccelcal;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import de.robv.android.xposed.XSharedPreferences;

public class Calibrator extends Activity implements SensorEventListener {
    private TextView instructions;

    static final int STATE_START = 0;
    static final int STATE_FIRST_MEASURING = 1;
    static final int STATE_FIRST_MEASURED = 2;
    static final int STATE_SECOND_MEASURING = 3;
    static final int STATE_MEASURED = 4;
    static final int STATE_SAVE = 5;

    int state = STATE_START;
    private Button backButton;
    private Button nextButton;
    CalibrationData calibration;

    static final long MEASURE_TIME = 8000;
    static final long MEASURE_ANTISHAKE_TIME = 2000;
    static long measureStart = 0;

    double[] firstG = new double[3];
    double[] g = new double[3];

    double[] capturedG = new double[3];
    int capturedCount;
    private TextView countdown;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.calibrator);
        instructions = (TextView) findViewById(R.id.calibrator_instructions);
        backButton = (Button) findViewById(R.id.calibrator_back);
        nextButton = (Button) findViewById(R.id.calibrator_next);
        countdown = (TextView) findViewById(R.id.calibrator_countdown);

        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY), SensorManager.SENSOR_DELAY_NORMAL);

        state = STATE_START;
        setState();
    }

    public void setState() {
        countdown.setText("");
        if (state == STATE_START) {
            instructions.setText("Put your device on a flat, stable and somewhat level surface with screen facing up, in a reproducible position. Then click the 'Next' button to start calibrating.");
            nextButton.setText("Next");
            nextButton.setEnabled(true);
        }
        else if (state == STATE_FIRST_MEASURING || state == STATE_SECOND_MEASURING) {
            instructions.setText((state == STATE_FIRST_MEASURING ? "Measuring in first orientation." : "Measuring in second orientation.") +
                    " Do not shake device or the surface it's on!");
            nextButton.setText("Next");
            nextButton.setEnabled(false);
            measure();
        }
        else if (state == STATE_FIRST_MEASURED) {
            instructions.setText("Keeping the screen facing upward, rotate your device as close to 180 degrees as you can, putting it in the same place on your surface. Then click the 'Next' button to complete calibration.");
            nextButton.setText("Next");
            nextButton.setEnabled(true);
        }
        else if (state == STATE_MEASURED) {
            instructions.setText("Calibration obtained. Press 'Save' to save it.");
            nextButton.setText("Save");
            nextButton.setEnabled(true);
        }
        else if (state == STATE_SAVE) {
            computeAndSave();
            state = STATE_START;
            finish();
        }
    }

    private void computeAndSave() {
        double[] _g = g.clone();
        double yz = 0;
        try {
            yz = Math.atan2(_g[2], _g[1]) - (Math.PI / 2);
        }
        catch(Exception e) {
            Toast.makeText(this, "Error calculating yz correction", Toast.LENGTH_LONG).show();
        }
        CalibrationData.correct(_g, 1, 2, Math.cos(yz), Math.sin(yz));
        double xz = 0;
        try {
            xz = Math.atan2(_g[2], _g[0]) - (Math.PI/2);
        }
        catch(Exception e) {
            Toast.makeText(this, "Error calculating xz correction", Toast.LENGTH_LONG).show();
        }
        saveCalibration(yz, xz);
    }

    protected void saveCalibration(double yz, double xz) {
        yz *= 180/Math.PI;
        xz *= 180/Math.PI;
        Log.v("xaccelcal", "saving "+yz+" "+xz);
        SharedPreferences prefs = getSharedPreferences(Options.PREFS, MODE_WORLD_READABLE);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString(Options.PREF_YZ, ""+yz);
        ed.putString(Options.PREF_XZ, ""+xz);
        ed.apply();
        Toast.makeText(this, "Correction "+yz+" "+xz, Toast.LENGTH_SHORT).show();
    }

    private void measure() {
        capturedCount = 0;
        capturedG[0] = 0;
        capturedG[1] = 0;
        capturedG[2] = 0;
        measureStart = SystemClock.uptimeMillis();
    }

    public void backClicked(View view) {
        if (state == STATE_START) {
            finish();
            return;
        }
        state--;
        if (state == STATE_FIRST_MEASURING || state == STATE_SECOND_MEASURING)
            state--;
        setState();
    }
    public void nextClicked(View view) {
        state++;
        setState();
    }
    public void cancelClicked(View view) {
        state = STATE_START;
        finish();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if ((state == STATE_FIRST_MEASURING || state == STATE_SECOND_MEASURING) && sensorEvent.values.length >= 3) {
            long t = SystemClock.uptimeMillis() - measureStart;
            if (MEASURE_ANTISHAKE_TIME <= t) {
                capturedG[0] += sensorEvent.values[0];
                capturedG[1] += sensorEvent.values[1];
                capturedG[2] += sensorEvent.values[2];
                capturedCount++;
            }
            long count = ((MEASURE_TIME - t + 999) / 1000);
            if (count < 0)
                count = 0;
            String countString = Long.toString(count);
            if (!countString.equals(countdown.getText())) {
                countdown.setText(countString);
            }
            if (t >= MEASURE_TIME && capturedCount > 10) {
                measured();
            }
        }
    }

    protected void onResume() {
        super.onResume();

        setState();
    }

    private void measured() {
        if (state == STATE_FIRST_MEASURING) {
            firstG[0] = capturedG[0]/capturedCount;
            firstG[1] = capturedG[1]/capturedCount;
            firstG[2] = capturedG[2]/capturedCount;
        }
        else {
            for (int i=0;i<3;i++)
                g[i] = (capturedG[i]/capturedCount + firstG[i])/2;
        }
        state++;
        setState();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    static class CalibrationData {
        private double yz;
        private double xz;
        private double sinYZ;
        private double cosYZ;
        private double sinXZ;
        private double cosXZ;

        public CalibrationData() {
            this(0.,0.);
        }

        public CalibrationData(double _yz, double _xz) {
            yz = _yz;
            xz = _xz;
            precalc();
        }

        public static void correct(float[] data, int i, int j, double cosine, double sine) {
            float y = (float) (data[i] * cosine + data[j] * sine);
            data[j] = (float) (-data[i] * sine + data[j] * cosine);
            data[i] = y;
        }
        public static void correct(double[] data, int i, int j, double cosine, double sine) {
            double y = (data[i] * cosine + data[j] * sine);
            data[j] = (-data[i] * sine + data[j] * cosine);
            data[i] = y;
        }
        public void correct(float[] data) {
            correct(data, 1, 2, cosYZ, sinYZ);
            correct(data, 0, 2, cosXZ, sinXZ);
        }

        public CalibrationData(String s) {
            yz = 0;
            xz = 0;
            int i = s.indexOf(",");
            if (0 <= i) {
                try {
                    yz = Double.parseDouble(s.substring(0, i));
                    xz = Double.parseDouble(s.substring(i + 1));
                }
                catch(Exception e) {
                }
            }
            precalc();
        }

        private void precalc() {
            double cosYZ = Math.cos(yz);
            double cosXZ = Math.cos(xz);
            sinYZ = Math.sin(yz);
            sinXZ = Math.sin(yz);
        }

        public String toString() {
            return ""+yz+","+xz;
        }
    }
}

