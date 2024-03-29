/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2021 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.privacy;

import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.SAXReaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provider for the privacy lists system. Privacy lists are read and written
 * from the {@code ofPrivacyList} database table.
 *
 * @author Gaston Dombiak
 */
public class PrivacyListProvider {

    private static final PrivacyListProvider instance = new PrivacyListProvider();
    
    private static final Logger Log = LoggerFactory.getLogger(PrivacyListProvider.class);

    private static final String PRIVACY_LIST_COUNT =
            "SELECT count(*) from ofPrivacyList";
    private static final String LOAD_LIST_NAMES =
            "SELECT name, isDefault FROM ofPrivacyList WHERE username=?";
    private static final String LOAD_PRIVACY_LIST =
            "SELECT isDefault, list FROM ofPrivacyList WHERE username=? AND name=?";
    private static final String LOAD_DEFAULT_PRIVACY_LIST =
            "SELECT name, list FROM ofPrivacyList WHERE username=? AND isDefault=1";
    private static final String DELETE_PRIVACY_LIST =
            "DELETE FROM ofPrivacyList WHERE username=? AND name=?";
    private static final String DELETE_PRIVACY_LISTS =
            "DELETE FROM ofPrivacyList WHERE username=?";
    private static final String UPDATE_PRIVACY_LIST =
            "UPDATE ofPrivacyList SET isDefault=?, list=? WHERE username=? AND name=?";
    private static final String INSERT_PRIVACY_LIST =
            "INSERT INTO ofPrivacyList (username, name, isDefault, list) VALUES (?, ?, ?, ?)";

    /**
     * Boolean used to optimize getters when the database is empty
     */
    private AtomicBoolean databaseContainsPrivacyLists;

    /**
     * Returns the unique instance of this class.
     *
     * @return the unique instance of this class.
     */
    public static PrivacyListProvider getInstance() {
        return instance;
    }
    
    private PrivacyListProvider() {
        super();

        // Checks if the PrivacyLists database is empty. 
        // In that case, we can optimize away many database calls. 
        databaseContainsPrivacyLists = new AtomicBoolean(false);
        loadDatabaseContainsPrivacyLists();
    }

    /**
     * Returns the names of the existing privacy lists indicating which one is the
     * default privacy list associated to a user.
     *
     * @param username the username of the user to get his privacy lists names.
     * @return the names of the existing privacy lists with a default flag.
     */
    public Map<String, Boolean> getPrivacyLists(String username) {
        // If there are no privacy lists stored, this method is a no-op.
        if (!databaseContainsPrivacyLists.get()) {
            return Collections.emptyMap();
        }

        Map<String, Boolean> names = new HashMap<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_LIST_NAMES);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                names.put(rs.getString(1), rs.getInt(2) == 1);
            }
        }
        catch (Exception e) {
            Log.error("Error loading names of privacy lists for username: " + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return names;
    }

    /**
     * Loads the requested privacy list from the database. Returns {@code null} if a list
     * with the specified name does not exist.
     *
     * @param username the username of the user to get his privacy list.
     * @param listName name of the list to load.
     * @return the privacy list with the specified name or {@code null} if a list
     *         with the specified name does not exist.
     */
    public PrivacyList loadPrivacyList(String username, String listName) {
        // If there are no privacy lists stored, this method is a no-op.
        if (!databaseContainsPrivacyLists.get()) {
            return null;
        }

        boolean isDefault = false;
        String listValue = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PRIVACY_LIST);
            pstmt.setString(1, username);
            pstmt.setString(2, listName);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                isDefault = rs.getInt(1) == 1;
                listValue = rs.getString(2);
            }
            else {
                return null;
            }
        }
        catch (Exception e) {
            Log.error("Error loading privacy list: " + listName + " of username: " + username, e);
            return null;
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        PrivacyList privacyList = null;
        try {
            Element listElement = SAXReaderUtil.readRootElement(listValue);
            privacyList = new PrivacyList(username, listName, isDefault, listElement);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return privacyList;
    }

    /**
     * Loads the default privacy list of a given user from the database. Returns {@code null}
     * if no list was found.
     *
     * @param username the username of the user to get his default privacy list.
     * @return the default privacy list or {@code null} if no list was found.
     */
    public PrivacyList loadDefaultPrivacyList(String username) {
        // If there are no privacy lists stored, this method is a no-op.
        if (!databaseContainsPrivacyLists.get()) {
            return null;
        }

        String listName = null;
        String listValue = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_DEFAULT_PRIVACY_LIST);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                listName = rs.getString(1);
                listValue = rs.getString(2);
            }
            else {
                return null;
            }
        }
        catch (Exception e) {
            Log.error("Error loading default privacy list of username: " + username, e);
            return null;
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        PrivacyList privacyList = null;
        try {
            Element listElement = SAXReaderUtil.readRootElement(listValue);
            privacyList = new PrivacyList(username, listName, true, listElement);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return privacyList;
    }

    /**
     * Creates and saves the new privacy list to the database.
     *
     * @param username the username of the user that created a new privacy list.
     * @param list the PrivacyList to save.
     */
    public void createPrivacyList(String username, PrivacyList list) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PRIVACY_LIST);
            pstmt.setString(1, username);
            pstmt.setString(2, list.getName());
            pstmt.setInt(3, (list.isDefault() ? 1 : 0));
            pstmt.setString(4, list.asElement().asXML());
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            Log.error("Error adding privacy list: " + list.getName() + " of username: " + username,
                    e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        databaseContainsPrivacyLists.set(true);
    }

    /**
     * Updated the existing privacy list in the database.
     *
     * @param username the username of the user that updated a privacy list.
     * @param list the PrivacyList to update in the database.
     */
    public void updatePrivacyList(String username, PrivacyList list) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PRIVACY_LIST);
            pstmt.setInt(1, (list.isDefault() ? 1 : 0));
            pstmt.setString(2, list.asElement().asXML());
            pstmt.setString(3, username);
            pstmt.setString(4, list.getName());
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            Log.error("Error updating privacy list: " + list.getName() + " of username: " +
                    username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        databaseContainsPrivacyLists.set(true);
    }

    /**
     * Deletes an existing privacy list from the database.
     *
     * @param username the username of the user that deleted a privacy list.
     * @param listName the name of the PrivacyList to delete.
     */
    public void deletePrivacyList(String username, String listName) {
        // If there are no privacy lists stored, this method is a no-op.
        if (!databaseContainsPrivacyLists.get()) {
            return;
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PRIVACY_LIST);
            pstmt.setString(1, username);
            pstmt.setString(2, listName);
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            Log.error("Error deleting privacy list: " + listName + " of username: " + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        databaseContainsPrivacyLists.set(true);
    }

    /**
     * Deletes all existing privacy list from the database for the given user.
     *
     * @param username the username of the user whose privacy lists are going to be deleted.
     */
    public void deletePrivacyLists(String username) {
        // If there are no privacy lists stored, this method is a no-op.
        if (!databaseContainsPrivacyLists.get()) {
            return;
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PRIVACY_LISTS);
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            Log.error("Error deleting privacy lists of username: " + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        databaseContainsPrivacyLists.set(true);
    }

    /**
     * Loads the total number of privacy lists stored in the database to know if we must use them.
     */
    private void loadDatabaseContainsPrivacyLists() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(PRIVACY_LIST_COUNT);
            rs = pstmt.executeQuery();
            rs.next();
            databaseContainsPrivacyLists.set(rs.getInt(1) != 0);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }
}
