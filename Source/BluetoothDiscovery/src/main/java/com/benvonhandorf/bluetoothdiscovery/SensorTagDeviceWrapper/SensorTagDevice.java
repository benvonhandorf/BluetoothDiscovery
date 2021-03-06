package com.benvonhandorf.bluetoothdiscovery.SensorTagDeviceWrapper;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import com.benvonhandorf.bluetoothdiscovery.BluetoothDeviceWrapper.Device;
import com.benvonhandorf.bluetoothdiscovery.SensorTagDeviceWrapper.IRSensor.IrSensorService;

/**
 * Created by benvh on 9/28/13.
 */
public class SensorTagDevice extends Device {
    public SensorTagDevice(Context context, BluetoothAdapter bluetoothAdapter) {
        super(context, bluetoothAdapter, new SensorTagElementFactory());
    }

    @Override
    protected boolean isTargetDevice(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
        return "SensorTag".equals(bluetoothDevice.getName());
    }

    public IrSensorService getIrService() {
        return (IrSensorService) getService(Sensors.IR_SENSOR.SERVICE);
    }

}
