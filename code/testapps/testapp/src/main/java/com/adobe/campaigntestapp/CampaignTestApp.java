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
import com.adobe.marketing.mobile.Assurance;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;

import java.util.Arrays;

public class CampaignTestApp extends Application {

    private static final String LOG_TAG = "CampaignTestApp";

    @Override
    public void onCreate() {
        super.onCreate();
        MobileCore.setApplication(this);
        MobileCore.setLogLevel(LoggingMode.DEBUG);

        MobileCore.registerExtensions(Arrays.asList(Campaign.EXTENSION, Lifecycle.EXTENSION, Identity.EXTENSION, Signal.EXTENSION, UserProfile.EXTENSION, Assurance.EXTENSION), o -> {
            MobileCore.configureWithAppID("31d8b0ad1f9f/98da4ef07438/launch-b7548c1d44a2-development");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (!task.isSuccessful()) {
                    Log.w(LOG_TAG, "getInstanceId failed", task.getException());
                    return;
                }

                // Get new FCM registration token
                String token = task.getResult();

                // Log and toast
                System.out.println("CampaignTestApp token: " + token);
                MobileCore.setPushIdentifier(token);
            }
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
