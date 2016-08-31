package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.listeners.P_EventFactory;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener;
import com.idevicesinc.sweetblue.utils.BleStatuses;
import java.util.UUID;


public final class P_Task_WriteDescriptor extends P_Task_ReadOrWrite
{

    private final byte[] mData;


    public P_Task_WriteDescriptor(BleDevice device, IStateListener listener, UUID serviceUuid, UUID charUuid, UUID descriptorUuid, byte[] value, BleTransaction txn, ReadWriteListener writelistener)
    {
        super(device, listener, serviceUuid, charUuid, descriptorUuid, txn, writelistener);
        mData = value;
    }

    public P_Task_WriteDescriptor(BleDevice device, IStateListener listener, BleWrite write, BleTransaction txn, ReadWriteListener writeListener)
    {
        this(device, listener, write.serviceUuid(), write.charUuid(), write.descriptorUuid(), write.value(), txn, writeListener);
    }

    @Override P_TaskPriority defaultPriority()
    {
        return P_TaskPriority.LOW;
    }

    @Override protected ReadWriteListener.Target getTarget()
    {
        return ReadWriteListener.Target.DESCRIPTOR;
    }

    @Override public void execute()
    {
        super.execute();
        if (!write_earlyOut(mData))
        {
            ReadWriteListener.Status status = getDevice().mGattManager.writeDescriptor(getServiceUuid(), getCharUuid(), getDescUuid(), mData);
            if (status != null)
            {
                ReadWriteListener.ReadWriteEvent event = newReadWriteEvent(status, BleStatuses.GATT_STATUS_NOT_APPLICABLE, mData);
                getDevice().postReadWriteEvent(getListener(), event);
                fail();
            }
        }
    }

    void onWriteSucceeded()
    {
        succeedWrite(mData);
    }

    void onWriteFailed(int gattStatus)
    {
        fail();
        ReadWriteListener.ReadWriteEvent event = newReadWriteEvent(ReadWriteListener.Status.REMOTE_GATT_FAILURE, gattStatus, mData);
        getDevice().postReadWriteEvent(getListener(), event);
    }

    @Override protected ReadWriteListener.ReadWriteEvent newReadWriteEvent(ReadWriteListener.Status status, int gattStatus, byte[] data)
    {
        return P_EventFactory.newReadWriteEvent(getDevice(), getServiceUuid(), getCharUuid(), getDescUuid(), ReadWriteListener.Type.WRITE, getTarget(), data,
                status, gattStatus, totalTime(), timeExecuting(), true);
    }
}
