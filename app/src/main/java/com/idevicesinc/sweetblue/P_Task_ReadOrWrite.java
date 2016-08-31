package com.idevicesinc.sweetblue;


import android.bluetooth.BluetoothGatt;

import com.idevicesinc.sweetblue.listeners.P_EventFactory;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener.Status;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener.ReadWriteEvent;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener.Type;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener.Target;
import com.idevicesinc.sweetblue.utils.BleStatuses;

import java.lang.reflect.Field;
import java.util.UUID;


abstract class P_Task_ReadOrWrite extends P_Task_Transactionable implements P_Task.IStateListener
{

    private static final String FIELD_NAME_AUTH_RETRY = "mAuthRetry";


    private Boolean mAuthRetryValue_onExecute = null;
    private boolean mTriedToKickOffBond = false;
    private ReadWriteListener mListener;
    private UUID mServiceUuid;
    private UUID mCharUuid;
    private UUID mDescriptorUuid;


    public P_Task_ReadOrWrite(BleDevice device, IStateListener listener, UUID serviceUuid, UUID charUuid, UUID descUuid, BleTransaction txn, ReadWriteListener rwListener)
    {
        super(device, listener, txn);
        mListener = rwListener;
        mServiceUuid = serviceUuid;
        mCharUuid = charUuid;
        mDescriptorUuid = descUuid;
    }


    protected abstract ReadWriteEvent newReadWriteEvent(Status status, int gattStatus, byte[] data);

    protected Target getTarget()
    {
        return Target.CHARACTERISTIC;
    }


    @Override public void onStateChanged(P_Task task, P_TaskState state)
    {
        if( state == P_TaskState.TIMED_OUT )
        {
            checkIfBondingKickedOff();

            if( triedToKickOffBond() )
            {
                // TODO - Implement these if it is determined that they are needed
                //getDevice().notifyOfPossibleImplicitBondingAttempt();
                //getDevice().mBondMgr.saveNeedsBondingIfDesired();

                getManager().getLogger().i("Kicked off bond and " + P_TaskState.TIMED_OUT.name());
            }
        }
    }

    @Override public void execute()
    {
        mAuthRetryValue_onExecute = getAuthRetryValue();
    }

    protected UUID getServiceUuid()
    {
        return mServiceUuid;
    }

    protected UUID getCharUuid()
    {
        return mCharUuid;
    }

    protected UUID getDescUuid()
    {
        return mDescriptorUuid;
    }

    protected ReadWriteListener getListener()
    {
        return mListener;
    }

    protected void succeedRead(byte[] value, Target target, Type type)
    {
        super.succeed();

        final ReadWriteEvent event = newSuccessReadWriteEvent(value, target, type);
        getDevice().addReadTime(event.time_total().millis());
        getDevice().postReadWriteEvent(mListener, event);
        getDevice().postReadWriteEvent(getManager().mDefaultReadWriteListener, event);
    }

    protected void succeedWrite(byte[] data)
    {
        super.succeed();

        final ReadWriteEvent event = newReadWriteEvent(Status.SUCCESS, BluetoothGatt.GATT_SUCCESS, data);
        getDevice().addWriteTime(event.time_total().millis());
        getDevice().postReadWriteEvent(mListener, event);
        getDevice().postReadWriteEvent(getManager().mDefaultReadWriteListener, event);
    }

    private ReadWriteEvent newSuccessReadWriteEvent(byte[] data, Target target, ReadWriteListener.Type type)
    {
        return P_EventFactory.newReadWriteEvent(getDevice(), getServiceUuid(), getCharUuid(), getDescUuid(), type, target, data, Status.SUCCESS, BluetoothGatt.GATT_SUCCESS, totalTime(),
                timeExecuting(), true);
    }


    private boolean triedToKickOffBond()
    {
        return mTriedToKickOffBond;
    }

    private Boolean getAuthRetryValue()
    {
        final BluetoothGatt gatt = getDevice().getNativeGatt();

        if( gatt != null )
        {
            try
            {
                final Field[] fields = gatt.getClass().getDeclaredFields();
                Field field = gatt.getClass().getDeclaredField(FIELD_NAME_AUTH_RETRY);
                final boolean isAccessible_saved = field.isAccessible();
                field.setAccessible(true);
                Boolean result = field.getBoolean(gatt);
                field.setAccessible(isAccessible_saved);

                return result;
            }
            catch (Exception e)
            {
                getManager().getLogger().e("Problem getting value of " + gatt.getClass().getSimpleName() + "." + FIELD_NAME_AUTH_RETRY);
            }
        }
        else
        {
            getManager().getLogger().e("Expected gatt object to be not null");
        }

        return null;
    }

    private void checkIfBondingKickedOff()
    {
        if( getState() == P_TaskState.EXECUTING )
        {
            if( mTriedToKickOffBond == false )
            {
                final Boolean authRetryValue_now = getAuthRetryValue();

                if( mAuthRetryValue_onExecute != null && authRetryValue_now != null )
                {
                    if( mAuthRetryValue_onExecute == false && authRetryValue_now == true )
                    {
                        mTriedToKickOffBond = true;

                        getManager().getLogger().i("Kicked off bond!");
                    }
                }
            }
        }
    }

    protected boolean write_earlyOut(final byte[] data_nullable)
    {
        if( data_nullable == null )
        {
            fail();
            ReadWriteEvent event = newReadWriteEvent(Status.NULL_DATA, BleStatuses.GATT_STATUS_NOT_APPLICABLE, data_nullable);
            getDevice().postReadWriteEvent(getListener(), event);
            return true;
        }
        else if( data_nullable.length == 0 )
        {
            fail();
            ReadWriteEvent event = newReadWriteEvent(Status.EMPTY_DATA, BleStatuses.GATT_STATUS_NOT_APPLICABLE, data_nullable);
            getDevice().postReadWriteEvent(getListener(), event);
            return true;
        }
        else
        {
            return false;
        }
    }
}
