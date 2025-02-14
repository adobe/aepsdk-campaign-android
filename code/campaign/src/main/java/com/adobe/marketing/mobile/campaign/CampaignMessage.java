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
import com.adobe.marketing.mobile.services.uri.UriOpening;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.StringUtils;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * The {@code CampaignMessage} class serves as the base class for any implementation of an in-app
 * message type.
 */
abstract class CampaignMessage {
    private static final String SELF_TAG = "CampaignMessage";
    private static final Map<String, Class> messageTypeDictionary = new HashMap<>();

    static {
        messageTypeDictionary.put(
                CampaignConstants.MESSAGE_TEMPLATE_FULLSCREEN, FullScreenMessage.class);
        messageTypeDictionary.put(CampaignConstants.MESSAGE_TEMPLATE_ALERT, AlertMessage.class);
        messageTypeDictionary.put(
                CampaignConstants.MESSAGE_TEMPLATE_LOCAL_NOTIFICATION,
                LocalNotificationMessage.class);
    }

    protected final String messageId;
    // package-private members
    final CampaignExtension parentModule;

    /**
     * Constructor.
     *
     * <p>Every {@link CampaignMessage} requires a {@link #messageId}, and must be of type {@value
     * CampaignConstants#MESSAGE_CONSEQUENCE_MESSAGE_TYPE}.
     *
     * <p>The {@code consequence} parameter for a {@code CampaignMessage} is required to have valid
     * values for the following fields:
     *
     * <ul>
     *   <li>{@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_ID} - {@link
     *       String} containing the message ID
     *   <li>{@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_TYPE} - {@code
     *       String} containing the consequence type
     *   <li>{@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_DETAIL} - {@code
     *       Map<String, Variant>} containing details of the Message
     * </ul>
     *
     * @param extension {@link CampaignExtension} instance that is the parent of this {@code
     *     CampaignMessage}
     * @param consequence {@link RuleConsequence} containing a {@code CampaignMessage}-defining
     *     payload
     * @throws CampaignMessageRequiredFieldMissingException if {@code consequence} is null or if it
     *     does not contain a valid {@code id}, {@code type}, or {@code detail}
     */
    protected CampaignMessage(final CampaignExtension extension, final RuleConsequence consequence)
            throws CampaignMessageRequiredFieldMissingException {
        parentModule = extension;

        if (consequence == null) {
            throw new CampaignMessageRequiredFieldMissingException("Consequence cannot be null!");
        }

        messageId = consequence.getId();

        if (StringUtils.isNullOrEmpty(messageId)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "Invalid consequence. Required field \"id\" is null or empty.");
            throw new CampaignMessageRequiredFieldMissingException(
                    "Required field: Message \"id\" is null or empty.");
        }

        final String consequenceType = consequence.getType();

        if (!CampaignConstants.MESSAGE_CONSEQUENCE_MESSAGE_TYPE.equals(consequenceType)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "Invalid consequence. Required field \"type\" is (%s) should be of type (iam).",
                    consequenceType);
            throw new CampaignMessageRequiredFieldMissingException(
                    "Required field: \"type\" is not equal to \"iam\".");
        }

        final Map<String, Object> details = consequence.getDetail();

        if (details == null || details.isEmpty()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "Invalid consequence. Required field \"detail\" is null or empty.");
            throw new CampaignMessageRequiredFieldMissingException(
                    "Required field: \"detail\" is null or empty.");
        }
    }

    /**
     * Static method that should be used to create an instance of any {@code CampaignMessage}
     * subclass.
     *
     * <p>Verifies that the {@code consequence} parameter contains a valid {@code CampaignMessage}
     * definition payload. If it does, this method will detect and call the proper constructor for
     * the corresponding {@link CampaignMessage} subclass.
     *
     * <p>At this stage in {@code CampaignMessage} initialization, the only required JSON field is
     * {@value CampaignConstants.EventDataKeys.RuleEngine#MESSAGE_CONSEQUENCE_DETAIL_KEY_TEMPLATE}.
     * If this value is missing, null, or empty, a {@link
     * CampaignMessageRequiredFieldMissingException} will be thrown.
     *
     * @param extension {@link CampaignExtension} instance that is the parent of this {@code
     *     CampaignMessage}
     * @param consequence {@link RuleConsequence} instance containing a {@code
     *     CampaignMessage}-defining payload
     * @return {@code CampaignMessage} that has been initialized using its proper constructor
     * @throws CampaignMessageRequiredFieldMissingException if {@code consequence} is null or if any
     *     required field for a {@code CampaignMessage} is null or empty
     */
    @SuppressWarnings("unchecked")
    // reflective access to the proper message constructor requires this access
    static CampaignMessage createMessageObject(
            final CampaignExtension extension, final RuleConsequence consequence)
            throws CampaignMessageRequiredFieldMissingException {
        // fast fail
        if (consequence == null) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "createMessageObject -  No message consequence found. Unable to proceed.");
            throw new CampaignMessageRequiredFieldMissingException("Message consequence is null.");
        }

        final Map<String, Object> detailObject = consequence.getDetail();

        // template is required
        final String template =
                DataReader.optString(
                        detailObject,
                        CampaignConstants.EventDataKeys.RuleEngine
                                .MESSAGE_CONSEQUENCE_DETAIL_KEY_TEMPLATE,
                        "");

        if (StringUtils.isNullOrEmpty(template)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "createMessageObject -  No message template found. Unable to proceed.");
            throw new CampaignMessageRequiredFieldMissingException(
                    "Required message field: Message template is null or empty.");
        }

        final Class messageClass = messageTypeDictionary.get(template);

        if (messageClass == null) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "createMessageObject -  Provided message type is not supported. Unable to"
                            + " proceed.");
            return null;
        }

        CampaignMessage msgObject = null;

        try {
            msgObject =
                    (CampaignMessage)
                            messageClass
                                    .getDeclaredConstructor(
                                            CampaignExtension.class, RuleConsequence.class)
                                    .newInstance(extension, consequence);
        } catch (final IllegalAccessException e) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "createMessageObject -  Caught IllegalAccessException exception while trying to"
                            + " instantiate Message object. \n"
                            + " (%s)",
                    e);
        } catch (final InstantiationException e) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "createMessageObject -  Caught InstantiationException exception while trying to"
                            + " instantiate Message object.\n"
                            + " (%s)",
                    e);
        } catch (final NoSuchMethodException e) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "createMessageObject -  Caught NoSuchMethodException exception while trying to"
                            + " instantiate Message object. \n"
                            + " (%s)",
                    e);
        } catch (final InvocationTargetException e) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "createMessageObject -  Caught InvocationTargetException exception while trying"
                            + " to instantiate Message object. \n"
                            + " (%s)",
                    e);
        }

        return msgObject;
    }

    /**
     * Abstract method to be overridden by child {@code CampaignMessage} class to display the
     * message.
     */
    abstract void showMessage();

    /**
     * Generates a {@code Map} with message data for a "message triggered" event and passes it to
     * the parent {@code CampaignExtension} for dispatch.
     *
     * @see CampaignExtension#dispatchMessageInteraction(Map)
     */
    protected void triggered() {
        final HashMap<String, Object> msgData = new HashMap<>();
        msgData.put(CampaignConstants.ContextDataKeys.MESSAGE_ID, messageId);
        msgData.put(CampaignConstants.ContextDataKeys.MESSAGE_TRIGGERED, String.valueOf(1));
        callDispatchMessageInteraction(msgData);
    }

    /**
     * Generates a {@code Map} with message data for a "message viewed" event and passes it to the
     * parent {@code CampaignExtension} for dispatch.
     *
     * @see CampaignExtension#dispatchMessageInteraction(Map)
     */
    protected void viewed() {
        final HashMap<String, Object> contextData = new HashMap<>();
        contextData.put(CampaignConstants.ContextDataKeys.MESSAGE_ID, messageId);
        contextData.put(CampaignConstants.ContextDataKeys.MESSAGE_VIEWED, String.valueOf(1));
        callDispatchMessageInteraction(contextData);
    }

    /**
     * Generates a {@code Map} with message data for a "message clicked" event and passes it to the
     * parent {@code CampaignExtension} for dispatch.
     *
     * @see CampaignExtension#dispatchMessageInteraction(Map)
     */
    protected void clickedThrough() {
        final HashMap<String, Object> contextData = new HashMap<>();
        contextData.put(CampaignConstants.ContextDataKeys.MESSAGE_ID, messageId);
        contextData.put(CampaignConstants.ContextDataKeys.MESSAGE_CLICKED, String.valueOf(1));
        callDispatchMessageInteraction(contextData);
    }

    /**
     * Generates a {@code Map} with message data for a "message clicked" event and passes it to the
     * parent {@code CampaignExtension} for dispatch.
     *
     * <p>This method also adds click through URL to the data and attempts to open the URL, after
     * decoding and expanding tokens in the URL.
     *
     * @param data {@code Map<String, String>} containing message interaction data
     * @see #openUrl(String)
     * @see CampaignExtension#dispatchMessageInteraction(Map)
     */
    protected void clickedWithData(final Map<String, String> data) {
        final Map<String, Object> messageData = new HashMap<>();

        // Need to decode and token expand the click through URL before putting it on the event
        for (Map.Entry<String, String> entry : data.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if (key.equals(CampaignConstants.CAMPAIGN_INTERACTION_URL)) {
                String url = null;

                try {
                    url = URLDecoder.decode(value, CampaignConstants.CHARSET_UTF_8);
                } catch (final UnsupportedEncodingException e) {
                    Log.warning(
                            CampaignConstants.LOG_TAG,
                            SELF_TAG,
                            "Failed to decode message interaction url (%s)",
                            e.getMessage());
                }

                final Map<String, String> urlTokens = new HashMap<>();
                urlTokens.put(CampaignConstants.MESSAGE_TOKEN_MESSAGE_ID, messageId);

                url = expandTokens(url, urlTokens);
                openUrl(url);
                messageData.put(key, url);
            } else {
                messageData.put(key, value);
            }
        }

        messageData.put(CampaignConstants.ContextDataKeys.MESSAGE_ID, messageId);
        messageData.put(CampaignConstants.ContextDataKeys.MESSAGE_CLICKED, String.valueOf(1));

        callDispatchMessageInteraction(messageData);
    }

    /**
     * Requests that the {@code UriOpening} show this {@code url}.
     *
     * @param url {@link String} containing url to be shown
     */
    protected void openUrl(final String url) {
        if (StringUtils.isNullOrEmpty(url)) {
            Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "Cannot open a null or empty URL.");
            return;
        }

        final UriOpening uriService = ServiceProvider.getInstance().getUriService();

        if (uriService == null || !uriService.openUri(url)) {
            Log.debug(CampaignConstants.LOG_TAG, "Could not open URL (%s)", url);
        }
    }

    /**
     * Expands provided {@code tokens} in the given {@code input} String.
     *
     * <p>It returns the same input {@link String} if,
     *
     * <ul>
     *   <li>Input {@code String} is null or empty.
     *   <li>Provided tokens Map is null or empty.
     *   <li>No key from the tokens Map is present in the input {@code String}.
     * </ul>
     *
     * @param input {@code String} in which we should replace the given tokens
     * @param tokens {@code Map<String, String>} with the tokens that need to be replaced and their
     *     values
     * @return the updated {@code String} with the expanded tokens
     */
    String expandTokens(final String input, final Map<String, String> tokens) {
        if (StringUtils.isNullOrEmpty(input)) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "expandTokens -  Unable to expand tokens, input string is null or empty");
            return input;
        }

        if (tokens == null || tokens.isEmpty()) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "expandTokens -  Unable to expand tokens, provided tokens Map is null or"
                            + " empty");
            return input;
        }

        String returnString = input;

        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if (key == null || value == null) {
                continue;
            }

            returnString = returnString.replace(entry.getKey(), entry.getValue());
        }

        return returnString;
    }

    /**
     * Invokes method on the {@code parentModule} to dispatch the provided message interaction
     * {@code data}.
     *
     * @param data {@code Map<String, String>} containing message interaction data
     */
    protected void callDispatchMessageInteraction(final Map<String, Object> data) {
        parentModule.dispatchMessageInteraction(data);
    }

    /**
     * Invokes method on the {@code parentModule} to dispatch the provided message info.
     *
     * @param broadlogId {@link String} containing message broadlogId
     * @param deliveryId {@code String} containing message deliveryId
     * @param action {@code String} containing message action
     */
    protected void callDispatchMessageInfo(
            final String broadlogId, final String deliveryId, final String action) {
        parentModule.dispatchMessageInfo(broadlogId, deliveryId, action);
    }

    /**
     * Determines whether a {@code CampaignMessage} should attempt to download assets for caching.
     *
     * @return {@code boolean} indicating whether this should download assets
     */
    abstract boolean shouldDownloadAssets();
}
