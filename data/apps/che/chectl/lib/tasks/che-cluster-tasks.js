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
exports.CheClusterTasks = void 0;
const tslib_1 = require("tslib");
const kube_client_1 = require("../api/kube-client");
const lodash_1 = require("lodash");
const context_1 = require("../context");
const flags_1 = require("../flags");
const core_1 = require("@oclif/core");
const eclipse_che_1 = require("./installers/eclipse-che/eclipse-che");
var CheClusterTasks;
(function (CheClusterTasks) {
    function getPatchEclipseCheCluster() {
        return {
            title: 'Patch CheCluster Custom Resource',
            enabled: (ctx) => !(0, lodash_1.isEmpty)(ctx[context_1.EclipseCheContext.CR_PATCH]),
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const flags = context_1.CheCtlContext.getFlags();
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const cheCluster = yield kubeHelper.getCheCluster(flags[flags_1.CHE_NAMESPACE_FLAG]);
                if (!cheCluster) {
                    core_1.ux.error(`${eclipse_che_1.EclipseChe.PRODUCT_NAME} cluster Custom Object not found in the namespace '${flags[flags_1.CHE_NAMESPACE_FLAG]}'`, { exit: 1 });
                }
                yield kubeHelper.patchNamespacedCustomObject(cheCluster.metadata.name, flags[flags_1.CHE_NAMESPACE_FLAG], ctx[context_1.EclipseCheContext.CR_PATCH], eclipse_che_1.EclipseChe.CHE_CLUSTER_API_GROUP, eclipse_che_1.EclipseChe.CHE_CLUSTER_API_VERSION_V2, eclipse_che_1.EclipseChe.CHE_CLUSTER_KIND_PLURAL);
                task.title = `${task.title}...[Patched]`;
            }),
        };
    }
    CheClusterTasks.getPatchEclipseCheCluster = getPatchEclipseCheCluster;
    function getCreateEclipseCheClusterTask() {
        return {
            title: 'Create CheCluster Custom Resource',
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                var _a, _b;
                const flags = context_1.CheCtlContext.getFlags();
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                let cheCluster = yield kubeHelper.getCheCluster(flags[flags_1.CHE_NAMESPACE_FLAG]);
                if (cheCluster) {
                    task.title = `${task.title}...[Exists]`;
                    return;
                }
                cheCluster = (ctx[context_1.EclipseCheContext.CUSTOM_CR] || ctx[context_1.EclipseCheContext.DEFAULT_CR]);
                // merge flags
                (0, lodash_1.merge)(cheCluster, { spec: { components: { cheServer: { debug: flags[flags_1.DEBUG_FLAG] } } } });
                if (flags[flags_1.CHE_IMAGE_FLAG]) {
                    (0, lodash_1.merge)(cheCluster, { spec: { components: { cheServer: { deployment: { containers: [{ image: flags[flags_1.CHE_IMAGE_FLAG] }] } } } } });
                }
                if (!ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
                    if (((_b = (_a = cheCluster.spec) === null || _a === void 0 ? void 0 : _a.networking) === null || _b === void 0 ? void 0 : _b.tlsSecretName) === undefined) {
                        (0, lodash_1.merge)(cheCluster, { spec: { networking: { tlsSecretName: eclipse_che_1.EclipseChe.CHE_TLS_SECRET_NAME } } });
                    }
                    if (flags[flags_1.DOMAIN_FLAG]) {
                        (0, lodash_1.merge)(cheCluster, { spec: { networking: { domain: flags[flags_1.DOMAIN_FLAG] } } });
                    }
                }
                if (flags[flags_1.WORKSPACE_PVS_STORAGE_CLASS_NAME_FLAG]) {
                    (0, lodash_1.merge)(cheCluster, { spec: { workspaces: { storage: { pvc: { storageClass: flags[flags_1.WORKSPACE_PVS_STORAGE_CLASS_NAME_FLAG] } } } } });
                }
                if (flags[flags_1.PLUGIN_REGISTRY_URL_FLAG]) {
                    (0, lodash_1.merge)(cheCluster, { spec: { components: { pluginRegistry: { disableInternalRegistry: true, externalPluginRegistries: [{ url: flags[flags_1.PLUGIN_REGISTRY_URL_FLAG] }] } } } });
                }
                if (flags[flags_1.DEVFILE_REGISTRY_URL_FLAG]) {
                    (0, lodash_1.merge)(cheCluster, { spec: { components: { devfileRegistry: { disableInternalRegistry: true, externalDevfileRegistries: [{ url: flags[flags_1.DEVFILE_REGISTRY_URL_FLAG] }] } } } });
                }
                if (flags[flags_1.PLATFORM_FLAG] === 'minikube' || flags[flags_1.PLATFORM_FLAG] === 'microk8s' || flags[flags_1.PLATFORM_FLAG] === 'docker-desktop') {
                    (0, lodash_1.merge)(cheCluster, { spec: { devEnvironments: { startTimeoutSeconds: 3000 } } });
                }
                yield kubeHelper.createNamespacedCustomObject(flags[flags_1.CHE_NAMESPACE_FLAG], eclipse_che_1.EclipseChe.CHE_CLUSTER_API_GROUP, eclipse_che_1.EclipseChe.CHE_CLUSTER_API_VERSION_V2, eclipse_che_1.EclipseChe.CHE_CLUSTER_KIND_PLURAL, cheCluster, true);
                if (ctx[context_1.EclipseCheContext.CR_PATCH]) {
                    // merge(cheCluster, ctx[EclipseCheContext.CR_PATCH])
                    yield kubeHelper.patchNamespacedCustomObject(cheCluster.metadata.name, flags[flags_1.CHE_NAMESPACE_FLAG], ctx[context_1.EclipseCheContext.CR_PATCH], eclipse_che_1.EclipseChe.CHE_CLUSTER_API_GROUP, eclipse_che_1.EclipseChe.CHE_CLUSTER_API_VERSION_V2, eclipse_che_1.EclipseChe.CHE_CLUSTER_KIND_PLURAL);
                }
                task.title = `${task.title}...[Created]`;
            }),
        };
    }
    CheClusterTasks.getCreateEclipseCheClusterTask = getCreateEclipseCheClusterTask;
})(CheClusterTasks || (exports.CheClusterTasks = CheClusterTasks = {}));
//# sourceMappingURL=che-cluster-tasks.js.map