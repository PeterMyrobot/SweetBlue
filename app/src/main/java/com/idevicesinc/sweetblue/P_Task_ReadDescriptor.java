package com.idevicesinc.sweetblue;


import android.bluetooth.BluetoothGatt;

import com.idevicesinc.sweetblue.listeners.P_EventFactory;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener;
import com.idevicesinc.sweetblue.utils.BleStatuses;

import java.util.UUID;


final class P_Task_ReadDescriptor extends P_Task_ReadOrWrite
{


    public P_Task_ReadDescriptor(BleDevice device, IStateListener listener, UUID serviceUuid, UUID charUuid, UUID descUuid, BleTransaction txn, ReadWriteListener rwListener)
    {
        super(device, listener, serviceUuid, charUuid, descUuid, txn, rwListener);
    }

    @Override protected ReadWriteListener.ReadWriteEvent newReadWriteEvent(ReadWriteListener.Status status, int gattStatus, byte[] data)
    {
        return P_EventFactory.newReadWriteEvent(getDevice(), getServiceUuid(), getCharUuid(), getDescUuid(), ReadWriteListener.Type.READ, getTarget(), data, status, gattStatus, totalTime(),
                timeExecuting(), true);
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
        final ReadWriteListener.Status status = getDevice().mGattManager.readDescriptor(getServiceUuid(), getCharUuid(), getDescUuid());
        if (status != null)
        {
            ReadWriteListener.ReadWriteEvent event = newReadWriteEvent(status, BleStatuses.GATT_STATUS_NOT_APPLICABLE, null);
            getDevice().postReadWriteEvent(getListener(), event);
            fail();
        }
    }

    final void onRead(byte[] data)
    {
        ReadWriteListener.ReadWriteEvent event = newReadWriteEvent(ReadWriteListener.Status.SUCCESS, BluetoothGatt.GATT_SUCCESS, data);
        getDevice().postReadWriteEvent(getListener(), event);
        succeed();
    }

    final void onReadFailed(int gattStatus)
    {
        ReadWriteListener.ReadWriteEvent event = newReadWriteEvent(ReadWriteListener.Status.REMOTE_GATT_FAILURE, gattStatus, null);
        getDevice().postReadWriteEvent(getListener(), event);
    }
}
