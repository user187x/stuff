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
exports.CheCtlContext = exports.OperatorImageUpgradeContext = exports.KubeHelperContext = exports.DevWorkspaceContext = exports.EclipseCheContext = exports.DexContext = exports.OIDCContext = exports.CliContext = exports.InfrastructureContext = void 0;
const tslib_1 = require("tslib");
const client_node_1 = require("@kubernetes/client-node");
const os = require("node:os");
const path = require("node:path");
const flags_1 = require("./flags");
const utls_1 = require("./utils/utls");
const dev_workspace_1 = require("./tasks/installers/dev-workspace/dev-workspace");
const eclipse_che_1 = require("./tasks/installers/eclipse-che/eclipse-che");
const fs = require("fs-extra");
const execaModule = require("execa");
// Support both CJS (execa is the function) and ESM interop (execa.default)
const execa = typeof execaModule === 'function' ? execaModule : execaModule.default;
var InfrastructureContext;
(function (InfrastructureContext) {
    InfrastructureContext.IS_OPENSHIFT = 'infrastructure-is-openshift';
    InfrastructureContext.OPENSHIFT_VERSION = 'infrastructure-openshift-version';
    InfrastructureContext.KUBERNETES_VERSION = 'infrastructure-kubernetes-version';
    InfrastructureContext.OPENSHIFT_ARCH = 'infrastructure-openshift-arch';
    InfrastructureContext.OPENSHIFT_OPERATOR_NAMESPACE = 'openshift-operator-namespace';
    InfrastructureContext.OPENSHIFT_MARKETPLACE_NAMESPACE = 'openshift-marketplace-namespace';
})(InfrastructureContext || (exports.InfrastructureContext = InfrastructureContext = {}));
var CliContext;
(function (CliContext) {
    CliContext.CLI_COMMAND_FLAGS = 'cli-command-flags';
    CliContext.CLI_COMMAND_START_TIME = 'cli-command-start-time';
    CliContext.CLI_COMMAND_END_TIME = 'cli-command-end-time';
    CliContext.CLI_COMMAND_ID = 'cli-command-id';
    CliContext.CLI_CONFIG_DIR = 'cli-config-dir';
    CliContext.CLI_CACHE_DIR = 'cli-cache-dir';
    CliContext.CLI_ERROR_LOG = 'cli-error-log';
    CliContext.CLI_COMMAND_LOGS_DIR = 'cli-logs-log';
    CliContext.CLI_IS_DEV_VERSION = 'cli-dev-version';
    CliContext.CLI_IS_CHECTL = 'cli-is-chectl';
    CliContext.CLI_CHE_OPERATOR_RESOURCES_DIR = 'cli-che-operator-resources-dir';
    CliContext.CLI_DEV_WORKSPACE_OPERATOR_RESOURCES_DIR = 'cli-dev-workspace-operator-resources-dir';
    CliContext.CLI_COMMAND_POST_OUTPUT_MESSAGES = 'cli-messages';
})(CliContext || (exports.CliContext = CliContext = {}));
var OIDCContext;
(function (OIDCContext) {
    OIDCContext.ISSUER_URL = 'oidc-issuer-url';
    OIDCContext.CLIENT_ID = 'oidc-client-id';
    OIDCContext.CA_FILE = 'oidc-ca-file';
})(OIDCContext || (exports.OIDCContext = OIDCContext = {}));
var DexContext;
(function (DexContext) {
    DexContext.DEX_CA_CRT = 'dex-ca.crt';
    DexContext.DEX_USERNAME = 'dex-username';
    DexContext.DEX_PASSWORD = 'dex-password';
    DexContext.DEX_PASSWORD_HASH = 'dex-password-hash';
})(DexContext || (exports.DexContext = DexContext = {}));
var EclipseCheContext;
(function (EclipseCheContext) {
    EclipseCheContext.CHANNEL = 'eclipse-che-channel';
    EclipseCheContext.CATALOG_SOURCE_NAME = 'eclipse-che-catalog-source-name';
    EclipseCheContext.CATALOG_SOURCE_IMAGE = 'eclipse-che-catalog-source-image';
    EclipseCheContext.CATALOG_SOURCE_NAMESPACE = 'eclipse-che-catalog-source-namespace';
    EclipseCheContext.PACKAGE_NAME = 'eclipse-che-package-name';
    EclipseCheContext.APPROVAL_STRATEGY = 'eclipse-che-approval-strategy';
    EclipseCheContext.CUSTOM_CR = 'eclipse-che-custom-cr';
    EclipseCheContext.CR_PATCH = 'eclipse-che-cr-patch';
    EclipseCheContext.DEFAULT_CR = 'eclipse-che-default-cr';
    EclipseCheContext.NAMESPACE = 'eclipse-che-namespace';
    EclipseCheContext.OPERATOR_NAMESPACE = 'eclipse-che-operator-namespace';
    EclipseCheContext.CREATE_CATALOG_SOURCE_AND_SUBSCRIPTION = 'eclipse-che-create-catalog-source-and-subscription';
})(EclipseCheContext || (exports.EclipseCheContext = EclipseCheContext = {}));
var DevWorkspaceContext;
(function (DevWorkspaceContext) {
    DevWorkspaceContext.CATALOG_SOURCE_NAME = 'dev-workspace-catalog-source-name';
    DevWorkspaceContext.CATALOG_SOURCE_IMAGE = 'dev-workspace-catalog-source-image';
    DevWorkspaceContext.CHANNEL = 'dev-workspace-install-plan';
    DevWorkspaceContext.NAMESPACE = 'dev-workspace-namespace';
})(DevWorkspaceContext || (exports.DevWorkspaceContext = DevWorkspaceContext = {}));
var KubeHelperContext;
(function (KubeHelperContext) {
    KubeHelperContext.POD_WAIT_TIMEOUT = 'kube-pod-wait-timeout';
    KubeHelperContext.POD_READY_TIMEOUT = 'kube-pod-ready-timeout';
    KubeHelperContext.POD_READY_TIMEOUT_EMBEDDED_PLUGIN_REGISTRY = 'kube-pod-ready-timeout-embedded-plugin-registry';
    KubeHelperContext.POD_DOWNLOAD_IMAGE_TIMEOUT = 'kube-pod-download-image-timeout';
    KubeHelperContext.POD_ERROR_RECHECK_TIMEOUT = 'kube-pod-error-recheck-timeout';
})(KubeHelperContext || (exports.KubeHelperContext = KubeHelperContext = {}));
var OperatorImageUpgradeContext;
(function (OperatorImageUpgradeContext) {
    OperatorImageUpgradeContext.NEW_IMAGE = 'operator-image-new';
    OperatorImageUpgradeContext.NEW_IMAGE_NAME = 'operator-image-name-new';
    OperatorImageUpgradeContext.NEW_IMAGE_TAG = 'operator-image-tag-new';
    OperatorImageUpgradeContext.DEPLOYED_IMAGE = 'operator-image-deployed';
    OperatorImageUpgradeContext.DEPLOYED_IMAGE_NAME = 'operator-image-name-deployed';
    OperatorImageUpgradeContext.DEPLOYED_IMAGE_TAG = 'operator-image-tag-deployed';
})(OperatorImageUpgradeContext || (exports.OperatorImageUpgradeContext = OperatorImageUpgradeContext = {}));
/**
 * chectl command context.
 * Can be requested from any location with `ChectlContext#get`
 */
var CheCtlContext;
(function (CheCtlContext) {
    const ctx = {};
    const CHE_OPERATOR_TEMPLATE_DIR = 'che-operator';
    const DEV_WORKSPACE_OPERATOR_TEMPLATE_DIR = 'devworkspace-operator';
    function init(flags, command) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            // CLI context
            ctx[CliContext.CLI_COMMAND_FLAGS] = flags;
            ctx[CliContext.CLI_IS_CHECTL] = (0, utls_1.isCheFlavor)();
            ctx[CliContext.CLI_IS_DEV_VERSION] = (0, utls_1.getProjectVersion)().includes('next') || (0, utls_1.getProjectVersion)() === '0.0.2';
            ctx[CliContext.CLI_COMMAND_START_TIME] = Date.now();
            ctx[CliContext.CLI_CONFIG_DIR] = command.config.configDir;
            ctx[CliContext.CLI_CACHE_DIR] = command.config.cacheDir;
            ctx[CliContext.CLI_ERROR_LOG] = command.config.errlog;
            ctx[CliContext.CLI_COMMAND_ID] = command.id;
            ctx[CliContext.CLI_COMMAND_LOGS_DIR] = path.resolve(flags[flags_1.LOG_DIRECTORY_FLAG] ? flags[flags_1.LOG_DIRECTORY_FLAG] : path.resolve(os.tmpdir(), 'chectl-logs', Date.now().toString()));
            ctx[CliContext.CLI_COMMAND_POST_OUTPUT_MESSAGES] = [];
            if (flags[flags_1.TEMPLATES_FLAG]) {
                if (path.basename(flags[flags_1.TEMPLATES_FLAG]) !== CHE_OPERATOR_TEMPLATE_DIR) {
                    ctx[CliContext.CLI_CHE_OPERATOR_RESOURCES_DIR] = path.join(flags[flags_1.TEMPLATES_FLAG], CHE_OPERATOR_TEMPLATE_DIR);
                    ctx[CliContext.CLI_DEV_WORKSPACE_OPERATOR_RESOURCES_DIR] = path.join(flags[flags_1.TEMPLATES_FLAG], DEV_WORKSPACE_OPERATOR_TEMPLATE_DIR);
                }
                else {
                    ctx[CliContext.CLI_CHE_OPERATOR_RESOURCES_DIR] = flags[flags_1.TEMPLATES_FLAG];
                    ctx[CliContext.CLI_DEV_WORKSPACE_OPERATOR_RESOURCES_DIR] = path.normalize(path.join(flags[flags_1.TEMPLATES_FLAG], '..', DEV_WORKSPACE_OPERATOR_TEMPLATE_DIR));
                }
            }
            else {
                // Use build-in templates if neither custom templates no version to deploy specified.
                // All flavors should use embedded templates if not custom templates is given.
                ctx[CliContext.CLI_CHE_OPERATOR_RESOURCES_DIR] = path.join((0, utls_1.getEmbeddedTemplatesDirectory)(), CHE_OPERATOR_TEMPLATE_DIR);
                ctx[CliContext.CLI_DEV_WORKSPACE_OPERATOR_RESOURCES_DIR] = path.join((0, utls_1.getEmbeddedTemplatesDirectory)(), DEV_WORKSPACE_OPERATOR_TEMPLATE_DIR);
            }
            // Infrastructure context
            ctx[InfrastructureContext.IS_OPENSHIFT] = yield isOpenShift();
            ctx[InfrastructureContext.OPENSHIFT_MARKETPLACE_NAMESPACE] = 'openshift-marketplace';
            if (ctx[InfrastructureContext.IS_OPENSHIFT]) {
                ctx[InfrastructureContext.OPENSHIFT_ARCH] = yield getOpenShiftArch();
                ctx[InfrastructureContext.OPENSHIFT_VERSION] = yield getOpenShiftVersion();
                ctx[InfrastructureContext.OPENSHIFT_OPERATOR_NAMESPACE] = 'openshift-operators';
            }
            ctx[InfrastructureContext.KUBERNETES_VERSION] = yield getKubernetesVersion(ctx[InfrastructureContext.IS_OPENSHIFT]);
            ctx[EclipseCheContext.NAMESPACE] = flags[flags_1.CHE_NAMESPACE_FLAG] || (yield findCheClusterNamespace()) || eclipse_che_1.EclipseChe.NAMESPACE;
            // for backward compatability
            flags[flags_1.CHE_NAMESPACE_FLAG] = ctx[EclipseCheContext.NAMESPACE];
            if (ctx[InfrastructureContext.IS_OPENSHIFT]) {
                ctx[EclipseCheContext.OPERATOR_NAMESPACE] = process.env[`${(0, utls_1.getProjectName)().toUpperCase()}_OPERATOR_NAMESPACE`] || ctx[InfrastructureContext.OPENSHIFT_OPERATOR_NAMESPACE];
            }
            else {
                ctx[EclipseCheContext.OPERATOR_NAMESPACE] = process.env[`${(0, utls_1.getProjectName)().toUpperCase()}_OPERATOR_NAMESPACE`] || ctx[EclipseCheContext.NAMESPACE];
            }
            // Eclipse Che context
            ctx[EclipseCheContext.CUSTOM_CR] = readFile(flags, flags_1.CHE_OPERATOR_CR_YAML_FLAG);
            ctx[EclipseCheContext.CR_PATCH] = readFile(flags, flags_1.CHE_OPERATOR_CR_PATCH_YAML_FLAG);
            ctx[EclipseCheContext.DEFAULT_CR] = (0, utls_1.safeLoadFromYamlFile)(path.join(ctx[CliContext.CLI_CHE_OPERATOR_RESOURCES_DIR], 'kubernetes', 'crds', 'org_checluster_cr.yaml'));
            ctx[EclipseCheContext.APPROVAL_STRATEGY] = flags[flags_1.AUTO_UPDATE_FLAG] ? eclipse_che_1.EclipseChe.APPROVAL_STRATEGY_AUTOMATIC : eclipse_che_1.EclipseChe.APPROVAL_STRATEGY_MANUAL;
            ctx[EclipseCheContext.CHANNEL] = flags[flags_1.OLM_CHANNEL_FLAG];
            if (!ctx[EclipseCheContext.CHANNEL]) {
                ctx[EclipseCheContext.CHANNEL] = ctx[CliContext.CLI_IS_DEV_VERSION] ? eclipse_che_1.EclipseChe.NEXT_CHANNEL : eclipse_che_1.EclipseChe.STABLE_CHANNEL;
            }
            else if (!ctx[CliContext.CLI_IS_CHECTL] && ctx[EclipseCheContext.CHANNEL] !== eclipse_che_1.EclipseChe.STABLE_CHANNEL) {
                // It is always EclipseChe.NEXT_CHANNEL, 'latest` and 'next` are added only to simplify UI
                // See https://issues.redhat.com/browse/CRW-3877
                ctx[EclipseCheContext.CHANNEL] = eclipse_che_1.EclipseChe.NEXT_CHANNEL;
            }
            ctx[EclipseCheContext.PACKAGE_NAME] = flags[flags_1.PACKAGE_MANIFEST_FLAG] || eclipse_che_1.EclipseChe.PACKAGE;
            ctx[EclipseCheContext.CATALOG_SOURCE_NAMESPACE] = flags[flags_1.CATALOG_SOURCE_NAMESPACE_FLAG] || ctx[InfrastructureContext.OPENSHIFT_MARKETPLACE_NAMESPACE];
            ctx[EclipseCheContext.CATALOG_SOURCE_NAME] = flags[flags_1.CATALOG_SOURCE_NAME_FLAG];
            if (!ctx[EclipseCheContext.CATALOG_SOURCE_NAME]) {
                ctx[EclipseCheContext.CATALOG_SOURCE_NAME] = ctx[EclipseCheContext.CHANNEL] === eclipse_che_1.EclipseChe.STABLE_CHANNEL ? eclipse_che_1.EclipseChe.STABLE_CHANNEL_CATALOG_SOURCE : eclipse_che_1.EclipseChe.NEXT_CHANNEL_CATALOG_SOURCE;
            }
            ctx[EclipseCheContext.CATALOG_SOURCE_IMAGE] = flags[flags_1.CATALOG_SOURCE_IMAGE_FLAG];
            if (ctx[EclipseCheContext.CATALOG_SOURCE_IMAGE]) {
                ctx[EclipseCheContext.CATALOG_SOURCE_NAMESPACE] = ctx[InfrastructureContext.OPENSHIFT_MARKETPLACE_NAMESPACE];
                ctx[EclipseCheContext.CATALOG_SOURCE_NAME] = ctx[EclipseCheContext.CATALOG_SOURCE_IMAGE].replaceAll(' ', '-').toLowerCase();
            }
            else {
                if (ctx[EclipseCheContext.CHANNEL] !== eclipse_che_1.EclipseChe.STABLE_CHANNEL) {
                    if (ctx[CliContext.CLI_IS_CHECTL]) {
                        ctx[EclipseCheContext.CATALOG_SOURCE_IMAGE] = eclipse_che_1.EclipseChe.NEXT_CATALOG_SOURCE_IMAGE;
                    }
                    else {
                        let iibImageTag = 'next';
                        if (flags[flags_1.OLM_CHANNEL_FLAG] === 'latest') {
                            iibImageTag = 'latest';
                        }
                        ctx[EclipseCheContext.CATALOG_SOURCE_IMAGE] = `quay.io/devspaces/iib:${iibImageTag}-v${ctx[InfrastructureContext.OPENSHIFT_VERSION]}-${ctx[InfrastructureContext.OPENSHIFT_ARCH]}`;
                    }
                }
                else {
                    const catalogSource = yield getCatalogSource(ctx[EclipseCheContext.CATALOG_SOURCE_NAME], ctx[EclipseCheContext.CATALOG_SOURCE_NAMESPACE]);
                    ctx[EclipseCheContext.CATALOG_SOURCE_IMAGE] = catalogSource === null || catalogSource === void 0 ? void 0 : catalogSource.spec.image;
                }
            }
            if (flags[flags_1.CATALOG_SOURCE_YAML_FLAG]) {
                const catalogSource = (0, utls_1.safeLoadFromYamlFile)(flags[flags_1.CATALOG_SOURCE_YAML_FLAG]);
                ctx[EclipseCheContext.CATALOG_SOURCE_NAME] = catalogSource.metadata.name;
                ctx[EclipseCheContext.CATALOG_SOURCE_NAMESPACE] = catalogSource.metadata.namespace;
                ctx[EclipseCheContext.CATALOG_SOURCE_IMAGE] = catalogSource.spec.image;
            }
            // DevWorkspaceContext
            if (ctx[EclipseCheContext.CHANNEL] === eclipse_che_1.EclipseChe.NEXT_CHANNEL) {
                ctx[DevWorkspaceContext.CHANNEL] = dev_workspace_1.DevWorkspace.NEXT_CHANNEL;
                ctx[DevWorkspaceContext.CATALOG_SOURCE_NAME] = dev_workspace_1.DevWorkspace.NEXT_CHANNEL_CATALOG_SOURCE;
                ctx[DevWorkspaceContext.CATALOG_SOURCE_IMAGE] = dev_workspace_1.DevWorkspace.NEXT_CHANNEL_CATALOG_SOURCE_IMAGE;
                if (!(0, utls_1.isCheFlavor)()) {
                    // Use the same IIB catalog source
                    ctx[DevWorkspaceContext.CATALOG_SOURCE_NAME] = ctx[EclipseCheContext.CATALOG_SOURCE_NAME];
                }
            }
            else {
                ctx[DevWorkspaceContext.CHANNEL] = dev_workspace_1.DevWorkspace.STABLE_CHANNEL;
                ctx[DevWorkspaceContext.CATALOG_SOURCE_NAME] = dev_workspace_1.DevWorkspace.STABLE_CHANNEL_CATALOG_SOURCE;
                ctx[DevWorkspaceContext.CATALOG_SOURCE_IMAGE] = dev_workspace_1.DevWorkspace.STABLE_CHANNEL_CATALOG_SOURCE_IMAGE;
            }
            ctx[DevWorkspaceContext.NAMESPACE] = ctx[InfrastructureContext.IS_OPENSHIFT] ? ctx[InfrastructureContext.OPENSHIFT_OPERATOR_NAMESPACE] : dev_workspace_1.DevWorkspace.KUBERNETES_NAMESPACE;
            // KubeHelperContext
            ctx[KubeHelperContext.POD_WAIT_TIMEOUT] = Number.parseInt(flags[flags_1.K8S_POD_WAIT_TIMEOUT_FLAG] || flags_1.DEFAULT_POD_WAIT_TIMEOUT, 10);
            ctx[KubeHelperContext.POD_READY_TIMEOUT] = Number.parseInt(flags[flags_1.K8S_POD_READY_TIMEOUT_FLAG] || flags_1.DEFAULT_K8S_POD_READY_TIMEOUT, 10);
            ctx[KubeHelperContext.POD_READY_TIMEOUT_EMBEDDED_PLUGIN_REGISTRY] = Math.max(ctx[KubeHelperContext.POD_READY_TIMEOUT], Number.parseInt(flags_1.DEFAULT_K8S_POD_READY_TIMEOUT_EMBEDDED_PLUGIN_REGISTRY, 10));
            ctx[KubeHelperContext.POD_DOWNLOAD_IMAGE_TIMEOUT] = Number.parseInt(flags[flags_1.K8S_POD_DOWNLOAD_IMAGE_TIMEOUT_FLAG] || flags_1.DEFAULT_K8S_POD_DOWNLOAD_IMAGE_TIMEOUT, 10);
            ctx[KubeHelperContext.POD_ERROR_RECHECK_TIMEOUT] = Number.parseInt(flags[flags_1.K8S_POD_ERROR_RECHECK_TIMEOUT_FLAG] || flags_1.DEFAULT_K8S_POD_ERROR_RECHECK_TIMEOUT, 10);
        });
    }
    CheCtlContext.init = init;
    function initAndGet(flags, command) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            yield init(flags, command);
            return ctx;
        });
    }
    CheCtlContext.initAndGet = initAndGet;
    function get() {
        return ctx;
    }
    CheCtlContext.get = get;
    function getFlags() {
        return ctx[CliContext.CLI_COMMAND_FLAGS];
    }
    CheCtlContext.getFlags = getFlags;
    function isOpenShift() {
        return IsAPIGroupSupported('apps.openshift.io');
    }
    function getKubernetesVersion(isOpenShift) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { stdout } = yield execa(isOpenShift ? 'oc' : 'kubectl', ['version', '-o', 'json'], { timeout: 60000 });
            const versionOutput = JSON.parse(stdout);
            return versionOutput.serverVersion.major + '.' + versionOutput.serverVersion.minor;
        });
    }
    function getOpenShiftVersion() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { stdout } = yield execa('oc', ['version', '-o', 'json'], { timeout: 60000 });
            const versionOutput = JSON.parse(stdout);
            if (versionOutput === null || versionOutput === void 0 ? void 0 : versionOutput.openshiftVersion) {
                const version = versionOutput.openshiftVersion.match(/^\d.\d+/);
                if (version) {
                    return version[0];
                }
            }
            return '4.x';
        });
    }
    function getOpenShiftArch() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { stdout } = yield execa('oc', ['version', '-o', 'json'], { timeout: 60000 });
            const versionOutput = JSON.parse(stdout);
            return versionOutput.serverVersion.platform.replace('linux/', '').replace('amd64', 'x86_64');
        });
    }
    function IsAPIGroupSupported(name, version) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeConfig = new client_node_1.KubeConfig();
            kubeConfig.loadFromDefault();
            const k8sCoreApi = kubeConfig.makeApiClient(client_node_1.ApisApi);
            const res = yield k8sCoreApi.getAPIVersions();
            if (!res || !res.body || !res.body.groups) {
                return false;
            }
            const group = res.body.groups.find(g => g.name === name);
            if (!group) {
                return false;
            }
            return version ? Boolean(group.versions.some(v => v.version === version)) : Boolean(group);
        });
    }
    function getCatalogSource(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeConfig = new client_node_1.KubeConfig();
            kubeConfig.loadFromDefault();
            const customObjectsApi = kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
            try {
                const response = yield customObjectsApi.getNamespacedCustomObject('operators.coreos.com', 'v1alpha1', namespace, 'catalogsources', name);
                return response.body;
            }
            catch (e) {
                if (e.response && e.response.statusCode === 404) {
                    return;
                }
                throw e;
            }
        });
    }
    function findCheClusterNamespace() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const kubeConfig = new client_node_1.KubeConfig();
            kubeConfig.loadFromDefault();
            try {
                const customObjectsApi = kubeConfig.makeApiClient(client_node_1.CustomObjectsApi);
                const { body } = yield customObjectsApi.listClusterCustomObject(eclipse_che_1.EclipseChe.CHE_CLUSTER_API_GROUP, eclipse_che_1.EclipseChe.CHE_CLUSTER_API_VERSION_V2, eclipse_che_1.EclipseChe.CHE_CLUSTER_KIND_PLURAL);
                return (_a = body.items[0]) === null || _a === void 0 ? void 0 : _a.metadata.namespace;
            }
            catch (_b) { }
        });
    }
    function readFile(flags, key) {
        const filePath = flags[key];
        if (!filePath) {
            return;
        }
        if (fs.existsSync(filePath)) {
            return (0, utls_1.safeLoadFromYamlFile)(filePath);
        }
        throw new Error(`Unable to find file defined in the flag '--${key}'`);
    }
})(CheCtlContext || (exports.CheCtlContext = CheCtlContext = {}));
//# sourceMappingURL=context.js.map