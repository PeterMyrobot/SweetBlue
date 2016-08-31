package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.listeners.BondListener;
import com.idevicesinc.sweetblue.listeners.P_EventFactory;
import com.idevicesinc.sweetblue.utils.BleStatuses;
import com.idevicesinc.sweetblue.utils.Utils;

import java.util.UUID;

final class P_BondManager
{

    private final BleDevice mDevice;
    private BondListener mListener;


    P_BondManager(BleDevice device)
    {
        mDevice = device;
    }

    private BleManager getManager()
    {
        return mDevice.getManager();
    }

    void onConnectAttempt()
    {
        final BleDeviceConfig.BondFilter filter = getManager().mConfig.bondFilter;

        if (filter != null)
        {
            BleDeviceConfig.BondFilter.Please p = filter.onEvent(new BleDeviceConfig.BondFilter.ConnectEvent(mDevice));
            Boolean bond = p.bond_private();
            if (bond != null && bond)
            {
                BleDeviceConfig.BondOnConnectOption bondOption = getManager().mConfig.bondOnConnectOption;
                if (bondOption != null)
                {
                    switch (bondOption)
                    {
                        case BOND:
                            if (!mDevice.isAny(BleDeviceState.BONDED, BleDeviceState.BONDING))
                            {
                                mDevice.bond(null);
                            }
                            break;
                        case RE_BOND:
                            if (mDevice.is(BleDeviceState.BONDED))
                            {
                                mDevice.unBond();
                            }
                            mDevice.bond(null);
                            break;
                        default:
                    }
                }
                else
                {
                    getManager().getLogger().e("BondFilter has been set, but bondOnConnectOption is null in BleManagerConfig!");
                }
            }
        }
    }

    boolean bondIfNeeded(final UUID charUuid, final BleDeviceConfig.BondFilter.CharacteristicEventType type)
    {
        final BleDeviceConfig.BondFilter bondFilter = mDevice.getConfig().bondFilter != null ? mDevice.getConfig().bondFilter : getManager().mConfig.bondFilter;

        if( bondFilter == null )  return false;

        final BleDeviceConfig.BondFilter.CharacteristicEvent event = new BleDeviceConfig.BondFilter.CharacteristicEvent(mDevice, charUuid, type);

        final BleDeviceConfig.BondFilter.Please please = bondFilter.onEvent(event);

        return applyPlease_BondFilter(please);
    }

    boolean applyPlease_BondFilter(BleDeviceConfig.BondFilter.Please please_nullable)
    {
        if( please_nullable == null )
        {
            return false;
        }

        if (!Utils.isKitKat())
        {
            return false;
        }

        final Boolean bond = please_nullable.bond_private();

        if( bond == null )
        {
            return false;
        }

        if( bond )
        {
            mDevice.bond(please_nullable.listener());
        }
        else if( !bond )
        {
            mDevice.unBond();
        }

        return bond;
    }

    void setBondListener(BondListener listener)
    {
        mListener = listener;
    }

    void onBond(P_StateTracker.E_Intent intent)
    {
        mDevice.stateTracker().update(intent, BleStatuses.GATT_STATUS_NOT_APPLICABLE, P_GattManager.RESET_TO_BONDED);
        postBondEvent(intent, BleStatuses.BOND_SUCCESS, BondListener.Status.SUCCESS);
    }

    void onBonding(P_StateTracker.E_Intent intent)
    {
        mDevice.stateTracker().update(intent, BleStatuses.GATT_STATUS_NOT_APPLICABLE, P_GattManager.RESET_TO_BONDING);
    }

    void onUnbond(P_StateTracker.E_Intent intent)
    {
        mDevice.stateTracker().update(intent, BleStatuses.GATT_STATUS_NOT_APPLICABLE, P_GattManager.RESET_TO_UNBONDED);
        postBondEvent(intent, BleStatuses.GATT_STATUS_NOT_APPLICABLE, BondListener.Status.SUCCESS);
    }

    void onBondFailed(P_StateTracker.E_Intent intent, int failReason, BondListener.Status status)
    {
        mDevice.stateTracker().update(intent, failReason, P_GattManager.RESET_TO_UNBONDED);
        postBondEvent(intent, failReason, status);
    }

    BondListener.BondEvent postBondEvent(P_StateTracker.E_Intent intent, int failReason, BondListener.Status status)
    {
        final BondListener.BondEvent event = P_EventFactory.newBondEvent(mDevice, status, failReason, intent.convert());
        if (mListener != null)
        {
            mDevice.getManager().mPostManager.postCallback(new Runnable()
            {
                @Override public void run()
                {
                    mListener.onEvent(event);
                }
            });
        }
        if (mDevice.getManager().mDefaultBondListener != null)
        {
            mDevice.getManager().mPostManager.postCallback(new Runnable()
            {
                @Override public void run()
                {
                    mDevice.getManager().mDefaultBondListener.onEvent(event);
                }
            });
        }
        return event;
    }

}
