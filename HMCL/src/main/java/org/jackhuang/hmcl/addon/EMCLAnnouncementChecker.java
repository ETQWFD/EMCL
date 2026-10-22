/*
 * EMCL (Eternal Minecraft Launcher)
 * Copyright (C) 2026  ETC Team <2416444244@qq.com>
 *
 * Forked from Hello Minecraft! Launcher (HMCL)
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.addon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * EMCL announcement system.
 *
 * <p>Fetches {@code announce.json} from the EMCL website
 * ({@code https://etqwfd.github.io/EMCL/announce.json}); the first entry with
 * {@code enabled == true} whose {@code id} was not seen yet is shown once and
 * the id is recorded in {@code EMCLAnnounceSeenId}.
 */
public final class EMCLAnnouncementChecker {
    private EMCLAnnouncementChecker() {
    }

    private static final String ANNOUNCE_URL = "https://etqwfd.github.io/EMCL/announce.json";

    public static void init() {
        thread(() -> {
            // Announcement is best-effort. Network failure, offline state, a captive
            // portal/HTML error page, or a non-array payload must never surface an error.
            String body;
            try {
                body = NetworkUtils.doGet(ANNOUNCE_URL);
            } catch (Throwable networkError) {
                return;
            }
            if (body == null) {
                return;
            }

            JsonElement root;
            try {
                root = JsonParser.parseString(body);
            } catch (Throwable notJson) {
                return;
            }
            if (root == null || !root.isJsonArray()) {
                return;
            }

            JsonArray array = root.getAsJsonArray();
            String seen = SettingsManager.userSettings().getEmclAnnounceSeenId();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                if (!object.has("enabled") || !object.get("enabled").getAsBoolean()) {
                    continue;
                }
                if (!object.has("id") || !object.has("title") || !object.has("content")) {
                    continue;
                }
                String id = object.get("id").getAsString();
                if (id == null || id.equals(seen)) {
                    continue;
                }
                String title = object.get("title").getAsString();
                String content = object.get("content").getAsString();
                Platform.runLater(() -> showAnnouncement(id, title, content));
                break;
            }
        }, "EMCL Announcement Checker", true);
    }

    private static void showAnnouncement(String id, String title, String content) {
        MessageDialogPane pane = new MessageDialogPane.Builder(content, title, MessageDialogPane.MessageType.INFO).build();
        JFXButton ok = new JFXButton(i18n("button.ok"));
        ok.getStyleClass().add("dialog-accept");
        ok.setOnAction(e -> {
            DialogUtils.close(pane);
            SettingsManager.userSettings().emclAnnounceSeenIdProperty().set(id);
            SettingsManager.savePendingChanges();
        });
        pane.addButton(ok);
        Controllers.dialog(pane);
    }
}
