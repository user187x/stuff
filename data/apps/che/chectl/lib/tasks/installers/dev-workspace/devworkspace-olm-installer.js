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
exports.DevWorkspaceOlmInstaller = void 0;
const tslib_1 = require("tslib");
const context_1 = require("../../../context");
const dev_workspace_tasks_1 = require("./dev-workspace-tasks");
const dev_workspace_1 = require("./dev-workspace");
const olm_tasks_1 = require("../../olm-tasks");
const common_tasks_1 = require("../../common-tasks");
const utls_1 = require("../../../utils/utls");
class DevWorkspaceOlmInstaller {
    getDeployTasks() {
        return {
            title: `Install ${dev_workspace_1.DevWorkspace.PRODUCT_NAME} operator`,
            task: (ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)();
                // Create CatalogSource to deploy a community version of Dev Workspace operator for Eclipse Che.
                // Otherwise, CatalogSource must be pre-created.
                if ((0, utls_1.isCheFlavor)()) {
                    tasks.add(olm_tasks_1.OlmTasks.getCreateCatalogSourceTask(ctx[context_1.DevWorkspaceContext.CATALOG_SOURCE_NAME], ctx[context_1.InfrastructureContext.OPENSHIFT_MARKETPLACE_NAMESPACE], ctx[context_1.DevWorkspaceContext.CATALOG_SOURCE_IMAGE]));
                }
                tasks.add(olm_tasks_1.OlmTasks.getCreateSubscriptionTask(dev_workspace_1.DevWorkspace.SUBSCRIPTION, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE], ctx[context_1.DevWorkspaceContext.CATALOG_SOURCE_NAME], ctx[context_1.InfrastructureContext.OPENSHIFT_MARKETPLACE_NAMESPACE], dev_workspace_1.DevWorkspace.PACKAGE, ctx[context_1.DevWorkspaceContext.CHANNEL], ctx[context_1.EclipseCheContext.APPROVAL_STRATEGY]));
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getWaitDevWorkspaceTask());
                return tasks;
            }),
        };
    }
    getDeleteTasks() {
        return {
            title: `Uninstall ${dev_workspace_1.DevWorkspace.PRODUCT_NAME} operator`,
            task: (ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)();
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteWebhooksTask());
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteCustomResourcesTasks());
                tasks.add(yield olm_tasks_1.OlmTasks.getDeleteSubscriptionAndCatalogSourceTask(dev_workspace_1.DevWorkspace.PACKAGE, dev_workspace_1.DevWorkspace.CSV_PREFIX, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]));
                tasks.add(yield dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteWorkloadsTask());
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteServicesTask());
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteRbacTask());
                return tasks;
            }),
        };
    }
    getPreUpdateTasks() {
        return common_tasks_1.CommonTasks.getDisabledTask();
    }
    getUpdateTasks() {
        return common_tasks_1.CommonTasks.getDisabledTask();
    }
}
exports.DevWorkspaceOlmInstaller = DevWorkspaceOlmInstaller;
//# sourceMappingURL=devworkspace-olm-installer.js.map