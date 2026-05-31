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
import { Command } from '@oclif/core';
export declare namespace InfrastructureContext {
    const IS_OPENSHIFT = "infrastructure-is-openshift";
    const OPENSHIFT_VERSION = "infrastructure-openshift-version";
    const KUBERNETES_VERSION = "infrastructure-kubernetes-version";
    const OPENSHIFT_ARCH = "infrastructure-openshift-arch";
    const OPENSHIFT_OPERATOR_NAMESPACE = "openshift-operator-namespace";
    const OPENSHIFT_MARKETPLACE_NAMESPACE = "openshift-marketplace-namespace";
}
export declare namespace CliContext {
    const CLI_COMMAND_FLAGS = "cli-command-flags";
    const CLI_COMMAND_START_TIME = "cli-command-start-time";
    const CLI_COMMAND_END_TIME = "cli-command-end-time";
    const CLI_COMMAND_ID = "cli-command-id";
    const CLI_CONFIG_DIR = "cli-config-dir";
    const CLI_CACHE_DIR = "cli-cache-dir";
    const CLI_ERROR_LOG = "cli-error-log";
    const CLI_COMMAND_LOGS_DIR = "cli-logs-log";
    const CLI_IS_DEV_VERSION = "cli-dev-version";
    const CLI_IS_CHECTL = "cli-is-chectl";
    const CLI_CHE_OPERATOR_RESOURCES_DIR = "cli-che-operator-resources-dir";
    const CLI_DEV_WORKSPACE_OPERATOR_RESOURCES_DIR = "cli-dev-workspace-operator-resources-dir";
    const CLI_COMMAND_POST_OUTPUT_MESSAGES = "cli-messages";
}
export declare namespace OIDCContext {
    const ISSUER_URL = "oidc-issuer-url";
    const CLIENT_ID = "oidc-client-id";
    const CA_FILE = "oidc-ca-file";
}
export declare namespace DexContext {
    const DEX_CA_CRT = "dex-ca.crt";
    const DEX_USERNAME = "dex-username";
    const DEX_PASSWORD = "dex-password";
    const DEX_PASSWORD_HASH = "dex-password-hash";
}
export declare namespace EclipseCheContext {
    const CHANNEL = "eclipse-che-channel";
    const CATALOG_SOURCE_NAME = "eclipse-che-catalog-source-name";
    const CATALOG_SOURCE_IMAGE = "eclipse-che-catalog-source-image";
    const CATALOG_SOURCE_NAMESPACE = "eclipse-che-catalog-source-namespace";
    const PACKAGE_NAME = "eclipse-che-package-name";
    const APPROVAL_STRATEGY = "eclipse-che-approval-strategy";
    const CUSTOM_CR = "eclipse-che-custom-cr";
    const CR_PATCH = "eclipse-che-cr-patch";
    const DEFAULT_CR = "eclipse-che-default-cr";
    const NAMESPACE = "eclipse-che-namespace";
    const OPERATOR_NAMESPACE = "eclipse-che-operator-namespace";
    const CREATE_CATALOG_SOURCE_AND_SUBSCRIPTION = "eclipse-che-create-catalog-source-and-subscription";
}
export declare namespace DevWorkspaceContext {
    const CATALOG_SOURCE_NAME = "dev-workspace-catalog-source-name";
    const CATALOG_SOURCE_IMAGE = "dev-workspace-catalog-source-image";
    const CHANNEL = "dev-workspace-install-plan";
    const NAMESPACE = "dev-workspace-namespace";
}
export declare namespace KubeHelperContext {
    const POD_WAIT_TIMEOUT = "kube-pod-wait-timeout";
    const POD_READY_TIMEOUT = "kube-pod-ready-timeout";
    const POD_READY_TIMEOUT_EMBEDDED_PLUGIN_REGISTRY = "kube-pod-ready-timeout-embedded-plugin-registry";
    const POD_DOWNLOAD_IMAGE_TIMEOUT = "kube-pod-download-image-timeout";
    const POD_ERROR_RECHECK_TIMEOUT = "kube-pod-error-recheck-timeout";
}
export declare namespace OperatorImageUpgradeContext {
    const NEW_IMAGE = "operator-image-new";
    const NEW_IMAGE_NAME = "operator-image-name-new";
    const NEW_IMAGE_TAG = "operator-image-tag-new";
    const DEPLOYED_IMAGE = "operator-image-deployed";
    const DEPLOYED_IMAGE_NAME = "operator-image-name-deployed";
    const DEPLOYED_IMAGE_TAG = "operator-image-tag-deployed";
}
/**
 * chectl command context.
 * Can be requested from any location with `ChectlContext#get`
 */
export declare namespace CheCtlContext {
    function init(flags: any, command: Command): Promise<void>;
    function initAndGet(flags: any, command: Command): Promise<any>;
    function get(): any;
    function getFlags(): any;
}
