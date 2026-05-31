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
export declare namespace PodTasks {
    function getDeploymentExistanceTask(deploymentName: string, namespace: string): Listr.ListrTask<any>;
    function getWaitLatestReplicaTask(deploymentName: string, namespace: string): Listr.ListrTask<any>;
    function getScaleDeploymentTask(name: string, deploymentName: string, replicas: number, namespace: string): Listr.ListrTask<any>;
    function getPodDeletedTask(name: string, selector: string, namespace: string): Listr.ListrTask<any>;
    function getPodStartTasks(name: string, selector: string, namespace: string): Listr.ListrTask<any>;
}
