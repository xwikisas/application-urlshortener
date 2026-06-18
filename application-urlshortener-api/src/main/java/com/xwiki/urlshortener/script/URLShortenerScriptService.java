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
package com.xwiki.urlshortener.script;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.AccessDeniedException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xwiki.urlshortener.URLShortenerException;
import com.xwiki.urlshortener.URLShortenerManager;

/**
 * Script service providing the URLShortener functionalities to the xwiki pages.
 *
 * @version $Id$
 * @since 1.3.4
 */
@Component
@Named("urlshortener")
@Singleton
public class URLShortenerScriptService implements ScriptService
{
    @Inject
    private ContextualAuthorizationManager contextualAuthorizationManager;

    @Inject
    private URLShortenerManager urlShortenerManager;

    /**
     * Associates an unique identifier to a xwiki page and returns it. This unique identifier can be used to retrieve
     * the document.
     *
     * @param documentReference the reference on the document for which an unique identifier will be created.
     * @return the unique identifier that can be used to retrieve the document reference.
     * @throws URLShortenerException if the user does not have view rights on the page.
     */
    public String createShortenedURL(DocumentReference documentReference) throws URLShortenerException
    {
        try {
            contextualAuthorizationManager.checkAccess(Right.VIEW, documentReference);
        } catch (AccessDeniedException e) {
            throw new URLShortenerException(
                String.format("Can't create shortened url for document [%s]", documentReference), e);
        }
        return urlShortenerManager.createShortenedURL(documentReference);
    }

    /**
     * Replace the given pageID for the given document with a newly generated one.
     *
     * @param documentReference the reference of the document that already has an unique id associated.
     * @param oldPageID the pageID, associated to the given document reference, to be replaced, as {@code String}.
     * @return the new unique identifier associated to the xwiki page.
     * @throws IllegalStateException if the passed document reference does not have the old id associated.
     * @throws URLShortenerException if the user does not have view rights on the page or if the regeneration
     *     process fails.
     */
    public String regenerateShortenedURL(DocumentReference documentReference, String oldPageID)
        throws IllegalStateException, URLShortenerException
    {
        try {
            contextualAuthorizationManager.checkAccess(Right.VIEW, documentReference);
        } catch (AccessDeniedException e) {
            throw new URLShortenerException(
                String.format("Can't regenerate shortened url for page [%s].", documentReference), e);
        }
        return urlShortenerManager.regenerateShortenedURL(documentReference, oldPageID);
    }

    /**
     * Retrieves the document reference identified by the given page id.
     *
     * @param wiki the id of the wiki where to look for the document reference.
     * @param id the unique id that is associated to an existing xwiki page.
     * @return the document reference that is associated to the unique id or null if nothing was found.
     * @throws URLShortenerException if the searching process failed or if the user does not have view rights on the
     *     page.
     */
    DocumentReference getDocumentReference(String wiki, String id) throws URLShortenerException
    {
        DocumentReference documentReference = urlShortenerManager.getDocumentReference(wiki, id);
        try {
            contextualAuthorizationManager.checkAccess(Right.VIEW, documentReference);
        } catch (AccessDeniedException e) {
            throw new URLShortenerException(
                String.format("User does not have view right on document with id [%s].", id), e);
        }

        return documentReference;
    }
}
