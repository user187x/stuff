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
exports.DexInstaller = exports.Dex = void 0;
const tslib_1 = require("tslib");
const client_node_1 = require("@kubernetes/client-node");
const core_1 = require("@oclif/core");
const crypto = require("node:crypto");
const fs = require("fs-extra");
const yaml = require("js-yaml");
const lodash_1 = require("lodash");
const os = require("node:os");
const path = require("node:path");
const context_1 = require("../../context");
const kube_client_1 = require("../../api/kube-client");
const utls_1 = require("../../utils/utls");
const flags_1 = require("../../flags");
const platform_tasks_1 = require("../platforms/platform-tasks");
const common_tasks_1 = require("../common-tasks");
var TemplatePlaceholders;
(function (TemplatePlaceholders) {
    TemplatePlaceholders.DOMAIN = '{{DOMAIN}}';
    TemplatePlaceholders.CHE_NAMESPACE = '{{NAMESPACE}}';
    TemplatePlaceholders.CLIENT_ID = '{{CLIENT_ID}}';
    TemplatePlaceholders.CLIENT_SECRET = '{{CLIENT_SECRET}}';
    TemplatePlaceholders.DEX_PASSWORD_HASH = '{{DEX_PASSWORD_HASH}}';
})(TemplatePlaceholders || (TemplatePlaceholders = {}));
var Dex;
(function (Dex) {
    Dex.CONFIG_MAP = 'dex-ca';
    Dex.CONFIG_MAP_LABELS = { 'app.kubernetes.io/part-of': 'che.eclipse.org', 'app.kubernetes.io/component': 'ca-bundle' };
})(Dex || (exports.Dex = Dex = {}));
class DexInstaller {
    constructor() {
        this.kubeClient = kube_client_1.KubeClient.getInstance();
    }
    getDeployTasks() {
        return {
            title: 'Install Dex',
            task: (ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)([], true);
                tasks.add({
                    title: `Create Namespace ${DexInstaller.NAMESPACE_NAME}`,
                    task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        if (yield this.kubeClient.getNamespace(DexInstaller.NAMESPACE_NAME)) {
                            task.title = `${task.title}...[Exists]`;
                        }
                        else {
                            const yamlFilePath = this.getDexResourceFilePath('namespace.yaml');
                            const namespace = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
                            yield this.kubeClient.createNamespace(namespace);
                            yield this.kubeClient.waitNamespaceActive(DexInstaller.NAMESPACE_NAME);
                            task.title = `${task.title}...[Created]`;
                        }
                    }),
                });
                tasks.add({
                    title: 'Create Certificates',
                    task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        const dexCaCertificateFilePath = this.getDexCaCertificateFilePath();
                        if (yield this.kubeClient.isSecretExists(DexInstaller.TLS_SECRET_NAME, DexInstaller.NAMESPACE_NAME)) {
                            task.title = `${task.title}...[Exists: ${dexCaCertificateFilePath}]`;
                        }
                        else {
                            let yamlFilePath = this.getDexResourceFilePath('selfsigned-issuer.yaml');
                            const saIssuer = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
                            yield this.kubeClient.createIssuer(saIssuer, DexInstaller.NAMESPACE_NAME);
                            yamlFilePath = this.getDexResourceFilePath('selfsigned-certificate.yaml');
                            const saCertificate = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
                            yield this.kubeClient.createCertificate(saCertificate, DexInstaller.NAMESPACE_NAME);
                            yield this.kubeClient.waitSecret('ca.crt', DexInstaller.NAMESPACE_NAME);
                            yamlFilePath = this.getDexResourceFilePath('issuer.yaml');
                            const issuer = yaml.load(fs.readFileSync(yamlFilePath).toString());
                            yield this.kubeClient.createIssuer(issuer, DexInstaller.NAMESPACE_NAME);
                            yamlFilePath = this.getDexResourceFilePath('certificate.yaml');
                            const certificate = yaml.load(fs.readFileSync(yamlFilePath).toString());
                            const flags = context_1.CheCtlContext.getFlags();
                            const dexDomain = 'dex.' + flags[flags_1.DOMAIN_FLAG];
                            const wildCardDexDomain = '*.' + dexDomain;
                            certificate.spec.dnsNames = [dexDomain, wildCardDexDomain];
                            yield this.kubeClient.createCertificate(certificate, DexInstaller.NAMESPACE_NAME);
                            yield this.kubeClient.waitSecret(DexInstaller.TLS_SECRET_NAME, DexInstaller.NAMESPACE_NAME);
                            task.title = `${task.title}...[Created: ${dexCaCertificateFilePath}]`;
                        }
                        const secret = yield this.kubeClient.getSecret(DexInstaller.TLS_SECRET_NAME, DexInstaller.NAMESPACE_NAME);
                        if (secret && secret.data) {
                            ctx[context_1.DexContext.DEX_CA_CRT] = (0, utls_1.base64Decode)(secret.data['ca.crt']);
                            fs.writeFileSync(dexCaCertificateFilePath, ctx[context_1.DexContext.DEX_CA_CRT]);
                        }
                        else {
                            throw new Error(`Dex certificate not found in the secret '${DexInstaller.TLS_SECRET_NAME}' in the namespace '${DexInstaller.NAMESPACE_NAME}'.`);
                        }
                    }),
                });
                tasks.add({
                    title: `Create ConfigMap ${Dex.CONFIG_MAP}`,
                    task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        const flags = context_1.CheCtlContext.getFlags();
                        const dexCa = new client_node_1.V1ConfigMap();
                        dexCa.metadata = new client_node_1.V1ObjectMeta();
                        dexCa.metadata.name = Dex.CONFIG_MAP;
                        dexCa.metadata.labels = Dex.CONFIG_MAP_LABELS;
                        dexCa.data = { 'ca.crt': ctx[context_1.DexContext.DEX_CA_CRT] };
                        if (yield this.kubeClient.isConfigMapExists(Dex.CONFIG_MAP, flags[flags_1.CHE_NAMESPACE_FLAG])) {
                            yield this.kubeClient.replaceConfigMap(Dex.CONFIG_MAP, dexCa, flags[flags_1.CHE_NAMESPACE_FLAG]);
                            task.title = `${task.title}...[Updated]`;
                        }
                        else {
                            yield this.kubeClient.createConfigMap(dexCa, flags[flags_1.CHE_NAMESPACE_FLAG]);
                            task.title = `${task.title}...[Created]`;
                        }
                    }),
                });
                tasks.add({
                    title: `Create ServiceAccount ${DexInstaller.DEX_NAME}`,
                    task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        if (yield this.kubeClient.isServiceAccountExist(DexInstaller.DEX_NAME, DexInstaller.NAMESPACE_NAME)) {
                            task.title = `${task.title}...[Exists]`;
                        }
                        else {
                            const yamlFilePath = this.getDexResourceFilePath('service-account.yaml');
                            const serviceAccount = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
                            yield this.kubeClient.createServiceAccount(serviceAccount, DexInstaller.NAMESPACE_NAME);
                            task.title = `${task.title}...[Created]`;
                        }
                    }),
                });
                tasks.add({
                    title: `Create ClusterRole ${DexInstaller.DEX_NAME}`,
                    task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        if (yield this.kubeClient.isClusterRoleExist(DexInstaller.DEX_NAME)) {
                            task.title = `${task.title}...[Exists]`;
                        }
                        else {
                            const yamlFilePath = this.getDexResourceFilePath('cluster-role.yaml');
                            const clusterRole = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
                            yield this.kubeClient.createClusterRole(clusterRole);
                            task.title = `${task.title}...[Created]`;
                        }
                    }),
                });
                tasks.add({
                    title: `Create ClusterRoleBinding ${DexInstaller.DEX_NAME}`,
                    task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        if (yield this.kubeClient.isClusterRoleBindingExist(DexInstaller.DEX_NAME)) {
                            task.title = `${task.title}...[Exists]`;
                        }
                        else {
                            const yamlFilePath = this.getDexResourceFilePath('cluster-role-binding.yaml');
                            const clusterRoleBinding = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
                            yield this.kubeClient.createClusterRoleBinding(clusterRoleBinding);
                            task.title = `${task.title}...[Created]`;
                        }
                    }),
                });
                tasks.add({
                    title: `Create Service ${DexInstaller.DEX_NAME}`,
                    task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        if (yield this.kubeClient.isServiceExists(DexInstaller.DEX_NAME, DexInstaller.NAMESPACE_NAME)) {
                            task.title = `${task.title}...[Exists]`;
                        }
                        else {
                            const yamlFilePath = this.getDexResourceFilePath('service.yaml');
                            const service = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
                            yield this.kubeClient.createService(service, DexInstaller.NAMESPACE_NAME);
                            task.title = `${task.title}...[Created]`;
                        }
                        // set service in a CR
                        ctx[context_1.EclipseCheContext.CR_PATCH] = ctx[context_1.EclipseCheContext.CR_PATCH] || {};
                        (0, lodash_1.merge)(ctx[context_1.EclipseCheContext.CR_PATCH], { spec: { networking: { auth: { identityProviderURL: 'http://dex.dex:5556' } } } });
                    }),
                });
                tasks.add({
                    title: `Create Ingress ${DexInstaller.DEX_NAME}`,
                    task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        if (yield this.kubeClient.isIngressExist(DexInstaller.DEX_NAME, DexInstaller.NAMESPACE_NAME)) {
                            task.title = `${task.title}...[Exists]`;
                        }
                        else {
                            const flags = context_1.CheCtlContext.getFlags();
                            const yamlFilePath = this.getDexResourceFilePath('ingress.yaml');
                            let yamlContent = fs.readFileSync(yamlFilePath).toString();
                            yamlContent = yamlContent.replace(new RegExp(TemplatePlaceholders.DOMAIN, 'g'), flags[flags_1.DOMAIN_FLAG]);
                            const ingress = yaml.load(yamlContent);
                            yield this.kubeClient.createIngress(ingress, DexInstaller.NAMESPACE_NAME);
                            task.title = `${task.title}...[Created]`;
                        }
                    }),
                });
                tasks.add({
                    title: 'Generate Dex username and password',
                    task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        const dexConfigMap = yield this.kubeClient.getConfigMap(DexInstaller.DEX_NAME, DexInstaller.NAMESPACE_NAME);
                        if (dexConfigMap && dexConfigMap.data) {
                            task.title = `${task.title}...[Exists]`;
                        }
                        else {
                            ctx[context_1.DexContext.DEX_USERNAME] = DexInstaller.DEX_USERNAME;
                            ctx[context_1.DexContext.DEX_PASSWORD] = DexInstaller.DEX_PASSWORD;
                            ctx[context_1.DexContext.DEX_PASSWORD_HASH] = DexInstaller.DEX_PASSWORD_HASH;
                            // create a secret to store credentials
                            const credentials = { user: DexInstaller.DEX_USERNAME, password: DexInstaller.DEX_PASSWORD };
                            yield this.kubeClient.createSecret(DexInstaller.CREDENTIALS_SECRET_NAME, DexInstaller.NAMESPACE_NAME, credentials);
                            task.title = `${task.title}...[OK: ${ctx[context_1.DexContext.DEX_USERNAME]}:${ctx[context_1.DexContext.DEX_PASSWORD]}]`;
                        }
                    }),
                });
                tasks.add({
                    title: `Create ConfigMap ${DexInstaller.DEX_NAME}`,
                    task: (ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        const dexConfigMap = yield this.kubeClient.getConfigMap(DexInstaller.DEX_NAME, DexInstaller.NAMESPACE_NAME);
                        if (dexConfigMap && dexConfigMap.data) {
                            // read client secret
                            const configYamlData = dexConfigMap.data['config.yaml'];
                            if (!configYamlData) {
                                throw new Error(`'config.yaml' not defined in the configmap '${DexInstaller.DEX_NAME}' in the namespace '${DexInstaller.NAMESPACE_NAME}'`);
                            }
                            const config = yaml.load(configYamlData);
                            const eclipseCheClient = config.staticClients.find(client => client.id === DexInstaller.CLIENT_ID);
                            if (!eclipseCheClient) {
                                core_1.ux.error(`'${DexInstaller.CLIENT_ID}' client not found in the configmap '${DexInstaller.DEX_NAME}' in the namespace '${DexInstaller.NAMESPACE_NAME}'.`, { exit: 1 });
                            }
                            // set in a CR
                            ctx[context_1.EclipseCheContext.CR_PATCH] = ctx[context_1.EclipseCheContext.CR_PATCH] || {};
                            (0, lodash_1.merge)(ctx[context_1.EclipseCheContext.CR_PATCH], { spec: { networking: { auth: { oAuthClientName: DexInstaller.CLIENT_ID, oAuthSecret: eclipseCheClient.secret } } } });
                            task.title = `${task.title}...[Exists]`;
                        }
                        else {
                            const flags = context_1.CheCtlContext.getFlags();
                            const yamlFilePath = this.getDexResourceFilePath('configmap.yaml');
                            let yamlContent = fs.readFileSync(yamlFilePath).toString();
                            yamlContent = yamlContent.replace(new RegExp(TemplatePlaceholders.DOMAIN, 'g'), flags[flags_1.DOMAIN_FLAG]);
                            yamlContent = yamlContent.replace(new RegExp(TemplatePlaceholders.CLIENT_ID, 'g'), DexInstaller.CLIENT_ID);
                            // generate client secret
                            let clientSecret = crypto.randomBytes(32).toString('base64');
                            clientSecret = 'EclipseChe';
                            yamlContent = yamlContent.replace(new RegExp(TemplatePlaceholders.CLIENT_SECRET, 'g'), clientSecret);
                            yamlContent = yamlContent.replace(new RegExp(TemplatePlaceholders.DEX_PASSWORD_HASH, 'g'), ctx[context_1.DexContext.DEX_PASSWORD_HASH]);
                            const configMap = yaml.load(yamlContent);
                            yield this.kubeClient.createConfigMap(configMap, DexInstaller.NAMESPACE_NAME);
                            // set in a CR
                            (0, lodash_1.merge)(ctx[context_1.EclipseCheContext.CR_PATCH], { spec: { networking: { auth: { oAuthClientName: DexInstaller.CLIENT_ID, oAuthSecret: clientSecret } } } });
                            task.title = `${task.title}...[Created]`;
                        }
                    }),
                });
                tasks.add({
                    title: `Create Deployment ${DexInstaller.DEX_NAME}`,
                    task: (_ctx, task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        if (yield this.kubeClient.isDeploymentExist(DexInstaller.DEX_NAME, DexInstaller.NAMESPACE_NAME)) {
                            task.title = `${task.title}...[Exists]`;
                        }
                        else {
                            const yamlFilePath = this.getDexResourceFilePath('deployment.yaml');
                            const deployment = (0, utls_1.safeLoadFromYamlFile)(yamlFilePath);
                            yield this.kubeClient.createDeployment(deployment, DexInstaller.NAMESPACE_NAME);
                            yield this.kubeClient.waitForPodReady(DexInstaller.SELECTOR, DexInstaller.NAMESPACE_NAME);
                            task.title = `${task.title}...[Created]`;
                        }
                    }),
                });
                tasks.add({
                    title: 'Configure API server',
                    task: (ctx) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                        const flags = context_1.CheCtlContext.getFlags();
                        const tasks = (0, utls_1.newListr)();
                        ctx[context_1.OIDCContext.CLIENT_ID] = DexInstaller.CLIENT_ID;
                        ctx[context_1.OIDCContext.ISSUER_URL] = `https://dex.${flags[flags_1.DOMAIN_FLAG]}`;
                        ctx[context_1.OIDCContext.CA_FILE] = this.getDexCaCertificateFilePath();
                        tasks.add(platform_tasks_1.PlatformTasks.getConfigureApiServerForDexTasks());
                        return tasks;
                    }),
                });
                return tasks;
            }),
        };
    }
    getDexCaCertificateFilePath() {
        return path.join(os.tmpdir(), DexInstaller.CA_CERTIFICATE_FILENAME);
    }
    getDexResourceFilePath(fileName) {
        return path.join((0, utls_1.getEmbeddedTemplatesDirectory)(), '..', 'resources', 'dex', fileName);
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
exports.DexInstaller = DexInstaller;
DexInstaller.DEX_USERNAME = 'admin';
DexInstaller.DEX_PASSWORD = 'admin';
DexInstaller.DEX_PASSWORD_HASH = '$2a$12$Cnptj8keBvBFuQkNebteYuGHnZRNKT6MivLrGmFRaTxrlyfEAOrSa';
DexInstaller.CLIENT_ID = 'eclipse-che';
DexInstaller.DEX_NAME = 'dex';
DexInstaller.NAMESPACE_NAME = 'dex';
DexInstaller.TLS_SECRET_NAME = 'dex.tls';
DexInstaller.CREDENTIALS_SECRET_NAME = 'dex-credentials';
DexInstaller.CA_CERTIFICATE_FILENAME = 'dex-ca.crt';
DexInstaller.SELECTOR = 'app=dex';
//# sourceMappingURL=dex-installer.js.map