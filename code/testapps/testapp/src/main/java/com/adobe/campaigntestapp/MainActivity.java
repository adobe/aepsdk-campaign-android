/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.campaigntestapp;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.adobe.marketing.mobile.Campaign;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // feature requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            });

    private void askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
                Log.d(
                        "MainActivity",
                        "Notification permission granted"
                );
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Log.d(
                        "MainActivity",
                        "Notification Permission: Not granted"
                );
            } else {
                // Directly ask for the permission
                Log.d(
                        "MainActivity",
                        "Requesting notification permission"
                );
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            Log.d(
                    "MainActivity",
                    "Notification permission granted"
            );
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MobileCore.setLogLevel(LoggingMode.VERBOSE);
        // setup global lifecycle callback for fullscreen messages
        getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                MobileCore.setApplication(getApplication());
                MobileCore.lifecycleStart(null);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                MobileCore.lifecyclePause();
            }

            // the following methods aren't needed for our lifecycle purposes, but are
            // required to be implemented by the ActivityLifecycleCallbacks object
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });

        // ATTENTION: This was auto-generated to handle app links.
        Intent appLinkIntent = getIntent();
        String appLinkAction = appLinkIntent.getAction();
        Uri appLinkData = appLinkIntent.getData();

        if (appLinkData != null) {
            String activityId = appLinkData.getLastPathSegment();

            switch (activityId) {
                case "settings":
                    Intent intent = new Intent(this, SettingsActivity.class);
                    startActivity(intent);
                    break;

                case "signup":
                    intent = new Intent(this, SignUpActivity.class);
                    startActivity(intent);
                    break;
            }
        }

        // 7 days is the default registration delay
        setRegistrationDelayOrRegistrationPausedStatus(7, false);

        askNotificationPermission();

    }

    @Override
    public void onResume() {
        super.onResume();

        EditText userNameField = findViewById(R.id.editText8);
        EditText passwordField = findViewById(R.id.editText5);

        userNameField.setText("");
        passwordField.setText("");

        handleTracking();
    }

    private void handleTracking() {
        // Check to see if this view was opened based on a notification
        Intent intent = getIntent();
        Bundle data = intent.getExtras();

        if (data != null) {
            // This was opened based on the notification, you need to get the tracking that was passed on.
            String deliveryId = data.getString("_dId");
            String messageId = data.getString("_mId");
            String acsDeliveryTracking = (String) data.get("_acsDeliveryTracking");
        /*
        This is to handle deliveries created before 21.1 release or deliveries with custom template
        where acsDeliveryTracking is not available.
        */
            if (acsDeliveryTracking == null) {
                acsDeliveryTracking = "on";
            }

            Map<String, Object> contextData = new HashMap<>();

            if (deliveryId != null && messageId != null && acsDeliveryTracking.equals("on")) {
                contextData.put("deliveryId", deliveryId);
                contextData.put("broadlogId", messageId);
                contextData.put("action", "2");

                // Send Click Tracking since the user did click on the notification
                MobileCore.collectMessageInfo(contextData);

                // Send Open Tracking since the user opened the app
                contextData.put("action", "1");

                MobileCore.collectMessageInfo(contextData);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }


    // Called when the user click on Continue As a Guest button
    public void onGuestClicked(View view) {
        Intent intent = new Intent(this, TriggerIAMActivity.class);
        startActivity(intent);

    }

    // Called when the user click on Sign Up button
    public void onSignUpClicked(View view) {
        Intent intent = new Intent(this, SignUpActivity.class);
        startActivity(intent);
    }

    // Called when the user click on Sign In button
    public void onSignInClicked(View view) {
        // Get textfield data
        EditText userNameField = findViewById(R.id.editText8);
        EditText passwordField = findViewById(R.id.editText5);

        String enteredUserName = userNameField.getText().toString();
        String enteredPassword = passwordField.getText().toString();

        if ((enteredUserName == null || enteredUserName.isEmpty())
                || (enteredPassword == null || enteredPassword.isEmpty())
        ) {
            AlertHelper.displayErrorAlert("Email or Password fields has not been populated!", this);
            return;
        }


        // Get handle to shared pref
        SharedPreferences pref = getApplicationContext().getSharedPreferences("LinkageFields", 0); // 0 - for private mode
        SharedPreferences.Editor editor = pref.edit();

        // Retrieve kv pairs from shared pref
        String firstName = pref.getString("cusFirstName", null);
        String lastName = pref.getString("cusLastName", null);
        String email = pref.getString("cusEmail", null);

        String username = pref.getString("cusUsername", null);
        String password = pref.getString("cusPassword", null);

        if ((username == null || username.isEmpty()) || (password == null || password.isEmpty())) {
            AlertHelper.displayErrorAlert("You must first create an account. No accounts have been found!", this);
            return;
        }

        // compare username and password
        if (!enteredUserName.equals(username) || !enteredPassword.equals(password)) {
            AlertHelper.displayErrorAlert("Incorrect Email or Password!", this);
            return;
        }

        // collect pii call to update app subscriber table
        Map<String, String> pii = new HashMap<>();
        pii.put("pushPlatform", "gcm");
        pii.put("cusFirstName", firstName);
        pii.put("cusLastName", lastName);
        pii.put("cusEmail", email);
        MobileCore.collectPii(pii);

        // Build linkage fields map
        Map<String, String> linkageFields = new HashMap<>();
        linkageFields.put("triggerKey", "collectPIIIOS");
        linkageFields.put("cusFirstName", firstName);
        linkageFields.put("cusLastName", lastName);
        linkageFields.put("cusEmail", email);
        Campaign.setLinkageFields(linkageFields);

        // set logged in status to true (just in case this is the scenario of a user who logged out but now wants to log back in)
        editor.putBoolean("loggedIn", true);

        editor.commit();

        //Redirect to Trigger IAM screen
        Intent intent = new Intent(this, TriggerIAMActivity.class);
        startActivity(intent);


    }

    // Called when the user click on Settings button
    public void onSettingsClicked(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }


    private void displayErrorAlert(String errorMessage) {


    }

    private void setRegistrationDelayOrRegistrationPausedStatus(final int delay, final boolean registrationPaused) {
        MobileCore.updateConfiguration(new HashMap<String, Object>() {
            {
                put("campaign.registrationDelay", delay);
                put("campaign.registrationPaused", registrationPaused);
            }
        });
    }

}
