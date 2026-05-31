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
import * as Listr from 'listr';
import { KubeClient } from '../../api/kube-client';
import { Installer } from './installer';
export declare namespace Dex {
    const CONFIG_MAP = "dex-ca";
    const CONFIG_MAP_LABELS: {
        'app.kubernetes.io/part-of': string;
        'app.kubernetes.io/component': string;
    };
}
export declare class DexInstaller implements Installer {
    private static readonly DEX_USERNAME;
    private static readonly DEX_PASSWORD;
    private static readonly DEX_PASSWORD_HASH;
    private static readonly CLIENT_ID;
    private static readonly DEX_NAME;
    private static readonly NAMESPACE_NAME;
    private static readonly TLS_SECRET_NAME;
    private static readonly CREDENTIALS_SECRET_NAME;
    private static readonly CA_CERTIFICATE_FILENAME;
    private static readonly SELECTOR;
    protected kubeClient: KubeClient;
    constructor();
    getDeployTasks(): Listr.ListrTask<any>;
    getDexCaCertificateFilePath(): string;
    getDexResourceFilePath(fileName: string): string;
    getPreUpdateTasks(): Listr.ListrTask<any>;
    getUpdateTasks(): Listr.ListrTask<any>;
    getDeleteTasks(): Listr.ListrTask<any>;
}
