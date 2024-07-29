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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.refactoring.event.DocumentCopiedEvent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.urlshortener.internal.rest.DefaultURLShortenerResource;

/**
 * Listener to make sure copied documents do not copy also the URLShortener object, which would mean 2 documents have
 * the same ID associated.
 *
 * @version $Id$
 * @since 1.1.1
 */
@Component
@Singleton
@Named(URLShortenerEventListener.NAME)
public class URLShortenerEventListener extends AbstractEventListener
{
    /**
     * Event name.
     */
    public static final String NAME = "URLShortenerEventListener";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private Logger logger;

    /**
     * Default constructor.
     */
    public URLShortenerEventListener()
    {
        super(NAME, new DocumentCopiedEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiContext xcontext = xcontextProvider.get();
        DocumentCopiedEvent copyEvent = (DocumentCopiedEvent) event;
        try {
            XWikiDocument targetDoc = xcontext.getWiki().getDocument(copyEvent.getTargetReference(), xcontext);
            if (targetDoc.getXObject(DefaultURLShortenerResource.URL_SHORTENER_CLASS_REFERENCE) != null) {
                targetDoc.removeXObjects(DefaultURLShortenerResource.URL_SHORTENER_CLASS_REFERENCE);
            }
        } catch (XWikiException e) {
            logger.warn("Failed to check copied document for an associated shortened URL. This could lead to problems "
                    + "when associating a shortened URL for the new document. Root cause: [{}]",
                ExceptionUtils.getRootCauseMessage(e));
        }
    }
}
