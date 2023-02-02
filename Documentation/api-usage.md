# Adobe Campaign Standard API reference

## Prerequisites

Refer to the [Getting started guide](./getting-started.md)

## API reference

* [extensionVersion](#extensionversion)
* [registerExtension](#registerextension)
* [resetLinkageFields](#resetlinkagefields)
* [setLinkageFields](#setlinkagefields)

## extensionVersion

Returns the running version of the Campaign Standard extension.

#### Java

```java
String campaignExtensionVersion = Campaign.extensionVersion();
```

#### Kotlin

```kotlin
val campaignExtensionVersion: String = Campaign.extensionVersion()
```

## registerExtension

> **Warning**
> Deprecated as of 2.0.0. Use the [MobileCore.registerExtensions API](https://github.com/adobe/aepsdk-core-android) instead.

Registers the Campaign Standard extension with the Mobile Core.

#### Java

```java
Campaign.registerExtension();
```

#### Kotlin

```kotlin
Campaign.registerExtension()
```

## resetLinkageFields

Clears previously stored linkage fields in the mobile SDK and triggers a Campaign rules download request to the configured Campaign server.

This method unregisters any previously registered rules with the Rules Engine and clears cached rules from the most recent rules download.

#### Java

```java
Campaign.resetLinkageFields();
```

#### Kotlin

```kotlin
Campaign.resetLinkageFields()
```

## setLinkageFields

Sets the Campaign linkage fields (CRM IDs) in the mobile SDK to be used for downloading personalized messages from Campaign.

The set linkage fields are stored as a base64 encoded JSON string in memory and they are sent in a custom HTTP header `X-InApp-Auth`.

#### Java

```java
HashMap<String, String> linkageFields = new HashMap<String, String>();
linkageFields.put("cusFirstName", "John");
linkageFields.put("cusLastName", "Doe");
linkageFields.put("cusEmail", "john.doe@email.com");
Campaign.setLinkageFields(linkageFields);
```

#### Kotlin

```kotlin
val linkageFields: Map<String, String?> = mapOf(
    "cusFirstName" to "John",
    "cusLastName" to "Doe",
    "cusEmail" to "john.doe@email.com"
)

Campaign.setLinkageFields(linkageFields)
```