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
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.client.solrj.response.QueryResponse;
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
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
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
            XWikiDocument currentDoc = xcontext.getWiki().getDocument(documentReference, xcontext);
            String pageID = addURLShortenerXObject(currentDoc);
            if (pageID == null || pageID.isEmpty()) {
                throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
            }

            return Response.ok().entity(Map.of(PAGE_ID, pageID)).type(MediaType.APPLICATION_JSON).build();
        } else {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
    }

    private String addURLShortenerXObject(XWikiDocument currentDoc)
    {
        String pageID = null;
        BaseObject urlShortenerObj = currentDoc.getXObject(URL_SHORTENER_CLASS_REFERENCE);

        if (urlShortenerObj == null) {
            try {
                XWikiContext xcontext = this.xcontextProvider.get();
                BaseObject object = currentDoc.newXObject(URL_SHORTENER_CLASS_REFERENCE, xcontext);
                pageID = createPageID();
                object.set(PAGE_ID, pageID, xcontext);

                // Don't create a history entry.
                currentDoc.setMetaDataDirty(false);
                currentDoc.setContentDirty(false);

                xcontext.getWiki().saveDocument(currentDoc, "Created URL Shortener.", true, xcontext);
            } catch (XWikiException | QueryException e) {
                this.logger.error(
                    String.format("Error while computing the shortened URL for document [%s]. Root cause: [%s]",
                        currentDoc.getDocumentReference().toString(), ExceptionUtils.getRootCauseMessage(e)));
            }
        } else {
            pageID = urlShortenerObj.getStringValue(PAGE_ID);
        }

        return pageID;
    }

    private String createPageID() throws QueryException
    {
        String id = UUID.randomUUID().toString().substring(0, 5);
        // Make sure the ID is not already used.
        if (getURLShortenerObjectWithID(id) != null) {
            id = createPageID();
        }

        return id;
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
