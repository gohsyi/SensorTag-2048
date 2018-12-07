package com.martindisch.accelerometer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.util.Log;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Locale;
import java.util.UUID;

/**
 * Fragment showing data for a connected device.
 */
public class DeviceFragment extends Fragment implements View.OnClickListener {

    private static final String ARG_ADDRESS = "address";
    private String mAddress;
    private boolean mIsRecording = false;
    private LinkedList<Measurement> mRecording;
    private OnStatusListener mListener;
    private Calendar previousRead, recordingStart;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mGatt;
    private BluetoothGattService mMovService;
    private BluetoothGattCharacteristic mRead, mEnable, mPeriod;

    private TextView mXAxis, mYAxis, mZAxis, mMax;
    private Button mStart, mStop, mExport;
    private LineChart mChart;

    /**
     * Mandatory empty constructor.
     */
    public DeviceFragment() {
    }

    /**
     * Returns a new instance of this Fragment.
     *
     * @param address the MAC address of the device to connect
     * @return A new instance of {@link DeviceFragment}
     */
    public static DeviceFragment newInstance(String address) {
        DeviceFragment fragment = new DeviceFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ADDRESS, address);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Called to do initial creation of a fragment.  This is called before
     * {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     *
     * <p>Note that this can be called while the fragment's activity is
     * still in the process of being created.  As such, you can not rely
     * on things like the activity's content view hierarchy being initialized
     * at this point.  If you want to do work once the activity itself is
     * created, see {@link #onActivityCreated(Bundle)}.
     *
     * <p>Any restored child fragments will be created before the base
     * <code>Fragment.onCreate</code> method returns.</p>
     *
     * @param savedInstanceState If the fragment is being re-created from
     * a previous saved state, this is the state.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {  // getArguments() returns a Bundle, like map in C++
            mAddress = getArguments().getString(ARG_ADDRESS);
        }

        // initialize bluetooth manager & adapter
        BluetoothManager manager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();
    }

    /**
     * Called when the fragment is visible to the user and actively running.
     */
    @Override
    public void onResume() {
        super.onResume();
        connectDevice(mAddress);
    }

    /**
     * Called when the Fragment is no longer resumed.
     */
    @Override
    public void onPause() {
        deviceDisconnected();
        super.onPause();
    }

    /**
     * Creates a GATT connection to the given device.
     *
     * @param address String containing the address of the device
     */
    private void connectDevice(String address) {
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(getActivity(), R.string.state_off, Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }
        mListener.onShowProgress();
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        mGatt = device.connectGatt(getActivity(), false, mCallback);
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
                Toast.makeText(getActivity(), R.string.service_not_found, Toast.LENGTH_LONG).show();
                getActivity().finish();
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
            mEnable.setValue(0b1000111000, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
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
                    Toast.makeText(getActivity(), R.string.service_not_found, Toast.LENGTH_LONG).show();
                    getActivity().finish();
                }
                mPeriod.setValue(0x0A, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                mGatt.writeCharacteristic(mPeriod);
            } else if (characteristic == mPeriod) {
                // if setting sensor period was successful, start polling for sensor values
                mRead = mMovService.getCharacteristic(UUID.fromString("F000AA81-0451-4000-B000-000000000000"));
                if (mRead == null) {
                    Toast.makeText(getActivity(), R.string.characteristic_not_found, Toast.LENGTH_LONG).show();
                    getActivity().finish();
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
            Log.d("Acceleration x", Double.toString(result[0]));
            Log.d("Acceleration y", Double.toString(result[1]));
            Log.d("Acceleration z", Double.toString(result[2]));

            if (mIsRecording) {
                Measurement measurement = new Measurement(result[0], result[1], result[2], formatter.format(Calendar.getInstance().getTime()));
                mRecording.add(measurement);
            }
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (isAdded()) {
                        // update current acceleration readings
                        mXAxis.setText(String.format(getString(R.string.xAxis), Math.abs(result[0])));
                        mYAxis.setText(String.format(getString(R.string.yAxis), Math.abs(result[1])));
                        mZAxis.setText(String.format(getString(R.string.zAxis), Math.abs(result[2])));
                        mXAxis.setTextColor(ContextCompat.getColor(getActivity(), result[0] < 0 ? R.color.red : R.color.green));
                        mYAxis.setTextColor(ContextCompat.getColor(getActivity(), result[1] < 0 ? R.color.red : R.color.green));
                        mZAxis.setTextColor(ContextCompat.getColor(getActivity(), result[2] < 0 ? R.color.red : R.color.green));
                    }
                }
            });
            // poll for next values
            previousRead = Calendar.getInstance();
            mGatt.readCharacteristic(mRead);
        }
    };

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bStart:
                startRecording();
                break;
            case R.id.bStop:
                stopRecording();
                break;
            case R.id.bExport:
                try {
                    // create and write output file in cache directory
                    File outputFile = new File(
                            getActivity().getCacheDir(),
                            "recording" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(Calendar.getInstance().getTime()) + ".csv"
                    );
                    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outputFile));
                    writer.write(Util.recordingToCSV(mRecording));
                    writer.close();

                    // get Uri from FileProvider
                    Uri contentUri = FileProvider.getUriForFile(getActivity(), "com.martindisch.accelerometer.fileprovider", outputFile);

                    // create sharing intent
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    // temp permission for receiving app to read this file
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.setType("text/csv");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    startActivity(Intent.createChooser(shareIntent, "Choose an app"));
                } catch (IOException e) {
                    Toast.makeText(getActivity(), R.string.error_file, Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    /**
     * Called when the device has been fully connected.
     */
    private void deviceConnected() {
        mListener.onHideProgress();
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStart.setEnabled(true);
            }
        });

        // start connection watcher thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean hasConnection = true;
                while (hasConnection) {
                    long diff = Calendar.getInstance().getTimeInMillis() - previousRead.getTimeInMillis();
                    if (diff > 2000) {  // no reacts in 2 seconds -> lose connection
                        hasConnection = false;
                        mStart.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getContext(), R.string.connection_lost, Toast.LENGTH_LONG).show();
                                deviceDisconnected();
                            }
                        });
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
        stopRecording();
        mStart.setEnabled(false);
        if (mGatt != null) mGatt.disconnect();
    }

    /**
     * Starts the recording and updates the UI to reflect that.
     */
    private void startRecording() {
        // update UI
        mStart.setEnabled(false);
        mExport.setEnabled(false);
        mStop.setEnabled(true);
        mIsRecording = true;
        mMax.setVisibility(View.INVISIBLE);

        mRecording = new LinkedList<>();
        recordingStart = Calendar.getInstance();
    }

    /**
     * Stops the recording and updates the UI to reflect that.
     */
    private void stopRecording() {
        if (mIsRecording) {
            // update UI
            mStop.setEnabled(false);
            mStart.setEnabled(true);
            mExport.setEnabled(true);
            mIsRecording = false;

            if (mRecording.size() > 0) {
                ArrayList<Entry> combined = new ArrayList<>(mRecording.size());
                ArrayList<Entry> x = new ArrayList<>(mRecording.size());
                ArrayList<Entry> y = new ArrayList<>(mRecording.size());
                ArrayList<Entry> z = new ArrayList<>(mRecording.size());
                int i = 0;
                double max = 0;
                for (Measurement m : mRecording) {
                    combined.add(new Entry(i, (float) m.getCombined()));
                    if (m.getCombined() > max) max = m.getCombined();
                    x.add(new Entry(i, (float) m.getX()));
                    y.add(new Entry(i, (float) m.getY()));
                    z.add(new Entry(i++, (float) m.getZ()));
                }
                LineDataSet sCombined = new LineDataSet(combined, getString(R.string.combined));
                LineDataSet sX = new LineDataSet(x, getString(R.string.x));
                LineDataSet sY = new LineDataSet(y, getString(R.string.y));
                LineDataSet sZ = new LineDataSet(z, getString(R.string.z));
                sCombined.setDrawCircles(false);
                sX.setDrawCircles(false);
                sY.setDrawCircles(false);
                sZ.setDrawCircles(false);
                sCombined.setColor(ContextCompat.getColor(getActivity(), R.color.red));
                sX.setColor(ContextCompat.getColor(getActivity(), R.color.green));
                sY.setColor(ContextCompat.getColor(getActivity(), R.color.light_green));
                sZ.setColor(ContextCompat.getColor(getActivity(), R.color.lime));
                LineData lineData = new LineData(sCombined, sX, sY, sZ);
                mChart.setData(lineData);
                mChart.invalidate();

                mMax.setText(String.format(getString(R.string.max), max));
                mMax.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(getContext(), R.string.no_data, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_device, container, false);
        mXAxis = (TextView) layout.findViewById(R.id.tvXAxis);
        mYAxis = (TextView) layout.findViewById(R.id.tvYAxis);
        mZAxis = (TextView) layout.findViewById(R.id.tvZAxis);
        mMax = (TextView) layout.findViewById(R.id.tvMax);
        mStart = (Button) layout.findViewById(R.id.bStart);
        mStop = (Button) layout.findViewById(R.id.bStop);
        mExport = (Button) layout.findViewById(R.id.bExport);
        mChart = (LineChart) layout.findViewById(R.id.chart);

        mChart.setDescription(null);
        mChart.setHighlightPerDragEnabled(false);
        mChart.setHighlightPerTapEnabled(false);
        mChart.setPinchZoom(true);
        mChart.getLegend().setDrawInside(true);
        mChart.setExtraTopOffset(10);

        mStart.setOnClickListener(this);
        mStop.setOnClickListener(this);
        mExport.setOnClickListener(this);
        return layout;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnStatusListener) {
            mListener = (OnStatusListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnStatusListener");
        }
    }
}
