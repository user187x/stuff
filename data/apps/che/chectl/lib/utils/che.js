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
exports.Che = void 0;
const tslib_1 = require("tslib");
const context_1 = require("../context");
const eclipse_che_1 = require("../tasks/installers/eclipse-che/eclipse-che");
const kube_client_1 = require("../api/kube-client");
const flags_1 = require("../flags");
const openshift_1 = require("./openshift");
const nodeforge = require("node-forge");
const utls_1 = require("./utls");
const che_logs_reader_1 = require("../api/che-logs-reader");
var Che;
(function (Che) {
    function isRedHatCatalogSources(catalogSourceName) {
        return catalogSourceName === 'community-operators' || catalogSourceName === 'redhat-operators';
    }
    Che.isRedHatCatalogSources = isRedHatCatalogSources;
    function readPodLog(namespace, podLabelSelector, directory, follow) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const logsReader = new che_logs_reader_1.CheLogsReader();
            return logsReader.readPodLog(namespace, podLabelSelector, directory, follow);
        });
    }
    Che.readPodLog = readPodLog;
    function readNamespaceEvents(namespace, directory, follow) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const logsReader = new che_logs_reader_1.CheLogsReader();
            return logsReader.readNamespaceEvents(namespace, directory, follow);
        });
    }
    Che.readNamespaceEvents = readNamespaceEvents;
    function getCheClusterFieldConfigured(fieldPath) {
        const ctx = context_1.CheCtlContext.get();
        const cheClusterPatch = ctx[context_1.EclipseCheContext.CR_PATCH];
        const cheCluster = ctx[context_1.EclipseCheContext.CUSTOM_CR] || ctx[context_1.EclipseCheContext.DEFAULT_CR];
        for (const cr of [cheClusterPatch, cheCluster]) {
            const fieldValue = fieldPath.split('.').reduce((acc, prop) => {
                return acc === null || acc === void 0 ? void 0 : acc[prop];
            }, cr);
            if (fieldValue !== undefined) {
                return fieldValue;
            }
        }
    }
    Che.getCheClusterFieldConfigured = getCheClusterFieldConfigured;
    function getCheVersion() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            var _a;
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const flags = context_1.CheCtlContext.getFlags();
            const cheCluster = yield kubeHelper.getCheCluster(flags[flags_1.CHE_NAMESPACE_FLAG]);
            return ((_a = cheCluster === null || cheCluster === void 0 ? void 0 : cheCluster.status) === null || _a === void 0 ? void 0 : _a.cheVersion) || 'NOT_FOUND';
        });
    }
    Che.getCheVersion = getCheVersion;
    function buildDashboardURL(cheUrl) {
        return cheUrl.endsWith('/') ? `${cheUrl}dashboard/` : `${cheUrl}/dashboard/`;
    }
    Che.buildDashboardURL = buildDashboardURL;
    function getCheURL(namespace) {
        const ctx = context_1.CheCtlContext.get();
        return ctx[context_1.InfrastructureContext.IS_OPENSHIFT] ? getCheOpenShiftURL(namespace) : getCheK8sURL(namespace);
    }
    Che.getCheURL = getCheURL;
    /**
     * Gets self-signed Che CA certificate from 'self-signed-certificate' secret.
     * If secret doesn't exist, undefined is returned.
     */
    function readCheCaCert(namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const cheCaSecretContent = yield getCheSelfSignedSecretContent(namespace);
            if (!cheCaSecretContent) {
                return;
            }
            const pemBeginHeader = '-----BEGIN CERTIFICATE-----';
            const pemEndHeader = '-----END CERTIFICATE-----';
            const certRegExp = new RegExp(`(^${pemBeginHeader}$(?:(?!${pemBeginHeader}).)*^${pemEndHeader}$)`, 'mgs');
            const certsPem = cheCaSecretContent.match(certRegExp);
            const caCertsPem = [];
            if (certsPem) {
                for (const certPem of certsPem) {
                    const cert = nodeforge.pki.certificateFromPem(certPem);
                    const basicConstraintsExt = cert.getExtension('basicConstraints');
                    if (basicConstraintsExt && basicConstraintsExt.cA) {
                        caCertsPem.push(certPem);
                    }
                }
            }
            return caCertsPem.join('\n');
        });
    }
    Che.readCheCaCert = readCheCaCert;
    /**
     * Retrieves content of Che self-signed-certificate secret or undefined if the secret doesn't exist.
     * Note, it contains certificate chain in pem format.
     */
    function getCheSelfSignedSecretContent(namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            const cheCaSecret = yield kubeHelper.getSecret(eclipse_che_1.EclipseChe.SELF_SIGNED_CERTIFICATE, namespace);
            if (!cheCaSecret) {
                return;
            }
            if (cheCaSecret.data && cheCaSecret.data['ca.crt']) {
                return (0, utls_1.base64Decode)(cheCaSecret.data['ca.crt']);
            }
            throw new Error(`Secret "${eclipse_che_1.EclipseChe.SELF_SIGNED_CERTIFICATE}" has invalid format: "ca.crt" key not found in data.`);
        });
    }
    function getCheK8sURL(namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeHelper = kube_client_1.KubeClient.getInstance();
            if (yield kubeHelper.isIngressExist(eclipse_che_1.EclipseChe.CHE_FLAVOR, namespace)) {
                const hostname = yield kubeHelper.getIngressHost(eclipse_che_1.EclipseChe.CHE_FLAVOR, namespace);
                return `https://${hostname}`;
            }
            throw new Error(`Ingress ${eclipse_che_1.EclipseChe.CHE_FLAVOR} not found`);
        });
    }
    function getCheOpenShiftURL(namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            if (yield openshift_1.OpenShift.isRouteExist(`${eclipse_che_1.EclipseChe.CHE_FLAVOR}`, namespace)) {
                const hostname = yield openshift_1.OpenShift.getRouteHost(eclipse_che_1.EclipseChe.CHE_FLAVOR, namespace);
                return `https://${hostname}`;
            }
            throw new Error(`Route ${eclipse_che_1.EclipseChe.CHE_FLAVOR} not found`);
        });
    }
})(Che || (exports.Che = Che = {}));
//# sourceMappingURL=che.js.map