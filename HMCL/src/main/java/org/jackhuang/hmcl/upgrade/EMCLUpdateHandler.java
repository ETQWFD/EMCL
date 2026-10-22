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
package org.jackhuang.hmcl.upgrade;

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * EMCL in-app update handler.
 *
 * <ul>
 *   <li>Three-button dialog: update now / ignore this version / later.</li>
 *   <li>Jar builds: download the {@code EMCL.jar} asset from the continuous
 *       release, relaunch with the new jar and self-delete it afterwards.</li>
 *   <li>jpackage (EXE) builds: prompt the user to download the new
 *       {@code EMCL-windows-portable.zip} (the whole app image must be replaced).</li>
 * </ul>
 */
public final class EMCLUpdateHandler {
    private EMCLUpdateHandler() {
    }

    private static boolean prompted = false;

    /// Shows the three-button update dialog once per session.
    public static void maybeShowUpdateDialog(RemoteVersion version) {
        if (prompted) {
            return;
        }
        if (SettingsManager.settings().disableAutoShowUpdateDialogProperty().get()) {
            return;
        }
        prompted = true;

        MessageDialogPane pane = new MessageDialogPane.Builder(
                i18n("emcl.update.available", version.version()),
                i18n("emcl.update.title"),
                MessageDialogPane.MessageType.INFO).build();

        JFXButton now = new JFXButton(i18n("emcl.update.now"));
        now.getStyleClass().add("dialog-accept");
        now.setOnAction(e -> {
            DialogUtils.close(pane);
            updateNow(version);
        });

        JFXButton ignore = new JFXButton(i18n("emcl.update.ignore"));
        ignore.setOnAction(e -> {
            DialogUtils.close(pane);
            SettingsManager.userSettings().emclUpdateIgnoreVersionProperty().set(version.version());
            SettingsManager.savePendingChanges();
            Controllers.showToast(i18n("emcl.update.ignored", version.version()));
        });

        JFXButton later = new JFXButton(i18n("emcl.update.later"));
        later.setOnAction(e -> DialogUtils.close(pane));

        pane.addButton(now);
        pane.addButton(ignore);
        pane.addButton(later);
        Controllers.dialog(pane);
    }

    /// Manual "check for update" from the About page.
    public static void checkNowWithDialog() {
        thread(() -> {
            try {
                RemoteVersion version = UpdateChecker.checkNow(UpdateChannel.getChannel(),
                        SettingsManager.settings().acceptPreviewUpdateProperty().get());
                Platform.runLater(() -> {
                    if (UpdateChecker.isOutdated()) {
                        maybeShowUpdateDialog(version);
                    } else {
                        Controllers.dialog(new MessageDialogPane.Builder(
                                i18n("emcl.update.latest"),
                                i18n("emcl.update.title"),
                                MessageDialogPane.MessageType.INFO)
                                .addAction(i18n("button.ok"), null)
                                .build());
                    }
                });
            } catch (Throwable e) {
                LOG.warning("Failed to check for update", e);
                Platform.runLater(() -> Controllers.dialog(new MessageDialogPane.Builder(
                        i18n("emcl.update.check_failed") + "\n" + e.getLocalizedMessage(),
                        i18n("emcl.update.title"),
                        MessageDialogPane.MessageType.ERROR)
                        .addAction(i18n("button.ok"), null)
                        .build()));
            }
        }, "EMCL Update Check (manual)", true);
    }

    /// Downloads and applies the latest jar, then restarts.
    public static void updateNow(RemoteVersion version) {
        if (Boolean.getBoolean("emcl.packaged")) {
            // jpackage / EXE build: the whole app image must be replaced.
            MessageDialogPane pane = new MessageDialogPane.Builder(
                    i18n("emcl.update.exe_hint"),
                    i18n("emcl.update.title"),
                    MessageDialogPane.MessageType.WARNING).build();
            JFXButton download = new JFXButton(i18n("emcl.update.download_zip"));
            download.getStyleClass().add("dialog-accept");
            download.setOnAction(e -> {
                DialogUtils.close(pane);
                FXUtils.openLink(Metadata.MANUAL_UPDATE_URL);
            });
            pane.addButton(download);
            Controllers.dialog(pane);
            return;
        }

        // Jar build: download to a temporary file, relaunch, self-delete.
        Controllers.dialog(new MessageDialogPane.Builder(
                i18n("emcl.update.downloading"),
                i18n("emcl.update.title"),
                MessageDialogPane.MessageType.INFO)
                .addAction(i18n("button.ok"), null)
                .build());

        Path target;
        try {
            target = Files.createTempFile("emcl-update-", ".jar");
        } catch (IOException e) {
            LOG.warning("Failed to create temp file", e);
            return;
        }

        try {
            downloadTo(version.url(), target);
        } catch (IOException e) {
            LOG.warning("Failed to download update", e);
            Controllers.dialog(new MessageDialogPane.Builder(
                    i18n("emcl.update.download_failed") + "\n" + e.getLocalizedMessage(),
                    i18n("emcl.update.title"),
                    MessageDialogPane.MessageType.ERROR)
                    .addAction(i18n("button.ok"), null)
                    .build());
            return;
        }

        try {
            SettingsManager.savePendingChanges();
        } catch (Exception ignored) {
            // Best effort; the new instance will start anyway.
        }

        try {
            String javaBin = Path.of(System.getProperty("java.home"), "bin",
                    "java" + (org.jackhuang.hmcl.util.platform.OperatingSystem.CURRENT_OS == org.jackhuang.hmcl.util.platform.OperatingSystem.WINDOWS ? ".exe" : ""))
                    .toString();
            new ProcessBuilder(javaBin, "-jar", target.toString(), "--emcl-self-delete=" + target)
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            LOG.warning("Failed to relaunch with the new jar", e);
            Controllers.dialog(new MessageDialogPane.Builder(
                    i18n("emcl.update.restart_failed"),
                    i18n("emcl.update.title"),
                    MessageDialogPane.MessageType.ERROR)
                    .addAction(i18n("button.ok"), null)
                    .build());
            return;
        }

        Platform.exit();
        System.exit(0);
    }

    /// Simple redirect-following download to a file.
    private static void downloadTo(String url, Path target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setRequestProperty("User-Agent", "EMCL/" + Metadata.VERSION);
        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + code + " while downloading " + url);
        }
        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
        }
    }
}
