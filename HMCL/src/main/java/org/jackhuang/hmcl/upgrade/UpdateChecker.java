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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * EMCL update checker.
 *
 * <p>Instead of a version channel API, EMCL compares the local injected commit
 * hash ({@code emcl.version.hash}, first 7 characters) with the latest commit
 * sha of the {@code main} branch of the ETQWFD/EMCL repository. No prompt is
 * shown when the local hash is missing, the network fails, the shas match, or
 * the version was explicitly ignored by the user.
 */
public final class UpdateChecker {
    private UpdateChecker() {
    }

    private static final String GITHUB_COMMITS_API = "https://api.github.com/repos/ETQWFD/EMCL/commits/main";

    private static final ObjectProperty<RemoteVersion> latestVersion = new SimpleObjectProperty<>();
    private static final ReadOnlyStringWrapper latestCommitSha = new ReadOnlyStringWrapper();
    private static final ReadOnlyBooleanWrapper checkingUpdate = new ReadOnlyBooleanWrapper(false);
    private static final ReadOnlyObjectWrapper<@Nullable Throwable> error = new ReadOnlyObjectWrapper<>();

    private static final BooleanBinding outdated = Bindings.createBooleanBinding(() -> {
        String remote = latestCommitSha.get();
        String local = Metadata.GITHUB_SHA;
        if (StringUtils.isBlank(local) || StringUtils.isBlank(remote)) {
            // No injected local hash or failed to fetch: do not prompt.
            return false;
        }
        String l = local.toLowerCase();
        String r = remote.toLowerCase();
        // Treat as up-to-date when either sha is a prefix of the other
        // (handles full-40 vs short-7 hashes in either direction).
        if (r.equals(l) || r.startsWith(l) || l.startsWith(r)) {
            return false;
        }
        String ignored = SettingsManager.userSettings().getEmclUpdateIgnoreVersion();
        if (StringUtils.isNotBlank(ignored) && r.startsWith(ignored.toLowerCase())) {
            return false;
        }
        return true;
    }, latestCommitSha);

    public static void init() {
        requestCheckUpdate(UpdateChannel.getChannel(), settings().acceptPreviewUpdateProperty().get());
    }

    public static RemoteVersion getLatestVersion() {
        return latestVersion.get();
    }

    public static ReadOnlyObjectProperty<RemoteVersion> latestVersionProperty() {
        return latestVersion;
    }

    public static boolean isOutdated() {
        return outdated.get();
    }

    public static ObservableBooleanValue outdatedProperty() {
        return outdated;
    }

    public static boolean isCheckingUpdate() {
        return checkingUpdate.get();
    }

    public static ReadOnlyBooleanProperty checkingUpdateProperty() {
        return checkingUpdate.getReadOnlyProperty();
    }

    public static ReadOnlyObjectProperty<@Nullable Throwable> errorProperty() {
        return error.getReadOnlyProperty();
    }

    /// Fetches the latest commit sha of ETQWFD/EMCL@main (synchronous).
    public static RemoteVersion checkNow(UpdateChannel channel, boolean preview) throws IOException {
        JsonObject response = JsonUtils.fromNonNullJson(NetworkUtils.doGet(GITHUB_COMMITS_API), JsonObject.class);
        JsonElement shaElement = response.get("sha");
        if (shaElement == null) {
            throw new IOException("GitHub commits response is missing sha");
        }
        String sha = shaElement.getAsString();
        if (StringUtils.isBlank(sha)) {
            throw new IOException("GitHub commits response has empty sha");
        }
        latestCommitSha.set(sha);
        String shortSha = sha.length() >= 7 ? sha.substring(0, 7) : sha;
        return new RemoteVersion(channel, shortSha, Metadata.DOWNLOAD_URL, RemoteVersion.Type.JAR, new IntegrityCheck("NONE", ""), preview, true);
    }

    public static void requestCheckUpdate(UpdateChannel channel, boolean preview) {
        Platform.runLater(() -> {
            if (isCheckingUpdate()) {
                return;
            }
            checkingUpdate.set(true);

            thread(() -> {
                RemoteVersion result = null;
                Throwable t = null;
                try {
                    result = checkNow(channel, preview);
                    LOG.info("EMCL latest commit is " + result);
                } catch (Throwable e) {
                    t = e;
                    LOG.warning("Failed to check for update", e);
                }
                Throwable throwable = t;
                RemoteVersion finalResult = result;

                Platform.runLater(() -> {
                    if (throwable != null) {
                        latestVersion.set(null);
                        error.set(throwable);
                    } else {
                        error.set(null);
                        latestVersion.set(finalResult);
                    }
                    checkingUpdate.set(false);

                    // EMCL: auto-show the three-button update dialog on startup.
                    if (throwable == null && finalResult != null && isOutdated()) {
                        EMCLUpdateHandler.maybeShowUpdateDialog(finalResult);
                    }
                });
            }, "Update Checker", true);
        });
    }
}
