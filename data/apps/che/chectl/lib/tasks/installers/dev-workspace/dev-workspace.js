"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.DevWorkspace = void 0;
var DevWorkspace;
(function (DevWorkspace) {
    DevWorkspace.PRODUCT_NAME = 'Dev Workspace';
    // Webhook
    DevWorkspace.WEBHOOK = 'controller.devfile.io';
    // API
    DevWorkspace.WORKSPACE_API_GROUP = 'workspace.devfile.io';
    DevWorkspace.WORKSPACE_API_VERSION = 'v1alpha2';
    DevWorkspace.DEV_WORKSPACES_KIND = 'devworkspaces';
    DevWorkspace.DEV_WORKSPACES_CRD = 'devworkspaces.workspace.devfile.io';
    DevWorkspace.DEV_WORKSPACE_TEMPLATES_KIND = 'devworkspacetemplates';
    DevWorkspace.DEV_WORKSPACES_TEMPLATES_CRD = 'devworkspacetemplates.workspace.devfile.io';
    DevWorkspace.CONTROLLER_API_GROUP = 'controller.devfile.io';
    DevWorkspace.CONTROLLER_API_VERSION = 'v1alpha1';
    DevWorkspace.DEV_WORKSPACE_ROUTINGS_KIND = 'devworkspaceroutings';
    DevWorkspace.DEV_WORKSPACE_ROUTINGS_CRD = 'devworkspaceroutings.controller.devfile.io';
    DevWorkspace.DEV_WORKSPACE_OPERATOR_CONFIGS_PLURAL = 'devworkspaceoperatorconfigs';
    DevWorkspace.DEV_WORKSPACE_OPERATOR_CONFIGS_CRD = 'devworkspaceoperatorconfigs.controller.devfile.io';
    // Services
    DevWorkspace.WEBHOOK_SERVER_SERVICE = 'devworkspace-webhookserver';
    DevWorkspace.DEV_WORKSPACE_CONTROLLER_METRICS_SERVICE = 'devworkspace-controller-metrics';
    DevWorkspace.DEV_WORKSPACE_CONTROLLER_SERVICE = 'devworkspace-controller-manager-service';
    // Secrets
    DevWorkspace.WEBHOOK_SERVER_CERT = 'devworkspace-operator-webhook-cert';
    DevWorkspace.WEBHOOK_SERVER_TLS = 'devworkspace-webhookserver-tls';
    DevWorkspace.DEV_WORKSPACE_CONTROLLER_SERVICE_CERT = 'devworkspace-controller-manager-service-cert';
    // Deployments
    DevWorkspace.WEBHOOK_SERVER_DEPLOYMENT = 'devworkspace-webhook-server';
    DevWorkspace.DEV_WORKSPACE_CONTROLLER_DEPLOYMENT = 'devworkspace-controller-manager';
    // ServiceAccounts
    DevWorkspace.WEBHOOK_SERVER_SERVICE_ACCOUNT = 'devworkspace-webhook-server';
    DevWorkspace.DEV_WORKSPACE_CONTROLLER_SERVICE_ACCOUNT = 'devworkspace-controller-serviceaccount';
    // Roles
    DevWorkspace.DEV_WORKSPACE_LEADER_ELECTION_ROLE = 'devworkspace-controller-leader-election-role';
    DevWorkspace.DEV_WORKSPACE_SERVICE_CERT_ROLE = 'devworkspace-controller-manager-service-cert';
    // RoleBindings
    DevWorkspace.DEV_WORKSPACE_LEADER_ELECTION_ROLE_BINDING = 'devworkspace-controller-leader-election-rolebinding';
    DevWorkspace.DEV_WORKSPACE_SERVICE_CERT_ROLE_BINDING = 'devworkspace-controller-manager-service-cert';
    DevWorkspace.DEV_WORKSPACE_SERVICE_AUTH_READER_ROLE_BINDING = 'devworkspace-controller-manager-service-auth-reader';
    // ClusterRoles
    DevWorkspace.DEV_WORKSPACES_CLUSTER_ROLE = 'devworkspace-controller-role';
    DevWorkspace.DEV_WORKSPACE_EDIT_WORKSPACES_CLUSTER_ROLE = 'devworkspace-controller-edit-workspaces';
    DevWorkspace.DEV_WORKSPACES_VIEW_WORKSPACES_CLUSTER_ROLE = 'devworkspace-controller-view-workspaces';
    DevWorkspace.DEV_WORKSPACE_PROXY_CLUSTER_ROLE = 'devworkspace-controller-proxy-role';
    DevWorkspace.DEV_WORKSPACES_METRICS_CLUSTER_ROLE = 'devworkspace-controller-metrics-reader';
    DevWorkspace.DEV_WORKSPACES_WEBHOOK_CLUSTER_ROLE = 'devworkspace-webhook-server';
    // ClusterRoleBindings
    DevWorkspace.DEV_WORKSPACES_PROXY_CLUSTER_ROLE_BINDING = 'devworkspace-controller-proxy-rolebinding';
    DevWorkspace.DEV_WORKSPACES_CLUSTER_ROLE_BINDING = 'devworkspace-controller-rolebinding';
    DevWorkspace.DEV_WORKSPACES_WEBHOOK_CLUSTER_ROLE_BINDING = 'devworkspace-webhook-server';
    // Issuer
    DevWorkspace.DEV_WORKSPACE_CONTROLLER_CERTIFICATE = 'devworkspace-controller-serving-cert';
    DevWorkspace.DEV_WORKSPACE_CONTROLLER_ISSUER = 'devworkspace-controller-selfsigned-issuer';
    // Olm
    DevWorkspace.KUBERNETES_NAMESPACE = 'devworkspace-controller';
    DevWorkspace.CSV_PREFIX = 'devworkspace-operator';
    DevWorkspace.SUBSCRIPTION = 'devworkspace-operator';
    DevWorkspace.PACKAGE = 'devworkspace-operator';
    DevWorkspace.NEXT_CHANNEL_CATALOG_SOURCE = 'devworkspace-operator';
    DevWorkspace.NEXT_CHANNEL = 'next';
    DevWorkspace.NEXT_CHANNEL_CATALOG_SOURCE_IMAGE = 'quay.io/devfile/devworkspace-operator-index:next';
    DevWorkspace.STABLE_CHANNEL_CATALOG_SOURCE = 'devworkspace-operator';
    DevWorkspace.STABLE_CHANNEL = 'fast';
    DevWorkspace.STABLE_CHANNEL_CATALOG_SOURCE_IMAGE = 'quay.io/devfile/devworkspace-operator-index:release';
})(DevWorkspace || (exports.DevWorkspace = DevWorkspace = {}));
//# sourceMappingURL=dev-workspace.js.map