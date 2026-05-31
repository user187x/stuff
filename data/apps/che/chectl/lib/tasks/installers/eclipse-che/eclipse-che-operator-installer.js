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
exports.EclipseCheOperatorInstaller = void 0;
const tslib_1 = require("tslib");
const context_1 = require("../../../context");
const common_tasks_1 = require("../../common-tasks");
const cert_manager_installer_1 = require("../cert-manager-installer");
const flags_1 = require("../../../flags");
const eclipse_che_1 = require("./eclipse-che");
const pod_tasks_1 = require("../../pod-tasks");
const che_cluster_tasks_1 = require("../../che-cluster-tasks");
const eclipse_che_tasks_1 = require("./eclipse-che-tasks");
const utls_1 = require("../../../utils/utls");
const dev_workspace_installer_factory_1 = require("../dev-workspace/dev-workspace-installer-factory");
class EclipseCheOperatorInstaller {
    getDeployTasks() {
        return {
            title: `Deploy ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator`,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)();
                const flags = context_1.CheCtlContext.getFlags();
                tasks.add(dev_workspace_installer_factory_1.DevWorkspaceInstallerFactory.getInstaller().getDeployTasks());
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateServiceAccountTask(true));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateRbacTasks(true));
                if (!flags[flags_1.SKIP_CERT_MANAGER_FLAG]) {
                    tasks.add(cert_manager_installer_1.CertManager.getWaitCertManagerTask());
                    tasks.add(common_tasks_1.CommonTasks.getWaitTask(5000));
                }
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateCertificateTask(true));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateIssuerTask(true));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateServiceTask(true));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateCrdTask(true));
                tasks.add(common_tasks_1.CommonTasks.getWaitTask(5000));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateDeploymentTask(true));
                tasks.add(pod_tasks_1.PodTasks.getPodStartTasks(eclipse_che_1.EclipseChe.CHE_OPERATOR, eclipse_che_1.EclipseChe.CHE_OPERATOR_SELECTOR, flags[flags_1.CHE_NAMESPACE_FLAG]));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateValidatingWebhookTask(true));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateMutatingWebhookTask(true));
                tasks.add(che_cluster_tasks_1.CheClusterTasks.getCreateEclipseCheClusterTask());
                return tasks;
            }),
        };
    }
    getUpdateTasks() {
        return {
            title: `Update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator`,
            task: (_ctx, _task) => {
                const tasks = (0, utls_1.newListr)();
                const flags = context_1.CheCtlContext.getFlags();
                tasks.add(dev_workspace_installer_factory_1.DevWorkspaceInstallerFactory.getInstaller().getUpdateTasks());
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateServiceAccountTask(false));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateRbacTasks(false));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateCertificateTask(false));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateIssuerTask(false));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateServiceTask(false));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateCrdTask(false));
                tasks.add(common_tasks_1.CommonTasks.getWaitTask(5000));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateDeploymentTask(false));
                tasks.add(pod_tasks_1.PodTasks.getWaitLatestReplicaTask(eclipse_che_1.EclipseChe.OPERATOR_DEPLOYMENT_NAME, flags[flags_1.CHE_NAMESPACE_FLAG]));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateValidatingWebhookTask(false));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateOrUpdateMutatingWebhookTask(false));
                tasks.add(che_cluster_tasks_1.CheClusterTasks.getPatchEclipseCheCluster());
                return tasks;
            },
        };
    }
    getPreUpdateTasks() {
        return {
            title: `${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator pre-update check`,
            task: (_ctx, _task) => {
                const flags = context_1.CheCtlContext.getFlags();
                const tasks = (0, utls_1.newListr)();
                tasks.add(pod_tasks_1.PodTasks.getDeploymentExistanceTask(eclipse_che_1.EclipseChe.OPERATOR_DEPLOYMENT_NAME, flags[flags_1.CHE_NAMESPACE_FLAG]));
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getDiscoverUpgradeImagePathTask());
                return tasks;
            },
        };
    }
    getDeleteTasks() {
        return {
            title: `Uninstall ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator`,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const flags = context_1.CheCtlContext.getFlags();
                const tasks = (0, utls_1.newListr)();
                if (flags[flags_1.DELETE_ALL_FLAG]) {
                    tasks.add(dev_workspace_installer_factory_1.DevWorkspaceInstallerFactory.getInstaller().getDeleteTasks());
                }
                tasks.add(yield eclipse_che_tasks_1.EclipseCheTasks.getDeleteClusterScopeObjectsTask());
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getDeleteEclipseCheResourcesTask());
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getDeleteNetworksTask());
                tasks.add(yield eclipse_che_tasks_1.EclipseCheTasks.getDeleteWorkloadsTask());
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getDeleteRbacTask());
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getDeleteCertificatesTask());
                return tasks;
            }),
        };
    }
}
exports.EclipseCheOperatorInstaller = EclipseCheOperatorInstaller;
//# sourceMappingURL=eclipse-che-operator-installer.js.map