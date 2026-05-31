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
const che_tasks_1 = require("../../tasks/che-tasks");
const eclipse_che_1 = require("../../tasks/installers/eclipse-che/eclipse-che");
const common_tasks_1 = require("../../tasks/common-tasks");
const constants_1 = require("../../constants");
const command_utils_1 = require("../../utils/command-utils");
const utls_1 = require("../../utils/utls");
class Stop extends core_1.Command {
    run() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { flags } = yield this.parse(Stop);
            const ctx = yield context_1.CheCtlContext.initAndGet(flags, this);
            yield this.config.runHook(constants_1.DEFAULT_ANALYTIC_HOOK_NAME, { command: Stop.id, flags });
            const tasks = (0, utls_1.newListr)();
            tasks.add(common_tasks_1.CommonTasks.getTestKubernetesApiTasks());
            tasks.add(che_tasks_1.CheTasks.getScaleCheDownTasks());
            tasks.add(che_tasks_1.CheTasks.getWaitPodsDeletedTasks());
            try {
                yield tasks.run(ctx);
                core_2.ux.log((0, command_utils_1.getCommandSuccessMessage)());
            }
            catch (err) {
                this.error((0, command_utils_1.wrapCommandError)(err));
            }
            if (!flags[flags_1.BATCH_FLAG]) {
                (0, command_utils_1.notifyCommandCompletedSuccessfully)();
            }
            this.exit(0);
        });
    }
}
Stop.description = `stop ${eclipse_che_1.EclipseChe.PRODUCT_NAME} server`;
Stop.flags = {
    help: core_1.Flags.help({ char: 'h' }),
    [flags_1.CHE_NAMESPACE_FLAG]: flags_1.CHE_NAMESPACE,
    [flags_1.LISTR_RENDERER_FLAG]: flags_1.LISTR_RENDERER,
    [flags_1.TELEMETRY_FLAG]: flags_1.TELEMETRY,
    [flags_1.SKIP_KUBE_HEALTHZ_CHECK_FLAG]: flags_1.SKIP_KUBE_HEALTHZ_CHECK,
};
exports.default = Stop;
//# sourceMappingURL=stop.js.map