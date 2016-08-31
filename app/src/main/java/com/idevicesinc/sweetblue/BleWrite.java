package com.idevicesinc.sweetblue;


import java.util.UUID;


public final class BleWrite
{

    private final UUID mServiceUuid;
    private final UUID mCharUuid;
    private final UUID mDescriptorUuid;
    private final byte[] mValue;

    public BleWrite(UUID serviceUuid, UUID charUuid, byte[] value)
    {
        this(serviceUuid, charUuid, null, value);
    }

    public BleWrite(UUID charUuid, byte[] value)
    {
        this(null, charUuid, value);
    }

    public BleWrite(UUID serviceUuid, UUID charUuid, UUID descriptorUuid, byte[] value)
    {
        mServiceUuid = serviceUuid;
        mCharUuid = charUuid;
        mDescriptorUuid = descriptorUuid;
        mValue = value;
    }

    public final UUID serviceUuid()
    {
        return mServiceUuid;
    }

    public final UUID charUuid()
    {
        return mCharUuid;
    }

    public final UUID descriptorUuid()
    {
        return mDescriptorUuid;
    }

    public final byte[] value()
    {
        return mValue;
    }

}
