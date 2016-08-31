package com.idevicesinc.sweetblue;


final class P_Transaction extends P_Task_RequiresConnection
{


    public P_Transaction(BleDevice device, IStateListener listener)
    {
        super(device, listener);
    }

    @Override public void execute()
    {
    }

    @Override public P_TaskPriority getPriority()
    {
        return null;
    }
}
