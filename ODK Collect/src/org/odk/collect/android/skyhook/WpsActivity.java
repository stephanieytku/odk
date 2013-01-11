package org.odk.collect.android.skyhook;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONException;
import org.json.JSONObject;
import org.odk.collect.android.utilities.NetUtils;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.skyhookwireless.wps.*;

public class WpsActivity
    extends Activity
{

    private static final String SERVER_URL_KEY = "Server URL";

    private String _username = "alexmead", _realm = "UC Berkeley";
    private boolean _isRegistering;
    private boolean _isRegistrationRequired;
    private String _localFilePath = null;
    private long _period=5000;
    private int _iterations=1;
    private int _desiredXpsAccuracy=30;
    private String _tilingPath = null;
    private long _maxDataSizePerSession=0;
    private long _maxDataSizeTotal=0;
    private String _serverUrl;
    private WPSStreetAddressLookup _streetAddressLookup;
    private XPS _xps;
    private TextView _tv = null;
    private Handler _handler;
    private Callback mCallback;
    private WPSAuthentication auth;
    private int TIME_INTERVAL = 1800; //30 minutes in seconds
    
    @Override
    public void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.d("SKYHOOK","ONCREATE");
        // create the XPS instance, passing in our Context
        _xps = new XPS(this);
        //_stop = false;
        mCallback = new Callback();
        auth = new WPSAuthentication(_username, _realm);
  
        // initialize the Handler which will display location data
        // in the text view. we use a Handler because UI updates
        // must occur in the UI thread
        //setUIHandler();
        
        //Start location callbacks
        startXPSCallbacks();

        _isRegistering = false;
        _isRegistrationRequired = true;
    }
    
    public void startXPSCallbacks(){
    	_xps.getXPSLocation(auth, (int) TIME_INTERVAL, _desiredXpsAccuracy, mCallback);
    }
    
    @Override
    protected void onResume()
    {
        super.onResume();
        if (_isRegistrationRequired)
            registerUser();
    }

    @Override
    public void onPause()
    {
        super.onPause();

        if (_isRegistering) // xps stopped during registration
            _isRegistrationRequired = true;

        // make sure WPS is stopped
        _xps.abort();
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
    }

    private class Callback implements IPLocationCallback, WPSLocationCallback, WPSPeriodicLocationCallback {
    	   private static final String TAG = "WPSCallback";

		@Override
    	   public void handleIPLocation(IPLocation arg0) {}
    	 
    	   @Override
    	   public WPSContinuation handleWPSPeriodicLocation(WPSLocation location) {
    	      if (location != null) {
    	         Log.d(TAG, "Location from skyhook " + location.toString());
    	         uploadLocation(location);
    	      } else { 
    	    	  //if (mActiveProviders == 1) {
    	         //Log.d(TAG, "WPS loc is null, starting a provider");
    	         //startAProvider();
    	    	  Log.d(TAG, "Location is null");
    	      }
    	   return WPSContinuation.WPS_CONTINUE;
    	   }   

    	   @Override
    	   public void done() {
    	      Log.d(TAG, "done");
    	      new Thread() {
    	         public void run() {
    	            startXPSCallbacks();
    	         }
    	      }.start();
    	   }
    	 
    	   @Override
    	   public WPSContinuation handleError(WPSReturnCode arg0) {      
    	      Log.d(TAG, "WPS ERROR " + arg0.toString());
    	      
    	      return WPSContinuation.WPS_CONTINUE;
    	   }

    	   @Override
    	   public void handleWPSLocation(WPSLocation arg0) {}
    	   
    	   //Upload Location to Server
    	   public void uploadLocation(WPSLocation location){
    		   HashMap<String,String> params = new HashMap();
    		   TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
			    String username = NetUtils.getStringPreference(WpsActivity.this, NetUtils.DOMAIN_STATIC,
	                    "username", "anonymous");
	            String secret_key = NetUtils.getStringPreference(WpsActivity.this, NetUtils.DOMAIN_STATIC,
	                    "secret_key", "anonymous");
	            try {
					secret_key = NetUtils.getMD5(secret_key + username);
				} catch (NoSuchAlgorithmException e1) {
					e1.printStackTrace();
				}
               params.put("username", username);
               params.put("identifier", tm.getDeviceId());
               params.put("secret_key", secret_key);
               try {
					String responseText = NetUtils.httpPostData(NetUtils.SurveyEndpoint, params, WpsActivity.this);
					JSONObject response = new JSONObject(responseText);
					if( response.getInt("code") == 1) {
						NetUtils.setStringPreference(WpsActivity.this, NetUtils.DOMAIN_STATIC,"has_surveyed", "true");
					}
				} catch (ClientProtocolException e) {
					Log.e(TAG,e.toString());
				} catch (SocketException e) {
					Log.e(TAG,e.toString());
				} catch (UnknownHostException e) {
					Log.e(TAG,e.toString());
				} catch (IOException e) {
					Log.e(TAG,e.toString());
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				finish();
			}
	

    	   }
    	
//    /**
//     * A single callback class that will be used to handle
//     * all location notifications sent by WPS to our app.
//     */
//    private class MyLocationCallback implements IPLocationCallback,  WPSLocationCallback, WPSPeriodicLocationCallback
//    {
//        public void done()
//        {
//            // tell the UI thread to re-enable the buttons
//            _handler.sendMessage(_handler.obtainMessage(DONE_MESSAGE));
//        }
//
//        public WPSContinuation handleError(final WPSReturnCode error)
//        {
//            // send a message to display the error
//            _handler.sendMessage(_handler.obtainMessage(ERROR_MESSAGE,
//                                                        error));
//            // return WPS_STOP if the user pressed the Stop button
//            if (! _stop)
//                return WPSContinuation.WPS_CONTINUE;
//            else
//                return WPSContinuation.WPS_STOP;
//        }
//
//        public void handleIPLocation(final IPLocation location)
//        {
//            // send a message to display the location
//            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
//                                                        location));
//        }
//
//        public void handleWPSLocation(final WPSLocation location)
//        {
//            // send a message to display the location
//            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
//                                                        location));
//        }
//
//        public WPSContinuation handleWPSPeriodicLocation(final WPSLocation location)
//        {
//            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
//                                                        location));
//            // return WPS_STOP if the user pressed the Stop button
//            if (! _stop)
//                return WPSContinuation.WPS_CONTINUE;
//            else
//                return WPSContinuation.WPS_STOP;
//        }
//    }

    private class MyRegistrationCallback implements RegistrationCallback
    {
        public void done()
        {
            _isRegistering = false;
        }

        public void handleSuccess()
        {
            // send a message to display registration success
            _handler.sendMessage(_handler.obtainMessage(REGISTRATION_SUCCESS_MESSAGE));
        }

        public WPSContinuation handleError(final WPSReturnCode error)
        {
            // send a message to display the error
            _handler.sendMessage(_handler.obtainMessage(REGISTRATION_ERROR_MESSAGE,
                                                        error));
            return WPSContinuation.WPS_CONTINUE;
        }
    }

  //  private final MyLocationCallback _callback = new MyLocationCallback();
    private final MyRegistrationCallback _registrationCallback = new MyRegistrationCallback();
//
//    private Button addIPLocationButton(final ViewGroup layout)
//    {
//        final Button ipLocationButton = new Button(this);
//        ipLocationButton.setText("Get IP Location");
//
//        ipLocationButton.setOnClickListener(new OnClickListener()
//        {
//            public void onClick(final View v)
//            {
//                activateStopButton();
//                _tv.setText("");
//                final WPSAuthentication auth =
//                    new WPSAuthentication(_username, _realm);
//                _xps.getIPLocation(auth,
//                                   _streetAddressLookup,
//                                   _callback);
//            }
//        });
//        layout.addView(ipLocationButton);
//        return ipLocationButton;
//    }
//
//    private Button addWPSPeriodicLocationButton(final ViewGroup layout)
//    {
//        final Button wpsLocationButton = new Button(this);
//        wpsLocationButton.setText("Get WPS Periodic Location");
//
//        wpsLocationButton.setOnClickListener(new OnClickListener()
//        {
//            public void onClick(final View v)
//            {
//                activateStopButton();
//                _tv.setText("");
//                final WPSAuthentication auth =
//                    new WPSAuthentication(_username, _realm);
//                _xps.getPeriodicLocation(auth,
//                                         _streetAddressLookup,
//                                         _period,
//                                         _iterations,
//                                         _callback);
//            }
//        });
//        layout.addView(wpsLocationButton);
//        return wpsLocationButton;
//    }
//
//    private Button addWPSLocationButton(final ViewGroup layout)
//    {
//        final Button wpsLocationButton = new Button(this);
//        wpsLocationButton.setText("Get WPS Location");
//
//        wpsLocationButton.setOnClickListener(new OnClickListener()
//        {
//            public void onClick(final View v)
//            {
//                activateStopButton();
//                _tv.setText("");
//                final WPSAuthentication auth =
//                    new WPSAuthentication(_username, _realm);
//                _xps.getLocation(auth,
//                                 _streetAddressLookup,
//                                 _callback);
//            }
//        });
//        layout.addView(wpsLocationButton);
//        return wpsLocationButton;
//    }
//
//    private Button addXPSLocationButton(final ViewGroup layout)
//    {
//        final Button xpsLocationButton = new Button(this);
//        xpsLocationButton.setText("Get XPS Location");
//        xpsLocationButton.setOnClickListener(new OnClickListener()
//        {
//            public void onClick(final View v)
//            {
//                activateStopButton();
//                _tv.setText("");
//                final WPSAuthentication auth =
//                    new WPSAuthentication(_username, _realm);
//                _xps.getXPSLocation(auth,
//                                    // note we convert _period to seconds
//                                    (int) (_period / 1000),
//                                    _desiredXpsAccuracy,
//                                    _callback);
//            }
//        });
//        layout.addView(xpsLocationButton);
//        return xpsLocationButton;
//    }


    // our Handler understands five messages
    private static final int LOCATION_MESSAGE = 1;
    private static final int ERROR_MESSAGE = 2;
    private static final int DONE_MESSAGE = 3;
    private static final int REGISTRATION_SUCCESS_MESSAGE = 4;
    private static final int REGISTRATION_ERROR_MESSAGE = 5;

//    private void setUIHandler()
//    {
//        _handler = new Handler()
//        {
//            @Override
//            public void handleMessage(final Message msg)
//            {
//                switch (msg.what)
//                {
//                case LOCATION_MESSAGE:
//                    final Location location = (Location) msg.obj;
//                    _tv.setText(location.toString());
//                    return;
//                case ERROR_MESSAGE:
//                    _tv.setText(((WPSReturnCode) msg.obj).name());
//                    return;
//                case DONE_MESSAGE:
//                    _stop = false;
//                    return;
//                case REGISTRATION_SUCCESS_MESSAGE:
//                    _tv.setText("Registration succeeded");
//                    return;
//                case REGISTRATION_ERROR_MESSAGE:
//                    _tv.setText("Registration failed ("+((WPSReturnCode) msg.obj).name()+")");
//                    return;
//                }
//            }
//        };
//    }


    private void registerUser()
    {
        _isRegistrationRequired = false;

        if (! _username.equals("") && ! _realm.equals(""))
        {
            _isRegistering = true;

            Log.d("WPSActivity","Starting registration");

            // trigger auto-registration
            _xps.registerUser(auth,
                              null,
                              _registrationCallback);
        }
        else
        {
        	Log.d("WPSActivity","Can't register: username and/or realm is not set");
        }
    }

}
