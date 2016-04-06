package com.idevicesinc.sweetblue;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;

import com.idevicesinc.sweetblue.compat.K_Util;
import com.idevicesinc.sweetblue.utils.Interval;
import com.idevicesinc.sweetblue.utils.Utils;
import com.idevicesinc.sweetblue.utils.Utils_Reflection;

import java.lang.reflect.Method;

class P_Task_Bond extends PA_Task_RequiresBleOn
{
    private static final String METHOD_NAME__CREATE_BOND = "createBond";
    private static final String METHOD_NAME__CANCEL_BOND_PROCESS	= "cancelBondProcess";

    //--- DRK > Originally used because for tab 4 (and any other bonding failure during connection) we'd force disconnect from the connection failing
    //---		and then put another bond task on the queue, but because we hadn't actually yet killed the transaction lock, the bond task would
    //---		cut the unbond task in the queue. Not adding bonding task in the disconnect flow now though so this is probably useless for future use.
    static enum E_TransactionLockBehavior
    {
        PASSES,
        DOES_NOT_PASS;
    }

    private final PE_TaskPriority m_priority;
    private final boolean m_explicit;
    private final boolean m_partOfConnection;
    private final boolean m_isRequest;
    private final E_TransactionLockBehavior m_lockBehavior;
    private long m_lastBondAttempt;
    private int m_currentBondAttempts;

    private int m_failReason = BleStatuses.BOND_FAIL_REASON_NOT_APPLICABLE;

    public P_Task_Bond(BleDevice device, boolean explicit, boolean partOfConnection, I_StateListener listener, PE_TaskPriority priority, E_TransactionLockBehavior lockBehavior, boolean isRequest)
    {
        super(device, listener);

        m_currentBondAttempts = 0;
        m_isRequest = isRequest;
        m_priority = priority == null ? PE_TaskPriority.FOR_EXPLICIT_BONDING_AND_CONNECTING : priority;
        m_explicit = explicit;
        m_partOfConnection = partOfConnection;
        m_lockBehavior = lockBehavior;
    }

    public P_Task_Bond(BleDevice device, boolean explicit, boolean partOfConnection, I_StateListener listener, E_TransactionLockBehavior lockBehavior, boolean isRequest)
    {
        this(device, explicit, partOfConnection, listener, null, lockBehavior, isRequest);
    }

    @Override public boolean isExplicit()
    {
        return m_explicit;
    }

    @SuppressLint("NewApi")
    @Override public void execute()
    {
        //--- DRK > Commenting out this block for now because Android can lie and tell us we're bonded when we're actually not,
        //---		so therefore we always try to force a bond regardless. Not sure if that actually forces
        //---		Android to "come clean" about its actual bond status or not, but worth a try.
        //---		UPDATE: No, it doesn't appear this works...Android lies even to itself, so commenting this back in.
        if (getDevice().m_nativeWrapper.isNativelyBonded())
        {
            getLogger().w("Already bonded!");

            succeed();
        }
        else
        {
            if (getDevice().m_nativeWrapper./*already*/isNativelyBonding())
            {
                // If the timeout period has been surpassed, then cancel the bond process, and we'll try
                // to bond again, if we haven't surpassed the retry count
                if (m_isRequest && System.currentTimeMillis() > (m_lastBondAttempt + getManager().m_config.requestBondTimeout.millis()))
                {
                    cancelBondProcess();
                    // Re-arm the task, to get it to execute again
                    arm();
                }
            }
            else if (false == m_explicit)
            {
                // DRK > Fail cause we're not natively bonding and this task was implicit, meaning we should be implicitly bonding.
                fail();
            }
            else {
                final boolean bondFailed = createBond();

                m_lastBondAttempt = System.currentTimeMillis();

                if (bondFailed)
                {
                    m_currentBondAttempts++;
                    if (!m_isRequest || m_currentBondAttempts >= getManager().m_config.requestBondRetryCount)
                    {
                        failImmediately();

                        getLogger().w("Bond failed immediately.");
                    }
                }
            }
        }
    }

    private boolean cancelBondProcess()
    {
        return Utils_Reflection.callBooleanReturnMethod(getDevice().getNative(), METHOD_NAME__CANCEL_BOND_PROCESS, getManager().m_config.loggingEnabled);
    }

    boolean isRequest()
    {
        return m_isRequest;
    }

    private boolean createBond()
    {
        final boolean useLeTransportForBonding = BleDeviceConfig.bool(getDevice().conf_device().useLeTransportForBonding, getDevice().conf_mngr().useLeTransportForBonding);

        if (useLeTransportForBonding)
        {
            final boolean theSneakyWayWorked = createBond_theSneakyWay();

            if (theSneakyWayWorked == false)
            {
                return createBond_theNormalWay();
            }
            else
            {
                return true;
            }
        }
        else
        {
            return createBond_theNormalWay();
        }
    }

    private boolean createBond_theNormalWay()
    {
        if (Utils.isKitKat())
        {
            return K_Util.createBond(getDevice());
        }
        else
        {
            m_failReason = BleStatuses.BOND_FAIL_REASON_NOT_AVAILABLE;
            return false;
        }
    }

    private boolean createBond_theSneakyWay()
    {
        if (Utils.isKitKat())
        {
            final Class[] paramTypes = new Class[]{int.class};

            return Utils_Reflection.callBooleanReturnMethod(getDevice().getNative(), METHOD_NAME__CREATE_BOND, paramTypes, getManager().m_config.loggingEnabled, BluetoothDevice.TRANSPORT_LE);
        }
        else
        {
            return false;
        }
    }

    @Override public boolean isMoreImportantThan(PA_Task task)
    {
        if (task instanceof P_Task_TxnLock)
        {
            if (m_lockBehavior == E_TransactionLockBehavior.PASSES)
            {
                P_Task_TxnLock task_cast = (P_Task_TxnLock) task;

                if (this.getDevice() == task_cast.getDevice())
                {
                    return true;
                }
            }
        }

        return super.isMoreImportantThan(task);
    }

    public void onNativeFail(int failReason)
    {
        m_failReason = failReason;

        m_currentBondAttempts++;

        if (!m_isRequest || m_currentBondAttempts >= getManager().m_config.requestBondRetryCount)
        {
            fail();
        }
        else
        {
            getLogger().w(String.format("Bonding failed with fail code %d. Will retry bond...", m_failReason));

            // Re-arm, so this task will try to execute again
            arm();
        }

    }

    public int getFailReason()
    {
        return m_failReason;
    }

    @Override public PE_TaskPriority getPriority()
    {
        return m_priority;
    }

    @Override protected boolean isSoftlyCancellableBy(PA_Task task)
    {
        if (this.getDevice().equals(task.getDevice()))
        {
            if (task.getClass() == P_Task_Disconnect.class)
            {
                if (this.m_partOfConnection && this.getState() == PE_TaskState.EXECUTING)
                {
                    return true;
                }
            }
            else if (task.getClass() == P_Task_Unbond.class)
            {
                return true;
            }
        }

        return super.isSoftlyCancellableBy(task);
    }

    @Override protected BleTask getTaskType()
    {
        if (m_isRequest)
        {
            return BleTask.REQUEST_BOND;
        }
        else
        {
            return BleTask.BOND;
        }
    }

    @Override boolean performTimeoutCheck()
    {
        if( !Interval.isDisabled(getTimeout()) && getTimeout() != Interval.INFINITE.secs() )
        {
            final double timeExecuting = (System.currentTimeMillis() - getresetableExecuteStartTime())/1000.0;

            final double timeout = m_isRequest ? getTimeout() * getManager().m_config.requestBondRetryCount : getTimeout();

            if( timeExecuting >= timeout )
            {
                timeout();
                return true;
            }
        }
        return false;
    }
}
