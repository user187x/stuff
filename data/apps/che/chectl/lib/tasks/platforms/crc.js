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
exports.CRCTasks = void 0;
const tslib_1 = require("tslib");
const execa = require("execa");
const context_1 = require("../../context");
const common_tasks_1 = require("../common-tasks");
const utls_1 = require("../../utils/utls");
/**
 * Helper for Code Ready Container
 */
var CRCTasks;
(function (CRCTasks) {
    function getPreflightCheckTasks() {
        const flags = context_1.CheCtlContext.getFlags();
        return [
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if oc is installed', 'oc not found', () => (0, utls_1.isCommandExists)('oc')),
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if OpenShift Local is installed', 'OpenShift Local not found', () => (0, utls_1.isCommandExists)('crc')),
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if OpenShift Local is running', 'OpenShift Local not ready', () => isCRCRunning()),
            {
                title: 'Retrieving OpenShift Local IP and domain for routes URLs',
                enabled: () => flags.domain !== undefined,
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const ip = yield getCRCIP();
                    flags.domain = ip + '.nip.io';
                    task.title = `${task.title}...[${flags.domain}]`;
                }),
            },
        ];
    }
    CRCTasks.getPreflightCheckTasks = getPreflightCheckTasks;
    function isCRCRunning() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { exitCode, stdout } = yield execa('crc', ['status'], { timeout: 60000, reject: false });
            return Boolean(exitCode === 0 &&
                stdout.includes('CRC VM:          Running') &&
                stdout.includes('OpenShift:       Running'));
        });
    }
    function getCRCIP() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { stdout } = yield execa('crc', ['ip'], { timeout: 10000 });
            return stdout;
        });
    }
})(CRCTasks || (exports.CRCTasks = CRCTasks = {}));
//# sourceMappingURL=crc.js.map