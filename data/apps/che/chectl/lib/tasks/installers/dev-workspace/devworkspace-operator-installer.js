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
exports.DevWorkspaceOperatorInstaller = void 0;
const tslib_1 = require("tslib");
const context_1 = require("../../../context");
const common_tasks_1 = require("../../common-tasks");
const dev_workspace_tasks_1 = require("./dev-workspace-tasks");
const flags_1 = require("../../../flags");
const dev_workspace_1 = require("./dev-workspace");
const utls_1 = require("../../../utils/utls");
/**
 * Handle setup of the dev workspace operator controller.
 */
class DevWorkspaceOperatorInstaller {
    constructor() {
        const flags = context_1.CheCtlContext.getFlags();
        this.skip = flags[flags_1.SKIP_DEV_WORKSPACE_FLAG];
    }
    getDeployTasks() {
        return {
            title: `Install ${dev_workspace_1.DevWorkspace.PRODUCT_NAME} operator`,
            skip: () => this.skip,
            task: (ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)();
                tasks.add(common_tasks_1.CommonTasks.getCreateNamespaceTask(ctx[context_1.DevWorkspaceContext.NAMESPACE], {}));
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getCreateOrUpdateDevWorkspaceTask(true));
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getWaitDevWorkspaceTask());
                return tasks;
            }),
        };
    }
    getUpdateTasks() {
        return {
            title: `Update ${dev_workspace_1.DevWorkspace.PRODUCT_NAME} operator`,
            skip: () => this.skip,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)();
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getCreateOrUpdateDevWorkspaceTask(false));
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getWaitDevWorkspaceTask());
                return tasks;
            }),
        };
    }
    getDeleteTasks() {
        return {
            title: `Uninstall ${dev_workspace_1.DevWorkspace.PRODUCT_NAME} operator`,
            skip: () => this.skip,
            task: (_ctx, _task) => tslib_1.__awaiter(this, void 0, void 0, function* () {
                const tasks = (0, utls_1.newListr)();
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteWebhooksTask());
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteCustomResourcesTasks());
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteServicesTask());
                tasks.add(yield dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteWorkloadsTask());
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteRbacTask());
                tasks.add(dev_workspace_tasks_1.DevWorkspacesTasks.getDeleteCertificatesTask());
                return tasks;
            }),
        };
    }
    getPreUpdateTasks() {
        return common_tasks_1.CommonTasks.getDisabledTask();
    }
}
exports.DevWorkspaceOperatorInstaller = DevWorkspaceOperatorInstaller;
//# sourceMappingURL=devworkspace-operator-installer.js.map