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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.xwiki.resource.AbstractResourceReference;
import org.xwiki.resource.ResourceType;

/**
 * Represents a reference to a shortened URL resource.
 *
 * @version $Id:$
 * @since 1.2
 */
public class URLShortenerResourceReference extends AbstractResourceReference
{
    /**
     * The role hint to use for URL Shortener related resources.
     */
    public static final String HINT = "short";

    /**
     * Represents a URL Shortener Resource Type.
     */
    public static final ResourceType TYPE = new ResourceType(HINT);

    private String pageId;

    /**
     * @param pageId see {@link #getPageId()}
     */
    public URLShortenerResourceReference(String pageId)
    {
        setType(TYPE);
        this.pageId = pageId;
    }

    /**
     * @return the ID used to identify a XWiki document.
     */
    public String getPageId()
    {
        return this.pageId;
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder(5, 5)
            .append(getType())
            .append(getPageId())
            .append(getParameters())
            .toHashCode();
    }

    @Override
    public boolean equals(Object object)
    {
        if (object == null) {
            return false;
        }
        if (object.getClass() != this.getClass()) {
            return false;
        }
        if (object == this) {
            return true;
        }
        URLShortenerResourceReference obj = (URLShortenerResourceReference) object;
        return new EqualsBuilder()
            .append(this.getPageId(), obj.getPageId())
            .append(getType(), obj.getType())
            .append(getParameters(), obj.getParameters())
            .isEquals();
    }
}
