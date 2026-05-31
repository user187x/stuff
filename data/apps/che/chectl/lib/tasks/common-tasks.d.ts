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
import { CreateResource, DeleteResource, IsResourceExists, ReplaceResource } from './installers/installer';
export declare namespace CommonTasks {
    function getTestKubernetesApiTasks(): Listr.ListrTask<any>;
    function getDeleteNamespaceTask(namespace: string): Listr.ListrTask<any>;
    function getCreateNamespaceTask(namespaceName: string, labels: {}): Listr.ListrTask<any>;
    function getCreateOrUpdateResourceTask(isCreateOnly: boolean, resourceKind: string, resourceName: string, isExistsResource: IsResourceExists, createResource: CreateResource, replaceResource: ReplaceResource): Listr.ListrTask<any>;
    function getSkipTask(title: string, skipMsg: string): Listr.ListrTask<any>;
    function getNotEclipseCheResourceSkipTask(title: string): Listr.ListrTask<any>;
    function getDisabledTask(): Listr.ListrTask<any>;
    function getCreateResourceTask(resourceKind: string, resourceName: string, isExistsResource: IsResourceExists, createResource: CreateResource): Listr.ListrTask<any>;
    function getDeleteResourcesTask(taskTitle: string, deleteResources: DeleteResource[]): {
        title: string;
        task: (_ctx: any, task: any) => Promise<void>;
    };
    function getWaitTask(milliseconds: number): Listr.ListrTask<any>;
    function getOpenShiftVersionTask(): Listr.ListrTask;
    function getPreparePostInstallationOutputTask(): Listr.ListrTask<any>;
    function getPrintHighlightedMessagesTask(): Listr.ListrTask<any>;
    function getVerifyCommand(title: string, errorMsg: string, isVerifiedResource: () => Promise<boolean> | boolean): Listr.ListrTask<any>;
}
