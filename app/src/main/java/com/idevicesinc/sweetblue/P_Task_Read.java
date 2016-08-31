package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.listeners.P_EventFactory;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener;
import com.idevicesinc.sweetblue.utils.BleStatuses;
import com.idevicesinc.sweetblue.utils.Uuids;
import java.util.UUID;


public final class P_Task_Read extends P_Task_ReadOrWrite
{

    public P_Task_Read(BleDevice device, IStateListener stateListener, UUID charUuid, BleTransaction txn, ReadWriteListener listener)
    {
        this(device, stateListener, null, charUuid, txn, listener);
    }

    public P_Task_Read(BleDevice device, IStateListener stateListener, BleRead read, BleTransaction txn, ReadWriteListener listener)
    {
        this(device, stateListener, read.serviceUuid(), read.charUuid(), txn, listener);
    }

    public P_Task_Read(BleDevice device, IStateListener stateListener, UUID serviceUuid, UUID charUuid, BleTransaction txn, ReadWriteListener listener)
    {
        super(device, stateListener, serviceUuid, charUuid, null, txn, listener);
    }

    @Override final P_TaskPriority defaultPriority()
    {
        return P_TaskPriority.LOW;
    }

    @Override public final void execute()
    {
        super.execute();
        if (!getDevice().mGattManager.read(getServiceUuid(), getCharUuid()))
        {
            ReadWriteListener.ReadWriteEvent e = newReadWriteEvent(ReadWriteListener.Status.NO_MATCHING_TARGET, BleStatuses.GATT_STATUS_NOT_APPLICABLE, null);
            getDevice().postReadWriteEvent(getListener(), e);
            failImmediately();
        }
    }

    @Override final void onTaskTimedOut()
    {
        super.onTaskTimedOut();
        ReadWriteListener.ReadWriteEvent event = newReadWriteEvent(ReadWriteListener.Status.TIMED_OUT, BleStatuses.GATT_STATUS_NOT_APPLICABLE, null);
        getDevice().postReadWriteEvent(getListener(), event);
    }

    /**
     * Gets called from {@link P_GattManager} when a read comes in.
     */
    final void onRead(final byte[] data)
    {
        succeedRead(data, ReadWriteListener.Target.CHARACTERISTIC, ReadWriteListener.Type.READ);
    }

    final void onReadFailed(int gattStatus)
    {
        ReadWriteListener.ReadWriteEvent event = newReadWriteEvent(ReadWriteListener.Status.REMOTE_GATT_FAILURE, gattStatus, null);
        getDevice().postReadWriteEvent(getListener(), event);
        fail();
    }

    @Override protected ReadWriteListener.ReadWriteEvent newReadWriteEvent(ReadWriteListener.Status status, int gattStatus, byte[] data)
    {
        return P_EventFactory.newReadWriteEvent(getDevice(), getServiceUuid(), getCharUuid(), getDescUuid(), ReadWriteListener.Type.READ, getTarget(),
                data, status, gattStatus, totalTime(), timeExecuting(), true);
    }
}
