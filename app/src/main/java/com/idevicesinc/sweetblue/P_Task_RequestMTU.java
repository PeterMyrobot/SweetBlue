package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.listeners.P_EventFactory;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener;
import com.idevicesinc.sweetblue.utils.BleStatuses;
import com.idevicesinc.sweetblue.utils.Utils;


final class P_Task_RequestMtu extends P_Task_Transactionable
{

    private final int mMtuSize;
    private final ReadWriteListener mListener;


    public P_Task_RequestMtu(BleDevice device, IStateListener listener, int mtuSize, BleTransaction txn, ReadWriteListener rwlistener)
    {
        super(device, listener, txn);
        mMtuSize = mtuSize;
        mListener = rwlistener;
    }

    @Override public final void execute()
    {
        if (Utils.isLollipop())
        {
            getDevice().mGattManager.requestMtuChange(mMtuSize);
        }
        else
        {
            fail();
        }
    }

    @Override void onTaskTimedOut()
    {
        super.onTaskTimedOut();
        if (mListener != null)
        {
            ReadWriteListener.ReadWriteEvent event = P_EventFactory.newReadWriteEvent(getDevice(), ReadWriteListener.Type.WRITE, getDevice().getRssi(),
                    ReadWriteListener.Status.TIMED_OUT, BleStatuses.GATT_REQUEST_MTU_TIME_OUT, 0, 0, true);
            mListener.onEvent(event);
        }
    }

    final void onMtuSuccess(int newMtu)
    {
        ReadWriteListener.ReadWriteEvent event = newEvent(ReadWriteListener.Status.SUCCESS, BleStatuses.GATT_STATUS_NOT_APPLICABLE, newMtu);
        getDevice().postReadWriteEvent(mListener, event);
    }

    final void onMtuFailed(int gattStatus)
    {
        ReadWriteListener.ReadWriteEvent event = newEvent(ReadWriteListener.Status.FAILED_TO_SET_VALUE_ON_TARGET, gattStatus, mMtuSize);
        getDevice().postReadWriteEvent(mListener, event);
    }

    private ReadWriteListener.ReadWriteEvent newEvent(ReadWriteListener.Status status, int gattStatus, int mtu)
    {
        return P_EventFactory.newReadWriteEvent(getDevice(), mtu, status, gattStatus, totalTime(), timeExecuting(), true);
    }

    @Override final P_TaskPriority defaultPriority()
    {
        return P_TaskPriority.MEDIUM;
    }
}
