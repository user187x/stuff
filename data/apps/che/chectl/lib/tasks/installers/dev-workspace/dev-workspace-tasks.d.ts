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
export declare namespace DevWorkspacesTasks {
    function getDeleteWebhooksTask(): Listr.ListrTask<any>;
    function getDeleteCustomResourcesTasks(): Listr.ListrTask<any>[];
    function getDeleteServicesTask(): Listr.ListrTask<any>;
    function getDeleteWorkloadsTask(): Promise<Listr.ListrTask<any>>;
    function getDeleteRbacTask(): Listr.ListrTask<any>;
    function getDeleteCertificatesTask(): Listr.ListrTask<any>;
    function getCreateOrUpdateDevWorkspaceTask(isCreateOnly: boolean): Listr.ListrTask<any>;
    function getWaitDevWorkspaceTask(): Listr.ListrTask<any>;
}
