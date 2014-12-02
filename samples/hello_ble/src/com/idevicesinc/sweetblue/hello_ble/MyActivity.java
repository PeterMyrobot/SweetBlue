package com.idevicesinc.sweetblue.hello_ble;

import com.idevicesinc.sweetblue.BleDevice;
import com.idevicesinc.sweetblue.BleDeviceState;
import com.idevicesinc.sweetblue.BleManager;
import com.idevicesinc.sweetblue.BleManagerConfig;
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener.Result;
import com.idevicesinc.sweetblue.BleManagerConfig.DefaultAdvertisingFilter;
import com.idevicesinc.sweetblue.utils.Uuids;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * 
 * @author dougkoellmer
 */
public class MyActivity extends Activity
{
	private BleManager m_bleManager;
	
	@Override protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		m_bleManager = new BleManager(getApplication());
		
		m_bleManager.startScan(new BleManager.DiscoveryListener()
		{
			@Override public void onDeviceDiscovered(final BleDevice device)
			{
				m_bleManager.stopScan();
				
				device.connect(new BleDevice.StateListener()
				{
					@Override public void onStateChange(final BleDevice device, int oldStateBits, int newStateBits)
					{
						if( BleDeviceState.INITIALIZED.wasEntered(oldStateBits, newStateBits) )
						{
							Log.i("SweetBlueExample", device.getDebugName() + " just initialized!");
							
							device.read(Uuids.BATTERY_LEVEL, new BleDevice.ReadWriteListener()
							{
								@Override public void onReadOrWriteComplete(Result result)
								{
									if( result.wasSuccess() )
									{
										Log.i("SweetBlueExample", "Battery level is " + result.data[0] + "%");
									}
								}
							});
						}
					}
				});
			}
		});
	}
	
	@Override protected void onResume()
	{
		super.onResume();
		
		m_bleManager.onResume();
	}
	
	@Override protected void onPause()
	{
		super.onPause();
		
		m_bleManager.onPause();
	}

	@Override protected void onDestroy()
	{
		super.onDestroy();
		
		m_bleManager.onDestroy();
	}
}