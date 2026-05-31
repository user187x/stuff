/**
 * Copyright (c) 2019-2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
export declare const CHECTL_DEVELOPMENT_VERSION = "0.0.2";
export declare namespace CheCtlVersion {
    /**
     * Returns latest chectl version for the given channel.
     */
    function getLatestCheCtlVersion(channel: string): Promise<string | undefined>;
    /**
     * Checks whether there is an update available for current chectl.
     */
    function isCheCtlUpdateAvailable(cacheDir: string, forceRecheck?: boolean): Promise<boolean>;
    /**
     * Returns true if verA > verB
     */
    function gtCheCtlVersion(verA: string, verB: string): Promise<boolean>;
}
