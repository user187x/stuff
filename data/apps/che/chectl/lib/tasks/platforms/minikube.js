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
exports.MinikubeTasks = void 0;
const tslib_1 = require("tslib");
const execa = require("execa");
const context_1 = require("../../context");
const kube_client_1 = require("../../api/kube-client");
const utls_1 = require("../../utils/utls");
const flags_1 = require("../../flags");
const common_tasks_1 = require("../common-tasks");
var MinikubeTasks;
(function (MinikubeTasks) {
    /**
     * Returns tasks list which perform preflight platform checks.
     */
    function getPreflightCheckTasks() {
        const flags = context_1.CheCtlContext.getFlags();
        return [
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if kubectl is installed', 'kubectl not found', () => (0, utls_1.isCommandExists)('kubectl')),
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if minikube is installed', 'minikube not found', () => (0, utls_1.isCommandExists)('minikube')),
            {
                title: 'Verify if minikube is running',
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const isRunning = yield isMinikubeRunning();
                    if (!isRunning) {
                        yield startMinikube();
                    }
                    task.title = `${task.title}...[OK]`;
                }),
            },
            {
                title: 'Enable minikube ingress addon',
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const enabled = yield isIngressAddonEnabled();
                    if (!enabled) {
                        yield enableIngressAddon();
                        const kubeClient = kube_client_1.KubeClient.getInstance();
                        yield kubeClient.waitForPodReady('app.kubernetes.io/instance=ingress-nginx,app.kubernetes.io/component=controller', 'ingress-nginx');
                    }
                    task.title = `${task.title}...[Enabled]`;
                }),
            },
            {
                title: 'Retrieving minikube IP and domain for ingress URLs',
                enabled: () => !flags[flags_1.DOMAIN_FLAG],
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const ip = yield getMinikubeIP();
                    flags[flags_1.DOMAIN_FLAG] = ip + '.nip.io';
                    task.title = `${task.title}...[${flags[flags_1.DOMAIN_FLAG]}]`;
                }),
            },
            {
                title: 'Checking minikube version',
                task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const version = yield getMinikubeVersion();
                    const versionComponents = version.split('.');
                    ctx.minikubeVersionMajor = Number.parseInt(versionComponents[0], 10);
                    ctx.minikubeVersionMinor = Number.parseInt(versionComponents[1], 10);
                    ctx.minikubeVersionPatch = Number.parseInt(versionComponents[2], 10);
                    task.title = `${task.title}...[${version}]`;
                }),
            },
        ];
    }
    MinikubeTasks.getPreflightCheckTasks = getPreflightCheckTasks;
    function configureApiServerForDex() {
        return [
            {
                title: 'Create /etc/ca-certificates directory',
                enabled: (ctx) => Boolean(ctx[context_1.OIDCContext.CA_FILE]),
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const args = [];
                    args.push('ssh', 'sudo mkdir -p /etc/ca-certificates');
                    const { stderr, exitCode } = yield execa('minikube', args, { timeout: 60000, reject: false });
                    // Check the error code and driver and if it is 14(MK_USAGE) and `none`, ignore the exception
                    // if not MK_USAGE(errorCode: 14) when throw Error.
                    // if MK_USAGE and not include `'none' driver` when throw Error.
                    const EXIT_CODE_MK_USAGE = 14;
                    if (exitCode && (exitCode !== EXIT_CODE_MK_USAGE || !stderr.includes('\'none\' driver'))) {
                        throw new Error(`Failed to create /etc/ca-certificates directory: ${stderr}`);
                    }
                    task.title = `${task.title}...[Created]`;
                }),
            },
            {
                title: 'Copy Dex certificate into Minikube',
                enabled: (ctx) => Boolean(ctx[context_1.OIDCContext.CA_FILE]),
                task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const args = [];
                    args.push('cp', ctx[context_1.OIDCContext.CA_FILE], '/etc/ca-certificates/dex-ca.crt');
                    yield execa('minikube', args, { timeout: 60000 });
                    task.title = `${task.title}...[OK]`;
                }),
            },
            {
                title: 'Configure Minikube API server',
                task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const args = [];
                    args.push(`--extra-config=apiserver.oidc-issuer-url=${ctx[context_1.OIDCContext.ISSUER_URL]}`, `--extra-config=apiserver.oidc-client-id=${ctx[context_1.OIDCContext.CLIENT_ID]}`);
                    if (ctx[context_1.OIDCContext.CA_FILE]) {
                        args.push('--extra-config=apiserver.oidc-ca-file=/etc/ca-certificates/dex-ca.crt');
                    }
                    args.push('--extra-config=apiserver.oidc-username-claim=name', '--extra-config=apiserver.oidc-username-prefix=-', '--extra-config=apiserver.oidc-groups-claim=groups', 'start');
                    yield execa('minikube', args, { timeout: 180000 });
                    task.title = `${task.title}...[OK]`;
                }),
            },
            {
                title: 'Wait for Minikube API server',
                task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    yield (0, utls_1.sleep)(30 * 1000);
                    const kubeClient = kube_client_1.KubeClient.getInstance();
                    yield kubeClient.waitForPodReady('component=kube-apiserver', 'kube-system');
                    task.title = `${task.title}...[OK]`;
                }),
            },
        ];
    }
    MinikubeTasks.configureApiServerForDex = configureApiServerForDex;
    function isMinikubeRunning() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { exitCode } = yield execa('minikube', ['status'], { timeout: 10000, reject: false });
            return exitCode === 0;
        });
    }
    function startMinikube() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            yield execa('minikube', ['start', '--memory=4096', '--cpus=4', '--disk-size=50g'], { timeout: 180000 });
        });
    }
    function isIngressAddonEnabled() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            // try with json output (recent minikube version)
            const { stdout, exitCode } = yield execa('minikube', ['addons', 'list', '-o', 'json'], { timeout: 10000, reject: false });
            if (exitCode === 0) {
                // grab json
                const json = JSON.parse(stdout);
                return json.ingress && json.ingress.Status === 'enabled';
            }
            else {
                // probably with old minikube, let's try with classic output
                const { stdout } = yield execa('minikube', ['addons', 'list'], { timeout: 10000 });
                return stdout.includes('ingress: enabled');
            }
        });
    }
    function enableIngressAddon() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            yield execa('minikube', ['addons', 'enable', 'ingress'], { timeout: 60000 });
        });
    }
    function getMinikubeIP() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { stdout } = yield execa('minikube', ['ip'], { timeout: 10000 });
            return stdout;
        });
    }
    function getMinikubeVersion() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { stdout } = yield execa('minikube', ['version'], { timeout: 10000 });
            const versionLine = stdout.split('\n')[0];
            const versionString = versionLine.trim().split(' ')[2].slice(1);
            return versionString;
        });
    }
})(MinikubeTasks || (exports.MinikubeTasks = MinikubeTasks = {}));
//# sourceMappingURL=minikube.js.map