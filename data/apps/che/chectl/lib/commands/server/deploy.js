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
exports.checkK8sRequiredConfig = checkK8sRequiredConfig;
const tslib_1 = require("tslib");
const core_1 = require("@oclif/core");
const core_2 = require("@oclif/core");
const cert_manager_installer_1 = require("../../tasks/installers/cert-manager-installer");
const context_1 = require("../../context");
const kube_client_1 = require("../../api/kube-client");
const constants_1 = require("../../constants");
const dex_installer_1 = require("../../tasks/installers/dex-installer");
const platform_tasks_1 = require("../../tasks/platforms/platform-tasks");
const eclipse_che_installer_factory_1 = require("../../tasks/installers/eclipse-che/eclipse-che-installer-factory");
const flags_1 = require("../../flags");
const eclipse_che_1 = require("../../tasks/installers/eclipse-che/eclipse-che");
const command_utils_1 = require("../../utils/command-utils");
const common_tasks_1 = require("../../tasks/common-tasks");
const che_tasks_1 = require("../../tasks/che-tasks");
const utls_1 = require("../../utils/utls");
const che_1 = require("../../utils/che");
class Deploy extends core_1.Command {
    run() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { flags } = yield this.parse(Deploy);
            const ctx = yield context_1.CheCtlContext.initAndGet(flags, this);
            yield this.config.runHook(constants_1.DEFAULT_ANALYTIC_HOOK_NAME, { command: Deploy.id, flags });
            if (!flags.batch && ctx.isChectl) {
                yield (0, command_utils_1.askForChectlUpdateIfNeeded)();
            }
            (0, flags_1.checkFlagsCompatability)(flags);
            checkK8sRequiredConfig(flags);
            // Platform Checks
            const platformTasks = (0, utls_1.newListr)();
            platformTasks.add(platform_tasks_1.PlatformTasks.getPreflightCheckTasks());
            // PreInstall tasks
            const preInstallTasks = (0, utls_1.newListr)();
            preInstallTasks.add(common_tasks_1.CommonTasks.getTestKubernetesApiTasks());
            preInstallTasks.add(common_tasks_1.CommonTasks.getOpenShiftVersionTask());
            // Install tasks
            const installTasks = (0, utls_1.newListr)();
            installTasks.add(common_tasks_1.CommonTasks.getCreateNamespaceTask(flags[flags_1.CHE_NAMESPACE_FLAG], getNamespaceLabels(flags)));
            if (!ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
                installTasks.add(new cert_manager_installer_1.CertManagerInstaller().getDeployTasks());
            }
            if (flags[flags_1.PLATFORM_FLAG] === 'minikube') {
                installTasks.add(new dex_installer_1.DexInstaller().getDeployTasks());
            }
            installTasks.add(che_tasks_1.CheTasks.getServerLogsTasks(true));
            installTasks.add(eclipse_che_installer_factory_1.EclipseCheInstallerFactory.getInstaller().getDeployTasks());
            // PostInstall tasks
            const postInstallTasks = (0, utls_1.newListr)([], false);
            postInstallTasks.add(che_tasks_1.CheTasks.getWaitCheDeployedTasks());
            postInstallTasks.add(che_tasks_1.CheTasks.getRetrieveSelfSignedCertificateTask());
            postInstallTasks.add(common_tasks_1.CommonTasks.getPreparePostInstallationOutputTask());
            postInstallTasks.add(common_tasks_1.CommonTasks.getPrintHighlightedMessagesTask());
            try {
                yield preInstallTasks.run(ctx);
                const kubeHelper = kube_client_1.KubeClient.getInstance();
                const cheCluster = yield kubeHelper.getCheCluster(flags[flags_1.CHE_NAMESPACE_FLAG]);
                if (cheCluster) {
                    core_2.ux.warn(`${eclipse_che_1.EclipseChe.PRODUCT_NAME} has been already deployed. Use server:start command to start a stopped ${eclipse_che_1.EclipseChe.PRODUCT_NAME} instance.`);
                }
                else {
                    yield platformTasks.run(ctx);
                    yield installTasks.run(ctx);
                    yield postInstallTasks.run(ctx);
                    this.log((0, command_utils_1.getCommandSuccessMessage)());
                }
            }
            catch (err) {
                this.error((0, command_utils_1.wrapCommandError)(err));
            }
            if (!flags[flags_1.BATCH_FLAG]) {
                (0, command_utils_1.notifyCommandCompletedSuccessfully)();
            }
            this.exit(0);
        });
    }
}
Deploy.description = `Deploy ${eclipse_che_1.EclipseChe.PRODUCT_NAME} server`;
Deploy.flags = {
    help: core_1.Flags.help({ char: 'h' }),
    [flags_1.CHE_NAMESPACE_FLAG]: flags_1.CHE_NAMESPACE,
    [flags_1.BATCH_FLAG]: flags_1.BATCH,
    [flags_1.LISTR_RENDERER_FLAG]: flags_1.LISTR_RENDERER,
    [flags_1.CHE_IMAGE_FLAG]: flags_1.CHE_IMAGE,
    [flags_1.TEMPLATES_FLAG]: flags_1.TEMPLATES,
    [flags_1.DEVFILE_REGISTRY_URL_FLAG]: flags_1.DEVFILE_REGISTRY_URL,
    [flags_1.PLUGIN_REGISTRY_URL_FLAG]: flags_1.PLUGIN_REGISTRY_URL,
    [flags_1.K8S_POD_WAIT_TIMEOUT_FLAG]: flags_1.K8S_POD_WAIT_TIMEOUT,
    [flags_1.K8S_POD_READY_TIMEOUT_FLAG]: flags_1.K8S_POD_READY_TIMEOUT,
    [flags_1.K8S_POD_DOWNLOAD_IMAGE_TIMEOUT_FLAG]: flags_1.K8S_POD_DOWNLOAD_IMAGE_TIMEOUT,
    [flags_1.K8S_POD_ERROR_RECHECK_TIMEOUT_FLAG]: flags_1.K8S_POD_ERROR_RECHECK_TIMEOUT,
    [flags_1.LOG_DIRECTORY_FLAG]: flags_1.LOG_DIRECTORY,
    [flags_1.PLATFORM_FLAG]: flags_1.PLATFORM,
    [flags_1.INSTALLER_FLAG]: flags_1.INSTALLER,
    [flags_1.DOMAIN_FLAG]: flags_1.DOMAIN,
    [flags_1.DEBUG_FLAG]: flags_1.DEBUG,
    [flags_1.CHE_OPERATOR_IMAGE_FLAG]: flags_1.CHE_OPERATOR_IMAGE,
    [flags_1.CHE_OPERATOR_CR_YAML_FLAG]: flags_1.CHE_OPERATOR_CR_YAML,
    [flags_1.CHE_OPERATOR_CR_PATCH_YAML_FLAG]: flags_1.CHE_OPERATOR_CR_PATCH_YAML,
    [flags_1.WORKSPACE_PVS_STORAGE_CLASS_NAME_FLAG]: flags_1.WORKSPACE_PVS_STORAGE_CLASS_NAME,
    [flags_1.SKIP_VERSION_CHECK_FLAG]: flags_1.SKIP_VERSION_CHECK,
    [flags_1.SKIP_CERT_MANAGER_FLAG]: flags_1.SKIP_CERT_MANAGER,
    [flags_1.SKIP_DEV_WORKSPACE_FLAG]: flags_1.SKIP_DEV_WORKSPACE,
    [flags_1.AUTO_UPDATE_FLAG]: flags_1.AUTO_UPDATE,
    [flags_1.STARTING_CSV_FLAG]: flags_1.STARTING_CSV,
    [flags_1.OLM_CHANNEL_FLAG]: flags_1.OLM_CHANNEL,
    [flags_1.PACKAGE_MANIFEST_FLAG]: flags_1.PACKAGE_MANIFEST,
    [flags_1.CATALOG_SOURCE_YAML_FLAG]: flags_1.CATALOG_SOURCE_YAML,
    [flags_1.CATALOG_SOURCE_NAME_FLAG]: flags_1.CATALOG_SOURCE_NAME,
    [flags_1.CATALOG_SOURCE_NAMESPACE_FLAG]: flags_1.CATALOG_SOURCE_NAMESPACE,
    [flags_1.CATALOG_SOURCE_IMAGE_FLAG]: flags_1.CATALOG_SOURCE_IMAGE,
    [flags_1.CLUSTER_MONITORING_FLAG]: flags_1.CLUSTER_MONITORING,
    [flags_1.TELEMETRY_FLAG]: flags_1.TELEMETRY,
    [flags_1.SKIP_KUBE_HEALTHZ_CHECK_FLAG]: flags_1.SKIP_KUBE_HEALTHZ_CHECK,
};
exports.default = Deploy;
function getNamespaceLabels(flags) {
    if (flags[flags_1.CLUSTER_MONITORING_FLAG] && flags[flags_1.PLATFORM_FLAG] === 'openshift') {
        return { 'openshift.io/cluster-monitoring': 'true' };
    }
    return {};
}
function checkK8sRequiredConfig(flags) {
    const ctx = context_1.CheCtlContext.get();
    if (!ctx[context_1.InfrastructureContext.IS_OPENSHIFT] && // Ensure required CheCluster fields are set (k8s platforms)
        flags[flags_1.PLATFORM_FLAG] !== 'minikube') {
        for (const field of [
            'spec.networking.auth.identityProviderURL',
            'spec.networking.auth.oAuthSecret',
            'spec.networking.auth.oAuthClientName',
        ]) {
            if (!che_1.Che.getCheClusterFieldConfigured(field)) {
                throw new Error(getMissedOIDCConfigClusterFieldErrorMsg());
            }
        }
    }
}
function getMissedOIDCConfigClusterFieldErrorMsg() {
    return `Some required configuration is not specifed in order to deploy ${eclipse_che_1.EclipseChe.PRODUCT_NAME}
on a Kubernetes cluster with an OIDC provider configured. Use the flag '--${flags_1.CHE_OPERATOR_CR_PATCH_YAML_FLAG} <PATH_TO_PATCH_FILE>' to
provide a CheCluster Custom Resource patch with the needed configuration. Find an example of such a configuration below:

kind: CheCluster
apiVersion: org.eclipse.che/v2
spec:
  networking:
    auth:
      oAuthClientName: "<CLIENT_ID>"
      oAuthSecret: "<CLIENT_SECRET>"
      identityProviderURL: "<ISSUER_URL>"

`;
}
//# sourceMappingURL=deploy.js.map