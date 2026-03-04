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
package com.xwiki.urlshortener.internal.rest;

import java.util.Arrays;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.urlshortener.URLShortenerManager;
import com.xwiki.urlshortener.rest.URLShortenerResource;

/**
 * Default implementation of {@link URLShortenerResource}.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named("com.xwiki.urlshortener.internal.rest.DefaultURLShortenerResource")
public class DefaultURLShortenerResource implements URLShortenerResource
{
    /**
     * URL Shortener Class Reference.
     */
    public static final LocalDocumentReference URL_SHORTENER_CLASS_REFERENCE =
        new LocalDocumentReference(Arrays.asList("URLShortener", "Code"), "URLShortenerClass");

    private static final String PAGE_ID = "pageID";

    @Inject
    private QueryManager queryManager;

    @Inject
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private EntityReferenceResolver<SolrDocument> solrEntityReferenceResolver;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private Logger logger;

    @Inject
    private URLShortenerManager urlShortenerManager;

    @Override
    public Response redirect(String pageID) throws Exception
    {
        SolrDocument result = getURLShortenerObjectWithID(pageID);
        if (result != null) {
            EntityReference docRef = solrEntityReferenceResolver.resolve(result, EntityType.DOCUMENT);
            XWikiContext xcontext = xcontextProvider.get();
            XWikiDocument doc = xcontext.getWiki().getDocument(docRef, xcontext);
            String stringURL = doc.getURL("view", xcontext);
            // Let the redirect action to check the view right on the document.
            xcontext.getResponse().sendRedirect(stringURL);

            return Response.status(301).build();
        } else {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
    }

    @Override
    public Response createShortenedURL(String currentDocRef) throws Exception
    {

        XWikiContext xcontext = xcontextProvider.get();
        DocumentReference documentReference = documentReferenceResolver.resolve(currentDocRef);

        if (!xcontext.getWiki().exists(documentReference, xcontext)) {
            return Response.status(Response.Status.NOT_FOUND).type(MediaType.APPLICATION_JSON).build();
        }

        if (authorization.hasAccess(Right.VIEW, documentReference)) {
            String pageID = urlShortenerManager.createShortenedURL(documentReference);

            return Response.ok().entity(Map.of(PAGE_ID, pageID)).type(MediaType.APPLICATION_JSON).build();
        } else {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
    }

    @Override
    public Response regenerateShortenedURL(String currentDocRef, String oldPageID) throws Exception
    {
        DocumentReference documentReference = documentReferenceResolver.resolve(currentDocRef);
        if (authorization.hasAccess(Right.EDIT, documentReference)) {
            try {
                String pageID = urlShortenerManager.regenerateShortenedURL(documentReference, oldPageID);
                return Response.ok().entity(Map.of(PAGE_ID, pageID)).type(MediaType.APPLICATION_JSON).build();
            } catch (IllegalStateException e) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } else {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
    }

    private SolrDocument getURLShortenerObjectWithID(String pageID) throws QueryException
    {
        // This query needs to be done on all wikis.
        String statement =
            "property.URLShortener.Code.URLShortenerClass.pageID:" + ClientUtils.escapeQueryChars(pageID);
        Query query = this.queryManager.createQuery(statement, "solr").setLimit(1);
        QueryResponse response = (QueryResponse) query.execute().get(0);
        SolrDocumentList results = response.getResults();

        return results.isEmpty() ? null : results.get(0);
    }
}
