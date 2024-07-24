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

import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.resource.CreateResourceReferenceException;
import org.xwiki.resource.ResourceReference;
import org.xwiki.resource.ResourceType;
import org.xwiki.resource.UnsupportedResourceReferenceException;
import org.xwiki.url.ExtendedURL;
import org.xwiki.url.internal.AbstractResourceReferenceResolver;

/**
 * Transforms a URLShortener URL into a typed Resource Reference. The URL format handled is
 * {@code http://server/context/p/pageId} (e.g. {@code http://localhost:8080/xwiki/p/12345}).
 *
 * @version $Id:$
 * @since 1.2
 */
@Component
@Named("p")
@Singleton
public class URLShortenerResourceReferenceResolver extends AbstractResourceReferenceResolver
{
    @Override
    public ResourceReference resolve(ExtendedURL extendedURL, ResourceType resourceType, Map<String, Object> parameters)
        throws CreateResourceReferenceException, UnsupportedResourceReferenceException
    {
        URLShortenerResourceReference reference;
        List<String> segments = extendedURL.getSegments();

        if (!segments.isEmpty()) {
            String pageId = segments.get(0);
            reference = new URLShortenerResourceReference(pageId);
            copyParameters(extendedURL, reference);
        } else {
            throw new CreateResourceReferenceException(
                String.format("Invalid URL Shortener Resource format: [%s]", extendedURL));
        }

        return reference;
    }
}
