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
exports.DockerDesktopTasks = void 0;
const tslib_1 = require("tslib");
const execa = require("execa");
const os = require("node:os");
const core_1 = require("@oclif/core");
const context_1 = require("../../context");
const flags_1 = require("../../flags");
const kube_client_1 = require("../../api/kube-client");
const common_tasks_1 = require("../common-tasks");
const utls_1 = require("../../utils/utls");
var DockerDesktopTasks;
(function (DockerDesktopTasks) {
    /**
     * Returns tasks list which perform preflight platform checks.
     */
    function getPreflightCheckTasks() {
        return [
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if oc is installed', 'oc not found', () => (0, utls_1.isCommandExists)('oc')),
            {
                title: 'Verify if kubectl context is Docker Desktop',
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const kubeClient = kube_client_1.KubeClient.getInstance();
                    const context = kubeClient.getCurrentContext();
                    if (context !== 'docker-for-desktop' && context !== 'docker-desktop') {
                        core_1.ux.error(`E_PLATFORM_NOT_READY: current kube context is not Docker Desktop context. Found ${context}`, { exit: 1 });
                    }
                    else {
                        task.title = `${task.title}: [Found ${context}]`;
                    }
                }),
            },
            {
                title: 'Verify if nginx ingress is installed',
                task: (ctx) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    ctx.isNginxIngressInstalled = yield isNginxIngressEnabled();
                }),
            },
            {
                title: 'Installing nginx ingress',
                skip: (ctx) => {
                    if (ctx.isNginxIngressInstalled) {
                        return 'Ngninx ingress is already setup.';
                    }
                },
                task: () => enableNginxIngress(),
            },
            {
                title: 'Verify domain is set',
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const flags = context_1.CheCtlContext.getFlags();
                    if (!flags[flags_1.DOMAIN_FLAG]) {
                        const ips = grabIps();
                        if (ips.length === 0) {
                            core_1.ux.error('E_MISSING_DOMAIN: Unable to find IPV4 ip on this computer. Needs to provide --domain flag', { exit: 1 });
                        }
                        else if (ips.length >= 1) {
                            flags[flags_1.DOMAIN_FLAG] = `${ips[0]}.nip.io`;
                        }
                        task.title = `${task.title}...[Auto-assigning domain to ${flags[flags_1.DOMAIN_FLAG]}]`;
                    }
                    task.title = `${task.title}...[OK]`;
                }),
            },
        ];
    }
    DockerDesktopTasks.getPreflightCheckTasks = getPreflightCheckTasks;
    // $ kubectl get services --namespace ingress-nginx
    function isNginxIngressEnabled() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeClient = kube_client_1.KubeClient.getInstance();
            const services = yield kubeClient.getServicesBySelector('', 'ingress-nginx');
            return services.items.length > 0;
        });
    }
    function enableNginxIngress() {
        return tslib_1.__awaiter(this, arguments, void 0, function* (execTimeout = 30000) {
            const version = 'controller-v1.1.0';
            const genericCommand = `kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/${version}/deploy/static/provider/cloud/deploy.yaml`;
            yield execa(genericCommand, { timeout: execTimeout, shell: true });
        });
    }
    function grabIps() {
        var _a;
        const networkInterfaces = os.networkInterfaces();
        const allIps = [];
        for (const interfaceName of Object.keys(networkInterfaces)) {
            (_a = networkInterfaces[interfaceName]) === null || _a === void 0 ? void 0 : _a.forEach(iface => {
                if (iface.family === 'IPv4' && !iface.internal) {
                    allIps.push(iface.address);
                }
            });
        }
        return allIps;
    }
})(DockerDesktopTasks || (exports.DockerDesktopTasks = DockerDesktopTasks = {}));
//# sourceMappingURL=docker-desktop.js.map