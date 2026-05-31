/**
 * Copyright (c) 2019-2022 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
export declare namespace DevWorkspace {
    const PRODUCT_NAME = "Dev Workspace";
    const WEBHOOK = "controller.devfile.io";
    const WORKSPACE_API_GROUP = "workspace.devfile.io";
    const WORKSPACE_API_VERSION = "v1alpha2";
    const DEV_WORKSPACES_KIND = "devworkspaces";
    const DEV_WORKSPACES_CRD = "devworkspaces.workspace.devfile.io";
    const DEV_WORKSPACE_TEMPLATES_KIND = "devworkspacetemplates";
    const DEV_WORKSPACES_TEMPLATES_CRD = "devworkspacetemplates.workspace.devfile.io";
    const CONTROLLER_API_GROUP = "controller.devfile.io";
    const CONTROLLER_API_VERSION = "v1alpha1";
    const DEV_WORKSPACE_ROUTINGS_KIND = "devworkspaceroutings";
    const DEV_WORKSPACE_ROUTINGS_CRD = "devworkspaceroutings.controller.devfile.io";
    const DEV_WORKSPACE_OPERATOR_CONFIGS_PLURAL = "devworkspaceoperatorconfigs";
    const DEV_WORKSPACE_OPERATOR_CONFIGS_CRD = "devworkspaceoperatorconfigs.controller.devfile.io";
    const WEBHOOK_SERVER_SERVICE = "devworkspace-webhookserver";
    const DEV_WORKSPACE_CONTROLLER_METRICS_SERVICE = "devworkspace-controller-metrics";
    const DEV_WORKSPACE_CONTROLLER_SERVICE = "devworkspace-controller-manager-service";
    const WEBHOOK_SERVER_CERT = "devworkspace-operator-webhook-cert";
    const WEBHOOK_SERVER_TLS = "devworkspace-webhookserver-tls";
    const DEV_WORKSPACE_CONTROLLER_SERVICE_CERT = "devworkspace-controller-manager-service-cert";
    const WEBHOOK_SERVER_DEPLOYMENT = "devworkspace-webhook-server";
    const DEV_WORKSPACE_CONTROLLER_DEPLOYMENT = "devworkspace-controller-manager";
    const WEBHOOK_SERVER_SERVICE_ACCOUNT = "devworkspace-webhook-server";
    const DEV_WORKSPACE_CONTROLLER_SERVICE_ACCOUNT = "devworkspace-controller-serviceaccount";
    const DEV_WORKSPACE_LEADER_ELECTION_ROLE = "devworkspace-controller-leader-election-role";
    const DEV_WORKSPACE_SERVICE_CERT_ROLE = "devworkspace-controller-manager-service-cert";
    const DEV_WORKSPACE_LEADER_ELECTION_ROLE_BINDING = "devworkspace-controller-leader-election-rolebinding";
    const DEV_WORKSPACE_SERVICE_CERT_ROLE_BINDING = "devworkspace-controller-manager-service-cert";
    const DEV_WORKSPACE_SERVICE_AUTH_READER_ROLE_BINDING = "devworkspace-controller-manager-service-auth-reader";
    const DEV_WORKSPACES_CLUSTER_ROLE = "devworkspace-controller-role";
    const DEV_WORKSPACE_EDIT_WORKSPACES_CLUSTER_ROLE = "devworkspace-controller-edit-workspaces";
    const DEV_WORKSPACES_VIEW_WORKSPACES_CLUSTER_ROLE = "devworkspace-controller-view-workspaces";
    const DEV_WORKSPACE_PROXY_CLUSTER_ROLE = "devworkspace-controller-proxy-role";
    const DEV_WORKSPACES_METRICS_CLUSTER_ROLE = "devworkspace-controller-metrics-reader";
    const DEV_WORKSPACES_WEBHOOK_CLUSTER_ROLE = "devworkspace-webhook-server";
    const DEV_WORKSPACES_PROXY_CLUSTER_ROLE_BINDING = "devworkspace-controller-proxy-rolebinding";
    const DEV_WORKSPACES_CLUSTER_ROLE_BINDING = "devworkspace-controller-rolebinding";
    const DEV_WORKSPACES_WEBHOOK_CLUSTER_ROLE_BINDING = "devworkspace-webhook-server";
    const DEV_WORKSPACE_CONTROLLER_CERTIFICATE = "devworkspace-controller-serving-cert";
    const DEV_WORKSPACE_CONTROLLER_ISSUER = "devworkspace-controller-selfsigned-issuer";
    const KUBERNETES_NAMESPACE = "devworkspace-controller";
    const CSV_PREFIX = "devworkspace-operator";
    const SUBSCRIPTION = "devworkspace-operator";
    const PACKAGE = "devworkspace-operator";
    const NEXT_CHANNEL_CATALOG_SOURCE = "devworkspace-operator";
    const NEXT_CHANNEL = "next";
    const NEXT_CHANNEL_CATALOG_SOURCE_IMAGE = "quay.io/devfile/devworkspace-operator-index:next";
    const STABLE_CHANNEL_CATALOG_SOURCE = "devworkspace-operator";
    const STABLE_CHANNEL = "fast";
    const STABLE_CHANNEL_CATALOG_SOURCE_IMAGE = "quay.io/devfile/devworkspace-operator-index:release";
}
