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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.urlshortener.URLShortenerException;
import com.xwiki.urlshortener.URLShortenerManager;

/**
 * @version $Id$
 * @since 1.3.0
 */
@Singleton
@Component
public class DefaultURLShortenerManager implements URLShortenerManager
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
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private Logger logger;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private DocumentReferenceResolver<SolrDocument> solrDocumentReferenceResolver;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Override
    public String createShortenedURL(DocumentReference documentReference) throws URLShortenerException
    {
        try {
            XWikiContext xcontext = xcontextProvider.get();
            XWikiDocument currentDoc = xcontext.getWiki().getDocument(documentReference, xcontext);
            return addURLShortenerXObject(currentDoc.clone());
        } catch (XWikiException | QueryException e) {
            this.logger.error(
                String.format("Error while computing the shortened URL for document [%s]. Root cause: [%s]",
                    documentReference, ExceptionUtils.getRootCauseMessage(e)));
            throw new URLShortenerException(String.format("Failed to retrieve the document [%s].", documentReference),
                e);
        }
    }

    @Override
    public String regenerateShortenedURL(DocumentReference documentReference, String oldPageID)
        throws IllegalStateException, URLShortenerException
    {
        XWikiContext xcontext = xcontextProvider.get();
        try {
            XWikiDocument currentDoc = xcontext.getWiki().getDocument(documentReference, xcontext);
            List<BaseObject> oldObjects =
                currentDoc.getXObjects(URL_SHORTENER_CLASS_REFERENCE).stream().filter(Objects::nonNull)
                    .filter((baseObject) -> baseObject.getStringValue(PAGE_ID).equals(oldPageID))
                    .collect(Collectors.toList());

            if (oldObjects.isEmpty()) {
                throw new IllegalStateException(String.format(
                    "The document does not contains the URLShortener object with id [%s] that needs regenerating.",
                    oldPageID));
            } else {
                String pageID = createPageID();
                oldObjects.get(0).setStringValue(PAGE_ID, pageID);
                // Don't create a history entry.
                currentDoc.setMetaDataDirty(false);
                currentDoc.setContentDirty(false);
                xcontext.getWiki().saveDocument(currentDoc, "Regenerate short URL.", true, xcontext);
                return pageID;
            }
        } catch (XWikiException | QueryException e) {
            throw new URLShortenerException(
                String.format("Failed to regenerate the short url for the document [%s].", documentReference), e);
        }
    }

    @Override
    public DocumentReference getDocumentReference(String wiki, String id) throws URLShortenerException
    {
        try {
            DocumentReference documentReference = null;

            List<?> results = getURLShortenerObjectWithID(id, wiki);

            if (!results.isEmpty()) {
                documentReference = documentReferenceResolver.resolve((String) results.get(0));
                if (wiki != null && !wiki.isEmpty()) {
                    documentReference =
                        documentReference.setWikiReference(new WikiReference(wiki));
                }
            } else {
                // If no short url is found on the given subwiki, try to find the pageID in all subwikis.
                results = getURLShortenerObjectWithIDOnAnyWiki(id);
                if (!results.isEmpty()) {
                    documentReference = documentReferenceResolver.resolve((String) results.get(0));
                }
            }
            return documentReference;
        } catch (QueryException e) {
            throw new URLShortenerException(
                String.format("Failed to find the xwiki page identified by id [%s] and wiki [%s].", id, wiki));
        }
    }

    private String addURLShortenerXObject(XWikiDocument currentDoc) throws XWikiException, QueryException
    {
        String pageID = null;
        BaseObject urlShortenerObj = currentDoc.getXObject(URL_SHORTENER_CLASS_REFERENCE);

        if (urlShortenerObj == null) {

            XWikiContext xcontext = this.xcontextProvider.get();
            BaseObject object = currentDoc.newXObject(URL_SHORTENER_CLASS_REFERENCE, xcontext);
            pageID = createPageID();
            object.set(PAGE_ID, pageID, xcontext);

            // Don't create a history entry.
            currentDoc.setMetaDataDirty(false);
            currentDoc.setContentDirty(false);
            xcontext.getWiki().saveDocument(currentDoc, "Created URL Shortener.", true, xcontext);
        } else {
            pageID = urlShortenerObj.getStringValue(PAGE_ID);
        }

        return pageID;
    }

    private String createPageID() throws QueryException
    {
        String id = UUID.randomUUID().toString().substring(0, 5);
        // Make sure the ID is not already used.
        if (!getURLShortenerObjectWithIDOnAnyWiki(id).isEmpty()) {
            id = createPageID();
        }

        return id;
    }

    private List<?> getURLShortenerObjectWithIDOnAnyWiki(String pageId)
        throws QueryException
    {
        // Note that the query is very slow when solr is reindexing.
        // Also, for newly added URLShortener objects, SOLR takes some moments to update with its value. So this SOLR
        // query might also return null if it didn't finish updating the index.
        String statement =
            "property.URLShortener.Code.URLShortenerClass.pageID:" + ClientUtils.escapeQueryChars(pageId);
        Query query = this.queryManager.createQuery(statement, "solr").setLimit(1);
        QueryResponse response = (QueryResponse) query.execute().get(0);
        List<?> queryResults = response.getResults().stream()
            .map((SolrDocument doc) -> serializer.serialize(solrDocumentReferenceResolver.resolve(doc)))
            .collect(Collectors.toList());
        return queryResults;
    }

    private List<?> getURLShortenerObjectWithID(String pageId, String wikiName) throws QueryException
    {
        String statement =
            "select distinct doc.fullName from Document as doc, doc.object('URLShortener.Code.URLShortenerClass') as "
                + "obj where obj.pageID = :pageID";
        Query query =
            this.queryManager.createQuery(statement, Query.XWQL).bindValue(PAGE_ID, pageId)
                .setLimit(1);
        // An empty wiki means we are on the main wiki, which doesn't need to be set on the query because it's the
        // default.
        if (wikiName != null && !wikiName.isEmpty()) {
            query = query.setWiki(wikiName);
        }
        return query.execute();
    }
}
