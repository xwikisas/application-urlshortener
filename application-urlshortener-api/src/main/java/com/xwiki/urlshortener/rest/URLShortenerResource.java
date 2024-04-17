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
package com.xwiki.urlshortener.rest;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.xwiki.rest.XWikiRestComponent;
import org.xwiki.stability.Unstable;

/**
 * Provides APIs for redirecting from a shortened URL to the actual document, along with what is needed for creating the
 * needed ID.
 *
 * @version $Id$
 * @since 1.0
 */
@Unstable
@Path("p")
public interface URLShortenerResource extends XWikiRestComponent
{
    /**
     * Redirect to the page that is uniquely identified by the given ID. If the ID is not assigned to any page, return a
     * 404.
     *
     * @param pageID the ID used to identify a XWiki page
     * @return the response
     * @throws Exception if there is no page associated to the given ID
     */
    @GET
    @Path("/{id}")
    Response redirect(@PathParam("id") String pageID) throws Exception;

    /**
     * Associate an ID for the given document, if it doesn't exist already, and the current user has at least view
     * rights on it.
     *
     * @param currentDocRef current document reference, as {@code String}
     * @return the created or found ID for the given document
     * @throws Exception if a error occurs while creating and saving the ID
     */
    @POST
    @Path("/create")
    Response createShortenedURL(@QueryParam("currentDocRef") String currentDocRef) throws Exception;
}
