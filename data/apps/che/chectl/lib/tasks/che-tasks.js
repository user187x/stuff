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
exports.CheTasks = void 0;
const tslib_1 = require("tslib");
const kube_client_1 = require("../api/kube-client");
const pod_tasks_1 = require("./pod-tasks");
const context_1 = require("../context");
const flags_1 = require("../flags");
const eclipse_che_1 = require("./installers/eclipse-che/eclipse-che");
const path = require("node:path");
const os = require("node:os");
const fs = require("fs-extra");
const che_1 = require("../utils/che");
const utls_1 = require("../utils/utls");
const core_1 = require("@oclif/core");
var CheTasks;
(function (CheTasks) {
    function getWaitCheDeployedTasks() {
        return {
            title: `Wait for ${eclipse_che_1.EclipseChe.PRODUCT_NAME} ready`,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                var _a, _b, _c;
                const flags = context_1.CheCtlContext.getFlags();
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const tasks = (0, utls_1.newListr)([]);
                const cheCluster = yield kubeHelper.getCheCluster(flags[flags_1.CHE_NAMESPACE_FLAG]);
                if (cheCluster) {
                    if (!((_c = (_b = (_a = cheCluster.spec) === null || _a === void 0 ? void 0 : _a.components) === null || _b === void 0 ? void 0 : _b.pluginRegistry) === null || _c === void 0 ? void 0 : _c.disableInternalRegistry)) {
                        tasks.add(pod_tasks_1.PodTasks.getPodStartTasks(eclipse_che_1.EclipseChe.PLUGIN_REGISTRY, eclipse_che_1.EclipseChe.PLUGIN_REGISTRY_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                    }
                    tasks.add(pod_tasks_1.PodTasks.getPodStartTasks(eclipse_che_1.EclipseChe.DASHBOARD, eclipse_che_1.EclipseChe.DASHBOARD_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                    tasks.add(pod_tasks_1.PodTasks.getPodStartTasks(eclipse_che_1.EclipseChe.GATEWAY, eclipse_che_1.EclipseChe.GATEWAY_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                    tasks.add(pod_tasks_1.PodTasks.getPodStartTasks(eclipse_che_1.EclipseChe.CHE_SERVER, eclipse_che_1.EclipseChe.CHE_SERVER_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                    tasks.add(getWaitEclipseCheActiveTask());
                }
                return tasks;
            }),
        };
    }
    CheTasks.getWaitCheDeployedTasks = getWaitCheDeployedTasks;
    function getWaitPodsDeletedTasks() {
        return {
            title: 'Wait all pods deleted',
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                var _a, _b, _c;
                const flags = context_1.CheCtlContext.getFlags();
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const cheCluster = yield kubeHelper.getCheCluster(flags[flags_1.CHE_NAMESPACE_FLAG]);
                const tasks = (0, utls_1.newListr)();
                tasks.add(pod_tasks_1.PodTasks.getPodDeletedTask(eclipse_che_1.EclipseChe.GATEWAY, eclipse_che_1.EclipseChe.GATEWAY_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                tasks.add(pod_tasks_1.PodTasks.getPodDeletedTask(eclipse_che_1.EclipseChe.DASHBOARD, eclipse_che_1.EclipseChe.DASHBOARD_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                tasks.add(pod_tasks_1.PodTasks.getPodDeletedTask(eclipse_che_1.EclipseChe.CHE_SERVER, eclipse_che_1.EclipseChe.CHE_SERVER_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                if (!((_c = (_b = (_a = cheCluster === null || cheCluster === void 0 ? void 0 : cheCluster.spec) === null || _a === void 0 ? void 0 : _a.components) === null || _b === void 0 ? void 0 : _b.pluginRegistry) === null || _c === void 0 ? void 0 : _c.disableInternalRegistry)) {
                    tasks.add(pod_tasks_1.PodTasks.getPodDeletedTask(eclipse_che_1.EclipseChe.PLUGIN_REGISTRY, eclipse_che_1.EclipseChe.PLUGIN_REGISTRY_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                }
                return tasks;
            }),
        };
    }
    CheTasks.getWaitPodsDeletedTasks = getWaitPodsDeletedTasks;
    function getScaleCheDownTasks() {
        return {
            title: `Scale ${eclipse_che_1.EclipseChe.PRODUCT_NAME} down`,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                var _a, _b, _c;
                const flags = context_1.CheCtlContext.getFlags();
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const cheCluster = yield kubeHelper.getCheCluster(flags[flags_1.CHE_NAMESPACE_FLAG]);
                const tasks = (0, utls_1.newListr)();
                tasks.add(pod_tasks_1.PodTasks.getScaleDeploymentTask(eclipse_che_1.EclipseChe.GATEWAY, eclipse_che_1.EclipseChe.GATEWAY_DEPLOYMENT_NAME, 0, flags[flags_1.CHE_NAMESPACE_FLAG]));
                tasks.add(pod_tasks_1.PodTasks.getScaleDeploymentTask(eclipse_che_1.EclipseChe.DASHBOARD, eclipse_che_1.EclipseChe.DASHBOARD_DEPLOYMENT_NAME, 0, flags[flags_1.CHE_NAMESPACE_FLAG]));
                tasks.add(pod_tasks_1.PodTasks.getScaleDeploymentTask(eclipse_che_1.EclipseChe.CHE_SERVER, eclipse_che_1.EclipseChe.CHE_SERVER_DEPLOYMENT_NAME, 0, flags[flags_1.CHE_NAMESPACE_FLAG]));
                if (!((_c = (_b = (_a = cheCluster === null || cheCluster === void 0 ? void 0 : cheCluster.spec) === null || _a === void 0 ? void 0 : _a.components) === null || _b === void 0 ? void 0 : _b.pluginRegistry) === null || _c === void 0 ? void 0 : _c.disableInternalRegistry)) {
                    tasks.add(pod_tasks_1.PodTasks.getScaleDeploymentTask(eclipse_che_1.EclipseChe.PLUGIN_REGISTRY, eclipse_che_1.EclipseChe.PLUGIN_REGISTRY_DEPLOYMENT_NAME, 0, flags[flags_1.CHE_NAMESPACE_FLAG]));
                }
                return tasks;
            }),
        };
    }
    CheTasks.getScaleCheDownTasks = getScaleCheDownTasks;
    function getScaleCheUpTasks() {
        return {
            title: `Scale ${eclipse_che_1.EclipseChe.PRODUCT_NAME} up`,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                var _a, _b, _c;
                const flags = context_1.CheCtlContext.getFlags();
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const cheCluster = yield kubeHelper.getCheCluster(flags[flags_1.CHE_NAMESPACE_FLAG]);
                const tasks = (0, utls_1.newListr)();
                if (cheCluster) {
                    if (!((_c = (_b = (_a = cheCluster.spec) === null || _a === void 0 ? void 0 : _a.components) === null || _b === void 0 ? void 0 : _b.pluginRegistry) === null || _c === void 0 ? void 0 : _c.disableInternalRegistry)) {
                        tasks.add(pod_tasks_1.PodTasks.getScaleDeploymentTask(eclipse_che_1.EclipseChe.PLUGIN_REGISTRY, eclipse_che_1.EclipseChe.PLUGIN_REGISTRY_DEPLOYMENT_NAME, 1, flags[flags_1.CHE_NAMESPACE_FLAG]));
                        tasks.add(pod_tasks_1.PodTasks.getPodStartTasks(eclipse_che_1.EclipseChe.PLUGIN_REGISTRY, eclipse_che_1.EclipseChe.PLUGIN_REGISTRY_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                    }
                    tasks.add(pod_tasks_1.PodTasks.getScaleDeploymentTask(eclipse_che_1.EclipseChe.DASHBOARD, eclipse_che_1.EclipseChe.DASHBOARD_DEPLOYMENT_NAME, 1, flags[flags_1.CHE_NAMESPACE_FLAG]));
                    tasks.add(pod_tasks_1.PodTasks.getPodStartTasks(eclipse_che_1.EclipseChe.DASHBOARD, eclipse_che_1.EclipseChe.DASHBOARD_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                    tasks.add(pod_tasks_1.PodTasks.getScaleDeploymentTask(eclipse_che_1.EclipseChe.GATEWAY, eclipse_che_1.EclipseChe.GATEWAY_DEPLOYMENT_NAME, 1, flags[flags_1.CHE_NAMESPACE_FLAG]));
                    tasks.add(pod_tasks_1.PodTasks.getPodStartTasks(eclipse_che_1.EclipseChe.GATEWAY, eclipse_che_1.EclipseChe.GATEWAY_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                    tasks.add(pod_tasks_1.PodTasks.getScaleDeploymentTask(eclipse_che_1.EclipseChe.CHE_SERVER, eclipse_che_1.EclipseChe.CHE_SERVER_DEPLOYMENT_NAME, 1, flags[flags_1.CHE_NAMESPACE_FLAG]));
                    tasks.add(pod_tasks_1.PodTasks.getPodStartTasks(eclipse_che_1.EclipseChe.CHE_SERVER, eclipse_che_1.EclipseChe.CHE_SERVER_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                }
                return tasks;
            }),
        };
    }
    CheTasks.getScaleCheUpTasks = getScaleCheUpTasks;
    function getDebugTasks() {
        return {
            title: `Debug ${eclipse_che_1.EclipseChe.CHE_SERVER}`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const flags = context_1.CheCtlContext.getFlags();
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const cheDebugServer = yield kubeHelper.getConfigMapValue(eclipse_che_1.EclipseChe.CONFIG_MAP, flags[flags_1.CHE_NAMESPACE_FLAG], 'CHE_DEBUG_SERVER');
                if (cheDebugServer !== 'true') {
                    throw new Error(`Debug is disabled. Use --${flags_1.DEBUG_FLAG} with server:deploy command to deploy ${eclipse_che_1.EclipseChe.CHE_SERVER} with debug mode enabled.`);
                }
                const chePods = yield kubeHelper.listNamespacedPod(flags[flags_1.CHE_NAMESPACE_FLAG], undefined, eclipse_che_1.EclipseChe.CHE_SERVER_SELECTOR);
                if (chePods.items.length === 0) {
                    throw new Error(`${eclipse_che_1.EclipseChe.CHE_SERVER} pod not found`);
                }
                const cheServerPodName = chePods.items[0].metadata.name;
                yield kubeHelper.portForward(cheServerPodName, flags[flags_1.CHE_NAMESPACE_FLAG], flags[flags_1.DEBUG_PORT_FLAG]);
                task.title = `${task.title}...[Enabled]`;
            }),
        };
    }
    CheTasks.getDebugTasks = getDebugTasks;
    function getServerLogsTasks(follow) {
        return {
            title: `${follow ? 'Start following' : 'Read'} ${eclipse_che_1.EclipseChe.PRODUCT_NAME} installation logs`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const flags = context_1.CheCtlContext.getFlags();
                yield che_1.Che.readPodLog(ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE], eclipse_che_1.EclipseChe.CHE_OPERATOR_SELECTOR, ctx[context_1.CliContext.CLI_COMMAND_LOGS_DIR], follow);
                yield che_1.Che.readPodLog(flags[flags_1.CHE_NAMESPACE_FLAG], eclipse_che_1.EclipseChe.CHE_SERVER_SELECTOR, ctx[context_1.CliContext.CLI_COMMAND_LOGS_DIR], follow);
                yield che_1.Che.readPodLog(flags[flags_1.CHE_NAMESPACE_FLAG], eclipse_che_1.EclipseChe.PLUGIN_REGISTRY_SELECTOR, ctx[context_1.CliContext.CLI_COMMAND_LOGS_DIR], follow);
                yield che_1.Che.readPodLog(flags[flags_1.CHE_NAMESPACE_FLAG], eclipse_che_1.EclipseChe.DASHBOARD_SELECTOR, ctx[context_1.CliContext.CLI_COMMAND_LOGS_DIR], follow);
                yield che_1.Che.readPodLog(flags[flags_1.CHE_NAMESPACE_FLAG], eclipse_che_1.EclipseChe.GATEWAY_SELECTOR, ctx[context_1.CliContext.CLI_COMMAND_LOGS_DIR], follow);
                yield che_1.Che.readNamespaceEvents(flags[flags_1.CHE_NAMESPACE_FLAG], ctx[context_1.CliContext.CLI_COMMAND_LOGS_DIR], follow);
                task.title = `${task.title}...[OK]`;
            }),
        };
    }
    CheTasks.getServerLogsTasks = getServerLogsTasks;
    function getRetrieveSelfSignedCertificateTask() {
        return {
            title: `Retrieving ${eclipse_che_1.EclipseChe.PRODUCT_NAME} self-signed CA certificate`,
            // It makes sense to retrieve CA certificate only if self-signed certificate is used.
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const flags = context_1.CheCtlContext.getFlags();
                const cheCaCert = yield che_1.Che.readCheCaCert(flags[flags_1.CHE_NAMESPACE_FLAG]);
                if (cheCaCert) {
                    const caCertFilePath = path.join(os.tmpdir(), eclipse_che_1.EclipseChe.DEFAULT_CA_CERT_FILE_NAME);
                    fs.writeFileSync(caCertFilePath, cheCaCert);
                    task.title = `${task.title}...[OK: ${caCertFilePath}]`;
                }
                else {
                    task.title = `${task.title}...[commonly trusted certificate is used]`;
                }
            }),
        };
    }
    CheTasks.getRetrieveSelfSignedCertificateTask = getRetrieveSelfSignedCertificateTask;
    function getWaitEclipseCheActiveTask() {
        return {
            title: `Wait ${eclipse_che_1.EclipseChe.PRODUCT_NAME} active`,
            task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                var _a, _b;
                const flags = context_1.CheCtlContext.getFlags();
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                for (let i = 0; i < 300; i++) {
                    const cheCluster = yield kubeHelper.getCheCluster(flags[flags_1.CHE_NAMESPACE_FLAG]);
                    if (((_a = cheCluster === null || cheCluster === void 0 ? void 0 : cheCluster.status) === null || _a === void 0 ? void 0 : _a.chePhase) !== 'Active' || !((_b = cheCluster === null || cheCluster === void 0 ? void 0 : cheCluster.status) === null || _b === void 0 ? void 0 : _b.cheVersion)) {
                        yield (0, utls_1.sleep)(1000);
                    }
                    else {
                        task.title = `${task.title}...[OK]`;
                        return;
                    }
                }
                core_1.ux.error(`${eclipse_che_1.EclipseChe.PRODUCT_NAME} is not Active.`, { exit: 1 });
            }),
        };
    }
    CheTasks.getWaitEclipseCheActiveTask = getWaitEclipseCheActiveTask;
})(CheTasks || (exports.CheTasks = CheTasks = {}));
//# sourceMappingURL=che-tasks.js.map