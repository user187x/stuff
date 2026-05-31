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
import { Installer } from '../installer';
/**
 * Handle setup of the dev workspace operator controller.
 */
export declare class DevWorkspaceOperatorInstaller implements Installer {
    protected skip: boolean;
    constructor();
    getDeployTasks(): Listr.ListrTask<any>;
    getUpdateTasks(): Listr.ListrTask<any>;
    getDeleteTasks(): Listr.ListrTask<any>;
    getPreUpdateTasks(): Listr.ListrTask<any>;
}
