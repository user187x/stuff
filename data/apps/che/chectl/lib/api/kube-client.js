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
exports.KubeClient = void 0;
const tslib_1 = require("tslib");
const client_node_1 = require("@kubernetes/client-node");
const axios_1 = require("axios");
const core_1 = require("@oclif/core");
const execa = require("execa");
const fs = require("node:fs");
const https = require("node:https");
const net = require("node:net");
const node_stream_1 = require("node:stream");
const utls_1 = require("../utils/utls");
const context_1 = require("../context");
const eclipse_che_1 = require("../tasks/installers/eclipse-che/eclipse-che");
class KubeClient {
    constructor(podWaitTimeout, podReadyTimeout) {
        this.podWaitTimeout = podWaitTimeout;
        this.podReadyTimeout = podReadyTimeout;
        this.kubeConfig = new client_node_1.KubeConfig();
        this.kubeConfig.loadFromDefault();
    }
    static getInstance() {
        const ctx = context_1.CheCtlContext.get();
        return new KubeClient(ctx[context_1.KubeHelperContext.POD_WAIT_TIMEOUT], ctx[context_1.KubeHelperContext.POD_READY_TIMEOUT]);
    }
    getKubeConfig() {
        return this.kubeConfig;
    }
    getCurrentContext() {
        return this.kubeConfig.getCurrentContext();
    }
    checkKubeApi() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const currentCluster = this.kubeConfig.getCurrentCluster();
            if (!currentCluster) {
                throw new Error('The current context is unknown.');
            }
            try {
                yield this.requestKubeHealthz(currentCluster);
            }
            catch (error) {
                if (error.message && error.message.includes('E_K8S_API_UNAUTHORIZED')) {
                    const token = yield this.getDefaultServiceAccountToken();
                    yield this.requestKubeHealthz(currentCluster, token);
                }
                else {
                    throw error;
                }
            }
        });
    }
    requestKubeHealthz(currentCluster, token) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const endpoint = `${currentCluster.server}/healthz`;
            try {
                const config = {
                    httpsAgent: new https.Agent({
                        rejectUnauthorized: false,
                        requestCert: true,
                    }),
                };
                if (token) {
                    config.headers = {
                        Authorization: `Bearer ${token}`,
                    };
                }
                const response = yield axios_1.default.get(`${endpoint}`, config);
                if (!response || response.status !== 200 || response.data !== 'ok') {
                    throw new Error('E_BAD_RESP_K8S_API');
                }
            }
            catch (error) {
                if (error.response && error.response.status === 403) {
                    throw new Error(`E_K8S_API_FORBIDDEN - Message: ${error.response.data.message}`);
                }
                if (error.response && error.response.status === 401) {
                    throw new Error(`E_K8S_API_UNAUTHORIZED - Message: ${error.response.data.message}`);
                }
                if (error.response) {
                    // The request was made and the server responded with a status code
                    // that falls out of the range of 2xx
                    throw new Error(`E_K8S_API_UNKNOWN_ERROR - Status: ${error.response.status}`);
                }
                else if (error.request) {
                    // The request was made but no response was received
                    // `error.request` is an instance of XMLHttpRequest in the browser and an instance of
                    // http.ClientRequest in node.js
                    throw new Error(`E_K8S_API_NO_RESPONSE - Endpoint: ${endpoint} - Error message: ${error.message}`);
                }
                else {
                    // Something happened in setting up the request that triggered an Error
                    throw new Error(`E_CHECTL_UNKNOWN_ERROR - Message: ${error.message}`);
                }
            }
        });
    }
    /**
     * Retrieve the default token from the default serviceAccount.
     */
    getDefaultServiceAccountToken() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            const namespaceName = 'default';
            const saName = 'default';
            let res;
            // now get the matching secrets
            try {
                res = yield k8sCoreApi.listNamespacedSecret(namespaceName);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
            if (!res || !res.body) {
                throw new Error('Unable to get default service account');
            }
            const v1SecretList = res.body;
            if (!v1SecretList.items || v1SecretList.items.length === 0) {
                throw new Error(`Unable to get default service account token since there is no secret in '${namespaceName}' namespace`);
            }
            const v1DefaultSATokenSecret = v1SecretList.items.find(secret => secret.metadata.annotations &&
                secret.metadata.annotations['kubernetes.io/service-account.name'] === saName &&
                secret.type === 'kubernetes.io/service-account-token');
            if (!v1DefaultSATokenSecret) {
                throw new Error(`Secret for '${saName}' service account is not found in namespace '${namespaceName}'`);
            }
            return Buffer.from(v1DefaultSATokenSecret.data.token, 'base64').toString();
        });
    }
    applyResource(yamlPath_1) {
        return tslib_1.__awaiter(this, arguments, void 0, function* (yamlPath, opts = '') {
            const command = `kubectl apply -f ${yamlPath} ${opts}`;
            yield execa(command, { timeout: 60000, shell: true });
        });
    }
    createNamespace(namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sCoreApi.createNamespace(namespace);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    waitNamespaceActive(name_1) {
        return tslib_1.__awaiter(this, arguments, void 0, function* (name, intervalMs = 500, timeoutMs = 60000) {
            const iterations = timeoutMs / intervalMs;
            for (let index = 0; index < iterations; index++) {
                const namespace = yield this.getNamespace(name);
                if (namespace && namespace.status && namespace.status.phase && namespace.status.phase === 'Active') {
                    return;
                }
                yield core_1.ux.wait(intervalMs);
            }
            throw new Error(`Namespace '${name}' is not in 'Active' phase.`);
        });
    }
    deleteService(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sApi.deleteNamespacedService(name, namespace);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    getServicesBySelector(labelSelector, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const res = yield k8sCoreApi.listNamespacedService(namespace, undefined, undefined, undefined, undefined, labelSelector);
                return res.body;
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    isServiceAccountExist(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sApi.readNamespacedServiceAccount(name, namespace);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteServiceAccount(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sCoreApi.deleteNamespacedServiceAccount(name, namespace);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    createServiceAccount(serviceAccount, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                (_a = serviceAccount.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield k8sCoreApi.createNamespacedServiceAccount(namespace, serviceAccount);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceServiceAccount(name, serviceAccount, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const response = yield k8sCoreApi.readNamespacedServiceAccount(name, namespace);
                serviceAccount.metadata.resourceVersion = response.body.metadata.resourceVersion;
                (_a = serviceAccount.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield k8sCoreApi.replaceNamespacedServiceAccount(name, namespace, serviceAccount);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    isRoleExist(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sRbacAuthApi.readNamespacedRole(name, namespace);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    isClusterRoleExist(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sRbacAuthApi.readClusterRole(name);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    createRole(role, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                (_a = role.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield k8sRbacAuthApi.createNamespacedRole(namespace, role);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceRole(role, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                (_a = role.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield k8sRbacAuthApi.replaceNamespacedRole(role.metadata.name, namespace, role);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    createClusterRole(clusterRole) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sRbacAuthApi.createClusterRole(clusterRole);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceClusterRole(custerRole) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sRbacAuthApi.replaceClusterRole(custerRole.metadata.name, custerRole);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteRole(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sCoreApi.deleteNamespacedRole(name, namespace);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    getPodListByLabel(namespace, labelSelector) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const { body: podList } = yield k8sCoreApi.listNamespacedPod(namespace, undefined, undefined, undefined, undefined, labelSelector);
                return podList.items;
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteClusterRole(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sCoreApi.deleteClusterRole(name);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    isRoleBindingExist(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sRbacAuthApi.readNamespacedRoleBinding(name, namespace);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    isValidatingWebhookConfigurationExists(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAdmissionApi = this.kubeConfig.makeApiClient(client_node_1.AdmissionregistrationV1Api);
            try {
                yield k8sAdmissionApi.readValidatingWebhookConfiguration(name);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceValidatingWebhookConfiguration(name, webhook) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAdmissionApi = this.kubeConfig.makeApiClient(client_node_1.AdmissionregistrationV1Api);
            try {
                const response = yield k8sAdmissionApi.readValidatingWebhookConfiguration(name);
                webhook.metadata.resourceVersion = response.body.metadata.resourceVersion;
                yield k8sAdmissionApi.replaceValidatingWebhookConfiguration(name, webhook);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    createValidatingWebhookConfiguration(webhook) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAdmissionApi = this.kubeConfig.makeApiClient(client_node_1.AdmissionregistrationV1Api);
            try {
                yield k8sAdmissionApi.createValidatingWebhookConfiguration(webhook);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteValidatingWebhookConfiguration(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAdmissionApi = this.kubeConfig.makeApiClient(client_node_1.AdmissionregistrationV1Api);
            try {
                yield k8sAdmissionApi.deleteValidatingWebhookConfiguration(name);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    isMutatingWebhookConfigurationExists(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAdmissionApi = this.kubeConfig.makeApiClient(client_node_1.AdmissionregistrationV1Api);
            try {
                yield k8sAdmissionApi.readMutatingWebhookConfiguration(name);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceVMutatingWebhookConfiguration(name, webhook) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAdmissionApi = this.kubeConfig.makeApiClient(client_node_1.AdmissionregistrationV1Api);
            try {
                const response = yield k8sAdmissionApi.readMutatingWebhookConfiguration(name);
                webhook.metadata.resourceVersion = response.body.metadata.resourceVersion;
                yield k8sAdmissionApi.replaceMutatingWebhookConfiguration(name, webhook);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    createMutatingWebhookConfiguration(webhook) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAdmissionApi = this.kubeConfig.makeApiClient(client_node_1.AdmissionregistrationV1Api);
            try {
                yield k8sAdmissionApi.createMutatingWebhookConfiguration(webhook);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteMutatingWebhookConfiguration(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAdmissionApi = this.kubeConfig.makeApiClient(client_node_1.AdmissionregistrationV1Api);
            try {
                yield k8sAdmissionApi.deleteMutatingWebhookConfiguration(name);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    isClusterRoleBindingExist(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sRbacAuthApi.readClusterRoleBinding(name);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    createRoleBinding(roleBinding, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                (_a = roleBinding.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                roleBinding.subjects[0].namespace = namespace;
                yield k8sRbacAuthApi.createNamespacedRoleBinding(namespace, roleBinding);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceRoleBinding(roleBinding, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                (_a = roleBinding.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                roleBinding.subjects[0].namespace = namespace;
                yield k8sRbacAuthApi.replaceNamespacedRoleBinding(roleBinding.metadata.name, namespace, roleBinding);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    createClusterRoleBinding(clusterRoleBinding) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sRbacAuthApi.createClusterRoleBinding(clusterRoleBinding);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceClusterRoleBinding(clusterRoleBinding) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sRbacAuthApi.replaceClusterRoleBinding(clusterRoleBinding.metadata.name, clusterRoleBinding);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteRoleBinding(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sRbacAuthApi.deleteNamespacedRoleBinding(name, namespace);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    deleteClusterRoleBinding(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sRbacAuthApi = this.kubeConfig.makeApiClient(client_node_1.RbacAuthorizationV1Api);
            try {
                yield k8sRbacAuthApi.deleteClusterRoleBinding(name);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    getConfigMap(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const { body } = yield k8sCoreApi.readNamespacedConfigMap(name, namespace);
                return body;
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    listConfigMaps(namespace, labelSelector) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const { body } = yield k8sCoreApi.listNamespacedConfigMap(namespace, undefined, undefined, undefined, undefined, labelSelector);
                return body.items;
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    getConfigMapValue(name, namespace, key) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const { body } = yield k8sCoreApi.readNamespacedConfigMap(name, namespace);
                if (body.data) {
                    return body.data[key];
                }
            }
            catch (_a) {
                return;
            }
        });
    }
    createConfigMap(configMap, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                (_a = configMap.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield k8sCoreApi.createNamespacedConfigMap(namespace, configMap);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteConfigMap(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sCoreApi.deleteNamespacedConfigMap(name, namespace);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    deleteSecret(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sCoreApi.deleteNamespacedSecret(name, namespace);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    getNamespace(namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const { body } = yield k8sApi.readNamespace(namespace);
                return body;
            }
            catch (_a) {
            }
        });
    }
    patchNamespacedCustomObject(name, namespace, patch, resourceAPIGroup, resourceAPIVersion, resourcePlural) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            // It is required to patch content-type, otherwise request will be rejected with 415 (Unsupported media type) error.
            const requestOptions = {
                headers: {
                    'content-type': 'application/merge-patch+json',
                },
            };
            try {
                const res = yield k8sCoreApi.patchNamespacedCustomObject(resourceAPIGroup, resourceAPIVersion, namespace, resourcePlural, name, patch, undefined, undefined, undefined, requestOptions);
                if (res && res.body) {
                    return res.body;
                }
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    getClusterCustomObject(group, version, plural, name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                const { body } = yield k8sCoreApi.getClusterCustomObject(group, version, plural, name);
                return body;
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    createClusterCustomObject(group, version, plural, body) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield k8sCoreApi.createClusterCustomObject(group, version, plural, body);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteClusterCustomObject(group, version, plural, name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield k8sCoreApi.deleteClusterCustomObject(group, version, plural, name);
                core_1.ux.debug(`Deleted ${plural}.${version}.${group} ${name} resource`);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    getPodWaitingState(namespace, selector, desiredPhase) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const pods = yield this.getPodListByLabel(namespace, selector);
            if (!pods.length) {
                return;
            }
            for (const pod of pods) {
                if (pod.status && pod.status.phase === desiredPhase && pod.status.containerStatuses) {
                    for (const status of pod.status.containerStatuses) {
                        if (status.state && status.state.waiting && status.state.waiting.message && status.state.waiting.reason) {
                            return status.state.waiting;
                        }
                    }
                }
            }
        });
    }
    getPodLastTerminatedState(namespace, selector) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const pods = yield this.getPodListByLabel(namespace, selector);
            if (!pods.length) {
                return;
            }
            for (const pod of pods) {
                if (pod.status && pod.status.containerStatuses) {
                    for (const status of pod.status.containerStatuses) {
                        if (status.lastState) {
                            return status.lastState.terminated;
                        }
                    }
                }
            }
        });
    }
    getPodCondition(namespace, selector, conditionType) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            let res;
            try {
                res = yield k8sCoreApi.listNamespacedPod(namespace, undefined, undefined, undefined, undefined, selector);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
            if (!res || !res.body || !res.body.items) {
                return [];
            }
            const conditions = [];
            for (const pod of res.body.items) {
                if (pod.status && pod.status.conditions) {
                    for (const condition of pod.status.conditions) {
                        if (condition.type === conditionType) {
                            conditions.push(condition);
                        }
                    }
                }
            }
            return conditions;
        });
    }
    getPodReadyConditionStatus(selector, namespace, allowMultiple) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            let res;
            try {
                res = yield k8sCoreApi.listNamespacedPod(namespace, undefined, undefined, undefined, undefined, selector);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
            if (!res || !res.body || !res.body.items) {
                throw new Error(`Get pods by selector "${selector}" returned an invalid response.`);
            }
            if (res.body.items.length < 1) {
                // No pods found by the specified selector. So, it's not ready.
                return 'False';
            }
            if (!allowMultiple && res.body.items.length > 1) {
                // Several pods found, rolling update?
                return;
            }
            if (!res.body.items[0].status || !res.body.items[0].status.conditions || !(res.body.items[0].status.conditions.length > 0)) {
                return;
            }
            const conditions = res.body.items[0].status.conditions;
            for (const condition of conditions) {
                if (condition.type === 'Ready') {
                    return condition.status;
                }
            }
        });
    }
    waitForPodReady(selector_1, namespace_1) {
        return tslib_1.__awaiter(this, arguments, void 0, function* (selector, namespace, allowMultiple = false, intervalMs = 500, timeoutMs = this.podReadyTimeout) {
            const iterations = timeoutMs / intervalMs;
            for (let index = 0; index < iterations; index++) {
                const readyStatus = yield this.getPodReadyConditionStatus(selector, namespace, allowMultiple);
                if (readyStatus === 'True') {
                    return;
                }
                yield core_1.ux.wait(intervalMs);
            }
            throw new Error(`ERR_TIMEOUT: Timeout set to pod ready timeout ${this.podReadyTimeout}`);
        });
    }
    waitUntilPodIsDeleted(selector_1, namespace_1) {
        return tslib_1.__awaiter(this, arguments, void 0, function* (selector, namespace, intervalMs = 500, timeoutMs = this.podReadyTimeout) {
            const iterations = timeoutMs / intervalMs;
            for (let index = 0; index < iterations; index++) {
                const pods = yield this.listNamespacedPod(namespace, undefined, selector);
                if (!pods.items.length) {
                    return;
                }
                yield core_1.ux.wait(intervalMs);
            }
            throw new Error('ERR_TIMEOUT: Waiting until pod is deleted took too long.');
        });
    }
    waitLatestReplica(name_1, namespace_1) {
        return tslib_1.__awaiter(this, arguments, void 0, function* (name, namespace, intervalMs = 500, timeoutMs = this.podWaitTimeout) {
            const iterations = timeoutMs / intervalMs;
            for (let index = 0; index < iterations; index++) {
                const deployment = yield this.getDeployment(name, namespace);
                if (!deployment) {
                    throw new Error(`Deployment ${namespace}/${name} is not found.`);
                }
                const deploymentStatus = deployment.status;
                if (!deploymentStatus) {
                    throw new Error(`Deployment ${namespace}/${name} does not have any status`);
                }
                if (deploymentStatus.unavailableReplicas && deploymentStatus.unavailableReplicas > 0) {
                    yield core_1.ux.wait(intervalMs);
                }
                else {
                    return;
                }
            }
            throw new Error(`ERR_TIMEOUT: Timeout set to pod wait timeout ${this.podWaitTimeout}`);
        });
    }
    isDeploymentExist(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.AppsV1Api);
            try {
                yield k8sApi.readNamespacedDeployment(name, namespace);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceConfigMap(name, configMap, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const response = yield k8sCoreApi.readNamespacedConfigMap(name, namespace);
                configMap.metadata.resourceVersion = response.body.metadata.resourceVersion;
                (_a = configMap.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield k8sCoreApi.replaceNamespacedConfigMap(name, namespace, configMap);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    isConfigMapExists(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sApi.readNamespacedConfigMap(name, namespace);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    scaleDeployment(name, namespace, replicas) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAppsApi = this.kubeConfig.makeApiClient(PatchedK8sAppsApi);
            const patch = {
                spec: {
                    replicas,
                },
            };
            let res;
            try {
                res = yield k8sAppsApi.patchNamespacedDeploymentScale(name, namespace, patch);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
            if (!res || !res.body) {
                throw new Error('Patch deployment scale returned an invalid response');
            }
        });
    }
    createDeployment(deployment, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sAppsApi = this.kubeConfig.makeApiClient(client_node_1.AppsV1Api);
            try {
                (_a = deployment.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield k8sAppsApi.createNamespacedDeployment(namespace, deployment);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceService(name, service, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const response = yield k8sCoreApi.readNamespacedService(name, namespace);
                service.metadata.resourceVersion = response.body.metadata.resourceVersion;
                (_a = service.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield k8sCoreApi.replaceNamespacedService(name, namespace, service);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    isServiceExists(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sCoreApi.readNamespacedService(name, namespace);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    createService(service, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                (_a = service.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield k8sApi.createNamespacedService(namespace, service);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deletePod(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sApi.deleteNamespacedPod(name, namespace);
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceDeployment(name, deployment, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const k8sAppsApi = this.kubeConfig.makeApiClient(client_node_1.AppsV1Api);
            deployment.spec.template.metadata.annotations = deployment.spec.template.metadata.annotations || {};
            deployment.spec.template.metadata.annotations['kubectl.kubernetes.io/restartedAt'] = new Date().toISOString();
            (_a = deployment.metadata) === null || _a === void 0 ? true : delete _a.namespace;
            try {
                yield k8sAppsApi.replaceNamespacedDeployment(name, namespace, deployment);
            }
            catch (e) {
                if (e.response && e.response.body && e.response.body.message && e.response.body.message.toString().endsWith('field is immutable')) {
                    try {
                        yield k8sAppsApi.deleteNamespacedDeployment(name, namespace);
                        yield k8sAppsApi.createNamespacedDeployment(namespace, deployment);
                    }
                    catch (e) {
                        throw this.wrapK8sClientError(e);
                    }
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteDeployment(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAppsApi = this.kubeConfig.makeApiClient(client_node_1.AppsV1Api);
            try {
                yield k8sAppsApi.deleteNamespacedDeployment(name, namespace);
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    getDeployment(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sAppsApi = this.kubeConfig.makeApiClient(client_node_1.AppsV1Api);
            try {
                const res = yield k8sAppsApi.readNamespacedDeployment(name, namespace);
                if (res && res.body) {
                    return res.body;
                }
            }
            catch (error) {
                if (error.response && error.response.statusCode === 404) {
                    return;
                }
                throw this.wrapK8sClientError(error);
            }
            throw new Error('ERR_GET_DEPLOYMENT');
        });
    }
    createIngress(ingress, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const networkingV1Api = this.kubeConfig.makeApiClient(client_node_1.NetworkingV1Api);
            try {
                (_a = ingress.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield networkingV1Api.createNamespacedIngress(namespace, ingress);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    isIngressExist(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const networkingV1Api = this.kubeConfig.makeApiClient(client_node_1.NetworkingV1Api);
            try {
                yield networkingV1Api.readNamespacedIngress(name, namespace);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                return false;
            }
        });
    }
    createCustomResourceDefinition(crd) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.ApiextensionsV1Api);
            try {
                yield k8sApi.createCustomResourceDefinition(crd);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceCustomResourceDefinition(crd) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.ApiextensionsV1Api);
            try {
                if (!crd.metadata.resourceVersion) {
                    const response = yield k8sApi.readCustomResourceDefinition(crd.metadata.name);
                    crd.metadata.resourceVersion = response.body.metadata.resourceVersion;
                }
                yield k8sApi.replaceCustomResourceDefinition(crd.metadata.name, crd);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    getCustomResourceDefinition(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.ApiextensionsV1Api);
            try {
                const { body } = yield k8sApi.readCustomResourceDefinition(name);
                return body;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    getCheCluster(namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const cheClusters = yield this.getAllCheClusters();
            return cheClusters.find(c => c.metadata.namespace === namespace);
        });
    }
    getAllCheClusters() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            for (let i = 0; i < 30; i++) {
                try {
                    return yield this.listClusterCustomObject(eclipse_che_1.EclipseChe.CHE_CLUSTER_API_GROUP, eclipse_che_1.EclipseChe.CHE_CLUSTER_API_VERSION_V2, eclipse_che_1.EclipseChe.CHE_CLUSTER_KIND_PLURAL);
                }
                catch (e) {
                    if (this.isWebhookAvailabilityError(e)) {
                        yield (0, utls_1.sleep)(5 * 1000);
                    }
                    else {
                        throw e;
                    }
                }
            }
            return [];
        });
    }
    isCheClusterAPIV2(checluster) {
        return checluster.apiVersion === `${eclipse_che_1.EclipseChe.CHE_CLUSTER_API_GROUP}/${eclipse_che_1.EclipseChe.CHE_CLUSTER_API_VERSION_V2}`;
    }
    deleteAllCustomResourcesAndCrd(crdName, apiGroup, version, plural) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a, _b, _c, _d, _e;
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            const crd = yield this.getCustomResourceDefinition(crdName);
            if (!crd) {
                return;
            }
            // 1. Disable conversion webhook
            crd.spec.conversion = null;
            // 2. Patch CRD to unblock potential invalid resource error
            for (let i = 0; i < crd.spec.versions.length; i++) {
                if ((_c = (_b = (_a = crd.spec.versions[i].schema) === null || _a === void 0 ? void 0 : _a.openAPIV3Schema) === null || _b === void 0 ? void 0 : _b.properties) === null || _c === void 0 ? void 0 : _c.spec) {
                    crd.spec.versions[i].schema.openAPIV3Schema.properties.spec = { type: 'object', properties: {} };
                }
            }
            yield this.replaceCustomResourceDefinition(crd);
            // 3. Delete resources
            let resources = yield this.listClusterCustomObject(apiGroup, version, plural);
            for (const resource of resources) {
                const name = resource.metadata.name;
                const namespace = resource.metadata.namespace;
                try {
                    yield customObjectsApi.deleteNamespacedCustomObject(apiGroup, version, namespace, plural, name, 60);
                }
                catch (_f) {
                    // ignore, check existence later
                }
            }
            // wait and check
            for (let i = 0; i < 12; i++) {
                const resources = yield this.listClusterCustomObject(apiGroup, version, plural);
                if (resources.length === 0) {
                    break;
                }
                yield core_1.ux.wait(5000);
            }
            // 4. Remove finalizers
            resources = yield this.listClusterCustomObject(apiGroup, version, plural);
            for (const resource of resources) {
                const name = resource.metadata.name;
                const namespace = resource.metadata.namespace;
                try {
                    yield this.patchNamespacedCustomObject(name, namespace, { metadata: { finalizers: null } }, apiGroup, version, plural);
                }
                catch (error) {
                    if (((_e = (_d = error.cause) === null || _d === void 0 ? void 0 : _d.body) === null || _e === void 0 ? void 0 : _e.reason) === 'NotFound') {
                        continue;
                    }
                    throw error;
                }
            }
            // 5. Remove CRD
            yield this.deleteCustomResourceDefinition(crdName);
            resources = yield this.listClusterCustomObject(apiGroup, version, plural);
            if (resources.length !== 0) {
                throw new Error(`Failed to remove Custom Resources: ${plural}${apiGroup}, ${resources.length} resource(s) left.`);
            }
        });
    }
    createNamespacedCustomObject(namespace, group, version, plural, body, handleWebhookAvailabilityError) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            if (body.apiVersion !== `${group}/${version}`) {
                throw new Error(`${body.metadata.name} Custom Object must be ${group}/${version} version`);
            }
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            (_a = body.metadata) === null || _a === void 0 ? true : delete _a.namespace;
            if (!handleWebhookAvailabilityError) {
                try {
                    yield k8sCoreApi.createNamespacedCustomObject(group, version, namespace, plural, body);
                }
                catch (e) {
                    throw this.wrapK8sClientError(e);
                }
            }
            else {
                for (let i = 0; i < 30; i++) {
                    try {
                        yield k8sCoreApi.createNamespacedCustomObject(group, version, namespace, plural, body);
                        return;
                    }
                    catch (e) {
                        const wrappedError = this.wrapK8sClientError(e);
                        if (this.isWebhookAvailabilityError(wrappedError)) {
                            yield (0, utls_1.sleep)(5 * 1000);
                        }
                        else {
                            throw wrappedError;
                        }
                    }
                }
            }
        });
    }
    listNamespacedCustomObject(resourceAPIGroup, resourceAPIVersion, namespace, resourcePlural) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return this.list(resourceAPIGroup, resourceAPIVersion, namespace, resourcePlural);
        });
    }
    listClusterCustomObject(resourceAPIGroup, resourceAPIVersion, resourcePlural) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return this.list(resourceAPIGroup, resourceAPIVersion, undefined, resourcePlural);
        });
    }
    list(resourceAPIGroup, resourceAPIVersion, namespace, resourcePlural) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            let errMsg = '';
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            for (let attempt = 1; attempt <= 10; attempt++) {
                try {
                    if (namespace === undefined) {
                        // If namespace is not specified, list cluster custom objects
                        const { body } = yield customObjectsApi.listClusterCustomObject(resourceAPIGroup, resourceAPIVersion, resourcePlural);
                        return body.items ? body.items : [];
                    }
                    else {
                        // If namespace is specified, list namespaced custom objects
                        const { body } = yield customObjectsApi.listNamespacedCustomObject(resourceAPIGroup, resourceAPIVersion, namespace, resourcePlural);
                        return body.items ? body.items : [];
                    }
                }
                catch (e) {
                    if (((_a = e.response) === null || _a === void 0 ? void 0 : _a.statusCode) === 404) {
                        return [];
                    }
                    const wrappedError = this.wrapK8sClientError(e);
                    errMsg = wrappedError.message;
                    if (this.isStorageIsReInitializingError(wrappedError) || this.isTooManyRequestsError(wrappedError)) {
                        yield core_1.ux.wait(1000);
                        continue;
                    }
                    throw wrappedError;
                }
            }
            throw new Error(`Exceeded maximum retry attempts to list cluster custom object: ${errMsg}`);
        });
    }
    isCatalogSourceExists(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield customObjectsApi.getNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'catalogsources', name);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    getCatalogSource(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                const { body } = yield customObjectsApi.getNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'catalogsources', name);
                return body;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    createCatalogSource(catalogSource, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                (_a = catalogSource.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield customObjectsApi.createNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'catalogsources', catalogSource);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    waitCatalogSource(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return this.watchAndRetryOnError(`/apis/operators.coreos.com/v1alpha1/namespaces/${namespace}/catalogsources`, `metadata.name=${name}`, (obj) => {
                if (obj) {
                    return obj;
                }
            }, `Timeout reached while waiting for "${name}" catalog source is created.`, 60);
        });
    }
    deleteCatalogSource(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield customObjectsApi.deleteNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'catalogsources', name);
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    createOperatorSubscription(subscription, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                delete subscription.metadata.namespace;
                yield customObjectsApi.createNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'subscriptions', subscription);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    getOperatorSubscriptionByPackageInNamespace(packageName, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const subs = yield this.listNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'subscriptions');
            return subs.find(sub => sub.spec.name === packageName);
        });
    }
    getOperatorSubscription(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                const { body } = yield customObjectsApi.getNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'subscriptions', name);
                return body;
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    waitInstalledCSVInSubscription(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return this.watchAndRetryOnError(`/apis/operators.coreos.com/v1alpha1/namespaces/${namespace}/subscriptions`, `metadata.name=${name}`, (obj) => {
                var _a;
                if (obj) {
                    const subscription = obj;
                    return (_a = subscription.status) === null || _a === void 0 ? void 0 : _a.installedCSV;
                }
            }, `Timeout reached while waiting for installed CSV of '${name}' subscription.`, 30);
        });
    }
    waitCSVStatusPhase(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return this.watchAndRetryOnError(`/apis/operators.coreos.com/v1alpha1/namespaces/${namespace}/clusterserviceversions`, `metadata.name=${name}`, (obj) => {
                var _a;
                if (obj) {
                    const csv = obj;
                    return (_a = csv.status) === null || _a === void 0 ? void 0 : _a.phase;
                }
            }, `Timeout reached while waiting CSV '${name}' status.`, 30);
        });
    }
    deleteOperatorSubscription(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield customObjectsApi.deleteNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'subscriptions', name);
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    waitOperatorSubscriptionReadyForApproval(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return this.watchAndRetryOnError(`/apis/operators.coreos.com/v1alpha1/namespaces/${namespace}/subscriptions`, `metadata.name=${name}`, (obj) => {
                var _a;
                if (obj) {
                    const subscription = obj;
                    if ((_a = subscription === null || subscription === void 0 ? void 0 : subscription.status) === null || _a === void 0 ? void 0 : _a.installplan) {
                        return subscription.status.installplan;
                    }
                }
            }, `Timeout reached while waiting for "${name}" subscription is ready.`, 120);
        });
    }
    approveOperatorInstallationPlan(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                const patch = {
                    spec: {
                        approved: true,
                    },
                };
                yield customObjectsApi.patchNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'installplans', name, patch, undefined, undefined, undefined, { headers: { 'Content-Type': 'application/merge-patch+json' } });
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    waitOperatorInstallPlan(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return this.watchAndRetryOnError(`/apis/operators.coreos.com/v1alpha1/namespaces/${namespace}/installplans`, `metadata.name=${name}`, (obj) => {
                var _a, _b;
                if (obj) {
                    const installPlan = obj;
                    if (((_a = installPlan.status) === null || _a === void 0 ? void 0 : _a.phase) === 'Failed') {
                        const errorMessage = [];
                        for (const condition of installPlan.status.conditions) {
                            if (!condition.reason) {
                                errorMessage.push(`Reason: ${condition.reason}`, !condition.message ? `Message: ${condition.message}` : '');
                            }
                        }
                        throw new Error(errorMessage.join(' '));
                    }
                    if ((_b = installPlan.status) === null || _b === void 0 ? void 0 : _b.conditions) {
                        for (const condition of installPlan.status.conditions) {
                            if (condition.type === 'Installed' && condition.status === 'True') {
                                return installPlan;
                            }
                        }
                    }
                }
            }, `Timeout reached while waiting for "${name}" has status 'Installed'.`, 180);
        });
    }
    getCSV(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                const { body } = yield customObjectsApi.getNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'clusterserviceversions', name);
                return body;
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    getCSVWithPrefix(namePrefix, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const csvs = yield this.listNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'clusterserviceversions');
            return csvs.filter(csv => csv.metadata.name.startsWith(namePrefix));
        });
    }
    patchClusterServiceVersion(name, namespace, jsonPatch) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            const requestOptions = {
                headers: {
                    'content-type': 'application/json-patch+json',
                },
            };
            try {
                const response = yield customObjectsApi.patchNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'clusterserviceversions', name, jsonPatch, undefined, undefined, undefined, requestOptions);
                return response.body;
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteClusterServiceVersion(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield customObjectsApi.deleteNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'clusterserviceversions', name);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    deleteCustomResourceDefinition(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.ApiextensionsV1Api);
            try {
                yield k8sApi.deleteCustomResourceDefinition(name);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    deleteNamespace(namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sCoreApi.deleteNamespace(namespace);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    deleteCertificate(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                // If cluster certificates doesn't exist an exception will be thrown
                yield customObjectsApi.deleteNamespacedCustomObject('cert-manager.io', 'v1', namespace, 'certificates', name);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    deleteIssuer(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield customObjectsApi.deleteNamespacedCustomObject('cert-manager.io', 'v1', namespace, 'issuers', name);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    createCertificate(certificate, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                (_a = certificate.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield customObjectsApi.createNamespacedCustomObject('cert-manager.io', 'v1', namespace, 'certificates', certificate);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceCertificate(name, certificate, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                const response = yield customObjectsApi.getNamespacedCustomObject('cert-manager.io', 'v1', namespace, 'certificates', name);
                certificate.metadata.resourceVersion = response.body.metadata.resourceVersion;
                (_a = certificate.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield customObjectsApi.replaceNamespacedCustomObject('cert-manager.io', 'v1', namespace, 'certificates', name, certificate);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    isCertificateExists(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield customObjectsApi.getNamespacedCustomObject('cert-manager.io', 'v1', namespace, 'certificates', name);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    createIssuer(issuer, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                (_a = issuer.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield customObjectsApi.createNamespacedCustomObject('cert-manager.io', 'v1', namespace, 'issuers', issuer);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    replaceIssuer(name, issuer, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                const response = yield customObjectsApi.getNamespacedCustomObject('cert-manager.io', 'v1', namespace, 'issuers', name);
                issuer.metadata.resourceVersion = response.body.metadata.resourceVersion;
                (_a = issuer.metadata) === null || _a === void 0 ? true : delete _a.namespace;
                yield customObjectsApi.replaceNamespacedCustomObject('cert-manager.io', 'v1', namespace, 'issuers', name, issuer);
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    isIssuerExists(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield customObjectsApi.getNamespacedCustomObject('cert-manager.io', 'v1', namespace, 'issuers', name);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    deleteOperator(name) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield customObjectsApi.deleteClusterCustomObject('operators.coreos.com', 'v1', 'operators', name);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    deleteLease(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const customObjectsApi = this.kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                yield customObjectsApi.deleteNamespacedCustomObject('coordination.k8s.io', 'v1', namespace, 'leases', name);
            }
            catch (e) {
                if (e.response.statusCode !== 404) {
                    throw this.wrapK8sClientError(e);
                }
            }
        });
    }
    getIngressHost(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const networkingV1Api = this.kubeConfig.makeApiClient(client_node_1.NetworkingV1Api);
            try {
                const res = yield networkingV1Api.readNamespacedIngress(name, namespace);
                if (res && res.body &&
                    res.body.spec &&
                    res.body.spec.rules &&
                    res.body.spec.rules.length > 0) {
                    return res.body.spec.rules[0].host || '';
                }
                throw new Error('ERR_INGRESS_NO_HOST');
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    getSecret(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            // now get the matching secrets
            try {
                const res = yield k8sCoreApi.readNamespacedSecret(name, namespace);
                return res && res.body && res.body ? res.body : undefined;
            }
            catch (_a) {
                return;
            }
        });
    }
    isSecretExists(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                yield k8sCoreApi.readNamespacedSecret(name, namespace);
                return true;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return false;
                }
                throw this.wrapK8sClientError(e);
            }
        });
    }
    /**
     * Creates a secret with given name and data.
     * Data should not be base64 encoded.
     */
    createSecret(name, namespace, data) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sCoreApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            const secret = new client_node_1.V1Secret();
            secret.metadata = new client_node_1.V1ObjectMeta();
            secret.metadata.name = name;
            secret.metadata.namespace = namespace;
            secret.stringData = data;
            try {
                return (yield k8sCoreApi.createNamespacedSecret(namespace, secret)).body;
            }
            catch (_a) {
                return;
            }
        });
    }
    /**
     * Awaits secret to be present and contain non-empty data fields specified in dataKeys parameter.
     */
    waitSecret(name_1, namespace_1) {
        return tslib_1.__awaiter(this, arguments, void 0, function* (name, namespace, dataKeys = []) {
            return new Promise((resolve, reject) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                // Set up watcher
                const watcher = new client_node_1.Watch(this.kubeConfig);
                const request = yield watcher
                    .watch(`/api/v1/namespaces/${namespace}/secrets/`, { fieldSelector: `metadata.name=${name}` }, (_phase, obj) => {
                    const secret = obj;
                    // Check all required data fields to be present
                    if (dataKeys.length > 0 && secret.data) {
                        for (const key of dataKeys) {
                            if (!secret.data[key]) {
                                // Key is missing or empty
                                return;
                            }
                        }
                    }
                    // The secret with all specified fields is present, stop watching
                    if (request) {
                        request.abort();
                    }
                    // Release awaiter
                    resolve();
                }, error => {
                    if (error) {
                        reject(error);
                    }
                });
                // Automatically stop watching after timeout
                const timeoutHandler = setTimeout(() => {
                    request.abort();
                    reject(`Timeout reached while waiting for "${name}" secret.`);
                }, 30 * 1000);
                // Request secret, for case if it is already exist
                const secret = yield this.getSecret(name, namespace);
                if (secret) {
                    // Stop watching
                    request.abort();
                    clearTimeout(timeoutHandler);
                    // Relese awaiter
                    resolve();
                }
            }));
        });
    }
    listNamespacedPod(namespace, fieldSelector, labelSelector) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const res = yield k8sApi.listNamespacedPod(namespace, undefined, undefined, undefined, fieldSelector, labelSelector);
                return res && res.body ? res.body : {
                    items: [],
                };
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    listNamespacedEvent(namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const k8sApi = this.kubeConfig.makeApiClient(client_node_1.CoreV1Api);
            try {
                const res = yield k8sApi.listNamespacedEvent(namespace);
                return res && res.body ? res.body : {
                    items: [],
                };
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    watchNamespacedEvents(namespace, callback, onError) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const watcher = new client_node_1.Watch(this.kubeConfig);
            yield watcher.watch(`/api/v1/namespaces/${namespace}/events`, {}, (_phase, obj) => {
                callback(obj);
            }, err => {
                if (onError) {
                    onError(err);
                }
            });
        });
    }
    /**
     * Reads log by chunk and writes into a file.
     */
    readNamespacedPodLog(pod, namespace, container, filename, follow) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return new Promise((resolve, reject) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const logHelper = new client_node_1.Log(this.kubeConfig);
                const stream = new node_stream_1.Writable();
                stream._write = function (chunk, encoding, done) {
                    fs.appendFileSync(filename, chunk, { encoding });
                    done();
                };
                yield logHelper.log(namespace, pod, container, stream, error => {
                    stream.end();
                    if (error) {
                        reject(error);
                    }
                    else {
                        resolve();
                    }
                }, { follow });
            }));
        });
    }
    /**
     * Forwards port, based on the example
     * https://github.com/kubernetes-client/javascript/blob/master/examples/typescript/port-forward/port-forward.ts
     */
    portForward(podName, namespace, port) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const portForwardHelper = new client_node_1.PortForward(this.kubeConfig, true);
            try {
                const server = net.createServer((socket) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                    yield portForwardHelper.portForward(namespace, podName, [port], socket, null, socket);
                }));
                server.listen(port, 'localhost');
                return;
            }
            catch (e) {
                throw this.wrapK8sClientError(e);
            }
        });
    }
    watch(path, fieldSelector, processObj, errMsg, timeout) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            let timeoutHandler;
            return new Promise((resolve, reject) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const watcher = new client_node_1.Watch(this.kubeConfig);
                const request = yield watcher.watch(path, { fieldSelector }, (_phase, obj) => {
                    const result = processObj(obj);
                    if (result) {
                        request.response.destroy();
                        resolve(result);
                    }
                }, error => {
                    if (timeoutHandler) {
                        clearTimeout(timeoutHandler);
                    }
                    if (error) {
                        reject(error);
                    }
                });
                timeoutHandler = setTimeout(() => {
                    request.abort();
                    reject(new Error(errMsg));
                }, timeout * 1000);
            }));
        });
    }
    watchAndRetryOnError(path, fieldSelector, processObj, errMsg, timeout) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            for (let attempt = 1; attempt <= 5; attempt++) {
                try {
                    return yield this.watch(path, fieldSelector, processObj, errMsg, timeout);
                }
                catch (e) {
                    const wrappedError = this.wrapK8sClientError(e);
                    if (this.isTooManyRequestsError(wrappedError)) {
                        yield core_1.ux.wait(2000);
                        continue;
                    }
                    throw wrappedError;
                }
            }
            throw new Error('Exceeded maximum retry attempts: TooManyRequests Error');
        });
    }
    wrapK8sClientError(e) {
        if (e.response && e.response.body) {
            if (e.response.body.message) {
                return (0, utls_1.newError)(e.response.body.message, e);
            }
            return (0, utls_1.newError)(e.response.body, e);
        }
        return e;
    }
    isWebhookAvailabilityError(error) {
        const msg = error.message;
        return msg.includes(`service "${eclipse_che_1.EclipseChe.CHE_FLAVOR}-operator-service" not found`) ||
            msg.includes(`no endpoints available for service "${eclipse_che_1.EclipseChe.CHE_FLAVOR}-operator-service"`) ||
            msg.includes('failed calling webhook') ||
            msg.includes('conversion webhook');
    }
    isStorageIsReInitializingError(error) {
        const msg = error.message;
        return msg !== undefined && msg.includes('storage is (re)initializing');
    }
    isTooManyRequestsError(error) {
        const msg = error.message;
        return msg !== undefined && (msg.includes('TooManyRequests') || (msg.includes('Too Many Requests')));
    }
}
exports.KubeClient = KubeClient;
class PatchedK8sAppsApi extends client_node_1.AppsV1Api {
    patchNamespacedDeployment(...args) {
        const oldDefaultHeaders = this.defaultHeaders;
        this.defaultHeaders = Object.assign({ 'Content-Type': 'application/strategic-merge-patch+json' }, this.defaultHeaders);
        const returnValue = super.patchNamespacedDeployment.apply(this, args);
        this.defaultHeaders = oldDefaultHeaders;
        return returnValue;
    }
    patchNamespacedDeploymentScale(...args) {
        const oldDefaultHeaders = this.defaultHeaders;
        this.defaultHeaders = Object.assign({ 'Content-Type': 'application/strategic-merge-patch+json' }, this.defaultHeaders);
        const returnValue = super.patchNamespacedDeploymentScale.apply(this, args);
        this.defaultHeaders = oldDefaultHeaders;
        return returnValue;
    }
}
//# sourceMappingURL=kube-client.js.map