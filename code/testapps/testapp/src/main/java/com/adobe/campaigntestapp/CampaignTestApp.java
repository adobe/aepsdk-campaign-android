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

import com.adobe.marketing.mobile.Identity;
import com.adobe.marketing.mobile.Lifecycle;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.Campaign;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.Signal;
import com.adobe.marketing.mobile.UserProfile;
import com.google.firebase.iid.FirebaseInstanceId;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.util.Arrays;

public class CampaignTestApp extends Application {

    private static final String LOG_TAG = "CampaignTestApp";

    private static Application application;

    @Override
    public void onCreate() {
        super.onCreate();
        MobileCore.setApplication(this);
        MobileCore.setLogLevel(LoggingMode.DEBUG);

        MobileCore.registerExtensions(Arrays.asList(Campaign.EXTENSION, Lifecycle.EXTENSION, Identity.EXTENSION, Signal.EXTENSION, UserProfile.EXTENSION), o -> {
            MobileCore.configureWithAppID("31d8b0ad1f9f/98da4ef07438/launch-b7548c1d44a2-development");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        application = this;
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("CampaignTestApp", "getInstanceId failed", task.getException());
                        return;
                    }

                    // Get new Instance ID token
                    String token = task.getResult().getToken();

                    // Log and toast
                    System.out.println("CampaignTestApp token: " + token);


                    MobileCore.setPushIdentifier(token);
                });
        // compare to latest versions at https://central.sonatype.com/namespace/com.adobe.marketing.mobile
        Log.d("Core version ", MobileCore.extensionVersion());
        Log.d("Campaign version ", Campaign.extensionVersion());
        Log.d("UserProfile version ", UserProfile.extensionVersion());
        Log.d("Identity version ", Identity.extensionVersion());
        Log.d("Lifecycle version ", Lifecycle.extensionVersion());
        Log.d("Signal version ", Signal.extensionVersion());
    }
}
