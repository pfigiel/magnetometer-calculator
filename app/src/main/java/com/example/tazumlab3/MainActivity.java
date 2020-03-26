package com.example.tazumlab3;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.TextView;

import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    // Magnetometer axis used to input data. Empirically determined Y to be the best on test device.
    private int activeAxis = AxisDict.Y;

    // Value on the activeAxis needed to deem the device calibrated
    private float calibrationPoint = 20;

    // Margin for the calibration point (+/- given value)
    private float calibrationPointMargin = 5;

    // Value of magnetometer reading crossing which is treated as bit input
    private float magnetometerInputThreshold = 5;

    // A flip-flop remembering if the device was last pointed towards the calibration point or not
    private boolean lastDeviceRotationFlipFlop = true;

    // Currently entered arithmetic expression
    private String expression = "";

    // Integer value of the token currently being entered (coded)
    private int currentlyCodedTokenValue = 0;

    // Number of bits already entered to code the current token
    private int codingBitCounter = 0;

    // Time of last device rotation
    private long lastRotationTime = System.currentTimeMillis();

    // Time needed to interpret no rotation as 0 bit
    private long zeroBitTimeInterval = 2000;

    private boolean isDeviceCalibrated = false;
    private boolean isCalculatorActive = true;

    private Sensor magnetometer;
    private SensorManager mSensorManager;

    private float[] mGeomagnetic = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView calibrationTextView = findViewById(R.id.mainTextView);
        calibrationTextView.setText(
                "Please rotate the device until the above reading reaches " +
                calibrationPoint + " (+/- " + calibrationPointMargin + ")");

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (null == magnetometer) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register for sensor updates

        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister all sensors
        mSensorManager.unregisterListener(this);

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = new float[3];
            System.arraycopy(event.values, 0, mGeomagnetic, 0, 3);
        }

        if (mGeomagnetic != null) {
            if (!isDeviceCalibrated) {
                try {
                    handleDeviceCalibrationProcess(mGeomagnetic[activeAxis]);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            else {
                try {
                    processMagnetometerReading(mGeomagnetic[activeAxis]);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void handleDeviceCalibrationProcess(float reading) throws InterruptedException {
        TextView coordinatesTextView = findViewById(R.id.coordinatesTextView);
        coordinatesTextView.setText(String.valueOf(mGeomagnetic[activeAxis]));

        if (reading >= calibrationPoint - calibrationPointMargin &&
            reading <= calibrationPoint + calibrationPointMargin) {
            isDeviceCalibrated = true;
            initializeCalculator();
        }
    }

    private void processMagnetometerReading(float reading) throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        boolean deviceRotationFlipFlop = reading > magnetometerInputThreshold;
        TextView displayTextView = findViewById(R.id.mainTextView);

        // Device has been rotated
        if (deviceRotationFlipFlop != lastDeviceRotationFlipFlop) {
            // Calculator is not active - user wants to turn it on
            if (!isCalculatorActive) {
                isCalculatorActive = true;
                initializeCalculator();
            // Calculator is active - user wants to enter '1' bit
            } else {
                currentlyCodedTokenValue += 1;
                lastRotationTime = currentTime;
                TextView coordinatesTextView = findViewById(R.id.coordinatesTextView);
                coordinatesTextView.setText(decodeCurrentToken());
            }
            lastDeviceRotationFlipFlop = deviceRotationFlipFlop;
        // Calculator is active and device has not been rotated - user wants to enter '0' bit
        } else if (currentTime - zeroBitTimeInterval > lastRotationTime && isCalculatorActive) {
            expression += decodeCurrentToken();
            System.out.println(expression);
            currentlyCodedTokenValue = 0;
            lastRotationTime = currentTime;
            vibrate(100);
            if(expression.length() == 2){
                currentlyCodedTokenValue = 10;
            }

            // 3 tokens have been entered - solve the expression
            if (expression.length() == 3) {
                Integer result = solveExpression();
                displayTextView.setText(result != null ? String.valueOf(result) : "E");
                isCalculatorActive = false;
            }
        }
    }

    private void initializeCalculator() throws InterruptedException {
        vibrate(1000);
        Thread.sleep(1000);

        expression = "";
        TextView mainTextView = findViewById(R.id.mainTextView);
        TextView coordinatesTextView = findViewById(R.id.coordinatesTextView);
        mainTextView.setText("");
        mainTextView.setTextSize(400);
        coordinatesTextView.setText("");
        lastRotationTime = System.currentTimeMillis();
    }

    private void vibrate(int milliseconds) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(milliseconds);
        }
    }

    private String decodeCurrentToken() {
        if (currentlyCodedTokenValue > 0 && currentlyCodedTokenValue < 10) {
            return String.valueOf(currentlyCodedTokenValue);
        } else if (currentlyCodedTokenValue < 13) {
            switch(currentlyCodedTokenValue) {
                case 11:
                    return "+";
                case 12:
                    return "-";
                case 13:
                    return "*";
            }
        }
        return "E";
    }

    private Integer solveExpression() {
        if (Pattern.compile("[0-9]{2}[+*-]").matcher(expression).matches()) {
            int a = Character.getNumericValue(expression.charAt(0));
            int b = Character.getNumericValue(expression.charAt(1));
            return performOperation(a, b, expression.charAt(2));
        } else if (Pattern.compile("[0-9][+*-][0-9]").matcher(expression).matches()) {
            int a = Character.getNumericValue(expression.charAt(0));
            int b = Character.getNumericValue(expression.charAt(2));
            return performOperation(a, b, expression.charAt(1));
        }

        return null;
    }

    private Integer performOperation(int a, int b, char operation) {
        switch (operation) {
            case '+':
                return a + b;
            case '-':
                return a - b;
            case '*':
                return a * b;
        }

        return null;
    }
}
