package com.idevicesinc.sweetblue;


abstract class P_Task_Transactionable extends P_Task_RequiresConnection
{

    protected P_TaskPriority mPriority = null;
    private final BleTransaction mTxn;


    public P_Task_Transactionable(BleDevice device, IStateListener listener, BleTransaction txn)
    {
        super(device, listener);
        mTxn = txn;
    }


    BleTransaction getTxn()
    {
        return mTxn;
    }

    abstract P_TaskPriority defaultPriority();

    @Override public P_TaskPriority getPriority()
    {
        return mPriority != null ? mPriority : defaultPriority();
    }

}
