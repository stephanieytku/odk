package org.odk.collect.android.skyhook;

import java.util.ArrayList;
import java.util.Arrays;

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
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.skyhookwireless.wps.IPLocation;
import com.skyhookwireless.wps.IPLocationCallback;
import com.skyhookwireless.wps.Location;
import com.skyhookwireless.wps.RegistrationCallback;
import com.skyhookwireless.wps.WPSAuthentication;
import com.skyhookwireless.wps.WPSContinuation;
import com.skyhookwireless.wps.WPSLocation;
import com.skyhookwireless.wps.WPSLocationCallback;
import com.skyhookwireless.wps.WPSPeriodicLocationCallback;
import com.skyhookwireless.wps.WPSReturnCode;
import com.skyhookwireless.wps.WPSStreetAddressLookup;
import com.skyhookwireless.wps.XPS;

public class WpsApiTest
    extends Activity
    implements OnSharedPreferenceChangeListener
{
    @Override
    public void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // create the XPS instance, passing in our Context
        _xps = new XPS(this);
        _stop = false;

        // listen for settings changes
        final SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        // read existing preferences
        onSharedPreferenceChanged(preferences, "Local File Path");
        onSharedPreferenceChanged(preferences, "Period");
        onSharedPreferenceChanged(preferences, "Iterations");
        onSharedPreferenceChanged(preferences, "Desired XPS Accuracy");
        onSharedPreferenceChanged(preferences, "Street Address Lookup");
        _username = preferences.getString("Username", "");
        _realm = preferences.getString("Realm", "");
        _tilingPath = preferences.getString("Tiling Path", "");
        _maxDataSizePerSession = Long.valueOf(preferences.getString("Max Data Per Session", "0"));
        _maxDataSizeTotal = Long.valueOf(preferences.getString("Max Data Total", "0"));
        _xps.setTiling(_tilingPath,
                       _maxDataSizePerSession,
                       _maxDataSizeTotal,
                       null);

        // set the UI layout
        _buttonLayout = new LinearLayout(this);        
        _buttonLayout.setOrientation(LinearLayout.VERTICAL);             
        setContentView(_buttonLayout);

        // initialize the Handler which will display location data
        // in the text view. we use a Handler because UI updates
        // must occur in the UI thread
        setUIHandler();

        // display the buttons.
        // _viewsToDisable is a list of views
        // which should be disabled when WPS is active
        _buttons = new LinearLayout(this);
        _buttons.setOrientation(LinearLayout.VERTICAL);
        
        _viewsToDisable.clear();
        _viewsToDisable.add(addSettingsButton(_buttons));
        _viewsToDisable.add(addIPLocationButton(_buttons));
        _viewsToDisable.add(addWPSLocationButton(_buttons));
        _viewsToDisable.add(addWPSPeriodicLocationButton(_buttons));
        _viewsToDisable.add(addXPSLocationButton(_buttons));
        _stopButton = addStopButton(_buttons);
        addAbortButton(_buttons);
        deactivateStopButton();     
        
        // Set the buttons within a ScrollView so that the user can scroll through
        // the buttons if there total length extends into the Location Display
        _scrollingButtons = new ScrollView(this);
        _scrollingButtons.addView(_buttons);      
        
        // Set up the overall layout so that the buttons take up 70 percent of the 
        // available screen and the Location display takes up the remaining 30.
 
        _buttonLayout.addView(_scrollingButtons,
                        	  new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                                                            0, 
                                                            0.7f ));        
        
        // create the location layout
        _tv = new TextView(this);
        _buttonLayout.addView(_tv,
                              new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
    			  		                                    0, 
    			  	                                        0.3f ));
                
        _isRegistering = false;
        _isRegistrationRequired = true;
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

    private LinearLayout _buttonLayout;
    private LinearLayout _buttons;
    private ScrollView _scrollingButtons;
    private final ArrayList<View> _viewsToDisable = new ArrayList<View>();
    private Button _stopButton;
    private boolean _stop;

    // add the 'Settings' button which leads to all the
    // WPS settings.
    private Button addSettingsButton(final ViewGroup layout)
    {
        final Button settingsButton = new Button(this);
        settingsButton.setText("Settings");
        settingsButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                final Intent launchPreferencesIntent =
                    new Intent().setClass(WpsApiTest.this, Preferences.class);
                startActivity(launchPreferencesIntent);
            }
        });        
        layout.addView(settingsButton);
        return settingsButton;
    }

    /**
     * A single callback class that will be used to handle
     * all location notifications sent by WPS to our app.
     */
    private class MyLocationCallback
        implements IPLocationCallback,
                   WPSLocationCallback,
                   WPSPeriodicLocationCallback
    {
        public void done()
        {
            // tell the UI thread to re-enable the buttons
            _handler.sendMessage(_handler.obtainMessage(DONE_MESSAGE));
        }

        public WPSContinuation handleError(final WPSReturnCode error)
        {
            // send a message to display the error
            _handler.sendMessage(_handler.obtainMessage(ERROR_MESSAGE,
                                                        error));
            // return WPS_STOP if the user pressed the Stop button
            if (! _stop)
                return WPSContinuation.WPS_CONTINUE;
            else
                return WPSContinuation.WPS_STOP;
        }

        public void handleIPLocation(final IPLocation location)
        {
            // send a message to display the location
            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
                                                        location));
        }

        public void handleWPSLocation(final WPSLocation location)
        {
            // send a message to display the location
            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
                                                        location));
        }

        public WPSContinuation handleWPSPeriodicLocation(final WPSLocation location)
        {
            _handler.sendMessage(_handler.obtainMessage(LOCATION_MESSAGE,
                                                        location));
            // return WPS_STOP if the user pressed the Stop button
            if (! _stop)
                return WPSContinuation.WPS_CONTINUE;
            else
                return WPSContinuation.WPS_STOP;
        }
    }

    private class MyRegistrationCallback
        implements RegistrationCallback
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

    private final MyLocationCallback _callback = new MyLocationCallback();
    private final MyRegistrationCallback _registrationCallback = new MyRegistrationCallback();

    private Button addIPLocationButton(final ViewGroup layout)
    {
        final Button ipLocationButton = new Button(this);
        ipLocationButton.setText("Get IP Location");

        ipLocationButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                activateStopButton();
                _tv.setText("");
                final WPSAuthentication auth =
                    new WPSAuthentication(_username, _realm);
                _xps.getIPLocation(auth,
                                   _streetAddressLookup,
                                   _callback);
            }
        });
        layout.addView(ipLocationButton);
        return ipLocationButton;
    }

    private Button addWPSPeriodicLocationButton(final ViewGroup layout)
    {
        final Button wpsLocationButton = new Button(this);
        wpsLocationButton.setText("Get WPS Periodic Location");

        wpsLocationButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                activateStopButton();
                _tv.setText("");
                final WPSAuthentication auth =
                    new WPSAuthentication(_username, _realm);
                _xps.getPeriodicLocation(auth,
                                         _streetAddressLookup,
                                         _period,
                                         _iterations,
                                         _callback);
            }
        });
        layout.addView(wpsLocationButton);
        return wpsLocationButton;
    }

    private Button addWPSLocationButton(final ViewGroup layout)
    {
        final Button wpsLocationButton = new Button(this);
        wpsLocationButton.setText("Get WPS Location");

        wpsLocationButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                activateStopButton();
                _tv.setText("");
                final WPSAuthentication auth =
                    new WPSAuthentication(_username, _realm);
                _xps.getLocation(auth,
                                 _streetAddressLookup,
                                 _callback);
            }
        });
        layout.addView(wpsLocationButton);
        return wpsLocationButton;
    }

    private Button addXPSLocationButton(final ViewGroup layout)
    {
        final Button xpsLocationButton = new Button(this);
        xpsLocationButton.setText("Get XPS Location");
        xpsLocationButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                activateStopButton();
                _tv.setText("");
                final WPSAuthentication auth =
                    new WPSAuthentication(_username, _realm);
                _xps.getXPSLocation(auth,
                                    // note we convert _period to seconds
                                    (int) (_period / 1000),
                                    _desiredXpsAccuracy,
                                    _callback);
            }
        });
        layout.addView(xpsLocationButton);
        return xpsLocationButton;
    }

    private Button addStopButton(final ViewGroup layout)
    {
        final Button stopButton = new Button(this);
        stopButton.setText("Stop");
        stopButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                _stop = true;
                stopButton.setEnabled(false);
            }
        });
        layout.addView(stopButton);
        return stopButton;
    }

    private Button addAbortButton(final ViewGroup layout)
    {
        final Button abortButton = new Button(this);
        abortButton.setText("Abort");
        abortButton.setOnClickListener(new OnClickListener()
        {
            public void onClick(final View v)
            {
                _xps.abort();
            }
        });
        layout.addView(abortButton);
        return abortButton;
    }

    private void activateStopButton()
    {
        for (final View view : _viewsToDisable)
        {
            view.setEnabled(false);
        }
        _stopButton.setEnabled(true);
    }

    private void deactivateStopButton()
    {
        for (final View view : _viewsToDisable)
        {
            view.setEnabled(true);
        }
        _stopButton.setEnabled(false);
    }

    // our Handler understands five messages
    private static final int LOCATION_MESSAGE = 1;
    private static final int ERROR_MESSAGE = 2;
    private static final int DONE_MESSAGE = 3;
    private static final int REGISTRATION_SUCCESS_MESSAGE = 4;
    private static final int REGISTRATION_ERROR_MESSAGE = 5;

    private void setUIHandler()
    {
        _handler = new Handler()
        {
            @Override
            public void handleMessage(final Message msg)
            {
                switch (msg.what)
                {
                case LOCATION_MESSAGE:
                    final Location location = (Location) msg.obj;
                    _tv.setText(location.toString());
                    return;
                case ERROR_MESSAGE:
                    _tv.setText(((WPSReturnCode) msg.obj).name());
                    return;
                case DONE_MESSAGE:
                    deactivateStopButton();
                    _stop = false;
                    return;
                case REGISTRATION_SUCCESS_MESSAGE:
                    _tv.setText("Registration succeeded");
                    return;
                case REGISTRATION_ERROR_MESSAGE:
                    _tv.setText("Registration failed ("+((WPSReturnCode) msg.obj).name()+")");
                    return;
                }
            }
        };
    }

    /**
     * Preferences management code
     */
    public static class Preferences
        extends PreferenceActivity
    {
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);

            if (options == null)
            {
                options = new Option[]
                    {
                        new Option("Username"                 , OptionType.TEXT, null),
                        new Option("Realm"                    , OptionType.TEXT, null),
                        new Option(SERVER_URL_KEY             , OptionType.TEXT, null),
                        new Option("Local File Path"          , OptionType.TEXT, null),
                        new Option("Period"                   , OptionType.NONNEGATIVE_INTEGER, null),
                        new Option("Iterations"               , OptionType.NONNEGATIVE_INTEGER, null),
                        new Option("Desired XPS Accuracy"     , OptionType.NONNEGATIVE_INTEGER, null),
                        new Option("Tiling Path"              , OptionType.TEXT, getFilesDir().getAbsolutePath()),
                        new Option("Max Data Per Session"     , OptionType.NONNEGATIVE_INTEGER, null),
                        new Option("Max Data Total"           , OptionType.NONNEGATIVE_INTEGER, null),
                        new ListOption("Street Address Lookup", OptionType.LIST, null, new String[] {"None", "Limited", "Full"})
                    };
            }

            setPreferenceScreen(createRootPreferenceScreen());
        }

        private PreferenceScreen createRootPreferenceScreen()
        {
            final PreferenceScreen root =
                getPreferenceManager().createPreferenceScreen(this);

            final PreferenceCategory category = new PreferenceCategory(this);
            category.setTitle("WpsApiTest Settings");
            root.addPreference(category);

            for (final Option option : options)
            {
                Preference setting = null;
                switch (option.type)
                {
                case CHECKBOX:
                {
                    setting = new CheckBoxPreference(this);
                    break;
                }
                case LIST:
                {
                    if (option instanceof ListOption)
                    {
                        final ListPreference listSetting = new ListPreference(this);
                        final String[] entries = ((ListOption)option).entries;
                        listSetting.setEntries(entries);
                        listSetting.setEntryValues(entries);
                        setting = listSetting;
                        break;
                    }
                }
                default:
                {
                    final EditTextPreference textSetting = new EditTextPreference(this);
                    textSetting.getEditText().setSingleLine();
                    if (option.type == OptionType.NONNEGATIVE_INTEGER)
                        textSetting.getEditText()
                                   .setKeyListener(new DigitsKeyListener(false,
                                                                         false));
                    setting = textSetting;
                }
                }

                if (setting != null)
                {
                    setting.setKey(option.name);
                    setting.setTitle(option.name);
                    if (option.defaultValue != null)
                        setting.setDefaultValue(option.defaultValue);

                    category.addPreference(setting);
                }
            }

            return root;
        }

        private enum OptionType
        {
            TEXT,
            NONNEGATIVE_INTEGER,
            LIST,
            CHECKBOX;
        }

        private class Option
        {
            private Option(final String name, final OptionType type, final Object defaultValue)
            {
                super();
                this.name = name;
                this.type = type;
                this.defaultValue = defaultValue;
            }

            String name;
            OptionType type;
            Object defaultValue;
        }

        private class ListOption extends Option
        {
            private ListOption(final String name, final OptionType type, final Object defaultValue, final String[] entries)
            {
                super(name, type, defaultValue);
                this.entries = entries;
            }

            String[] entries;
        }

        private static Option[] options = null;
    }

    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key)
    {
        if (sharedPreferences.getString(key, "default").equals(""))
        {
            // delete empty preferences so we get the default values below
            final Editor editor = sharedPreferences.edit();
            editor.remove(key);
            editor.commit();
        }

        boolean authChanged = false;
        boolean tilingChanged = false;

        if (key.equals("Username"))
        {
            _username = sharedPreferences.getString(key, "");
            authChanged = true;
        }
        else if (key.equals("Realm"))
        {
            _realm = sharedPreferences.getString(key, "");
            authChanged = true;
        }
        else if (key.equals("Local File Path"))
        {
            _localFilePath = sharedPreferences.getString(key, "");
            // TODO: clean this up?
            ArrayList<String> paths = null;
            if (! _localFilePath.equals(""))
            {
                paths = new ArrayList<String>(Arrays.asList(new String[]{_localFilePath}));
            }
            _xps.setLocalFilePaths(paths);
            return;
        }
        else if (key.equals("Period"))
        {
            _period = Long.valueOf(sharedPreferences.getString(key, "5000"));
            return;
        }
        else if (key.equals("Iterations"))
        {
            _iterations = Integer.valueOf(sharedPreferences.getString(key, "1"));
            return;
        }
        else if (key.equals("Desired XPS Accuracy"))
        {
            _desiredXpsAccuracy = Integer.valueOf(sharedPreferences.getString(key, "30"));
            return;
        }
        else if (key.equals("Tiling Path"))
        {
            _tilingPath = sharedPreferences.getString(key, "");
            tilingChanged = true;
        }
        else if (key.equals("Max Data Per Session"))
        {
            _maxDataSizePerSession = Long.valueOf(sharedPreferences.getString(key, "0"));
            tilingChanged = true;
        }
        else if (key.equals("Max Data Total"))
        {
            _maxDataSizeTotal = Long.valueOf(sharedPreferences.getString(key, "0"));
            tilingChanged = true;
        }
        else if (key.equals("Street Address Lookup"))
        {
            final String setting = sharedPreferences.getString(key, "None");
            if (setting.equals("None"))
            {
                _streetAddressLookup = WPSStreetAddressLookup.WPS_NO_STREET_ADDRESS_LOOKUP;
            }
            else if (setting.equals("Limited"))
            {
                _streetAddressLookup = WPSStreetAddressLookup.WPS_LIMITED_STREET_ADDRESS_LOOKUP;
            }
            else if (setting.equals("Full"))
            {
                _streetAddressLookup = WPSStreetAddressLookup.WPS_FULL_STREET_ADDRESS_LOOKUP;
            }
            return;
        }
        else if (key.equals(SERVER_URL_KEY))
        {
            _serverUrl = sharedPreferences.getString(SERVER_URL_KEY, "");
            XPS.setServerUrl(_serverUrl);
            return;
        }

        if (authChanged)
        {
            _isRegistrationRequired = true;
        }

        if (tilingChanged)
        {
            _xps.setTiling(_tilingPath,
                           _maxDataSizePerSession,
                           _maxDataSizeTotal,
                           null);
        }
    }

    private void registerUser()
    {
        _isRegistrationRequired = false;

        if (! _username.equals("") && ! _realm.equals(""))
        {
            _isRegistering = true;

            _tv.setText("Starting registration");

            // trigger auto-registration
            _xps.registerUser(new WPSAuthentication(_username, _realm),
                              null,
                              _registrationCallback);
        }
        else
        {
            _tv.setText("Can't register: username and/or realm is not set");
        }
    }

    private static final String SERVER_URL_KEY = "Server URL";

    private String _username = null, _realm = null;
    private boolean _isRegistering;
    private boolean _isRegistrationRequired;
    private String _localFilePath = null;
    private long _period;
    private int _iterations;
    private int _desiredXpsAccuracy;
    private String _tilingPath = null;
    private long _maxDataSizePerSession;
    private long _maxDataSizeTotal;
    private String _serverUrl;
    private WPSStreetAddressLookup _streetAddressLookup;
    private XPS _xps;
    private TextView _tv = null;
    private Handler _handler;
}
