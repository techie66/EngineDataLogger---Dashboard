package net.xtlive.EDL.Dashboard;

import android.annotation.SuppressLint;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import com.github.anastr.speedviewlib.ImageSpeedometer;



public class MainActivity extends AppCompatActivity {

    // GUI Components
    private TextView mBluetoothStatus;
    private TextView mOilText;
    private TextView mOdometer;
    private TextView mTrip;
    private TextView mLambda;
    private TextView mGearIndicator;
    //private Button mListPairedDevicesBtn;
    //private ListView mDevicesListView;
    private CheckBox mLED1;
    // TODO Add blinkers
    private ImageView mBlinkLeft;
    private ImageView mBlinkRight;
    private ImageView mOilWarn;

    private DrawerLayout drawerLayout;

    //GPS Speed
    LocationManager locManager;
    LocationListener li;
    private TextView mSpeedText;

    private final String TAG = MainActivity.class.getSimpleName();

    private MainActivityViewModel model;
    private SharedPreferences sharedPrefs;
    private BTConnector_Thread mBTConnector;

    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private final Handler mBluetoothHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            //Intent myIntent = new Intent(FullscreenActivity.this, MainActivity.class);
            //startActivity(myIntent);
            return false;
        }
    };
    private final Runnable mConnectBluetooth = new Runnable() {
        @Override
        public void run() {
            model.connectDevice(sharedPrefs.getString("bt_device",""));
        }
    };

    private final class BTConnector_Thread extends Thread {
        private boolean running = true;
        private boolean shownPreferences = false;
        @Override
        public void run() {
            while (running) {
                if (sharedPrefs.contains("bt_device")) {
                    if (!model.isConnected()) {
                        mBluetoothHandler.removeCallbacks(mConnectBluetooth);
                        mBluetoothHandler.post(mConnectBluetooth);
                        SystemClock.sleep(2000);
                    }
                } else {
                    if (!shownPreferences) {
                        shownPreferences = true;
                        Intent intent = new Intent(MainActivity.this, PrefsActivity.class);
                        startActivity(intent);

                    }
                }
            }
        }

        public void cancel() {
            running = false;
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothStatus = findViewById(R.id.bluetoothStatus);

        mLED1 = findViewById(R.id.checkboxLED1);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        //mDevicesListView = (ListView)findViewById(R.id.devicesListView);
        //mDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // Ask for location permission if not already allowed
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

        model = ViewModelProviders.of(this).get(MainActivityViewModel.class);

        if (!model.btAvailable()) {
            // Device does not support Bluetooth
            mBluetoothStatus.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(),"Bluetooth device not found!",Toast.LENGTH_SHORT).show();
        }
        else {

            mLED1.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    model.sendByte("1");
                }
            });
            // Spawn a new thread to keep Bluetooth active
            mBTConnector = new BTConnector_Thread();
            mBTConnector.start();


        }

        // Navigation Drawer Stuff
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);

        drawerLayout = findViewById(R.id.drawer_layout);

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        // set item as selected to persist highlight
                        //menuItem.setChecked(true);
                        // close drawer when item is tapped
                        drawerLayout.closeDrawers();

                        // Add code here to update the UI based on the item selected
                        // For example, swap UI fragments here
                        switch (menuItem.getItemId()) {
                            case R.id.nav_settings:
                                Intent intent = new Intent(MainActivity.this,PrefsActivity.class);
                                startActivity(intent);
                                break;
                            case R.id.nav_fullscreen:
                                toggle();
                                break;
                            case R.id.nav_trip_reset:
                                model.resetTrip();
                                break;
                            default:
                                break;
                        }

                        return true;
                    }
                });
        navigationView.bringToFront();

        mVisible = true;
        mControlsView = findViewById(R.id.toolbar);
        mContentView = findViewById(R.id.content_frame);

        // Speedometer Stuff
        ImageSpeedometer speedGauge = findViewById(R.id.speedView);
        Drawable mSpeedPicture = ContextCompat.getDrawable(this, R.drawable.speed);
        mSpeedPicture.setColorFilter(ContextCompat.getColor(this, R.color.Indicator), PorterDuff.Mode.SRC_ATOP);

        ImageSpeedometer rpmGauge = findViewById(R.id.rpmView);
        Drawable mRpmPicture = ContextCompat.getDrawable(this, R.drawable.rpm);
        mRpmPicture.setColorFilter(ContextCompat.getColor(this, R.color.Indicator), PorterDuff.Mode.SRC_ATOP);

        ImageSpeedometer voltageGauge = findViewById(R.id.voltageView);
        Drawable mVoltageImage = ContextCompat.getDrawable(this, R.drawable.voltage_gauge);
        mVoltageImage.setColorFilter(ContextCompat.getColor(this, R.color.Indicator), PorterDuff.Mode.SRC_ATOP);

        ImageSpeedometer oilTemp = findViewById(R.id.oilTemp);
        Drawable mOilPicture = ContextCompat.getDrawable(this, R.drawable.oil_temp);
        mOilPicture.setColorFilter(ContextCompat.getColor(this, R.color.Indicator), PorterDuff.Mode.SRC_ATOP);

        // move to 50
        speedGauge.speedTo(0);
        rpmGauge.speedTo(0);
        voltageGauge.speedTo(6); // Backwards 6=11


        // Updating from ViewModel
        /*model.getPairedList().observe(this, pairedList -> {
            // update UI
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, android.R.id.text1, pairedList);
            // Assign adapter to ListView
            mDevicesListView.setAdapter(adapter);
        });*/
        model.getRPM().observe(this,myRPM -> {
            //Update UI
            rpmGauge.speedTo(myRPM,100);
        });
        model.getSpeed().observe(this,mySpeed -> {
            //Update UI
            speedGauge.speedTo(mySpeed,100);
        });
        model.getVoltage().observe(this,myVoltage -> {
            //Update UI
            voltageGauge.speedTo(myVoltage,100);
        });

        mOilText = findViewById(R.id.oilText);
        model.getOilTemp().observe(this,myOilTemp -> {
            //Update UI
            oilTemp.speedTo(myOilTemp,100);
            mOilText.setText(myOilTemp.toString());
        });

        mOdometer = findViewById(R.id.odometer);
        model.getOdometer().observe(this,myOdometer -> {
            // Update GUI
            mOdometer.setText(myOdometer.toString());
        });
        model.getStatus().observe(this,myStatus -> {
            //Update UI
            mBluetoothStatus.setText(myStatus.toString());
        });

        mGearIndicator = findViewById(R.id.gearIndicator);
        model.getGear().observe(this,myGearIndicator -> {
            //Update UI
            mGearIndicator.setText(myGearIndicator.toString());
        });

        //GPS Speed
        mSpeedText = findViewById(R.id.gpsSpeed);

        locManager=(LocationManager)this.getSystemService(LOCATION_SERVICE);
        li=new speed();
        locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, li);

        // Blinkers
        mBlinkLeft = findViewById(R.id.leftBlinker);
        mBlinkRight = findViewById(R.id.rightBlinker);
        Drawable mLeftPicture = ContextCompat.getDrawable(this, R.drawable.arrow_boxed_solid_left);
        mLeftPicture.setColorFilter(ContextCompat.getColor(this, R.color.Indicator), PorterDuff.Mode.SRC_ATOP);
        Drawable mRightPicture = ContextCompat.getDrawable(this, R.drawable.arrow_boxed_solid_right);
        mRightPicture.setColorFilter(ContextCompat.getColor(this, R.color.Indicator), PorterDuff.Mode.SRC_ATOP);

        Animation animation = new AlphaAnimation(1, 0); //to change visibility from visible to invisible
        animation.setDuration(300); //1 second duration for each animation cycle
        animation.setInterpolator(new LinearInterpolator());
        animation.setRepeatCount(Animation.INFINITE); //repeating indefinitely
        animation.setRepeatMode(Animation.REVERSE); //animation will start from end point once ended.
        animation.start();
        mBlinkRight.setVisibility(View.INVISIBLE);
        mBlinkLeft.setVisibility(View.INVISIBLE);

        model.getmBlinkLeft().observe(this,myBlinkLeft -> {
            if (myBlinkLeft) {
                mBlinkLeft.setAnimation(animation);
            }
            else {
                mBlinkLeft.clearAnimation();
                mBlinkLeft.setVisibility(View.INVISIBLE);
            }
        });
        model.getmBlinkRight().observe(this,myBlinkRight -> {
            if (myBlinkRight) {
                mBlinkRight.setAnimation(animation);
            }
            else {
                mBlinkRight.clearAnimation();
                mBlinkRight.setVisibility(View.INVISIBLE);
            }
        });

        mOilWarn = findViewById(R.id.oilWarning);
        model.getOilPres().observe(this,myOilPres -> {
            if (myOilPres < 50) {
                mOilWarn.setVisibility(View.VISIBLE);
            }
            else {
                mOilWarn.setVisibility(View.INVISIBLE);
            }
        });

        // Trip
        mTrip = findViewById(R.id.trip);
        model.getmTrip().observe(this,myTrip -> {
            // Update GUI
            mTrip.setText(myTrip.toString());
        });

        // Lambda
        mLambda = findViewById(R.id.lambda);
        model.getLambda().observe(this,myLambda -> {
            // Update GUI
            mLambda.setText(myLambda.toString());
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBTConnector.cancel();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data){
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mBluetoothStatus.setText("Enabled");
            }
            else
                mBluetoothStatus.setText("Disabled");
        }
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            model.connectDevice(((TextView) v).getText().toString());
        }
    };

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    // GPS Speed copy and paste
    class speed implements LocationListener {
        @Override
        public void onLocationChanged(Location loc) {
            double thespeed=loc.getSpeed();
            thespeed *= 2.23694;
            mSpeedText.setText(String.valueOf((int)thespeed));
        }
        @Override
        public void onProviderDisabled(String arg0) {}
        @Override
        public void onProviderEnabled(String arg0) {}
        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}

    }
}
