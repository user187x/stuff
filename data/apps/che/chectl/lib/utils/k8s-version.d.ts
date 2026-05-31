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
export declare namespace K8sVersion {
    const MINIMAL_K8S_VERSION = "1.19";
    function checkMinimalK8sVersion(actualVersion: string): boolean;
    /**
     * Compare versions and return true if actual version is greater or equal to minimal.
     * The comparison will be done by major and minor versions.
     */
    function checkMinimalVersion(actual: string, minimal: string): boolean;
    function getMinimalK8sVersionError(actualVersion: string): Error;
}
