package com.idevicesinc.sweetblue;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

import com.idevicesinc.sweetblue.annotations.Nullable;
import com.idevicesinc.sweetblue.listeners.BondListener;
import com.idevicesinc.sweetblue.listeners.DeviceConnectionFailListener;
import com.idevicesinc.sweetblue.listeners.DeviceConnectionFailListener.ConnectionFailEvent;
import com.idevicesinc.sweetblue.listeners.DeviceConnectionFailListener.Status;
import com.idevicesinc.sweetblue.listeners.DeviceConnectionFailListener.Timing;
import com.idevicesinc.sweetblue.listeners.DeviceStateListener;
import com.idevicesinc.sweetblue.listeners.DiscoveryListener;
import com.idevicesinc.sweetblue.listeners.NotifyListener;
import com.idevicesinc.sweetblue.listeners.P_BaseConnectionFailListener;
import com.idevicesinc.sweetblue.listeners.P_EventFactory;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener.ReadWriteEvent;
import com.idevicesinc.sweetblue.utils.BleScanInfo;
import com.idevicesinc.sweetblue.utils.BleStatuses;
import com.idevicesinc.sweetblue.utils.Percent;
import com.idevicesinc.sweetblue.utils.State;
import com.idevicesinc.sweetblue.utils.Utils;
import com.idevicesinc.sweetblue.utils.Utils_Rssi;
import com.idevicesinc.sweetblue.utils.Utils_String;
import com.idevicesinc.sweetblue.utils.Uuids;

import java.util.Map;
import java.util.UUID;

import static com.idevicesinc.sweetblue.BleDeviceState.*;


public class BleDevice extends BleNode
{

    public final static BleDevice NULL = new BleDevice(null, null, null, null, null, true);
    public final static String NULL_STRING = "NULL";
    static DeviceConnectionFailListener DEFAULT_CONNECTION_FAIL_LISTENER = new DefaultConnectionFailListener();


    private final boolean mIsNull;
    private int mRssi;
    private final P_DeviceStateTracker mStateTracker;
    private final P_DeviceStateTracker mStateTracker_shortTermReconnect;
    private final BleDeviceOrigin mOrigin;
    private BleDeviceOrigin mOrigin_last;
    private BleDeviceConfig mConfig;
    private BleConnectionPriority mConnectionPriority = BleConnectionPriority.MEDIUM;
    private int mMtu = BleDeviceConfig.DEFAULT_MTU_SIZE;
    private ReadWriteEvent mNullReadWriteEvent;
    private BondListener.BondEvent mNullBondEvent;
    private BleScanInfo mScanInfo;
    private String mName_native;
    private String mName_scanRecord;
    private String mName_device;
    private String mName_debug;
    //private DeviceConnectionFailListener mConnectionFailListener;
    final P_GattManager mGattManager;
    private P_ReconnectManager mReconnectManager;
    private BondListener mBondListener;
    private NotifyListener mNotifyListener;
    final P_TransactionManager mTxnManager;
    private long mLastDiscovery;
    private P_ConnectionFailManager mConnectionFailMgr;
    private DeviceConnectionFailListener.ConnectionFailEvent mNullConnectionFailEvent;
    private P_DeviceServiceManager mServiceMgr;
    P_BondManager mBondMgr;


    BleDevice(BleManager mgr, BluetoothDevice nativeDevice, BleDeviceOrigin origin, BleDeviceConfig config_nullable, String deviceName, boolean isNull)
    {
        super(mgr);
        mIsNull = isNull;
        mOrigin = origin;
        mOrigin_last = mOrigin;
        mName_device = deviceName;
        mConnectionFailMgr = new P_ConnectionFailManager(this);
        if (nativeDevice != null)
        {
            String[] address_split = nativeDevice.getAddress().split(":");
            String lastFourOfMac = address_split[address_split.length - 2] + address_split[address_split.length - 1];
            if (!mName_device.contains(lastFourOfMac))
            {
                mName_debug = Utils_String.concatStrings(mName_device.toLowerCase(), "_", lastFourOfMac);
            }
            else
            {
                mName_debug = mName_device;
            }
        }
        else
        {
            mName_debug = mName_device;
        }

        mServiceMgr = getServiceManager();

        if (!mIsNull)
        {
            mStateTracker = new P_DeviceStateTracker(this, false);
            mStateTracker_shortTermReconnect = new P_DeviceStateTracker(this, true);
            stateTracker().set(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, UNDISCOVERED, true, DISCONNECTED, true);
            mGattManager = new P_GattManager(this, nativeDevice);
            mGattManager.checkCurrentBondState();
            mReconnectManager = new P_ReconnectManager(this);
            mReconnectManager.setMaxReconnectTries(getConfig().reconnectionTries);
            mTxnManager = new P_TransactionManager(this);
            mBondMgr = new P_BondManager(this);
        }
        else
        {
            mName_device = "NULL";
            mStateTracker = new P_DeviceStateTracker(this, false);
            mGattManager = null;
            mReconnectManager = null;
            stateTracker().set(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, BleDeviceState.NULL, true);
            mStateTracker_shortTermReconnect = null;
            mTxnManager = null;
            mBondMgr = null;
        }
    }

    public void setConfig(BleDeviceConfig config)
    {
        if (isNull())
        {
            return;
        }

        mConfig = config == null ? null : config.clone();

        mReconnectManager.setMaxReconnectTries(getConfig().reconnectionTries);
        // TODO - There's more to do here
    }

    @Override public BleDeviceConfig getConfig()
    {
        return mConfig == null ? getManager().mConfig : mConfig;
    }

    public void setStateListener(DeviceStateListener listener)
    {
        mStateTracker.setListener(listener);
    }

    public DeviceStateListener getStateListener()
    {
        return mStateTracker.mStateListener;
    }

    public void setConnectionFailListener(DeviceConnectionFailListener failListener)
    {
        mConnectionFailMgr.setConnectionFailListener(failListener);
    }

    public void setNotifyListener(NotifyListener listener)
    {
        mNotifyListener = listener;
    }

    public int getMtu()
    {
        return mMtu;
    }

    public boolean setMtu(int mtuSize, ReadWriteListener listener)
    {
        if (mtuSize > 22 && mtuSize <= 517 && is(CONNECTED))
        {
            getManager().mTaskManager.add(new P_Task_RequestMtu(this, null, mtuSize, mTxnManager.getCurrent(), listener));
            return true;
        }
        return false;
    }

    public BleConnectionPriority getConnectionPriority()
    {
        return mConnectionPriority;
    }

    void updateConnectionPriority(BleConnectionPriority priority)
    {
        mConnectionPriority = priority;
    }

    /**
     * Same as {@link #setConnectionPriority(BleConnectionPriority, ReadWriteListener)}, without the {@link ReadWriteListener} argument, if you don't
     * care about when this updates (or are using a default {@link ReadWriteListener} in {@link BleManager}).
     */
    public ReadWriteEvent setConnectionPriority(BleConnectionPriority priority)
    {
        return setConnectionPriority_private(priority, null, P_TaskPriority.LOW);
    }

    /**
     * Wrapper for {@link BluetoothGatt#requestConnectionPriority(int)} which attempts to change the connection priority for a given connection.
     * This will eventually update the value returned by {@link #getConnectionPriority()} but it is not
     * instantaneous. When we receive confirmation from the native stack then this value will be updated. The device must be {@link BleDeviceState#CONNECTED} for
     * this call to succeed.
     *
     * @see #setConnectionPriority(BleConnectionPriority, ReadWriteListener)
     * @see #getConnectionPriority()
     *
     * @return (see similar comment for return value of {@link #connect(BleTransaction.Auth, BleTransaction.Init, StateListener, ConnectionFailListener)}).
     */
    public ReadWriteEvent setConnectionPriority(BleConnectionPriority priority, ReadWriteListener listener)
    {
        return setConnectionPriority_private(priority, listener, P_TaskPriority.LOW);
    }

    private ReadWriteEvent setConnectionPriority_private(BleConnectionPriority priority, ReadWriteListener listener, P_TaskPriority taskPriority)
    {
        if (!Utils.isLollipop())
        {
            ReadWriteEvent event = P_EventFactory.newReadWriteEvent(this, priority, ReadWriteListener.Status.ANDROID_VERSION_NOT_SUPPORTED, BleStatuses.GATT_STATUS_NOT_APPLICABLE, 0d, 0d, true);
            postReadWriteEvent(listener, event);
            return event;
        }
        ReadWriteEvent event = mServiceMgr.getEarlyOutEvent(Uuids.INVALID, Uuids.INVALID, Uuids.INVALID, null, ReadWriteListener.Type.WRITE, ReadWriteListener.Target.CONNECTION_PRIORITY);
        if (event != null)
        {
            postReadWriteEvent(listener, event);
            return event;
        }
        addTask(new P_Task_RequestConnectionPriority(this, null, mTxnManager.getCurrent(), taskPriority, priority, listener));
        return NULL_READWRITE_EVENT();
    }

    /**
     * Returns the name returned from {@link BluetoothDevice#getName()}.
     */
    public String getName_native()
    {
        return mName_native;
    }

    /**
     * Returns the name of this device. This is our best guess for which name to use. First, it pulls the name from
     * {@link BluetoothDevice#getName()}. If that is null, it is then pulled from the scan record. If that is null,
     * then a name will be assigned &lt;No_Name_XX:XX&gt;, where XX:XX are the last 4 of the device's mac address.
     */
    public String getName()
    {
        return mName_device;
    }

    /**
     * Returns the name that was parsed from this device's scan record.
     */
    public String getName_scanRecord()
    {
        return mName_scanRecord;
    }

    public BluetoothDevice getNative()
    {
        return mGattManager.getNativeDevice();
    }

    public BluetoothGatt getNativeGatt()
    {
        return mGattManager.getGatt();
    }

    @Override public String getMacAddress()
    {
        return mGattManager.getMacAddress();
    }

    public int getTxPower()
    {
        return mScanInfo.getTxPower().value;
    }

    public int getManufacturerId()
    {
        return mScanInfo.getManufacturerId();
    }

    public byte[] getManufacturerData()
    {
        return mScanInfo.getManufacturerData();
    }

    public Map<UUID, byte[]> getAdvertisedServiceData()
    {
        return mScanInfo.getServiceData();
    }

    public boolean is(BleDeviceState state)
    {
        return state.overlaps(getStateMask());
    }

    public boolean isAny(BleDeviceState... states)
    {
        if (states != null)
        {
            for (int i = 0; i < states.length; i++)
            {
                if (is(states[i])) return true;
            }
        }
        return false;
    }

    /**
     * Returns <code>true</code> if there is any bitwise overlap between the provided value and {@link #getStateMask()}.
     *
     * @see #isAll(int)
     */
    public boolean isAny(final int mask_BleDeviceState)
    {
        return (getStateMask() & mask_BleDeviceState) != 0x0;
    }

    /**
     * Returns <code>true</code> if there is complete bitwise overlap between the provided value and {@link #getStateMask()}.
     *
     * @see #isAny(int)
     */
    public boolean isAll(final int mask_BleDeviceState)
    {
        return (getStateMask() & mask_BleDeviceState) == mask_BleDeviceState;
    }

    public int getRssi()
    {
        return mRssi;
    }

    public Percent getRssiPercent()
    {
        if (isNull())
        {
            return Percent.ZERO;
        }
        final double percent = Utils_Rssi.percent(getRssi(), getConfig().rssi_min, getConfig().rssi_max);
        return Percent.fromDouble_clamped(percent);
    }

    public boolean isNull()
    {
        return mIsNull;
    }

    @Override P_ServiceManager newServiceManager()
    {
        return new P_DeviceServiceManager(this);
    }

    public BleDeviceOrigin getOrigin()
    {
        return mOrigin;
    }

    public boolean equals(@Nullable(Nullable.Prevalence.NORMAL) final BleDevice device_nullable)
    {
        if (device_nullable == null) return false;
        if (device_nullable == this) return true;
        if (device_nullable.getNative() == null || this.getNative() == null) return false;
        if (this.isNull() && device_nullable.isNull()) return true;

        return device_nullable.getNative().equals(this.getNative());
    }

    public void onUndiscovered(P_StateTracker.E_Intent intent)
    {
        stateTracker().update(intent, BleStatuses.GATT_STATUS_NOT_APPLICABLE, UNDISCOVERED, true, DISCOVERED, false, DISCONNECTED, true, ADVERTISING, false);
        if (mTxnManager != null)
        {
            mTxnManager.cancelAllTxns();
        }
        if (getManager().mDiscoveryListener != null)
        {
            final DiscoveryListener.DiscoveryEvent event = P_EventFactory.newDiscoveryEvent(this, DiscoveryListener.LifeCycle.UNDISCOVERED);
            getManager().mPostManager.postCallback(new Runnable()
            {
                @Override public void run()
                {
                    if (getManager().mDiscoveryListener != null)
                    {
                        getManager().mDiscoveryListener.onEvent(event);
                    }
                }
            });
        }
    }

    public void undiscover()
    {
        // TODO - Implement this
    }

    public ConnectionFailEvent connect(BleTransaction.Auth authTxn)
    {
        return connect(authTxn, getConfig().defaultInitTxn, null, null);
    }

    public ConnectionFailEvent connect(BleTransaction.Init initTxn)
    {
        return connect(getConfig().defaultAuthTxn, initTxn, null, null);
    }

    public ConnectionFailEvent connect(BleTransaction.Auth authTxn, BleTransaction.Init initTxn)
    {
        return connect(authTxn, initTxn, null, null);
    }

    public ConnectionFailEvent connect(DeviceConnectionFailListener failListener)
    {
        return connect(getConfig().defaultAuthTxn, getConfig().defaultInitTxn, null, failListener);
    }

    public ConnectionFailEvent connect(DeviceStateListener stateListener, DeviceConnectionFailListener failListener)
    {
        return connect(getConfig().defaultAuthTxn, getConfig().defaultInitTxn, stateListener, failListener);
    }

    public ConnectionFailEvent connect(BleTransaction.Auth authTxn, DeviceStateListener stateListener, DeviceConnectionFailListener failListener)
    {
        return connect(authTxn, getConfig().defaultInitTxn, stateListener, failListener);
    }

    public ConnectionFailEvent connect(BleTransaction.Init initTxn, DeviceStateListener stateListener, DeviceConnectionFailListener failListener)
    {
        return connect(getConfig().defaultAuthTxn, initTxn, stateListener, failListener);
    }

    public ConnectionFailEvent connect(BleTransaction.Auth authTxn, BleTransaction.Init initTxn, DeviceStateListener stateListener, DeviceConnectionFailListener failListener)
    {
        mTxnManager.setAuthTxn(authTxn);
        mTxnManager.setInitTxn(initTxn);
        if (failListener != null)
        {
            mConnectionFailMgr.setConnectionFailListener(failListener);
        }
        if (stateListener != null)
        {
            mStateTracker.setListener(stateListener);
        }
        return connect();
    }

    public ConnectionFailEvent connect()
    {
        return connect_private(true);
    }

    public BondListener.BondEvent bond(BondListener listener)
    {
        if (listener != null)
        {
            mBondMgr.setBondListener(listener);
        }

        if (isNull())
        {
            return mBondMgr.postBondEvent(P_StateTracker.E_Intent.INTENTIONAL, BleStatuses.BOND_FAIL_REASON_NOT_APPLICABLE, BondListener.Status.NULL_DEVICE);
        }
        if (isAny(BONDED, BONDING))
        {
            return mBondMgr.postBondEvent(P_StateTracker.E_Intent.INTENTIONAL, BleStatuses.BOND_FAIL_REASON_NOT_APPLICABLE, BondListener.Status.ALREADY_BONDING_OR_BONDED);
        }
        getManager().mTaskManager.add(new P_Task_Bond(this, null));

        return NULL_BOND_EVENT();
    }

    void unbond_internal(final P_TaskPriority priority_nullable, final BondListener.Status status)
    {
        getManager().mTaskManager.add(new P_Task_Unbond(this, null, priority_nullable));

        final boolean wasBonding = is(BONDING);

        stateTracker().update(P_StateTracker.E_Intent.INTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, P_GattManager.RESET_TO_UNBONDED);

        if (wasBonding)
        {
            mBondMgr.postBondEvent(P_StateTracker.E_Intent.INTENTIONAL, BleStatuses.BOND_FAIL_REASON_NOT_APPLICABLE, status);
        }
    }

    public boolean unBond()
    {
        final boolean alreadyUnbonded = is(UNBONDED);

        unbond_internal(null, BondListener.Status.CANCELLED_FROM_UNBOND);

        return alreadyUnbonded;
    }

    private ConnectionFailEvent connect_private(boolean explicit)
    {
        if (!isAny(CONNECTING, CONNECTED, CONNECTING_OVERALL) || isAny(RECONNECTING_SHORT_TERM, RECONNECTING_LONG_TERM))
        {
            mConnectionFailMgr.onExplicitConnectionStarted();
            mBondMgr.onConnectAttempt();
            stateTracker().update(P_StateTracker.E_Intent.INTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, CONNECTING_OVERALL, true);
            getManager().mTaskManager.add(new P_Task_Connect(this, null, explicit));
            return NULL_CONNECTIONFAIL();
        }
        else
        {
            return DeviceConnectionFailListener.ConnectionFailEvent.EARLY_OUT(this, Status.ALREADY_CONNECTING_OR_CONNECTED);
        }
    }

    void connect_implicitly()
    {
        connect_private(false);
    }

    public void disconnect()
    {
        if (!isAny(DISCONNECTED, DISCONNECTING))
        {
            getManager().mTaskManager.add(new P_Task_Disconnect(this, null));
        }
    }

    public void disconnectWhenReady()
    {
        if (!isAny(DISCONNECTED, DISCONNECTING))
        {
            getManager().mTaskManager.add(new P_Task_Disconnect(this, null, P_TaskPriority.LOW));
        }
    }

    public void disconnect_remote()
    {
        // TODO - Implement this
    }

    public ReadWriteEvent read(BleRead read, ReadWriteListener listener)
    {
        return read_internal(read.serviceUuid(), read.charUuid(), read.descUuid(), listener);
    }

    public ReadWriteEvent read(BleRead read)
    {
        return read_internal(read.serviceUuid(), read.charUuid(), read.descUuid(), null);
    }

    public ReadWriteEvent read(UUID charUuid, ReadWriteListener listener)
    {
        return read_internal(null, charUuid, null, listener);
    }

    public ReadWriteEvent read(UUID charUuid)
    {
        return read_internal(null, charUuid, null, null);
    }

    public ReadWriteEvent read(UUID serviceUuid, UUID charUuid, ReadWriteListener listener)
    {
        return read_internal(serviceUuid, charUuid, null, listener);
    }

    public ReadWriteEvent read(UUID serviceUuid, UUID charUuid)
    {
        return read_internal(serviceUuid, charUuid, null, null);
    }

    public ReadWriteEvent readDescriptor(BleRead read, ReadWriteListener listener)
    {
        return read_internal(read.serviceUuid(), read.charUuid(), read.descUuid(), listener);
    }

    public ReadWriteEvent readDescriptor(BleRead read)
    {
        return read_internal(read.serviceUuid(), read.charUuid(), read.descUuid(), null);
    }

    public ReadWriteEvent readDescriptor(UUID charUuid, UUID descriptorUuid, ReadWriteListener listener)
    {
        return read_internal(null, charUuid, descriptorUuid, listener);
    }

    public ReadWriteEvent readDescriptor(UUID charUuid, UUID descriptorUuid)
    {
        return read_internal(null, charUuid, descriptorUuid, null);
    }

    public ReadWriteEvent readDescriptor(UUID serviceUuid, UUID charUuid, UUID descriptorUuid, ReadWriteListener listener)
    {
        return read_internal(serviceUuid, charUuid, descriptorUuid, listener);
    }

    public ReadWriteEvent readDescriptor(UUID serviceUuid, UUID charUuid, UUID descriptorUuid)
    {
        return read_internal(serviceUuid, charUuid, descriptorUuid, null);
    }

    private ReadWriteEvent read_internal(UUID serviceUuid, UUID charUuid, UUID descriptorUuid, ReadWriteListener listener)
    {
        ReadWriteListener.Target t = (descriptorUuid == null || descriptorUuid.equals(Uuids.INVALID)) ? ReadWriteListener.Target.CHARACTERISTIC : ReadWriteListener.Target.DESCRIPTOR;
        ReadWriteEvent earlyEvent = mServiceMgr.getEarlyOutEvent(serviceUuid, charUuid, descriptorUuid, null, ReadWriteListener.Type.READ, t);
        if (earlyEvent != null)
        {
            postReadWriteEvent(listener, earlyEvent);

            return earlyEvent;
        }

        if (t == ReadWriteListener.Target.CHARACTERISTIC)
        {
            mBondMgr.bondIfNeeded(charUuid, BleDeviceConfig.BondFilter.CharacteristicEventType.READ);
            final P_Task_Read read = new P_Task_Read(this, null, serviceUuid, charUuid, mTxnManager.getCurrent(), listener);
            addTask(read);
        }
        else
        {
            // TODO - Implement reading descriptor task
        }
        return NULL_READWRITE_EVENT();
    }

    public ReadWriteEvent write(BleWrite write, ReadWriteListener listener)
    {
        return write(write.serviceUuid(), write.charUuid(), write.value(), listener);
    }

    public ReadWriteEvent write(BleWrite write)
    {
        return write(write.serviceUuid(), write.charUuid(), write.value(), null);
    }

    public ReadWriteEvent write(UUID charUuid, byte[] data, ReadWriteListener listener)
    {
        return write(null, charUuid, data, listener);
    }

    public ReadWriteEvent write(UUID charUuid, byte[] data)
    {
        return write(null, charUuid, data, null);
    }

    public ReadWriteEvent write(UUID serviceUuid, UUID charUuid, byte[] data)
    {
        return write_internal(serviceUuid, charUuid, null, data, null);
    }

    public ReadWriteEvent write(UUID serviceUuid, UUID charUuid, byte[] data, ReadWriteListener listener)
    {
        return write_internal(serviceUuid, charUuid, null, data, listener);
    }

    public ReadWriteEvent writeDescriptor(BleWrite write, ReadWriteListener listener)
    {
        return write_internal(write.serviceUuid(), write.charUuid(), write.descriptorUuid(), write.value(), listener);
    }

    public ReadWriteEvent writeDescriptor(BleWrite write)
    {
        return write_internal(write.serviceUuid(), write.charUuid(), write.descriptorUuid(), write.value(), null);
    }

    public ReadWriteEvent writeDescriptor(UUID charUuid, UUID descriptorUuid, byte[] data)
    {
        return write_internal(null, charUuid, descriptorUuid, data, null);
    }

    public ReadWriteEvent writeDescriptor(UUID charUuid, UUID descriptorUuid, byte[] data, ReadWriteListener listener)
    {
        return write_internal(null, charUuid, descriptorUuid, data, listener);
    }

    public ReadWriteEvent writeDescriptor(UUID serviceUuid, UUID charUuid, UUID descriptorUuid, byte[] data)
    {
        return write_internal(serviceUuid, charUuid, descriptorUuid, data, null);
    }

    public ReadWriteEvent writeDescriptor(UUID serviceUuid, UUID charUuid, UUID descriptorUuid, byte[] data, ReadWriteListener listener)
    {
        return write_internal(serviceUuid, charUuid, descriptorUuid, data, listener);
    }

    private ReadWriteEvent write_internal(UUID serviceUuid, UUID charUuid, UUID descriptorUuid, byte[] data, ReadWriteListener listener)
    {
        ReadWriteListener.Target t = (descriptorUuid == null || descriptorUuid.equals(Uuids.INVALID)) ? ReadWriteListener.Target.CHARACTERISTIC : ReadWriteListener.Target.DESCRIPTOR;
        ReadWriteEvent earlyOutEvent = mServiceMgr.getEarlyOutEvent(serviceUuid, charUuid, descriptorUuid, data, ReadWriteListener.Type.WRITE, t);
        if (earlyOutEvent != null)
        {
            postReadWriteEvent(listener, earlyOutEvent);

            return earlyOutEvent;
        }

        if (t == ReadWriteListener.Target.DESCRIPTOR)
        {
            // TODO - Implement writing descriptor task
        }
        else
        {
            mBondMgr.bondIfNeeded(charUuid, BleDeviceConfig.BondFilter.CharacteristicEventType.WRITE);
            final P_Task_Write write = new P_Task_Write(this, null, serviceUuid, charUuid, data, mTxnManager.getCurrent(), listener);
            addTask(write);
        }

        return NULL_READWRITE_EVENT();
    }

    public ReadWriteEvent enableNotify(UUID serviceUuid, UUID charUuid, ReadWriteListener listener)
    {
        ReadWriteEvent earlyEvent = mServiceMgr.getEarlyOutEvent(serviceUuid, charUuid, Uuids.INVALID, null, ReadWriteListener.Type.ENABLING_NOTIFICATION, ReadWriteListener.Target.CHARACTERISTIC);
        if (earlyEvent != null)
        {
            postReadWriteEvent(listener, earlyEvent);
            return earlyEvent;
        }
        mBondMgr.bondIfNeeded(charUuid, BleDeviceConfig.BondFilter.CharacteristicEventType.ENABLE_NOTIFY);
        P_Task_ToggleNotify toggle = new P_Task_ToggleNotify(this, null, serviceUuid, charUuid, true, mTxnManager.getCurrent(), listener);
        addTask(toggle);
        return NULL_READWRITE_EVENT();
    }

    public ReadWriteEvent enableNotify(UUID charUuid, ReadWriteListener listener)
    {
        return enableNotify(null, charUuid, listener);
    }

    public ReadWriteEvent disableNotify(UUID serviceUuid, UUID charUuid, ReadWriteListener listener)
    {
        ReadWriteEvent earlyEvent = mServiceMgr.getEarlyOutEvent(serviceUuid, charUuid, null, null, ReadWriteListener.Type.DISABLING_NOTIFICATION, ReadWriteListener.Target.CHARACTERISTIC);
        if (earlyEvent != null)
        {
            postReadWriteEvent(listener, earlyEvent);
            return earlyEvent;
        }
        final P_Task_ToggleNotify toggle = new P_Task_ToggleNotify(this, null, serviceUuid, charUuid, false, mTxnManager.getCurrent(), listener);
        addTask(toggle);
        return NULL_READWRITE_EVENT();
    }

    public ReadWriteEvent disableNotify(UUID charUuid, ReadWriteListener listener)
    {
        return disableNotify(null, charUuid, listener);
    }

    public void disconnectWithReason(P_TaskPriority priority, Status bleTurningOff, Timing notApplicable, int gattStatusNotApplicable, int bondFailReasonNotApplicable, ReadWriteEvent readWriteEvent)
    {
        // TODO - Implement this
    }

    public void performOta(BleTransaction.Ota otaTxn)
    {
        if (!is(INITIALIZED))
        {
            // TODO - Throw error here
            return;
        }
        if (is(PERFORMING_OTA))
        {
            // TODO - Throw error here
            return;
        }
        stateTracker().update(P_StateTracker.E_Intent.INTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, PERFORMING_OTA, true);
        mTxnManager.start(otaTxn);
    }

    @Override public String toString()
    {
        if (isNull())
        {
            return NULL_STRING;
        }
        else
        {
            return Utils_String.concatStrings(mName_debug, " ", stateTracker().toString());
        }
    }


    void setNameFromNative(String name)
    {
        mName_native = name;
    }

    void setNameFromScanRecord(String name)
    {
        mName_scanRecord = name;
    }

    void onNewlyDiscovered(int rssi, BleScanInfo scanInfo, BleDeviceOrigin origin)
    {
        mOrigin_last = origin;
        mRssi = rssi;
        onDiscovered_private(rssi, scanInfo);
        stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, UNDISCOVERED, false, DISCOVERED, true, ADVERTISING, origin == BleDeviceOrigin.FROM_DISCOVERY);
    }

    void onRediscovered(BluetoothDevice device_native, BleScanInfo scanInfo, int rssi, BleDeviceOrigin origin)
    {
        mOrigin_last = origin;
        onDiscovered_private(rssi, scanInfo);
        stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, ADVERTISING, true);
    }

    long lastDiscovery()
    {
        return mLastDiscovery;
    }

    void update(long curTimeMs)
    {
    }

    P_DeviceStateTracker stateTracker()
    {
        return mStateTracker;
    }

    public int getStateMask()
    {
        return stateTracker().getState();
    }

    void onNotify(final NotifyListener.NotifyEvent event)
    {
        getManager().mPostManager.postCallback(new Runnable()
        {
            @Override public void run()
            {
                if (mNotifyListener != null)
                {
                    mNotifyListener.onEvent(event);
                }
                if (getManager().mDefaultNotifyListener != null)
                {
                    getManager().mDefaultNotifyListener.onEvent(event);
                }
            }
        });
    }

    void doNativeConnect()
    {
        mGattManager.connect();
    }

    void onConnected()
    {
        stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, CONNECTED, true, DISCOVERING_SERVICES, true, CONNECTING, false,
                CONNECTING_OVERALL, false, DISCONNECTED, false, RECONNECTING_SHORT_TERM, false, RECONNECTING_LONG_TERM, false);
        getManager().deviceConnected(this);
        getManager().mTaskManager.succeedTask(P_Task_Connect.class, this);
        getManager().mTaskManager.add(new P_Task_DiscoverServices(this, null));
    }

    void onConnecting()
    {
        stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, CONNECTING, true);
    }

    void onConnectionFailed(Status status, Timing timing, int gattStatus)
    {
        if (mReconnectManager.shouldFail())
        {
            mConnectionFailMgr.onConnectionFailed(status, timing, gattStatus, BleDeviceState.getTransitoryConnectionState(getStateMask()), P_BaseConnectionFailListener.AutoConnectUsage.UNKNOWN,
                    BleStatuses.BOND_FAIL_REASON_NOT_AVAILABLE, null);
            resetToDisconnected();
            getManager().mTaskManager.failTask(P_Task_Connect.class, this, false);
        }
        else
        {
            disconnect();
            getManager().mTaskManager.cancelTask(P_Task_Connect.class, this);
            mReconnectManager.reconnect(gattStatus);
        }
    }

    void onDisconnectedExplicitly()
    {
        mConnectionFailMgr.onExplicitDisconnect();
        resetToDisconnected();
        getManager().mTaskManager.succeedTask(P_Task_Disconnect.class, this);
    }

    void onDisconnected(int gattStatus)
    {
        if (getManager().mTaskManager.isCurrent(P_Task_Connect.class, this))
        {
            getManager().mTaskManager.failTask(P_Task_Connect.class, this, false);
            onConnectionFailed(Status.NATIVE_CONNECTION_FAILED, Timing.EVENTUALLY, gattStatus);
            return;
        }

        resetToDisconnected();
    }

    void onServicesDiscovered()
    {
        stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, DISCOVERING_SERVICES, false, SERVICES_DISCOVERED, true,
                AUTHENTICATING, true);

        getManager().mTaskManager.succeedTask(P_Task_Connect.class, this);

        if (mConnectionPriority != BleConnectionPriority.MEDIUM)
        {
            if (isAny(RECONNECTING_SHORT_TERM, RECONNECTING_LONG_TERM))
            {
                setConnectionPriority_private(mConnectionPriority, null, P_TaskPriority.MEDIUM);
            }
        }

        if (mTxnManager.getAuthTxn() != null)
        {
            mTxnManager.start(mTxnManager.getAuthTxn());
        }
        else
        {
            stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, AUTHENTICATED, true, AUTHENTICATING, false,
                    INITIALIZING, true);
            if (mTxnManager.getInitTxn() != null)
            {
                mTxnManager.start(mTxnManager.getInitTxn());
            }
            else
            {
                onInitialized();
            }
        }
    }

    void onAuthenticated()
    {
        stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, AUTHENTICATED, true, AUTHENTICATING, false);
    }

    void onInitialized()
    {
        mConnectionFailMgr.onFullyInitialized();
        stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, INITIALIZED, true, INITIALIZING, false);
    }

    void onBonding(P_StateTracker.E_Intent intent)
    {
        mBondMgr.onBonding(intent);
    }

    void onBond(P_StateTracker.E_Intent intent)
    {
        mBondMgr.onBond(intent);
    }

    void onBondFailed(P_StateTracker.E_Intent intent, int failReason, BondListener.Status status)
    {
        mBondMgr.onBondFailed(intent, failReason, status);
    }

    void onUnbond(P_StateTracker.E_Intent intent)
    {
        mBondMgr.onUnbond(intent);
    }

    void onMtuChanged(int newMtu)
    {
        mMtu = newMtu;
    }

    void postReadWriteEvent(final ReadWriteListener listener, final ReadWriteEvent event)
    {
        getManager().mPostManager.postCallback(new Runnable()
        {
            @Override public void run()
            {
                if (listener != null)
                {
                    listener.onEvent(event);
                }
                if (getManager().mDefaultReadWriteListener != null)
                {
                    getManager().mDefaultReadWriteListener.onEvent(event);
                }
            }
        });
    }

    void addReadTime(long time)
    {
        // TODO - Implement with time estimators
    }

    void addWriteTime(long time)
    {
        // TODO - Implement with addReadTime once time estimators are implemented
    }

    private ReadWriteEvent EARLY_OUT_READWRITE(ReadWriteListener.Status reason, ReadWriteListener.Type type)
    {
        return P_EventFactory.newReadWriteEvent(this, type, mRssi, reason, BleStatuses.GATT_STATUS_NOT_APPLICABLE, 0d, 0d, true);
    }

    BondListener.BondEvent NULL_BOND_EVENT()
    {
        if (mNullBondEvent != null)
        {
            return mNullBondEvent;
        }

        mNullBondEvent = BondListener.BondEvent.NULL(this);

        return mNullBondEvent;
    }

    private ReadWriteEvent NULL_READWRITE_EVENT()
    {
        if (mNullReadWriteEvent != null)
        {
            return mNullReadWriteEvent;
        }

        mNullReadWriteEvent = ReadWriteEvent.NULL(this);

        return mNullReadWriteEvent;
    }

    private DeviceConnectionFailListener.ConnectionFailEvent NULL_CONNECTIONFAIL()
    {
        if (mNullConnectionFailEvent != null)
        {
            return mNullConnectionFailEvent;
        }

        mNullConnectionFailEvent = DeviceConnectionFailListener.ConnectionFailEvent.NULL(this);

        return mNullConnectionFailEvent;
    }

    private void resetToDisconnected()
    {
        stateTracker().set(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, DISCONNECTED, true, ADVERTISING, true, DISCOVERED, true);
        if (mGattManager == null)
        {
            stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, UNBONDED, true);
        }
        else if (mGattManager.isBonding())
        {
            stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, BONDING, true);
        }
        else if (mGattManager.isBonded())
        {
            stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, BONDED, true);
        }
        else
        {
            stateTracker().update(P_StateTracker.E_Intent.UNINTENTIONAL, BleStatuses.GATT_STATUS_NOT_APPLICABLE, UNBONDED, true);
        }
    }

    private void onDiscovered_private(int rssi, BleScanInfo scanInfo)
    {
        mLastDiscovery = System.currentTimeMillis();
        if (!scanInfo.isNull())
        {
            if (mScanInfo == null || !mScanInfo.equals(scanInfo))
            {
                mScanInfo = scanInfo.clone();
            }
        }
        if (rssi < 0)
        {
            updateRssi(rssi);
        }
    }

    private void updateRssi(int rssi)
    {
        mRssi = rssi;
    }

    private void addTask(P_Task task)
    {
        if (mTxnManager.isRunning())
        {
            // If it's an atomic transaction, and a transactionable task, then change the priority so it ends up
            // before any other task with lower priority.
            if (mTxnManager.isAtomic() && task instanceof P_Task_Transactionable)
            {
                ((P_Task_Transactionable) task).mPriority = P_TaskPriority.ATOMIC_TRANSACTION;
            }
        }
        getManager().mTaskManager.add(task);
    }

    /**
     * Spells out "Decaff Coffee"...clever, right? I figure all zeros or
     * something would actually have a higher chance of collision in a dev
     * environment.
     */
    static String NULL_MAC()
    {
        return "DE:CA:FF:C0:FF:EE";
    }

    public static class DefaultConnectionFailListener implements DeviceConnectionFailListener
    {

        /**
         * The default retry count provided to {@link DefaultConnectionFailListener}.
         * So if you were to call {@link BleDevice#connect()} and all connections failed, in total the
         * library would try to connect {@value #DEFAULT_CONNECTION_FAIL_RETRY_COUNT}+1 times.
         *
         * @see DefaultConnectionFailListener
         */
        public static final int DEFAULT_CONNECTION_FAIL_RETRY_COUNT = 2;

        /**
         * The default connection fail limit past which {@link DefaultConnectionFailListener} will start returning {@link DeviceConnectionFailListener.Please#retryWithAutoConnectTrue()}.
         */
        public static final int DEFAULT_FAIL_COUNT_BEFORE_USING_AUTOCONNECT = 2;

        private final int m_retryCount;
        private final int m_failCountBeforeUsingAutoConnect;

        public DefaultConnectionFailListener()
        {
            this(DEFAULT_CONNECTION_FAIL_RETRY_COUNT, DEFAULT_FAIL_COUNT_BEFORE_USING_AUTOCONNECT);
        }

        public DefaultConnectionFailListener(int retryCount, int failCountBeforeUsingAutoConnect)
        {
            m_retryCount = retryCount;
            m_failCountBeforeUsingAutoConnect = failCountBeforeUsingAutoConnect;
        }

        public int getRetryCount()
        {
            return m_retryCount;
        }

        @Override public Please onEvent(ConnectionFailEvent e)
        {
            //--- DRK > Not necessary to check this ourselves, just being explicit.
            if (!e.status().allowsRetry() || e.device().is(RECONNECTING_LONG_TERM))
            {
                return Please.doNotRetry();
            }

            if (e.failureCountSoFar() <= m_retryCount)
            {
                if (e.failureCountSoFar() >= m_failCountBeforeUsingAutoConnect)
                {
                    return Please.retryWithAutoConnectTrue();
                }
                else
                {
                    if (e.status() == Status.NATIVE_CONNECTION_FAILED && e.timing() == Timing.TIMED_OUT)
                    {
                        if (e.autoConnectUsage() == AutoConnectUsage.USED)
                        {
                            return Please.retryWithAutoConnectFalse();
                        }
                        else if (e.autoConnectUsage() == AutoConnectUsage.NOT_USED)
                        {
                            return Please.retryWithAutoConnectTrue();
                        }
                        else
                        {
                            return Please.retry();
                        }
                    }
                    else
                    {
                        return Please.retry();
                    }
                }
            }
            else
            {
                return Please.doNotRetry();
            }
        }
    }

}
