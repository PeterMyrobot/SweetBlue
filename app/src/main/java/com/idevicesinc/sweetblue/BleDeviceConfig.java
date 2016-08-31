package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.annotations.Immutable;
import com.idevicesinc.sweetblue.listeners.BondListener;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener;
import com.idevicesinc.sweetblue.utils.Event;
import com.idevicesinc.sweetblue.utils.Interval;
import com.idevicesinc.sweetblue.utils.Utils;
import com.idevicesinc.sweetblue.utils.Utils_String;

import java.util.UUID;

public class BleDeviceConfig extends BleNodeConfig implements Cloneable
{

    /**
     * Default fallback value for {@link #rssi_min}.
     */
    public static final int DEFAULT_RSSI_MIN					= -120;

    /**
     * Default fallback value for {@link #rssi_max}.
     */
    public static final int DEFAULT_RSSI_MAX					= -30;

    /**
     * The default MTU size in bytes for gatt reads/writes/notifies/etc.
     */
    public static final int DEFAULT_MTU_SIZE					= 23;

    /**
     * Default value for {@link #minScanTimeToUndiscover}.
     */
    public static final double DEFAULT_MINIMUM_SCAN_TIME		= 5.0;

    public static final double DEFAULT_CONNECT_TIME_OUT         = 31.0;

    public boolean cacheDeviceOnUndiscovery                     = true;

    /**
     * Default is <code>false</code> - whether to use <code>BluetoothGatt.refresh()</code> right before service discovery.
     * This method is not in the public Android API, so its use is disabled by default. You may find it useful to enable
     * if your remote device is routinely changing its gatt service profile. This method call supposedly clears a cache
     * that would otherwise prevent changes from being discovered.
     */
    public Boolean useGattRefresh								= false;

    /**
     * The number of times SweetBlue will retry connecting to a device, if it fails. Default is <code>2</code> (So it will try a total of
     * 3 times to connect).
     */
    public int reconnectionTries                                = 2;

    /**
     * Default is {@link Interval#DISABLED}. If a device exceeds this amount of time since its
     * last discovery then it is a candidate for being undiscovered.
     * You may want to configure this number based on the phone or
     * manufacturer. For example, based on testing, in order to make undiscovery snappier the Galaxy S5 could use lower times.
     */
    public Interval timeToUndiscover                            = Interval.DISABLED;

    public Interval connectTimeOut                              = Interval.secs(DEFAULT_CONNECT_TIME_OUT);

    /**
     * Default is {@link #DEFAULT_MINIMUM_SCAN_TIME}seconds - Undiscovery of devices must be
     * approximated by checking when the last time was that we discovered a device,
     * and if this time is greater than {@link #timeToUndiscover} then the device is undiscovered. However a scan
     * operation must be allowed a certain amount of time to make sure it discovers all nearby devices that are
     * still advertising. This is that time in seconds.
     * <br><br>
     * Use {@link Interval#DISABLED} to disable undiscovery altogether.
     */
    public Interval minScanTimeToUndiscover                     = Interval.secs(DEFAULT_MINIMUM_SCAN_TIME);

    public boolean useLeTransportForBonding                     = false;

    /**
     * This will set the bond behavior of a device when connecting. See {@link BondOnConnectOption} for possible values.
     * Default is {@link BondOnConnectOption#NONE}.
     */
    public BondOnConnectOption bondOnConnectOption              = BondOnConnectOption.NONE;

    public BondFilter bondFilter                                = new DefaultBondFilter();

    /**
     * Tells SweetBlue to use Android's built-in autoConnect option. It's been observed that this doesn't work very
     * well for some devices, so it's <code>false</code> by default.
     */
    public boolean useAndroidAutoConnect                        = false;

    public int rssi_min                                         = DEFAULT_RSSI_MIN;

    public int rssi_max                                         = DEFAULT_RSSI_MAX;

    public BleTransaction.Auth defaultAuthTxn                   = null;

    public BleTransaction.Init defaultInitTxn                   = null;


    @Override public BleDeviceConfig clone()
    {
        return (BleDeviceConfig) super.clone();
    }


    interface BondFilter
    {

        /**
         * Class pass to {@link BondFilter#onEvent(ConnectEvent)} when attempting to connect
         * to a {@link BleDevice}.
         */
        final class ConnectEvent
        {

            private final BleDevice mDevice;

            ConnectEvent(BleDevice device)
            {
                mDevice = device;
            }

            /**
             * Return the {@link BleDevice} attempting to connect.
             */
            public final BleDevice device()
            {
                return mDevice;
            }
        }

        enum CharacteristicEventType
        {
            /**
             * Started from {@link BleDevice#read(UUID, ReadWriteListener)} and related methods.
             */
            READ,

            /**
             * Started from {@link BleDevice#write(UUID, byte[], ReadWriteListener)} or overloads.
             */
            WRITE,

            /**
             * Started from {@link BleDevice#enableNotify(UUID, ReadWriteListener)} or overloads.
             */
            ENABLE_NOTIFY;
        }

        /**
         * Class passed to {@link BondFilter#onEvent(CharacteristicEvent)}.
         */
        final class CharacteristicEvent extends Event
        {

            private final BleDevice m_device;
            private final UUID m_uuid;
            private final CharacteristicEventType m_type;


            CharacteristicEvent(BleDevice device, UUID uuid, CharacteristicEventType type)
            {
                m_device = device;
                m_uuid = uuid;
                m_type = type;
            }

            /**
             * Returns the {@link BleDevice} in question.
             */
            public final BleDevice device(){  return m_device;  }

            /**
             * Convience to return the mac address of {@link #device()}.
             */
            public final String macAddress()  {  return m_device.getMacAddress();  }

            /**
             * Returns the type of characteristic operation, read, write, etc.
             */
            public final CharacteristicEventType type(){  return m_type;  }

            /**
             * Returns the {@link UUID} of the characteristic in question.
             */
            public final UUID charUuid(){  return m_uuid;  }


            @Override public final String toString()
            {
                return Utils_String.toString
                        (
                                this.getClass(),
                                "device",		device().getName(),
                                "charUuid",		device().getManager().getLogger().charName(charUuid()),
                                "type",			type()
                        );
            }
        }

        /**
         * Return value for the various interface methods of {@link BondFilter}.
         * Use static constructor methods to create instances.
         */
        @com.idevicesinc.sweetblue.annotations.Advanced
        @Immutable
        class Please
        {
            private final boolean m_bond;
            private final BondListener m_bondListener;


            Please(boolean bond, BondListener listener)
            {
                m_bond = bond;
                m_bondListener = listener;
            }

            boolean bond_private()
            {
                return m_bond;
            }

            BondListener listener()
            {
                return m_bondListener;
            }

            /**
             * Device should be bonded if it isn't already.
             */
            public static Please bond()
            {
                return new Please(true, null);
            }

            /**
             * Returns {@link #bond()} if the given condition holds <code>true</code>, {@link #doNothing()} otherwise.
             */
            public static Please bondIf(boolean condition)
            {
                return condition ? bond() : doNothing();
            }

            /**
             * Same as {@link #bondIf(boolean)} but lets you pass a {@link BondListener} as well.
             */
            public static Please bondIf(boolean condition, BondListener listener)
            {
                return condition ? bond(listener) : doNothing();
            }

            /**
             * Same as {@link #bond()} but lets you pass a {@link BondListener} as well.
             */
            public static Please bond(BondListener listener)
            {
                return new Please(true, listener);
            }

            /**
             * Device's bond state should not be affected.
             */
            public static Please doNothing()
            {
                return new Please(false, null);
            }
        }

        Please onEvent(ConnectEvent event);
        Please onEvent(CharacteristicEvent event);
    }

    public class DefaultBondFilter implements BondFilter
    {

        public boolean phoneHasBondingIssues()
        {
            return Utils.phoneHasBondingIssues();
        }

        @Override public Please onEvent(ConnectEvent event)
        {
            return Please.bondIf(phoneHasBondingIssues());
        }

        @Override public Please onEvent(CharacteristicEvent event)
        {
            return Please.doNothing();
        }
    }

    public enum BondOnConnectOption
    {
        /**
         * Do no automatic bond
         */
        NONE,

        /**
         * Perform a bond before connecting, if the device is not already bonded.
         */
        BOND,

        /**
         * Bond with the device before connecting. If the device is already bonded, then unbond first, then bond.
         */
        RE_BOND;
    }
}
