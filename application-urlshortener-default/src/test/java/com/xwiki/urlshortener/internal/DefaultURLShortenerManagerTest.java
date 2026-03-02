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
package com.xwiki.urlshortener.internal;

import java.util.Collections;
import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.urlshortener.URLShortenerException;

import static com.xwiki.urlshortener.internal.DefaultURLShortenerManager.URL_SHORTENER_CLASS_REFERENCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class DefaultURLShortenerManagerTest
{
    public static final String PAGE_ID = "pageID";

    private static final String PAGE_ID_VALUE = "12345";

    @InjectMockComponents
    private DefaultURLShortenerManager urlShortenerManager;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

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
    private BaseObject object;

    @Mock
    private Query solrQuery;

    @Mock
    private QueryResponse solrResponse;

    @BeforeEach
    void beforeEach() throws QueryException
    {
        when(xcontextProvider.get()).thenReturn(xcontext);
        when(xcontext.getWiki()).thenReturn(xwiki);
        when(document.clone()).thenReturn(document);
    }

    @Test
    void createShortenedURLWithObject() throws Exception
    {
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        // The URLShortenerClass object already exists
        when(xwiki.getDocument(currentDocRef, xcontext)).thenReturn(document);
        BaseObject urlObject = new BaseObject();
        urlObject.setStringValue(PAGE_ID, PAGE_ID_VALUE);
        when(document.getXObject(URL_SHORTENER_CLASS_REFERENCE)).thenReturn(urlObject);

        String result = this.urlShortenerManager.createShortenedURL(currentDocRef);
        assertEquals(PAGE_ID_VALUE, result);
    }

    /**
     * Test the case where there isn't any URLShortener object, so the page ID needs to be computed.
     */
    @Test
    void createShortenedURLWithoutObject() throws Exception
    {
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(xwiki.getDocument(currentDocRef, xcontext)).thenReturn(document);

        // The URLShortenerClass object does not exist.
        when(document.getXObject(URL_SHORTENER_CLASS_REFERENCE)).thenReturn(null);
        when(document.newXObject(URL_SHORTENER_CLASS_REFERENCE, xcontext)).thenReturn(object);

        // There is no other document with the generated ID.
        when(queryManager.createQuery(any(String.class), eq("solr"))).thenReturn(solrQuery);
        when(solrQuery.setLimit(anyInt())).thenReturn(solrQuery);
        when(solrQuery.execute()).thenReturn(List.of(solrResponse));
        when(solrResponse.getResults()).thenReturn(new SolrDocumentList());

        this.urlShortenerManager.createShortenedURL(currentDocRef);

        verify(xwiki).saveDocument(document, "Created URL Shortener.", true, xcontext);
        verify(object).set(eq(PAGE_ID), any(String.class), eq(xcontext));
    }

    @Test
    void regenerateShortenedURLWithObject() throws Exception
    {
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(xwiki.getDocument(currentDocRef, xcontext)).thenReturn(document);
        when(this.queryManager.createQuery(any(), eq("solr"))).thenReturn(query);
        when(query.setLimit(anyInt())).thenReturn(query);
        when(query.execute()).thenReturn(Collections.singletonList(queryResponse));
        when(queryResponse.getResults()).thenReturn(new SolrDocumentList());

        // The URLShortenerClass object already exists
        BaseObject urlObject = new BaseObject();
        urlObject.setStringValue(PAGE_ID, PAGE_ID_VALUE);
        when(document.getXObjects(URL_SHORTENER_CLASS_REFERENCE)).thenReturn(List.of(urlObject));

        String response = this.urlShortenerManager.regenerateShortenedURL(currentDocRef, PAGE_ID_VALUE);
        assertNotEquals(PAGE_ID_VALUE, response);
        verify(xwiki).saveDocument(eq(document), anyString(), anyBoolean(), eq(xcontext));
    }

    /**
     * Test the case where there isn't any URLShortener object, so no update is made.
     */
    @Test
    void regenerateShortenedURLWithoutObject() throws Exception
    {
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(xwiki.getDocument(currentDocRef, xcontext)).thenReturn(document);

        // The URLShortenerClass object does not exist.
        when(document.getXObjects(URL_SHORTENER_CLASS_REFERENCE)).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> {
            this.urlShortenerManager.regenerateShortenedURL(currentDocRef, PAGE_ID_VALUE);
        });
        verify(xwiki, never()).saveDocument(eq(document), anyString(), anyBoolean(), eq(xcontext));
    }

    /**
     * Test the case where some exception is thrown.
     */
    @Test
    void regenerateShortenedURLAndExceptionIsThrown() throws Exception
    {
        DocumentReference currentDocRef = new DocumentReference("wiki", "A", "B");
        when(xwiki.getDocument(currentDocRef, xcontext)).thenThrow(XWikiException.class);

        assertThrows(URLShortenerException.class, () -> {
            this.urlShortenerManager.regenerateShortenedURL(currentDocRef, PAGE_ID_VALUE);
        });
        verify(xwiki, never()).saveDocument(eq(document), anyString(), anyBoolean(), eq(xcontext));
    }

    /**
     * Test the case when a document reference is retrieved from a subwiki.
     */
    @Test
    void getDocumentReferenceWithQueryResultsOnSubWiki() throws Exception
    {
        String pageId = "123";
        String wikiId = "test";

        when(queryManager.createQuery(any(String.class), eq(Query.XWQL))).thenReturn(query);
        when(query.bindValue(PAGE_ID, pageId)).thenReturn(query);
        when(query.setLimit(anyInt())).thenReturn(query);
        when(query.setWiki(wikiId)).thenReturn(query);
        String docRef = "test.Space.Page";
        DocumentReference documentReference = new DocumentReference(wikiId, "Space", "Page");
        when(documentReferenceResolver.resolve(docRef)).thenReturn(documentReference);
        when(query.execute()).thenReturn(Collections.singletonList(docRef));

        DocumentReference result = this.urlShortenerManager.getDocumentReference(wikiId, pageId);
        assertEquals(documentReference, result);

        verify(queryManager, never()).createQuery(any(), eq("solr"));
        verify(query).setWiki(eq(wikiId));
    }

    /**
     * Test the case when a document reference is retrieved from the main wiki.
     */
    @Test
    void getDocumentReferenceWithQueryResultsOnMainWiki() throws Exception
    {
        String pageId = "123";
        String wikiId = "";

        when(queryManager.createQuery(any(String.class), eq(Query.XWQL))).thenReturn(query);
        when(query.bindValue(URLShortenerResourceReferenceHandler.PAGE_ID, pageId)).thenReturn(query);
        when(query.setLimit(anyInt())).thenReturn(query);
        String docRef = "wiki.Space.Page";
        DocumentReference documentReference = new DocumentReference("wiki", "Space", "Page");
        when(documentReferenceResolver.resolve(docRef)).thenReturn(documentReference);
        when(query.execute()).thenReturn(Collections.singletonList(docRef));

        DocumentReference result = this.urlShortenerManager.getDocumentReference(wikiId, pageId);
        assertEquals(documentReference, result);

        verify(queryManager, never()).createQuery(any(), eq("solr"));
        verify(query, never()).setWiki(eq(wikiId));
    }

    /**
     * Test the case when a document reference is retrieved using solr.
     */
    @Test
    void getDocumentReferenceWithSolrQueryResults() throws Exception
    {
        String pageId = "123";
        String wikiId = "test";

        when(queryManager.createQuery(any(String.class), eq(Query.XWQL))).thenReturn(query);
        when(query.bindValue(URLShortenerResourceReferenceHandler.PAGE_ID, pageId)).thenReturn(query);
        when(query.setLimit(anyInt())).thenReturn(query);
        when(query.setWiki(wikiId)).thenReturn(query);
        when(query.execute()).thenReturn(Collections.emptyList());
        when(queryManager.createQuery(any(String.class), eq("solr"))).thenReturn(solrQuery);
        String docRef = "test.Space.Page";
        DocumentReference documentReference = new DocumentReference(wikiId, "Space", "Page");
        when(documentReferenceResolver.resolve(docRef)).thenReturn(documentReference);

        SolrDocumentList solrDocumentList = new SolrDocumentList();
        SolrDocument solrDocument = Mockito.mock(SolrDocument.class);
        when(solrDocument.toString()).thenReturn(docRef);
        solrDocumentList.add(new SolrDocument());
        when(solrQuery.setLimit(anyInt())).thenReturn(solrQuery);
        when(solrQuery.execute()).thenReturn(List.of(solrResponse));
        when(solrResponse.getResults()).thenReturn(solrDocumentList);
        when(documentReferenceResolver.resolve(any())).thenReturn(documentReference);

        DocumentReference result = this.urlShortenerManager.getDocumentReference(wikiId, pageId);
        assertEquals(documentReference, result);
        verify(queryManager).createQuery(any(), eq(Query.XWQL));
        verify(queryManager).createQuery(any(), eq("solr"));
    }

    /**
     * Test the case when a document reference is not found.
     */
    @Test
    void getDocumentReferenceWithoutQueryResults() throws Exception
    {
        String pageId = "123";
        String wikiId = "test";

        when(queryManager.createQuery(any(String.class), eq(Query.XWQL))).thenReturn(query);
        when(query.bindValue(URLShortenerResourceReferenceHandler.PAGE_ID, pageId)).thenReturn(query);
        when(query.setLimit(anyInt())).thenReturn(query);
        when(query.setWiki(wikiId)).thenReturn(query);
        when(query.execute()).thenReturn(Collections.emptyList());
        when(queryManager.createQuery(any(String.class), eq("solr"))).thenReturn(solrQuery);
        when(solrQuery.setLimit(anyInt())).thenReturn(solrQuery);
        when(solrQuery.execute()).thenReturn(List.of(solrResponse));
        when(solrResponse.getResults()).thenReturn(new SolrDocumentList());

        DocumentReference result = urlShortenerManager.getDocumentReference(wikiId, pageId);
        assertNull(result);
        verify(queryManager).createQuery(any(), eq(Query.XWQL));
        verify(queryManager).createQuery(any(), eq("solr"));
    }

    /**
     * Test the case when a document an error is thrown when retrieving the document reference.
     */
    @Test
    void getDocumentReferenceWithException() throws Exception
    {
        String pageId = "123";
        String wikiId = "test";

        when(queryManager.createQuery(any(String.class), eq(Query.XWQL))).thenThrow(
            new QueryException("Error", null, null));

        assertThrows(URLShortenerException.class, () -> {
            urlShortenerManager.getDocumentReference(wikiId, pageId);
        });
    }
}
