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
package com.xwiki.urlshortener.internal.rest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.web.XWikiResponse;

import ch.qos.logback.classic.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultURLShortenerResource}.
 *
 * @version $Id$
 * @since 1.0
 */
@ComponentTest
public class DefaultURLShortenerResourceTest
{
    public static final String DEFAULT_STATEMENT = "property.URLShortener.Code.URLShortenerClass.pageID:12345";

    public static final String PAGE_ID = "pageID";

    private static final LocalDocumentReference URL_SHORTENER_CLASS_REFERENCE =
        new LocalDocumentReference(Arrays.asList("URLShortener", "Code"), "URLShortenerClass");

    private static final String PAGE_ID_VALUE = "12345";

    @RegisterExtension
    static LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.ERROR);

    @InjectMockComponents
    private DefaultURLShortenerResource urlShortenerResource;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    private EntityReferenceResolver<SolrDocument> solrEntityReferenceResolver;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @Mock
    private Query query;

    @Mock
    private QueryResponse queryResponse;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument document;

    @Mock
    private XWikiResponse xwikiResponse;

    @Mock
    private BaseObject object;

    @BeforeEach
    void beforeEach() throws QueryException
    {
        when(xcontextProvider.get()).thenReturn(xcontext);
        when(xcontext.getWiki()).thenReturn(xwiki);
    }

    /**
     * Test the case where the URLShortenerClass object exists.
     */
    @Test
    void redirectWithShortenedURL() throws Exception
    {
        EntityReference docReference = new EntityReference("ref", EntityType.DOCUMENT);
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        SolrDocument solrDocument = new SolrDocument();
        solrDocumentList.add(solrDocument);
        when(this.queryManager.createQuery(DEFAULT_STATEMENT, "solr")).thenReturn(query);
        when(query.execute()).thenReturn(Collections.singletonList(queryResponse));
        when(queryResponse.getResults()).thenReturn(solrDocumentList);
        when(solrEntityReferenceResolver.resolve(solrDocument, EntityType.DOCUMENT)).thenReturn(docReference);

        when(xwiki.getDocument(docReference, xcontext)).thenReturn(document);
        when(document.getURL("view", xcontext)).thenReturn("myURL");

        when(xcontext.getResponse()).thenReturn(xwikiResponse);

        Response response = Response.status(301).build();
        Response actual = this.urlShortenerResource.redirect(PAGE_ID_VALUE);
        assertEquals(response.getStatus(), actual.getStatus());
    }

    /**
     * Test the case where the URLShortenerClass object doesn't exist.
     */
    @Test
    void redirectWithoutShortenedURL() throws Exception
    {
        // Empty solr list response.
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        when(this.queryManager.createQuery(DEFAULT_STATEMENT, "solr")).thenReturn(query);
        when(query.execute()).thenReturn(Collections.singletonList(queryResponse));
        when(queryResponse.getResults()).thenReturn(solrDocumentList);

        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> this.urlShortenerResource.redirect(PAGE_ID_VALUE));
        assertEquals(404, exception.getResponse().getStatus());
    }

    /**
     * Test the case where there is already an URLShortener object, so that value is returned.
     */
    @Test
    void createShortenedURLWithObject() throws Exception
    {
        String currentDocRefStr = "A.B";
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(documentReferenceResolver.resolve(currentDocRefStr)).thenReturn(currentDocRef);
        when(authorization.hasAccess(Right.VIEW, currentDocRef)).thenReturn(true);
        when(xwiki.getDocument(currentDocRef, xcontext)).thenReturn(document);

        // The URLShortenerClass object already exists
        BaseObject urlObject = new BaseObject();
        urlObject.setStringValue(PAGE_ID, PAGE_ID_VALUE);
        when(document.getXObject(URL_SHORTENER_CLASS_REFERENCE)).thenReturn(urlObject);

        Response response = this.urlShortenerResource.createShortenedURL(currentDocRefStr);
        assertEquals("{pageID=12345}", String.valueOf(response.getEntity()));
    }

    /**
     * Test the case where there isn't any URLShortener object, so the page ID needs to be computed.
     */
    @Test
    void createShortenedURLWithoutObject() throws Exception
    {
        String currentDocRefStr = "A.B";
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(documentReferenceResolver.resolve(currentDocRefStr)).thenReturn(currentDocRef);
        when(authorization.hasAccess(Right.VIEW, currentDocRef)).thenReturn(true);
        when(xwiki.getDocument(currentDocRef, xcontext)).thenReturn(document);

        // The URLShortenerClass object does not exist.
        when(document.getXObject(URL_SHORTENER_CLASS_REFERENCE)).thenReturn(null);
        when(document.newXObject(URL_SHORTENER_CLASS_REFERENCE, xcontext)).thenReturn(object);

        // There is no other document with the generated ID.
        SolrDocumentList solrDocumentList = new SolrDocumentList();
        when(queryManager.createQuery(any(String.class), eq("solr"))).thenReturn(query);
        when(query.execute()).thenReturn(Collections.singletonList(queryResponse));
        when(queryResponse.getResults()).thenReturn(solrDocumentList);

        this.urlShortenerResource.createShortenedURL(currentDocRefStr);

        verify(xwiki).saveDocument(document, "Created URL Shortener.", true, xcontext);
        verify(object).set(eq(PAGE_ID), any(String.class), eq(xcontext));
    }

    /**
     * Test the case where the current user does not have view access on the requested document.
     */
    @Test
    void createShortenedURLWithoutViewAccess() throws Exception
    {
        String currentDocRefStr = "A.B";
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(documentReferenceResolver.resolve(currentDocRefStr)).thenReturn(currentDocRef);
        when(authorization.hasAccess(Right.VIEW, currentDocRef)).thenReturn(false);

        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.urlShortenerResource.createShortenedURL(currentDocRefStr));
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), exception.getResponse().getStatus());
    }

    /**
     * Test the case where there is an error while computing the page ID.
     */
    @Test
    void createShortenedURLWithErrorOnPageID() throws Exception
    {
        String currentDocRefStr = "A.B";
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(documentReferenceResolver.resolve(currentDocRefStr)).thenReturn(currentDocRef);
        when(authorization.hasAccess(Right.VIEW, currentDocRef)).thenReturn(true);
        when(xwiki.getDocument(currentDocRef, xcontext)).thenReturn(document);
        when(document.getDocumentReference()).thenReturn(currentDocRef);

        // The URLShortenerClass object does not exist.
        when(document.getXObject(URL_SHORTENER_CLASS_REFERENCE)).thenReturn(null);
        when(document.newXObject(URL_SHORTENER_CLASS_REFERENCE, xcontext)).thenThrow(new XWikiException());

        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.urlShortenerResource.createShortenedURL(currentDocRefStr));
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), exception.getResponse().getStatus());

        assertEquals(1, logCapture.size());
        assertEquals(Level.ERROR, logCapture.getLogEvent(0).getLevel());
        assertEquals(String.format(
            "Error while computing the shortened URL for document [%s]. Root cause: [XWikiException: Error number"
                + " 0 in 0]", currentDocRef.toString()), logCapture.getMessage(0));
    }

    /**
     * Test the case where the requested URLShortener object exists, so that value is replaced.
     */
    @Test
    void regenerateShortenedURLWithObject() throws Exception
    {
        String currentDocRefStr = "A.B";
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(documentReferenceResolver.resolve(currentDocRefStr)).thenReturn(currentDocRef);
        when(authorization.hasAccess(Right.EDIT, currentDocRef)).thenReturn(true);
        when(xwiki.getDocument(currentDocRef, xcontext)).thenReturn(document);
        when(this.queryManager.createQuery(any(), eq("solr"))).thenReturn(query);
        when(query.execute()).thenReturn(Collections.singletonList(queryResponse));
        when(queryResponse.getResults()).thenReturn(new SolrDocumentList());

        // The URLShortenerClass object already exists
        BaseObject urlObject = new BaseObject();
        urlObject.setStringValue(PAGE_ID, PAGE_ID_VALUE);
        when(document.getXObjects(URL_SHORTENER_CLASS_REFERENCE)).thenReturn(List.of(urlObject));

        Response response = this.urlShortenerResource.regenerateShortenedURL(currentDocRefStr, PAGE_ID_VALUE);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotEquals("{pageID=12345}", String.valueOf(response.getEntity()));
        verify(xwiki).saveDocument(eq(document), anyString(), anyBoolean(), eq(xcontext));
    }

    /**
     * Test the case where there isn't any URLShortener object, so no update is made.
     */
    @Test
    void regenerateShortenedURLWithoutObject() throws Exception
    {
        String currentDocRefStr = "A.B";
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(documentReferenceResolver.resolve(currentDocRefStr)).thenReturn(currentDocRef);
        when(authorization.hasAccess(Right.EDIT, currentDocRef)).thenReturn(true);
        when(xwiki.getDocument(currentDocRef, xcontext)).thenReturn(document);

        // The URLShortenerClass object does not exist.
        when(document.getXObjects(URL_SHORTENER_CLASS_REFERENCE)).thenReturn(List.of());

        Response response = this.urlShortenerResource.regenerateShortenedURL(currentDocRefStr, PAGE_ID_VALUE);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        verify(xwiki, never()).saveDocument(eq(document), anyString(), anyBoolean(), eq(xcontext));
    }

    /**
     * Test the case where the current user does not have edit access on the requested document.
     */
    @Test
    void regenerateShortenedURLWithoutViewAccess() throws Exception
    {
        String currentDocRefStr = "A.B";
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(documentReferenceResolver.resolve(currentDocRefStr)).thenReturn(currentDocRef);
        when(authorization.hasAccess(Right.EDIT, currentDocRef)).thenReturn(false);

        WebApplicationException exception = assertThrows(WebApplicationException.class,
            () -> this.urlShortenerResource.regenerateShortenedURL(currentDocRefStr, PAGE_ID_VALUE));
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), exception.getResponse().getStatus());
    }
}
