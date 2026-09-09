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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.inject.Provider;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.container.Container;
import org.xwiki.container.servlet.ServletResponse;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.resource.ResourceReferenceHandlerChain;
import org.xwiki.resource.ResourceReferenceHandlerException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;
import com.xwiki.urlshortener.URLShortenerException;
import com.xwiki.urlshortener.URLShortenerManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private Container container;

    @MockComponent
    private URLShortenerManager urlShortenerManager;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    @Mock
    private XWikiRequest request;

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

    @BeforeEach
    void beforeEach()
    {
        when(xcontextProvider.get()).thenReturn(xcontext);
        when(xcontext.getWiki()).thenReturn(xwiki);
        when(xcontext.getRequest()).thenReturn(request);

        when(this.container.getResponse()).thenReturn(response);
        when(response.getHttpServletResponse()).thenReturn(httpServletServletResponse);
    }

    @Test
    void handleWithQueryString() throws Exception
    {
        String pageId = "123";
        String wikiId = "test";

        DocumentReference documentReference = new DocumentReference(wikiId, "Space", "Page");

        when(urlShortenerManager.getDocumentReference(wikiId, pageId)).thenReturn(documentReference);
        when(authorization.hasAccess(Right.VIEW, documentReference)).thenReturn(true);

        String docURL = "docURL";
        when(xwiki.getURL(eq(documentReference), any(String.class), any(String.class), any(String.class),
            eq(xcontext))).thenReturn(docURL);

        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(wikiId, pageId);
        resourceReference.addParameter("test", "testValue1");
        resourceReference.addParameter("test", "testValue2%3A");
        resourceReferenceHandler.handle(resourceReference, handlerChain);

        verify(xwiki, times(1)).getURL(documentReference, "view", "test=testValue1&test=testValue2%253A", "", xcontext);
        verify(httpServletServletResponse, times(1)).sendRedirect(docURL);
    }

    @Test
    void handleWithoutQueryResults() throws Exception
    {
        String pageId = "123";
        String wikiId = "test";

        when(urlShortenerManager.getDocumentReference(wikiId, pageId)).thenReturn(null);

        String documentDoesNotExistURL = stubDocumentDoesNotExistURL(wikiId, pageId);

        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(wikiId, pageId);
        resourceReferenceHandler.handle(resourceReference, handlerChain);

        verify(httpServletServletResponse, times(1)).sendRedirect(documentDoesNotExistURL);
        verify(httpServletServletResponse, times(0)).sendError(anyInt(), any(String.class));
        verify(handlerChain, times(1)).handleNext(resourceReference);
    }

    @Test
    void handleWithoutViewAccess() throws Exception
    {
        String pageId = "123";
        String wikiId = "test";

        DocumentReference documentReference = new DocumentReference(wikiId, "Space", "Page");

        when(xcontext.getUserReference()).thenReturn(new DocumentReference(wikiId, "XWiki", "Alice"));
        when(urlShortenerManager.getDocumentReference(wikiId, pageId)).thenReturn(documentReference);
        when(authorization.hasAccess(Right.VIEW, documentReference)).thenReturn(false);

        String documentDoesNotExistURL = stubDocumentDoesNotExistURL(wikiId, pageId);

        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(wikiId, pageId);
        resourceReferenceHandler.handle(resourceReference, handlerChain);

        verify(httpServletServletResponse, times(1)).sendRedirect(documentDoesNotExistURL);
        verify(httpServletServletResponse, times(0)).sendError(anyInt(), any(String.class));
        verify(handlerChain, times(1)).handleNext(resourceReference);
    }

    @Test
    void handleWithoutViewAccessAsGuest() throws Exception
    {
        String pageId = "123";
        String wikiId = "test";

        DocumentReference documentReference = new DocumentReference(wikiId, "Space", "Page");

        when(xcontext.getUserReference()).thenReturn(null);
        when(urlShortenerManager.getDocumentReference(wikiId, pageId)).thenReturn(documentReference);
        when(authorization.hasAccess(Right.VIEW, documentReference)).thenReturn(false);

        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(wikiId, pageId);
        String shortURL = "http://localhost:8080/xwiki/short/test/" + pageId;
        when(request.getRequestURL()).thenReturn(new StringBuffer(shortURL));
        when(request.getQueryString()).thenReturn(null);
        String loginURL = "loginURL";
        when(xwiki.getURL(eq(new DocumentReference(wikiId, "XWiki", "XWikiLogin")), eq("login"),
            eq("xredirect=" + URLEncoder.encode(shortURL, StandardCharsets.UTF_8)), eq(""), eq(xcontext))).thenReturn(
            loginURL);

        resourceReferenceHandler.handle(resourceReference, handlerChain);

        verify(httpServletServletResponse, times(1)).sendRedirect(loginURL);
        verify(httpServletServletResponse, times(0)).sendError(anyInt(), any(String.class));
        verify(handlerChain, times(1)).handleNext(resourceReference);
    }

    @Test
    void handleWithoutViewAccessOnMainWiki() throws Exception
    {
        String pageId = "123";
        String wikiId = "";

        when(xcontext.getWikiId()).thenReturn("xwiki");
        when(xcontext.getUserReference()).thenReturn(new DocumentReference("xwiki", "XWiki", "Alice"));

        DocumentReference documentReference = new DocumentReference("xwiki", "Space", "Page");

        when(urlShortenerManager.getDocumentReference(wikiId, pageId)).thenReturn(documentReference);
        when(authorization.hasAccess(Right.VIEW, documentReference)).thenReturn(false);

        String documentDoesNotExistURL = stubDocumentDoesNotExistURL("xwiki", pageId);

        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(wikiId, pageId);
        resourceReferenceHandler.handle(resourceReference, handlerChain);

        verify(httpServletServletResponse, times(1)).sendRedirect(documentDoesNotExistURL);
        verify(httpServletServletResponse, times(0)).sendError(anyInt(), any(String.class));
        verify(handlerChain, times(1)).handleNext(resourceReference);
    }

    @Test
    void handleWithException() throws Exception
    {
        String pageId = "123";
        String wikiId = "test";

        when(urlShortenerManager.getDocumentReference(wikiId, pageId)).thenThrow(URLShortenerException.class);
        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(wikiId, pageId);
        ResourceReferenceHandlerException resourceReferenceHandlerException =
            assertThrows(ResourceReferenceHandlerException.class, () -> {
                this.resourceReferenceHandler.handle(resourceReference, handlerChain);
            });

        assertEquals(String.format("Failed to handle resource [%s]", URLShortenerResourceReference.TYPE),
            resourceReferenceHandlerException.getMessage());
        assertEquals(URLShortenerException.class, resourceReferenceHandlerException.getCause().getClass());
    }

    private String stubDocumentDoesNotExistURL(String wikiId, String pageId)
    {
        DocumentReference documentDoesNotExist = new DocumentReference(wikiId, "URLShortener", "DocumentDoesNotExist");
        String documentDoesNotExistURL = "documentDoesNotExistURL";
        String queryString = "shortURLID=" + pageId;
        when(xwiki.getURL(eq(documentDoesNotExist), eq("view"), eq(queryString), eq(""), eq(xcontext))).thenReturn(
            documentDoesNotExistURL);
        return documentDoesNotExistURL;
    }
}
