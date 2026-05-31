"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.ConfigManager = void 0;
const fs = require("fs-extra");
const lodash_1 = require("lodash");
const path = require("node:path");
const context_1 = require("../context");
/**
 * ChectlConfig contains necessary methods to interact with cache configDir of chectl.
 */
class ConfigManager {
    constructor(configDir) {
        if (!fs.existsSync(configDir)) {
            fs.mkdirsSync(configDir);
        }
        this.configPath = path.join(configDir, ConfigManager.CHECTL_CONFIG_FILE_NAME);
        this.data = this.readData();
    }
    static getInstance() {
        if (this.configManager) {
            return this.configManager;
        }
        const ctx = context_1.CheCtlContext.get();
        const configDir = ctx[context_1.CliContext.CLI_CONFIG_DIR];
        this.configManager = new ConfigManager(configDir);
        return this.configManager;
    }
    setProperty(name, value) {
        this.data = (0, lodash_1.merge)(this.data, { [name]: value });
        fs.writeFileSync(this.configPath, JSON.stringify(this.data));
    }
    getProperty(name) {
        return this.data[name];
    }
    readData() {
        if (!fs.existsSync(this.configPath)) {
            return {};
        }
        return JSON.parse(fs.readFileSync(this.configPath, 'utf8'));
    }
}
exports.ConfigManager = ConfigManager;
ConfigManager.CHECTL_CONFIG_FILE_NAME = 'config.json';
//# sourceMappingURL=config-manager.js.map