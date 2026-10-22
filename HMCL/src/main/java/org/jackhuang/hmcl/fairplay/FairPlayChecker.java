/*
 * EMCL (Eternal Minecraft Launcher)
 * Copyright (C) 2026  ETC Team <2416444244@qq.com>
 *
 * Forked from Hello Minecraft! Launcher (HMCL)
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.fairplay;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * EMCL fair-play protection: a static pre-launch check that refuses to start
 * the game when known cheat clients / cheat mods or JVM injection arguments
 * are detected. The reason is written to {@code EMCL/FairPlayBlock.txt}.
 *
 * <p>Honest boundary: this is a static, file-name / metadata / argument level
 * check. It cannot detect driver-level, system-level or fully unknown
 * obfuscated injections.
 */
public final class FairPlayChecker {
    private FairPlayChecker() {
    }

    private static final String[] CHEAT_TOKENS = {
            "wurst", "impact", "liquidbounce", "sigma", "aristois", "meteor-client", "meteorclient",
            "kami", "kamo", "salhack", "rusherhack", "seppuku", "phobos", "inertia", "nightx",
            "flare-client", "eris", "koid", "xatz", "exeter", "aurora-client", "tenacity", "raven",
            "fdpclient", "novoline", "vape", "kazuki", "sprayclient", "trollhack", "gameiris",
            "moonx", "future-client", "yolist", "exploitfixer-no", "xray-client", "killaura", "crystalaura"
    };

    private static final String[] BLOCKED_JVM_ARG_TOKENS = {
            "-javaagent", "-agentpath", "-agentlib", "-Xverify:none", "-noverify", "-XX:-UseSplitVerifier"
    };

    private static final String[] BLOCKED_ENV_VARS = {
            "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"
    };

    /**
     * Runs the static fair-play check for the given instance.
     *
     * @return a list of human-readable violation reasons; empty means OK.
     */
    public static List<String> check(HMCLGameInstance gameInstance) {
        List<String> violations = new ArrayList<>();

        // 1) JVM arguments configured for the instance.
        GameSettings.Effective settings = gameInstance.getEffectiveSettings();
        String jvmOptions = settings.getInheritable(GameSettings::jvmOptionsProperty);
        if (StringUtils.isNotBlank(jvmOptions)) {
            for (String arg : StringUtils.tokenize(jvmOptions)) {
                String lower = arg.toLowerCase(Locale.ROOT);
                for (String token : BLOCKED_JVM_ARG_TOKENS) {
                    if (lower.startsWith(token)) {
                        violations.add(i18n("fairplay.reason.jvm_arg", arg));
                        break;
                    }
                }
            }
        }

        // 2) Injection environment variables.
        for (String var : BLOCKED_ENV_VARS) {
            String value = System.getenv(var);
            if (StringUtils.isNotBlank(value)) {
                String lower = value.toLowerCase(Locale.ROOT);
                for (String token : BLOCKED_JVM_ARG_TOKENS) {
                    if (lower.contains(token)) {
                        violations.add(i18n("fairplay.reason.env", var, value.trim()));
                        break;
                    }
                }
            }
        }

        // 3) File scan: instance dir, mods, coremods and version-specific mods.
        Path runDir = gameInstance.getRunDirectory();
        List<Path> scanDirs = new ArrayList<>();
        scanDirs.add(runDir.resolve("mods"));
        scanDirs.add(runDir.resolve("coremods"));
        if (gameInstance.getVersion() != null) {
            scanDirs.add(runDir.resolve("mods").resolve(gameInstance.getVersion().toString()));
        }
        scanDirs.add(runDir);

        for (Path dir : scanDirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.walk(dir, 3)) {
                var files = stream.filter(Files::isRegularFile).toList();
                for (Path file : files) {
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".jar") && !name.endsWith(".zip")) {
                        // File-name level scan (e.g. a loose cheat client class jar).
                        if (matchesToken(name)) {
                            violations.add(i18n("fairplay.reason.file", file.toString()));
                        }
                        continue;
                    }
                    if (matchesToken(name)) {
                        violations.add(i18n("fairplay.reason.file", file.toString()));
                        continue;
                    }
                    // Metadata / package scan for jars whose name looks mod-like.
                    if (name.endsWith(".jar")) {
                        List<String> meta = scanJarMetadata(file);
                        if (!meta.isEmpty()) {
                            violations.add(i18n("fairplay.reason.mod", file.toString(), String.join(", ", meta)));
                        }
                    }
                }
            } catch (IOException e) {
                // Skip unreadable directories.
            }
        }

        return violations;
    }

    private static boolean matchesToken(String lowerName) {
        for (String token : CHEAT_TOKENS) {
            if (lowerName.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Opens a jar and looks at its mod metadata and entry names for cheat tokens.
     * Bounded: only the first 200 entries are inspected and the metadata files.
     */
    private static List<String> scanJarMetadata(Path file) {
        List<String> hits = new ArrayList<>();
        if (file.toFile().length() > 64 * 1024 * 1024) {
            return hits;
        }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entries = zip.entries();
            int inspected = 0;
            while (entries.hasMoreElements() && inspected < 400) {
                ZipEntry entry = entries.nextElement();
                inspected++;
                String entryName = entry.getName().toLowerCase(Locale.ROOT);
                boolean isMetadata = entryName.equals("fabric.mod.json")
                        || entryName.equals("quilt.mod.json")
                        || entryName.endsWith("/fabric.mod.json")
                        || entryName.equals("mcmod.info")
                        || entryName.equals("mods.toml");
                if (isMetadata && !entry.isDirectory()) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                        for (String token : CHEAT_TOKENS) {
                            if (content.contains(token)) {
                                hits.add(token);
                                break;
                            }
                        }
                    }
                } else if (entryName.endsWith(".class")) {
                    for (String token : CHEAT_TOKENS) {
                        if (entryName.contains(token.replace("-", ""))) {
                            hits.add(token);
                            break;
                        }
                    }
                }
                if (!hits.isEmpty()) {
                    break;
                }
            }
        } catch (IOException | RuntimeException e) {
            // Corrupt or unreadable jar: ignore.
        }
        return hits;
    }

    /// Writes the block reasons to `EMCL/FairPlayBlock.txt`.
    public static void writeBlockFile(List<String> violations) {
        try {
            Path dir = Metadata.EMCL_USER_HOME;
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder();
            sb.append("EMCL Fair Play Block - ").append(new java.util.Date()).append('\n');
            sb.append(i18n("fairplay.blocked")).append('\n');
            for (String violation : violations) {
                sb.append(" - ").append(violation).append('\n');
            }
            sb.append(i18n("fairplay.removal_hint")).append('\n');
            Files.writeString(dir.resolve("FairPlayBlock.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Non-fatal.
        }
    }

    /// Shows the block dialog and writes the block file.
    public static void showBlockDialog(List<String> violations) {
        writeBlockFile(violations);
        StringBuilder sb = new StringBuilder(i18n("fairplay.blocked")).append("\n\n");
        for (String violation : violations) {
            sb.append(" • ").append(violation).append('\n');
        }
        sb.append('\n').append(i18n("fairplay.removal_hint"));
        Controllers.dialog(new MessageDialogPane.Builder(sb.toString(), i18n("fairplay.title"), MessageDialogPane.MessageType.WARNING)
                .addAction(i18n("button.ok"), null)
                .build());
    }
}
