"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.CheCtlVersion = exports.CHECTL_DEVELOPMENT_VERSION = void 0;
const tslib_1 = require("tslib");
const axios_1 = require("axios");
const core_1 = require("@oclif/core");
const fs = require("fs-extra");
const https = require("node:https");
const path = require("node:path");
const semver = require("semver");
const github_client_1 = require("../api/github-client");
const utls_1 = require("./utls");
const context_1 = require("../context");
exports.CHECTL_DEVELOPMENT_VERSION = '0.0.2';
const UPDATE_INFO_FILENAME = 'update-info.json';
const A_DAY_IN_MS = 24 * 60 * 60 * 1000;
var CheCtlVersion;
(function (CheCtlVersion) {
    /**
     * Returns latest chectl version for the given channel.
     */
    function getLatestCheCtlVersion(channel) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            if (!context_1.CheCtlContext.get()[context_1.CliContext.CLI_IS_CHECTL]) {
                return;
            }
            const axiosInstance = axios_1.default.create({
                httpsAgent: new https.Agent({}),
            });
            try {
                const { data } = yield axiosInstance.get(`https://che-incubator.github.io/chectl/channels/${channel}/linux-x64`);
                return data.version;
            }
            catch (_a) {
                return;
            }
        });
    }
    CheCtlVersion.getLatestCheCtlVersion = getLatestCheCtlVersion;
    /**
     * Checks whether there is an update available for current chectl.
     */
    function isCheCtlUpdateAvailable(cacheDir_1) {
        return tslib_1.__awaiter(this, arguments, void 0, function* (cacheDir, forceRecheck = false) {
            // Do not use ctx inside this function as the function is used from hook where ctx is not yet defined.
            if (!context_1.CheCtlContext.get()[context_1.CliContext.CLI_IS_CHECTL]) {
                // Do nothing for not chectl flavors
                return false;
            }
            const currentVersion = (0, utls_1.getProjectVersion)();
            if (currentVersion === exports.CHECTL_DEVELOPMENT_VERSION) {
                // Skip it, chectl is built from source
                return false;
            }
            const channel = currentVersion.includes('next') ? 'next' : 'stable';
            const newVersionInfoFilePath = path.join(cacheDir, `${channel}-${UPDATE_INFO_FILENAME}`);
            let newVersionInfo = {
                latestVersion: '0.0.0',
                lastCheck: 0,
            };
            if (yield fs.pathExists(newVersionInfoFilePath)) {
                try {
                    newVersionInfo = (yield fs.readJson(newVersionInfoFilePath, { encoding: 'utf8' }));
                }
                catch (_a) {
                    // file is corrupted
                }
            }
            // Check cache, if it is already known that newer version available
            let isCachedNewerVersionAvailable = false;
            try {
                isCachedNewerVersionAvailable = yield gtCheCtlVersion(newVersionInfo.latestVersion, currentVersion);
            }
            catch (error) {
                // not a version (corrupted data)
                core_1.ux.debug(`Failed to compare versions '${newVersionInfo.latestVersion}' and '${currentVersion}': ${error}`);
            }
            const now = Date.now();
            const isCacheExpired = now - newVersionInfo.lastCheck > A_DAY_IN_MS;
            if (forceRecheck || (!isCachedNewerVersionAvailable && isCacheExpired)) {
                // Cached info is expired. Fetch actual info about versions.
                // undefined cannot be returned from getLatestChectlVersion as 'is flavor' check was done before.
                const latestVersion = (yield getLatestCheCtlVersion(channel));
                // if request failed (GitHub endpoint is not available) then
                // assume update is not available
                if (!latestVersion) {
                    return false;
                }
                newVersionInfo = { latestVersion, lastCheck: now };
                yield fs.writeJson(newVersionInfoFilePath, newVersionInfo, { encoding: 'utf8' });
                try {
                    return gtCheCtlVersion(newVersionInfo.latestVersion, currentVersion);
                }
                catch (error) {
                    // not to fail unexpectedly
                    core_1.ux.debug(`Failed to compare versions '${newVersionInfo.latestVersion}' and '${currentVersion}': ${error}`);
                    return false;
                }
            }
            // Information whether a newer version available is already in cache
            return isCachedNewerVersionAvailable;
        });
    }
    CheCtlVersion.isCheCtlUpdateAvailable = isCheCtlUpdateAvailable;
    /**
     * Returns true if verA > verB
     */
    function gtCheCtlVersion(verA, verB) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return (yield compareCheCtlVersions(verA, verB)) > 0;
        });
    }
    CheCtlVersion.gtCheCtlVersion = gtCheCtlVersion;
    /**
     * Retruns:
     *  1 if verA > verB
     *  0 if verA = verB
     * -1 if verA < verB
     */
    function compareCheCtlVersions(verA, verB) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            if (verA === verB) {
                return 0;
            }
            const verAChannel = verA.includes('next') ? 'next' : 'stable';
            const verBChannel = verB.includes('next') ? 'next' : 'stable';
            if (verAChannel !== verBChannel) {
                // Consider next is always newer
                return (verAChannel === 'next') ? 1 : -1;
            }
            if (verAChannel === 'stable') {
                return semver.gt(verA, verB) ? 1 : -1;
            }
            // Compare next versions, like: 0.0.20210715-next.597729a
            const verABase = verA.split('-')[0];
            const verBBase = verB.split('-')[0];
            if (verABase !== verBBase) {
                // Releases are made in different days
                // It is possible to compare just versions
                return semver.gt(verA, verB) ? 1 : -1;
            }
            // Releases are made in the same day
            // It is not possible to compare by versions as the difference only in commits hashes
            const verACommitId = verA.split('-')[1].split('.')[1];
            const verBCommitId = verB.split('-')[1].split('.')[1];
            const githubClient = new github_client_1.CheGithubClient();
            const verACommitDateString = yield githubClient.getCommitDate(github_client_1.ECLIPSE_CHE_INCUBATOR_ORG, github_client_1.CHECTL_REPO, verACommitId);
            const verBCommitDateString = yield githubClient.getCommitDate(github_client_1.ECLIPSE_CHE_INCUBATOR_ORG, github_client_1.CHECTL_REPO, verBCommitId);
            const verATimestamp = Date.parse(verACommitDateString);
            const verBTimestamp = Date.parse(verBCommitDateString);
            return verATimestamp > verBTimestamp ? 1 : -1;
        });
    }
})(CheCtlVersion || (exports.CheCtlVersion = CheCtlVersion = {}));
//# sourceMappingURL=chectl-version.js.map