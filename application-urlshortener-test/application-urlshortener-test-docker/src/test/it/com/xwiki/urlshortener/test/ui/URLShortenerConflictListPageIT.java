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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.repository.test.SolrTestUtils;
import org.xwiki.rest.model.jaxb.Object;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.docker.junit5.servletengine.ServletEngine;
import org.xwiki.test.integration.XWikiExecutor;
import org.xwiki.test.ui.TestUtils;

import com.xwiki.urlshortener.test.po.URLShortenerConflictListPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verify that the short URLs conflicts page displays the right information, while you can also fix these conflicts.
 *
 * @version $Id$
 * @since 1.2.6
 */
@UITest(extraJARs = {
    // The Solr store is not ready yet to be installed as extension
    "org.xwiki.platform:xwiki-platform-eventstream-store-solr:14.10",
    "org.xwiki.platform:xwiki-platform-search-solr-query:14.10" })
class URLShortenerConflictListPageIT
{
    public static final LocalDocumentReference URL_SHORTENER_CLASS_REFERENCE =
        new LocalDocumentReference(Arrays.asList("URLShortener", "Code"), "URLShortenerClass");

    private final DocumentReference test0Reference = new DocumentReference("xwiki", "Space", "Test0");

    private final DocumentReference test1Reference = new DocumentReference("xwiki", "Space", "Test1");

    private final DocumentReference test2Reference = new DocumentReference("xwiki", "Space", "Test2");

    private final DocumentReference test3Reference = new DocumentReference("xwiki", "Space", "Test3");

    private final DocumentReference test4Reference = new DocumentReference("xwiki", "Space", "Test4");

    private final DocumentReference test5Reference = new DocumentReference("xwiki", "Space", "Test5");

    private final Map<DocumentReference, String> testPagesList =
        Map.of(test0Reference, "abcde", test1Reference, "12345", test2Reference, "12345", test3Reference, "54321",
            test4Reference, "54321", test5Reference, "54321");

    @BeforeAll
    void setup(TestUtils testUtils, TestConfiguration testConfiguration) throws Exception
    {
        testUtils.loginAsSuperAdmin();
        testUtils.setGlobalRights("XWiki.XWikiAdminGroup", "", "admin", true);
        testUtils.createAdminUser();
        testPagesList.keySet().forEach((pageReference) -> {
            testUtils.createPage("Space", pageReference.getName(), "", pageReference.getName());
            testUtils.updateObject(pageReference, URL_SHORTENER_CLASS_REFERENCE.toString(), 0, "pageID",
                testPagesList.get(pageReference));
        });
        waitUntilSolrReindex(testUtils, testConfiguration);
    }

    @AfterAll
    void tearDown(TestUtils testUtils)
    {
        testPagesList.keySet().forEach(testUtils::deletePage);
    }

    @Test
    @Order(1)
    void URLConflictListNoSubwikis(TestUtils testUtils)
    {
        testUtils.loginAsAdmin();
        URLShortenerConflictListPage urlShortenerConflictsPage = URLShortenerConflictListPage.gotoPage();
        Map<String, List<String>> conflictsMap = urlShortenerConflictsPage.getConflictsMap();
        assertEquals(Set.of("12345", "54321"), conflictsMap.keySet());
        assertEquals(Set.of(test1Reference.toString(), test2Reference.toString()),
            Set.of(conflictsMap.get("12345").toArray()));
        assertEquals(Set.of(test3Reference.toString(), test4Reference.toString(), test5Reference.toString()),
            Set.of(conflictsMap.get("54321").toArray()));

        // Since all pages are on the same subwiki, all conflicts are critical.
        for (String pageId : conflictsMap.keySet()) {
            for (String pageRef : conflictsMap.get(pageId)) {
                assertEquals(URLShortenerConflictListPage.ConflictType.CRITICAL,
                    urlShortenerConflictsPage.getConflictType(pageId, pageRef));
            }
        }
    }

    @Test
    @Order(2)
    void URLConflictRegenerate(TestUtils testUtils) throws Exception
    {
        testUtils.loginAsAdmin();
        URLShortenerConflictListPage urlShortenerConflictsPage = URLShortenerConflictListPage.gotoPage();
        urlShortenerConflictsPage.regenerateId("12345", test1Reference.toString());
        testUtils.getDriver().waitUntilElementIsVisible(By.cssSelector(".xnotification-done"));
        // Check that the URL was changed.
        Object urlShortenerObject =
            testUtils.rest().get(new ObjectReference("URLShortener.Code.URLShortenerClass[0]", test1Reference));
        String pageIdValue = urlShortenerObject.getProperties().get(0).getValue();
        assertNotEquals("12345", pageIdValue);
    }

    @Test
    @Order(3)
    void URLConflictRegenerateNoAdmin(TestUtils testUtils)
    {
        testUtils.setRights(new DocumentReference("wiki", "URLShortener", "ConflictList"), "", "XWiki.alice", "edit",
            false);
        testUtils.createUserAndLogin("alice", "pass");
        URLShortenerConflictListPage.gotoPage();
        assertEquals(0,
            testUtils.getDriver().findElements(By.cssSelector("button.btn.urlshortener-regenerate-btn")).size());
    }

    private String computedHostURL(TestConfiguration testConfiguration)
    {
        ServletEngine servletEngine = testConfiguration.getServletEngine();
        return String.format("http://%s:%d%s", servletEngine.getIP(), servletEngine.getPort(),
            XWikiExecutor.DEFAULT_CONTEXT);
    }

    private void waitUntilSolrReindex(TestUtils testUtils, TestConfiguration testConfiguration) throws Exception
    {
        System.out.println("Waiting for solr to finish indexing. This may take a while...");
        new SolrTestUtils(testUtils, computedHostURL(testConfiguration)).waitEmpyQueue();
        System.out.println("Solr indexing finished.");
    }
}
