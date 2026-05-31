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
exports.DEBUG_FLAG = exports.DOMAIN = exports.DOMAIN_FLAG = exports.CHE_IMAGE = exports.CHE_IMAGE_FLAG = exports.CHE_OPERATOR_IMAGE = exports.CHE_OPERATOR_IMAGE_FLAG = exports.CLUSTER_MONITORING = exports.CLUSTER_MONITORING_FLAG = exports.SKIP_VERSION_CHECK = exports.SKIP_VERSION_CHECK_FLAG = exports.TELEMETRY = exports.TELEMETRY_FLAG = exports.LOG_DIRECTORY = exports.LOG_DIRECTORY_FLAG = exports.TEMPLATES = exports.TEMPLATES_FLAG = exports.K8S_POD_ERROR_RECHECK_TIMEOUT = exports.K8S_POD_ERROR_RECHECK_TIMEOUT_FLAG = exports.DEFAULT_K8S_POD_ERROR_RECHECK_TIMEOUT = exports.K8S_POD_READY_TIMEOUT = exports.K8S_POD_READY_TIMEOUT_FLAG = exports.DEFAULT_K8S_POD_READY_TIMEOUT = exports.DEFAULT_K8S_POD_READY_TIMEOUT_EMBEDDED_PLUGIN_REGISTRY = exports.K8S_POD_DOWNLOAD_IMAGE_TIMEOUT = exports.K8S_POD_DOWNLOAD_IMAGE_TIMEOUT_FLAG = exports.DEFAULT_K8S_POD_DOWNLOAD_IMAGE_TIMEOUT = exports.K8S_POD_WAIT_TIMEOUT = exports.K8S_POD_WAIT_TIMEOUT_FLAG = exports.DEFAULT_POD_WAIT_TIMEOUT = exports.CHE_OPERATOR_CR_YAML = exports.CHE_OPERATOR_CR_YAML_FLAG = exports.ASSUME_YES = exports.ASSUME_YES_FLAG = exports.CHE_OPERATOR_CR_PATCH_YAML = exports.CHE_OPERATOR_CR_PATCH_YAML_FLAG = exports.SKIP_DEV_WORKSPACE = exports.SKIP_DEV_WORKSPACE_FLAG = exports.SKIP_CERT_MANAGER = exports.SKIP_CERT_MANAGER_FLAG = exports.SKIP_KUBE_HEALTHZ_CHECK = exports.SKIP_KUBE_HEALTHZ_CHECK_FLAG = exports.LISTR_RENDERER = exports.LISTR_RENDERER_FLAG = exports.BATCH = exports.BATCH_FLAG = exports.CHE_NAMESPACE = exports.CHE_NAMESPACE_FLAG = exports.PLATFORM = exports.PLATFORM_FLAG = void 0;
exports.INSTALLER = exports.INSTALLER_FLAG = exports.CATALOG_SOURCE_IMAGE = exports.CATALOG_SOURCE_NAME = exports.CATALOG_SOURCE_NAMESPACE = exports.CATALOG_SOURCE_YAML = exports.CATALOG_SOURCE_YAML_FLAG = exports.CATALOG_SOURCE_IMAGE_FLAG = exports.CATALOG_SOURCE_NAME_FLAG = exports.CATALOG_SOURCE_NAMESPACE_FLAG = exports.PACKAGE_MANIFEST = exports.PACKAGE_MANIFEST_FLAG = exports.OLM_CHANNEL = exports.OLM_CHANNEL_FLAG = exports.AUTO_UPDATE = exports.AUTO_UPDATE_FLAG = exports.STARTING_CSV = exports.STARTING_CSV_FLAG = exports.DESTINATION = exports.DESTINATION_FLAG = exports.DELETE_ALL = exports.DELETE_ALL_FLAG = exports.DELETE_NAMESPACE = exports.DELETE_NAMESPACE_FLAG = exports.DEBUG_PORT = exports.DEBUG_PORT_FLAG = exports.PLUGIN_REGISTRY_URL = exports.PLUGIN_REGISTRY_URL_FLAG = exports.DEVFILE_REGISTRY_URL = exports.DEVFILE_REGISTRY_URL_FLAG = exports.WORKSPACE_PVS_STORAGE_CLASS_NAME = exports.WORKSPACE_PVS_STORAGE_CLASS_NAME_FLAG = exports.DEBUG = void 0;
exports.checkFlagsCompatability = checkFlagsCompatability;
const core_1 = require("@oclif/core");
const eclipse_che_1 = require("./tasks/installers/eclipse-che/eclipse-che");
const context_1 = require("./context");
exports.PLATFORM_FLAG = 'platform';
exports.PLATFORM = core_1.Flags.string({
    char: 'p',
    description: 'Type of Kubernetes platform.',
    options: ['minikube', 'k8s', 'openshift', 'microk8s', 'docker-desktop', 'crc'],
});
exports.CHE_NAMESPACE_FLAG = 'chenamespace';
exports.CHE_NAMESPACE = core_1.Flags.string({
    char: 'n',
    description: `${eclipse_che_1.EclipseChe.PRODUCT_NAME} Kubernetes namespace.`,
    env: 'CHE_NAMESPACE',
});
exports.BATCH_FLAG = 'batch';
exports.BATCH = core_1.Flags.boolean({
    description: 'Batch mode. Running a command without end user interaction.',
    default: false,
    required: false,
});
exports.LISTR_RENDERER_FLAG = 'listr-renderer';
exports.LISTR_RENDERER = core_1.Flags.string({
    description: 'Listr renderer',
    options: ['default', 'silent', 'verbose'],
    default: 'default',
    hidden: true,
});
exports.SKIP_KUBE_HEALTHZ_CHECK_FLAG = 'skip-kubernetes-health-check';
exports.SKIP_KUBE_HEALTHZ_CHECK = core_1.Flags.boolean({
    description: 'Skip Kubernetes health check',
    default: false,
});
exports.SKIP_CERT_MANAGER_FLAG = 'skip-cert-manager';
exports.SKIP_CERT_MANAGER = core_1.Flags.boolean({
    default: false,
    description: 'Skip installing Cert Manager (Kubernetes cluster only).',
});
exports.SKIP_DEV_WORKSPACE_FLAG = 'skip-devworkspace-operator';
exports.SKIP_DEV_WORKSPACE = core_1.Flags.boolean({
    default: false,
    description: 'Skip installing Dev Workspace Operator.',
});
exports.CHE_OPERATOR_CR_PATCH_YAML_FLAG = 'che-operator-cr-patch-yaml';
exports.CHE_OPERATOR_CR_PATCH_YAML = core_1.Flags.string({
    description: 'Path to a yaml file that overrides the default values in CheCluster CR used by the operator. This parameter is used only when the installer is the \'operator\' or the \'olm\'.',
    default: '',
});
exports.ASSUME_YES_FLAG = 'yes';
exports.ASSUME_YES = core_1.Flags.boolean({
    description: 'Automatic yes to prompts; assume "yes" as answer to all prompts and run non-interactively',
    char: 'y',
    default: false,
    required: false,
    exclusive: [exports.BATCH_FLAG],
});
exports.CHE_OPERATOR_CR_YAML_FLAG = 'che-operator-cr-yaml';
exports.CHE_OPERATOR_CR_YAML = core_1.Flags.string({
    description: 'Path to a yaml file that defines a CheCluster used by the operator.',
    default: '',
});
exports.DEFAULT_POD_WAIT_TIMEOUT = '120000';
exports.K8S_POD_WAIT_TIMEOUT_FLAG = 'k8spodwaittimeout';
exports.K8S_POD_WAIT_TIMEOUT = core_1.Flags.string({
    description: 'Waiting time for Pod scheduled condition (in milliseconds)',
    default: exports.DEFAULT_POD_WAIT_TIMEOUT,
});
exports.DEFAULT_K8S_POD_DOWNLOAD_IMAGE_TIMEOUT = '1200000';
exports.K8S_POD_DOWNLOAD_IMAGE_TIMEOUT_FLAG = 'k8spoddownloadimagetimeout';
exports.K8S_POD_DOWNLOAD_IMAGE_TIMEOUT = core_1.Flags.string({
    description: 'Waiting time for Pod downloading image (in milliseconds)',
    default: exports.DEFAULT_K8S_POD_DOWNLOAD_IMAGE_TIMEOUT,
});
exports.DEFAULT_K8S_POD_READY_TIMEOUT_EMBEDDED_PLUGIN_REGISTRY = '600000';
exports.DEFAULT_K8S_POD_READY_TIMEOUT = '120000';
exports.K8S_POD_READY_TIMEOUT_FLAG = 'k8spodreadytimeout';
exports.K8S_POD_READY_TIMEOUT = core_1.Flags.string({
    description: 'Waiting time for Pod Ready condition (in milliseconds)',
    default: exports.DEFAULT_K8S_POD_READY_TIMEOUT,
});
exports.DEFAULT_K8S_POD_ERROR_RECHECK_TIMEOUT = '60000';
exports.K8S_POD_ERROR_RECHECK_TIMEOUT_FLAG = 'k8spoderrorrechecktimeout';
exports.K8S_POD_ERROR_RECHECK_TIMEOUT = core_1.Flags.string({
    description: 'Waiting time for Pod rechecking error (in milliseconds)',
    default: exports.DEFAULT_K8S_POD_ERROR_RECHECK_TIMEOUT,
});
exports.TEMPLATES_FLAG = 'templates';
exports.TEMPLATES = core_1.Flags.string({
    char: 't',
    description: 'Path to the templates folder',
    env: 'CHE_TEMPLATES_FOLDER',
});
exports.LOG_DIRECTORY_FLAG = 'directory';
exports.LOG_DIRECTORY = core_1.Flags.string({
    char: 'd',
    description: 'Directory to store logs into',
    env: 'CHE_LOGS',
});
exports.TELEMETRY_FLAG = 'telemetry';
exports.TELEMETRY = core_1.Flags.string({
    description: 'Enable or disable telemetry. This flag skips a prompt and enable/disable telemetry',
    options: ['on', 'off'],
});
exports.SKIP_VERSION_CHECK_FLAG = 'skip-version-check';
exports.SKIP_VERSION_CHECK = core_1.Flags.boolean({
    description: 'Skip minimal versions check.',
    default: false,
});
exports.CLUSTER_MONITORING_FLAG = 'cluster-monitoring';
exports.CLUSTER_MONITORING = core_1.Flags.boolean({
    default: false,
    description: `Enable cluster monitoring to scrape ${eclipse_che_1.EclipseChe.PRODUCT_NAME} metrics in Prometheus.
                    This parameter is used only when the platform is 'openshift'.`,
});
exports.CHE_OPERATOR_IMAGE_FLAG = 'che-operator-image';
exports.CHE_OPERATOR_IMAGE = core_1.Flags.string({
    description: 'Container image of the operator.',
});
exports.CHE_IMAGE_FLAG = 'cheimage';
exports.CHE_IMAGE = core_1.Flags.string({
    char: 'i',
    description: `${eclipse_che_1.EclipseChe.PRODUCT_NAME} server container image`,
    env: 'CHE_CONTAINER_IMAGE',
});
exports.DOMAIN_FLAG = 'domain';
exports.DOMAIN = core_1.Flags.string({
    char: 'b',
    description: `Domain of the Kubernetes cluster (e.g. example.k8s-cluster.com or <local-ip>.nip.io)
                    This flag makes sense only for Kubernetes family infrastructures and will be autodetected for Minikube and MicroK8s in most cases.
                    However, for Kubernetes cluster it is required to specify.
                    Please note, that just setting this flag will not likely work out of the box.
                    According changes should be done in Kubernetes cluster configuration as well.
                    In case of Openshift, domain adjustment should be done on the cluster configuration level.`,
    default: '',
});
exports.DEBUG_FLAG = 'debug';
exports.DEBUG = core_1.Flags.boolean({
    description: `'Enables the debug mode for ${eclipse_che_1.EclipseChe.PRODUCT_NAME} server. To debug ${eclipse_che_1.EclipseChe.PRODUCT_NAME} server from localhost use \'server:debug\' command.'`,
    default: false,
});
exports.WORKSPACE_PVS_STORAGE_CLASS_NAME_FLAG = 'workspace-pvc-storage-class-name';
exports.WORKSPACE_PVS_STORAGE_CLASS_NAME = core_1.Flags.string({
    description: `persistent volume(s) storage class name to use to store ${eclipse_che_1.EclipseChe.PRODUCT_NAME} workspaces data`,
    env: 'CHE_INFRA_KUBERNETES_PVC_STORAGE__CLASS__NAME',
    default: '',
});
exports.DEVFILE_REGISTRY_URL_FLAG = 'devfile-registry-url';
exports.DEVFILE_REGISTRY_URL = core_1.Flags.string({
    description: 'The URL of the external Devfile registry.',
    env: 'CHE_WORKSPACE_DEVFILE__REGISTRY__URL',
});
exports.PLUGIN_REGISTRY_URL_FLAG = 'plugin-registry-url';
exports.PLUGIN_REGISTRY_URL = core_1.Flags.string({
    description: 'The URL of the external plugin registry.',
    env: 'CHE_WORKSPACE_PLUGIN__REGISTRY__URL',
});
exports.DEBUG_PORT_FLAG = 'debug-port';
exports.DEBUG_PORT = core_1.Flags.integer({
    description: `${eclipse_che_1.EclipseChe.PRODUCT_NAME} server debug port`,
    default: 8000,
});
exports.DELETE_NAMESPACE_FLAG = 'delete-namespace';
exports.DELETE_NAMESPACE = core_1.Flags.boolean({
    description: `Indicates that a ${eclipse_che_1.EclipseChe.PRODUCT_NAME} namespace will be deleted as well`,
    default: false,
});
exports.DELETE_ALL_FLAG = 'delete-all';
exports.DELETE_ALL = core_1.Flags.boolean({
    description: `Indicates to delete ${eclipse_che_1.EclipseChe.PRODUCT_NAME} and Dev Workspace related resources`,
    default: false,
});
exports.DESTINATION_FLAG = 'destination';
exports.DESTINATION = core_1.Flags.string({
    char: 'd',
    description: `Destination where to store ${eclipse_che_1.EclipseChe.PRODUCT_NAME} self-signed CA certificate.
                    If the destination is a file (might not exist), then the certificate will be saved there in PEM format.
                    If the destination is a directory, then ${eclipse_che_1.EclipseChe.DEFAULT_CA_CERT_FILE_NAME} file will be created there with ${eclipse_che_1.EclipseChe.PRODUCT_NAME} certificate in PEM format.
                    If this option is omitted, then ${eclipse_che_1.EclipseChe.PRODUCT_NAME} certificate will be stored in a user's temporary directory as ${eclipse_che_1.EclipseChe.DEFAULT_CA_CERT_FILE_NAME}.`,
    env: 'CHE_CA_CERT_LOCATION',
    default: '',
});
exports.STARTING_CSV_FLAG = 'starting-csv';
exports.STARTING_CSV = core_1.Flags.string({
    description: `Starting cluster service version(CSV) for installation ${eclipse_che_1.EclipseChe.PRODUCT_NAME}.
                    Flags uses to set up start installation version Che.
                    For example: 'starting-csv' provided with value 'eclipse-che.v7.10.0' for stable channel.
                    Then OLM will install ${eclipse_che_1.EclipseChe.PRODUCT_NAME} with version 7.10.0.
                    Notice: this flag will be ignored with 'auto-update' flag. OLM with auto-update mode installs the latest known version.`,
});
exports.AUTO_UPDATE_FLAG = 'auto-update';
exports.AUTO_UPDATE = core_1.Flags.boolean({
    description: `Auto update approval strategy for installation ${eclipse_che_1.EclipseChe.PRODUCT_NAME}.
                    With this strategy will be provided auto-update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} without any human interaction.
                    By default this flag is enabled.`,
    allowNo: true,
    default: true,
});
exports.OLM_CHANNEL_FLAG = 'olm-channel';
exports.OLM_CHANNEL = core_1.Flags.string({
    description: `Olm channel to install ${eclipse_che_1.EclipseChe.PRODUCT_NAME}, f.e. stable.
                    If options was not set, will be used default version for package manifest.`,
});
exports.PACKAGE_MANIFEST_FLAG = 'package-manifest-name';
exports.PACKAGE_MANIFEST = core_1.Flags.string({
    description: `Package manifest name to subscribe to ${eclipse_che_1.EclipseChe.PRODUCT_NAME} OLM package manifest.`,
});
exports.CATALOG_SOURCE_NAMESPACE_FLAG = 'catalog-source-namespace';
exports.CATALOG_SOURCE_NAME_FLAG = 'catalog-source-name';
exports.CATALOG_SOURCE_IMAGE_FLAG = 'catalog-source-image';
exports.CATALOG_SOURCE_YAML_FLAG = 'catalog-source-yaml';
exports.CATALOG_SOURCE_YAML = core_1.Flags.string({
    description: `Path to a yaml file that describes custom catalog source for installation ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator.
                    Catalog source will be applied to the namespace with ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator.
                    Also you need define 'olm-channel' name and 'package-manifest-name'.`,
    dependsOn: [exports.OLM_CHANNEL_FLAG],
    exclusive: [exports.CATALOG_SOURCE_NAME_FLAG, exports.CATALOG_SOURCE_NAMESPACE_FLAG, exports.CATALOG_SOURCE_IMAGE_FLAG],
});
exports.CATALOG_SOURCE_NAMESPACE = core_1.Flags.string({
    description: `Namespace for OLM catalog source to install ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator.`,
    dependsOn: [exports.CATALOG_SOURCE_NAME_FLAG, exports.OLM_CHANNEL_FLAG],
    exclusive: [exports.CATALOG_SOURCE_YAML_FLAG, exports.CATALOG_SOURCE_IMAGE_FLAG],
});
exports.CATALOG_SOURCE_NAME = core_1.Flags.string({
    description: `Name of the OLM catalog source or index bundle (IIB) from which to install ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator.`,
    dependsOn: [exports.CATALOG_SOURCE_NAMESPACE_FLAG, exports.OLM_CHANNEL_FLAG],
    exclusive: [exports.CATALOG_SOURCE_YAML_FLAG, exports.CATALOG_SOURCE_IMAGE_FLAG],
});
exports.CATALOG_SOURCE_IMAGE = core_1.Flags.string({
    description: `OLM catalog source image or index bundle (IIB) from which to install the ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator.`,
    dependsOn: [exports.OLM_CHANNEL_FLAG],
    exclusive: [exports.CATALOG_SOURCE_YAML_FLAG, exports.CATALOG_SOURCE_NAMESPACE_FLAG, exports.CATALOG_SOURCE_NAME_FLAG],
});
exports.INSTALLER_FLAG = 'installer';
exports.INSTALLER = core_1.Flags.string({
    char: 'a',
    description: 'Installer type. If not set, default is "olm" for OpenShift 4.x platform otherwise "operator".',
    options: ['operator', 'olm'],
    hidden: true,
});
function checkFlagsCompatability(flags) {
    const ctx = context_1.CheCtlContext.get();
    if (ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
        if (flags[exports.STARTING_CSV_FLAG] && flags[exports.AUTO_UPDATE_FLAG]) {
            throw new Error(`--${exports.STARTING_CSV_FLAG} can be provided with only --no-${exports.AUTO_UPDATE_FLAG}`);
        }
        if (flags[exports.DOMAIN_FLAG]) {
            throw new Error(`--${exports.DOMAIN_FLAG} cannot be provided  for OpenShift platform.`);
        }
    }
    else {
        // Not OLM installer
        if (flags[exports.STARTING_CSV_FLAG]) {
            throw new Error(`--${exports.STARTING_CSV_FLAG} can be provided only for OpenShift platform.`);
        }
        if (flags[exports.CATALOG_SOURCE_YAML_FLAG]) {
            throw new Error(`--${exports.CATALOG_SOURCE_YAML_FLAG} can be provided only for OpenShift platform.`);
        }
        if (flags[exports.OLM_CHANNEL_FLAG]) {
            throw new Error(`--${exports.OLM_CHANNEL_FLAG} can be provided only for OpenShift platform.`);
        }
        if (flags[exports.PACKAGE_MANIFEST_FLAG]) {
            throw new Error(`--${exports.PACKAGE_MANIFEST_FLAG} can be provided only for OpenShift platform.`);
        }
        if (flags[exports.CATALOG_SOURCE_NAME_FLAG]) {
            throw new Error(`--${exports.CATALOG_SOURCE_NAME_FLAG} can be provided only for OpenShift platform.`);
        }
        if (flags[exports.CATALOG_SOURCE_IMAGE_FLAG]) {
            throw new Error(`--${exports.CATALOG_SOURCE_IMAGE_FLAG} can be provided only for OpenShift platform.`);
        }
        if (flags[exports.CATALOG_SOURCE_NAMESPACE_FLAG]) {
            throw new Error(`--${exports.CATALOG_SOURCE_NAMESPACE_FLAG} can be provided only for OpenShift platform.`);
        }
        if (flags[exports.CLUSTER_MONITORING_FLAG]) {
            throw new Error(`--${exports.CLUSTER_MONITORING_FLAG} can be provided only for OpenShift platform.`);
        }
    }
}
//# sourceMappingURL=flags.js.map