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

import com.adobe.marketing.mobile.launch.rulesengine.RuleConsequence;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.ui.NotificationSetting;
import com.adobe.marketing.mobile.services.ui.UIService;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.StringUtils;

import java.util.Map;

/**
 * Provides implementation logic for handling events related to native Local Notification messages.
 * <p>
 * The actual UI implementation happens in the platform services.
 */
class LocalNotificationMessage extends CampaignMessage {
    private final String SELF_TAG = "LocalNotificationMessage";
    private final static long DEFAULT_DELAY = -1L;
    private final UIService uiService;

    // ================================================================================
    // protected class members
    // ================================================================================
    String content;
    String deeplink;
    String sound;
    Map<String, Object> userdata;
    int localNotificationDelay;
    long fireDate;
    String title;

    /**
     * Constructor.
     *
     * @param extension   parent {@link CampaignExtension} instance
     * @param consequence {@link RuleConsequence} instance containing a message defining payload
     * @throws CampaignMessageRequiredFieldMissingException {@code consequence} is null or if any required field for the
     *                                                      {@link LocalNotificationMessage} is null or empty
     */
    LocalNotificationMessage(final CampaignExtension extension, final RuleConsequence consequence)
            throws CampaignMessageRequiredFieldMissingException {
        super(extension, consequence);
        uiService = ServiceProvider.getInstance().getUIService();
        parseLocalNotificationMessagePayload(consequence);
    }

    /**
     * Parses a {@code Map<String, Object>} instance defining message payload for a {@code LocalNotificationMessage} object.
     * <p>
     * Required fields:
     * <ul>
     *     <li>{@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_DETAIL_KEY_CONTENT} - {@link String} containing the message content for this message</li>
     * </ul>
     * Optional fields:
     * <ul>
     *     <li>{@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_DETAIL_KEY_FIRE_DATE} - {@code long} containing number of seconds since epoch to schedule the notification to be shown.  This field has priority over that in the {@code wait} field.</li>
     *     <li>{@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_DETAIL_KEY_WAIT} - {@code int} containing delay, in seconds, until the notification should show.  If a {@code date} is specified, this field is ignored.</li>
     *     <li>{@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_DETAIL_KEY_DEEPLINK_URL} - {@code String} containing a deeplink URL.</li>
     *     <li>{@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_DETAIL_KEY_USER_DATA} - {@link org.json.JSONObject} containing additional user data.</li>
     *     <li>{@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_DETAIL_KEY_SOUND} - {@code String} containing the name of a bundled sound file to use when the notification is triggered.</li>
     *     <li>{@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_DETAIL_KEY_TITLE} - {@code String} containing the title for this message.</li>
     * </ul>
     *
     * @param consequence {@link RuleConsequence} instance containing the message payload to be parsed
     * @throws CampaignMessageRequiredFieldMissingException if any of the required fields are missing from {@code consequence}
     */
    @SuppressWarnings("unchecked")
    private void parseLocalNotificationMessagePayload(final RuleConsequence consequence) throws
            CampaignMessageRequiredFieldMissingException {

        if (consequence == null) {
            throw new CampaignMessageRequiredFieldMissingException("Message consequence is null.");
        }

        final Map<String, Object> detailDictionary = consequence.getDetail();

        if (detailDictionary == null || detailDictionary.isEmpty()) {
            throw new CampaignMessageRequiredFieldMissingException("Message \"detail\" is missing.");
        }

        // content is required
        content = DataReader.optString(detailDictionary, CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_CONTENT, "");

        if (StringUtils.isNullOrEmpty(content)) {
            throw new CampaignMessageRequiredFieldMissingException("Message \"content\" is empty.");
        }

        // prefer the date specified by fire date, otherwise use provided delay. both are optional
        fireDate = DataReader.optLong(detailDictionary, CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_FIRE_DATE, DEFAULT_DELAY);

        if (fireDate <= 0) {
            localNotificationDelay = DataReader.optInt(detailDictionary, CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_WAIT, CampaignConstants.DEFAULT_LOCAL_NOTIFICATION_DELAY_SECONDS);
        }

        // deeplink is optional
        deeplink = DataReader.optString(detailDictionary, CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_DEEPLINK_URL, "");

        if (StringUtils.isNullOrEmpty(deeplink)) {
            Log.trace(CampaignConstants.LOG_TAG, SELF_TAG,
                    "parseLocalNotificationMessagePayload -  Tried to read \"adb_deeplink\" for local notification but found none. This is not a required field.");
        }

        // userInfo is optional
        userdata = DataReader.optTypedMap(Object.class, detailDictionary, CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_USER_DATA, null);

        if (userdata == null || userdata.isEmpty()) {
            Log.trace(CampaignConstants.LOG_TAG, SELF_TAG,
                    "parseLocalNotificationMessagePayload -  Tried to read \"userData\" for local notification but found none. This is not a required field.");
        }

        // sound is optional
        sound = DataReader.optString(detailDictionary, CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_SOUND, "");

        if (StringUtils.isNullOrEmpty(sound)) {
            Log.trace(CampaignConstants.LOG_TAG, SELF_TAG,
                    "parseLocalNotificationMessagePayload -  Tried to read \"sound\" for local notification but found none. This is not a required field.");
        }

        // title is optional
        title = DataReader.optString(detailDictionary, CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_TITLE, "");

        if (StringUtils.isNullOrEmpty(title)) {
            Log.trace(CampaignConstants.LOG_TAG, SELF_TAG,
                    "parseLocalNotificationMessagePayload -  Tried to read \"title\" for local notification but found none. This is not a required field.");
        }

    }


    /**
     * This method requests {@code UIService} to show this {@code LocalNotificationMessage} and invokes method on the
     * parent {@code CampaignMessage} class to dispatch a triggered event.
     *
     * @see CampaignMessage#triggered()
     * @see com.adobe.marketing.mobile.services.ui.UIService#showLocalNotification(NotificationSetting)
     */
    @Override
    void showMessage() {
        triggered();

        // Dispatch message info to send track request to Campaign
        if (userdata != null && !userdata.isEmpty()
                && userdata.containsKey(CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_BROADLOG_ID)
                && userdata.containsKey(CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_DELIVERY_ID)) {

            final String broadlogId = DataReader.optString(userdata, CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_BROADLOG_ID, "");
            final String deliveryId = DataReader.optString(userdata, CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_DELIVERY_ID, "");

            if (!StringUtils.isNullOrEmpty(broadlogId) || !StringUtils.isNullOrEmpty(deliveryId)) {
                Log.trace(CampaignConstants.LOG_TAG, SELF_TAG,
                        "showMessage -  Calling dispatch message Info with broadlogId(%s) and deliveryId(%s) for the triggered message.",
                        broadlogId, deliveryId);
                callDispatchMessageInfo(broadlogId, deliveryId, CampaignConstants.MESSAGE_TRIGGERED_ACTION_VALUE);
            } else {
                Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                        "showMessage -  Cannot dispatch message info because broadlogid and/or deliveryid are empty.");

            }
        }

        final NotificationSetting notificationSetting = NotificationSetting.build(messageId, content, fireDate, localNotificationDelay, deeplink, userdata, sound, title);
        Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "showMessage -  Scheduling local notification message with ID (%s)", messageId);
        uiService.showLocalNotification(notificationSetting);
    }

    @Override
    boolean shouldDownloadAssets() {
        return false;
    }
}