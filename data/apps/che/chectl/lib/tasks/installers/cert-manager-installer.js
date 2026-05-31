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
exports.CertManagerInstaller = exports.CertManager = void 0;
const tslib_1 = require("tslib");
const kube_client_1 = require("../../api/kube-client");
const context_1 = require("../../context");
const flags_1 = require("../../flags");
const common_tasks_1 = require("../common-tasks");
const utls_1 = require("../../utils/utls");
var CertManager;
(function (CertManager) {
    CertManager.NAMESPACE = 'cert-manager';
    CertManager.VERSION = 'v1.8.2';
    function getApplyResourcesTask() {
        return {
            title: 'Apply resources',
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const certManagerCrd = yield kubeHelper.getCustomResourceDefinition('certificates.cert-manager.io');
                if (certManagerCrd) {
                    task.title = `${task.title}...[Exists]`;
                }
                else {
                    yield kubeHelper.applyResource(`https://github.com/cert-manager/cert-manager/releases/download/${CertManager.VERSION}/cert-manager.yaml`);
                    task.title = `${task.title}...[Created]`;
                }
            }),
        };
    }
    CertManager.getApplyResourcesTask = getApplyResourcesTask;
    function getWaitCertManagerTask() {
        return {
            title: 'Wait for Cert Manager pods ready',
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                yield kubeHelper.waitForPodReady('app.kubernetes.io/name=cert-manager', CertManager.NAMESPACE);
                yield kubeHelper.waitForPodReady('app.kubernetes.io/name=webhook', CertManager.NAMESPACE);
                yield kubeHelper.waitForPodReady('app.kubernetes.io/name=cainjector', CertManager.NAMESPACE);
                task.title = `${task.title}...[OK]`;
            }),
        };
    }
    CertManager.getWaitCertManagerTask = getWaitCertManagerTask;
})(CertManager || (exports.CertManager = CertManager = {}));
class CertManagerInstaller {
    constructor() {
        const flags = context_1.CheCtlContext.getFlags();
        this.skip = flags[flags_1.SKIP_CERT_MANAGER_FLAG];
    }
    getDeployTasks() {
        return {
            title: `Install Cert Manager ${CertManager.VERSION}`,
            skip: () => this.skip,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)();
                tasks.add(CertManager.getApplyResourcesTask());
                tasks.add(CertManager.getWaitCertManagerTask());
                return tasks;
            }),
        };
    }
    getPreUpdateTasks() {
        return common_tasks_1.CommonTasks.getDisabledTask();
    }
    getUpdateTasks() {
        return common_tasks_1.CommonTasks.getDisabledTask();
    }
    getDeleteTasks() {
        return common_tasks_1.CommonTasks.getDisabledTask();
    }
}
exports.CertManagerInstaller = CertManagerInstaller;
//# sourceMappingURL=cert-manager-installer.js.map