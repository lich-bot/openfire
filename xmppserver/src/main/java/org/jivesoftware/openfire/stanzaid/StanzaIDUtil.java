/*
 * Copyright (C) 2019-2022 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.stanzaid;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Utility methods that implement XEP-0359: Unique and Stable Stanza IDs.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0359.html">XEP-0359</a>
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class StanzaIDUtil
{
    private static final Logger Log = LoggerFactory.getLogger( StanzaIDUtil.class );

    /**
     * Modifies the stanza that's passed as a packet by adding a Stanza ID on behalf of what is assumed to be a local
     * entity.
     *
     * @param packet The inbound packet (cannot be null).
     * @param self The ID of the 'local' entity that will generate the stanza ID (cannot be null).
     * @return the updated packet
     * @see <a href="https://xmpp.org/extensions/xep-0359.html">XEP-0359</a>
     */
    public static Packet ensureUniqueAndStableStanzaID( final Packet packet, final JID self )
    {
        if ( !JiveGlobals.getBooleanProperty( "xmpp.sid.enabled", true ) )
        {
            return packet;
        }

        if ( packet instanceof IQ && !JiveGlobals.getBooleanProperty( "xmpp.sid.iq.enabled", false ) )
        {
            return packet;
        }

        if ( packet instanceof Message && !JiveGlobals.getBooleanProperty( "xmpp.sid.message.enabled", true ) )
        {
            return packet;
        }

        if ( packet instanceof Presence && !JiveGlobals.getBooleanProperty( "xmpp.sid.presence.enabled", false ) )
        {
            return packet;
        }

        final Element parentElement;
        if ( packet instanceof IQ ) {
            parentElement = ((IQ) packet).getChildElement();
        } else {
            parentElement = packet.getElement();
        }

        // The packet likely is an IQ result or error, which can, but are not required to have a child element.
        // To have a consistent behavior for these, we'll not add a stanza-ID here.
        if ( parentElement == null )
        {
            Log.debug( "Unable to find appropriate element. Not adding stanza-id to packet: {}", packet );
            return packet;
        }

        // Stanza ID generating entities, which encounter a <stanza-id/> element where the 'by' attribute matches the 'by'
        // attribute they would otherwise set, MUST delete that element even if they are not adding their own stanza ID.
        final Iterator<Element> existingElementIterator = parentElement.elementIterator( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        while (existingElementIterator.hasNext()) {
            final Element element = existingElementIterator.next();

            if (self.toString().equals( element.attributeValue( "by" ) ) ) {
                Log.warn( "Removing a 'stanza-id' element from an inbound stanza, as its 'by' attribute value matches the value that we would set. Offending stanza: {}", packet );
                existingElementIterator.remove();
            }
        }

        final String id = UUID.randomUUID().toString();
        Log.debug( "Using newly generated value '{}' for stanza that has id '{}'.", id, packet.getID() );

        final Element stanzaIdElement = parentElement.addElement( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        stanzaIdElement.addAttribute( "id", id );
        stanzaIdElement.addAttribute( "by", self.toString() );

        return packet;
    }

    /**
     * Returns the first stable and unique stanza-id value from the packet, that is defined
     * for a particular 'by' value.
     *
     * This method does not evaluate 'origin-id' elements in the packet.
     *
     * @param packet The stanza (cannot be null).
     * @param by The 'by' value for which to return the ID (cannot be null or an empty string).
     * @return The unique and stable ID, or null if no such ID is found.
     */
    public static String findFirstUniqueAndStableStanzaID( final Packet packet, final String by )
    {
        if ( packet == null )
        {
            throw new IllegalArgumentException( "Argument 'packet' cannot be null." );
        }
        if ( by == null || by.isEmpty() )
        {
            throw new IllegalArgumentException( "Argument 'by' cannot be null or an empty string." );
        }

        final List<Element> sids = packet.getElement().elements( QName.get( "stanza-id", "urn:xmpp:sid:0" ) );
        if ( sids == null )
        {
            return null;
        }

        for ( final Element sid : sids )
        {
            if ( by.equals( sid.attributeValue( "by" ) ) )
            {
                final String result = sid.attributeValue( "id" );
                if ( result != null && !result.isEmpty() )
                {
                    return result;
                }
            }
        }

        return null;
    }
}
