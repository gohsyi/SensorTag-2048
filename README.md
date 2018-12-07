# SensorTag Accelerometer
An Android app connecting to a TI CC2650 SensorTag for acceleration measurements.

It does
* Realtime display of acceleration measurements on all three axes
* Recording of acceleration data in a certain timeframe with
* Display of maximum experienced combined acceleration
* Linegraph showing values for all three axes as well as combined acceleration
* Export of raw data as CSV

Tested on a TI SensorTag CC2650STK running firmware 1.32.

## Libraries
This project uses the following libraries
* [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)
* Android AppCompat and Support Library

## Implementations

- `DeviceFragment.java extends View.OnClickListener`
    - `BluetoothGattCallback mCallback`
        - `onCharacteristicRead` has parameter `BluetoothGattCharacteristic characteristic`, which contains information read from another parameter `BluetoothGatt gatt`.
