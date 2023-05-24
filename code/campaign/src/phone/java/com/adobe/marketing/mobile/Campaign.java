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

package com.adobe.marketing.mobile;

import androidx.annotation.NonNull;

import com.adobe.marketing.mobile.campaign.CampaignExtension;
import com.adobe.marketing.mobile.services.Log;

import java.util.HashMap;
import java.util.Map;

public class Campaign {
	private final static String EXTENSION_VERSION = "2.0.4";
	private static final String LOG_TAG = "Campaign";
	private static final String LINKAGE_FIELDS = "linkagefields";

	public static final Class<? extends Extension> EXTENSION = CampaignExtension.class;

	private Campaign() {
	}

	/**
	 * Returns the version of the {@link Campaign} extension
	 *
	 * @return The version as {@code String}
	 */
	@NonNull
	public static String extensionVersion() {
		return EXTENSION_VERSION;
	}

	/**
	 * Registers the extension with the Mobile SDK. This method should be called only once in your application class.
	 */
	@Deprecated
	public static void registerExtension() {
		MobileCore.registerExtension(CampaignExtension.class, extensionError -> {
			if (extensionError == null) {
				return;
			}
			Log.error(LOG_TAG, "registerExtension", "There was an error when registering the Campaign extension: %s",
					extensionError.getErrorName());
		});
	}

	/**
	 * Sets the Campaign linkage fields (CRM IDs) in the mobile SDK to be used for downloading personalized messages from Campaign.
	 * <p>
	 * The set linkage fields are stored as base64 encoded JSON string in memory and they are sent in a custom HTTP header 'X-InApp-Auth'
	 * in all future Campaign rules download requests until {@link #resetLinkageFields()} is invoked. These in-memory variables are also
	 * lost in the wake of an Application crash event or upon graceful Application termination or when the privacy status is updated to
	 * {@link MobilePrivacyStatus#OPT_OUT}.
	 * <p>
	 * This method clears cached rules from previous download before triggering a rules download request to the configured Campaign server.
	 * If the current SDK privacy status is not {@code MobilePrivacyStatus.OPT_IN}, no rules download happens.
	 * Dispatches a {@code EventType.CAMPAIGN}, {@code EventSource.REQUEST_IDENTITY} event to set linkage fields in the SDK.
	 * <p>
	 * If the provided {@code linkageFields} Map is null or empty, no event is dispatched.
	 *
	 * @param linkageFields {@code Map<String, String>} containing the linkage fields key-value pairs
	 */
	public static void setLinkageFields(@NonNull final Map<String, String> linkageFields) {
		if (linkageFields == null || linkageFields.isEmpty()) {
			Log.debug(LOG_TAG, "setLinkageFields",
					"setLinkageFields -  Cannot set Linkage Fields, provided linkage fields map is empty. \n For more information: https://aep-sdks.gitbook.io/docs/using-mobile-extensions/adobe-campaign-standard/adobe-campaign-standard-api-reference#set-linkage-fields");
			return;
		}

		final Map<String, Object> eventData = new HashMap<>();
		eventData.put(LINKAGE_FIELDS, linkageFields);

		final Event event = new Event.Builder("setLinkageFields Event",
				EventType.CAMPAIGN, EventSource.REQUEST_IDENTITY).setEventData(eventData).build();

		// dispatch event
		MobileCore.dispatchEvent(event);
	}

	/**
	 * Clears previously stored linkage fields in the mobile SDK and triggers Campaign rules download request to the configured Campaign server.
	 * <p>
	 * This method unregisters any previously registered rules with the Event Hub and clears cached rules from previous download.
	 * If the current SDK privacy status is not {@link MobilePrivacyStatus#OPT_IN}, no rules download happens.
	 * To reset the linkage field, this function dispatches a {@code EventType.CAMPAIGN}, {@code EventSource.REQUEST_RESET} event to clear previously set
	 * linkage fields in the SDK.
	 *
	 * @see #setLinkageFields(Map)
	 */
	public static void resetLinkageFields() {

		final Event event = new Event.Builder("resetLinkageFields Event",
				EventType.CAMPAIGN, EventSource.REQUEST_RESET).build();

		// dispatch event
		MobileCore.dispatchEvent(event);
	}

}