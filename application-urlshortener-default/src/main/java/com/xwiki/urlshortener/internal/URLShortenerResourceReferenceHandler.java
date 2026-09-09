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
     * View action.
     */
    private static final String VIEW_ACTION = "view";

    /**
     * Application parent space.
     */
    private static final String URLSHORTENER_SPACE = "URLShortener";

    /**
     * The name of the page shown when the shortened URL cannot be resolved to a document the user is allowed to view.
     */
    private static final String DOCUMENT_DOES_NOT_EXIST_PAGE = "DocumentDoesNotExist";

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
        try {
            HttpServletResponse response = ((ServletResponse) this.container.getResponse()).getHttpServletResponse();
            URLShortenerResourceReference urlResourceReference = (URLShortenerResourceReference) reference;
            XWikiContext xcontext = xcontextProvider.get();

            DocumentReference documentReference =
                urlShortenerManager.getDocumentReference(urlResourceReference.getWikiId(),
                    urlResourceReference.getPageId());
            if (null == documentReference) {
                redirectToDocumentDoesNotExist(response, xcontext, urlResourceReference);
            } else if (!authorization.hasAccess(Right.VIEW, documentReference)) {
                redirectUnauthorized(response, xcontext, urlResourceReference);
            } else {
                // Preserve query parameters from the shortened URL request.
                String queryString = URLEncodedUtils.format(urlResourceReference.getParameters().entrySet().stream()
                    .flatMap(
                        entry -> entry.getValue().stream().map(value -> new BasicNameValuePair(entry.getKey(), value)))
                    .collect(Collectors.toList()), StandardCharsets.UTF_8);

                String stringURL = xcontext.getWiki().getURL(documentReference, VIEW_ACTION, queryString, "", xcontext);
                response.sendRedirect(stringURL);
            }
        } catch (Exception e) {
            throw new ResourceReferenceHandlerException(
                String.format("Failed to handle resource [%s]", URLShortenerResourceReference.TYPE), e);
        }

        chain.handleNext(reference);
    }

    /**
     * Redirect an authenticated user without view rights, or a guest, to the appropriate page without leaking the
     * existence of the target document.
     */
    private void redirectUnauthorized(HttpServletResponse response, XWikiContext xcontext,
        URLShortenerResourceReference reference) throws Exception
    {
        if (null == xcontext.getUserReference()) {
            // A guest user is sent to the login page, keeping the shortened URL as the login redirect target.
            redirectToLogin(response, xcontext, resolveWikiId(reference));
        } else {
            // An authenticated user without view rights is redirected to a generic error page.
            redirectToDocumentDoesNotExist(response, xcontext, reference);
        }
    }

    private void redirectToDocumentDoesNotExist(HttpServletResponse response, XWikiContext xcontext,
        URLShortenerResourceReference reference) throws Exception
    {
        DocumentReference documentDoesNotExist =
            new DocumentReference(resolveWikiId(reference), URLSHORTENER_SPACE, DOCUMENT_DOES_NOT_EXIST_PAGE);
        String queryString = "shortURLID=" + reference.getPageId();
        response.sendRedirect(xcontext.getWiki().getURL(documentDoesNotExist, VIEW_ACTION, queryString, "", xcontext));
    }

    private void redirectToLogin(HttpServletResponse response, XWikiContext xcontext, String wikiId) throws Exception
    {
        // Keep the shortened URL as the login redirect target.
        String shortURL = xcontext.getRequest().getRequestURL().toString();
        String queryString = xcontext.getRequest().getQueryString();
        if (null != queryString) {
            shortURL += "?" + queryString;
        }
        DocumentReference loginReference = new DocumentReference(wikiId, "XWiki", "XWikiLogin");
        String loginURL = xcontext.getWiki()
            .getURL(loginReference, "login", "xredirect=" + URLEncoder.encode(shortURL, StandardCharsets.UTF_8), "",
                xcontext);
        response.sendRedirect(loginURL);
    }

    private String resolveWikiId(URLShortenerResourceReference reference)
    {
        return reference.getWikiId().isEmpty() ? xcontextProvider.get().getWikiId() : reference.getWikiId();
    }
}
