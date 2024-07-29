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

import javax.inject.Inject;
import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.resource.SerializeResourceReferenceException;
import org.xwiki.resource.UnsupportedResourceReferenceException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.url.ExtendedURL;
import org.xwiki.url.URLNormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ComponentTest
public class URLShortenerResourceReferenceSerializerTest
{
    @InjectMockComponents
    private URLShortenerResourceReferenceSerializer urlShortenerResourceReferenceSerializer;

    @MockComponent
    @Named("contextpath")
    private URLNormalizer<ExtendedURL> extendedURLNormalizer;

    @BeforeEach
    void setup()
    {
        when(extendedURLNormalizer.normalize(any())).then(returnsFirstArg());
    }

    @Test
    void serialize() throws SerializeResourceReferenceException, UnsupportedResourceReferenceException
    {
        String pageId = "12345";
        URLShortenerResourceReference resourceReference = new URLShortenerResourceReference(pageId);
        ExtendedURL extendedURL = urlShortenerResourceReferenceSerializer.serialize(resourceReference);

        verify(extendedURLNormalizer, times(1)).normalize(any());
        List<String> segments = extendedURL.getSegments();
        assertEquals(2, segments.size());
        assertEquals(URLShortenerResourceReference.HINT, segments.get(0));
        assertEquals(pageId, segments.get(1));
        assertTrue(extendedURL.getParameters().isEmpty());
    }
}
