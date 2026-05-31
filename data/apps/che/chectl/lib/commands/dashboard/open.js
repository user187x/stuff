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
const cli_ux_1 = require("cli-ux");
const context_1 = require("../../context");
const flags_1 = require("../../flags");
const constants_1 = require("../../constants");
const eclipse_che_1 = require("../../tasks/installers/eclipse-che/eclipse-che");
const che_1 = require("../../utils/che");
class Open extends core_1.Command {
    run() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { flags } = yield this.parse(Open);
            yield context_1.CheCtlContext.init(flags, this);
            yield this.config.runHook(constants_1.DEFAULT_ANALYTIC_HOOK_NAME, { command: Open.id, flags });
            try {
                const dashboardUrl = che_1.Che.buildDashboardURL(yield che_1.Che.getCheURL(flags[flags_1.CHE_NAMESPACE_FLAG]));
                core_2.ux.info(`Opening ... ${dashboardUrl}`);
                yield cli_ux_1.default.open(dashboardUrl);
            }
            catch (error) {
                this.error(error);
            }
            this.exit(0);
        });
    }
}
Open.description = `Open ${eclipse_che_1.EclipseChe.PRODUCT_NAME} dashboard`;
Open.flags = {
    help: core_1.Flags.help({ char: 'h' }),
    [flags_1.CHE_NAMESPACE_FLAG]: flags_1.CHE_NAMESPACE,
    [flags_1.TELEMETRY_FLAG]: flags_1.TELEMETRY,
};
exports.default = Open;
//# sourceMappingURL=open.js.map