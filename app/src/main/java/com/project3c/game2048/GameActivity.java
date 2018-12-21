package com.project3c.game2048;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.project3c.sensortag.Measurement;
import com.project3c.R;
import com.project3c.sensortag.Util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Locale;
import java.util.UUID;

public class GameActivity extends AppCompatActivity {

    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String SCORE = "score";
    private static final String HIGH_SCORE = "high score temp";
    private static final String UNDO_SCORE = "undo score";
    private static final String CAN_UNDO = "can undo";
    private static final String UNDO_GRID = "undo";
    private static final String GAME_STATE = "game state";
    private static final String UNDO_GAME_STATE = "undo game state";
    private MainView view;
    private static int my_time=10;
    private static double small_thld=80;
    private static double big_thld=100;
    private static int st_time=20;
    private int ac_number=0;
    private static int record_number=1;
    private double my_Xacc;
    private double my_Yacc;
    private int record_time=0;
  //  private double my_Xdis[];
   // private double my_Ydis[];
    private double sum_X;
    private double sum_Y;
    private int current_time=0;
    private int last_time=0;
    private int order=0;

    /* SensorTag part */
    private static final String ARG_ADDRESS = "address";
    private String mAddress;
    private boolean mIsRecording = false;
    private LinkedList<Measurement> mRecording;
//    private OnStatusListener mListener;
    private Calendar previousRead, recordingStart;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mGatt;
    private BluetoothGattService mMovService;
    private BluetoothGattCharacteristic mRead, mEnable, mPeriod;

//    private TextView mXAxis, mYAxis, mZAxis, mMax;
//    private Button mStart, mStop, mExport;
//    private LineChart mChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mAddress = intent.getStringExtra(ARG_ADDRESS);

        view = new MainView(this);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        view.hasSaveState = settings.getBoolean("save_state", false);

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("hasState")) {
                load();
            }
        }
        setContentView(view);

        /* initialize bluetooth manager & adapter */
//        TODO debug if necessary
//        BluetoothManager manager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothManager manager = (BluetoothManager) GameActivity.this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();
        new Thread(new Mythread()).start();
    }

    /*
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            //Do nothing
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            view.game.move(2);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            view.game.move(0);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            view.game.move(3);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            view.game.move(1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    */

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean("hasState", true);
        save();
    }

    /**
     * Called when the Fragment is no longer resumed.
     */
    protected void onPause() {
        deviceDisconnected();
        super.onPause();
        save();
    }

    public Handler handler =new Handler(){
        public void handleMessage(Message msg){
            if(order==10+record_number){view.game.move(1);last_time=0-st_time;order++;}
            else if(order==20+record_number){view.game.move(2);last_time=0-st_time;order++;}
            else if(order==30+record_number){view.game.move(3);last_time=0-st_time;order++;}
            else if(order==40+record_number){view.game.move(0);last_time=0-st_time;order++;}
            super.handleMessage(msg);
        }
    };

    public class Mythread implements Runnable{
        @Override
        public void run(){
            while(true){
                try{
                    Thread.sleep(100);
                    Message message =new Message();
                    message.what=1;
                    handler.sendMessage(message);
                }catch(InterruptedException e){
                    e.printStackTrace();
                }

            }
        }
    }

    private void save() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        Tile[][] field = view.game.grid.field;
        Tile[][] undoField = view.game.grid.undoField;
        editor.putInt(WIDTH, field.length);
        editor.putInt(HEIGHT, field.length);
        for (int xx = 0; xx < field.length; xx++) {
            for (int yy = 0; yy < field[0].length; yy++) {
                if (field[xx][yy] != null) {
                    editor.putInt(xx + " " + yy, field[xx][yy].getValue());
                } else {
                    editor.putInt(xx + " " + yy, 0);
                }

                if (undoField[xx][yy] != null) {
                    editor.putInt(UNDO_GRID + xx + " " + yy, undoField[xx][yy].getValue());
                } else {
                    editor.putInt(UNDO_GRID + xx + " " + yy, 0);
                }
            }
        }
        editor.putLong(SCORE, view.game.score);
        editor.putLong(HIGH_SCORE, view.game.highScore);
        editor.putLong(UNDO_SCORE, view.game.lastScore);
        editor.putBoolean(CAN_UNDO, view.game.canUndo);
        editor.putInt(GAME_STATE, view.game.gameState);
        editor.putInt(UNDO_GAME_STATE, view.game.lastGameState);
        editor.commit();
    }

    /**
     * Called when the fragment is visible to the user and actively running.
     */
    protected void onResume() {
        super.onResume();
        connectDevice(mAddress);
        load();
    }

    private void load() {
        //Stopping all animations
        view.game.aGrid.cancelAnimations();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        for (int xx = 0; xx < view.game.grid.field.length; xx++) {
            for (int yy = 0; yy < view.game.grid.field[0].length; yy++) {
                int value = settings.getInt(xx + " " + yy, -1);
                if (value > 0) {
                    view.game.grid.field[xx][yy] = new Tile(xx, yy, value);
                } else if (value == 0) {
                    view.game.grid.field[xx][yy] = null;
                }

                int undoValue = settings.getInt(UNDO_GRID + xx + " " + yy, -1);
                if (undoValue > 0) {
                    view.game.grid.undoField[xx][yy] = new Tile(xx, yy, undoValue);
                } else if (value == 0) {
                    view.game.grid.undoField[xx][yy] = null;
                }
            }
        }

        view.game.score = settings.getLong(SCORE, view.game.score);
        view.game.highScore = settings.getLong(HIGH_SCORE, view.game.highScore);
        view.game.lastScore = settings.getLong(UNDO_SCORE, view.game.lastScore);
        view.game.canUndo = settings.getBoolean(CAN_UNDO, view.game.canUndo);
        view.game.gameState = settings.getInt(GAME_STATE, view.game.gameState);
        view.game.lastGameState = settings.getInt(UNDO_GAME_STATE, view.game.lastGameState);
    }

    /**
     * Creates a GATT connection to the given device.
     *
     * @param address String containing the address of the device
     */
    private void connectDevice(String address) {
        if (!mBluetoothAdapter.isEnabled()) {
//            Toast.makeText(getActivity(), R.string.state_off, Toast.LENGTH_SHORT).show();
//            getActivity().finish();
            Toast.makeText(GameActivity.this, R.string.state_off, Toast.LENGTH_SHORT).show();
            GameActivity.this.finish();
        }
//        mListener.onShowProgress();
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
//        mGatt = device.connectGatt(getActivity(), false, mCallback);
        mGatt = device.connectGatt(GameActivity.this, false, mCallback);
    }

    private BluetoothGattCallback mCallback = new BluetoothGattCallback() {
        double result[];
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS", Locale.getDefault());

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState) {
                case BluetoothGatt.STATE_CONNECTED:
                    // as soon as we're connected, discover services
                    mGatt.discoverServices();
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            // as soon as services are discovered, acquire characteristic and try enabling
            mMovService = mGatt.getService(UUID.fromString("F000AA80-0451-4000-B000-000000000000"));
            mEnable = mMovService.getCharacteristic(UUID.fromString("F000AA82-0451-4000-B000-000000000000"));
            if (mEnable == null) {
//                Toast.makeText(getActivity(), R.string.service_not_found, Toast.LENGTH_LONG).show();
//                getActivity().finish();
                Toast.makeText(GameActivity.this, R.string.service_not_found, Toast.LENGTH_LONG).show();
                GameActivity.this.finish();
            }
            /*
             * Bits starting with the least significant bit (the rightmost one)
             * 0       Gyroscope z axis enable
             * 1       Gyroscope y axis enable
             * 2       Gyroscope x axis enable
             * 3       Accelerometer z axis enable
             * 4       Accelerometer y axis enable
             * 5       Accelerometer x axis enable
             * 6       Magnetometer enable (all axes)
             * 7       Wake-On-Motion Enable
             * 8:9	    Accelerometer range (0=2G, 1=4G, 2=8G, 3=16G)
             * 10:15   Not used
             */
            mEnable.setValue(0b1001111111, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            mGatt.writeCharacteristic(mEnable);
        }

        /**
         * Callback indicating the result of a characteristic write operation.
         *
         * <p>If this callback is invoked while a reliable write transaction is
         * in progress, the value of the characteristic represents the value
         * reported by the remote device. An application should compare this
         * value to the desired value to be written. If the values don't match,
         * the application must abort the reliable write transaction.
         *
         * @param gatt GATT client invoked {@link BluetoothGatt#writeCharacteristic}
         * @param characteristic Characteristic that was written to the associated
         *                       remote device.
         * @param status The result of the write operation
         *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (characteristic == mEnable) {
                // if enable was successful, set the sensor period to the lowest value
                mPeriod = mMovService.getCharacteristic(UUID.fromString("F000AA83-0451-4000-B000-000000000000"));
                if (mPeriod == null) {
//                    Toast.makeText(getActivity(), R.string.service_not_found, Toast.LENGTH_LONG).show();
//                    getActivity().finish();
                    Toast.makeText(GameActivity.this, R.string.service_not_found, Toast.LENGTH_LONG).show();
                    GameActivity.this.finish();
                }
                mPeriod.setValue(0x0A, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                mGatt.writeCharacteristic(mPeriod);
            } else if (characteristic == mPeriod) {
                // if setting sensor period was successful, start polling for sensor values
                mRead = mMovService.getCharacteristic(UUID.fromString("F000AA81-0451-4000-B000-000000000000"));
                if (mRead == null) {
//                    Toast.makeText(getActivity(), R.string.characteristic_not_found, Toast.LENGTH_LONG).show();
//                    getActivity().finish();
                    Toast.makeText(GameActivity.this, R.string.characteristic_not_found, Toast.LENGTH_LONG).show();
                    GameActivity.this.finish();
                }
                previousRead = Calendar.getInstance();
                mGatt.readCharacteristic(mRead);
                deviceConnected();
            }
        }

        /**
         * Callback reporting the result of a characteristic read operation.
         *
         * @param gatt GATT client invoked {@link BluetoothGatt#readCharacteristic}
         * @param characteristic Characteristic that was read from the associated
         *                       remote device.
         * @param status {@link BluetoothGatt#GATT_SUCCESS} if the read operation
         *               was completed successfully.
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            // convert raw byte array to G unit values for xyz axes
            result = Util.convertAccel(characteristic.getValue());  // TODO figure out how this work
            Log.i("Acceleration x", Double.toString(result[0]));
            Log.i("Acceleration y", Double.toString(result[1]));
            Log.i("Acceleration z", Double.toString(result[2]));

            if (mIsRecording) {
                Measurement measurement = new Measurement(result[0], result[1], result[2], formatter.format(Calendar.getInstance().getTime()));
                mRecording.add(measurement);
            }

            // poll for next values


            current_time=(current_time+1);
            int index=(current_time-1)%record_number;
            Log.i("time", Integer.toString(current_time));
           my_Xacc=result[1];
           my_Yacc=result[0];
           sum_X=sum_X+my_Xacc;
           sum_Y=sum_Y+my_Yacc;
           double Xdis=0,Ydis=0;
           int i;
            Log.i("time", Integer.toString(st_time));
           int xx=1,yy=1;
           if(my_Xacc < 0) {xx=-1;my_Xacc=-my_Xacc;}
           if(my_Yacc < 0) {yy=-1;my_Yacc=-my_Yacc;}
           int tmp=0;
           if(current_time>10) {
               if (my_Xacc > big_thld && my_Xacc > my_Yacc) {
                   if (xx > 0) {
                       tmp = 4;
                   } else {
                       tmp = 2;
                   }
               }
               else if (my_Yacc > big_thld) {
                   if (yy > 0) {
                       tmp = 3;
                   } else {
                       tmp = 1;
                   }
               }
               else if(my_Xacc<small_thld && my_Yacc<small_thld){
                   tmp=0;
               }
           }
           if(last_time<0) {
               last_time++;
           }

           if(order==0 && tmp!=0 && last_time==0){
               order=tmp*10+1;
               record_time=1;
           }
           else if(order%10<record_number && tmp!=0 ){
               if(order/10==tmp){
                   order++;
               }
               else{
                   order=0;
                   last_time=0;
                   record_time=0;
               }
           }
           else if(order%10>record_number && order%10<record_number+6 && ((order/10!= tmp && (order/10)%2==tmp%2) || tmp==0)){
                   order=order+1;
           }
           else if(order%10==record_number+6 ){
               order=0;
               last_time=0;
           }
           else if(last_time==0 && my_Xacc<small_thld && my_Yacc<small_thld){
               order=0;
               last_time=0;
           }
            Log.i("order", Integer.toString(order));
            previousRead = Calendar.getInstance();
            mGatt.readCharacteristic(mRead);

        }
    };


    /**
     * Called when the device has been fully connected.
     */
    private void deviceConnected() {
        // start connection watcher thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean hasConnection = true;
                while (hasConnection) {
                    long diff = Calendar.getInstance().getTimeInMillis() - previousRead.getTimeInMillis();
                    if (diff > 2000) {  // no reacts in 2 seconds -> lose connection
                        hasConnection = false;
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    /**
     * Called when the device should be disconnected.
     */
    private void deviceDisconnected() {
        if (mGatt != null) mGatt.disconnect();
    }
}
