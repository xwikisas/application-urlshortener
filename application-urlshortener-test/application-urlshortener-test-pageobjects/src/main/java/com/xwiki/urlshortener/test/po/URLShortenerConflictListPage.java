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
public class URLShortenerConflictListPage extends ViewPage
{
    public enum ConflictType
    {
        CRITICAL,
        POTENTIAL
    }

    public URLShortenerConflictListPage()
    {
    }

    static public URLShortenerConflictListPage gotoPage()
    {
        getUtil().gotoPage("URLShortener", "ConflictList");
        return new URLShortenerConflictListPage();
    }

    public final Map<String, List<String>> getConflictsMap()
    {
        return getDriver().findElement(By.cssSelector("section#urlshortener-conflicts-section"))
            .findElements(By.tagName("section")).stream().collect(Collectors.toMap(
                (webElement) -> webElement.findElement(
                        By.cssSelector("tr:has(.urlshortener-conflicts-table-hierarchy)"))
                    .getAttribute("data-old-page-id"),
                (webElement) -> webElement.findElements(
                        By.cssSelector("tr:has(.urlshortener-conflicts-table-hierarchy)")).stream()
                    .map((trElement) -> trElement.getAttribute("data-page-ref")).collect(Collectors.toList())));
    }

    public WebElement getRow(String pageId, String pageRef)
    {
        return getDriver().findElement(
            By.cssSelector("tr[data-old-page-id=\"" + pageId + "\"][data-page-ref=\"" + pageRef + "\"]"));
    }

    public final ConflictType getConflictType(String pageId, String pageRef)
    {
        WebElement row = getRow(pageId, pageRef);
        if (row.findElements(By.cssSelector("img[src*=\"exclamation\"]")).size() == 1) {
            return ConflictType.CRITICAL;
        } else if (row.findElements(By.cssSelector("img[src*=\"warning\"]")).size() == 1) {
            return ConflictType.POTENTIAL;
        } else {
            return null;
        }
    }

    public void regenerateId(String oldPageId, String pageRef)
    {
        WebElement button = getRow(oldPageId, pageRef).findElement(By.cssSelector(".urlshortener-regenerate-btn"));
        button.click();
    }
}
