/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import androidx.test.core.app.ApplicationProvider
import mozilla.components.browser.session.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.tv.firefox.ext.toSafeIntent
import org.robolectric.RobolectricTestRunner

private const val TEST_URL = "https://github.com/mozilla-mobile/focus-android"

@RunWith(RobolectricTestRunner::class)
class IntentValidatorTest {
    private lateinit var appContext: Application

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testViewIntent() {
        var isCalled = false
        val expectedUrl = TEST_URL
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(expectedUrl)).toSafeIntent()
        IntentValidator.validate(appContext, intent) { url, source ->
            isCalled = true
            assertEquals(expectedUrl, url)
            assertEquals(Session.Source.ACTION_VIEW, source)
        }

        assertTrue("Expected intent to be valid", isCalled)
    }

    /** In production we see apps send VIEW intents without an URL. (Focus #1373) */
    @Test
    fun testViewIntentWithNullURL() {
        val intent = Intent(Intent.ACTION_VIEW, null).toSafeIntent()
        IntentValidator.validate(appContext, intent) { _, _ ->
            fail("Null URL should not be vaIntlid")
        }
    }

    @Test
    fun testCustomTabIntent() {
        val expectedUrl = TEST_URL
        val intent = CustomTabsIntent.Builder()
                .setToolbarColor(Color.GREEN)
                .addDefaultShareMenuItem()
                .build()
                .intent
                .setData(Uri.parse(expectedUrl))
                .toSafeIntent()

        var isCalled = false
        IntentValidator.validate(appContext, intent) { url, source ->
            isCalled = true
            assertEquals(expectedUrl, url)
            assertEquals(Session.Source.ACTION_VIEW, source)
        }

        assertTrue("Expected intent to be valid", isCalled)
    }

    @Test
    fun testViewIntentFromHistoryIsIgnored() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        }.toSafeIntent()

        IntentValidator.validateOnCreate(appContext, intent, null) { _, _ ->
            fail("Intent from history should not be valid")
        }
    }

    @Test
    fun testIntentNotValidIfWeAreRestoring() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL)).toSafeIntent()
        IntentValidator.validateOnCreate(appContext, intent, Bundle()) { _, _ ->
            fail("Intent from restore should not be valid")
        }
    }

    @Test
    fun testShareIntentViaNewIntent() {
        val expectedUrl = TEST_URL
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, expectedUrl)
        }.toSafeIntent()

        var isCalled = false
        IntentValidator.validate(appContext, intent) { url, source ->
            isCalled = true
            assertEquals(Session.Source.ACTION_SEND, source)
            assertEquals(expectedUrl, url)
        }

        assertTrue("Expected share intent to be valid", isCalled)
    }

    @Test
    fun testShareIntentWithTextViaNewIntent() {
        val expectedText = "Hello World Firefox TV"
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, expectedText)
        }.toSafeIntent()

        var isCalled = false
        IntentValidator.validate(appContext, intent) { url, source ->
            isCalled = true
            assertEquals(Session.Source.ACTION_SEND, source)
            expectedText.split(" ").forEach {
                assertTrue("Expected search url to contain $it", url.contains(it))
            }
        }

        assertTrue("Expected share intent to be valid", isCalled)
    }

    @Test
    fun `WHEN receiving MAIN intent with DIAL extra THEN call browser intent with Youtube and params`() {
        val param = "parameter"
        val expectedURL = "https://www.youtube.com/tv?$param"
        val intent = Intent(Intent.ACTION_MAIN).apply {
            putExtra(IntentValidator.DIAL_PARAMS_KEY, param)
        }.toSafeIntent()

        var isCalled = false
        IntentValidator.validate(appContext, intent) { url, source ->
            isCalled = true
            assertEquals(Session.Source.ACTION_VIEW, source)
            assertEquals(expectedURL, url)
        }
        assertTrue("Expected MAIN intent to be valid", isCalled)
    }

    @Test
    fun `WHEN receiving MAIN intent without DIAL extra THEN do not call browser intent`() {
        val intent = Intent(Intent.ACTION_MAIN).toSafeIntent()

        IntentValidator.validateOnCreate(appContext, intent, null) { _, _ ->
            fail("Intent without DIAL params should not be valid")
        }
    }
}
