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
const tslib_1 = require("tslib");
const core_1 = require("@oclif/core");
const core_2 = require("@oclif/core");
const context_1 = require("../../context");
const flags_1 = require("../../flags");
const constants_1 = require("../../constants");
const che_tasks_1 = require("../../tasks/che-tasks");
const eclipse_che_installer_factory_1 = require("../../tasks/installers/eclipse-che/eclipse-che-installer-factory");
const common_tasks_1 = require("../../tasks/common-tasks");
const client_node_1 = require("@kubernetes/client-node");
const eclipse_che_1 = require("../../tasks/installers/eclipse-che/eclipse-che");
const command_utils_1 = require("../../utils/command-utils");
const utls_1 = require("../../utils/utls");
class Delete extends core_1.Command {
    run() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { flags } = yield this.parse(Delete);
            const ctx = yield context_1.CheCtlContext.initAndGet(flags, this);
            yield this.config.runHook(constants_1.DEFAULT_ANALYTIC_HOOK_NAME, { command: Delete.id, flags });
            const tasks = (0, utls_1.newListr)();
            tasks.add(common_tasks_1.CommonTasks.getTestKubernetesApiTasks());
            tasks.add(common_tasks_1.CommonTasks.getOpenShiftVersionTask());
            tasks.add(eclipse_che_installer_factory_1.EclipseCheInstallerFactory.getInstaller().getDeleteTasks());
            tasks.add(che_tasks_1.CheTasks.getWaitPodsDeletedTasks());
            if (flags[flags_1.DELETE_NAMESPACE_FLAG]) {
                tasks.add(common_tasks_1.CommonTasks.getDeleteNamespaceTask(flags[flags_1.CHE_NAMESPACE_FLAG]));
            }
            if (yield this.isDeletionConfirmed(flags)) {
                try {
                    yield tasks.run(ctx);
                    core_2.ux.log((0, command_utils_1.getCommandSuccessMessage)());
                }
                catch (err) {
                    this.error((0, command_utils_1.wrapCommandError)(err));
                }
            }
            else {
                this.exit(0);
            }
            if (!flags[flags_1.BATCH_FLAG]) {
                (0, command_utils_1.notifyCommandCompletedSuccessfully)();
            }
            this.exit(0);
        });
    }
    isDeletionConfirmed(flags) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const kubeConfig = new client_node_1.KubeConfig();
            kubeConfig.loadFromDefault();
            const cluster = kubeConfig.getCurrentCluster();
            if (!cluster) {
                throw new Error('Failed to get current Kubernetes cluster. Check if the current context is set via kubectl/oc');
            }
            if (!flags[flags_1.BATCH_FLAG] && !flags[flags_1.ASSUME_YES_FLAG]) {
                return core_2.ux.confirm(`You're going to remove ${eclipse_che_1.EclipseChe.PRODUCT_NAME} server in namespace '${flags[flags_1.CHE_NAMESPACE_FLAG]}' on server '${cluster ? cluster.server : ''}'. If you want to continue - press Y`);
            }
            return true;
        });
    }
}
Delete.description = `delete any ${eclipse_che_1.EclipseChe.PRODUCT_NAME} related resource`;
Delete.flags = {
    help: core_1.Flags.help({ char: 'h' }),
    [flags_1.CHE_NAMESPACE_FLAG]: flags_1.CHE_NAMESPACE,
    [flags_1.DELETE_ALL_FLAG]: flags_1.DELETE_ALL,
    [flags_1.DELETE_NAMESPACE_FLAG]: flags_1.DELETE_NAMESPACE,
    [flags_1.LISTR_RENDERER_FLAG]: flags_1.LISTR_RENDERER,
    [flags_1.TELEMETRY_FLAG]: flags_1.TELEMETRY,
    [flags_1.SKIP_KUBE_HEALTHZ_CHECK_FLAG]: flags_1.SKIP_KUBE_HEALTHZ_CHECK,
    [flags_1.BATCH_FLAG]: flags_1.BATCH,
    [flags_1.ASSUME_YES_FLAG]: flags_1.ASSUME_YES,
};
exports.default = Delete;
//# sourceMappingURL=delete.js.map