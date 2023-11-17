/*
  Copyright 2024 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */
package com.adobe.marketing.mobile.campaign;

import androidx.annotation.NonNull;

import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.SharedStateResolution;
import com.adobe.marketing.mobile.SharedStateResult;

import java.util.Map;

public class MonitorExtension extends Extension {
    private static ConfigurationMonitor configurationMonitor = null;

    protected MonitorExtension(ExtensionApi extensionApi) {
        super(extensionApi);
    }

    @NonNull
    @Override
    protected String getName() {
        return "MonitorExtension";
    }

    @Override
    protected void onRegistered() {
        super.onRegistered();
        getApi().registerEventListener(EventType.WILDCARD, EventSource.WILDCARD, event -> {
            final SharedStateResult sharedStateResult = getApi().getSharedState("com.adobe.module.configuration", event, false, SharedStateResolution.LAST_SET);
            if (sharedStateResult != null) {
                final Map<String, Object> configuration = sharedStateResult.getValue();
                if (configurationMonitor != null) {
                    configurationMonitor.call(configuration);
                }
            }
        });
    }

    public static void configurationAwareness(@NonNull final ConfigurationMonitor monitor) {
        configurationMonitor = monitor;
    }
}
