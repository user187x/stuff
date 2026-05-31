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
exports.DevWorkspacesTasks = void 0;
const tslib_1 = require("tslib");
const kube_client_1 = require("../../../api/kube-client");
const common_tasks_1 = require("../../common-tasks");
const context_1 = require("../../../context");
const dev_workspace_1 = require("./dev-workspace");
const path = require("node:path");
var DevWorkspacesTasks;
(function (DevWorkspacesTasks) {
    function getDeleteWebhooksTask() {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete Webhooks', [
            () => kubeHelper.deleteMutatingWebhookConfiguration(dev_workspace_1.DevWorkspace.WEBHOOK),
            () => kubeHelper.deleteValidatingWebhookConfiguration(dev_workspace_1.DevWorkspace.WEBHOOK),
        ]);
    }
    DevWorkspacesTasks.getDeleteWebhooksTask = getDeleteWebhooksTask;
    function getDeleteCustomResourcesTasks() {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        return [
            common_tasks_1.CommonTasks.getDeleteResourcesTask(`Delete ${dev_workspace_1.DevWorkspace.DEV_WORKSPACES_CRD} resources`, [() => kubeHelper.deleteAllCustomResourcesAndCrd(dev_workspace_1.DevWorkspace.DEV_WORKSPACES_CRD, dev_workspace_1.DevWorkspace.WORKSPACE_API_GROUP, dev_workspace_1.DevWorkspace.WORKSPACE_API_VERSION, dev_workspace_1.DevWorkspace.DEV_WORKSPACES_KIND)]),
            common_tasks_1.CommonTasks.getDeleteResourcesTask(`Delete ${dev_workspace_1.DevWorkspace.DEV_WORKSPACES_TEMPLATES_CRD} resources`, [() => kubeHelper.deleteAllCustomResourcesAndCrd(dev_workspace_1.DevWorkspace.DEV_WORKSPACES_TEMPLATES_CRD, dev_workspace_1.DevWorkspace.WORKSPACE_API_GROUP, dev_workspace_1.DevWorkspace.WORKSPACE_API_VERSION, dev_workspace_1.DevWorkspace.DEV_WORKSPACE_TEMPLATES_KIND)]),
            common_tasks_1.CommonTasks.getDeleteResourcesTask(`Delete ${dev_workspace_1.DevWorkspace.DEV_WORKSPACE_ROUTINGS_CRD} resources`, [() => kubeHelper.deleteAllCustomResourcesAndCrd(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_ROUTINGS_CRD, dev_workspace_1.DevWorkspace.CONTROLLER_API_GROUP, dev_workspace_1.DevWorkspace.CONTROLLER_API_VERSION, dev_workspace_1.DevWorkspace.DEV_WORKSPACE_ROUTINGS_KIND)]),
            common_tasks_1.CommonTasks.getDeleteResourcesTask(`Delete ${dev_workspace_1.DevWorkspace.DEV_WORKSPACE_OPERATOR_CONFIGS_CRD} resources`, [() => kubeHelper.deleteAllCustomResourcesAndCrd(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_OPERATOR_CONFIGS_CRD, dev_workspace_1.DevWorkspace.CONTROLLER_API_GROUP, dev_workspace_1.DevWorkspace.CONTROLLER_API_VERSION, dev_workspace_1.DevWorkspace.DEV_WORKSPACE_OPERATOR_CONFIGS_PLURAL)]),
        ];
    }
    DevWorkspacesTasks.getDeleteCustomResourcesTasks = getDeleteCustomResourcesTasks;
    function getDeleteServicesTask() {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const ctx = context_1.CheCtlContext.get();
        const deleteResources = [];
        if (!ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
            deleteResources.push(() => kubeHelper.deleteService(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_CONTROLLER_SERVICE, ctx[context_1.DevWorkspaceContext.NAMESPACE]), () => kubeHelper.deleteService(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_CONTROLLER_METRICS_SERVICE, ctx[context_1.DevWorkspaceContext.NAMESPACE]));
        }
        deleteResources.push(() => kubeHelper.deleteService(dev_workspace_1.DevWorkspace.WEBHOOK_SERVER_SERVICE, ctx[context_1.DevWorkspaceContext.NAMESPACE]));
        return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete Services', deleteResources);
    }
    DevWorkspacesTasks.getDeleteServicesTask = getDeleteServicesTask;
    function getDeleteWorkloadsTask() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const ctx = context_1.CheCtlContext.get();
            const deleteResources = [];
            if (!ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
                deleteResources.push(() => kubeHelper.deleteDeployment(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_CONTROLLER_DEPLOYMENT, ctx[context_1.DevWorkspaceContext.NAMESPACE]), () => kubeHelper.deleteSecret(dev_workspace_1.DevWorkspace.WEBHOOK_SERVER_CERT, ctx[context_1.DevWorkspaceContext.NAMESPACE]), () => kubeHelper.deleteSecret(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_CONTROLLER_SERVICE_CERT, ctx[context_1.DevWorkspaceContext.NAMESPACE]));
            }
            deleteResources.push(() => kubeHelper.deleteDeployment(dev_workspace_1.DevWorkspace.WEBHOOK_SERVER_DEPLOYMENT, ctx[context_1.DevWorkspaceContext.NAMESPACE]), () => kubeHelper.deleteSecret(dev_workspace_1.DevWorkspace.WEBHOOK_SERVER_TLS, ctx[context_1.DevWorkspaceContext.NAMESPACE]));
            // Delete leader election related resources
            const cms = yield kubeHelper.listConfigMaps(ctx[context_1.DevWorkspaceContext.NAMESPACE]);
            for (const cm of cms) {
                const configMapName = cm.metadata.name;
                if (configMapName.endsWith('devfile.io')) {
                    deleteResources.push(() => kubeHelper.deleteConfigMap(configMapName, ctx[context_1.DevWorkspaceContext.NAMESPACE]), () => kubeHelper.deleteLease(configMapName, ctx[context_1.DevWorkspaceContext.NAMESPACE]));
                }
            }
            return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete Workloads', deleteResources);
        });
    }
    DevWorkspacesTasks.getDeleteWorkloadsTask = getDeleteWorkloadsTask;
    function getDeleteRbacTask() {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const ctx = context_1.CheCtlContext.get();
        return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete RBAC', [
            () => kubeHelper.deleteRole(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_LEADER_ELECTION_ROLE, ctx[context_1.DevWorkspaceContext.NAMESPACE]),
            () => kubeHelper.deleteRoleBinding(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_LEADER_ELECTION_ROLE_BINDING, ctx[context_1.DevWorkspaceContext.NAMESPACE]),
            () => kubeHelper.deleteRoleBinding(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_SERVICE_CERT_ROLE, ctx[context_1.DevWorkspaceContext.NAMESPACE]),
            () => kubeHelper.deleteRoleBinding(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_SERVICE_CERT_ROLE_BINDING, ctx[context_1.DevWorkspaceContext.NAMESPACE]),
            () => kubeHelper.deleteRoleBinding(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_SERVICE_AUTH_READER_ROLE_BINDING, 'kube-system'),
            () => kubeHelper.deleteClusterRoleBinding(dev_workspace_1.DevWorkspace.DEV_WORKSPACES_CLUSTER_ROLE_BINDING),
            () => kubeHelper.deleteClusterRoleBinding(dev_workspace_1.DevWorkspace.DEV_WORKSPACES_PROXY_CLUSTER_ROLE_BINDING),
            () => kubeHelper.deleteClusterRoleBinding(dev_workspace_1.DevWorkspace.DEV_WORKSPACES_WEBHOOK_CLUSTER_ROLE_BINDING),
            () => kubeHelper.deleteClusterRole(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_EDIT_WORKSPACES_CLUSTER_ROLE),
            () => kubeHelper.deleteClusterRole(dev_workspace_1.DevWorkspace.DEV_WORKSPACES_VIEW_WORKSPACES_CLUSTER_ROLE),
            () => kubeHelper.deleteClusterRole(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_PROXY_CLUSTER_ROLE),
            () => kubeHelper.deleteClusterRole(dev_workspace_1.DevWorkspace.DEV_WORKSPACES_METRICS_CLUSTER_ROLE),
            () => kubeHelper.deleteClusterRole(dev_workspace_1.DevWorkspace.DEV_WORKSPACES_CLUSTER_ROLE),
            () => kubeHelper.deleteClusterRole(dev_workspace_1.DevWorkspace.DEV_WORKSPACES_WEBHOOK_CLUSTER_ROLE),
            () => kubeHelper.deleteServiceAccount(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_CONTROLLER_SERVICE_ACCOUNT, ctx[context_1.DevWorkspaceContext.NAMESPACE]),
            () => kubeHelper.deleteServiceAccount(dev_workspace_1.DevWorkspace.WEBHOOK_SERVER_SERVICE_ACCOUNT, ctx[context_1.DevWorkspaceContext.NAMESPACE]),
        ]);
    }
    DevWorkspacesTasks.getDeleteRbacTask = getDeleteRbacTask;
    function getDeleteCertificatesTask() {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const ctx = context_1.CheCtlContext.get();
        return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete Certificates', [
            () => kubeHelper.deleteIssuer(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_CONTROLLER_ISSUER, ctx[context_1.DevWorkspaceContext.NAMESPACE]),
            () => kubeHelper.deleteCertificate(dev_workspace_1.DevWorkspace.DEV_WORKSPACE_CONTROLLER_CERTIFICATE, ctx[context_1.DevWorkspaceContext.NAMESPACE]),
        ]);
    }
    DevWorkspacesTasks.getDeleteCertificatesTask = getDeleteCertificatesTask;
    function getCreateOrUpdateDevWorkspaceTask(isCreateOnly) {
        return {
            title: `${isCreateOnly ? 'Create' : 'Update'} ${dev_workspace_1.DevWorkspace.PRODUCT_NAME} operator resources`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                yield kubeHelper.applyResource(`${path.normalize(ctx[context_1.CliContext.CLI_DEV_WORKSPACE_OPERATOR_RESOURCES_DIR])}/kubernetes/combined.yaml`);
                task.title = `${task.title}...[${isCreateOnly ? 'Created' : 'Updated'}]`;
            }),
        };
    }
    DevWorkspacesTasks.getCreateOrUpdateDevWorkspaceTask = getCreateOrUpdateDevWorkspaceTask;
    function getWaitDevWorkspaceTask() {
        return {
            title: `Wait for ${dev_workspace_1.DevWorkspace.PRODUCT_NAME} operator ready`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                yield kubeHelper.waitForPodReady('app.kubernetes.io/name=devworkspace-controller', ctx[context_1.DevWorkspaceContext.NAMESPACE]);
                yield kubeHelper.waitForPodReady('app.kubernetes.io/name=devworkspace-webhook-server', ctx[context_1.DevWorkspaceContext.NAMESPACE], true);
                task.title = `${task.title}...[OK]`;
            }),
        };
    }
    DevWorkspacesTasks.getWaitDevWorkspaceTask = getWaitDevWorkspaceTask;
})(DevWorkspacesTasks || (exports.DevWorkspacesTasks = DevWorkspacesTasks = {}));
//# sourceMappingURL=dev-workspace-tasks.js.map