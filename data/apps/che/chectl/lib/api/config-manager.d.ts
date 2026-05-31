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
 * ChectlConfig contains necessary methods to interact with cache configDir of chectl.
 */
export declare class ConfigManager {
    private static configManager;
    private static readonly CHECTL_CONFIG_FILE_NAME;
    private data;
    private readonly configPath;
    private constructor();
    static getInstance(): ConfigManager;
    setProperty(name: string, value: any): void;
    getProperty(name: string): any;
    private readData;
}
