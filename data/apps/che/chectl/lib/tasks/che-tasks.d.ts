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
export declare namespace CheTasks {
    function getWaitCheDeployedTasks(): Listr.ListrTask<any>;
    function getWaitPodsDeletedTasks(): Listr.ListrTask<any>;
    function getScaleCheDownTasks(): Listr.ListrTask<any>;
    function getScaleCheUpTasks(): Listr.ListrTask<any>;
    function getDebugTasks(): Listr.ListrTask<any>;
    function getServerLogsTasks(follow: boolean): Listr.ListrTask<any>;
    function getRetrieveSelfSignedCertificateTask(): Listr.ListrTask;
    function getWaitEclipseCheActiveTask(): Listr.ListrTask<any>;
}
