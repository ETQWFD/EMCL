/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNullByDefault;

/**
 * Region detection is intentionally disabled in EMCL.
 *
 * <p>The upstream launcher gates (disables) game/mod downloading and shows a legal
 * notice when it believes the machine/network is in China Mainland, and a failed
 * geo-IP probe was treated as "China" as well. EMCL removes that behaviour entirely:
 * downloading is always enabled for every user, and mirror/official source selection
 * is handled globally (mirror-first with official fallback) in
 * {@link org.jackhuang.hmcl.download.HMCLDownloadProvider}.</p>
 */
@NotNullByDefault
public final class RegionUtils {

    /// Kept as a no-op so existing call sites (Launcher initialization) do not change.
    /// Never probes location and never blocks the UI.
    public static void init() {
    }

    /** Always {@code false}: no machine-region restriction is applied. */
    public static boolean isIsChinaMainlandMachine() {
        return false;
    }

    /** Always {@code false}: download pages are never disabled and no region/legal hint is shown. */
    public static boolean isIsChinaMainlandNetwork() {
        return false;
    }

    private RegionUtils() {
    }
}
