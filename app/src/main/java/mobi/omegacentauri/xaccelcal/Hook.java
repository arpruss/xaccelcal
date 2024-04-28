package mobi.omegacentauri.xaccelcal;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.annotation.SuppressLint;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.inputmethodservice.InputMethodService;

import java.util.HashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Hook implements IXposedHookLoadPackage {
    static InputMethodService ims = null;
//    final Hook.Data persistentData = new Hook.Data();
    @SuppressLint("NewApi")
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
//        XposedBridge.log("handleLoadPackage "+lpparam.packageName);
        XSharedPreferences prefs = new XSharedPreferences(Options.class.getPackage().getName(), Options.PREFS);

        if (!lpparam.packageName.equals("mobi.omegacentauri.xaccelcal")) {
            double yz = Options.parseAngle(prefs.getString(Options.PREF_YZ, "0"));
            double xz = Options.parseAngle(prefs.getString(Options.PREF_XZ, "0"));

            XposedBridge.log("xaccelcal: "+yz +" "+xz);

            hookCalibration(lpparam, yz, xz);
        }
    }

    private void hookCalibration(LoadPackageParam lpparam, final double yz, final double xz) {
        final double cosYZ = Math.cos(yz);
        final double sinYZ = Math.sin(yz);
        final double cosXZ = Math.cos(xz);
        final double sinXZ = Math.sin(xz);

        findAndHookMethod("android.hardware.SystemSensorManager$SensorEventQueue",
                lpparam.classLoader, "dispatchSensorEvent", int.class, float[].class, int.class, long.class,
                new XC_MethodHook() {
                    @SuppressLint("InlinedApi")
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        SensorEventListener listener = (SensorEventListener) XposedHelpers.getObjectField(param.thisObject, "mListener");
                        int handle = (int) param.args[0];
                        Object mgr = XposedHelpers.getObjectField(param.thisObject, "mManager");
                        HashMap<Integer, Sensor> sensors = (HashMap<Integer, Sensor>) XposedHelpers.getObjectField(mgr, "mHandleToSensor");
                        Sensor s = sensors.get(handle);
                        if (s != null && (s.getType()==Sensor.TYPE_GRAVITY || s.getType()==Sensor.TYPE_ACCELEROMETER)) {
                            float[] data = (float[]) param.args[1];
                            if (data != null && data.length >= 3) {
                                if (yz != 0) {
                                    Options.correct(data, 1, 2, cosYZ, sinYZ);
                                }
                                if (xz != 0) {
                                    Options.correct(data, 0, 2, cosYZ, sinYZ);
                                    float x = (float) (data[0] * cosXZ - data[2] * sinXZ);
                                    data[2] = (float) (data[0] * sinXZ + data[2] * cosXZ);
                                    data[0] = x;
                                }
                            }
                        }
                    }
                });
    }

//    class Data {
//        long lastLongClick = 0;
//    }
}