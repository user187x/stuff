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
exports.MicroK8sTasks = void 0;
const tslib_1 = require("tslib");
const execa = require("execa");
const context_1 = require("../../context");
const flags_1 = require("../../flags");
const common_tasks_1 = require("../common-tasks");
const utls_1 = require("../../utils/utls");
var MicroK8sTasks;
(function (MicroK8sTasks) {
    /**
     * Returns tasks list which perform preflight platform checks.
     */
    function getPeflightCheckTasks() {
        const flags = context_1.CheCtlContext.getFlags();
        return [
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if kubectl is installed', 'kubectl not found', () => (0, utls_1.isCommandExists)('kubectl')),
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if microk8s is installed', 'MicroK8s not found', () => (0, utls_1.isCommandExists)('microk8s.status')),
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if microk8s is running', 'MicroK8s is not running.', () => isMicroK8sRunning()),
            {
                title: 'Verify if microk8s ingress addon is enabled',
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const enabledAddons = yield getEnabledAddons();
                    if (!enabledAddons.ingress) {
                        yield enableIngressAddon();
                    }
                    task.title = `${task.title}...[Enabled]`;
                }),
            },
            {
                title: 'Verify if microk8s storage addon is enabled',
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const enabledAddons = yield getEnabledAddons();
                    if (!enabledAddons.storage) {
                        yield enableStorageAddon();
                    }
                    task.title = `${task.title}...[Enabled]`;
                }),
            },
            {
                title: 'Retrieving microk8s IP and domain for ingress URLs',
                enabled: () => !flags[flags_1.DOMAIN_FLAG],
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const ip = yield getMicroK8sIP();
                    flags[flags_1.DOMAIN_FLAG] = ip + '.nip.io';
                    task.title = `${task.title}...[${flags[flags_1.DOMAIN_FLAG]}]`;
                }),
            },
        ];
    }
    MicroK8sTasks.getPeflightCheckTasks = getPeflightCheckTasks;
    function isMicroK8sRunning() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { exitCode } = yield execa('microk8s.status', { timeout: 10000, reject: false });
            return exitCode === 0;
        });
    }
    function getEnabledAddons() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { stdout } = yield execa('microk8s.status', ['--format', 'short'], { timeout: 10000 });
            return {
                ingress: stdout.includes('ingress: enabled'),
                storage: stdout.includes('storage: enabled'),
            };
        });
    }
    function enableIngressAddon() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            yield execa('microk8s.enable', ['ingress'], { timeout: 10000 });
        });
    }
    function enableStorageAddon() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            yield execa('microk8s.enable', ['storage'], { timeout: 10000 });
        });
    }
    function getMicroK8sIP() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { stdout } = yield execa('microk8s.config', { timeout: 10000 });
            const regMatch = /server:\s*https?:\/\/([\d.]+)/.exec(stdout);
            return regMatch ? regMatch[1] : '';
        });
    }
})(MicroK8sTasks || (exports.MicroK8sTasks = MicroK8sTasks = {}));
//# sourceMappingURL=microk8s.js.map