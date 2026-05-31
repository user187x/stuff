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
exports.EclipseCheOlmInstaller = void 0;
const tslib_1 = require("tslib");
const context_1 = require("../../../context");
const eclipse_che_tasks_1 = require("./eclipse-che-tasks");
const eclipse_che_1 = require("./eclipse-che");
const che_cluster_tasks_1 = require("../../che-cluster-tasks");
const olm_tasks_1 = require("../../olm-tasks");
const flags_1 = require("../../../flags");
const utls_1 = require("../../../utils/utls");
const dev_workspace_installer_factory_1 = require("../dev-workspace/dev-workspace-installer-factory");
const common_tasks_1 = require("../../common-tasks");
const dev_workspace_1 = require("../dev-workspace/dev-workspace");
const che_1 = require("../../../utils/che");
const pod_tasks_1 = require("../../pod-tasks");
class EclipseCheOlmInstaller {
    getDeployTasks() {
        return {
            title: `Deploy ${eclipse_che_1.EclipseChe.PRODUCT_NAME}`,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)();
                yield this.addCommonInstallTasks(tasks);
                tasks.add(olm_tasks_1.OlmTasks.getSetCustomEclipseCheOperatorImageTask());
                tasks.add(olm_tasks_1.OlmTasks.getCreatePrometheusRBACTask());
                tasks.add(olm_tasks_1.OlmTasks.getFetchCheClusterSampleTask());
                tasks.add(che_cluster_tasks_1.CheClusterTasks.getCreateEclipseCheClusterTask());
                return tasks;
            }),
        };
    }
    getPreUpdateTasks() {
        return common_tasks_1.CommonTasks.getDisabledTask();
    }
    getUpdateTasks() {
        return {
            title: `Update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator`,
            task: (ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)();
                const flags = context_1.CheCtlContext.getFlags();
                if (ctx[context_1.EclipseCheContext.CREATE_CATALOG_SOURCE_AND_SUBSCRIPTION]) {
                    if (!flags[flags_1.SKIP_DEV_WORKSPACE_FLAG]) {
                        tasks.add(yield olm_tasks_1.OlmTasks.getDeleteSubscriptionAndCatalogSourceTask(dev_workspace_1.DevWorkspace.PACKAGE, dev_workspace_1.DevWorkspace.CSV_PREFIX, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]));
                    }
                    tasks.add(yield olm_tasks_1.OlmTasks.getDeleteSubscriptionAndCatalogSourceTask(eclipse_che_1.EclipseChe.PACKAGE, eclipse_che_1.EclipseChe.CSV_PREFIX, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]));
                    yield this.addCommonInstallTasks(tasks);
                }
                tasks.add(olm_tasks_1.OlmTasks.getSetCustomEclipseCheOperatorImageTask());
                tasks.add(olm_tasks_1.OlmTasks.getApproveInstallPlanTask(eclipse_che_1.EclipseChe.SUBSCRIPTION));
                tasks.add(che_cluster_tasks_1.CheClusterTasks.getPatchEclipseCheCluster());
                return tasks;
            }),
        };
    }
    getDeleteTasks() {
        return {
            title: `Uninstall ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator`,
            task: (ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const flags = context_1.CheCtlContext.getFlags();
                const tasks = (0, utls_1.newListr)();
                if (flags[flags_1.DELETE_ALL_FLAG]) {
                    tasks.add(dev_workspace_installer_factory_1.DevWorkspaceInstallerFactory.getInstaller().getDeleteTasks());
                }
                tasks.add(yield olm_tasks_1.OlmTasks.getDeleteSubscriptionAndCatalogSourceTask(eclipse_che_1.EclipseChe.PACKAGE, eclipse_che_1.EclipseChe.CSV_PREFIX, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]));
                tasks.add(yield eclipse_che_tasks_1.EclipseCheTasks.getDeleteClusterScopeObjectsTask());
                tasks.add(yield eclipse_che_tasks_1.EclipseCheTasks.getDeleteWorkloadsTask());
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getDeleteRbacTask());
                tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getDeleteEclipseCheResourcesTask());
                if (!(0, utls_1.isCheFlavor)()) {
                    tasks.add(yield eclipse_che_tasks_1.EclipseCheTasks.getDeleteImageContentSourcePolicyTask());
                }
                tasks.add(olm_tasks_1.OlmTasks.getDeleteOperatorsTask());
                return tasks;
            }),
        };
    }
    addCommonInstallTasks(tasks) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const ctx = context_1.CheCtlContext.get();
            const flags = context_1.CheCtlContext.getFlags();
            if (!che_1.Che.isRedHatCatalogSources(ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAME])) {
                if (!(0, utls_1.isCheFlavor)() && ctx[context_1.EclipseCheContext.CHANNEL] !== eclipse_che_1.EclipseChe.STABLE_CHANNEL) {
                    tasks.add(eclipse_che_tasks_1.EclipseCheTasks.getCreateImageContentSourcePolicyTask());
                }
                tasks.add(olm_tasks_1.OlmTasks.getCreateCatalogSourceTask(ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAME], ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAMESPACE], ctx[context_1.EclipseCheContext.CATALOG_SOURCE_IMAGE]));
            }
            if (!flags[flags_1.SKIP_DEV_WORKSPACE_FLAG]) {
                tasks.add(dev_workspace_installer_factory_1.DevWorkspaceInstallerFactory.getInstaller().getDeployTasks());
            }
            tasks.add(olm_tasks_1.OlmTasks.getCreateSubscriptionTask(eclipse_che_1.EclipseChe.SUBSCRIPTION, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE], ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAME], ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAMESPACE], ctx[context_1.EclipseCheContext.PACKAGE_NAME], ctx[context_1.EclipseCheContext.CHANNEL], ctx[context_1.EclipseCheContext.APPROVAL_STRATEGY], flags[flags_1.STARTING_CSV_FLAG]));
            tasks.add(pod_tasks_1.PodTasks.getPodStartTasks(eclipse_che_1.EclipseChe.CHE_OPERATOR, eclipse_che_1.EclipseChe.CHE_OPERATOR_SELECTOR, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]));
        });
    }
}
exports.EclipseCheOlmInstaller = EclipseCheOlmInstaller;
//# sourceMappingURL=eclipse-che-olm-installer.js.map