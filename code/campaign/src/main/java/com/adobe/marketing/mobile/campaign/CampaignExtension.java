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

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.adobe.marketing.mobile.Campaign;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.MobilePrivacyStatus;
import com.adobe.marketing.mobile.SharedStateResolution;
import com.adobe.marketing.mobile.SharedStateResult;
import com.adobe.marketing.mobile.SharedStateStatus;
import com.adobe.marketing.mobile.launch.rulesengine.LaunchRulesEngine;
import com.adobe.marketing.mobile.launch.rulesengine.RuleConsequence;
import com.adobe.marketing.mobile.launch.rulesengine.download.RulesLoadResult;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.DataQueue;
import com.adobe.marketing.mobile.services.DataQueuing;
import com.adobe.marketing.mobile.services.DataStoring;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.PersistentHitQueue;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StreamUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/**
 * The CampaignExtension class is responsible for showing Messages and downloading/caching their
 * remote assets when applicable.
 *
 * <p>The {@link CampaignExtension} class listens for the following {@link Event}s:
 *
 * <ul>
 *   <li>{@link EventType#CAMPAIGN} - {@link EventSource#REQUEST_CONTENT}
 *   <li>{@code EventType.CAMPAIGN} - {@link EventSource#REQUEST_IDENTITY}
 *   <li>{@link EventType#CONFIGURATION} - {@link EventSource#RESPONSE_CONTENT}
 *   <li>{@link EventType#GENERIC_DATA} - {@link EventSource#OS}
 *   <li>{@link EventType#HUB} - {@link EventSource#SHARED_STATE}
 *   <li>{@link EventType#LIFECYCLE} - {@link EventSource#RESPONSE_CONTENT}
 * </ul>
 *
 * <p>The {@code CampaignExtension} class dispatches the following {@code Event}s:
 *
 * <ul>
 *   <li>{@link EventType#CAMPAIGN} - {@code EventSource.RESPONSE_CONTENT}
 * </ul>
 */
public class CampaignExtension extends Extension {
    private static final String DATA_FOR_MESSAGE_REQUEST_EVENT_NAME = "DataForMessageRequest";
    private static final String INTERNAL_GENERIC_DATA_EVENT_NAME = "InternalGenericDataEvent";
    private static final String CLICKED_STRING_VALUE = "2";
    private static final String VIEWED_STRING_VALUE = "1";
    private final String SELF_TAG = "CampaignExtension";
    private final ExtensionApi extensionApi;
    private final PersistentHitQueue campaignPersistentHitQueue;
    private final LaunchRulesEngine campaignRulesEngine;
    private final CacheService cacheService;
    private final CampaignRulesDownloader campaignRulesDownloader;
    private final CampaignState campaignState;
    private final DataStoring dataStoreService;
    private String linkageFields;
    private boolean hasCachedRulesLoaded = false;
    private boolean hasToDownloadRules = true;

    /**
     * Constructor.
     *
     * @param extensionApi {@link ExtensionApi} instance
     */
    public CampaignExtension(final ExtensionApi extensionApi) {
        super(extensionApi);
        this.extensionApi = extensionApi;

        // retrieve service dependencies
        dataStoreService = ServiceProvider.getInstance().getDataStoreService();

        // migrate ACPCampaign datastore if present
        migrateFromACPCampaign(getNamedCollection());

        // initialize campaign rules engine
        campaignRulesEngine =
                new LaunchRulesEngine(CampaignConstants.RULE_ENGINE_NAME, extensionApi);

        // initialize campaign rules downloader
        cacheService = ServiceProvider.getInstance().getCacheService();
        campaignRulesDownloader =
                new CampaignRulesDownloader(
                        extensionApi, campaignRulesEngine, getNamedCollection(), cacheService);

        // setup persistent hit queue
        final DataQueuing campaignDataQueueService =
                ServiceProvider.getInstance().getDataQueueService();
        final DataQueue campaignDataQueue =
                campaignDataQueueService.getDataQueue(CampaignConstants.EXTENSION_NAME);
        campaignPersistentHitQueue =
                new PersistentHitQueue(campaignDataQueue, new CampaignHitProcessor());

        // initialize the campaign state
        campaignState = new CampaignState();
    }

    /**
     * Testing Constructor.
     *
     * @param extensionApi {@link ExtensionApi} instance
     * @param persistentHitQueue {@link PersistentHitQueue} instance to use for testing
     * @param dataStoreService {@link DataStoring} instance to use for testing
     * @param launchRulesEngine {@link LaunchRulesEngine} instance to use for testing
     * @param campaignState {@link CampaignState} instance to use for testing
     * @param cacheService {@link CacheService} instance to use for testing
     * @param campaignRulesDownloader {@link CampaignRulesDownloader} instance to use for testing
     */
    @VisibleForTesting
    CampaignExtension(
            final ExtensionApi extensionApi,
            final PersistentHitQueue persistentHitQueue,
            final DataStoring dataStoreService,
            final LaunchRulesEngine launchRulesEngine,
            final CampaignState campaignState,
            final CacheService cacheService,
            final CampaignRulesDownloader campaignRulesDownloader) {
        super(extensionApi);
        this.extensionApi = extensionApi;

        //  use passed in datastore service
        this.dataStoreService = dataStoreService;

        // use passed in rules engine
        this.campaignRulesEngine = launchRulesEngine;

        // use passed in cache service
        this.cacheService = cacheService;

        // use passed in campaign rules downloader
        this.campaignRulesDownloader = campaignRulesDownloader;

        // use passed in persistent hit queue
        this.campaignPersistentHitQueue = persistentHitQueue;

        // use passed in campaign state
        this.campaignState = campaignState;
    }

    @Override
    protected String getName() {
        return CampaignConstants.EXTENSION_NAME;
    }

    @Override
    protected String getFriendlyName() {
        return CampaignConstants.FRIENDLY_NAME;
    }

    @Override
    protected String getVersion() {
        return Campaign.extensionVersion();
    }

    @Override
    protected void onRegistered() {
        Log.debug(
                CampaignConstants.LOG_TAG,
                SELF_TAG,
                "Registered Campaign extension - version %s",
                getVersion());
        // register listeners
        getApi().registerEventListener(
                        EventType.CAMPAIGN,
                        EventSource.REQUEST_IDENTITY,
                        this::handleLinkageFieldsEvent);
        getApi().registerEventListener(
                        EventType.CAMPAIGN,
                        EventSource.REQUEST_RESET,
                        this::handleLinkageFieldsEvent);
        getApi().registerEventListener(
                        EventType.CONFIGURATION,
                        EventSource.RESPONSE_CONTENT,
                        this::processConfigurationResponse);
        getApi().registerEventListener(
                        EventType.GENERIC_DATA, EventSource.OS, this::processMessageInformation);
        getApi().registerEventListener(
                        EventType.LIFECYCLE,
                        EventSource.RESPONSE_CONTENT,
                        this::processLifecycleUpdate);
        getApi().registerEventListener(
                        EventType.WILDCARD, EventSource.WILDCARD, this::handleWildcardEvents);
        getApi().registerEventListener(
                        EventType.RULES_ENGINE,
                        EventSource.RESPONSE_CONTENT,
                        this::handleRuleEngineResponseEvents);

        FileUtils.deleteDatabaseFromCacheDir(CampaignConstants.DEPRECATED_1X_HIT_DATABASE_FILENAME);
    }

    @Override
    public boolean readyForEvent(final @NonNull Event event) {
        final Map<String, Object> eventData = event.getEventData();
        final String stateOwner =
                DataReader.optString(eventData, CampaignConstants.EventDataKeys.STATE_OWNER, "");
        if (stateOwner.equals(CampaignConstants.EventDataKeys.Identity.EXTENSION_NAME)) {
            setCampaignState(event);

            if (hasToDownloadRules && campaignState.canDownloadRulesWithCurrentState()) {
                hasToDownloadRules = false;
                triggerRulesDownload();
            }
        }

        final SharedStateResult configurationSharedState =
                getApi().getSharedState(
                                CampaignConstants.EventDataKeys.Configuration.EXTENSION_NAME,
                                event,
                                false,
                                SharedStateResolution.ANY);
        final SharedStateResult identitySharedState =
                getApi().getSharedState(
                                CampaignConstants.EventDataKeys.Identity.EXTENSION_NAME,
                                event,
                                false,
                                SharedStateResolution.ANY);
        return configurationSharedState != null
                && configurationSharedState.getStatus() == SharedStateStatus.SET
                && identitySharedState != null
                && identitySharedState.getStatus() == SharedStateStatus.SET;
    }

    // ========================================================================
    // package-private methods
    // ========================================================================

    /**
     * Processes all events dispatched to the {@code EventHub} to determine if any rules are
     * matched.
     *
     * @param event incoming {@link Event} object to be processed
     */
    void handleWildcardEvents(final Event event) {
        campaignRulesEngine.processEvent(event);
    }

    /**
     * Handles Rule Engine Response Content events which are dispatched when a event matches a rule
     * in the Campaign {@link LaunchRulesEngine}. This handler will attempt to show a {@link
     * CampaignMessage} created from the triggered rule consequence.
     *
     * @param event incoming {@link Event} object to be processed
     */
    void handleRuleEngineResponseEvents(final Event event) {
        if (!Utils.isInAppMessageEvent(event)) {
            return;
        }

        final Map<String, Object> consequenceMap =
                DataReader.optTypedMap(
                        Object.class,
                        event.getEventData(),
                        CampaignConstants.EventDataKeys.RuleEngine.CONSEQUENCE_TRIGGERED,
                        null);
        if (MapUtils.isNullOrEmpty(consequenceMap)) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "handleRulesResponseEvents - null or empty consequences found. Will not handle"
                            + " rules response event.");
            return;
        }

        final String id =
                DataReader.optString(
                        consequenceMap,
                        CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_ID,
                        "");
        final String type =
                DataReader.optString(
                        consequenceMap,
                        CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_TYPE,
                        "");
        final Map<String, Object> detail =
                DataReader.optTypedMap(
                        Object.class,
                        consequenceMap,
                        CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_DETAIL,
                        null);

        // detail is required
        if (MapUtils.isNullOrEmpty(detail)) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "handleRulesResponseEvents - null or empty consequence details found. Will not"
                            + " handle rules response event.");
            return;
        }

        try {
            final CampaignMessage triggeredMessage =
                    CampaignMessage.createMessageObject(
                            this, new RuleConsequence(id, type, detail));

            if (triggeredMessage == null) {
                Log.error(
                        CampaignConstants.LOG_TAG,
                        SELF_TAG,
                        "handleRulesResponseEvents -  Campaign message creation failed.");
                return;
            }

            triggeredMessage.showMessage();
        } catch (final CampaignMessageRequiredFieldMissingException ex) {
            Log.error(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "handleRulesResponseEvents -  Error reading message definition: \n %s",
                    ex);
        }
    }

    /**
     * Stores {@code Identity} and {@code Configuration} information.
     *
     * @param event incoming {@link Event} object to be processed
     */
    void setCampaignState(final Event event) {
        final SharedStateResult configSharedStateResult =
                getApi().getSharedState(
                                CampaignConstants.EventDataKeys.Configuration.EXTENSION_NAME,
                                event,
                                false,
                                SharedStateResolution.LAST_SET);
        final SharedStateResult identitySharedStateResult =
                getApi().getSharedState(
                                CampaignConstants.EventDataKeys.Identity.EXTENSION_NAME,
                                event,
                                false,
                                SharedStateResolution.LAST_SET);

        campaignState.setState(configSharedStateResult, identitySharedStateResult);
    }

    /**
     * Processes {@code Configuration} response to handle any update to {@code MobilePrivacyStatus},
     * handle any update to the number of days to delay or pause the sending of the Campaign
     * registration request, and to trigger Campaign rules download.
     *
     * <p>If {@link MobilePrivacyStatus} is changed to {@link MobilePrivacyStatus#OPT_OUT}, invokes
     * #processPrivacyOptOut() to handle privacy change. No event is queued for rules download in
     * this case.
     *
     * @param event to be processed
     * @see #processPrivacyOptOut()
     */
    @SuppressWarnings("checkstyle:NestedIfDepth")
    void processConfigurationResponse(final Event event) {
        if (event == null) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processConfigurationResponse - Unable to process event, event received is"
                            + " null.");
            return;
        }

        final Map<String, Object> eventData = event.getEventData();
        if (eventData == null || eventData.isEmpty()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processConfigurationResponse - Configuration response event is null");
            return;
        }

        setCampaignState(event);

        // attempt to load cached rules on the first configuration event received
        if (!hasCachedRulesLoaded) {
            final CacheResult cachedRulesZip =
                    cacheService.get(
                            CampaignConstants.CACHE_BASE_DIR
                                    + File.separator
                                    + CampaignConstants.RULES_CACHE_FOLDER,
                            CampaignConstants.ZIP_HANDLE);
            if (cachedRulesZip != null) {
                final CacheResult cacheRulesJson =
                        cacheService.get(
                                CampaignConstants.CACHE_BASE_DIR
                                        + File.separator
                                        + CampaignConstants.RULES_CACHE_FOLDER,
                                CampaignConstants.RULES_JSON_FILE_NAME);
                if (cacheRulesJson != null) {
                    final RulesLoadResult cachedRules =
                            new RulesLoadResult(
                                    StreamUtils.readAsString(cacheRulesJson.getData()),
                                    RulesLoadResult.Reason.SUCCESS);
                    campaignRulesDownloader.registerRules(cachedRules);
                    hasCachedRulesLoaded = true;
                }
            }
        }

        final MobilePrivacyStatus privacyStatus = campaignState.getMobilePrivacyStatus();
        // notify campaign persistent hit queue of any privacy status changes
        campaignPersistentHitQueue.handlePrivacyChange(privacyStatus);
        if (privacyStatus.equals(MobilePrivacyStatus.OPT_OUT)) {
            processPrivacyOptOut();
            return;
        }

        if (hasToDownloadRules && campaignState.canDownloadRulesWithCurrentState()) {
            hasToDownloadRules = false;
            triggerRulesDownload();
        } else {
            // Cannot download rules now. Most probably because Identity shared state hasn't
            // received yet and we don't have ECID. Will try to download rules after receiving
            // Identity shared state.
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processConfigurationResponse -  Campaign extension is not configured to"
                            + " download campaign rules.");
            hasToDownloadRules = true;
        }
    }

    /**
     * Processes {@code MobilePrivacyStatus} update to {@code MobilePrivacyStatus#OPT_OUT} on {@code
     * Configuration} response. And as a result,
     *
     * <ul>
     *   <li>Clears stored {@link #linkageFields}.
     *   <li>Unregisters previously registered rules.
     *   <li>Clears directory containing any previously cached rules.
     *   <li>Clears the Campaign data store.
     * </ul>
     */
    void processPrivacyOptOut() {
        Log.trace(
                CampaignConstants.LOG_TAG,
                SELF_TAG,
                "processPrivacyOptOut -  Clearing out cached data.");

        linkageFields = "";

        // unregister campaign rules
        campaignRulesEngine.replaceRules(null);

        // clear cached rules
        clearRulesCacheDirectory();

        // clear the datastore
        clearCampaignNamedCollection();
    }

    /**
     * Processes {@code Generic Data} events to send message tracking request to the configured
     * {@code Campaign} server.
     *
     * <p>If the current {@code Configuration} properties do not allow sending track request, no
     * request is sent.
     *
     * @param event {@link Event} object to be processed
     * @see CampaignState#canSendTrackInfoWithCurrentState()
     */
    void processMessageInformation(final Event event) {
        if (!campaignState.canSendTrackInfoWithCurrentState()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processMessageInformation -  Campaign extension is not configured to send"
                            + " message track request.");
            return;
        }

        if (event == null) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processMessageInformation - Unable to process event, event received is null.");
            return;
        }

        final Map<String, Object> eventData = event.getEventData();

        if (eventData == null || eventData.isEmpty()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processMessageInformation -  Cannot send message track request, eventData is"
                            + " null.");
            return;
        }

        final String broadlogId =
                DataReader.optString(
                        eventData,
                        CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_BROADLOG_ID,
                        "");
        final String deliveryId =
                DataReader.optString(
                        eventData,
                        CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_DELIVERY_ID,
                        "");
        final String action =
                DataReader.optString(
                        eventData,
                        CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_ACTION,
                        "");

        if (StringUtils.isNullOrEmpty(broadlogId)
                || StringUtils.isNullOrEmpty(deliveryId)
                || StringUtils.isNullOrEmpty(action)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processMessageInformation -  Cannot send message track request, %s %s %s null"
                            + " or empty.",
                    StringUtils.isNullOrEmpty(broadlogId) ? "broadlogId" : "",
                    StringUtils.isNullOrEmpty(deliveryId) ? "deliveryId" : "",
                    StringUtils.isNullOrEmpty(action) ? "action" : "");
            return;
        }

        dispatchMessageEvent(action, deliveryId);

        final String url =
                buildTrackingUrl(
                        campaignState.getCampaignServer(),
                        broadlogId,
                        deliveryId,
                        action,
                        campaignState.getExperienceCloudId());

        processRequest(url, "", campaignState, event);
    }

    /**
     * Dispatches an event with message id and action click. This is to mark that a notification is
     * interacted by user.
     *
     * @param action @{@link String} value represents user interaction action. Possible values are
     *     "7"(impression),"1"(open) and "2"(click).
     * @param deliveryId Hexadecimal value which is use to derive message id by converting it to
     *     Decimal.
     */
    void dispatchMessageEvent(final String action, final String deliveryId) {
        // Dispatch event only in case of action value "1"(open) and "2"(click).
        String actionKey = null;

        if (CLICKED_STRING_VALUE.equals(action)) {
            actionKey = CampaignConstants.ContextDataKeys.MESSAGE_CLICKED;
        } else if (VIEWED_STRING_VALUE.equals(action)) {
            actionKey = CampaignConstants.ContextDataKeys.MESSAGE_VIEWED;
        }

        if (actionKey == null) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "dispatchMessageEvent -  Action received is other than viewed or clicked, so"
                            + " cannot dispatch Campaign response. ");
            return;
        }

        final int hashMapCapacity = 2;
        final Map<String, Object> contextData = new HashMap<>(hashMapCapacity);
        // Convert hex format deliveryId to base 10, which is message id.
        final int hexBase = 16;
        contextData.put(
                CampaignConstants.ContextDataKeys.MESSAGE_ID,
                String.valueOf(Integer.parseInt(deliveryId, hexBase)));
        contextData.put(actionKey, String.valueOf(1));

        dispatchMessageInteraction(contextData);
    }

    /**
     * Processes {@code Lifecycle} event to send registration request to the configured {@code
     * Campaign} server.
     *
     * <p>If current {@code Configuration} properties do not allow sending registration request, no
     * request is sent.
     *
     * @param event The received Lifecycle response {@link Event}
     * @see CampaignState#canRegisterWithCurrentState()
     */
    void processLifecycleUpdate(final Event event) {
        if (event == null) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processLifecycleUpdate - Unable to process event, event received is null.");
            return;
        }

        if (!campaignState.canRegisterWithCurrentState()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processLifecycleUpdate -  Campaign extension is not configured to send"
                            + " registration request.");
            return;
        }

        final String url =
                buildRegistrationUrl(
                        campaignState.getCampaignServer(),
                        campaignState.getCampaignPkey(),
                        campaignState.getExperienceCloudId());
        final String payload =
                buildRegistrationPayload(
                        "gcm", campaignState.getExperienceCloudId(), new HashMap<>());

        processRequest(url, payload, campaignState, event);
    }

    /**
     * Triggers rules download from the configured {@code Campaign} server.
     *
     * <p>If current {@code Configuration} properties do not allow downloading {@code Campaign}
     * rules, no request is sent.
     *
     * @see CampaignState#canDownloadRulesWithCurrentState()
     * @see CampaignRulesDownloader#loadRulesFromUrl(String, String)
     */
    void triggerRulesDownload() {
        final String rulesUrl =
                String.format(
                        CampaignConstants.CAMPAIGN_RULES_DOWNLOAD_URL,
                        campaignState.getCampaignMcias(),
                        campaignState.getCampaignServer(),
                        campaignState.getPropertyId(),
                        campaignState.getExperienceCloudId());

        campaignRulesDownloader.loadRulesFromUrl(rulesUrl, getLinkageFields());
    }

    /**
     * Processes campaign request identity event then queues a set linkage fields or reset linkage
     * fields event.
     *
     * <p>If the event's data contains a map of linkage fields then the map will be stored in memory
     * and subsequently used to download personalized rules. If the event source is {@link
     * EventSource#REQUEST_RESET} then any stored linkage fields are cleared from memory.
     *
     * @param event {@link Event} object to be processed.
     */
    void handleLinkageFieldsEvent(final Event event) {
        if (event == null) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "handleLinkageFieldsEvent - Unable to process event, event received is null.");
            return;
        }

        if (event.getSource().equals(EventSource.REQUEST_RESET)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "handleLinkageFieldsEvent - Resetting linkage fields.");
            handleResetLinkageFields();
            return;
        }

        final Map<String, Object> eventData = event.getEventData();
        if (eventData == null || eventData.isEmpty()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "handleLinkageFieldsEvent - Ignoring event with null or empty EventData.");
            return;
        }

        final Map<String, String> linkageFields =
                DataReader.optStringMap(
                        eventData, CampaignConstants.EventDataKeys.Campaign.LINKAGE_FIELDS, null);
        if (linkageFields == null || linkageFields.isEmpty()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "handleLinkageFieldsEvent - Unable to set linkage fields, received linkage"
                            + " fields are null or empty.");
            return;
        }

        final String linkageFieldsJsonString = new JSONObject(linkageFields).toString();
        if (StringUtils.isNullOrEmpty(linkageFieldsJsonString)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "handleLinkageFieldsEvent -  Cannot set linkage fields, linkageFields JSON"
                            + " string is null or empty.");
            return;
        }
        this.linkageFields =
                Base64.encodeToString(linkageFieldsJsonString.getBytes(), Base64.NO_WRAP);

        if (StringUtils.isNullOrEmpty(this.linkageFields)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "handleLinkageFieldsEvent -  Cannot set linkage fields, base64 encoded linkage"
                            + " fields string is empty.");
            return;
        }

        if (!campaignState.canDownloadRulesWithCurrentState()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "handleLinkageFieldsEvent -  Campaign extension is not configured to download"
                            + " campaign rules.");
            return;
        }
        clearRulesCacheDirectory();
        triggerRulesDownload();
    }

    /** Clears the rules cache directory. */
    void clearRulesCacheDirectory() {
        final File rulesCacheDir =
                new File(
                        ServiceProvider.getInstance()
                                        .getDeviceInfoService()
                                        .getApplicationCacheDir()
                                + File.separator
                                + CampaignConstants.AEPSDK_CACHE_BASE_DIR
                                + File.separator
                                + CampaignConstants.CACHE_BASE_DIR
                                + File.separator
                                + CampaignConstants.RULES_CACHE_FOLDER);
        Utils.cleanDirectory(rulesCacheDir);
    }

    /**
     * Invokes the extension api dispatch function {@code EventType#CAMPAIGN}, {@code
     * EventSource#RESPONSE_CONTENT} event with the provided {@code messageData} Map.
     *
     * @param messageData {@link Map<String, Object>} containing message interaction data
     */
    void dispatchMessageInteraction(final Map<String, Object> messageData) {
        // Dispatch a campaign event to the event hub
        if (messageData == null || messageData.isEmpty()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "dispatchMessageInteraction -  Cannot dispatch Campaign response event, message"
                            + " interaction data is null or empty.");
            return;
        }

        final Event messageEvent =
                new Event.Builder(
                                DATA_FOR_MESSAGE_REQUEST_EVENT_NAME,
                                EventType.CAMPAIGN,
                                EventSource.RESPONSE_CONTENT)
                        .setEventData(messageData)
                        .build();

        extensionApi.dispatch(messageEvent);
    }

    /**
     * Invokes the extension api dispatch function to dispatch {@code EventType.GENERIC_DATA},
     * {@code EventSource.OS} event with the provided message info.
     *
     * @param broadlogId {@link String} containing message broadlogId
     * @param deliveryId {@code String} containing message deliveryId
     * @param action {@code String} containing message action
     */
    void dispatchMessageInfo(
            final String broadlogId, final String deliveryId, final String action) {
        // Dispatch a generic data OS event to the event hub
        final Map<String, Object> eventData = new HashMap<>();

        eventData.put(
                CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_BROADLOG_ID, broadlogId);
        eventData.put(
                CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_DELIVERY_ID, deliveryId);
        eventData.put(CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_ACTION, action);

        final Event messageEvent =
                new Event.Builder(
                                INTERNAL_GENERIC_DATA_EVENT_NAME,
                                EventType.GENERIC_DATA,
                                EventSource.OS)
                        .setEventData(eventData)
                        .build();

        extensionApi.dispatch(messageEvent);
    }

    /**
     * Returns this {@code linkageFields}.
     *
     * @return {@link String} containing this {@link #linkageFields}
     */
    String getLinkageFields() {
        return linkageFields;
    }

    // ========================================================================
    // private methods
    // ========================================================================

    /**
     * Processes campaign request reset event then queues the event.
     *
     * <p>This event has no data but is used as a signal that the SDK should clear any persisted
     * linkage fields and personalized rules and subsequently download generic rules.
     */
    private void handleResetLinkageFields() {
        linkageFields = "";

        campaignRulesEngine.replaceRules(new ArrayList<>());

        clearRulesCacheDirectory();

        triggerRulesDownload();
    }

    /**
     * Returns {@code CampaignExtension}'s {@link NamedCollection}.
     *
     * <p>If {@link DataStoring} is unavailable this method returns null.
     *
     * @return {@link NamedCollection} object for this {@link CampaignExtension}
     */
    private NamedCollection getNamedCollection() {
        return dataStoreService.getNamedCollection(
                CampaignConstants.CAMPAIGN_NAMED_COLLECTION_NAME);
    }

    /**
     * Migrates any datastore entries found in an ACPCampaign datastore to the {@code AEPCampaign}
     * datastore.
     *
     * @param aepDatastore the AEPCampaign {@link NamedCollection}
     */
    private void migrateFromACPCampaign(final NamedCollection aepDatastore) {
        if (aepDatastore == null) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "migrateFromACPCampaign - Will not perform migration, provided datastore is"
                            + " null.");
            return;
        }

        SharedPreferences sharedPreferences = null;
        final Application application = MobileCore.getApplication();
        if (application == null) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "migrateFromACPCampaign - Will not perform migration, application is null.");
            return;
        }
        final Context appContext = application.getApplicationContext();
        if (appContext != null) {
            sharedPreferences =
                    appContext.getSharedPreferences(
                            CampaignConstants.ACP_CAMPAIGN_DATASTORE_NAME, 0);
        }

        if (sharedPreferences == null) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "migrateFromACPCampaign - Will not perform migration, shared preferences are"
                            + " null.");
            return;
        }

        // we need to check if the ACPCampaign datastore exists by checking for the presence of
        // any entries in the shared preferences as the returned object is never null
        if (sharedPreferences.getAll().isEmpty()) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "migrateFromACPCampaign - Will not perform migration, existing shared"
                            + " preferences not found.");
            return;
        }

        Log.trace(
                CampaignConstants.LOG_TAG,
                SELF_TAG,
                "migrateFromACPCampaign - Campaign preferences found, migrating existing shared"
                        + " preferences.");
        // start copying values from ACP datastore to the AEP datastore
        // the key names are the same so we can use the AEP datastore constants
        aepDatastore.setString(
                CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY,
                sharedPreferences.getString(
                        CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY, ""));
        aepDatastore.setString(
                CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY,
                sharedPreferences.getString(
                        CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY, ""));
        aepDatastore.setLong(
                CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY,
                sharedPreferences.getLong(
                        CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY,
                        -1L));

        // delete the ACP datastore at com.app.package.name/shared_prefs/CampaignDatastore.xml
        final File applicationBaseDir =
                ServiceProvider.getInstance().getDeviceInfoService().getApplicationBaseDir();
        final File acpDatastore =
                new File(
                        applicationBaseDir.getPath()
                                + File.separator
                                + "shared_prefs"
                                + File.separator
                                + CampaignConstants.ACP_CAMPAIGN_DATASTORE_NAME
                                + ".xml");
        if (acpDatastore.exists()) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "migrateFromACPCampaign - Deleting migrated shared preferences file (%s).",
                    acpDatastore.getName());
            FileUtils.deleteFile(acpDatastore, false);
        }
    }

    /**
     * Updates {@value CampaignConstants#CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY} in
     * {@code CampaignExtension}'s {@link NamedCollection}.
     *
     * <p>If provided {@code ecid} is null or empty, {@value
     * CampaignConstants#CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY} key is removed from the
     * named collection.
     *
     * @param ecid {@code String} containing the last known experience cloud id.
     */
    private void updateEcidInNamedCollection(final String ecid) {
        final NamedCollection campaignNamedCollection = getNamedCollection();

        if (campaignNamedCollection == null) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "updateEcidInNamedCollection - Campaign Named Collection is null, cannot store"
                            + " ecid.");
            return;
        }

        if (StringUtils.isNullOrEmpty(ecid)) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "updateEcidInNamedCollection -  Removing experience cloud id key in Campaign"
                            + " Named Collection.");
            campaignNamedCollection.remove(
                    CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY);
        } else {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "updateEcidInNamedCollection -  Persisting experience cloud id (%s) in Campaign"
                            + " Named Collection.",
                    ecid);
            campaignNamedCollection.setString(
                    CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY, ecid);
        }
    }

    /**
     * Queues a {@code Campaign} registration request by creating a {@link
     * com.adobe.marketing.mobile.services.DataEntity} object and inserting it to the Campaign
     * {@link DataQueue} instance.
     *
     * <p>If the {@code payload} is null or empty then the {@code Campaign} registration request is
     * dropped.
     *
     * @param url {@link String} containing the registration request URL
     * @param payload {@link String} containing the registration request payload
     * @param campaignState {@link CampaignState} instance containing the current {@code Campaign}
     *     configuration
     * @param event {@link Event} which triggered the queuing of the {@code Campaign} registration
     *     request
     */
    private void processRequest(
            final String url,
            final String payload,
            final CampaignState campaignState,
            final Event event) {
        // check if this request is a registration request by checking for the presence of a payload
        // and if it is a registration request, determine if it should be sent.
        if (!StringUtils.isNullOrEmpty(payload)
                && !shouldSendRegistrationRequest(campaignState, event.getTimestamp())) {
            return;
        }

        // create a data entity and add it to the data queue
        final CampaignHit campaignHit =
                new CampaignHit(url, payload, campaignState.getCampaignTimeout());
        final DataEntity dataEntity = new DataEntity(campaignHit.toString());
        Log.debug(
                CampaignConstants.LOG_TAG,
                SELF_TAG,
                "processRequest - Campaign Request Queued with url (%s) and body (%s)",
                url,
                payload);
        campaignPersistentHitQueue.queue(dataEntity);
    }

    /** Clears the {@code CampaignExtension}'s {@link NamedCollection}. */
    private void clearCampaignNamedCollection() {
        final NamedCollection campaignNamedCollection = getNamedCollection();

        if (campaignNamedCollection == null) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "clearCampaignNamedCollection -  Campaign Named Collection is not available to"
                            + " be cleared.");
            return;
        }

        campaignNamedCollection.removeAll();
    }

    /**
     * Creates a Campaign track request URL with the provided {@code Campaign} properties.
     *
     * @param server {@link String} containing the configured Campaign server
     * @param experienceCloudId {@code String} containing the Experience Cloud Id
     * @return {@code String} containing the Campaign message tracking URL
     */
    private String buildTrackingUrl(
            final String server,
            final String broadlogId,
            final String deliveryId,
            final String action,
            final String experienceCloudId) {
        return String.format(
                CampaignConstants.CAMPAIGN_TRACKING_URL,
                server,
                broadlogId,
                deliveryId,
                action,
                experienceCloudId);
    }

    /**
     * Creates a Campaign registration request URL with the provided {@code Campaign} properties.
     *
     * @param server {@link String} containing the configured Campaign server
     * @param pkey {@code String} containing the configured Campaign pkey
     * @param experienceCloudId {@code String} containing the Experience Cloud Id
     * @return {@code String} containing the Campaign registration URL
     */
    private String buildRegistrationUrl(
            final String server, final String pkey, final String experienceCloudId) {
        return String.format(
                CampaignConstants.CAMPAIGN_REGISTRATION_URL, server, pkey, experienceCloudId);
    }

    /**
     * Creates payload {@code String} for Campaign registration request.
     *
     * @param platform {@link String} specifying the push platform "gcm" or "apns"
     * @param experienceCloudId {@code String} containing the Experience Cloud Id
     * @param data {@code Map<String, String>} containing additional key-value pairs to be sent in
     *     the request
     * @return {@code String} containing the Campaign registration payload
     */
    private String buildRegistrationPayload(
            final String platform, final String experienceCloudId, final Map<String, String> data) {
        Map<String, String> profileData = new HashMap<>(data);
        profileData.put(CampaignConstants.CAMPAIGN_PUSH_PLATFORM, platform);
        profileData.put(CampaignConstants.EXPERIENCE_CLOUD_ID, experienceCloudId);

        JSONObject bodyJSON = new JSONObject(profileData);

        if (bodyJSON == null) {
            return "";
        }

        return bodyJSON.toString();
    }

    /**
     * Determines if a registration request should be sent to Campaign.
     *
     * @param campaignState {@link CampaignState} instance containing the current {@code Campaign}
     *     configuration
     * @param eventTimestamp {@link Long} containing the registration event's timestamp.
     * @return the {@code boolean} indicating if a registration request should be sent.
     */
    private boolean shouldSendRegistrationRequest(
            final CampaignState campaignState, final long eventTimestamp) {
        // quick out if registration requests should be ignored
        final boolean shouldPauseRegistration = campaignState.getCampaignRegistrationPaused();

        if (shouldPauseRegistration) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "shouldSendRegistrationRequest -  Registration requests are paused.");
            return false;
        }

        final NamedCollection campaignNamedCollection = getNamedCollection();
        final String retrievedEcid =
                campaignNamedCollection.getString(
                        CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY, "");
        final String currentEcid = campaignState.getExperienceCloudId();
        final long retrievedTimestamp =
                campaignNamedCollection.getLong(
                        CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY,
                        CampaignConstants.DEFAULT_TIMESTAMP_VALUE);
        final int registrationDelay = campaignState.getCampaignRegistrationDelay();
        final long registrationDelayInMilliseconds = TimeUnit.DAYS.toMillis(registrationDelay);

        if (!retrievedEcid.equals(currentEcid)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "shouldSendRegistrationRequest - The current ecid (%s) is new, sending the"
                            + " registration request.",
                    currentEcid);
            updateEcidInNamedCollection(currentEcid);
            return true;
        }

        if (eventTimestamp - retrievedTimestamp >= registrationDelayInMilliseconds) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "shouldSendRegistrationRequest -  Registration delay of (%d) days has elapsed."
                            + " Sending the Campaign registration request.",
                    registrationDelay);
            return true;
        }

        Log.debug(
                CampaignConstants.LOG_TAG,
                SELF_TAG,
                "shouldSendRegistrationRequest - The registration request will not be sent because"
                        + " the registration delay of (%d) days has not elapsed.",
                registrationDelay);
        return false;
    }
}
