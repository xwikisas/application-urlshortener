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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
import org.xwiki.container.servlet.ServletResponse;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.resource.AbstractResourceReferenceHandler;
import org.xwiki.resource.ResourceReference;
import org.xwiki.resource.ResourceReferenceHandlerChain;
import org.xwiki.resource.ResourceReferenceHandlerException;
import org.xwiki.resource.ResourceType;
import org.xwiki.resource.annotations.Authenticate;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.urlshortener.URLShortenerManager;

/**
 * URL Resource Handler for redirecting from a shortened URL to the actual document, which is uniquely identified by an
 * ID.
 *
 * @version $Id:$
 * @since 1.2
 */
@Component
@Named(URLShortenerResourceReference.HINT)
@Singleton
@Authenticate
public class URLShortenerResourceReferenceHandler extends AbstractResourceReferenceHandler<ResourceType>
{
    /**
     * Page ID.
     */
    public static final String PAGE_ID = "pageID";

    /**
     * No document is associated to the given ID.
     */
    public static final String NO_DOCUMENT = "No document is associated to the given ID: [%s]";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private Container container;

    @Inject
    private URLShortenerManager urlShortenerManager;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Override
    public List<ResourceType> getSupportedResourceReferences()
    {
        return Collections.singletonList(URLShortenerResourceReference.TYPE);
    }

    @Override
    public void handle(ResourceReference reference, ResourceReferenceHandlerChain chain)
        throws ResourceReferenceHandlerException
    {
        HttpServletResponse response = ((ServletResponse) this.container.getResponse()).getHttpServletResponse();
        try {
            URLShortenerResourceReference urlResourceReference = (URLShortenerResourceReference) reference;
            DocumentReference documentReference =
                urlShortenerManager.getDocumentReference(urlResourceReference.getWikiId(),
                    urlResourceReference.getPageId());
            if (null != documentReference) {
                XWikiContext xcontext = xcontextProvider.get();
                // Check the view right on the document before emitting the redirect. Return 404 to avoid revealing
                // whether the document exists or not.
                if (!authorization.hasAccess(Right.VIEW, documentReference)) {
                    response.sendError(404, String.format(NO_DOCUMENT, urlResourceReference.getPageId()));
                    chain.handleNext(reference);
                    return;
                }
                // Preserve query parameters from the shortened URL request.
                String queryString = URLEncodedUtils.format(urlResourceReference.getParameters().entrySet().stream()
                    .flatMap(
                        entry -> entry.getValue().stream().map(value -> new BasicNameValuePair(entry.getKey(), value)))
                    .collect(Collectors.toList()), StandardCharsets.UTF_8);

                String stringURL = xcontext.getWiki().getURL(documentReference, "view", queryString, "", xcontext);
                response.sendRedirect(stringURL);
            } else {
                response.sendError(404, String.format(NO_DOCUMENT, urlResourceReference.getPageId()));
            }
        } catch (Exception e) {
            throw new ResourceReferenceHandlerException(
                String.format("Failed to handle resource [%s]", URLShortenerResourceReference.TYPE), e);
        }

        chain.handleNext(reference);
    }
}
