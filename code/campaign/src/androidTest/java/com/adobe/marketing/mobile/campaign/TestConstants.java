/*
  Copyright 2023 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.campaign;

public class TestConstants {
    // Global
    static final String MOCK_CAMPAIGN_SERVER = "campaign.adobe.com";
    static final String MOCK_IDENTITY_SERVER = "identity.adobe.com";
    static final String MOCK_RULES_SERVER = "launch.adobe.com";
    static final String GLOBAL_PRIVACY = "global.privacy";
    static final String GLOBAL_SSL = "global.ssl";
    static final String BUILD_ENVIRONMENT = "build.environment";
    static final String PROPERTY_ID = "property.id";
    // Campaign
    static final String CAMPAIGN_TIMEOUT = "campaign.timeout";
    static final String CAMPAIGN_PKEY = "campaign.pkey";
    static final String CAMPAIGN_SERVER = "campaign.server";
    static final String CAMPAIGN_MCIAS = "campaign.mcias";
    static final String CAMPAIGN_REGISTRATION_DELAY = "campaign.registrationDelay";
    static final String CAMPAIGN_REGISTRATION_PAUSED = "campaign.registrationPaused";
    // Lifecycle
    static final String LIFECYCLE_SESSION_TIMEOUT = "lifecycle.sessionTimeout";
    // Identity
    static final String IDENTITY_ORG_ID = "experienceCloud.org";
    static final String IDENTITY_SERVER = "experienceCloud.server";
    // Rules
    static final String RULES_SERVER = "rules.url";

    public static final class EventType {
        public static final String MONITOR = "com.adobe.functional.eventType.monitor";

        private EventType() {
        }
    }

    public static final class EventSource {
        public static final String XDM_SHARED_STATE_REQUEST = "com.adobe.eventSource.xdmsharedStateRequest";
        public static final String XDM_SHARED_STATE_RESPONSE = "com.adobe.eventSource.xdmsharedStateResponse";
        public static final String SHARED_STATE_REQUEST = "com.adobe.eventSource.sharedStateRequest";
        public static final String SHARED_STATE_RESPONSE = "com.adobe.eventSource.sharedStateResponse";
        public static final String UNREGISTER = "com.adobe.eventSource.unregister";

        private EventSource() {
        }
    }

    public static final class EventDataKey {
        public static final String STATE_OWNER = "stateowner";

        private EventDataKey() {
        }
    }
}
