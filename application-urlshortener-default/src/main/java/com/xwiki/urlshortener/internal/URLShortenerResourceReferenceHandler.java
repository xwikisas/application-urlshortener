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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletResponse;

import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
import org.xwiki.container.servlet.ServletResponse;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.resource.AbstractResourceReferenceHandler;
import org.xwiki.resource.ResourceReference;
import org.xwiki.resource.ResourceReferenceHandlerChain;
import org.xwiki.resource.ResourceReferenceHandlerException;
import org.xwiki.resource.ResourceType;

import com.xpn.xwiki.XWikiContext;

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
    private QueryManager queryManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private Container container;

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
            List<?> results = getURLShortenerObjectWithID(urlResourceReference);
            if (!results.isEmpty()) {
                DocumentReference documentReference = documentReferenceResolver.resolve((String) results.get(0));
                XWikiContext xcontext = xcontextProvider.get();
                String stringURL = xcontext.getWiki().getURL(documentReference, xcontext);

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

    private List<?> getURLShortenerObjectWithID(URLShortenerResourceReference resourceReference) throws QueryException
    {
        String statement =
            "select distinct doc.fullName from Document as doc, doc.object('URLShortener.Code.URLShortenerClass') as "
                + "obj where obj.pageID = :pageID";
        Query query =
            this.queryManager.createQuery(statement, Query.XWQL).bindValue(PAGE_ID, resourceReference.getPageId());
        // An empty wiki means we are on the main wiki, which doesn't need to be set on the query because it's the
        // default.
        if (!resourceReference.getWikiId().isEmpty()) {
            query = query.setWiki(resourceReference.getWikiId());
        }
        return query.execute();
    }
}
