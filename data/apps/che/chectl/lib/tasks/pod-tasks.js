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
exports.PodTasks = void 0;
const tslib_1 = require("tslib");
const core_1 = require("@oclif/core");
const kube_client_1 = require("../api/kube-client");
const context_1 = require("../context");
const eclipse_che_1 = require("./installers/eclipse-che/eclipse-che");
const utls_1 = require("../utils/utls");
var PodTasks;
(function (PodTasks) {
    const INTERVAL = 500;
    function getDeploymentExistanceTask(deploymentName, namespace) {
        return {
            title: `Checking if deployment ${deploymentName} exists`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeClient = kube_client_1.KubeClient.getInstance();
                const exists = yield kubeClient.isDeploymentExist(deploymentName, namespace);
                if (!exists) {
                    core_1.ux.error(`Deployment ${deploymentName} not found.`, { exit: 1 });
                }
                task.title = `${task.title}...[Found]`;
            }),
        };
    }
    PodTasks.getDeploymentExistanceTask = getDeploymentExistanceTask;
    function getWaitLatestReplicaTask(deploymentName, namespace) {
        return {
            title: `Wait for ${deploymentName} latest replica`,
            task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeClient = kube_client_1.KubeClient.getInstance();
                yield core_1.ux.wait(1000);
                yield kubeClient.waitLatestReplica(deploymentName, namespace);
                task.title = `${task.title}...[OK]`;
            }),
        };
    }
    PodTasks.getWaitLatestReplicaTask = getWaitLatestReplicaTask;
    function getScaleDeploymentTask(name, deploymentName, replicas, namespace) {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        return {
            title: `Scale ${name} ${replicas > 0 ? 'Up' : 'Down'}`,
            task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                yield kubeHelper.scaleDeployment(deploymentName, namespace, replicas);
                task.title = `${task.title}...[OK]`;
            }),
        };
    }
    PodTasks.getScaleDeploymentTask = getScaleDeploymentTask;
    function getPodDeletedTask(name, selector, namespace) {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        return {
            title: `${name} pod`,
            task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                yield kubeHelper.waitUntilPodIsDeleted(selector, namespace);
                task.title = `${task.title}...[Deleted]`;
            }),
        };
    }
    PodTasks.getPodDeletedTask = getPodDeletedTask;
    function getPodStartTasks(name, selector, namespace) {
        return {
            title: `${name} pod bootstrap`,
            task: (ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)([]);
                tasks.add(getSchedulingTask(selector, namespace));
                tasks.add(getDownloadingTask(selector, namespace));
                if (name === eclipse_che_1.EclipseChe.PLUGIN_REGISTRY) {
                    // if embedded plugin registry is configured, use longer timeout for pod readiness
                    tasks.add(getStartingTask(selector, namespace, ctx[context_1.KubeHelperContext.POD_READY_TIMEOUT_EMBEDDED_PLUGIN_REGISTRY]));
                }
                else {
                    tasks.add(getStartingTask(selector, namespace));
                }
                return tasks;
            }),
        };
        function getSchedulingTask(selector, namespace) {
            return {
                title: 'Scheduling',
                task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const iterations = ctx[context_1.KubeHelperContext.POD_WAIT_TIMEOUT] / INTERVAL;
                    for (let i = 1; i <= iterations; i++) {
                        // check cheCluster status
                        const cheClusterFailState = yield getCheClusterFailState(namespace);
                        // check 'PodScheduled' condition
                        const podFailState = yield getPodFailState(namespace, selector, 'PodScheduled');
                        if (cheClusterFailState || podFailState) {
                            const iterations = ctx[context_1.KubeHelperContext.POD_ERROR_RECHECK_TIMEOUT] / 1000;
                            let cheClusterFailState;
                            let podFailState;
                            for (let j = 0; j < iterations; j++) {
                                yield core_1.ux.wait(1000);
                                cheClusterFailState = yield getCheClusterFailState(namespace);
                                podFailState = yield getPodFailState(namespace, selector, 'PodScheduled');
                                if (!cheClusterFailState && !podFailState) {
                                    break;
                                }
                            }
                            if (cheClusterFailState) {
                                throw new Error(`${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator failed, reason: ${cheClusterFailState.reason}, message: ${cheClusterFailState.message}. Consider increasing error recheck timeout with --k8spoderrorrechecktimeout flag.`);
                            }
                            if (podFailState) {
                                throw new Error(`Failed to schedule a pod, reason: ${podFailState.reason}, message: ${podFailState.message}. Consider increasing error recheck timeout with --k8spoderrorrechecktimeout flag.`);
                            }
                        }
                        const allScheduled = yield isPodConditionStatusPassed(namespace, selector, 'PodScheduled');
                        if (allScheduled) {
                            task.title = `${task.title}...[OK]`;
                            return;
                        }
                        yield core_1.ux.wait(INTERVAL);
                    }
                    throw new Error(`Failed to schedule a pod: ${yield getTimeOutErrorMessage(namespace, selector)}`);
                }),
            };
        }
        function getDownloadingTask(selector, namespace) {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            return {
                title: 'Downloading images',
                task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    const iterations = ctx[context_1.KubeHelperContext.POD_DOWNLOAD_IMAGE_TIMEOUT] / INTERVAL;
                    for (let i = 1; i <= iterations; i++) {
                        const failedState = yield getContainerFailState(namespace, selector, 'Pending');
                        if (failedState) {
                            const iterations = ctx[context_1.KubeHelperContext.POD_ERROR_RECHECK_TIMEOUT] / 1000;
                            let failedState;
                            for (let j = 0; j < iterations; j++) {
                                yield core_1.ux.wait(1000);
                                failedState = yield getContainerFailState(namespace, selector, 'Pending');
                                if (!failedState) {
                                    break;
                                }
                            }
                            if (failedState) {
                                throw new Error(`Failed to download image, reason: ${failedState.reason}, message: ${failedState.message}.`);
                            }
                        }
                        const pods = yield kubeHelper.getPodListByLabel(namespace, selector);
                        const allRunning = !pods.some(value => !value.status || value.status.phase !== 'Running');
                        if (pods.length && allRunning) {
                            task.title = `${task.title}...[OK]`;
                            return;
                        }
                        yield core_1.ux.wait(INTERVAL);
                    }
                    throw new Error(`Failed to download image: ${yield getTimeOutErrorMessage(namespace, selector)}`);
                }),
            };
        }
        function getStartingTask(selector, namespace, podReadyTimeout) {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            return {
                title: 'Starting',
                task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    let iterations = ctx[context_1.KubeHelperContext.POD_READY_TIMEOUT] / INTERVAL;
                    if (podReadyTimeout) {
                        iterations = podReadyTimeout / INTERVAL;
                    }
                    for (let i = 1; i <= iterations; i++) {
                        // check cheCluster status
                        const cheClusterFailState = yield getCheClusterFailState(namespace);
                        const failedState = yield getContainerFailState(namespace, selector, 'Running');
                        if (cheClusterFailState || failedState) {
                            const iterations = ctx[context_1.KubeHelperContext.POD_ERROR_RECHECK_TIMEOUT] / 1000;
                            let cheClusterFailState;
                            let failedState;
                            for (let j = 0; j < iterations; j++) {
                                yield core_1.ux.wait(1000);
                                cheClusterFailState = yield getCheClusterFailState(namespace);
                                failedState = yield getContainerFailState(namespace, selector, 'Running');
                                if (!cheClusterFailState && !failedState) {
                                    break;
                                }
                            }
                            if (cheClusterFailState) {
                                throw new Error(`${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator failed, reason: ${cheClusterFailState.reason}, message: ${cheClusterFailState.message}. Consider increasing error recheck timeout with --k8spoderrorrechecktimeout flag.`);
                            }
                            if (failedState) {
                                throw new Error(`Failed to start a pod, reason: ${failedState.reason}, message: ${failedState.message}`);
                            }
                        }
                        const terminatedState = yield kubeHelper.getPodLastTerminatedState(namespace, selector);
                        if (terminatedState) {
                            let errorMsg = `Failed to start a pod, reason: ${terminatedState.reason}`;
                            terminatedState.message && (errorMsg += `, message: ${terminatedState.message}`);
                            terminatedState.exitCode && (errorMsg += `, exitCode: ${terminatedState.exitCode}`);
                            terminatedState.signal && (errorMsg += `, signal: ${terminatedState.signal}`);
                            throw new Error(errorMsg);
                        }
                        const allStarted = yield isPodConditionStatusPassed(namespace, selector, 'Ready');
                        if (allStarted) {
                            task.title = `${task.title}...[OK]`;
                            return;
                        }
                        yield core_1.ux.wait(INTERVAL);
                    }
                    throw new Error(`Failed to start a pod: ${yield getTimeOutErrorMessage(namespace, selector)}`);
                }),
            };
        }
    }
    PodTasks.getPodStartTasks = getPodStartTasks;
    function getPodFailState(namespace, selector, conditionType) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const status = yield kubeHelper.getPodCondition(namespace, selector, conditionType);
            return status.find(s => s.status === 'False' && s.message && s.reason);
        });
    }
    function isPodConditionStatusPassed(namespace, selector, conditionType) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const status = yield kubeHelper.getPodCondition(namespace, selector, conditionType);
            const allScheduled = !status.some(s => s.status !== 'True');
            return Boolean(status.length) && allScheduled;
        });
    }
    /**
     * Checks if there is any reason for a given pod state and returns message if so.
     */
    function getContainerFailState(namespace, selector, state) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const waitingState = yield kubeHelper.getPodWaitingState(namespace, selector, state);
            if (waitingState && waitingState.reason && waitingState.message) {
                return waitingState;
            }
        });
    }
    /**
     * Returns extended timeout error message explaining a failure.
     */
    function getTimeOutErrorMessage(namespace, selector) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const pods = yield kubeHelper.getPodListByLabel(namespace, selector);
            if (!pods.length) {
                throw new Error(`Timeout: there are no pods in the namespace: ${namespace}, selector: ${selector}. Check ${eclipse_che_1.EclipseChe.PRODUCT_NAME} logs for details. Consider increasing error recheck timeout with --k8spoderrorrechecktimeout flag.`);
            }
            let errorMessage = 'Timeout:';
            for (const pod of pods) {
                errorMessage += `\nPod: ${pod.metadata.name}`;
                if (pod.status) {
                    if (pod.status.containerStatuses) {
                        errorMessage += `\n\t\tstatus: ${JSON.stringify(pod.status.containerStatuses, undefined, '  ')}`;
                    }
                    if (pod.status.conditions) {
                        errorMessage += `\n\t\tconditions: ${JSON.stringify(pod.status.conditions, undefined, '  ')}`;
                    }
                }
                else {
                    errorMessage += ', status not found.';
                }
            }
            return errorMessage;
        });
    }
    function getCheClusterFailState(namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a, _b;
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const cheCluster = yield kubeHelper.getCheCluster(namespace);
            if (((_a = cheCluster === null || cheCluster === void 0 ? void 0 : cheCluster.status) === null || _a === void 0 ? void 0 : _a.reason) && ((_b = cheCluster === null || cheCluster === void 0 ? void 0 : cheCluster.status) === null || _b === void 0 ? void 0 : _b.message)) {
                return cheCluster.status;
            }
        });
    }
})(PodTasks || (exports.PodTasks = PodTasks = {}));
//# sourceMappingURL=pod-tasks.js.map