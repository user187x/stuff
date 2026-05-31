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
/**
 * Returns command success message with execution time.
 */
export declare function getCommandSuccessMessage(): string;
/**
 * Wraps error into command error.
 */
export declare function wrapCommandError(error: Error): Error;
export declare function notifyCommandCompletedSuccessfully(): void;
export declare function askForChectlUpdateIfNeeded(): Promise<void>;
