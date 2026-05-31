"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.EclipseCheTasks = void 0;
const tslib_1 = require("tslib");
const common_tasks_1 = require("../../common-tasks");
const eclipse_che_1 = require("./eclipse-che");
const yaml = require("js-yaml");
const fs = require("node:fs");
const context_1 = require("../../../context");
const path = require("node:path");
const kube_client_1 = require("../../../api/kube-client");
const flags_1 = require("../../../flags");
const utls_1 = require("../../../utils/utls");
const core_1 = require("@oclif/core");
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
var EclipseCheTasks;
(function (EclipseCheTasks) {
    function getCreateOrUpdateDeploymentTask(isCreateOnly) {
        const flags = context_1.CheCtlContext.getFlags();
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const yamlFilePath = getResourcePath('operator.yaml');
        const deployment = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
        if (flags[flags_1.CHE_OPERATOR_IMAGE_FLAG]) {
            const container = deployment.spec.template.spec.containers.find(c => c.name === `${eclipse_che_1.EclipseChe.CHE_FLAVOR}-operator`);
            container.image = flags[flags_1.CHE_OPERATOR_IMAGE_FLAG];
        }
        return common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'Deployment', eclipse_che_1.EclipseChe.OPERATOR_DEPLOYMENT_NAME, () => kubeHelper.isDeploymentExist(eclipse_che_1.EclipseChe.OPERATOR_DEPLOYMENT_NAME, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.createDeployment(deployment, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.replaceDeployment(eclipse_che_1.EclipseChe.OPERATOR_DEPLOYMENT_NAME, deployment, flags[flags_1.CHE_NAMESPACE_FLAG]));
    }
    EclipseCheTasks.getCreateOrUpdateDeploymentTask = getCreateOrUpdateDeploymentTask;
    function getCreateOrUpdateCrdTask(isCreateOnly) {
        const flags = context_1.CheCtlContext.getFlags();
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const yamlFilePath = getCRDResourcePath();
        const crd = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
        crd.spec.conversion.webhook.clientConfig.service.namespace = flags[flags_1.CHE_NAMESPACE_FLAG];
        crd.metadata.annotations['cert-manager.io/inject-ca-from'] = `${flags[flags_1.CHE_NAMESPACE_FLAG]}/${eclipse_che_1.EclipseChe.K8S_CERTIFICATE}`;
        return common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'CRD', eclipse_che_1.EclipseChe.CHE_CLUSTER_CRD, () => kubeHelper.getCustomResourceDefinition(eclipse_che_1.EclipseChe.CHE_CLUSTER_CRD), () => kubeHelper.createCustomResourceDefinition(crd), () => kubeHelper.replaceCustomResourceDefinition(crd));
    }
    EclipseCheTasks.getCreateOrUpdateCrdTask = getCreateOrUpdateCrdTask;
    function getCreateOrUpdateMutatingWebhookTask(isCreateOnly) {
        const flags = context_1.CheCtlContext.getFlags();
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const yamlFilePath = getResourcePath('org.eclipse.che.MutatingWebhookConfiguration.yaml');
        const webhook = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
        webhook.webhooks[0].clientConfig.service.namespace = flags[flags_1.CHE_NAMESPACE_FLAG];
        webhook.metadata.annotations['cert-manager.io/inject-ca-from'] = `${flags[flags_1.CHE_NAMESPACE_FLAG]}/${eclipse_che_1.EclipseChe.K8S_CERTIFICATE}`;
        return common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'MutatingWebhookConfiguration', eclipse_che_1.EclipseChe.MUTATING_WEBHOOK, () => kubeHelper.isMutatingWebhookConfigurationExists(eclipse_che_1.EclipseChe.MUTATING_WEBHOOK), () => kubeHelper.createMutatingWebhookConfiguration(webhook), () => kubeHelper.replaceVMutatingWebhookConfiguration(eclipse_che_1.EclipseChe.MUTATING_WEBHOOK, webhook));
    }
    EclipseCheTasks.getCreateOrUpdateMutatingWebhookTask = getCreateOrUpdateMutatingWebhookTask;
    function getCreateOrUpdateValidatingWebhookTask(isCreateOnly) {
        const flags = context_1.CheCtlContext.getFlags();
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const yamlFilePath = getResourcePath('org.eclipse.che.ValidatingWebhookConfiguration.yaml');
        const webhook = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
        webhook.webhooks[0].clientConfig.service.namespace = flags[flags_1.CHE_NAMESPACE_FLAG];
        webhook.metadata.annotations['cert-manager.io/inject-ca-from'] = `${flags[flags_1.CHE_NAMESPACE_FLAG]}/${eclipse_che_1.EclipseChe.K8S_CERTIFICATE}`;
        return common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'ValidatingWebhookConfiguration', eclipse_che_1.EclipseChe.VALIDATING_WEBHOOK, () => kubeHelper.isValidatingWebhookConfigurationExists(eclipse_che_1.EclipseChe.VALIDATING_WEBHOOK), () => kubeHelper.createValidatingWebhookConfiguration(webhook), () => kubeHelper.replaceValidatingWebhookConfiguration(eclipse_che_1.EclipseChe.VALIDATING_WEBHOOK, webhook));
    }
    EclipseCheTasks.getCreateOrUpdateValidatingWebhookTask = getCreateOrUpdateValidatingWebhookTask;
    function getCreateOrUpdateIssuerTask(isCreateOnly) {
        const flags = context_1.CheCtlContext.getFlags();
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const yamlFilePath = getResourcePath('selfsigned-issuer.yaml');
        const issuer = yaml.load(fs.readFileSync(yamlFilePath).toString());
        return common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'Issuer', eclipse_che_1.EclipseChe.K8S_ISSUER, () => kubeHelper.isIssuerExists(eclipse_che_1.EclipseChe.K8S_ISSUER, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.createIssuer(issuer, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.replaceIssuer(eclipse_che_1.EclipseChe.K8S_ISSUER, issuer, flags[flags_1.CHE_NAMESPACE_FLAG]));
    }
    EclipseCheTasks.getCreateOrUpdateIssuerTask = getCreateOrUpdateIssuerTask;
    function getCreateOrUpdateCertificateTask(isCreateOnly) {
        const flags = context_1.CheCtlContext.getFlags();
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const yamlFilePath = getResourcePath('serving-cert.yaml');
        const certificate = yaml.load(fs.readFileSync(yamlFilePath).toString());
        certificate.spec.dnsNames = [`${eclipse_che_1.EclipseChe.OPERATOR_SERVICE}.${flags[flags_1.CHE_NAMESPACE_FLAG]}.svc`, `${eclipse_che_1.EclipseChe.OPERATOR_SERVICE}.${flags[flags_1.CHE_NAMESPACE_FLAG]}.svc.cluster.local`];
        return common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'Certificate', eclipse_che_1.EclipseChe.K8S_CERTIFICATE, () => kubeHelper.isCertificateExists(eclipse_che_1.EclipseChe.K8S_CERTIFICATE, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.createCertificate(certificate, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.replaceCertificate(eclipse_che_1.EclipseChe.K8S_CERTIFICATE, certificate, flags[flags_1.CHE_NAMESPACE_FLAG]));
    }
    EclipseCheTasks.getCreateOrUpdateCertificateTask = getCreateOrUpdateCertificateTask;
    function getCreateOrUpdateServiceAccountTask(isCreateOnly) {
        const flags = context_1.CheCtlContext.getFlags();
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const yamlFilePath = getResourcePath('service_account.yaml');
        const serviceAccount = yaml.load(fs.readFileSync(yamlFilePath).toString());
        return common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'ServiceAccount', eclipse_che_1.EclipseChe.OPERATOR_SERVICE_ACCOUNT, () => kubeHelper.isServiceAccountExist(eclipse_che_1.EclipseChe.OPERATOR_SERVICE_ACCOUNT, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.createServiceAccount(serviceAccount, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.replaceServiceAccount(eclipse_che_1.EclipseChe.OPERATOR_SERVICE_ACCOUNT, serviceAccount, flags[flags_1.CHE_NAMESPACE_FLAG]));
    }
    EclipseCheTasks.getCreateOrUpdateServiceAccountTask = getCreateOrUpdateServiceAccountTask;
    function getCreateOrUpdateServiceTask(isCreateOnly) {
        const flags = context_1.CheCtlContext.getFlags();
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const yamlFilePath = getResourcePath('webhook-service.yaml');
        const service = yaml.load(fs.readFileSync(yamlFilePath).toString());
        return common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'Service', eclipse_che_1.EclipseChe.OPERATOR_SERVICE, () => kubeHelper.isServiceExists(eclipse_che_1.EclipseChe.OPERATOR_SERVICE, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.createService(service, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.replaceService(eclipse_che_1.EclipseChe.OPERATOR_SERVICE, service, flags[flags_1.CHE_NAMESPACE_FLAG]));
    }
    EclipseCheTasks.getCreateOrUpdateServiceTask = getCreateOrUpdateServiceTask;
    function getCreateOrUpdateRbacTasks(isCreateOnly) {
        return {
            title: `${isCreateOnly ? 'Create' : 'Update'} RBAC`,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const flags = context_1.CheCtlContext.getFlags();
                const kubeClient = kube_client_1.KubeClient.getInstance();
                const resources = collectRolesAndBindingsResources();
                const tasks = (0, utls_1.newListr)();
                for (const role of resources.roles) {
                    const name = role.metadata.name;
                    tasks.add(common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'Role', name, () => kubeClient.isRoleExist(name, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.createRole(role, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.replaceRole(role, flags[flags_1.CHE_NAMESPACE_FLAG])));
                }
                for (const roleBinding of resources.roleBindings) {
                    const name = roleBinding.metadata.name;
                    tasks.add(common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'RoleBinding', name, () => kubeClient.isRoleBindingExist(name, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.createRoleBinding(roleBinding, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.replaceRoleBinding(roleBinding, flags[flags_1.CHE_NAMESPACE_FLAG])));
                }
                for (const clusterRole of resources.clusterRoles) {
                    clusterRole.metadata.name = flags[flags_1.CHE_NAMESPACE_FLAG] + '-' + clusterRole.metadata.name;
                    const name = clusterRole.metadata.name;
                    tasks.add(common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'RoleBinding', name, () => kubeClient.isClusterRoleExist(name), () => kubeClient.createClusterRole(clusterRole), () => kubeClient.replaceClusterRole(clusterRole)));
                }
                for (const clusterRoleBinding of resources.clusterRoleBindings) {
                    clusterRoleBinding.metadata.name = flags[flags_1.CHE_NAMESPACE_FLAG] + '-' + clusterRoleBinding.metadata.name;
                    clusterRoleBinding.roleRef.name = flags[flags_1.CHE_NAMESPACE_FLAG] + '-' + clusterRoleBinding.roleRef.name;
                    for (const subj of clusterRoleBinding.subjects || []) {
                        subj.namespace = flags[flags_1.CHE_NAMESPACE_FLAG];
                    }
                    const name = clusterRoleBinding.metadata.name;
                    tasks.add(common_tasks_1.CommonTasks.getCreateOrUpdateResourceTask(isCreateOnly, 'RoleBinding', name, () => kubeClient.isClusterRoleBindingExist(name), () => kubeClient.createClusterRoleBinding(clusterRoleBinding), () => kubeClient.replaceClusterRoleBinding(clusterRoleBinding)));
                }
                return tasks;
            }),
        };
    }
    EclipseCheTasks.getCreateOrUpdateRbacTasks = getCreateOrUpdateRbacTasks;
    function getDiscoverUpgradeImagePathTask() {
        return {
            title: `Discover ${eclipse_che_1.EclipseChe.PRODUCT_NAME} upgrade path`,
            task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const flags = context_1.CheCtlContext.getFlags();
                const kubeClient = kube_client_1.KubeClient.getInstance();
                const deployment = yield kubeClient.getDeployment(eclipse_che_1.EclipseChe.OPERATOR_DEPLOYMENT_NAME, flags[flags_1.CHE_NAMESPACE_FLAG]);
                if (!deployment) {
                    throw new Error(`Deployment ${eclipse_che_1.EclipseChe.OPERATOR_DEPLOYMENT_NAME} not found`);
                }
                ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE] = getContainerImage(deployment);
                const [deployedImage, deployedTag] = (0, utls_1.getImageNameAndTag)(ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE]);
                ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE_NAME] = deployedImage;
                ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE_TAG] = deployedTag;
                if (flags[flags_1.CHE_OPERATOR_IMAGE_FLAG]) {
                    ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE] = flags[flags_1.CHE_OPERATOR_IMAGE_FLAG];
                }
                else {
                    // Load new operator image from templates
                    const newCheOperatorYaml = (0, utls_1.safeLoadFromYamlFile)(getResourcePath('operator.yaml'));
                    ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE] = getContainerImage(newCheOperatorYaml);
                }
                const [newImage, newTag] = (0, utls_1.getImageNameAndTag)(ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE]);
                ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_NAME] = newImage;
                ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_TAG] = newTag;
                task.title = `${task.title} ${ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE_TAG]} -> ${ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_TAG]}`;
            }),
        };
    }
    EclipseCheTasks.getDiscoverUpgradeImagePathTask = getDiscoverUpgradeImagePathTask;
    function getContainerImage(deployment) {
        const containers = deployment.spec.template.spec.containers;
        const namespace = deployment.metadata.namespace;
        const name = deployment.metadata.name;
        const container = containers.find(c => c.name === eclipse_che_1.EclipseChe.OPERATOR_DEPLOYMENT_NAME);
        if (!container) {
            throw new Error(`Can not evaluate image of ${namespace}/${name} deployment. Containers list are empty`);
        }
        if (!container.image) {
            throw new Error(`Container ${container.name} in deployment ${namespace}/${name} must have image specified`);
        }
        return container.image;
    }
    function collectRolesAndBindingsResources() {
        const ctx = context_1.CheCtlContext.get();
        const resources = {};
        resources.roles = [];
        resources.roleBindings = [];
        resources.clusterRoles = [];
        resources.clusterRoleBindings = [];
        for (const basePath of [path.join(ctx[context_1.CliContext.CLI_CHE_OPERATOR_RESOURCES_DIR], 'kubernetes')]) {
            if (!fs.existsSync(basePath)) {
                continue;
            }
            const filesList = fs.readdirSync(basePath);
            for (const fileName of filesList) {
                if (!fileName.endsWith('.yaml')) {
                    continue;
                }
                const yamlContent = (0, utls_1.safeLoadFromYamlFile)(path.join(basePath, fileName));
                if (!(yamlContent && yamlContent.kind)) {
                    continue;
                }
                switch (yamlContent.kind) {
                    case 'Role':
                        resources.roles.push(yamlContent);
                        break;
                    case 'RoleBinding':
                        resources.roleBindings.push(yamlContent);
                        break;
                    case 'ClusterRole':
                        resources.clusterRoles.push(yamlContent);
                        break;
                    case 'ClusterRoleBinding':
                        resources.clusterRoleBindings.push(yamlContent);
                        break;
                    default:
                    // Ignore this object kind
                }
            }
        }
        // Check consistency
        if (resources.roles.length !== resources.roleBindings.length) {
            core_1.ux.warn('Number of Roles and Role Bindings is different');
        }
        if (resources.clusterRoles.length !== resources.clusterRoleBindings.length) {
            core_1.ux.warn('Number of Cluster Roles and Cluster Role Bindings is different');
        }
        return resources;
    }
    function getCRDResourcePath() {
        const ctx = context_1.CheCtlContext.get();
        return path.join(ctx[context_1.CliContext.CLI_CHE_OPERATOR_RESOURCES_DIR], 'kubernetes', 'crds', 'org.eclipse.che_checlusters.yaml');
    }
    function getResourcePath(resourceName) {
        const ctx = context_1.CheCtlContext.get();
        return path.join(ctx[context_1.CliContext.CLI_CHE_OPERATOR_RESOURCES_DIR], 'kubernetes', resourceName);
    }
    function getDeleteClusterScopeObjectsTask() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a, _b, _c, _d, _e, _f, _g, _h;
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const ctx = context_1.CheCtlContext.get();
            const flags = context_1.CheCtlContext.getFlags();
            const deleteResources = [
                () => kubeHelper.deleteValidatingWebhookConfiguration(eclipse_che_1.EclipseChe.VALIDATING_WEBHOOK),
                () => kubeHelper.deleteMutatingWebhookConfiguration(eclipse_che_1.EclipseChe.MUTATING_WEBHOOK),
            ];
            if (ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
                const checluster = yield kubeHelper.getCheCluster(flags[flags_1.CHE_NAMESPACE_FLAG]);
                // ConsoleLink
                deleteResources.push(() => kubeHelper.deleteClusterCustomObject('console.openshift.io', 'v1', 'consolelinks', eclipse_che_1.EclipseChe.CONSOLE_LINK));
                // OAuthClient
                const oAuthClientName = ((_c = (_b = (_a = checluster === null || checluster === void 0 ? void 0 : checluster.spec) === null || _a === void 0 ? void 0 : _a.networking) === null || _b === void 0 ? void 0 : _b.auth) === null || _c === void 0 ? void 0 : _c.oAuthClientName) || `${flags[flags_1.CHE_NAMESPACE_FLAG]}-client`;
                deleteResources.push(() => kubeHelper.deleteClusterCustomObject('oauth.openshift.io', 'v1', 'oauthclients', oAuthClientName));
                // SCC
                const sccName = ((_f = (_e = (_d = checluster === null || checluster === void 0 ? void 0 : checluster.spec) === null || _d === void 0 ? void 0 : _d.devEnvironments) === null || _e === void 0 ? void 0 : _e.containerBuildConfiguration) === null || _f === void 0 ? void 0 : _f.openShiftSecurityContextConstraint) || 'container-build';
                const scc = yield kubeHelper.getClusterCustomObject('security.openshift.io', 'v1', 'securitycontextconstraints', sccName);
                if (((_h = (_g = scc === null || scc === void 0 ? void 0 : scc.metadata) === null || _g === void 0 ? void 0 : _g.labels) === null || _h === void 0 ? void 0 : _h['app.kubernetes.io/managed-by']) === `${eclipse_che_1.EclipseChe.CHE_FLAVOR}-operator`) {
                    deleteResources.push(() => kubeHelper.deleteClusterCustomObject('security.openshift.io', 'v1', 'securitycontextconstraints', sccName));
                }
            }
            return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete cluster scope objects', deleteResources);
        });
    }
    EclipseCheTasks.getDeleteClusterScopeObjectsTask = getDeleteClusterScopeObjectsTask;
    function getDeleteEclipseCheResourcesTask() {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        return common_tasks_1.CommonTasks.getDeleteResourcesTask(`Delete ${eclipse_che_1.EclipseChe.CHE_CLUSTER_KIND_PLURAL}.${eclipse_che_1.EclipseChe.CHE_CLUSTER_API_GROUP} resources`, [() => kubeHelper.deleteAllCustomResourcesAndCrd(eclipse_che_1.EclipseChe.CHE_CLUSTER_CRD, eclipse_che_1.EclipseChe.CHE_CLUSTER_API_GROUP, eclipse_che_1.EclipseChe.CHE_CLUSTER_API_VERSION_V2, eclipse_che_1.EclipseChe.CHE_CLUSTER_KIND_PLURAL)]);
    }
    EclipseCheTasks.getDeleteEclipseCheResourcesTask = getDeleteEclipseCheResourcesTask;
    function getDeleteNetworksTask() {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        const flags = context_1.CheCtlContext.getFlags();
        return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete Networks', [() => kubeHelper.deleteService(eclipse_che_1.EclipseChe.OPERATOR_SERVICE, flags[flags_1.CHE_NAMESPACE_FLAG])]);
    }
    EclipseCheTasks.getDeleteNetworksTask = getDeleteNetworksTask;
    function getDeleteImageContentSourcePolicyTask() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const imsp = yield kubeHelper.getClusterCustomObject('operator.openshift.io', 'v1alpha1', 'imagecontentsourcepolicies', eclipse_che_1.EclipseChe.IMAGE_CONTENT_SOURCE_POLICY);
            return imsp && !(0, utls_1.isPartOfEclipseChe)(imsp) ? common_tasks_1.CommonTasks.getSkipTask(`Delete ImageContentSourcePolicy ${eclipse_che_1.EclipseChe.IMAGE_CONTENT_SOURCE_POLICY}`, `Not ${eclipse_che_1.EclipseChe.PRODUCT_NAME} resource`) : common_tasks_1.CommonTasks.getDeleteResourcesTask(`Delete ImageContentSourcePolicy ${eclipse_che_1.EclipseChe.IMAGE_CONTENT_SOURCE_POLICY}`, [() => kubeHelper.deleteClusterCustomObject('operator.openshift.io', 'v1alpha1', 'imagecontentsourcepolicies', eclipse_che_1.EclipseChe.IMAGE_CONTENT_SOURCE_POLICY)]);
        });
    }
    EclipseCheTasks.getDeleteImageContentSourcePolicyTask = getDeleteImageContentSourcePolicyTask;
    function getDeleteWorkloadsTask() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const ctx = context_1.CheCtlContext.get();
            const flags = context_1.CheCtlContext.getFlags();
            const deleteResources = [];
            let cms = yield kubeHelper.listConfigMaps(flags[flags_1.CHE_NAMESPACE_FLAG], 'app.kubernetes.io/part-of=che.eclipse.org,app.kubernetes.io/component=gateway-config');
            for (const cm of cms) {
                deleteResources.push(() => kubeHelper.deleteConfigMap(cm.metadata.name, cm.metadata.namespace));
            }
            if (!ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
                deleteResources.push(() => kubeHelper.deleteSecret(eclipse_che_1.EclipseChe.OPERATOR_SERVICE_CERT_SECRET, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeHelper.deleteDeployment(eclipse_che_1.EclipseChe.OPERATOR_DEPLOYMENT_NAME, flags[flags_1.CHE_NAMESPACE_FLAG]));
                const pods = yield kubeHelper.listNamespacedPod(flags[flags_1.CHE_NAMESPACE_FLAG], undefined, 'app.kubernetes.io/part-of=che.eclipse.org,app.kubernetes.io/component=che-create-tls-secret-job');
                for (const pod of pods.items) {
                    deleteResources.push(() => kubeHelper.deletePod(pod.metadata.name, pod.metadata.namespace));
                }
            }
            // Delete leader election related resources
            cms = yield kubeHelper.listConfigMaps(ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]);
            for (const cm of cms) {
                const configMapName = cm.metadata.name;
                if (configMapName.endsWith('org.eclipse.che')) {
                    deleteResources.push(() => kubeHelper.deleteConfigMap(configMapName, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]), () => kubeHelper.deleteLease(configMapName, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]));
                }
            }
            return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete Workloads', deleteResources);
        });
    }
    EclipseCheTasks.getDeleteWorkloadsTask = getDeleteWorkloadsTask;
    function getDeleteRbacTask() {
        const kubeClient = kube_client_1.KubeClient.getInstance();
        const ctx = context_1.CheCtlContext.get();
        const flags = context_1.CheCtlContext.getFlags();
        const deleteResources = [];
        if (ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
            deleteResources.push(() => kubeClient.deleteRole(eclipse_che_1.EclipseChe.PROMETHEUS, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.deleteRoleBinding(eclipse_che_1.EclipseChe.PROMETHEUS, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.deleteClusterRole(`${eclipse_che_1.EclipseChe.CHE_FLAVOR}-user-container-build`), () => kubeClient.deleteClusterRole('dev-workspace-container-build'), () => kubeClient.deleteClusterRoleBinding('dev-workspace-container-build'), () => kubeClient.deleteRoleBinding(eclipse_che_1.EclipseChe.PROMETHEUS, flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.deleteRoleBinding(`${eclipse_che_1.EclipseChe.CHE_FLAVOR}-operator-service-auth-reader`, 'kube-system'));
        }
        else {
            deleteResources.push(() => kubeClient.deleteRole('che-operator', flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.deleteRole('che-operator-leader-election', flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.deleteRoleBinding('che-operator', flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.deleteRoleBinding('che-operator-leader-election', flags[flags_1.CHE_NAMESPACE_FLAG]), () => kubeClient.deleteClusterRole(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-che-operator`), () => kubeClient.deleteClusterRoleBinding(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-che-operator`), () => kubeClient.deleteServiceAccount(eclipse_che_1.EclipseChe.OPERATOR_SERVICE_ACCOUNT, flags[flags_1.CHE_NAMESPACE_FLAG]));
        }
        deleteResources.push(() => kubeClient.deleteClusterRole(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-che-gateway`), () => kubeClient.deleteClusterRole(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-che-dashboard`), () => kubeClient.deleteClusterRole(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-cheworkspaces-namespaces-clusterrole`), () => kubeClient.deleteClusterRole(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-cheworkspaces-clusterrole`), () => kubeClient.deleteClusterRole(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-cheworkspaces-devworkspace-clusterrole`), () => kubeClient.deleteClusterRoleBinding(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-che-gateway`), () => kubeClient.deleteClusterRoleBinding(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-che-dashboard`), () => kubeClient.deleteClusterRoleBinding(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-cheworkspaces-namespaces-clusterrole`), () => kubeClient.deleteClusterRoleBinding(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-cheworkspaces-clusterrole`), () => kubeClient.deleteClusterRoleBinding(`${flags[flags_1.CHE_NAMESPACE_FLAG]}-cheworkspaces-devworkspace-clusterrole`));
        return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete RBAC', deleteResources);
    }
    EclipseCheTasks.getDeleteRbacTask = getDeleteRbacTask;
    function getDeleteCertificatesTask() {
        const kubeClient = kube_client_1.KubeClient.getInstance();
        const flags = context_1.CheCtlContext.getFlags();
        return common_tasks_1.CommonTasks.getDeleteResourcesTask('Delete Certificates', [
            () => kubeClient.deleteIssuer(eclipse_che_1.EclipseChe.K8S_ISSUER, flags[flags_1.CHE_NAMESPACE_FLAG]),
            () => kubeClient.deleteCertificate(eclipse_che_1.EclipseChe.K8S_CERTIFICATE, flags[flags_1.CHE_NAMESPACE_FLAG]),
        ]);
    }
    EclipseCheTasks.getDeleteCertificatesTask = getDeleteCertificatesTask;
    function getCreateImageContentSourcePolicyTask() {
        const kubeHelper = kube_client_1.KubeClient.getInstance();
        return common_tasks_1.CommonTasks.getCreateResourceTask('ImageContentSourcePolicy', eclipse_che_1.EclipseChe.IMAGE_CONTENT_SOURCE_POLICY, () => kubeHelper.getClusterCustomObject('operator.openshift.io', 'v1alpha1', 'imagecontentsourcepolicies', eclipse_che_1.EclipseChe.IMAGE_CONTENT_SOURCE_POLICY), () => kubeHelper.createClusterCustomObject('operator.openshift.io', 'v1alpha1', 'imagecontentsourcepolicies', constructImageContentSourcePolicy()));
    }
    EclipseCheTasks.getCreateImageContentSourcePolicyTask = getCreateImageContentSourcePolicyTask;
    function constructImageContentSourcePolicy() {
        return {
            apiVersion: 'operator.openshift.io/v1alpha1',
            kind: 'ImageContentSourcePolicy',
            metadata: {
                name: eclipse_che_1.EclipseChe.IMAGE_CONTENT_SOURCE_POLICY,
                labels: {
                    'app.kubernetes.io/part-of': 'che.eclipse.org',
                },
            },
            spec: {
                repositoryDigestMirrors: [
                    {
                        mirrors: [
                            'quay.io',
                        ],
                        source: 'registry.redhat.io',
                    },
                    {
                        mirrors: [
                            'quay.io',
                        ],
                        source: 'registry.stage.redhat.io',
                    },
                    {
                        mirrors: [
                            'quay.io',
                        ],
                        source: 'registry-proxy.engineering.redhat.com',
                    },
                    {
                        mirrors: [
                            'registry.redhat.io',
                        ],
                        source: 'registry.stage.redhat.io',
                    },
                    {
                        mirrors: [
                            'registry.stage.redhat.io',
                        ],
                        source: 'registry-proxy.engineering.redhat.com',
                    },
                    {
                        mirrors: [
                            'registry.redhat.io',
                        ],
                        source: 'registry-proxy.engineering.redhat.com',
                    },
                    {
                        mirrors: [
                            'quay.io/devfile/devworkspace-operator-bundle',
                        ],
                        source: 'registry.redhat.io/devworkspace/devworkspace-operator-bundle',
                    },
                    {
                        mirrors: [
                            'quay.io/devfile/devworkspace-operator-bundle',
                        ],
                        source: 'registry.stage.redhat.io/devworkspace/devworkspace-operator-bundle',
                    },
                    {
                        mirrors: [
                            'quay.io/devfile/devworkspace-operator-bundle',
                        ],
                        source: 'registry-proxy.engineering.redhat.com/rh-osbs/devworkspace-operator-bundle',
                    },
                    {
                        mirrors: [
                            'quay.io/devworkspace/devworkspace-operator-bundle',
                        ],
                        source: 'registry.redhat.io/devworkspace/devworkspace-operator-bundle',
                    },
                    {
                        mirrors: [
                            'quay.io/devworkspace/devworkspace-operator-bundle',
                        ],
                        source: 'registry.stage.redhat.io/devworkspace/devworkspace-operator-bundle',
                    },
                    {
                        mirrors: [
                            'quay.io/devworkspace/devworkspace-operator-bundle',
                        ],
                        source: 'registry-proxy.engineering.redhat.com/rh-osbs/devworkspace-operator-bundle',
                    },
                    {
                        mirrors: [
                            'registry.redhat.io/devworkspace/devworkspace-operator-bundle',
                        ],
                        source: 'registry.stage.redhat.io/devworkspace/devworkspace-operator-bundle',
                    },
                    {
                        mirrors: [
                            'registry.stage.redhat.io/devworkspace/devworkspace-operator-bundle',
                        ],
                        source: 'registry-proxy.engineering.redhat.com/rh-osbs/devworkspace-operator-bundle',
                    },
                    {
                        mirrors: [
                            'registry.redhat.io/devworkspace/devworkspace-operator-bundle',
                        ],
                        source: 'registry-proxy.engineering.redhat.com/rh-osbs/devworkspace-operator-bundle',
                    },
                    {
                        mirrors: [
                            'quay.io/devspaces/devspaces-operator-bundle',
                        ],
                        source: 'registry.redhat.io/devspaces/devspaces-operator-bundle',
                    },
                    {
                        mirrors: [
                            'quay.io/devspaces/devspaces-operator-bundle',
                        ],
                        source: 'registry.stage.redhat.io/devspaces/devspaces-operator-bundle',
                    },
                    {
                        mirrors: [
                            'quay.io/devspaces/devspaces-operator-bundle',
                        ],
                        source: 'registry-proxy.engineering.redhat.com/rh-osbs/devspaces-operator-bundle',
                    },
                    {
                        mirrors: [
                            'registry.redhat.io/devspaces/devspaces-operator-bundle',
                        ],
                        source: 'registry.stage.redhat.io/devspaces/devspaces-operator-bundle',
                    },
                    {
                        mirrors: [
                            'registry.stage.redhat.io/devspaces/devspaces-operator-bundle',
                        ],
                        source: 'registry-proxy.engineering.redhat.com/rh-osbs/devspaces-operator-bundle',
                    },
                    {
                        mirrors: [
                            'registry.redhat.io/devspaces/devspaces-operator-bundle',
                        ],
                        source: 'registry-proxy.engineering.redhat.com/rh-osbs/devspaces-operator-bundle',
                    },
                ],
            },
        };
    }
})(EclipseCheTasks || (exports.EclipseCheTasks = EclipseCheTasks = {}));
//# sourceMappingURL=eclipse-che-tasks.js.map