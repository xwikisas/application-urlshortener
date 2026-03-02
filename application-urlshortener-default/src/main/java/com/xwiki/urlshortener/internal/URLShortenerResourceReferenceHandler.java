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
public class URLShortenerResourceReferenceHandler extends AbstractResourceReferenceHandler<ResourceType>
{
    /**
     * Page ID.
     */
    public static final String PAGE_ID = "pageID";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private Container container;

    @Inject
    private URLShortenerManager urlShortenerManager;

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
                // Preserve query parameters from the shortened URL request.
                String queryString = URLEncodedUtils.format(urlResourceReference.getParameters().entrySet().stream()
                    .flatMap(
                        entry -> entry.getValue().stream().map(value -> new BasicNameValuePair(entry.getKey(), value)))
                    .collect(Collectors.toList()), StandardCharsets.UTF_8);

                String stringURL = xcontext.getWiki().getURL(documentReference, "view", queryString, "", xcontext);
                // Let the redirect action to check the view right on the document.
                response.sendRedirect(stringURL);
            } else {
                response.sendError(404,
                    String.format("No document is associated to the given ID: [%s]", urlResourceReference.getPageId()));
            }
        } catch (Exception e) {
            throw new ResourceReferenceHandlerException(
                String.format("Failed to handle resource [%s]", URLShortenerResourceReference.TYPE), e);
        }

        chain.handleNext(reference);
    }
}
