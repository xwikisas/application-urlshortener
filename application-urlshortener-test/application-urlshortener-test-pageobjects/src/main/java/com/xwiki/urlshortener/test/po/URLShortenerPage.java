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
package com.xwiki.urlshortener.test.po;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Actions for interacting with a {@link ViewPage} that has the URL Shortener feature in the options menu.
 *
 * @version $Id$
 * @since 1.0
 */
public class URLShortenerPage extends ViewPage
{
    public WebElement getURLShortenerButton()
    {
        WebElement menu = getDriver().findElement(By.xpath("//div[@id='tmMoreActions']/button"));
        menu.click();

        String id = "urlShortener";
        getDriver().waitUntilElementIsVisible(By.id(id));
        return getDriver().findElementWithoutWaiting(By.id(id));
    }

    /**
     * Replace the browser clipboard with a fake one. The Clipboard API is only available in secure contexts (HTTPS or
     * localhost), which is not the case for the Docker test browser, so the real one would always fail.
     */
    public void mockClipboard()
    {
        getDriver().executeJavascript("window.ClipboardItem = function(items) { this.items = items; };"
            + "Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {write: items =>"
            + " Promise.resolve(items[0].items['text/plain']).then(text => { window.copiedText = text; })}});");
    }

    /**
     * @return the text written to the clipboard mocked by {@link #mockClipboard()}
     */
    public String getCopiedText()
    {
        getDriver().waitUntilJavascriptCondition("return !!window.copiedText");
        return (String) getDriver().executeJavascript("return window.copiedText");
    }

    public boolean isErrorNotificationDisplayed()
    {
        try {
            getDriver().waitUntilElementIsVisible(By.className("xnotification-error"));
            return getDriver().findElementWithoutWaiting(By.className("xnotification-error")).isDisplayed();
        } catch (TimeoutException e) {
            return false;
        }
    }
}
