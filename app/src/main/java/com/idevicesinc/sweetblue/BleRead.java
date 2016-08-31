package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.utils.Uuids;
import java.util.UUID;


public final class BleRead
{

    private final UUID mServiceUuid;
    private final UUID mCharUuid;
    private final UUID mDescUuid;


    public BleRead(UUID serviceUuid, UUID charUuid)
    {
        this(serviceUuid, charUuid, Uuids.INVALID);
    }

    public BleRead(UUID charUuid)
    {
        this(Uuids.INVALID, charUuid, Uuids.INVALID);
    }

    public BleRead(UUID serviceUuid, UUID charUuid, UUID descriptorUuid)
    {
        mServiceUuid = serviceUuid;
        mCharUuid = charUuid;
        mDescUuid = descriptorUuid;
    }

    public final UUID serviceUuid()
    {
        return mServiceUuid;
    }

    public final UUID charUuid()
    {
        return mCharUuid;
    }

    public final UUID descUuid()
    {
        return mDescUuid;
    }
}
