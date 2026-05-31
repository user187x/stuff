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
exports.OlmTasks = void 0;
const tslib_1 = require("tslib");
const context_1 = require("../context");
const kube_client_1 = require("../api/kube-client");
const utls_1 = require("../utils/utls");
const path = require("node:path");
const yaml = require("js-yaml");
const common_tasks_1 = require("./common-tasks");
const flags_1 = require("../flags");
const eclipse_che_1 = require("./installers/eclipse-che/eclipse-che");
const dev_workspace_1 = require("./installers/dev-workspace/dev-workspace");
const che_1 = require("../utils/che");
var OlmTasks;
(function (OlmTasks) {
    function getDeleteSubscriptionAndCatalogSourceTask(packageName, csvPrefix, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            let title = 'Delete Subscription';
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const deleteResources = [];
            // Subscription
            const subscription = yield kubeHelper.getOperatorSubscriptionByPackageInNamespace(packageName, namespace);
            if (subscription) {
                title = `${title} ${subscription.metadata.name}`;
                deleteResources.push(() => kubeHelper.deleteOperatorSubscription(subscription.metadata.name, namespace));
                // CatalogSource
                const catalogSource = yield kubeHelper.getCatalogSource(subscription.spec.source, subscription.spec.sourceNamespace);
                if (!che_1.Che.isRedHatCatalogSources(catalogSource === null || catalogSource === void 0 ? void 0 : catalogSource.metadata.name)) {
                    title = `${title} and CatalogSource ${subscription.spec.source}`;
                    deleteResources.push(() => kubeHelper.deleteCatalogSource(subscription.spec.source, subscription.spec.sourceNamespace));
                }
            }
            // ClusterServiceVersion
            const csvs = yield kubeHelper.getCSVWithPrefix(csvPrefix, namespace);
            for (const csv of csvs) {
                deleteResources.push(() => kubeHelper.deleteClusterServiceVersion(csv.metadata.name, namespace));
            }
            return common_tasks_1.CommonTasks.getDeleteResourcesTask(title, deleteResources);
        });
    }
    OlmTasks.getDeleteSubscriptionAndCatalogSourceTask = getDeleteSubscriptionAndCatalogSourceTask;
    function getDeleteOperatorsTask() {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const ctx = context_1.CheCtlContext.get();
        const flags = context_1.CheCtlContext.getFlags();
        const deleteResources = [() => kubeHelper.deleteOperator(`${eclipse_che_1.EclipseChe.PACKAGE}.${ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]}`)];
        if (flags[flags_1.DELETE_ALL_FLAG]) {
            deleteResources.push(() => kubeHelper.deleteOperator(`${dev_workspace_1.DevWorkspace.PACKAGE}.${ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]}`));
        }
        return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete Operators', deleteResources);
    }
    OlmTasks.getDeleteOperatorsTask = getDeleteOperatorsTask;
    function getCreateSubscriptionTask(name, namespace, catalogSource, catalogSourceNamespace, packageName, channel, approvalStrategy, startingCSV) {
        return {
            title: `Create Subscription ${name}`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                let subscription = yield kubeHelper.getOperatorSubscription(name, namespace);
                const subscriptionExists = subscription !== undefined;
                if (!subscriptionExists) {
                    subscription = {
                        apiVersion: 'operators.coreos.com/v1alpha1',
                        kind: 'Subscription',
                        metadata: {
                            name: name,
                            namespace: namespace,
                            labels: {
                                'app.kubernetes.io/part-of': 'che.eclipse.org',
                            },
                        },
                        spec: {
                            channel: channel,
                            installPlanApproval: approvalStrategy,
                            name: packageName,
                            source: catalogSource,
                            sourceNamespace: catalogSourceNamespace,
                            startingCSV: startingCSV,
                        },
                    };
                    yield kubeHelper.createOperatorSubscription(subscription, namespace);
                }
                // wait for Subscription
                const installPlan = yield kubeHelper.waitOperatorSubscriptionReadyForApproval(name, namespace);
                // approve InstallPlan
                yield kubeHelper.approveOperatorInstallationPlan(installPlan.name, namespace);
                yield kubeHelper.waitOperatorInstallPlan(installPlan.name, namespace);
                // wait for CSV
                const installedCSVName = yield kubeHelper.waitInstalledCSVInSubscription(name, namespace);
                const phase = yield kubeHelper.waitCSVStatusPhase(installedCSVName, namespace);
                if (phase === 'Failed') {
                    const csv = yield kubeHelper.getCSV(installedCSVName, namespace);
                    if (!csv) {
                        throw new Error(`Cluster service version '${installedCSVName}' not found.`);
                    }
                    throw new Error(`Cluster service version resource failed, cause: ${csv.status.message}, reason: ${csv.status.reason}.`);
                }
                task.title = `${task.title}...[${subscriptionExists ? 'Exists' : 'Created'}]`;
            }),
        };
    }
    OlmTasks.getCreateSubscriptionTask = getCreateSubscriptionTask;
    function getCreateCatalogSourceTask(name, namespace, image) {
        return {
            title: `Create CatalogSource ${name}`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                if (!(yield kubeHelper.isCatalogSourceExists(name, namespace))) {
                    const catalogSource = {
                        apiVersion: 'operators.coreos.com/v1alpha1',
                        kind: 'CatalogSource',
                        metadata: {
                            name: name,
                            namespace: namespace,
                            labels: {
                                'app.kubernetes.io/part-of': 'che.eclipse.org',
                            },
                        },
                        spec: {
                            image: image,
                            sourceType: 'grpc',
                            updateStrategy: {
                                registryPoll: {
                                    interval: '15m',
                                },
                            },
                        },
                    };
                    yield kubeHelper.createCatalogSource(catalogSource, namespace);
                    yield kubeHelper.waitCatalogSource(name, namespace);
                    task.title = `${task.title}...[Created]`;
                }
                else {
                    task.title = `${task.title}...[Exists]`;
                }
            }),
        };
    }
    OlmTasks.getCreateCatalogSourceTask = getCreateCatalogSourceTask;
    function getCreatePrometheusRBACTask() {
        const flags = context_1.CheCtlContext.getFlags();
        return {
            enabled: () => flags[flags_1.CLUSTER_MONITORING_FLAG],
            title: `Create ${eclipse_che_1.EclipseChe.PROMETHEUS} RBAC`,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const roleYamlFilePath = path.join((0, utls_1.getEmbeddedTemplatesDirectory)(), '..', 'resources', 'prometheus-role.yaml');
                const role = (0, utls_1.safeLoadFromYamlFile)(roleYamlFilePath);
                const roleBindingYamlFilePath = path.join((0, utls_1.getEmbeddedTemplatesDirectory)(), '..', 'resources', 'prometheus-role-binding.yaml');
                const roleBinding = (0, utls_1.safeLoadFromYamlFile)(roleBindingYamlFilePath);
                const tasks = (0, utls_1.newListr)();
                tasks.add(common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(true, 'Role', eclipse_che_1.EclipseChe.PROMETHEUS, () => kubeHelper.isRoleExist(eclipse_che_1.EclipseChe.PROMETHEUS, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.createRole(role, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.replaceRole(role, flags[flags_1.CHE_NAMESPACE_FLAG])));
                tasks.add(common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(true, 'RoleBinding', eclipse_che_1.EclipseChe.PROMETHEUS, () => kubeHelper.isRoleBindingExist(eclipse_che_1.EclipseChe.PROMETHEUS, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.createRoleBinding(roleBinding, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.replaceRoleBinding(roleBinding, flags[flags_1.CHE_NAMESPACE_FLAG])));
                return tasks;
            }),
        };
    }
    OlmTasks.getCreatePrometheusRBACTask = getCreatePrometheusRBACTask;
    function getApproveInstallPlanTask(subscriptionName) {
        return {
            title: `Approve InstallPlan for ${subscriptionName}`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                var _a;
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const subscription = yield kubeHelper.getOperatorSubscription(subscriptionName, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]);
                if (!subscription) {
                    throw new Error(`Subscription ${subscriptionName} not found.`);
                }
                if (subscription.status) {
                    if (subscription.status.state === 'AtLatestKnown') {
                        task.title = `${task.title}...[Everything is up to date. Installed the latest known '${getVersionFromCSV(subscription.status.currentCSV)}' version]`;
                        return;
                    }
                    if (subscription.status.state === 'UpgradeAvailable') {
                        task.title = `${task.title}...[Upgrade is already in progress]`;
                        return;
                    }
                    if (subscription.status.state === 'UpgradePending') {
                        const installedCSV = subscription.status.installedCSV;
                        const currentCSV = subscription.status.currentCSV;
                        if (!((_a = subscription.status.installplan) === null || _a === void 0 ? void 0 : _a.name)) {
                            throw new Error(`${eclipse_che_1.EclipseChe.PRODUCT_NAME} InstallPlan name is empty.`);
                        }
                        yield kubeHelper.approveOperatorInstallationPlan(subscription.status.installplan.name, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]);
                        yield kubeHelper.waitOperatorInstallPlan(subscription.status.installplan.name, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]);
                        if (installedCSV) {
                            ctx[context_1.CliContext.CLI_COMMAND_POST_OUTPUT_MESSAGES].push(`${subscription.spec.name} is upgraded from '${getVersionFromCSV(installedCSV)}' to '${getVersionFromCSV(currentCSV)}' version`);
                        }
                        else {
                            ctx[context_1.CliContext.CLI_COMMAND_POST_OUTPUT_MESSAGES].push(`${subscription.spec.name} '${getVersionFromCSV(currentCSV)}' version installed`);
                        }
                        task.title = `${task.title}...[OK]`;
                        return;
                    }
                    throw new Error(`Subscription in '${subscription.status.state}' state.`);
                }
                throw new Error('InstallPlan not found.');
            }),
        };
    }
    OlmTasks.getApproveInstallPlanTask = getApproveInstallPlanTask;
    function getSetCustomEclipseCheOperatorImageTask() {
        const flags = context_1.CheCtlContext.getFlags();
        return {
            title: 'Set custom operator image',
            enabled: () => flags[flags_1.CHE_OPERATOR_IMAGE_FLAG],
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const csvs = yield kubeHelper.getCSVWithPrefix(eclipse_che_1.EclipseChe.CSV_PREFIX, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]);
                if (csvs.length !== 1) {
                    throw new Error(`${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator CSV not found.`);
                }
                const jsonPatch = [{ op: 'replace', path: '/spec/install/spec/deployments/0/spec/template/spec/containers/0/image', value: flags[flags_1.CHE_OPERATOR_IMAGE_FLAG] }];
                yield kubeHelper.patchClusterServiceVersion(csvs[0].metadata.name, csvs[0].metadata.namespace, jsonPatch);
                task.title = `${task.title}...[${flags[flags_1.CHE_OPERATOR_IMAGE_FLAG]}: OK]`;
            }),
        };
    }
    OlmTasks.getSetCustomEclipseCheOperatorImageTask = getSetCustomEclipseCheOperatorImageTask;
    function getFetchCheClusterSampleTask() {
        return {
            title: 'Fetch CheCluster sample from a CSV',
            enabled: (ctx) => !ctx[context_1.EclipseCheContext.CUSTOM_CR],
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const subscription = yield kubeHelper.getOperatorSubscription(eclipse_che_1.EclipseChe.SUBSCRIPTION, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]);
                if (!subscription) {
                    throw new Error(`Subscription ${eclipse_che_1.EclipseChe.SUBSCRIPTION} not found.`);
                }
                const installedCSV = subscription.status.installedCSV;
                const csv = yield kubeHelper.getCSV(installedCSV, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]);
                if (csv && csv.metadata.annotations) {
                    const rawYaml = csv.metadata.annotations['alm-examples'];
                    ctx[context_1.EclipseCheContext.DEFAULT_CR] = yaml.load(rawYaml).find(cr => kubeHelper.isCheClusterAPIV2(cr));
                }
                else {
                    throw new Error(`Unable to fetch CheCluster CR sample ${!csv ? '' : 'from CSV: ' + csv.spec.displayName}`);
                }
                task.title = `${task.title}...[OK]`;
            }),
        };
    }
    OlmTasks.getFetchCheClusterSampleTask = getFetchCheClusterSampleTask;
    function getVersionFromCSV(csvName) {
        return csvName.slice(csvName.lastIndexOf('v') + 1);
    }
})(OlmTasks || (exports.OlmTasks = OlmTasks = {}));
//# sourceMappingURL=olm-tasks.js.map