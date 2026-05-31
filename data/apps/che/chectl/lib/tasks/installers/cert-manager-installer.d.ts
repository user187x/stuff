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
import { Installer } from './installer';
export declare namespace CertManager {
    const NAMESPACE = "cert-manager";
    const VERSION = "v1.8.2";
    function getApplyResourcesTask(): Listr.ListrTask<any>;
    function getWaitCertManagerTask(): Listr.ListrTask<any>;
}
export declare class CertManagerInstaller implements Installer {
    protected skip: boolean;
    constructor();
    getDeployTasks(): Listr.ListrTask<any>;
    getPreUpdateTasks(): Listr.ListrTask<any>;
    getUpdateTasks(): Listr.ListrTask<any>;
    getDeleteTasks(): Listr.ListrTask<any>;
}
