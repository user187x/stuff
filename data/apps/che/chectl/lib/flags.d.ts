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
export declare const PLATFORM_FLAG = "platform";
export declare const PLATFORM: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const CHE_NAMESPACE_FLAG = "chenamespace";
export declare const CHE_NAMESPACE: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const BATCH_FLAG = "batch";
export declare const BATCH: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const LISTR_RENDERER_FLAG = "listr-renderer";
export declare const LISTR_RENDERER: import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const SKIP_KUBE_HEALTHZ_CHECK_FLAG = "skip-kubernetes-health-check";
export declare const SKIP_KUBE_HEALTHZ_CHECK: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const SKIP_CERT_MANAGER_FLAG = "skip-cert-manager";
export declare const SKIP_CERT_MANAGER: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const SKIP_DEV_WORKSPACE_FLAG = "skip-devworkspace-operator";
export declare const SKIP_DEV_WORKSPACE: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const CHE_OPERATOR_CR_PATCH_YAML_FLAG = "che-operator-cr-patch-yaml";
export declare const CHE_OPERATOR_CR_PATCH_YAML: import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const ASSUME_YES_FLAG = "yes";
export declare const ASSUME_YES: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const CHE_OPERATOR_CR_YAML_FLAG = "che-operator-cr-yaml";
export declare const CHE_OPERATOR_CR_YAML: import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const DEFAULT_POD_WAIT_TIMEOUT = "120000";
export declare const K8S_POD_WAIT_TIMEOUT_FLAG = "k8spodwaittimeout";
export declare const K8S_POD_WAIT_TIMEOUT: import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const DEFAULT_K8S_POD_DOWNLOAD_IMAGE_TIMEOUT = "1200000";
export declare const K8S_POD_DOWNLOAD_IMAGE_TIMEOUT_FLAG = "k8spoddownloadimagetimeout";
export declare const K8S_POD_DOWNLOAD_IMAGE_TIMEOUT: import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const DEFAULT_K8S_POD_READY_TIMEOUT_EMBEDDED_PLUGIN_REGISTRY = "600000";
export declare const DEFAULT_K8S_POD_READY_TIMEOUT = "120000";
export declare const K8S_POD_READY_TIMEOUT_FLAG = "k8spodreadytimeout";
export declare const K8S_POD_READY_TIMEOUT: import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const DEFAULT_K8S_POD_ERROR_RECHECK_TIMEOUT = "60000";
export declare const K8S_POD_ERROR_RECHECK_TIMEOUT_FLAG = "k8spoderrorrechecktimeout";
export declare const K8S_POD_ERROR_RECHECK_TIMEOUT: import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const TEMPLATES_FLAG = "templates";
export declare const TEMPLATES: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const LOG_DIRECTORY_FLAG = "directory";
export declare const LOG_DIRECTORY: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const TELEMETRY_FLAG = "telemetry";
export declare const TELEMETRY: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const SKIP_VERSION_CHECK_FLAG = "skip-version-check";
export declare const SKIP_VERSION_CHECK: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const CLUSTER_MONITORING_FLAG = "cluster-monitoring";
export declare const CLUSTER_MONITORING: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const CHE_OPERATOR_IMAGE_FLAG = "che-operator-image";
export declare const CHE_OPERATOR_IMAGE: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const CHE_IMAGE_FLAG = "cheimage";
export declare const CHE_IMAGE: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const DOMAIN_FLAG = "domain";
export declare const DOMAIN: import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const DEBUG_FLAG = "debug";
export declare const DEBUG: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const WORKSPACE_PVS_STORAGE_CLASS_NAME_FLAG = "workspace-pvc-storage-class-name";
export declare const WORKSPACE_PVS_STORAGE_CLASS_NAME: import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const DEVFILE_REGISTRY_URL_FLAG = "devfile-registry-url";
export declare const DEVFILE_REGISTRY_URL: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const PLUGIN_REGISTRY_URL_FLAG = "plugin-registry-url";
export declare const PLUGIN_REGISTRY_URL: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const DEBUG_PORT_FLAG = "debug-port";
export declare const DEBUG_PORT: import("@oclif/core/lib/interfaces").OptionFlag<number, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const DELETE_NAMESPACE_FLAG = "delete-namespace";
export declare const DELETE_NAMESPACE: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const DELETE_ALL_FLAG = "delete-all";
export declare const DELETE_ALL: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const DESTINATION_FLAG = "destination";
export declare const DESTINATION: import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const STARTING_CSV_FLAG = "starting-csv";
export declare const STARTING_CSV: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const AUTO_UPDATE_FLAG = "auto-update";
export declare const AUTO_UPDATE: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
export declare const OLM_CHANNEL_FLAG = "olm-channel";
export declare const OLM_CHANNEL: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const PACKAGE_MANIFEST_FLAG = "package-manifest-name";
export declare const PACKAGE_MANIFEST: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const CATALOG_SOURCE_NAMESPACE_FLAG = "catalog-source-namespace";
export declare const CATALOG_SOURCE_NAME_FLAG = "catalog-source-name";
export declare const CATALOG_SOURCE_IMAGE_FLAG = "catalog-source-image";
export declare const CATALOG_SOURCE_YAML_FLAG = "catalog-source-yaml";
export declare const CATALOG_SOURCE_YAML: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const CATALOG_SOURCE_NAMESPACE: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const CATALOG_SOURCE_NAME: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const CATALOG_SOURCE_IMAGE: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare const INSTALLER_FLAG = "installer";
export declare const INSTALLER: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
export declare function checkFlagsCompatability(flags: any): void;
