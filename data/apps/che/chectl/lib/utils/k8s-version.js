"use strict";
/**
 * Copyright (c) 2019-2022 Red Hat, Inc.
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
exports.K8sVersion = void 0;
var K8sVersion;
(function (K8sVersion) {
    K8sVersion.MINIMAL_K8S_VERSION = '1.19';
    function checkMinimalK8sVersion(actualVersion) {
        return checkMinimalVersion(actualVersion, K8sVersion.MINIMAL_K8S_VERSION);
    }
    K8sVersion.checkMinimalK8sVersion = checkMinimalK8sVersion;
    /**
     * Compare versions and return true if actual version is greater or equal to minimal.
     * The comparison will be done by major and minor versions.
     */
    function checkMinimalVersion(actual, minimal) {
        actual = removeVPrefix(actual);
        let vers = actual.split('.');
        const actualMajor = Number.parseInt(vers[0], 10);
        const actualMinor = Number.parseInt(vers[1], 10);
        minimal = removeVPrefix(minimal);
        vers = minimal.split('.');
        const minimalMajor = Number.parseInt(vers[0], 10);
        const minimalMinor = Number.parseInt(vers[1], 10);
        return (actualMajor > minimalMajor || (actualMajor === minimalMajor && actualMinor >= minimalMinor));
    }
    K8sVersion.checkMinimalVersion = checkMinimalVersion;
    function getMinimalK8sVersionError(actualVersion) {
        return new Error(`The minimal supported version of Kubernetes is '${K8sVersion.MINIMAL_K8S_VERSION} but '${actualVersion}' was found. To bypass version check use '--skip-version-check' flag.`);
    }
    K8sVersion.getMinimalK8sVersionError = getMinimalK8sVersionError;
    /**
     * Removes 'v' prefix from version string.
     * @param version version to process
     * @param checkForNumber if true remove prefix only if a numeric version follow it (e.g. v7.x -> 7.x, vNext -> vNext)
     */
    function removeVPrefix(version, checkForNumber = false) {
        if (version.startsWith('v') && version.length > 1) {
            if (checkForNumber) {
                const char2 = version.charAt(1);
                if (char2 >= '0' && char2 <= '9') {
                    return version.slice(1);
                }
            }
            return version.slice(1);
        }
        return version;
    }
})(K8sVersion || (exports.K8sVersion = K8sVersion = {}));
//# sourceMappingURL=k8s-version.js.map