/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.urlshortener.test.ui;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebElement;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import com.xwiki.urlshortener.test.po.URLShortenerPage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests for the URL Shortener Application. Because the text is not actually saved on clipboard in the
 * functional tests, we only check that the button is present and no error is displayed. The reason for the limitation
 * is that the navigator.clipboard.writeText API requires the clipboardWrite permission for actually saving text to
 * clipboard, but this is not present in this context.
 *
 * @version $Id$
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/Clipboard/writeText#browser_compatibility">Browser
 *     compatibility explanation</a>
 * @since 1.0
 */
@UITest(extraJARs = {
    // The Solr store is not ready yet to be installed as extension
    "org.xwiki.platform:xwiki-platform-eventstream-store-solr:14.10",
    "org.xwiki.platform:xwiki-platform-search-solr-query:14.10" })
class URLShortenerIT
{
    @Test
    void copyURLShortenerPresentWithAdmin(TestUtils testUtils, TestReference testReference)
    {
        testUtils.loginAsSuperAdmin();
        // By default the AdminGroup doesn't have "admin" right, so give it since we're going to create the Admin
        // user and make it part of the Admin group and we need that Admin to have "admin" rights.
        testUtils.setGlobalRights("XWiki.XWikiAdminGroup", "", "admin", true);
        testUtils.createAdminUser();
        testUtils.loginAsAdmin();

        testUtils.createPage(testReference, "");
        URLShortenerPage page = new URLShortenerPage();

        WebElement urlShortenerButton = page.getURLShortenerButton();
        assertTrue(urlShortenerButton.isDisplayed());

        urlShortenerButton.click();
        assertFalse(page.isErrorNotificationDisplayed());
    }

    @Test
    void copyURLShortenerPresentWithSimpleUser(TestUtils testUtils, TestReference testReference)
    {
        testUtils.createUserAndLogin("alice", "pass");

        testUtils.createPage(testReference, "");
        URLShortenerPage page = new URLShortenerPage();

        WebElement urlShortenerButton = page.getURLShortenerButton();
        assertTrue(urlShortenerButton.isDisplayed());

        urlShortenerButton.click();
        assertFalse(page.isErrorNotificationDisplayed());
    }
}
