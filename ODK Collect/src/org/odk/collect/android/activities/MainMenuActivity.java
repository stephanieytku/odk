/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.activities;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.preferences.PreferencesActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.skyhookwireless.wps.*;

/**
 * Responsible for displaying buttons to launch the major activities. Launches some activities based
 * on returns of others.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class MainMenuActivity extends Activity {
    private static final String t = "MainMenuActivity";

    // menu options
    private static final int MENU_PREFERENCES = Menu.FIRST;

    // buttons
    private Button mEnterDataButton;
    private Button mManageFilesButton;
    private Button mSendDataButton;
    private Button mReviewDataButton;
    private Button mGetFormsButton;

    private AlertDialog mAlertDialog;

    private static boolean EXIT = true;


    // private static boolean DO_NOT_EXIT = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // must be at the beginning of any activity that can be called from an external intent
        Log.i(t, "Starting up, creating directories");
        try {
            Collect.createODKDirs();
        } catch (RuntimeException e) {
            createErrorDialog(e.getMessage(), EXIT);
            return;
        }

        setContentView(R.layout.main_menu);

        {
        	// dynamically construct the "ODK Collect vA.B" string
	        TextView mainMenuMessageLabel = (TextView) findViewById(R.id.main_menu_header);
	        mainMenuMessageLabel.setText(Collect.getInstance().getVersionedAppName());
        }
        
        setTitle(getString(R.string.app_name) + " > " + getString(R.string.main_menu));

        // enter data button. expects a result.
        mEnterDataButton = (Button) findViewById(R.id.enter_data);
        mEnterDataButton.setText(getString(R.string.enter_data_button));
        mEnterDataButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
        	    Collect.getInstance().getActivityLogger().logAction(this, "fillBlankForm", "click");
                Intent i = new Intent(getApplicationContext(), FormChooserList.class);
                startActivity(i);
            }
        });

        // review data button. expects a result.
        mReviewDataButton = (Button) findViewById(R.id.review_data);
        mReviewDataButton.setText(getString(R.string.review_data_button));
        mReviewDataButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
        	    Collect.getInstance().getActivityLogger().logAction(this, "editSavedForm", "click");
                Intent i = new Intent(getApplicationContext(), InstanceChooserList.class);
                startActivity(i);
            }
        });

        // send data button. expects a result.
        mSendDataButton = (Button) findViewById(R.id.send_data);
        mSendDataButton.setText(getString(R.string.send_data_button));
        mSendDataButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
        	    Collect.getInstance().getActivityLogger().logAction(this, "uploadForms", "click");
                Intent i = new Intent(getApplicationContext(), InstanceUploaderList.class);
                startActivity(i);
            }
        });

        // manage forms button. no result expected.
        mGetFormsButton = (Button) findViewById(R.id.get_forms);
        mGetFormsButton.setText(getString(R.string.get_forms));
        mGetFormsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
        	    Collect.getInstance().getActivityLogger().logAction(this, "downloadBlankForms", "click");
                Intent i = new Intent(getApplicationContext(), FormDownloadList.class);
                startActivity(i);

            }
        });

        // manage forms button. no result expected.
        mManageFilesButton = (Button) findViewById(R.id.manage_forms);
        mManageFilesButton.setText(getString(R.string.manage_files));
        mManageFilesButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
        	    Collect.getInstance().getActivityLogger().logAction(this, "deleteSavedForms", "click");
                Intent i = new Intent(getApplicationContext(), FileManagerTabs.class);
                startActivity(i);
            }
        });
    //*****SKYHOOK*****
        
        // Create the authentication object
        // myAndroidContext must be a Context instance
        Context myAndroidContext = getApplicationContext();
        XPS _xps = new XPS(myAndroidContext);
        WPSAuthentication auth = new WPSAuthentication("alexmead", "UC Berkeley");

        class MyRegistrationCallback implements RegistrationCallback
        {
        	public void handleSuccess()
        	{
        		// Indicates that registration has been successful
        	}

        	public WPSContinuation handleError(final WPSReturnCode error)
        	{
        		// Indicates that registration has failed, along with the error code.
        		// Return continue to keep trying, stop to give up.
        		return WPSContinuation.WPS_CONTINUE;  
        	}

        	public void done()
        	{
        		// Indicates that registration is completed.  
        		// If you call abort() during registration, done() will be
        		// called without either handle method being called.
        	}
        }
        final MyRegistrationCallback _regCallback = new MyRegistrationCallback();

        _xps.registerUser(auth, null, _regCallback);

        //*****END SKYHOOK*****
        
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
    }
	
    @Override
    protected void onStart() {
    	super.onStart();
		Collect.getInstance().getActivityLogger().logOnStart(this); 
    }
    
    @Override
    protected void onStop() {
		Collect.getInstance().getActivityLogger().logOnStop(this); 
    	super.onStop();
    }
  
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
	    Collect.getInstance().getActivityLogger().logAction(this, "onCreateOptionsMenu", "show");
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_PREFERENCES, 0, getString(R.string.general_preferences)).setIcon(
            android.R.drawable.ic_menu_preferences);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PREFERENCES:
        	    Collect.getInstance().getActivityLogger().logAction(this, "onOptionsItemSelected", "MENU_PREFERENCES");
                Intent ig = new Intent(this, PreferencesActivity.class);
                startActivity(ig);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void createErrorDialog(String errorMsg, final boolean shouldExit) {
	    Collect.getInstance().getActivityLogger().logAction(this, "createErrorDialog", "show");
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertDialog.setMessage(errorMsg);
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1:
                	    Collect.getInstance().getActivityLogger().logAction(this, "createErrorDialog",
                	    		shouldExit ? "exitApplication" : "OK");
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.ok), errorListener);
        mAlertDialog.show();
    }

}
