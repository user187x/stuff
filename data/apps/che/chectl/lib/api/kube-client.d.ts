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
import { KubeConfig, V1ClusterRole, V1ClusterRoleBinding, V1ConfigMap, V1ContainerStateTerminated, V1ContainerStateWaiting, V1Deployment, CoreV1Event, CoreV1EventList, V1Ingress, V1Namespace, V1Pod, V1PodCondition, V1PodList, V1Role, V1RoleBinding, V1Secret, V1Service, V1ServiceAccount, V1ServiceList, V1CustomResourceDefinition, V1ValidatingWebhookConfiguration, V1MutatingWebhookConfiguration } from '@kubernetes/client-node';
import { Cluster } from '@kubernetes/client-node/dist/config_types';
import { V1Certificate } from './types/cert-manager';
import { CatalogSource, ClusterServiceVersion, InstallPlan, Subscription } from './types/olm';
import { CheCluster } from './types/che-cluster';
export declare class KubeClient {
    protected readonly podWaitTimeout: number;
    protected readonly podReadyTimeout: number;
    private readonly kubeConfig;
    private constructor();
    static getInstance(): KubeClient;
    getKubeConfig(): KubeConfig;
    getCurrentContext(): string;
    checkKubeApi(): Promise<void>;
    requestKubeHealthz(currentCluster: Cluster, token?: string): Promise<void>;
    /**
     * Retrieve the default token from the default serviceAccount.
     */
    getDefaultServiceAccountToken(): Promise<string>;
    applyResource(yamlPath: string, opts?: string): Promise<void>;
    createNamespace(namespace: V1Namespace): Promise<void>;
    waitNamespaceActive(name: string, intervalMs?: number, timeoutMs?: number): Promise<void>;
    deleteService(name: string, namespace: string): Promise<void>;
    getServicesBySelector(labelSelector: string, namespace: string): Promise<V1ServiceList>;
    isServiceAccountExist(name: string, namespace: string): Promise<boolean>;
    deleteServiceAccount(name: string, namespace: string): Promise<void>;
    createServiceAccount(serviceAccount: V1ServiceAccount, namespace: string): Promise<void>;
    replaceServiceAccount(name: string, serviceAccount: V1ServiceAccount, namespace: string): Promise<void>;
    isRoleExist(name: string, namespace: string): Promise<boolean>;
    isClusterRoleExist(name: string): Promise<boolean>;
    createRole(role: V1Role, namespace: string): Promise<void>;
    replaceRole(role: V1Role, namespace: string): Promise<void>;
    createClusterRole(clusterRole: V1ClusterRole): Promise<void>;
    replaceClusterRole(custerRole: V1ClusterRole): Promise<void>;
    deleteRole(name: string, namespace: string): Promise<void>;
    getPodListByLabel(namespace: string, labelSelector: string): Promise<V1Pod[]>;
    deleteClusterRole(name: string): Promise<void>;
    isRoleBindingExist(name: string, namespace: string): Promise<boolean>;
    isValidatingWebhookConfigurationExists(name: string): Promise<boolean>;
    replaceValidatingWebhookConfiguration(name: string, webhook: V1ValidatingWebhookConfiguration): Promise<void>;
    createValidatingWebhookConfiguration(webhook: V1ValidatingWebhookConfiguration): Promise<void>;
    deleteValidatingWebhookConfiguration(name: string): Promise<void>;
    isMutatingWebhookConfigurationExists(name: string): Promise<boolean>;
    replaceVMutatingWebhookConfiguration(name: string, webhook: V1MutatingWebhookConfiguration): Promise<void>;
    createMutatingWebhookConfiguration(webhook: V1MutatingWebhookConfiguration): Promise<void>;
    deleteMutatingWebhookConfiguration(name: string): Promise<void>;
    isClusterRoleBindingExist(name: string): Promise<boolean>;
    createRoleBinding(roleBinding: V1RoleBinding, namespace: string): Promise<void>;
    replaceRoleBinding(roleBinding: V1RoleBinding, namespace: string): Promise<void>;
    createClusterRoleBinding(clusterRoleBinding: V1ClusterRoleBinding): Promise<void>;
    replaceClusterRoleBinding(clusterRoleBinding: V1ClusterRoleBinding): Promise<void>;
    deleteRoleBinding(name: string, namespace: string): Promise<void>;
    deleteClusterRoleBinding(name: string): Promise<void>;
    getConfigMap(name: string, namespace: string): Promise<V1ConfigMap | undefined>;
    listConfigMaps(namespace: string, labelSelector?: string): Promise<V1ConfigMap[]>;
    getConfigMapValue(name: string, namespace: string, key: string): Promise<string | undefined>;
    createConfigMap(configMap: V1ConfigMap, namespace: string): Promise<void>;
    deleteConfigMap(name: string, namespace: string): Promise<void>;
    deleteSecret(name: string, namespace: string): Promise<void>;
    getNamespace(namespace: string): Promise<V1Namespace | undefined>;
    patchNamespacedCustomObject(name: string, namespace: string, patch: any, resourceAPIGroup: string, resourceAPIVersion: string, resourcePlural: string): Promise<any | undefined>;
    getClusterCustomObject(group: string, version: string, plural: string, name: any): Promise<any>;
    createClusterCustomObject(group: string, version: string, plural: string, body: any): Promise<void>;
    deleteClusterCustomObject(group: string, version: string, plural: string, name: string): Promise<void>;
    getPodWaitingState(namespace: string, selector: string, desiredPhase: string): Promise<V1ContainerStateWaiting | undefined>;
    getPodLastTerminatedState(namespace: string, selector: string): Promise<V1ContainerStateTerminated | undefined>;
    getPodCondition(namespace: string, selector: string, conditionType: string): Promise<V1PodCondition[]>;
    getPodReadyConditionStatus(selector: string, namespace: string, allowMultiple: boolean): Promise<string | undefined>;
    waitForPodReady(selector: string, namespace: string, allowMultiple?: boolean, intervalMs?: number, timeoutMs?: number): Promise<void>;
    waitUntilPodIsDeleted(selector: string, namespace: string, intervalMs?: number, timeoutMs?: number): Promise<void>;
    waitLatestReplica(name: string, namespace: string, intervalMs?: number, timeoutMs?: number): Promise<void>;
    isDeploymentExist(name: string, namespace: string): Promise<boolean>;
    replaceConfigMap(name: string, configMap: V1ConfigMap, namespace: string): Promise<void>;
    isConfigMapExists(name: string, namespace: string): Promise<boolean>;
    scaleDeployment(name: string, namespace: string, replicas: number): Promise<void>;
    createDeployment(deployment: V1Deployment, namespace: string): Promise<void>;
    replaceService(name: string, service: V1Service, namespace: string): Promise<void>;
    isServiceExists(name: string, namespace: string): Promise<boolean>;
    createService(service: V1Service, namespace: string): Promise<void>;
    deletePod(name: string, namespace: string): Promise<void>;
    replaceDeployment(name: string, deployment: V1Deployment, namespace: string): Promise<void>;
    deleteDeployment(name: string, namespace: string): Promise<void>;
    getDeployment(name: string, namespace: string): Promise<V1Deployment | undefined>;
    createIngress(ingress: V1Ingress, namespace: string): Promise<void>;
    isIngressExist(name: string, namespace: string): Promise<boolean>;
    createCustomResourceDefinition(crd: V1CustomResourceDefinition): Promise<void>;
    replaceCustomResourceDefinition(crd: V1CustomResourceDefinition): Promise<void>;
    getCustomResourceDefinition(name: string): Promise<any | undefined>;
    getCheCluster(namespace: string): Promise<CheCluster | undefined>;
    getAllCheClusters(): Promise<any[]>;
    isCheClusterAPIV2(checluster: any): boolean;
    deleteAllCustomResourcesAndCrd(crdName: string, apiGroup: string, version: string, plural: string): Promise<void>;
    createNamespacedCustomObject(namespace: string, group: string, version: string, plural: string, body: any, handleWebhookAvailabilityError: boolean): Promise<void>;
    listNamespacedCustomObject(resourceAPIGroup: string, resourceAPIVersion: string, namespace: string, resourcePlural: string): Promise<any[]>;
    listClusterCustomObject(resourceAPIGroup: string, resourceAPIVersion: string, resourcePlural: string): Promise<any[]>;
    list(resourceAPIGroup: string, resourceAPIVersion: string, namespace: string | undefined, resourcePlural: string): Promise<any[]>;
    isCatalogSourceExists(name: string, namespace: string): Promise<boolean>;
    getCatalogSource(name: string, namespace: string): Promise<CatalogSource | undefined>;
    createCatalogSource(catalogSource: CatalogSource, namespace: string): Promise<void>;
    waitCatalogSource(name: string, namespace: string): Promise<CatalogSource>;
    deleteCatalogSource(name: string, namespace: string): Promise<void>;
    createOperatorSubscription(subscription: Subscription, namespace: string): Promise<void>;
    getOperatorSubscriptionByPackageInNamespace(packageName: string, namespace: string): Promise<Subscription | undefined>;
    getOperatorSubscription(name: string, namespace: string): Promise<Subscription | undefined>;
    waitInstalledCSVInSubscription(name: string, namespace: string): Promise<string>;
    waitCSVStatusPhase(name: string, namespace: string): Promise<string>;
    deleteOperatorSubscription(name: string, namespace: string): Promise<void>;
    waitOperatorSubscriptionReadyForApproval(name: string, namespace: string): Promise<InstallPlan>;
    approveOperatorInstallationPlan(name: string, namespace: string): Promise<void>;
    waitOperatorInstallPlan(name: string, namespace: string): Promise<any>;
    getCSV(name: string, namespace: string): Promise<ClusterServiceVersion | undefined>;
    getCSVWithPrefix(namePrefix: string, namespace: string): Promise<ClusterServiceVersion[]>;
    patchClusterServiceVersion(name: string, namespace: string, jsonPatch: any[]): Promise<ClusterServiceVersion>;
    deleteClusterServiceVersion(name: string, namespace: string): Promise<void>;
    deleteCustomResourceDefinition(name: string): Promise<void>;
    deleteNamespace(namespace: string): Promise<void>;
    deleteCertificate(name: string, namespace: string): Promise<void>;
    deleteIssuer(name: string, namespace: string): Promise<void>;
    createCertificate(certificate: V1Certificate, namespace: string): Promise<void>;
    replaceCertificate(name: string, certificate: V1Certificate, namespace: string): Promise<void>;
    isCertificateExists(name: string, namespace: string): Promise<boolean>;
    createIssuer(issuer: any, namespace: string): Promise<void>;
    replaceIssuer(name: string, issuer: any, namespace: string): Promise<void>;
    isIssuerExists(name: string, namespace: string): Promise<boolean>;
    deleteOperator(name: string): Promise<void>;
    deleteLease(name: string, namespace: string): Promise<void>;
    getIngressHost(name: string, namespace: string): Promise<string>;
    getSecret(name: string, namespace: string): Promise<V1Secret | undefined>;
    isSecretExists(name: string, namespace: string): Promise<boolean>;
    /**
     * Creates a secret with given name and data.
     * Data should not be base64 encoded.
     */
    createSecret(name: string, namespace: string, data: {
        [key: string]: string;
    }): Promise<V1Secret | undefined>;
    /**
     * Awaits secret to be present and contain non-empty data fields specified in dataKeys parameter.
     */
    waitSecret(name: string, namespace: string, dataKeys?: string[]): Promise<void>;
    listNamespacedPod(namespace: string, fieldSelector?: string, labelSelector?: string): Promise<V1PodList>;
    listNamespacedEvent(namespace: string): Promise<CoreV1EventList>;
    watchNamespacedEvents(namespace: string, callback: (event: CoreV1Event) => void, onError?: (err: any) => void): Promise<void>;
    /**
     * Reads log by chunk and writes into a file.
     */
    readNamespacedPodLog(pod: string, namespace: string, container: string, filename: string, follow: boolean): Promise<void>;
    /**
     * Forwards port, based on the example
     * https://github.com/kubernetes-client/javascript/blob/master/examples/typescript/port-forward/port-forward.ts
     */
    portForward(podName: string, namespace: string, port: number): Promise<void>;
    watch(path: string, fieldSelector: string, processObj: (obj: any) => any | undefined, errMsg: string, timeout: number): Promise<any>;
    watchAndRetryOnError(path: string, fieldSelector: string, processObj: (obj: any) => any | undefined, errMsg: string, timeout: number): Promise<any>;
    private wrapK8sClientError;
    private isWebhookAvailabilityError;
    private isStorageIsReInitializingError;
    private isTooManyRequestsError;
}
