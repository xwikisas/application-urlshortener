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

import javax.inject.Named;
import javax.inject.Provider;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.container.Container;
import org.xwiki.container.servlet.ServletResponse;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.resource.ResourceReferenceHandlerChain;
import org.xwiki.resource.ResourceReferenceHandlerException;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class URLShortenerResourceReferenceHandlerTest
{
    @InjectMockComponents
    private URLShortenerResourceReferenceHandler resourceReferenceHandler;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private Container container;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWiki xwiki;

    @Mock
    private ServletResponse response;

    @Mock
    private HttpServletResponse httpServletServletResponse;

    @Mock
    private ResourceReferenceHandlerChain handlerChain;

    @Mock
    private Query query;

    @BeforeEach
    void beforeEach()
    {
        when(xcontextProvider.get()).thenReturn(xcontext);
        when(xcontext.getWiki()).thenReturn(xwiki);

        when(this.container.getResponse()).thenReturn(response);
        when(response.getHttpServletResponse()).thenReturn(httpServletServletResponse);
    }

    @Test
    void handleWithQueryResultsOnSubWiki() throws Exception
    {
        String pageId = "123";
        String wikiId = "wiki";

        when(queryManager.createQuery(any(String.class), eq(Query.XWQL))).thenReturn(query);
        when(query.bindValue(URLShortenerResourceReferenceHandler.PAGE_ID, pageId)).thenReturn(query);
        when(query.setWiki(wikiId)).thenReturn(query);
        String docRef = "wiki.Space.Page";
        DocumentReference documentReference = new DocumentReference(wikiId, "Space", "Page");
        when(documentReferenceResolver.resolve(docRef)).thenReturn(documentReference);
        when(query.execute()).thenReturn(Collections.singletonList(docRef));

        String docURL = "docURL";
        when(xwiki.getURL(documentReference, xcontext)).thenReturn(docURL);

        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(wikiId, pageId);
        resourceReferenceHandler.handle(resourceReference, handlerChain);

        verify(httpServletServletResponse, times(1)).sendRedirect(docURL);
        verify(handlerChain, times(1)).handleNext(resourceReference);
    }

    @Test
    void handleWithQueryResultsOnMainWiki() throws Exception
    {
        String pageId = "123";
        String wikiId = "";

        when(queryManager.createQuery(any(String.class), eq(Query.XWQL))).thenReturn(query);
        when(query.bindValue(URLShortenerResourceReferenceHandler.PAGE_ID, pageId)).thenReturn(query);
        String docRef = "wiki.Space.Page";
        DocumentReference documentReference = new DocumentReference("wiki", "Space", "Page");
        when(documentReferenceResolver.resolve(docRef)).thenReturn(documentReference);
        when(query.execute()).thenReturn(Collections.singletonList(docRef));

        String docURL = "docURL";
        when(xwiki.getURL(documentReference, xcontext)).thenReturn(docURL);

        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(wikiId, pageId);
        resourceReferenceHandler.handle(resourceReference, handlerChain);

        verify(httpServletServletResponse, times(1)).sendRedirect(docURL);
        verify(handlerChain, times(1)).handleNext(resourceReference);
        verify(query, times(0)).setWiki(wikiId);
    }

    @Test
    void handleWithoutQueryResults() throws Exception
    {
        String pageId = "123";
        String wikiId = "wiki";

        when(queryManager.createQuery(any(String.class), eq(Query.XWQL))).thenReturn(query);
        when(query.bindValue(URLShortenerResourceReferenceHandler.PAGE_ID, pageId)).thenReturn(query);
        when(query.setWiki(wikiId)).thenReturn(query);
        when(query.execute()).thenReturn(Collections.emptyList());

        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(wikiId, pageId);
        resourceReferenceHandler.handle(resourceReference, handlerChain);

        verify(httpServletServletResponse, times(0)).sendRedirect(any(String.class));
        verify(httpServletServletResponse, times(1)).sendError(404,
            String.format("No document is associated to the given ID: [%s]", resourceReference.getPageId()));
        verify(handlerChain, times(1)).handleNext(resourceReference);
    }

    @Test
    void handleWithException() throws Exception
    {
        String pageId = "123";
        String wikiId = "wiki";

        when(queryManager.createQuery(any(String.class), eq(Query.XWQL))).thenThrow(
            new QueryException("Error", null, null));

        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(wikiId, pageId);
        ResourceReferenceHandlerException resourceReferenceHandlerException =
            assertThrows(ResourceReferenceHandlerException.class, () -> {
                this.resourceReferenceHandler.handle(resourceReference, handlerChain);
            });

        assertEquals(String.format("Failed to handle resource [%s]", URLShortenerResourceReference.TYPE),
            resourceReferenceHandlerException.getMessage());
        assertEquals(QueryException.class, resourceReferenceHandlerException.getCause().getClass());
    }
}
