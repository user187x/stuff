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
import ListrModule = require('listr');
export declare function base64Decode(arg: string): string;
export declare function sleep(ms: number): Promise<void>;
export declare function newError(message: string, cause: Error): Error;
export declare function getProjectName(): string;
export declare function getProjectVersion(): string;
export declare function safeLoadFromYamlFile(filePath: string): any;
export declare function getEmbeddedTemplatesDirectory(): string;
export declare function addTrailingSlash(url: string): string;
export declare function getImageNameAndTag(image: string): [string, string];
export declare function newListr(tasks?: ReadonlyArray<ListrModule.ListrTask<any>>, collapse?: boolean): ListrModule;
export declare function isPartOfEclipseChe(resource: any): boolean;
export declare function isCheFlavor(): boolean;
export declare function isCommandExists(commandName: string): Promise<boolean>;
