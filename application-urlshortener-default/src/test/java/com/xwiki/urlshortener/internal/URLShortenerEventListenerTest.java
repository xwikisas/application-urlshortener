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

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.refactoring.event.DocumentCopiedEvent;
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
import com.xwiki.urlshortener.internal.rest.DefaultURLShortenerResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class URLShortenerEventListenerTest
{
    @InjectMockComponents
    private URLShortenerEventListener eventListener;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument targetDoc;

    @BeforeEach
    void beforeEach()
    {
        when(xcontextProvider.get()).thenReturn(xcontext);
        when(xcontext.getWiki()).thenReturn(xwiki);
    }

    @Test
    void onEventWithoutObject() throws Exception
    {
        DocumentReference sourceRef = new DocumentReference("wiki", "Space", "Test1");
        DocumentReference targetRef = new DocumentReference("wiki", "Space", "Test2");
        DocumentCopiedEvent event = new DocumentCopiedEvent(sourceRef, targetRef);

        when(xwiki.getDocument(targetRef, xcontext)).thenReturn(targetDoc);
        when(targetDoc.getXObject(DefaultURLShortenerResource.URL_SHORTENER_CLASS_REFERENCE)).thenReturn(null);

        eventListener.onEvent(event, null, null);

        verify(targetDoc, times(0)).removeXObjects(DefaultURLShortenerResource.URL_SHORTENER_CLASS_REFERENCE);
    }

    @Test
    void onEventWithObject() throws Exception
    {
        DocumentReference sourceRef = new DocumentReference("wiki", "Space", "Test1");
        DocumentReference targetRef = new DocumentReference("wiki", "Space", "Test2");
        DocumentCopiedEvent event = new DocumentCopiedEvent(sourceRef, targetRef);

        when(xwiki.getDocument(targetRef, xcontext)).thenReturn(targetDoc);
        when(targetDoc.getXObject(DefaultURLShortenerResource.URL_SHORTENER_CLASS_REFERENCE)).thenReturn(
            new BaseObject());

        eventListener.onEvent(event, null, null);

        verify(targetDoc, times(1)).removeXObjects(DefaultURLShortenerResource.URL_SHORTENER_CLASS_REFERENCE);
    }

    @Test
    void onEventWithError() throws Exception
    {
        DocumentReference sourceRef = new DocumentReference("wiki", "Space", "Test1");
        DocumentReference targetRef = new DocumentReference("wiki", "Space", "Test2");
        DocumentCopiedEvent event = new DocumentCopiedEvent(sourceRef, targetRef);

        when(xwiki.getDocument(targetRef, xcontext)).thenThrow(new XWikiException());

        eventListener.onEvent(event, null, null);

        verify(targetDoc, times(0)).removeXObjects(DefaultURLShortenerResource.URL_SHORTENER_CLASS_REFERENCE);
        assertEquals("Failed to check copied document for an associated shortened URL. This could lead to problems "
            + "when associating a shortened URL for the new document. Root cause: [XWikiException: Error number 0"
            + " in 0]", logCapture.getMessage(0));
    }
}
