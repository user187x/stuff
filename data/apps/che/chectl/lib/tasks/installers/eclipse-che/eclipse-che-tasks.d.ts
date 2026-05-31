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
export declare namespace EclipseCheTasks {
    function getCreateOrUpdateDeploymentTask(isCreateOnly: boolean): Listr.ListrTask<any>;
    function getCreateOrUpdateCrdTask(isCreateOnly: boolean): Listr.ListrTask<any>;
    function getCreateOrUpdateMutatingWebhookTask(isCreateOnly: boolean): Listr.ListrTask<any>;
    function getCreateOrUpdateValidatingWebhookTask(isCreateOnly: boolean): Listr.ListrTask<any>;
    function getCreateOrUpdateIssuerTask(isCreateOnly: boolean): Listr.ListrTask<any>;
    function getCreateOrUpdateCertificateTask(isCreateOnly: boolean): Listr.ListrTask<any>;
    function getCreateOrUpdateServiceAccountTask(isCreateOnly: boolean): Listr.ListrTask<any>;
    function getCreateOrUpdateServiceTask(isCreateOnly: boolean): Listr.ListrTask<any>;
    function getCreateOrUpdateRbacTasks(isCreateOnly: boolean): Listr.ListrTask<any>;
    function getDiscoverUpgradeImagePathTask(): Listr.ListrTask<any>;
    function getDeleteClusterScopeObjectsTask(): Promise<Listr.ListrTask<any>>;
    function getDeleteEclipseCheResourcesTask(): Listr.ListrTask<any>;
    function getDeleteNetworksTask(): Listr.ListrTask<any>;
    function getDeleteImageContentSourcePolicyTask(): Promise<Listr.ListrTask<any>>;
    function getDeleteWorkloadsTask(): Promise<Listr.ListrTask<any>>;
    function getDeleteRbacTask(): Listr.ListrTask<any>;
    function getDeleteCertificatesTask(): Listr.ListrTask<any>;
    function getCreateImageContentSourcePolicyTask(): Listr.ListrTask<Listr.ListrContext>;
}
