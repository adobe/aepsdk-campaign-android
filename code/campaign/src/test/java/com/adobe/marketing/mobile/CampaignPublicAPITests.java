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

import static org.junit.Assert.assertEquals;

import com.adobe.marketing.mobile.campaign.CampaignExtension;
import com.adobe.marketing.mobile.services.Log;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CampaignPublicAPITests {

    @Test
    public void test_extensionVersion() {
        assertEquals("2.0.6", Campaign.extensionVersion());
    }

    @Test
    public void test_publicExtensionConstants() {
        assertEquals(CampaignExtension.class, Campaign.EXTENSION);
        List<Class<? extends Extension>> extensions = new ArrayList<>();
        extensions.add(Campaign.EXTENSION);
        // should not throw exceptions
        MobileCore.registerExtensions(extensions, null);
    }

    @Test
    public void test_setLinkageFields() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic = Mockito.mockStatic(MobileCore.class)) {
            // setup
            ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            Map<String, String> linkageFields = new HashMap<>();
            linkageFields.put("cusFirstName", "firstName");
            linkageFields.put("cusLastName", "lastName");
            linkageFields.put("cusEmail", "firstNameLastName@email.com");
            // test
            Campaign.setLinkageFields(linkageFields);
            // verify campaign request identity event dispatched
            mobileCoreMockedStatic.verify(() -> MobileCore.dispatchEvent(eventCaptor.capture()));
            Event capturedEvent = eventCaptor.getValue();
            assertEquals(EventType.CAMPAIGN, capturedEvent.getType());
            assertEquals(EventSource.REQUEST_IDENTITY, capturedEvent.getSource());
            assertEquals("setLinkageFields Event", capturedEvent.getName());
            Map<String, Object> capturedEventData = capturedEvent.getEventData();
            Map<String, String> capturedLinkageFields = (Map<String, String>) capturedEventData.get("linkagefields");
            assertEquals("firstName", capturedLinkageFields.get("cusFirstName"));
            assertEquals("lastName", capturedLinkageFields.get("cusLastName"));
            assertEquals("firstNameLastName@email.com", capturedLinkageFields.get("cusEmail"));
        }
    }

    @Test
    public void test_setLinkageFields_NullMap() {
        // test
        try (MockedStatic<MobileCore> mobileCoreMockedStatic = Mockito.mockStatic(MobileCore.class)) {
            Campaign.setLinkageFields(null);
            // verify campaign request identity event not dispatched
            mobileCoreMockedStatic.verifyNoInteractions();
        }
    }

    @Test
    public void test_setLinkageFields_EmptyMap() {
        // test
        try (MockedStatic<MobileCore> mobileCoreMockedStatic = Mockito.mockStatic(MobileCore.class)) {
            Campaign.setLinkageFields(new HashMap<>());
            // verify campaign request identity event not dispatched
            mobileCoreMockedStatic.verifyNoInteractions();
        }
    }

    @Test
    public void test_resetLinkageFields() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic = Mockito.mockStatic(MobileCore.class)) {
            // setup
            ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            // test
            Campaign.resetLinkageFields();
            // verify campaign request reset event dispatched
            mobileCoreMockedStatic.verify(() -> MobileCore.dispatchEvent(eventCaptor.capture()));
            Event capturedEvent = eventCaptor.getValue();
            assertEquals(EventType.CAMPAIGN, capturedEvent.getType());
            assertEquals(EventSource.REQUEST_RESET, capturedEvent.getSource());
            assertEquals("resetLinkageFields Event", capturedEvent.getName());
        }
    }
}
