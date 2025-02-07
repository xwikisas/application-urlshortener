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
import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.ObservationContext;
import org.xwiki.observation.event.Event;
import org.xwiki.refactoring.event.DocumentCopiedEvent;
import org.xwiki.refactoring.event.DocumentRenamingEvent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.urlshortener.internal.rest.DefaultURLShortenerResource;

/**
 * Listener to make sure copied documents do not copy also the URLShortener object, which would mean 2 documents have
 * the same ID associated. We also listen to DocumentCreatedEvent, since documents could be created from templates which
 * had the URLShortener object added.
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

    private static final DocumentRenamingEvent DOCUMENT_RENAMING_EVENT = new DocumentRenamingEvent();

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private Logger logger;

    @Inject
    private ObservationContext observationContext;

    /**
     * Default constructor.
     */
    public URLShortenerEventListener()
    {
        super(NAME, new DocumentCopiedEvent(), new DocumentCreatedEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        XWikiContext xcontext = xcontextProvider.get();

        try {
            XWikiDocument targetDoc = xcontext.getWiki().getDocument(getDocumentReference(event), xcontext);

            if (!observationContext.isIn(DOCUMENT_RENAMING_EVENT)
                && targetDoc.getXObject(DefaultURLShortenerResource.URL_SHORTENER_CLASS_REFERENCE) != null) {
                targetDoc.removeXObjects(DefaultURLShortenerResource.URL_SHORTENER_CLASS_REFERENCE);

                // Don't create a history entry.
                targetDoc.setMetaDataDirty(false);
                targetDoc.setContentDirty(false);
                xcontext.getWiki()
                    .saveDocument(targetDoc, "Removed the URL Shortener object specific to the source page.", true,
                        xcontext);
            }
        } catch (XWikiException e) {
            logger.warn("Failed to check copied document for an associated shortened URL. This could lead to problems "
                    + "when associating a shortened URL for the new document. Root cause: [{}]",
                ExceptionUtils.getRootCauseMessage(e));
        }
    }

    private DocumentReference getDocumentReference(Event event)
    {
        if (event instanceof DocumentCreatedEvent) {
            DocumentCreatedEvent createdEvent = (DocumentCreatedEvent) event;
            return createdEvent.getDocumentReference();
        } else {
            DocumentCopiedEvent copyEvent = (DocumentCopiedEvent) event;
            return copyEvent.getTargetReference();
        }
    }
}
