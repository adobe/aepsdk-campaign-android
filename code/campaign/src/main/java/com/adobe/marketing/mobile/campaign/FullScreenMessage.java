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

import androidx.annotation.VisibleForTesting;

import com.adobe.marketing.mobile.launch.rulesengine.RuleConsequence;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.services.ui.FullscreenMessage;
import com.adobe.marketing.mobile.services.ui.FullscreenMessageDelegate;
import com.adobe.marketing.mobile.services.ui.MessageSettings;
import com.adobe.marketing.mobile.services.ui.UIService;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.StreamUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.util.UrlUtils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides implementation logic for handling events related to full screen in-app messages.
 * <p>
 * The actual UI implementation happens in the platform services.
 */
class FullScreenMessage extends CampaignMessage {
    private final String SELF_TAG = "FullScreenMessage";
    private final static int FILL_DEVICE_DISPLAY = 100;
    private final String MESSAGES_CACHE = CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.MESSAGE_CACHE_DIR + File.separator;
    private final CacheService cacheService;
    private final UIService uiService;

    private String html;
    private String htmlContent;
    private String messageId;
    private final List<List<String>> assets = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param extension   {@link CampaignExtension} that is this parent
     * @param consequence {@link RuleConsequence} instance containing a message-defining payload
     * @throws CampaignMessageRequiredFieldMissingException if {@code consequence} is null or if any required field for a
     *                                                      {@link FullScreenMessage} is null or empty
     */
    FullScreenMessage(final CampaignExtension extension, final RuleConsequence consequence) throws CampaignMessageRequiredFieldMissingException {
        super(extension, consequence);
        cacheService = ServiceProvider.getInstance().getCacheService();
        uiService = ServiceProvider.getInstance().getUIService();
        parseFullScreenMessagePayload(consequence);
    }

    /**
     * Parses a {@code CampaignRuleConsequence} instance defining message payload for a {@code FullScreenMessage} object.
     * <p>
     * Required fields:
     * <ul>
     *     <li>{@code html} - {@link String} containing html for this message</li>
     * </ul>
     * Optional fields:
     * <ul>
     *     <li>{@code assets} - {@code Array} of {@code String[]}s containing remote assets to prefetch and cache</li>
     * </ul>
     *
     * @param consequence {@code CampaignRuleConsequence} instance containing the message payload to be parsed
     * @throws CampaignMessageRequiredFieldMissingException if any of the required fields are missing from {@code consequence}
     */
    @SuppressWarnings("unchecked")
    private void parseFullScreenMessagePayload(final RuleConsequence consequence) throws CampaignMessageRequiredFieldMissingException {
        if (consequence == null) {
            throw new CampaignMessageRequiredFieldMissingException("Message consequence is null.");
        }

        final Map<String, Object> detailDictionary = consequence.getDetail();

        if (detailDictionary == null || detailDictionary.isEmpty()) {
            throw new CampaignMessageRequiredFieldMissingException("Unable to create fullscreen message, message detail is missing or not an object.");
        }

        // html is required
        html = DataReader.optString(detailDictionary, CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_HTML, "");

        if (StringUtils.isNullOrEmpty(html)) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                    "parseFullScreenMessagePayload -  Unable to create fullscreen message, html is missing/empty.");
            throw new CampaignMessageRequiredFieldMissingException("Messages - Unable to create fullscreen message, html is missing/empty.");
        }

        // remote assets are optional
        final List<List<String>> assetsList = (List<List<String>>) detailDictionary.get(CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_DETAIL_KEY_REMOTE_ASSETS);
        if (assetsList != null && !assetsList.isEmpty()) {
            for (final List<String> assets : assetsList) {
                extractAssets(assets);
            }
        } else {
            Log.trace(CampaignConstants.LOG_TAG, SELF_TAG,
                    "parseFullScreenMessagePayload -  Tried to read \"assets\" for fullscreen message but none found.  This is not a required field.");
        }

        // store message id for retrieving cached assets
        messageId = consequence.getId();
    }

    /**
     * Extract assets for the HTML message.
     *
     * @param assets A {@code List} of {@code String}s containing assets specific for this fullscreen message.
     */
    private void extractAssets(final List<String> assets) {
        if (assets == null || assets.isEmpty()) {
            Log.trace(CampaignConstants.LOG_TAG, SELF_TAG, "extractAssets - There are no assets to extract.");
            return;
        }
        final List<String> foundAssets = new ArrayList<>();
        for (final String asset : assets) {
            foundAssets.add(asset);
        }
        Log.trace(CampaignConstants.LOG_TAG, SELF_TAG, "extractAssets - Adding %s to extracted assets.", foundAssets);
        this.assets.add(foundAssets);
    }

    /**
     * Creates and shows a new {@link FullscreenMessage} object and registers a {@link FullScreenMessageUiListener}
     * instance with the {@code UIService} to receive message interaction events.
     * <p>
     * This method reads the {@link #htmlContent} from the cached html at {@link #assets} and generates a map containing the asset url and
     * it's cached file location. The asset map is set in the created {@code FullscreenMessage} before invoking the method {@link FullscreenMessage#show()} to display
     * the fullscreen in-app message.
     *
     * @see #createCachedResourcesMap()
     * @see UIService#createFullscreenMessage(String, FullscreenMessageDelegate, boolean, MessageSettings)
     */
    @Override
    void showMessage() {
        Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "showMessage - Attempting to show fullscreen message with ID %s", messageId);
        if (uiService == null) {
            Log.warning(CampaignConstants.LOG_TAG, SELF_TAG,
                    "showMessage - UI Service is unavailable. Unable to show fullscreen message with ID (%s)",
                    messageId);
            return;
        }

        if (cacheService == null) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                    "showMessage - No cache service found, to show fullscreen message with ID %s", messageId);
            return;
        }

        final CacheResult cacheResult = cacheService.get(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER, html);
        if (cacheResult == null) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                    "showMessage - Unable to find cached html content for fullscreen message with ID %s", messageId);
            return;
        }
        htmlContent = StreamUtils.readAsString(cacheResult.getData());

        if (StringUtils.isNullOrEmpty(htmlContent)) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                    "showMessage -  No html content in file (%s). File is missing or invalid!", html);
            return;
        }

        final Map<String, String> cachedResourcesMap = createCachedResourcesMap();

        final FullScreenMessageUiListener fullScreenMessageUiListener = new FullScreenMessageUiListener();
        final MessageSettings messageSettings = new MessageSettings();
        // ACS fullscreen messages are displayed at 100% scale
        messageSettings.setHeight(FILL_DEVICE_DISPLAY);
        messageSettings.setWidth(FILL_DEVICE_DISPLAY);
        messageSettings.setParent(this);
        messageSettings.setVerticalAlign(MessageSettings.MessageAlignment.TOP);
        messageSettings.setHorizontalAlign(MessageSettings.MessageAlignment.CENTER);
        messageSettings.setDisplayAnimation(MessageSettings.MessageAnimation.BOTTOM);
        messageSettings.setDismissAnimation(MessageSettings.MessageAnimation.BOTTOM);
        messageSettings.setBackdropColor("#FFFFFF"); // html code for white
        messageSettings.setBackdropOpacity(0.0f);
        messageSettings.setUiTakeover(true);
        final FullscreenMessage fullscreenMessage = uiService.createFullscreenMessage(htmlContent,
                fullScreenMessageUiListener, !cachedResourcesMap.isEmpty(), messageSettings);


        if (fullscreenMessage != null) {
            fullscreenMessage.setLocalAssetsMap(cachedResourcesMap);
            fullscreenMessage.show();
        }
    }

    /**
     * Determines whether this class has downloadable assets.
     *
     * @return true as this class has downloadable assets
     */
    @Override
    boolean shouldDownloadAssets() {
        return true;
    }

    /**
     * Returns a {@code Map<String,String>} containing the remote resource URL as key and cached resource path as value for a cached remote resource.
     * <p>
     * This function uses the {@link CacheService} to find a cached remote file. if a cached file is found, its added to the {@code Map<String, String>} that will be returned.
     * </p>
     * This functions returns an empty map in the following cases:
     * <ul>
     * <li>The Asset List is empty.</li>
     * <li>The {@link CacheService} is null.</li>
     * </ul>
     *
     * @return {@code Map<String, String>}
     */
    private Map<String, String> createCachedResourcesMap() {
        // early bail if we don't have assets or if cache service is unavailable
        if (assets == null || assets.isEmpty()) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "createCachedResourcesMap - No cached assets found, cannot expand URLs in the HTML.");
            return Collections.emptyMap();
        }

        if (cacheService == null) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                    "createCachedResourcesMap - No cache service found, cannot generate local resource mapping.");
            return Collections.emptyMap();
        }

        final Map<String, String> cachedImagesMap = new HashMap<>();
        final Map<String, String> fallbackImagesMap = new HashMap<>();

        for (final List<String> currentAssetArray : assets) {
            if (currentAssetArray.isEmpty()) {
                continue;
            }

            final String assetUrl = currentAssetArray.get(0);
            final int currentAssetArrayCount = currentAssetArray.size();
            String assetValue = null;
            int currentAssetNumber = 0;

            // loop through our assets to see if we have any of them in cache
            while (currentAssetNumber < currentAssetArrayCount) {
                final String currentAsset = currentAssetArray.get(currentAssetNumber);
                final CacheResult assetValueFile = cacheService.get(MESSAGES_CACHE + messageId, currentAsset);

                if (assetValueFile != null) {
                    assetValue = assetValueFile.getMetadata().get(CampaignConstants.METADATA_PATH);
                    break;
                }

                currentAssetNumber++;
            }

            // if assetValue is still null, none of our urls have been cached, so we check for a bundled asset
            if (StringUtils.isNullOrEmpty(assetValue)) {
                assetValue = currentAssetArray.get(currentAssetArrayCount - 1);
                boolean isLocalImage = !UrlUtils.isValidUrl(assetValue);

                if (isLocalImage) {
                    fallbackImagesMap.put(assetUrl, assetValue);
                }
            } else {
                cachedImagesMap.put(assetUrl, assetValue);
            }
        }
        cachedImagesMap.putAll(fallbackImagesMap);

        return cachedImagesMap;
    }

    /**
     * Attempts to handle {@code Fullscreen} message interaction by inspecting the {@code id} field on the clicked message.
     * <p>
     * The method looks for {@code id} field in the provided {@code query} Map and invokes method on the parent {@link CampaignMessage}
     * class to dispatch message click-through or viewed event. The {@code id} field is a {@code String} in the form
     * {@literal {broadlogId},{deliveryId},{tagId}}, where {@code tagId} can assume values 3,4 or 5.
     * <p>
     * If the {@code id} field is missing in the provided {@code query} or if it cannot be parsed to extract a valid {@code tagId}
     * then no message interaction event shall be dispatched.
     *
     * @param query {@code Map<String, String>} query containing message interaction details
     * @see #clickedWithData(Map)
     */
    private void processMessageInteraction(final Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                    "processMessageInteraction -  Cannot process message interaction, input query is null or empty.");
            return;
        }

        if (query.containsKey(CampaignConstants.MESSAGE_DATA_TAG_ID)) {
            final String id = query.get(CampaignConstants.MESSAGE_DATA_TAG_ID);
            final String[] strTokens = id.split(CampaignConstants.MESSAGE_DATA_TAG_ID_DELIMITER);

            if (strTokens.length != CampaignConstants.MESSAGE_DATA_ID_TOKENS_LEN) {
                Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                        "processMessageInteraction -  Cannot process message interaction, input query contains insufficient id tokens.");
                return;
            }

            int tagId = 0;

            try {
                tagId = Integer.parseInt(strTokens[2]);
            } catch (NumberFormatException e) {
                Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                        "processMessageInteraction -  Cannot parse tag Id from the id field in given query (%s).", e);
            }

            switch (tagId) {
                case CampaignConstants.MESSAGE_DATA_TAG_ID_BUTTON_1: // adbinapp://confirm/?id=h11901a,86f10d,3
                case CampaignConstants.MESSAGE_DATA_TAG_ID_BUTTON_2: // adbinapp://confirm/?id=h11901a,86f10d,4
                case CampaignConstants.MESSAGE_DATA_TAG_ID_BUTTON_X: // adbinapp://cancel?id=h11901a,86f10d,5
                    clickedWithData(query);
                    viewed();
                    break;

                default:
                    Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                            "processMessageInteraction -  Unsupported tag Id found in the id field in the given query (%s).", tagId);
                    break;

            }
        }
    }

    /**
     * Added for unit testing.
     *
     * @return the {@code List<List<String>>} of assets.
     */
    @VisibleForTesting
    List<List<String>> getAssetsList() {
        return assets;
    }

    class FullScreenMessageUiListener implements FullscreenMessageDelegate {
        /**
         * Invoked when a {@code UIFullScreenMessage} is displayed.
         * <p>
         * Triggers a call to parent method {@link CampaignMessage#triggered()}.
         *
         * @param message the {@link FullscreenMessage} being displayed
         */
        @Override
        public void onShow(final FullscreenMessage message) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "Fullscreen on show callback received.");
            triggered();
        }

        /**
         * Invoked when a {@code UIFullScreenMessage} is dismissed.
         * <p>
         * Triggers a call to parent method {@link CampaignMessage#viewed()}.
         *
         * @param message the {@link FullscreenMessage} being dismissed
         */
        @Override
        public void onDismiss(final FullscreenMessage message) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "Fullscreen on dismiss callback received.");
            viewed();
        }

        /**
         * Invoked when a {@code FullscreenMessage} is attempting to load a URL.
         * <p>
         * The provided {@code urlString} can be in one of the following forms:
         * <ul>
         *     <li>{@literal adbinapp://confirm?id={broadlogId},{deliveryId},3&url={clickThroughUrl}}</li>
         *     <li>{@literal adbinapp://confirm?id={broadlogId},{deliveryId},4}</li>
         *     <li>{@literal adbinapp://cancel?id={broadlogId},{deliveryId},5}</li>
         * </ul>
         * Returns false if the scheme of the given {@code urlString} is not equal to {@value CampaignConstants#MESSAGE_SCHEME},
         * or if the host is not one of {@value CampaignConstants#MESSAGE_SCHEME_PATH_CONFIRM} or
         * {@value CampaignConstants#MESSAGE_SCHEME_PATH_CANCEL}.
         * <p>
         * Extracts the host and query information from the provided {@code urlString} in a {@code Map<String, String>} and
         * passes it to the {@link CampaignMessage} class to dispatch a message click-through or viewed event.
         *
         * @param message   the {@link FullscreenMessage} instance
         * @param urlString {@link String} containing the URL being loaded by the {@code CampaignMessage}
         * @return true if the SDK wants to handle the URL
         * @see #processMessageInteraction(Map)
         */
        @Override
        public boolean overrideUrlLoad(final FullscreenMessage message, final String urlString) {

            Log.trace(CampaignConstants.LOG_TAG, "Fullscreen overrideUrlLoad callback received with url (%s)", urlString);

            if (StringUtils.isNullOrEmpty(urlString)) {
                Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "Cannot process provided URL string, it is null or empty.");
                return true;
            }

            URI uri;

            try {
                uri = new URI(urlString);

            } catch (URISyntaxException ex) {
                Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "overrideUrlLoad -  Invalid message URI found (%s).", urlString);
                return true;
            }

            // check adbinapp scheme
            final String messageScheme = uri.getScheme();

            if (!messageScheme.equals(CampaignConstants.MESSAGE_SCHEME)) {
                Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "overrideUrlLoad -  Invalid message scheme found in URI. (%s)", urlString);
                return false;
            }

            // cancel or confirm
            final String host = uri.getHost();

            if (!host.equals(CampaignConstants.MESSAGE_SCHEME_PATH_CONFIRM) &&
                    !host.equals(CampaignConstants.MESSAGE_SCHEME_PATH_CANCEL)) {
                Log.debug(CampaignConstants.LOG_TAG, SELF_TAG,
                        "overrideUrlLoad -  Unsupported URI host found, neither \"confirm\" nor \"cancel\". (%s)", urlString);
                return false;
            }

            // extract query, eg: id=h11901a,86f10d,3&url=https://www.adobe.com
            final String query = uri.getRawQuery();

            // Populate message data
            final Map<String, String> messageData = Utils.extractQueryParameters(query);

            if (messageData != null && !messageData.isEmpty()) {
                messageData.put(CampaignConstants.CAMPAIGN_INTERACTION_TYPE, host);

                // handle message interaction
                processMessageInteraction(messageData);
            }

            if (message != null) {
                message.dismiss();
            }

            return true;
        }

        @Override
        public void onShowFailure() {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "onShowFailure -  Fullscreen message failed to show.");
        }
    }
}