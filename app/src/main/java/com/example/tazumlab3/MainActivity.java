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
    // Currently entered arithmetic expression
    private String expression = "";

    // Integer value of the token currently being entered (coded)
    private int currentlyChoosenValue = 0;

    private int lastChoosenValue = 0;

    private boolean calculatorActive = true;

    // Time of last device rotation
    private long lastRotationTime = System.currentTimeMillis();

    // Time needed to interpret no rotation as 0 bit
    private long timeInterval = 2000;

    private Sensor magnetometer;
    private SensorManager mSensorManager;

    private float[] mGeomagnetic = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try{
            initializeCalculator();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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
            float angle = (float)Math.toDegrees(Math.abs(mGeomagnetic[1])/mGeomagnetic[1]*(Math.PI/2+Math.abs(mGeomagnetic[1])/mGeomagnetic[1]*Math.atan(mGeomagnetic[0]/mGeomagnetic[1])));
                try {
                    processMagnetometerReading(angle);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void processMagnetometerReading(float reading) throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        TextView displayTextView = findViewById(R.id.mainTextView);

        lastChoosenValue = currentlyChoosenValue;
        currentlyChoosenValue = (int)(reading+180)/27;
        if(lastChoosenValue != currentlyChoosenValue){
            lastRotationTime = currentTime;
            TextView numberTextView = findViewById(R.id.numberTextView);
            numberTextView.setText(decodeCurrentToken());
        }
        if (currentTime - timeInterval > lastRotationTime && (calculatorActive || currentlyChoosenValue == 0)){
            lastRotationTime = currentTime;
            vibrate(100);
            if(currentlyChoosenValue == 0){
                initializeCalculator();
                return;
            }
            expression += decodeCurrentToken();
            System.out.println(expression);

            // 3 tokens have been entered - solve the expression
            if (expression.length() == 3) {
                Integer result = solveExpression();
                displayTextView.setText(result != null ? String.valueOf(result) : "E");
                calculatorActive = false;
            }
        }
    }

    private void initializeCalculator() throws InterruptedException {
        vibrate(1000);
        Thread.sleep(1000);

        expression = "";
        TextView mainTextView = findViewById(R.id.mainTextView);
        mainTextView.setText("");
        mainTextView.setTextSize(400);
        lastRotationTime = System.currentTimeMillis();
        calculatorActive = true;
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
        if (currentlyChoosenValue > 0 && currentlyChoosenValue < 10) {
            return String.valueOf(currentlyChoosenValue);
        } else if (currentlyChoosenValue < 13) {
            switch(currentlyChoosenValue) {
                case 10:
                    return "+";
                case 11:
                    return "-";
                case 12:
                    return "*";
            }
        }
        return "Reset";
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
