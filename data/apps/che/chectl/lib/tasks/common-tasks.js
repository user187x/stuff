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
exports.CommonTasks = void 0;
const tslib_1 = require("tslib");
const context_1 = require("../context");
const kube_client_1 = require("../api/kube-client");
const core_1 = require("@oclif/core");
const flags_1 = require("../flags");
const eclipse_che_1 = require("./installers/eclipse-che/eclipse-che");
const utls_1 = require("../utils/utls");
const k8s_version_1 = require("../utils/k8s-version");
const che_1 = require("../utils/che");
var CommonTasks;
(function (CommonTasks) {
    const OUTPUT_SEPARATOR = '-------------------------------------------------------------------------------';
    function getTestKubernetesApiTasks() {
        return {
            title: 'Verify Kubernetes API',
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const flags = context_1.CheCtlContext.getFlags();
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                try {
                    core_1.ux.info(`› Current Kubernetes context: '${kubeHelper.getCurrentContext()}'`);
                    if (!flags[flags_1.SKIP_KUBE_HEALTHZ_CHECK_FLAG]) {
                        yield kubeHelper.checkKubeApi();
                    }
                    task.title = `${task.title}...[${ctx[context_1.InfrastructureContext.KUBERNETES_VERSION]}]`;
                    if (!flags[flags_1.SKIP_VERSION_CHECK_FLAG]) {
                        const checkPassed = k8s_version_1.K8sVersion.checkMinimalK8sVersion(ctx[context_1.InfrastructureContext.KUBERNETES_VERSION]);
                        if (!checkPassed) {
                            throw k8s_version_1.K8sVersion.getMinimalK8sVersionError(ctx[context_1.InfrastructureContext.KUBERNETES_VERSION]);
                        }
                    }
                }
                catch (error) {
                    return (0, utls_1.newError)('Failed to connect to Kubernetes API. If you\'re sure that your Kubernetes cluster is healthy - you can skip this check with \'--skip-kubernetes-health-check\' flag.', error);
                }
            }),
        };
    }
    CommonTasks.getTestKubernetesApiTasks = getTestKubernetesApiTasks;
    function getDeleteNamespaceTask(namespace) {
        return {
            title: `Delete Namespace ${namespace}`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                if (namespace === 'openshift-operators') {
                    return task.skip('openshift-operators namespace is protected and can not be deleted.');
                }
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                yield kubeHelper.deleteNamespace(namespace);
                task.title = `${task.title}...[Deleted]`;
            }),
        };
    }
    CommonTasks.getDeleteNamespaceTask = getDeleteNamespaceTask;
    function getCreateNamespaceTask(namespaceName, labels) {
        return {
            title: `Create Namespace ${namespaceName}`,
            task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const namespace = yield kubeHelper.getNamespace(namespaceName);
                if (namespace) {
                    yield kubeHelper.waitNamespaceActive(namespaceName);
                    task.title = `${task.title}...[Exists]`;
                }
                else {
                    const namespace = {
                        apiVersion: 'v1',
                        kind: 'Namespace',
                        metadata: {
                            labels,
                            name: namespaceName,
                        },
                    };
                    yield kubeHelper.createNamespace(namespace);
                    yield kubeHelper.waitNamespaceActive(namespaceName);
                    task.title = `${task.title}...[Created]`;
                }
            }),
        };
    }
    CommonTasks.getCreateNamespaceTask = getCreateNamespaceTask;
    function getCreateOrUpdateResourceTask(isCreateOnly, resourceKind, resourceName, isExistsResource, createResource, replaceResource) {
        return {
            title: `${isCreateOnly ? 'Create' : 'Update'} ${resourceKind} ${resourceName}`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const exist = yield isExistsResource();
                if (exist) {
                    if (isCreateOnly) {
                        task.title = `${task.title}...[Exists]`;
                    }
                    else {
                        yield replaceResource();
                        task.title = `${task.title}...[Updated]`;
                    }
                }
                else {
                    yield createResource();
                    task.title = `${task.title}...[Created]`;
                }
            }),
        };
    }
    CommonTasks.getCreateOrUpdateResourceTask = getCreateOrUpdateResourceTask;
    function getSkipTask(title, skipMsg) {
        return {
            title,
            task: (_ctx, task) => {
                task.skip(skipMsg);
            },
        };
    }
    CommonTasks.getSkipTask = getSkipTask;
    function getNotEclipseCheResourceSkipTask(title) {
        return getSkipTask(title, `Not ${eclipse_che_1.EclipseChe.PRODUCT_NAME} resource`);
    }
    CommonTasks.getNotEclipseCheResourceSkipTask = getNotEclipseCheResourceSkipTask;
    function getDisabledTask() {
        return {
            title: '',
            enabled: () => false,
            task: () => tslib_1.__awaiter(this, void 0, void 0, function* () { }),
        };
    }
    CommonTasks.getDisabledTask = getDisabledTask;
    function getCreateResourceTask(resourceKind, resourceName, isExistsResource, createResource) {
        return {
            title: `Create ${resourceKind} ${resourceName}`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const exist = yield isExistsResource();
                if (exist) {
                    task.title = `${task.title}...[Exists]`;
                }
                else {
                    yield createResource();
                    task.title = `${task.title}...[Created]`;
                }
            }),
        };
    }
    CommonTasks.getCreateResourceTask = getCreateResourceTask;
    function getDeleteResourcesTask(taskTitle, deleteResources) {
        return {
            title: `${taskTitle}`,
            task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                let failed = false;
                for (const deleteResource of deleteResources) {
                    try {
                        yield deleteResource();
                    }
                    catch (e) {
                        failed = true;
                        task.title = `${task.title}...[Failed: ${e.message}]`;
                    }
                }
                if (!failed) {
                    task.title = `${task.title}...[Deleted]`;
                }
            }),
        };
    }
    CommonTasks.getDeleteResourcesTask = getDeleteResourcesTask;
    function getWaitTask(milliseconds) {
        return {
            title: 'Waiting',
            task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                yield core_1.ux.wait(milliseconds);
                task.title = `${task.title}...[OK]`;
            }),
        };
    }
    CommonTasks.getWaitTask = getWaitTask;
    function getOpenShiftVersionTask() {
        return {
            title: 'OpenShift version',
            enabled: (ctx) => ctx[context_1.InfrastructureContext.IS_OPENSHIFT],
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                task.title = `${task.title}...[${ctx[context_1.InfrastructureContext.OPENSHIFT_VERSION]}]`;
            }),
        };
    }
    CommonTasks.getOpenShiftVersionTask = getOpenShiftVersionTask;
    function getPreparePostInstallationOutputTask() {
        return {
            title: 'Prepare post installation output',
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const flags = context_1.CheCtlContext.getFlags();
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const messages = [];
                const version = yield che_1.Che.getCheVersion();
                messages.push(`${eclipse_che_1.EclipseChe.PRODUCT_NAME} ${version.trim()} has been successfully deployed.`, `Documentation             : ${eclipse_che_1.EclipseChe.DOC_LINK}`);
                if (eclipse_che_1.EclipseChe.DOC_LINK_RELEASE_NOTES) {
                    messages.push(`Release Notes           : ${eclipse_che_1.EclipseChe.DOC_LINK_RELEASE_NOTES}`);
                }
                messages.push(OUTPUT_SEPARATOR);
                const dashboardURL = che_1.Che.buildDashboardURL(yield che_1.Che.getCheURL(flags[flags_1.CHE_NAMESPACE_FLAG]));
                messages.push(`Users Dashboard           : ${dashboardURL}`, OUTPUT_SEPARATOR);
                const cheConfigMap = yield kubeHelper.getConfigMap(eclipse_che_1.EclipseChe.CONFIG_MAP, flags[flags_1.CHE_NAMESPACE_FLAG]);
                if (cheConfigMap && cheConfigMap.data) {
                    if (cheConfigMap.data.CHE_WORKSPACE_PLUGIN__REGISTRY__URL) {
                        messages.push(`Plug-in Registry          : ${(0, utls_1.addTrailingSlash)(cheConfigMap.data.CHE_WORKSPACE_PLUGIN__REGISTRY__URL)}`, OUTPUT_SEPARATOR);
                    }
                    if (flags[flags_1.PLATFORM_FLAG] === 'minikube') {
                        messages.push('Dex user credentials      : che@eclipse.org:admin', 'Dex user credentials      : user1@che:password', 'Dex user credentials      : user2@che:password', 'Dex user credentials      : user3@che:password', 'Dex user credentials      : user4@che:password', 'Dex user credentials      : user5@che:password', OUTPUT_SEPARATOR);
                    }
                }
                ctx[context_1.CliContext.CLI_COMMAND_POST_OUTPUT_MESSAGES] = messages.concat(ctx[context_1.CliContext.CLI_COMMAND_POST_OUTPUT_MESSAGES]);
                task.title = `${task.title}...[OK]`;
            }),
        };
    }
    CommonTasks.getPreparePostInstallationOutputTask = getPreparePostInstallationOutputTask;
    function getPrintHighlightedMessagesTask() {
        return {
            title: 'Show important messages',
            enabled: ctx => ctx[context_1.CliContext.CLI_COMMAND_POST_OUTPUT_MESSAGES].length > 0,
            task: (ctx) => {
                const tasks = (0, utls_1.newListr)();
                for (const message of ctx[context_1.CliContext.CLI_COMMAND_POST_OUTPUT_MESSAGES]) {
                    tasks.add({
                        title: message,
                        task: () => { },
                    });
                }
                return tasks;
            },
        };
    }
    CommonTasks.getPrintHighlightedMessagesTask = getPrintHighlightedMessagesTask;
    function getVerifyCommand(title, errorMsg, isVerifiedResource) {
        return {
            title,
            task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                if (yield isVerifiedResource()) {
                    task.title = `${task.title}...[OK]`;
                }
                else {
                    core_1.ux.error(errorMsg, { exit: 1 });
                }
            }),
        };
    }
    CommonTasks.getVerifyCommand = getVerifyCommand;
})(CommonTasks || (exports.CommonTasks = CommonTasks = {}));
//# sourceMappingURL=common-tasks.js.map