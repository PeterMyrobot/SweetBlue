package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.compat.L_Util;
import com.idevicesinc.sweetblue.listeners.P_EventFactory;
import com.idevicesinc.sweetblue.listeners.ReadWriteListener;
import com.idevicesinc.sweetblue.utils.BleStatuses;
import com.idevicesinc.sweetblue.utils.Utils;


final class P_Task_RequestConnectionPriority extends P_Task_Transactionable implements P_Task.IStateListener
{

    private final BleConnectionPriority mConnPriority;
    private final ReadWriteListener mListener;
    private final P_TaskPriority mPriority;


    public P_Task_RequestConnectionPriority(BleDevice device, IStateListener listener, BleTransaction txn, P_TaskPriority taskPriority, BleConnectionPriority connPriority, ReadWriteListener rwListener)
    {
        super(device, listener, txn);
        mPriority = taskPriority;
        mConnPriority = connPriority;
        mListener = rwListener;
    }

    @Override P_TaskPriority defaultPriority()
    {
        return mPriority;
    }

    @Override public void execute()
    {
        if (Utils.isLollipop())
        {
            if (!L_Util.requestConnectionPriority(getDevice(), mConnPriority.getNativeMode()))
            {
                fail(ReadWriteListener.Status.FAILED_TO_SEND_OUT, BleStatuses.GATT_STATUS_NOT_APPLICABLE);
            }
            else
            {
                // We wait for about a half a second to say success, as there's no native callback for this right now.
            }
        }
        else
        {
            fail(ReadWriteListener.Status.ANDROID_VERSION_NOT_SUPPORTED, BleStatuses.GATT_STATUS_NOT_APPLICABLE);
        }
    }

    @Override public void update(long curTimeMs)
    {
        final double timeToSuccess = 500d; //TODO

        if( getState() == P_TaskState.EXECUTING && timeExecuting() >= timeToSuccess )
        {
            succeed(mConnPriority);
        }
    }


    @Override public void onStateChanged(P_Task task, P_TaskState state)
    {
        if( state == P_TaskState.TIMED_OUT )
        {
            getDevice().postReadWriteEvent(mListener, newEvent(ReadWriteListener.Status.TIMED_OUT, BleStatuses.GATT_STATUS_NOT_APPLICABLE, mConnPriority));
        }
        else if( state == P_TaskState.SUCCEEDED )
        {
            getDevice().updateConnectionPriority(mConnPriority);
        }
    }

    private void fail(ReadWriteListener.Status status, int gattStatus)
    {
        this.fail();

        getDevice().postReadWriteEvent(mListener, newEvent(status, gattStatus, mConnPriority));
    }

    private void succeed(final BleConnectionPriority connectionPriority)
    {
        super.succeed();

        final ReadWriteListener.ReadWriteEvent event = newEvent(ReadWriteListener.Status.SUCCESS, BleStatuses.GATT_SUCCESS, connectionPriority);

        getDevice().postReadWriteEvent(mListener, event);
    }

    private ReadWriteListener.ReadWriteEvent newEvent(final ReadWriteListener.Status status, final int gattStatus, final BleConnectionPriority connectionPriority)
    {
        return P_EventFactory.newReadWriteEvent(getDevice(), mConnPriority, status, gattStatus, totalTime(), timeExecuting(), true);
    }
}
