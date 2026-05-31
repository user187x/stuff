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
export declare namespace EclipseChe {
    const CHE_FLAVOR = "che";
    const PRODUCT_ID = "eclipse-che";
    const PRODUCT_NAME = "Eclipse Che";
    const NAMESPACE = "eclipse-che";
    const OPERATOR_SERVICE = "che-operator-service";
    const OPERATOR_SERVICE_CERT_SECRET = "che-operator-service-cert";
    const OPERATOR_SERVICE_ACCOUNT = "che-operator";
    const K8S_CERTIFICATE = "che-operator-serving-cert";
    const K8S_ISSUER = "che-operator-selfsigned-issuer";
    const VALIDATING_WEBHOOK = "org.eclipse.che";
    const MUTATING_WEBHOOK = "org.eclipse.che";
    const CONFIG_MAP = "che";
    const PLUGIN_REGISTRY_CONFIG_MAP = "plugin-registry";
    const CONSOLE_LINK = "che";
    const PROMETHEUS = "prometheus-k8s";
    const IMAGE_CONTENT_SOURCE_POLICY = "quay.io";
    const CHE_CLUSTER_CRD = "checlusters.org.eclipse.che";
    const CHE_CLUSTER_API_GROUP = "org.eclipse.che";
    const CHE_CLUSTER_API_VERSION_V2 = "v2";
    const CHE_CLUSTER_KIND_PLURAL = "checlusters";
    const PACKAGE = "eclipse-che";
    const STABLE_CHANNEL = "stable";
    const STABLE_CHANNEL_CATALOG_SOURCE = "community-operators";
    const NEXT_CHANNEL = "next";
    const NEXT_CHANNEL_CATALOG_SOURCE = "eclipse-che";
    const NEXT_CATALOG_SOURCE_IMAGE = "quay.io/eclipse/eclipse-che-olm-catalog:next";
    const SUBSCRIPTION = "eclipse-che";
    const CSV_PREFIX = "eclipse-che";
    const APPROVAL_STRATEGY_MANUAL = "Manual";
    const APPROVAL_STRATEGY_AUTOMATIC = "Automatic";
    const CHE_TLS_SECRET_NAME = "che-tls";
    const SELF_SIGNED_CERTIFICATE = "self-signed-certificate";
    const DEFAULT_CA_CERT_FILE_NAME = "cheCA.crt";
    const OPERATOR_IMAGE_NAME = "quay.io/eclipse/che-operator";
    const OPERATOR_IMAGE_NEXT_TAG = "next";
    const DOC_LINK = "https://www.eclipse.org/che/docs/";
    const DOC_LINK_RELEASE_NOTES = "";
    const DOC_LINK_CONFIGURE_API_SERVER = "https://kubernetes.io/docs/reference/access-authn-authz/authentication/#configuring-the-api-server";
    const CHE_SERVER = "Eclipse Che Server";
    const DASHBOARD = "Dashboard";
    const GATEWAY = "Gateway";
    const PLUGIN_REGISTRY = "Plugin Registry";
    const CHE_OPERATOR = "Eclipse Che Operator";
    const OPERATOR_DEPLOYMENT_NAME = "che-operator";
    const CHE_SERVER_DEPLOYMENT_NAME = "che";
    const DASHBOARD_DEPLOYMENT_NAME = "che-dashboard";
    const GATEWAY_DEPLOYMENT_NAME = "che-gateway";
    const PLUGIN_REGISTRY_DEPLOYMENT_NAME = "plugin-registry";
    const CHE_OPERATOR_SELECTOR = "app=che-operator,app.kubernetes.io/component=che-operator";
    const CHE_SERVER_SELECTOR = "app.kubernetes.io/name=che,app.kubernetes.io/component=che";
    const DASHBOARD_SELECTOR = "app.kubernetes.io/name=che,app.kubernetes.io/component=che-dashboard";
    const PLUGIN_REGISTRY_SELECTOR = "app.kubernetes.io/name=che,app.kubernetes.io/component=plugin-registry";
    const GATEWAY_SELECTOR = "app.kubernetes.io/name=che,app.kubernetes.io/component=che-gateway";
}
