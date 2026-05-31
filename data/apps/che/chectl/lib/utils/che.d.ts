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
export declare namespace Che {
    function isRedHatCatalogSources(catalogSourceName?: string): boolean;
    function readPodLog(namespace: string, podLabelSelector: string | undefined, directory: string, follow: boolean): Promise<void>;
    function readNamespaceEvents(namespace: string, directory: string, follow: boolean): Promise<void>;
    function getCheClusterFieldConfigured(fieldPath: string): any | undefined;
    function getCheVersion(): Promise<string>;
    function buildDashboardURL(cheUrl: string): string;
    function getCheURL(namespace: string): Promise<string>;
    /**
     * Gets self-signed Che CA certificate from 'self-signed-certificate' secret.
     * If secret doesn't exist, undefined is returned.
     */
    function readCheCaCert(namespace: string): Promise<string | undefined>;
}
