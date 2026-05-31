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
exports.CheLogsReader = void 0;
const tslib_1 = require("tslib");
const client_node_1 = require("@kubernetes/client-node");
const fs = require("fs-extra");
const path = require("node:path");
const kube_client_1 = require("./kube-client");
const core_1 = require("@oclif/core");
class CheLogsReader {
    constructor() {
        this.kubeHelper = kube_client_1.KubeClient.getInstance();
    }
    /**
     * Reads logs from pods that match a given selector.
     */
    readPodLog(namespace, podLabelSelector, directory, follow) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            yield (follow ? this.watchNamespacedPods(namespace, podLabelSelector, directory) : this.readNamespacedPodLog(namespace, podLabelSelector, directory));
        });
    }
    /**
     * Reads containers logs inside pod that match a given selector.
     */
    readNamespacedPodLog(namespace, podLabelSelector, directory) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const pods = yield this.kubeHelper.listNamespacedPod(namespace, undefined, podLabelSelector);
            for (const pod of pods.items) {
                if (!pod.status || !pod.status.containerStatuses) {
                    return;
                }
                const podName = pod.metadata.name;
                for (const containerName of this.getContainers(pod)) {
                    const fileName = this.doCreateLogFile(namespace, podName, containerName, directory);
                    yield this.doReadNamespacedPodLog(namespace, podName, containerName, fileName, false);
                }
            }
        });
    }
    /**
     * Reads all namespace events and store into a file.
     */
    readNamespaceEvents(namespace, directory, follow) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const fileName = path.resolve(directory, namespace, 'events.txt');
            fs.ensureFileSync(fileName);
            const outStream = fs.createWriteStream(fileName, { flags: 'a' });
            const eventList = yield this.kubeHelper.listNamespacedEvent(namespace);
            for (const event of eventList.items) {
                outStream.write(this.formatEvent(event), error => {
                    if (error) {
                        core_1.ux.warn(error);
                    }
                });
            }
            if (follow) {
                yield this.kubeHelper.watchNamespacedEvents(namespace, event => {
                    outStream.write(this.formatEvent(event));
                }, error => {
                    if (error) {
                        core_1.ux.warn(error);
                    }
                });
            }
        });
    }
    formatEvent(event) {
        var _a, _b;
        const lastTimestamp = event.lastTimestamp ? new Date(event.lastTimestamp).toISOString() : '<unknown>';
        const type = event.type || '';
        const reason = event.reason || '';
        const objectKind = ((_a = event.involvedObject) === null || _a === void 0 ? void 0 : _a.kind) || '';
        const objectName = ((_b = event.involvedObject) === null || _b === void 0 ? void 0 : _b.name) || '';
        const message = event.message || '';
        return `${lastTimestamp}\t${type}\t${reason}\t${objectKind}/${objectName}\t${message}\n`;
    }
    watchNamespacedPods(namespace, podLabelSelector, directory) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const processedContainers = new Map();
            const watcher = new client_node_1.Watch(this.kubeHelper.getKubeConfig());
            return watcher.watch(`/api/v1/namespaces/${namespace}/pods`, {}, (_phase, obj) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const pod = obj;
                if (!pod || !pod.metadata || !pod.metadata.name) {
                    return;
                }
                const podName = pod.metadata.name;
                if (!processedContainers.has(podName)) {
                    processedContainers.set(podName, new Set());
                }
                if (!podLabelSelector || this.matchLabels(pod.metadata.labels || {}, podLabelSelector)) {
                    for (const containerName of this.getContainers(pod)) {
                        // not to read logs from the same containers twice
                        if (!processedContainers.get(podName).has(containerName)) {
                            processedContainers.get(podName).add(containerName);
                            const fileName = this.doCreateLogFile(namespace, podName, containerName, directory);
                            yield this.doReadNamespacedPodLog(namespace, pod.metadata.name, containerName, fileName, true);
                        }
                    }
                }
            }), () => {
            });
        });
    }
    /**
     * Indicates if pod matches given labels.
     */
    matchLabels(podLabels, podLabelSelector) {
        const labels = podLabelSelector.split(',');
        for (const label of labels) {
            if (label) {
                const keyValue = label.split('=');
                if (podLabels[keyValue[0]] !== keyValue[1]) {
                    return false;
                }
            }
        }
        return true;
    }
    /**
     * Returns containers names.
     */
    getContainers(pod) {
        if (!pod.status || !pod.status.containerStatuses) {
            return [];
        }
        return pod.status.containerStatuses.filter(s => s.ready).map(s => s.name);
    }
    /**
     * Reads pod log from a specific container of the pod.
     */
    doReadNamespacedPodLog(namespace, podName, containerName, fileName, follow) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            if (follow) {
                try {
                    yield this.kubeHelper.readNamespacedPodLog(podName, namespace, containerName, fileName, follow);
                }
                catch (_a) {
                    // retry in 200ms, container might not be started
                    setTimeout(() => tslib_1.__awaiter(this, void 0, void 0, function* () { return this.doReadNamespacedPodLog(namespace, podName, containerName, fileName, follow); }), 200);
                }
            }
            else {
                yield this.kubeHelper.readNamespacedPodLog(podName, namespace, containerName, fileName, follow);
            }
        });
    }
    doCreateLogFile(namespace, podName, containerName, directory) {
        const fileName = path.resolve(directory, namespace, podName, `${containerName}.log`);
        fs.ensureFileSync(fileName);
        return fileName;
    }
}
exports.CheLogsReader = CheLogsReader;
//# sourceMappingURL=che-logs-reader.js.map