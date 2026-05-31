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
export declare namespace OlmTasks {
    function getDeleteSubscriptionAndCatalogSourceTask(packageName: string, csvPrefix: string, namespace: string): Promise<Listr.ListrTask<any>>;
    function getDeleteOperatorsTask(): Listr.ListrTask<any>;
    function getCreateSubscriptionTask(name: string, namespace: string, catalogSource: string, catalogSourceNamespace: string, packageName: string, channel: string, approvalStrategy: string, startingCSV?: string): Listr.ListrTask<any>;
    function getCreateCatalogSourceTask(name: string, namespace: string, image: string): Listr.ListrTask<any>;
    function getCreatePrometheusRBACTask(): Listr.ListrTask<any>;
    function getApproveInstallPlanTask(subscriptionName: string): Listr.ListrTask<any>;
    function getSetCustomEclipseCheOperatorImageTask(): Listr.ListrTask<any>;
    function getFetchCheClusterSampleTask(): Listr.ListrTask<any>;
}
