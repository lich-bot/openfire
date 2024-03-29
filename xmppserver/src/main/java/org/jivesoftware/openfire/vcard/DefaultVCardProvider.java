/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2021 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.vcard;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.SAXReaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Default implementation of the VCardProvider interface, which reads and writes data
 * from the {@code ofVCard} database table.
 *
 * @author Gaston Dombiak
 */
public class DefaultVCardProvider implements VCardProvider {

    private static final Logger Log = LoggerFactory.getLogger(DefaultVCardProvider.class);

    private static final Interner<JID> userBaseMutex = Interners.newWeakInterner();
    
    private static final String LOAD_PROPERTIES =
        "SELECT vcard FROM ofVCard WHERE username=?";
    private static final String DELETE_PROPERTIES =
        "DELETE FROM ofVCard WHERE username=?";
    private static final String UPDATE_PROPERTIES =
        "UPDATE ofVCard SET vcard=? WHERE username=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO ofVCard (username, vcard) VALUES (?, ?)";

    @Override
    public Element loadVCard(String username) {
        final JID mutex;
        if (username.contains("@")) {
            // OF-2320: VCards are used for MUC rooms, to store their avatar. For this usecase, the 'username' is actually a (bare) JID value representing the room.
            mutex = new JID(username);
        } else {
            mutex = XMPPServer.getInstance().createJID(username, null);
        }
        synchronized (userBaseMutex.intern(mutex)) {
            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            Element vCardElement = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(LOAD_PROPERTIES);
                pstmt.setString(1, username);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    vCardElement = SAXReaderUtil.readRootElement(rs.getString(1));
                }
            }
            catch (Exception e) {
                Log.error("Error loading vCard of username: " + username, e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }

            if ( JiveGlobals.getBooleanProperty( PhotoResizer.PROPERTY_RESIZE_ON_LOAD, PhotoResizer.PROPERTY_RESIZE_ON_LOAD_DEFAULT ) )
            {
                PhotoResizer.resizeAvatar( vCardElement );
            }

            return vCardElement;
        }
    }

    @Override
    public Element createVCard(String username, Element vCardElement) throws AlreadyExistsException {
        if (loadVCard(username) != null) {
            // The user already has a vCard
            throw new AlreadyExistsException("Username " + username + " already has a vCard");
        }

        if ( JiveGlobals.getBooleanProperty( PhotoResizer.PROPERTY_RESIZE_ON_CREATE, PhotoResizer.PROPERTY_RESIZE_ON_CREATE_DEFAULT ) )
        {
            PhotoResizer.resizeAvatar( vCardElement );
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            pstmt.setString(1, username);
            pstmt.setString(2, vCardElement.asXML());
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error("Error creating vCard for username: " + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return vCardElement;
    }

    @Override
    public Element updateVCard(String username, Element vCardElement) throws NotFoundException {
        if (loadVCard(username) == null) {
            // The user does not have a vCard
            throw new NotFoundException("Username " + username + " does not have a vCard");
        }

        if ( JiveGlobals.getBooleanProperty( PhotoResizer.PROPERTY_RESIZE_ON_CREATE, PhotoResizer.PROPERTY_RESIZE_ON_CREATE_DEFAULT ) )
        {
            PhotoResizer.resizeAvatar( vCardElement );
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTIES);
            pstmt.setString(1, vCardElement.asXML());
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error("Error updating vCard of username: " + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return vCardElement;
    }

    @Override
    public void deleteVCard(String username) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTIES);
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error("Error deleting vCard of username: " + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }
}
