`CHT-gateway`
===============

<a href="https://travis-ci.org/medic/cht-gateway"><img src="https://travis-ci.org/medic/cht-gateway.svg?branch=master"/></a>

Download APKs from: https://github.com/medic/cht-gateway/releases

-----

An SMS gateway for Android.  Send and receive SMS from your webapp via an Android phone.

	+--------+                 +-----------+
	|  web   |                 |  cht-     | <-------- SMS
	| server | <---- HTTP ---- |  gateway  |
	|        |                 | (android) | --------> SMS
	+--------+                 +-----------+

# Use

## Installation

Install the latest APK from https://github.com/medic/cht-gateway/releases.

## Configuration

### CHT

If you're configuring `cht-gateway` for use with hosted [`CHT-Core`](https://github.com/medic/cht-core), with a URL of e.g. `https://myproject.dev.medicmobile.org` and a username of `gateway` and a password of `topSecret`, fill in the settings as follows:

#### Medic-branded gateway

Please note that in the medic-branded build, the username is hard-coded as `gateway`, and cannot be changed.

```
Instance name: myproject [dev]
Password: topSecret
```

#### Generic-branded gateway

```
WebappUrl: https://gateway:topSecret@myproject.some-subdomain.medicmobile.org/api/sms
```

### Other

If you're configuring `cht-gateway` for use with other services, you will need to use the _generic_ build of `cht-gateway`, and find out the value for _CHT URL_ from your tech support.

### CDMA Compatibility Mode

Some CDMA networks have limited support for multipart SMS messages.  This can occur within the same network, or only when sending SMS from a GSM network to a CDMA network.  Check this box if `cht-gateway` is running on a GSM network and:

* multipart messages sent to CDMA phones never arrive; or
* multipart messages sent to CDMA phones are truncated

# Passwords

When using HTTP Basic Auth with gateway, all characters in the password must be chosen from the [ISO-8859-1](https://en.wikipedia.org/wiki/ISO/IEC_8859-1) characterset, excluding `#`, `/`, `?`, `@`.

# API

This is the API specification for communications between `cht-gateway` and a web server.  Messages in both directions are `application/json`.

Where a list of values is expected but there are no values provided, it is acceptable to:

* provide a `null` value; or
* provide an empty array (`[]`); or
* omit the field completely

Bar array behaviour specified above, `cht-gateway` _must_ include fields specified in this document, and the web server _must_ include all expected fields in its responses.  Either party _may_ include extra fields as they see fit.

## Idempotence

N.B. messages are considered duplicate by `cht-gateway` if they have identical values for `id`.  The webapp is expected to do the same.

`cht-gateway` will not re-process duplicate webapp-originating messages.

`cht-gateway` may forward a webapp-terminating message to the webapp multiple times.

`cht-gateway` may forward a delivery status report to the webapp multiple times for the same message.  This should indicate a change of state, but duplicate delivery reports may be delivered in some circumstances, including:

* the phone receives multiple delivery status reports from the mobile network for the same message
* `cht-gateway` failed to process the webapp's response when the delivery report was last forwarded from `cht-gateway` to webapp

## Authorisation

`cht-gateway` supports [HTTP Basic Auth](https://en.wikipedia.org/wiki/Basic_access_authentication).  Just include the username and password for your web endpoint when configuring `cht-gateway`, e.g.:

	https://username:password@example.com/cht-gateway-api-endpoint

## Messages

The entire API should be implemented by a webapp at a single endpoint, e.g. https://exmaple.com/cht-gateway-api-endpoint

### GET

Expected response:

	{
		"medic-gateway": true
	}

### POST

`cht-gateway` will accept and process any relevant data received in a response.  However, it may choose to only send certain types of information in a particular request (e.g. only provide a webapp-terminating SMS), and will also poll the web service periodically for webapp-originating messages, even if it has no new data to pass to the web service.

### Request

#### Headers

The following headers will be set by requests:

header           | value
-----------------|-------------------
`Accept`         | `application/json`
`Accept-Charset` | `utf-8`
`Accept-Encoding`| `gzip`
`Cache-Control`  | `no-cache`
`Content-Type`   | `application/json`

Requests and responses may be sent with `Content-Encoding` set to `gzip`.

#### Content

	{
		"messages": [
			{
				"id": <String: uuid, generated by `cht-gateway`>,
				"from": <String: international phone number>,
				"content": <String: message content>,
				"sms_sent": <long: ms since unix epoch that message was sent>,
				"sms_received": <long: ms since unix epoch that message was received>
			},
			...
		],
		"updates": [
			{
				"id": <String: uuid, generated by webapp>,
				"status": <String: PENDING|SENT|DELIVERED|FAILED>,
				"reason": <String: failure reason (optional - only present for status:FAILED)>
			},
			...
		],
	}

The status field is defined as follows.

Status           | Description
-----------------|-------------------
PENDING | The message has been sent to the gateway's network
SENT | The message has been sent to the recipient's network
DELIVERED | The message has been received by the recipient's phone
FAILED | The delivery has failed and will not be retried

### Response

#### Success

##### HTTP Status: `2xx`

Clients may respond with any status code in the `200`-`299` range, as they feel is
appropriate.  `cht-gateway` will treat all of these statuses the same.

##### Content

	{
		"messages": [
			{
				"id": <String: uuid, generated by webapp>,
				"to": <String: local or international phone number>,
				"content": <String: message content>
			},
			...
		],
	}

#### HTTP Status `400`+

Response codes of `400` and above will be treated as errors.

If the response's `Content-Type` header is set to `application/json`, `cht-gateway` will attempt to parse the body as JSON.  The following structure is expected:

	{
		"error": true,
		"message": <String: error message>
	}

The `message` property may be logged and/or displayed to users in the `cht-gateway` UI.

#### Other response codes

Treatment of response codes below `200` and between `300` and `399` will _probably_ be handled sensibly by Android.

# SMS Retry Mechanism

Gateway will retry to send the SMS when any of these errors occurs: `RESULT_ERROR_NO_SERVICE`, `RESULT_ERROR_NULL_PDU` and `RESULT_ERROR_RADIO_OFF`.

1. A possible temporary error occurs and Gateway retries sending the SMS:
    1.1 SMS status will be updated to `UNSENT`, so Gateway will find it and add it into the `send queue` automatically.
    1.2 SMS' `retry counter` increases by 1.
    1.3 The retry attempt is scheduled based on this formula: `SMS' last activity time + ( 1 minute * (retry counter ^ 1.5) )`. This means the time between retries is incremental.
    1.4 Gateway logs: the error, the retry counter and the retry scheduled time. Sample: `Sending SMS to +1123123123 failed (cause: radio off) Retry #5 in 15 min`

2. Gateway has a maximum limit of attempts to retry sending SMS (currently 20), If this is reached then:
    2.1 Gateway will hard fail the SMS by updating its status to `FAILED` and won't retry again.
    2.2 Gateway logs error. Sample: `Sending message to +1123123123 failed (cause: radio off) Not retrying`

3. At this point the user has the option of manually select the SMS and press `Retry` button.
    3.1 If they do and SMS fails again, then the process will restart from step # 1.

# Development

## Requirements

### `cht-gateway`

- JDK 11.
- The Android SDK and relevant libraries. Check `build.gradle`'s `compileSdkVersion` parameter for which one need to be installed
- Either a physical or emulated device running Lolipop (AKA Android 5.1 AKA api 22). Later android versions may cause end to end tests to fail

### `demo-server`

* npm

## Building locally

To build locally and install to an attached android device:

	make

To run unit tests and static analysis tools locally:

	make test

To run end to end tests, first either connect a physical device, or start an emulated android device, and then:

    make e2e


## `demo-server`

There is a demonstration implementation of a server included for `cht-gateway` to communicate with.  You can add messages to this server, and query it to see the interactions that it has with `cht-gateway`.

### Local

To start the demo server locally:

	make demo-server

To list the data stored on the server:

	curl http://localhost:8000

To make the next good request to `/app` return an error:

	curl --data '"Something failed!"' http://localhost:8000/error

To add a webapp-originating message (to be send by `cht-gateway`):

	curl -vvv --data '{ "id": "3E105262-070C-4913-949B-E7ACA4F42B71", "to": "+447123555888", "content": "hi" }' http://localhost:8000

To simulate a request from `cht-gateway`:

	curl http://localhost:8000/app -H "Accept: application/json" -H "Accept-Charset: utf-8" -H "Accept-Encoding: gzip" -H "Cache-Control: no-cache" -H "Content-Type: application/json" --data '{}'

To clear the data stored on the server:

	curl -X DELETE http://localhost:8000

To set a username and password on the demo server:

	curl --data '{"username":"alice", "password":"secret"}' http://localhost:8000/auth

### Remote

It's simple to deploy the demo server to remote NodeJS hosts.

#### Heroku

TODO walkthrough

#### Modulus

TODO walkthrough

## Releasing

1. Create a git tag for the version, eg: `v1.7.4`.
2. Push the tag to the repo and travis will build and publish the apk to GitHub.
3. Announce the release on the [CHT forum](https://forum.communityhealthtoolkit.org), under the "Product - Releases" category.

# Android Version Support

## "Default SMS/Messaging app"

Some changes were made to the Android SMS APIs in 4.4 (Kitkat®).  The significant change was this:

> from android 4.4 onwards, apps cannot delete messages from the device inbox _unless they are set, by the user, as the default messaging app for the device_

Some reading on this can be found at:

* http://android-developers.blogspot.com.es/2013/10/getting-your-sms-apps-ready-for-kitkat.html
* https://www.addhen.org/blog/2014/02/15/android-4-4-api-changes/

Adding support for kitkat® means that there is some extra code in `cht-gateway` whose purpose is not obvious:

### Non-existent activities in `AndroidManifest.xml`

Activities `HeadlessSmsSendService` and `ComposeSmsActivity` are declared in `AndroidManifest.xml`, but are not implemented in the code.

### Unwanted permissions

The `BROADCAST_WAP_PUSH` permission is requested in `AndroidManifest.xml`, and an extra `BroadcastReceiver`, `MmsIntentProcessor` is declared.  When `cht-gateway` is the default messaging app on a device, incoming MMS messages will be ignored.  Actual WAP Push messages are probably ignored too.

### Extra intents

To support being the default messaging app, `cht-gateway` listens for `SMS_DELIVER` as well as `SMS_RECEIVED`.  If the default SMS app, we need to ignore `SMS_RECEIVED`.

## Runtime Permissions

Since Android 6.0 (marshmallow), permissions for sending and receiving SMS must be requested both in `AndroidManifest.xml` and also at runtime.  Read more at https://developer.android.com/intl/ru/about/versions/marshmallow/android-6.0-changes.html#behavior-runtime-permissions

## Copyright

Copyright 2013-2021 Medic Mobile, Inc. <hello@medicmobile.org>

## License

The software is provided under AGPL-3.0. Contributions to this project are accepted under the same license.
