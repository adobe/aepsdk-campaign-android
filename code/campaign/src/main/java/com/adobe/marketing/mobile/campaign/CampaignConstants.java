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

package com.adobe.marketing.mobile.campaign;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Contains {@code static} constants used by the classes within the Campaign extension.
 * <p>
 * This class is not instantiable.
 */
final class CampaignConstants {

    static final String LOG_TAG = "Campaign";
    static final String EXTENSION_NAME = "com.adobe.module.campaign";
    static final String RULE_ENGINE_NAME = EXTENSION_NAME + ".rulesengine";
    static final String FRIENDLY_NAME = "Campaign";
    static final String DEPRECATED_1X_HIT_DATABASE_FILENAME = "ADBMobileCampaign.sqlite";

    static final String CAMPAIGN_NAMED_COLLECTION_NAME = "CampaignCollection";
    static final String CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY = "CampaignRemoteUrl";
    static final String CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY = "ExperienceCloudId";
    static final String CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY = "CampaignRegistrationTimestamp";

    // acp campaign datastore file
    static final String ACP_CAMPAIGN_DATASTORE_NAME = "CampaignDataStore";

    static final String AEPSDK_CACHE_BASE_DIR = "aepsdkcache";
    static final String CACHE_BASE_DIR = "campaign";
    static final String MESSAGE_CACHE_DIR = "messages";
    static final String ZIP_HANDLE = "campaign_rules.zip";

    static final String MESSAGE_SCHEME = "adbinapp";
    static final String MESSAGE_SCHEME_PATH_CANCEL = "cancel";
    static final String MESSAGE_SCHEME_PATH_CONFIRM = "confirm";

    static final int MESSAGE_DATA_ID_TOKENS_LEN = 3;
    static final String MESSAGE_DATA_TAG_ID = "id";
    static final String MESSAGE_DATA_TAG_ID_DELIMITER = ",";
    static final int MESSAGE_DATA_TAG_ID_BUTTON_1 = 3;
    static final int MESSAGE_DATA_TAG_ID_BUTTON_2 = 4;
    static final int MESSAGE_DATA_TAG_ID_BUTTON_X = 5;

    static final String CAMPAIGN_INTERACTION_URL = "url";
    static final String CAMPAIGN_INTERACTION_TYPE = "type";

    static final long DEFAULT_TIMESTAMP_VALUE = -1;
    static final int DEFAULT_REGISTRATION_DELAY_DAYS = 7;

    // message consequence "type" value
    static final String MESSAGE_CONSEQUENCE_MESSAGE_TYPE = "iam";

    // message consequence "template" values
    static final String MESSAGE_TEMPLATE_ALERT = "alert";
    static final String MESSAGE_TEMPLATE_FULLSCREEN = "fullscreen";
    static final String MESSAGE_TEMPLATE_LOCAL_NOTIFICATION = "local";

    static final String RULES_CACHE_FOLDER = "campaignRules";
    static final String RULES_JSON_FILE_NAME = "rules.json";

    static final String CAMPAIGN_REGISTRATION_URL = "https://%s/rest/head/mobileAppV5/%s/subscriptions/%s";
    static final String CAMPAIGN_RULES_DOWNLOAD_URL = "https://%s/%s/%s/%s/rules.zip";
    static final String CAMPAIGN_TRACKING_URL = "https://%s/r/?id=%s,%s,%s&mcId=%s";
    static final int CAMPAIGN_TIMEOUT_DEFAULT = 5;

    static final String CAMPAIGN_PUSH_PLATFORM = "pushPlatform";
    static final String EXPERIENCE_CLOUD_ID = "marketingCloudId";

    static final String MESSAGE_TOKEN_MESSAGE_ID = "messageId";

    static final int DEFAULT_LOCAL_NOTIFICATION_DELAY_SECONDS = 0;
    static final int INVALID_CONNECTION_RESPONSE_CODE = -1;

    static final String LINKAGE_FIELD_NETWORK_HEADER = "X-InApp-Auth";

    static final String MESSAGE_TRIGGERED_ACTION_VALUE = "7";
    static final String CHARSET_UTF_8 = "UTF-8";
    static final String HTTP_HEADER_KEY_ACCEPT = "Accept";
    static final String HTTP_HEADER_KEY_CONNECTION = "connection";
    static final String HTTP_HEADER_KEY_CONTENT_TYPE = "Content-Type";
    static final String HTTP_HEADER_CONTENT_TYPE_JSON_APPLICATION = "application/json";
    static final String HTTP_HEADER_IF_MODIFIED_SINCE = "If-Modified-Since";
    static final String HTTP_HEADER_LAST_MODIFIED = "Last-Modified";
    static final String HTTP_HEADER_IF_NONE_MATCH = "If-None-Match";
    static final String HTTP_HEADER_IF_RANGE = "If-Range";
    static final String HTTP_HEADER_ETAG = "Etag";
    static final ArrayList<Integer> recoverableNetworkErrorCodes = new ArrayList(Arrays.asList(HttpURLConnection.HTTP_CLIENT_TIMEOUT, HttpURLConnection.HTTP_GATEWAY_TIMEOUT, HttpURLConnection.HTTP_UNAVAILABLE));

    // cache service metadata
    static final String METADATA_PATH = "pathToFile";

    private CampaignConstants() {
    }

    /**
     * Context data keys
     */
    static final class ContextDataKeys {
        // in-app constants for message tracking
        static final String MESSAGE_TRIGGERED = "a.message.triggered";
        static final String MESSAGE_CLICKED = "a.message.clicked";
        static final String MESSAGE_VIEWED = "a.message.viewed";
        static final String MESSAGE_ID = "a.message.id";

        private ContextDataKeys() {
        }
    }

    static final class CampaignHit {
        static final String URL = "url";
        static final String PAYLOAD = "payload";
        static final String TIMEOUT = "timeout";

        private CampaignHit() {
        }
    }

    static final class Notification {
        static final String CONTENT_KEY = "NOTIFICATION_CONTENT";
        static final String USER_INFO_KEY = "NOTIFICATION_USER_INFO";
        static final String IDENTIFIER_KEY = "NOTIFICATION_IDENTIFIER";
        static final String DEEPLINK_KEY = "NOTIFICATION_DEEPLINK";
        static final String SOUND_KEY = "NOTIFICATION_SOUND";
        static final String SENDER_CODE_KEY = "NOTIFICATION_SENDER_CODE";
        static final int SENDER_CODE = 750183;
        static final String REQUEST_CODE_KEY = "NOTIFICATION_REQUEST_CODE";
        static final String TITLE = "NOTIFICATION_TITLE";

        private Notification() {
        }
    }

    /*
        EventDataKeys
     */
    static final class EventDataKeys {
        static final String STATE_OWNER = "stateowner";

        private EventDataKeys() {
        }

        static final class Campaign {
            static final String LINKAGE_FIELDS = "linkagefields";
            static final String TRACK_INFO_KEY_BROADLOG_ID = "broadlogId";
            static final String TRACK_INFO_KEY_DELIVERY_ID = "deliveryId";
            static final String TRACK_INFO_KEY_ACTION = "action";

            private Campaign() {
            }
        }

        static final class Configuration {
            static final String EXTENSION_NAME = "com.adobe.module.configuration";

            // config response keys
            static final String GLOBAL_CONFIG_PRIVACY = "global.privacy";
            static final String PROPERTY_ID = "property.id";
            static final String CAMPAIGN_SERVER_KEY = "campaign.server";
            static final String CAMPAIGN_PKEY_KEY = "campaign.pkey";
            static final String CAMPAIGN_MCIAS_KEY = "campaign.mcias";
            static final String CAMPAIGN_TIMEOUT = "campaign.timeout";
            static final String CAMPAIGN_REGISTRATION_DELAY_KEY = "campaign.registrationDelay";
            static final String CAMPAIGN_REGISTRATION_PAUSED_KEY = "campaign.registrationPaused";

            private Configuration() {
            }
        }

        static final class RuleEngine {
            static final String CONSEQUENCE_TRIGGERED = "triggeredconsequence";

            // root message consequence keys
            static final String MESSAGE_CONSEQUENCE_ID = "id";
            static final String MESSAGE_CONSEQUENCE_TYPE = "type";
            static final String MESSAGE_CONSEQUENCE_DETAIL = "detail";

            // all message types
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_TEMPLATE = "template";

            // fullscreen messages only
            static final String MESSAGE_CONSEQUENCE_ASSETS_PATH = "assetsPath";
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_HTML = "html";
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_REMOTE_ASSETS = "remoteAssets";

            // alert and local notifications messages only
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_CONTENT = "content";
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_TITLE = "title";

            // alert messages only
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_CONFIRM = "confirm";
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_CANCEL = "cancel";
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_URL = "url";

            // local notification messages only
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_WAIT = "wait";
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_FIRE_DATE = "date";
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_DEEPLINK_URL = "adb_deeplink";
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_USER_DATA = "userData";
            static final String MESSAGE_CONSEQUENCE_DETAIL_KEY_SOUND = "sound";

            private RuleEngine() {
            }
        }

        static final class Identity {
            static final String EXTENSION_NAME = "com.adobe.module.identity";
            static final String VISITOR_ID_MID = "mid";

            private Identity() {
            }
        }

        static final class Lifecycle {
            static final String EXTENSION_NAME = "com.adobe.module.lifecycle";
            static final String LAUNCH_EVENT = "launchevent";
            static final String INSTALL_EVENT = "installevent";
            static final String LIFECYCLE_CONTEXT_DATA = "lifecyclecontextdata";

            private Lifecycle() {
            }
        }

    }
}