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
export declare namespace MinikubeTasks {
    /**
     * Returns tasks list which perform preflight platform checks.
     */
    function getPreflightCheckTasks(): Listr.ListrTask<any>[];
    function configureApiServerForDex(): Listr.ListrTask<any>[];
}
