package com.idevicesinc.sweetblue;

import com.idevicesinc.sweetblue.listeners.P_EventFactory;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener;
import com.idevicesinc.sweetblue.utils.BleStatuses;
import java.util.UUID;


public final class P_Task_Write extends P_Task_ReadOrWrite
{

    private byte[] mValue;


    public P_Task_Write(BleDevice device, IStateListener listener, UUID serviceUuid, UUID charUuid, byte[] value, BleTransaction txn, ReadWriteListener writeListener)
    {
        super(device, listener, serviceUuid, charUuid, null, txn, writeListener);
        mValue = value;
    }

    public P_Task_Write(BleDevice device, IStateListener listener, BleWrite write, BleTransaction txn, ReadWriteListener writeListener)
    {
        this(device, listener, write.serviceUuid(), write.charUuid(), write.value(), txn, writeListener);
    }

    @Override public final void execute()
    {
        super.execute();
        if (!write_earlyOut(mValue))
        {
            if (!getDevice().mGattManager.write(getServiceUuid(), getCharUuid(), mValue))
            {
                ReadWriteListener.ReadWriteEvent event = newReadWriteEvent(ReadWriteListener.Status.NO_MATCHING_TARGET, BleStatuses.GATT_STATUS_NOT_APPLICABLE, mValue);
                getDevice().postReadWriteEvent(getListener(), event);
                failImmediately();
            }
        }
    }

    final void onWrite()
    {
        succeedWrite(mValue);
    }

    final byte[] getValue()
    {
        return mValue;
    }

    @Override final void onTaskTimedOut()
    {
        super.onTaskTimedOut();
        ReadWriteListener.ReadWriteEvent event = newReadWriteEvent(ReadWriteListener.Status.TIMED_OUT, BleStatuses.GATT_STATUS_NOT_APPLICABLE, mValue);
        getDevice().postReadWriteEvent(getListener(), event);
    }

    @Override final P_TaskPriority defaultPriority()
    {
        return P_TaskPriority.LOW;
    }

    @Override protected ReadWriteListener.ReadWriteEvent newReadWriteEvent(ReadWriteListener.Status status, int gattStatus, byte[] data)
    {
        return P_EventFactory.newReadWriteEvent(getDevice(), getServiceUuid(), getCharUuid(), getDescUuid(), ReadWriteListener.Type.WRITE, getTarget(), mValue, status,
                gattStatus, totalTime(), timeExecuting(), true);
    }
}
