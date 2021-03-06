package com.benvonhandorf.bluetoothdiscovery.BluetoothDeviceWrapper;

import android.bluetooth.*;
import android.content.Context;
import android.util.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by benvh on 9/28/13.
 */
public abstract class Device implements BluetoothGattCallbackImplementation.BluetoothGattEvents {
    private static final String TAG = Device.class.getSimpleName();

    public interface OnDeviceStateChangedListener {
        void onDeviceReady(Device device);

        void onDeviceDisconnect(Device device);
    }

    private final Context _context;
    private final BluetoothAdapter _bluetoothAdapter;
    private final BluetoothGattCallbackImplementation _bluetoothGattCallback;
    private final HashMap<UUID, Service> _serviceMap;
    private final Queue<DeviceCommand> _commandQueue;
    private OnDeviceStateChangedListener _deviceStateChangedListener;

    private BluetoothGatt _bluetoothGatt;

    private BluetoothDevice _bluetoothDevice;

    private ElementFactory _elementFactory;
    public enum BluetoothEvents {
        onConnectionStateChange,
        onServicesDiscovered,
        onDescriptorRead,
        onDescriptorWrite,
        onCharacteristicRead,
        onCharacteristicWrite,
        onCharacteristicChanged,

    }

    public Device(Context context, BluetoothAdapter bluetoothAdapter) {
        _context = context;
        _bluetoothAdapter = bluetoothAdapter;
        _bluetoothGattCallback = new BluetoothGattCallbackImplementation(this);
        _commandQueue = new ArrayBlockingQueue<DeviceCommand>(100);
        _serviceMap = new HashMap<UUID, Service>();
    }

    public Device(Context context, BluetoothAdapter bluetoothAdapter, ElementFactory elementFactory) {
        _context = context;
        _bluetoothAdapter = bluetoothAdapter;
        _bluetoothGattCallback = new BluetoothGattCallbackImplementation(this);
        _commandQueue = new ArrayBlockingQueue<DeviceCommand>(100);
        _serviceMap = new HashMap<UUID, Service>();
        _elementFactory = elementFactory;
    }

    protected abstract boolean isTargetDevice(BluetoothDevice bluetoothDevice, int i, byte[] bytes);
    private BluetoothAdapter.LeScanCallback _bluetoothLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            Log.v(TAG, String.format("Scan found device: %s at %s", bluetoothDevice.getName(), bluetoothDevice.getAddress()));
            if (isTargetDevice(bluetoothDevice, i, bytes)) {
                Log.v(TAG, String.format("Found target device: %s at %s", bluetoothDevice.getName(), bluetoothDevice.getAddress()));
                _bluetoothAdapter.stopLeScan(_bluetoothLeScanCallback);
                _bluetoothGatt = bluetoothDevice.connectGatt(_context, false, _bluetoothGattCallback);
                _bluetoothDevice = bluetoothDevice;
            } else {
                Log.v(TAG, String.format("Ignoring device: %s at %s", bluetoothDevice.getName(), bluetoothDevice.getAddress()));
            }
        }
    };


    private void scanForDevice() {
        _bluetoothAdapter.startLeScan(_bluetoothLeScanCallback);
    }

    public Device setDeviceReadyListener(OnDeviceStateChangedListener onDeviceStateChangedListener) {
        _deviceStateChangedListener = onDeviceStateChangedListener;
        return this;
    }

    public Device connect() {
        Log.v(TAG, "Starting device scan");
        scanForDevice();

        return this;
    }

    public void disconnect() {
        if (_bluetoothAdapter != null) {
            _bluetoothAdapter.stopLeScan(_bluetoothLeScanCallback);
        }

        if (_bluetoothGatt != null) {
            _bluetoothGatt.close();
        }

    }

    public void executeCommand(DeviceCommand command) {
        _commandQueue.add(command);
        if (_commandQueue.size() == 1) {
            //Nothing else is already on the queue (which implies waiting for that command to complete),
            // so go ahead and run our new command
            executeNextQueuedCommand();
        }
    }

    private void executeNextQueuedCommand() {
        DeviceCommand command = _commandQueue.peek();

        if (command != null
                && !command.isExecuting()) {
            Log.v(TAG, "Starting execution of next command");
            command.execute(_bluetoothGatt);

            if (!command.isExecuting()) {
                //Command execution is complete.  We can recurse here to get the next command that requires
                //delay
                _commandQueue.remove();

                executeNextQueuedCommand();
            }
        }
    }

    private void processEventForExecutingCommand(BluetoothEvents eventType, UUID uuid) {
        DeviceCommand command = _commandQueue.peek();

        if (command != null
                && command.isExecuting()) {
            if(command.isCommandCompleted(eventType, uuid)) {
                Log.v(TAG, "Removing command from queue, since the execution is complete");
                _commandQueue.remove();
                executeNextQueuedCommand();
            }

        }
    }

    protected ElementFactory getElementFactory() {
        if (_elementFactory == null) {
            return new ElementFactory();
        }

        return _elementFactory;
    }

    private void scanDeviceForServices() {
        //Populates our full object model of the connected device, allowing any later operations to use our own objects.
        for (BluetoothGattService bluetoothGattService : _bluetoothGatt.getServices()) {

            Service service = getElementFactory().createSpecificService(this, bluetoothGattService, getElementFactory());

            _serviceMap.put(service.getUUID(), service);
        }
    }

    public Service getService(UUID serviceId) {
        return _serviceMap.get(serviceId);
    }

    public Characteristic getCharacteristic(UUID characteristicId) {
        Characteristic result = null ;

        for(Service service : _serviceMap.values()) {
            result = service.getCharacteristic(characteristicId);

            if(result != null) {
                break ;
            }
        }

        return result;
    }

    public BluetoothDevice getBluetoothDevice() {
        return _bluetoothDevice;
    }

    @Override
    public void onConnected() {
        Log.v(TAG, "Device connected.  Discovering services");
        _bluetoothGatt.discoverServices();
    }

    @Override
    public void onDisconnected() {
        disconnect();
        if(_deviceStateChangedListener != null) {
            _deviceStateChangedListener.onDeviceDisconnect(this);
        }
    }

    @Override
    public void onServicesDiscovered() {
        Log.v(TAG, "Services discovered.  Building object graph");
        scanDeviceForServices();

        if (_deviceStateChangedListener != null) {
            Log.v(TAG, "Notifying listener of device readiness");
            _deviceStateChangedListener.onDeviceReady(this);
        }
    }

    @Override
    public void onCharacteristicEvent(BluetoothEvents eventType, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        processEventForExecutingCommand(eventType, bluetoothGattCharacteristic.getUuid());

        Characteristic characteristic = getCharacteristic(bluetoothGattCharacteristic.getUuid());

        if(characteristic != null) {
            characteristic.notifyEvent(eventType);
        }
    }

    @Override
    public void onDescriptorEvent(BluetoothEvents eventType, BluetoothGattDescriptor bluetoothGattDescriptor) {
        processEventForExecutingCommand(eventType, bluetoothGattDescriptor.getUuid());
    }

    @Override
    public void onError(BluetoothEvents eventType, int status) {

    }

    public Collection<Service> getServices() {
        return _serviceMap.values();
    }

    public static class Builder<T extends Device> {
        private Context _context;
        private BluetoothAdapter _bluetoothAdapter;
        private OnDeviceStateChangedListener _deviceStateChangedListener;

        public Builder<T> withContext(Context context) {
            _context = context ;
            return this ;
        }

        public Builder<T> withBluetoothAdapter(BluetoothAdapter bluetoothAdapter) {
            _bluetoothAdapter = bluetoothAdapter;
            return this ;
        }

        public Builder<T> withDeviceStateChangedListener(OnDeviceStateChangedListener onDeviceStateChangedListener) {
            _deviceStateChangedListener = onDeviceStateChangedListener;
            return this;
        }

        public T build() {
            return null ;
        }
    }
}
