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

import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.internal.util.StringEncoder;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.util.TimeUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

class Utils {
    private Utils() {
    }

    /**
     * Creates a {@code CampaignHit} object from the given {@code DataEntity}.
     *
     * @param dataEntity {@link DataEntity} containing a Campaign network request
     * @return {@link CampaignHit} created from the {@code DataEntity}
     */
    static CampaignHit campaignHitFromDataEntity(final DataEntity dataEntity) {
        try {
            final JSONObject jsonData = new JSONObject(dataEntity.getData());
            return new CampaignHit(jsonData.getString(CampaignConstants.CampaignHit.URL), jsonData.getString(CampaignConstants.CampaignHit.PAYLOAD), jsonData.getInt(CampaignConstants.CampaignHit.TIMEOUT));
        } catch (final JSONException jsonException) {
            Log.warning(CampaignConstants.LOG_TAG, "campaignHitFromDataEntity",
                    "JSON exception occurred converting data entity to campaign hit: %s", jsonException.getMessage());
        }
        return null;
    }

    /**
     * Recursively checks and deletes files within the cached assets directory which aren't within the {@code assetsToRetain} list.
     *
     * @param cacheAsset     {@link File} containing the cached assets directory
     * @param assetsToRetain {@code List<String>} containing assets which should be retained
     */
    static void clearCachedAssetsNotInList(final File cacheAsset, final List<String> assetsToRetain) {
        if (cacheAsset.isDirectory()) {
            for (final File child : cacheAsset.listFiles()) {
                clearCachedAssetsNotInList(child, assetsToRetain);
            }
        } else {
            for (final String asset : assetsToRetain) {
                if (!cacheAsset.getName().equals(StringEncoder.sha2hash(asset)) && cacheAsset.exists()) {
                    cacheAsset.delete();
                }
            }
        }
    }

    /**
     * Recursively deletes cached html files and rules within the given directory
     *
     * @param file {@link File} containing the campaign rules directory
     */
    static void cleanDirectory(final File file) {
        if (file.isDirectory()) {
            for (final File child : file.listFiles()) {
                cleanDirectory(child);
            }
        }
        file.delete();
    }

    /**
     * Extracts the response properties (like {@code HTTP_HEADER_ETAG} , {@code HTTP_HEADER_LAST_MODIFIED}
     * that are useful as cache metadata.
     *
     * @param response the {@code HttpConnecting} from where the response properties should be extracted from
     * @return a map of metadata keys and their values as obrained from the {@code response}
     */
    static HashMap<String, String> extractMetadataFromResponse(final HttpConnecting response) {
        final HashMap<String, String> metadata = new HashMap<>();

        final String lastModifiedProp = response.getResponsePropertyValue(CampaignConstants.HTTP_HEADER_LAST_MODIFIED);
        final Date lastModifiedDate = TimeUtils.parseRFC2822Date(
                lastModifiedProp, TimeZone.getTimeZone("GMT"), Locale.US);
        final String lastModifiedMetadata = lastModifiedDate == null
                ? String.valueOf(new Date(0L).getTime())
                : String.valueOf(lastModifiedDate.getTime());
        metadata.put(CampaignConstants.HTTP_HEADER_LAST_MODIFIED, lastModifiedMetadata);

        final String eTagProp = response.getResponsePropertyValue(CampaignConstants.HTTP_HEADER_ETAG);
        metadata.put(CampaignConstants.HTTP_HEADER_ETAG, eTagProp == null ? "" : eTagProp);

        return metadata;
    }

    /**
     * Creates http headers for conditional fetching, based on the metadata of the
     * {@code CacheResult} provided.
     *
     * @param cacheResult the cache result whose metadata should be used for finding headers
     * @return a map of headers (HTTP_HEADER_IF_MODIFIED_SINCE, HTTP_HEADER_IF_NONE_MATCH)
     * that can be used while fetching any modified content.
     */
    static Map<String, String> extractHeadersFromCache(final CacheResult cacheResult) {
        final Map<String, String> headers = new HashMap<>();
        if (cacheResult == null) {
            return headers;
        }

        final Map<String, String> metadata = cacheResult.getMetadata();
        final String eTag = metadata == null ? "" : metadata.get(CampaignConstants.HTTP_HEADER_ETAG);
        headers.put(CampaignConstants.HTTP_HEADER_IF_NONE_MATCH, eTag != null ? eTag : "");

        // Last modified in cache metadata is stored in epoch string. So Convert it to RFC-2822 date format.
        final String lastModified = metadata == null ? null : metadata.get(CampaignConstants.HTTP_HEADER_LAST_MODIFIED);
        long lastModifiedEpoch;
        try {
            lastModifiedEpoch = lastModified != null ? Long.parseLong(lastModified) : 0L;
        } catch (final NumberFormatException e) {
            lastModifiedEpoch = 0L;
        }

        final String ifModifiedSince = TimeUtils.getRFC2822Date(lastModifiedEpoch,
                TimeZone.getTimeZone("GMT"), Locale.US);
        headers.put(CampaignConstants.HTTP_HEADER_IF_MODIFIED_SINCE, ifModifiedSince);
        return headers;
    }

    /**
     * Extracts query parameters from a given {@code String} into a {@code Map<String, String>}.
     *
     * @param queryString {@link String} containing query parameters
     * @return the extracted {@code Map<String, String>} query parameters
     */
    static Map<String, String> extractQueryParameters(final String queryString) {
        if (StringUtils.isNullOrEmpty(queryString)) {
            return null;
        }

        final Map<String, String> parameters = new HashMap<>();
        final String[] paramArray = queryString.split("&");

        for (String currentParam : paramArray) {
            // quick out in case this entry is null or empty string
            if (StringUtils.isNullOrEmpty(currentParam)) {
                continue;
            }

            final String[] currentParamArray = currentParam.split("=", 2);

            if (currentParamArray.length != 2 ||
                    (currentParamArray[0].isEmpty() || currentParamArray[1].isEmpty())) {
                continue;
            }

            final String key = currentParamArray[0];
            final String value = currentParamArray[1];
            parameters.put(key, value);
        }

        return parameters;
    }

    static boolean isInAppMessageEvent(final Event event) {
        final Map<String, Object> consequenceMap = DataReader.optTypedMap(Object.class, event.getEventData(), CampaignConstants.EventDataKeys.RuleEngine.CONSEQUENCE_TRIGGERED, null);
        if (MapUtils.isNullOrEmpty(consequenceMap)) {
            return false;
        }
        return consequenceMap.get(CampaignConstants.EventDataKeys.RuleEngine.MESSAGE_CONSEQUENCE_TYPE).equals(CampaignConstants.MESSAGE_CONSEQUENCE_MESSAGE_TYPE);
    }
}