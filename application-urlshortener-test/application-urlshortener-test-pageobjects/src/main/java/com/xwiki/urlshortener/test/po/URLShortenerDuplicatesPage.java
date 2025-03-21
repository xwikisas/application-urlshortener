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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Actions for interacting with a {@link ViewPage} that has the URL Shortener feature in the options menu.
 *
 * @version $Id$
 * @since 1.0
 */
public class URLShortenerDuplicatesPage extends ViewPage
{
    public URLShortenerDuplicatesPage()
    {
    }

    static public URLShortenerDuplicatesPage gotoPage()
    {
        getUtil().gotoPage("URLShortener", "WebHome");
        return new URLShortenerDuplicatesPage();
    }

    public final Map<String, List<String>> getCriticalDuplicateURLs()
    {
        return getDriver().findElement(By.cssSelector("section#urlshortener-critical-duplicates-section"))
            .findElements(By.tagName("section")).stream().collect(Collectors.toMap(
                (webElement) -> webElement.findElement(By.className("urlshortener-regenerate-btn"))
                    .getAttribute("data-old-page-id"),
                (webElement) -> webElement.findElements(By.className("urlshortener-regenerate-btn")).stream()
                    .map((buttonElement) -> buttonElement.getAttribute("data-page-ref")).collect(Collectors.toList())));
    }

    public final Map<String, List<String>> getPotentialDuplicateURLs()
    {
        return getDriver().findElement(By.cssSelector("section#urlshortener-potential-duplicates-section"))
            .findElements(By.tagName("section")).stream().collect(Collectors.toMap(
                (webElement) -> webElement.findElement(By.className("urlshortener-regenerate-btn"))
                    .getAttribute("data-old-page-id"),
                (webElement) -> webElement.findElements(By.className("urlshortener-regenerate-btn")).stream()
                    .map((buttonElement) -> buttonElement.getAttribute("data-page-ref")).collect(Collectors.toList())));
    }

    public void regenerateId(String oldPageId, String pageRef)
    {
        WebElement button = getDriver().findElement(
            By.cssSelector("button[data-old-page-id=\"" + oldPageId + "\"][data-page-ref=\"" + pageRef + "\"]"));
        button.click();
    }
}
