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
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.resource.CreateResourceReferenceException;
import org.xwiki.resource.UnsupportedResourceReferenceException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.url.ExtendedURL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ComponentTest
public class URLShortenerResourceReferenceResolverTest
{
    @InjectMockComponents
    private URLShortenerResourceReferenceResolver resolver;

    @Test
    void resolveOnSubWiki() throws CreateResourceReferenceException, UnsupportedResourceReferenceException
    {
        String pageId = "12345";
        String wikiId = "test";
        ExtendedURL extendedURL = new ExtendedURL(Arrays.asList(wikiId, pageId));
        URLShortenerResourceReference expectedReference = new URLShortenerResourceReference(wikiId, pageId);

        URLShortenerResourceReference actualReference =
            (URLShortenerResourceReference) resolver.resolve(extendedURL, URLShortenerResourceReference.TYPE,
                Collections.emptyMap());
        assertEquals(expectedReference, actualReference);
    }

    @Test
    void resolveOnMainWiki() throws CreateResourceReferenceException, UnsupportedResourceReferenceException
    {
        String pageId = "12345";
        String wikiId = "";
        ExtendedURL extendedURL = new ExtendedURL(List.of(pageId));
        URLShortenerResourceReference expectedReference = new URLShortenerResourceReference(wikiId, pageId);

        URLShortenerResourceReference actualReference =
            (URLShortenerResourceReference) resolver.resolve(extendedURL, URLShortenerResourceReference.TYPE,
                Collections.emptyMap());
        assertEquals(expectedReference, actualReference);
    }

    @Test
    void resolveWithException() throws CreateResourceReferenceException, UnsupportedResourceReferenceException
    {
        String pageId = "12345";
        String wikiId = "test";
        ExtendedURL wrongResourceURL = new ExtendedURL(Arrays.asList(wikiId, "test", pageId));

        CreateResourceReferenceException exception = assertThrows(CreateResourceReferenceException.class,
            () -> resolver.resolve(wrongResourceURL, URLShortenerResourceReference.TYPE, Collections.emptyMap()));
        assertEquals(String.format("Invalid URL Shortener Resource format: [%s]", wrongResourceURL),
            exception.getMessage());
    }
}
