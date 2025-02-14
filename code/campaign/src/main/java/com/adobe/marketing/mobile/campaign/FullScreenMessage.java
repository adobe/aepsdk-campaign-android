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

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.adobe.marketing.mobile.launch.rulesengine.RuleConsequence;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheEntry;
import com.adobe.marketing.mobile.services.caching.CacheExpiry;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.services.ui.InAppMessage;
import com.adobe.marketing.mobile.services.ui.Presentable;
import com.adobe.marketing.mobile.services.ui.Presentation;
import com.adobe.marketing.mobile.services.ui.PresentationError;
import com.adobe.marketing.mobile.services.ui.PresentationUtilityProvider;
import com.adobe.marketing.mobile.services.ui.UIService;
import com.adobe.marketing.mobile.services.ui.message.InAppMessageEventListener;
import com.adobe.marketing.mobile.services.ui.message.InAppMessageSettings;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.DefaultPresentationUtilityProvider;
import com.adobe.marketing.mobile.util.StreamUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.util.UrlUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides implementation logic for handling events related to full screen in-app messages.
 *
 * <p>The actual UI implementation happens in the platform services.
 */
class FullScreenMessage extends CampaignMessage {
    private final String SELF_TAG = "FullScreenMessage";
    private static final int FILL_DEVICE_DISPLAY = 100;
    private final String MESSAGES_CACHE =
            CampaignConstants.CACHE_BASE_DIR
                    + File.separator
                    + CampaignConstants.MESSAGE_CACHE_DIR
                    + File.separator;
    private final CacheService cacheService;
    private final UIService uiService;

    private String html;
    private String htmlContent;
    private String messageId;
    private final List<List<String>> assets = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param extension {@link CampaignExtension} that is this parent
     * @param consequence {@link RuleConsequence} instance containing a message-defining payload
     * @throws CampaignMessageRequiredFieldMissingException if {@code consequence} is null or if any
     *     required field for a {@link FullScreenMessage} is null or empty
     */
    FullScreenMessage(final CampaignExtension extension, final RuleConsequence consequence)
            throws CampaignMessageRequiredFieldMissingException {
        super(extension, consequence);
        cacheService = ServiceProvider.getInstance().getCacheService();
        uiService = ServiceProvider.getInstance().getUIService();
        parseFullScreenMessagePayload(consequence);
    }

    /**
     * Parses a {@code CampaignRuleConsequence} instance defining message payload for a {@code
     * FullScreenMessage} object.
     *
     * <p>Required fields:
     *
     * <ul>
     *   <li>{@code html} - {@link String} containing html for this message
     * </ul>
     *
     * Optional fields:
     *
     * <ul>
     *   <li>{@code assets} - {@code Array} of {@code String[]}s containing remote assets to
     *       prefetch and cache
     * </ul>
     *
     * @param consequence {@code CampaignRuleConsequence} instance containing the message payload to
     *     be parsed
     * @throws CampaignMessageRequiredFieldMissingException if any of the required fields are
     *     missing from {@code consequence}
     */
    @SuppressWarnings("unchecked")
    private void parseFullScreenMessagePayload(final RuleConsequence consequence)
            throws CampaignMessageRequiredFieldMissingException {
        if (consequence == null) {
            throw new CampaignMessageRequiredFieldMissingException("Message consequence is null.");
        }

        final Map<String, Object> detailDictionary = consequence.getDetail();

        if (detailDictionary == null || detailDictionary.isEmpty()) {
            throw new CampaignMessageRequiredFieldMissingException(
                    "Unable to create fullscreen message, message detail is missing or not an"
                            + " object.");
        }

        // html is required
        html =
                DataReader.optString(
                        detailDictionary,
                        CampaignConstants.EventDataKeys.RuleEngine
                                .MESSAGE_CONSEQUENCE_DETAIL_KEY_HTML,
                        "");

        if (StringUtils.isNullOrEmpty(html)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "parseFullScreenMessagePayload -  Unable to create fullscreen message, html is"
                            + " missing/empty.");
            throw new CampaignMessageRequiredFieldMissingException(
                    "Messages - Unable to create fullscreen message, html is missing/empty.");
        }

        // remote assets are optional
        final List<List<String>> assetsList =
                (List<List<String>>)
                        detailDictionary.get(
                                CampaignConstants.EventDataKeys.RuleEngine
                                        .MESSAGE_CONSEQUENCE_DETAIL_KEY_REMOTE_ASSETS);
        if (assetsList != null && !assetsList.isEmpty()) {
            for (final List<String> assets : assetsList) {
                extractAssets(assets);
            }
        } else {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "parseFullScreenMessagePayload -  Tried to read \"assets\" for fullscreen"
                            + " message but none found.  This is not a required field.");
        }

        // store message id for retrieving cached assets
        messageId = consequence.getId();
    }

    /**
     * Extract assets for the HTML message.
     *
     * @param assets A {@code List} of {@code String}s containing assets specific for this
     *     fullscreen message.
     */
    private void extractAssets(final List<String> assets) {
        if (assets == null || assets.isEmpty()) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "extractAssets - There are no assets to extract.");
            return;
        }
        final List<String> foundAssets = new ArrayList<>();
        for (final String asset : assets) {
            foundAssets.add(asset);
        }
        Log.trace(
                CampaignConstants.LOG_TAG,
                SELF_TAG,
                "extractAssets - Adding %s to extracted assets.",
                foundAssets);
        this.assets.add(foundAssets);
    }

    /**
     * Creates and shows a new {@link Presentable<InAppMessage>} object and registers a {@link
     * FullScreenMessageUiListener} instance with the {@code UIService} to receive message
     * interaction events.
     *
     * <p>This method reads the {@link #htmlContent} from the cached html at {@link #assets} and
     * generates a map containing the asset url and it's cached file location. The asset map is set
     * when creating the {@code FullscreenMessage} before invoking the method {@link
     * Presentable<InAppMessage>#show()} to display the fullscreen in-app message.
     *
     * @see #createCachedResourcesMap()
     * @see UIService#create(Presentation, PresentationUtilityProvider)
     */
    @Override
    void showMessage() {
        Log.debug(
                CampaignConstants.LOG_TAG,
                SELF_TAG,
                "showMessage - Attempting to show fullscreen message with ID %s",
                messageId);
        if (uiService == null) {
            Log.warning(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "showMessage - UI Service is unavailable. Unable to show fullscreen message"
                            + " with ID (%s)",
                    messageId);
            return;
        }

        if (cacheService == null) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "showMessage - No cache service found, to show fullscreen message with ID %s",
                    messageId);
            return;
        }

        final CacheResult cacheResult =
                cacheService.get(
                        CampaignConstants.CACHE_BASE_DIR
                                + File.separator
                                + CampaignConstants.RULES_CACHE_FOLDER,
                        html);
        if (cacheResult == null) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "showMessage - Unable to find cached html content for fullscreen message with"
                            + " ID %s",
                    messageId);
            return;
        }
        htmlContent = StreamUtils.readAsString(cacheResult.getData());

        if (StringUtils.isNullOrEmpty(htmlContent)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "showMessage -  No html content in file (%s). File is missing or invalid!",
                    html);
            return;
        }

        final Map<String, String> cachedResourcesMap = createCachedResourcesMap();

        final FullScreenMessageUiListener fullScreenMessageUiListener =
                new FullScreenMessageUiListener();
        // ACS fullscreen messages are displayed at 100% scale
        final InAppMessageSettings messageSettings =
                new InAppMessageSettings.Builder()
                        .content(htmlContent)
                        .height(FILL_DEVICE_DISPLAY)
                        .width(FILL_DEVICE_DISPLAY)
                        .verticalAlignment(InAppMessageSettings.MessageAlignment.TOP)
                        .horizontalAlignment(InAppMessageSettings.MessageAlignment.CENTER)
                        .displayAnimation(InAppMessageSettings.MessageAnimation.BOTTOM)
                        .dismissAnimation(InAppMessageSettings.MessageAnimation.BOTTOM)
                        .backgroundColor("#FFFFFF")
                        .backdropOpacity(0.0f)
                        .assetMap(cachedResourcesMap)
                        .shouldTakeOverUi(true)
                        .build();

        final Presentable<InAppMessage> fullscreenMessage =
                uiService.create(
                        new InAppMessage(messageSettings, fullScreenMessageUiListener),
                        new DefaultPresentationUtilityProvider());
        fullscreenMessage.show();
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
     * Returns a {@code Map<String,String>} containing the remote resource URL as key and cached
     * resource path as value for a cached remote resource.
     *
     * <p>This function uses the {@link CacheService} to find a cached remote file. if a cached file
     * is found, its added to the {@code Map<String, String>} that will be returned. This functions
     * returns an empty map in the following cases:
     *
     * <ul>
     *   <li>The Asset List is empty.
     *   <li>The {@link CacheService} is null.
     * </ul>
     *
     * @return {@code Map<String, String>}
     */
    private Map<String, String> createCachedResourcesMap() {
        // early bail if we don't have assets or if cache service is unavailable
        if (assets == null || assets.isEmpty()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "createCachedResourcesMap - No valid remote asset list found for message with"
                            + " id %s.",
                    messageId);
            return Collections.emptyMap();
        }

        final Map<String, String> cachedImagesMap = new HashMap<>();
        final Map<String, String> fallbackImagesMap = new HashMap<>();
        final String cacheName = MESSAGES_CACHE + messageId;

        for (final List<String> currentAssetArray : assets) {
            if (currentAssetArray.isEmpty()) {
                continue;
            }

            final String remoteAssetUrl = currentAssetArray.get(0);
            final int currentAssetArrayCount = currentAssetArray.size();
            String assetCacheLocation = null;
            int currentAssetNumber = 0;

            // loop through our assets to see if we have any of them in cache
            while (currentAssetNumber < currentAssetArrayCount) {
                final String currentAsset = currentAssetArray.get(currentAssetNumber);
                final CacheResult assetValueFile = cacheService.get(cacheName, currentAsset);

                if (assetValueFile != null) {
                    assetCacheLocation = cacheName;
                    break;
                }

                currentAssetNumber++;
            }

            // if the asset cache location is still null then the specified remote url was not
            // present in the cache.
            // if a bundled asset is available, use it as a fallback by caching it for use later
            // when the message is displayed.
            if (StringUtils.isNullOrEmpty(assetCacheLocation)) {
                final String bundledFileName = currentAssetArray.get(currentAssetArrayCount - 1);
                boolean isLocalImage = !UrlUtils.isValidUrl(bundledFileName);

                if (isLocalImage) {
                    try {
                        final Context applicationContext =
                                ServiceProvider.getInstance()
                                        .getAppContextService()
                                        .getApplicationContext();
                        if (applicationContext != null) {
                            final InputStream bundledFile =
                                    applicationContext.getAssets().open(bundledFileName);
                            // if the remote asset url is a non valid url then we have a local asset
                            // which needs the local file system uri appended
                            final String finalizedAssetUrl =
                                    !UrlUtils.isValidUrl(remoteAssetUrl)
                                            ? CampaignConstants.LOCAL_ASSET_URI + remoteAssetUrl
                                            : remoteAssetUrl;
                            cacheService.set(
                                    cacheName,
                                    finalizedAssetUrl,
                                    new CacheEntry(bundledFile, CacheExpiry.never(), null));
                            fallbackImagesMap.put(finalizedAssetUrl, cacheName);
                            bundledFile.close();
                        }
                    } catch (final IOException exception) {
                        Log.debug(
                                CampaignConstants.LOG_TAG,
                                SELF_TAG,
                                "createCachedResourcesMap - Exception occurred reading bundled"
                                        + " asset: %s.",
                                exception.getMessage());
                    }
                }
            } else {
                cachedImagesMap.put(remoteAssetUrl, assetCacheLocation);
            }
        }
        cachedImagesMap.putAll(fallbackImagesMap);

        return cachedImagesMap;
    }

    /**
     * Attempts to handle {@code Fullscreen} message interaction by inspecting the {@code id} field
     * on the clicked message.
     *
     * <p>The method looks for {@code id} field in the provided {@code query} Map and invokes method
     * on the parent {@link CampaignMessage} class to dispatch message click-through or viewed
     * event. The {@code id} field is a {@code String} in the form {@literal
     * {broadlogId},{deliveryId},{tagId}}, where {@code tagId} can assume values 3,4 or 5.
     *
     * <p>If the {@code id} field is missing in the provided {@code query} or if it cannot be parsed
     * to extract a valid {@code tagId} then no message interaction event shall be dispatched.
     *
     * @param query {@code Map<String, String>} query containing message interaction details
     * @see #clickedWithData(Map)
     */
    private void processMessageInteraction(final Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processMessageInteraction -  Cannot process message interaction, input query"
                            + " is null or empty.");
            return;
        }

        if (query.containsKey(CampaignConstants.MESSAGE_DATA_TAG_ID)) {
            final String id = query.get(CampaignConstants.MESSAGE_DATA_TAG_ID);
            final String[] strTokens = id.split(CampaignConstants.MESSAGE_DATA_TAG_ID_DELIMITER);

            if (strTokens.length != CampaignConstants.MESSAGE_DATA_ID_TOKENS_LEN) {
                Log.debug(
                        CampaignConstants.LOG_TAG,
                        SELF_TAG,
                        "processMessageInteraction -  Cannot process message interaction, input"
                                + " query contains insufficient id tokens.");
                return;
            }

            int tagId = 0;

            try {
                tagId = Integer.parseInt(strTokens[2]);
            } catch (NumberFormatException e) {
                Log.debug(
                        CampaignConstants.LOG_TAG,
                        SELF_TAG,
                        "processMessageInteraction -  Cannot parse tag Id from the id field in"
                                + " given query (%s).",
                        e);
            }

            switch (tagId) {
                case CampaignConstants
                        .MESSAGE_DATA_TAG_ID_BUTTON_1: // adbinapp://confirm/?id=h11901a,86f10d,3
                case CampaignConstants
                        .MESSAGE_DATA_TAG_ID_BUTTON_2: // adbinapp://confirm/?id=h11901a,86f10d,4
                case CampaignConstants
                        .MESSAGE_DATA_TAG_ID_BUTTON_X: // adbinapp://cancel?id=h11901a,86f10d,5
                    clickedWithData(query);
                    viewed();
                    break;

                default:
                    Log.debug(
                            CampaignConstants.LOG_TAG,
                            SELF_TAG,
                            "processMessageInteraction -  Unsupported tag Id found in the id field"
                                    + " in the given query (%s).",
                            tagId);
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

    class FullScreenMessageUiListener implements InAppMessageEventListener {
        /**
         * Invoked when a {@code UIFullScreenMessage} is displayed.
         *
         * <p>Triggers a call to parent method {@link CampaignMessage#triggered()}.
         *
         * @param presentable the {@link Presentable<InAppMessage>} being displayed
         */
        @Override
        public void onShow(final @NonNull Presentable<InAppMessage> presentable) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "Fullscreen on show callback received.");
            triggered();
        }

        /**
         * Invoked when a {@code UIFullScreenMessage} is dismissed.
         *
         * <p>Triggers a call to parent method {@link CampaignMessage#viewed()}.
         *
         * @param presentable the {@link Presentable<InAppMessage>} being dismissed
         */
        @Override
        public void onDismiss(final @NonNull Presentable<InAppMessage> presentable) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "Fullscreen on dismiss callback received.");
            viewed();
        }

        @Override
        public void onError(
                final @NonNull Presentable<InAppMessage> presentable,
                final @NonNull PresentationError presentationError) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "onShowFailure -  Fullscreen message failed to show.");
        }

        @Override
        public void onHide(final @NonNull Presentable<InAppMessage> presentable) {}

        @Override
        public void onBackPressed(final @NonNull Presentable<InAppMessage> presentable) {}

        /**
         * Invoked when a {@code FullscreenMessage} is attempting to load a URL.
         *
         * <p>The provided {@code urlString} can be in one of the following forms:
         *
         * <ul>
         *   <li>{@literal adbinapp://confirm?id={broadlogId},{deliveryId},3&url={clickThroughUrl}}
         *   <li>{@literal adbinapp://confirm?id={broadlogId},{deliveryId},4}
         *   <li>{@literal adbinapp://cancel?id={broadlogId},{deliveryId},5}
         * </ul>
         *
         * Returns false if the scheme of the given {@code urlString} is not equal to {@value
         * CampaignConstants#MESSAGE_SCHEME}, or if the host is not one of {@value
         * CampaignConstants#MESSAGE_SCHEME_PATH_CONFIRM} or {@value
         * CampaignConstants#MESSAGE_SCHEME_PATH_CANCEL}.
         *
         * <p>Extracts the host and query information from the provided {@code urlString} in a
         * {@code Map<String, String>} and passes it to the {@link CampaignMessage} class to
         * dispatch a message click-through or viewed event.
         *
         * @param inAppMessagePresentable the {@link Presentable<InAppMessage>} instance
         * @param urlString {@link String} containing the URL being loaded by the {@code
         *     CampaignMessage}
         * @return true if the SDK wants to handle the URL
         * @see #processMessageInteraction(Map)
         */
        @Override
        public boolean onUrlLoading(
                final @NonNull Presentable<InAppMessage> inAppMessagePresentable,
                final @NonNull String urlString) {

            Log.trace(
                    CampaignConstants.LOG_TAG,
                    "Fullscreen overrideUrlLoad callback received with url (%s)",
                    urlString);

            if (StringUtils.isNullOrEmpty(urlString)) {
                Log.debug(
                        CampaignConstants.LOG_TAG,
                        SELF_TAG,
                        "Cannot process provided URL string, it is null or empty.");
                return true;
            }

            URI uri;

            try {
                uri = new URI(urlString);

            } catch (URISyntaxException ex) {
                Log.debug(
                        CampaignConstants.LOG_TAG,
                        SELF_TAG,
                        "overrideUrlLoad -  Invalid message URI found (%s).",
                        urlString);
                return true;
            }

            // check adbinapp scheme
            final String messageScheme = uri.getScheme();

            if (!messageScheme.equals(CampaignConstants.MESSAGE_SCHEME)) {
                Log.debug(
                        CampaignConstants.LOG_TAG,
                        SELF_TAG,
                        "overrideUrlLoad -  Invalid message scheme found in URI. (%s)",
                        urlString);
                return false;
            }

            // cancel or confirm
            final String host = uri.getHost();

            if (!host.equals(CampaignConstants.MESSAGE_SCHEME_PATH_CONFIRM)
                    && !host.equals(CampaignConstants.MESSAGE_SCHEME_PATH_CANCEL)) {
                Log.debug(
                        CampaignConstants.LOG_TAG,
                        SELF_TAG,
                        "overrideUrlLoad -  Unsupported URI host found, neither \"confirm\" nor"
                                + " \"cancel\". (%s)",
                        urlString);
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

            inAppMessagePresentable.dismiss();
            return true;
        }
    }
}
