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
exports.EclipseChe = void 0;
var EclipseChe;
(function (EclipseChe) {
    EclipseChe.CHE_FLAVOR = 'che';
    EclipseChe.PRODUCT_ID = 'eclipse-che';
    EclipseChe.PRODUCT_NAME = 'Eclipse Che';
    // Resources
    EclipseChe.NAMESPACE = 'eclipse-che';
    EclipseChe.OPERATOR_SERVICE = `${EclipseChe.CHE_FLAVOR}-operator-service`;
    EclipseChe.OPERATOR_SERVICE_CERT_SECRET = `${EclipseChe.CHE_FLAVOR}-operator-service-cert`;
    EclipseChe.OPERATOR_SERVICE_ACCOUNT = `${EclipseChe.CHE_FLAVOR}-operator`;
    EclipseChe.K8S_CERTIFICATE = 'che-operator-serving-cert';
    EclipseChe.K8S_ISSUER = 'che-operator-selfsigned-issuer';
    EclipseChe.VALIDATING_WEBHOOK = 'org.eclipse.che';
    EclipseChe.MUTATING_WEBHOOK = 'org.eclipse.che';
    EclipseChe.CONFIG_MAP = 'che';
    EclipseChe.PLUGIN_REGISTRY_CONFIG_MAP = 'plugin-registry';
    EclipseChe.CONSOLE_LINK = 'che';
    EclipseChe.PROMETHEUS = 'prometheus-k8s';
    EclipseChe.IMAGE_CONTENT_SOURCE_POLICY = 'quay.io';
    // API
    EclipseChe.CHE_CLUSTER_CRD = 'checlusters.org.eclipse.che';
    EclipseChe.CHE_CLUSTER_API_GROUP = 'org.eclipse.che';
    EclipseChe.CHE_CLUSTER_API_VERSION_V2 = 'v2';
    EclipseChe.CHE_CLUSTER_KIND_PLURAL = 'checlusters';
    // OLM
    EclipseChe.PACKAGE = EclipseChe.PRODUCT_ID;
    EclipseChe.STABLE_CHANNEL = 'stable';
    EclipseChe.STABLE_CHANNEL_CATALOG_SOURCE = 'community-operators';
    EclipseChe.NEXT_CHANNEL = 'next';
    EclipseChe.NEXT_CHANNEL_CATALOG_SOURCE = EclipseChe.PRODUCT_ID;
    EclipseChe.NEXT_CATALOG_SOURCE_IMAGE = 'quay.io/eclipse/eclipse-che-olm-catalog:next';
    EclipseChe.SUBSCRIPTION = EclipseChe.PRODUCT_ID;
    EclipseChe.CSV_PREFIX = EclipseChe.PRODUCT_ID;
    EclipseChe.APPROVAL_STRATEGY_MANUAL = 'Manual';
    EclipseChe.APPROVAL_STRATEGY_AUTOMATIC = 'Automatic';
    // TLS
    EclipseChe.CHE_TLS_SECRET_NAME = 'che-tls';
    EclipseChe.SELF_SIGNED_CERTIFICATE = 'self-signed-certificate';
    EclipseChe.DEFAULT_CA_CERT_FILE_NAME = 'cheCA.crt';
    // Operator image
    EclipseChe.OPERATOR_IMAGE_NAME = 'quay.io/eclipse/che-operator';
    EclipseChe.OPERATOR_IMAGE_NEXT_TAG = 'next';
    // Doc links
    EclipseChe.DOC_LINK = 'https://www.eclipse.org/che/docs/';
    EclipseChe.DOC_LINK_RELEASE_NOTES = '';
    EclipseChe.DOC_LINK_CONFIGURE_API_SERVER = 'https://kubernetes.io/docs/reference/access-authn-authz/authentication/#configuring-the-api-server';
    // Components
    EclipseChe.CHE_SERVER = `${EclipseChe.PRODUCT_NAME} Server`;
    EclipseChe.DASHBOARD = 'Dashboard';
    EclipseChe.GATEWAY = 'Gateway';
    EclipseChe.PLUGIN_REGISTRY = 'Plugin Registry';
    EclipseChe.CHE_OPERATOR = `${EclipseChe.PRODUCT_NAME} Operator`;
    // Deployments
    EclipseChe.OPERATOR_DEPLOYMENT_NAME = `${EclipseChe.CHE_FLAVOR}-operator`;
    EclipseChe.CHE_SERVER_DEPLOYMENT_NAME = `${EclipseChe.CHE_FLAVOR}`;
    EclipseChe.DASHBOARD_DEPLOYMENT_NAME = `${EclipseChe.CHE_FLAVOR}-dashboard`;
    EclipseChe.GATEWAY_DEPLOYMENT_NAME = 'che-gateway';
    EclipseChe.PLUGIN_REGISTRY_DEPLOYMENT_NAME = 'plugin-registry';
    // Selectors
    // It must be `app=`, see: https://issues.redhat.com/browse/CRW-4848
    EclipseChe.CHE_OPERATOR_SELECTOR = `app=${EclipseChe.CHE_FLAVOR}-operator,app.kubernetes.io/component=${EclipseChe.CHE_FLAVOR}-operator`;
    EclipseChe.CHE_SERVER_SELECTOR = `app.kubernetes.io/name=${EclipseChe.CHE_FLAVOR},app.kubernetes.io/component=${EclipseChe.CHE_FLAVOR}`;
    EclipseChe.DASHBOARD_SELECTOR = `app.kubernetes.io/name=${EclipseChe.CHE_FLAVOR},app.kubernetes.io/component=${EclipseChe.CHE_FLAVOR}-dashboard`;
    EclipseChe.PLUGIN_REGISTRY_SELECTOR = `app.kubernetes.io/name=${EclipseChe.CHE_FLAVOR},app.kubernetes.io/component=plugin-registry`;
    EclipseChe.GATEWAY_SELECTOR = `app.kubernetes.io/name=${EclipseChe.CHE_FLAVOR},app.kubernetes.io/component=che-gateway`;
})(EclipseChe || (exports.EclipseChe = EclipseChe = {}));
//# sourceMappingURL=eclipse-che.js.map