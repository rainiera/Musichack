//Copyright (c) Microsoft Corporation All rights reserved.  
// 
//MIT License: 
// 
//Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
//documentation files (the  "Software"), to deal in the Software without restriction, including without limitation
//the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
//to permit persons to whom the Software is furnished to do so, subject to the following conditions: 
// 
//The above copyright notice and this permission notice shall be included in all copies or substantial portions of
//the Software. 
// 
//THE SOFTWARE IS PROVIDED ""AS IS"", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
//TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
//THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
//CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
//IN THE SOFTWARE.
package com.example.musichack;

import java.lang.Override;import java.lang.Runnable;import java.lang.String;import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.microsoft.band.BandException;
import com.microsoft.band.sdksample.R;import com.microsoft.band.sensors.BandAccelerometerEvent;
import com.microsoft.band.sensors.BandContactEvent;
import com.microsoft.band.sensors.BandContactEventListener;
import com.microsoft.band.sensors.BandDistanceEvent;
import com.microsoft.band.sensors.BandGyroscopeEvent;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.BandPedometerEvent;
import com.microsoft.band.sensors.BandSensorManager;
import com.microsoft.band.sensors.BandSkinTemperatureEvent;
import com.microsoft.band.sensors.BandUVEvent;

import android.support.v4.app.Fragment;
import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TextView;

public class SensorsFragment extends Fragment implements FragmentListener {

    // HR sensor controls
    private Switch mSwitchHeartRate;
    private TableLayout mTableHeartRate;
    private TextView mTextHeartRate;
    private TextView mTextHeartRateQuality;

    // Contact sensor controls
    private Switch mSwitchContact;
    private TableLayout mTableContact;
    private TextView mTextContact;

    // Each sensor switch has an associated TableLayout containing it's display controls.
    // The TableLayout remains hidden until the corresponding sensor switch is turned on.
    private HashMap<Switch, TableLayout> mSensorMap;

    //
    // For managing communication between the incoming sensor events and the UI thread
    //
    private volatile boolean mIsHandlerScheduled;
    private AtomicReference<BandAccelerometerEvent> mPendingAccelerometerEvent = new AtomicReference<BandAccelerometerEvent>();
    private AtomicReference<BandGyroscopeEvent> mPendingGyroscopeEvent = new AtomicReference<BandGyroscopeEvent>();
    private AtomicReference<BandDistanceEvent> mPendingDistanceEvent = new AtomicReference<BandDistanceEvent>();
    private AtomicReference<BandHeartRateEvent> mPendingHeartRateEvent = new AtomicReference<BandHeartRateEvent>();
    private AtomicReference<BandContactEvent> mPendingContactEvent = new AtomicReference<BandContactEvent>();
    private AtomicReference<BandSkinTemperatureEvent> mPendingSkinTemperatureEvent = new AtomicReference<BandSkinTemperatureEvent>();
    private AtomicReference<BandUVEvent> mPendingUVEvent = new AtomicReference<BandUVEvent>();
    private AtomicReference<BandPedometerEvent> mPendingPedometerEvent = new AtomicReference<BandPedometerEvent>();

    public SensorsFragment() {
    }

    public void onFragmentSelected() {
        if (isVisible()) {
            refreshControls();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_sensors, container, false);

        mSensorMap = new HashMap<Switch, TableLayout>();

        //
        // Heart rate setup
        //
        mSwitchHeartRate = (Switch)rootView.findViewById(R.id.switchHeartRate);
        mTableHeartRate = (TableLayout)rootView.findViewById(R.id.tableHeartRate);
        mSensorMap.put(mSwitchHeartRate, mTableHeartRate);
        mTableHeartRate.setVisibility(View.GONE);
        mSwitchHeartRate.setOnCheckedChangeListener(mToggleSensorSection);

        mTextHeartRate = (TextView)rootView.findViewById(R.id.textHeartRate);
        mTextHeartRateQuality = (TextView)rootView.findViewById(R.id.textHeartRateQuality);

        //
        // Contact setup
        //
        mSwitchContact = (Switch)rootView.findViewById(R.id.switchContact);
        mTableContact = (TableLayout)rootView.findViewById(R.id.tableContact);
        mSensorMap.put(mSwitchContact, mTableContact);
        mTableContact.setVisibility(View.GONE);
        mSwitchContact.setOnCheckedChangeListener(mToggleSensorSection);

        mTextContact = (TextView)rootView.findViewById(R.id.textContact);


        return rootView;
    }

    //
    // When pausing, turn off any active sensors.
    //
    @Override
    public void onPause() {
        for (Switch sw : mSensorMap.keySet()) {
            if (sw.isChecked()) {
                sw.setChecked(false);
                mToggleSensorSection.onCheckedChanged(sw, false);
            }
        }

        super.onPause();
    }

    private OnCheckedChangeListener mToggleSensorSection = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!Model.getInstance().isConnected()) {
                return;
            }

            Switch sw = (Switch)buttonView;
            TableLayout table = mSensorMap.get(sw);
            BandSensorManager sensorMgr = Model.getInstance().getClient().getSensorManager();

            if (isChecked) {
                table.setVisibility(View.VISIBLE);

                // Turn on the appropriate sensor

                try {
                    if (sw == mSwitchHeartRate) {
                        mTextHeartRate.setText("");
                        mTextHeartRateQuality.setText("");
                        sensorMgr.registerHeartRateEventListener(mHeartRateEventListener);
                    } else {
                        mTextContact.setText("");
                        sensorMgr.registerContactEventListener(mContactEventListener);
                    }
                } catch (BandException ex) {
                    Util.showExceptionAlert(getActivity(), "Register sensor listener", ex);
                }
            } else {
                table.setVisibility(View.GONE);

                // Turn off the appropriate sensor

                try {
                    if (sw == mSwitchHeartRate) {
                        sensorMgr.unregisterHeartRateEventListener(mHeartRateEventListener);
                    } else if (sw == mSwitchContact) {
                        sensorMgr.unregisterContactEventListener(mContactEventListener);
                    }
                } catch (BandException ex) {
                    Util.showExceptionAlert(getActivity(), "Unregister sensor listener", ex);
                }
            }
        }
    };

    //
    // This method is scheduled to run on the UI thread after a sensor event has been received.
    // We clear our "is scheduled" flag and then update the UI controls for any new sensor
    // events (which we also clear).
    //
    private void handlePendingSensorReports() {
        // Because we clear this flag before reading the sensor events, it's possible that a
        // newly-generated event will schedule the handler to run unnecessarily. This is
        // harmless. If we update the flag after checking the sensors, we could fail to call
        // the handler at all.
        mIsHandlerScheduled = false;

        BandHeartRateEvent heartRateEvent = mPendingHeartRateEvent.getAndSet(null);
        if (heartRateEvent != null) {
            mTextHeartRate.setText(String.valueOf(heartRateEvent.getHeartRate()));
            mTextHeartRateQuality.setText(heartRateEvent.getQuality().toString());
        }

        BandContactEvent contactEvent = mPendingContactEvent.getAndSet(null);
        if (contactEvent != null) {
            mTextContact.setText(contactEvent.getContactStatus().toString());
        }

    }

    //
    // Queue an action to run on the UI thread to process sensor updates. Make sure
    // that we have at most one callback queued for the UI thread.
    //
    private synchronized void scheduleSensorHandler() {
        if (mIsHandlerScheduled) {
            return;
        }

        Activity activity = getActivity();

        if (activity != null) {
            mIsHandlerScheduled = true;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handlePendingSensorReports();
                }
            });
        }
    }

    //
    // Sensor event handlers - each handler just writes the new sample to an atomic
    // reference where it will be read by the UI thread. Samples that arrive faster
    // than they can be processed by the UI thread overwrite older samples. Each
    // handler calls scheduleSensorHandler() which makes sure that at most one call
    // is queued to the UI thread to update all of the sensor displays.
    //


    private BandHeartRateEventListener mHeartRateEventListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(final BandHeartRateEvent event) {
            mPendingHeartRateEvent.set(event);
            scheduleSensorHandler();
        }
    };

    private BandContactEventListener mContactEventListener = new BandContactEventListener() {
        @Override
        public void onBandContactChanged(final BandContactEvent event) {
            mPendingContactEvent.set(event);
            scheduleSensorHandler();
        }
    };

    //
    // Other helpers
    //

    private static void setChildrenEnabled(RadioGroup radioGroup, boolean enabled) {
        for (int i = radioGroup.getChildCount() - 1; i >= 0; i--) {
            radioGroup.getChildAt(i).setEnabled(enabled);
        }
    }

    private void refreshControls() {
        boolean connected = Model.getInstance().isConnected();

        for (Switch sw : mSensorMap.keySet()) {
            sw.setEnabled(connected);
            if (!connected) {
                sw.setChecked(false);
                mToggleSensorSection.onCheckedChanged(sw, false);
            }
        }
    }
}
