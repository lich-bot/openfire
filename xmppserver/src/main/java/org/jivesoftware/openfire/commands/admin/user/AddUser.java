/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.commands.admin.user;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adds a user to Openfire if the provider is not read-only.
 *
 * @author Alexander Wenckus
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#add-user">XEP-0133 Service Administration: Add User</a>
 */
public class AddUser extends AdHocCommand {
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#add-user";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.user.adduser.label");
    }

    @Override
    public int getMaxStages(@Nonnull final SessionData data) {
        return 1;
    }

    @Override
    public void execute(@Nonnull SessionData sessionData, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(sessionData.getOwner());

        Element note = command.addElement("note");
        // Check if groups cannot be modified (backend is read-only)
        if (UserManager.getUserProvider().isReadOnly()) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.adduser.note.users-readonly", preferredLocale));
            return;
        }
        Map<String, List<String>> data = sessionData.getData();

        // Let's create the jid and check that they are a local user
        JID account;
        try {
            account = new JID(get(data, "accountjid", 0));
        }
        catch (IllegalArgumentException e) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.adduser.note.jid-invalid", preferredLocale));
            return;
        }
        catch (NullPointerException npe) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.adduser.note.jid-required", preferredLocale));
            return;
        }
        if (!XMPPServer.getInstance().isLocal(account)) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.adduser.note.jid-not-local", preferredLocale));
            return;
        }

        String password = get(data, "password", 0);
        String passwordRetry = get(data, "password-verify", 0);

        if (password == null || password.isEmpty() || !password.equals(passwordRetry)) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.adduser.note.passwords-dont-match", preferredLocale));
            return;
        }

        String email = get(data, "email", 0);
        String givenName = get(data, "given_name", 0);
        String surName = get(data, "surname", 0);
        String name = (givenName == null ? "" : givenName) + (surName == null ? "" : surName);
        name = (name.isEmpty() ? null : name);

        // If provider requires email, validate
        if (UserManager.getUserProvider().isEmailRequired() && !StringUtils.isValidEmailAddress(email)) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.adduser.note.email-required", preferredLocale));
            return;
        }

        try {
            UserManager.getInstance().createUser(account.getNode(), password, name, email);
        }
        catch (UserAlreadyExistsException e) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.adduser.note.user-exists", preferredLocale));
            return;
        }
        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText(LocaleUtils.getLocalizedString("commands.global.operation.finished.success", preferredLocale));
    }

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.user.adduser.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.user.adduser.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.user.adduser.form.field.accountjid.label", preferredLocale));
        field.setVariable("accountjid");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.user.adduser.form.field.password.label", preferredLocale));
        field.setVariable("password");

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.user.adduser.form.field.password-verify.label", preferredLocale));
        field.setVariable("password-verify");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.user.adduser.form.field.email.label", preferredLocale));
        field.setVariable("email");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.user.adduser.form.field.given_name.label", preferredLocale));
        field.setVariable("given_name");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.user.adduser.form.field.surname.label", preferredLocale));
        field.setVariable("surname");

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    protected List<Action> getActions(@Nonnull final SessionData data) {
        return Collections.singletonList(Action.complete);
    }

    @Override
    protected AdHocCommand.Action getExecuteAction(@Nonnull final SessionData data) {
        return AdHocCommand.Action.complete;
    }

    @Override
    public boolean hasPermission(JID requester) {
        return (super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester))
                && !UserManager.getUserProvider().isReadOnly();
    }
}
