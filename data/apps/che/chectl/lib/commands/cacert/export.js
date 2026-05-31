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
const fs = require("fs-extra");
const os = require("node:os");
const path = require("node:path");
const context_1 = require("../../context");
const flags_1 = require("../../flags");
const constants_1 = require("../../constants");
const eclipse_che_1 = require("../../tasks/installers/eclipse-che/eclipse-che");
const command_utils_1 = require("../../utils/command-utils");
const che_1 = require("../../utils/che");
class Export extends core_1.Command {
    run() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { flags } = yield this.parse(Export);
            yield context_1.CheCtlContext.init(flags, this);
            yield this.config.runHook(constants_1.DEFAULT_ANALYTIC_HOOK_NAME, { command: Export.id, flags });
            try {
                const cheCaCert = yield che_1.Che.readCheCaCert(flags[flags_1.CHE_NAMESPACE_FLAG]);
                if (cheCaCert) {
                    const targetFile = this.getTargetFile(flags[flags_1.DESTINATION_FLAG]);
                    fs.writeFileSync(targetFile, cheCaCert);
                    this.log(`${eclipse_che_1.EclipseChe.PRODUCT_NAME} self-signed CA certificate is exported to ${targetFile}`);
                }
                else {
                    this.log('Self signed certificate secret not found. Is commonly trusted certificate used?');
                }
            }
            catch (err) {
                this.error((0, command_utils_1.wrapCommandError)(err));
            }
        });
    }
    getTargetFile(destination) {
        if (!destination) {
            return path.join(os.tmpdir(), eclipse_che_1.EclipseChe.DEFAULT_CA_CERT_FILE_NAME);
        }
        if (fs.existsSync(destination)) {
            return fs.lstatSync(destination).isDirectory() ? path.join(destination, eclipse_che_1.EclipseChe.DEFAULT_CA_CERT_FILE_NAME) : destination;
        }
        throw new Error(`Path \'${destination}\' doesn't exist.`);
    }
}
Export.description = `Retrieves ${eclipse_che_1.EclipseChe.PRODUCT_NAME} self-signed certificate`;
Export.flags = {
    help: core_1.Flags.help({ char: 'h' }),
    [flags_1.CHE_NAMESPACE_FLAG]: flags_1.CHE_NAMESPACE,
    [flags_1.LISTR_RENDERER_FLAG]: flags_1.LISTR_RENDERER,
    [flags_1.TELEMETRY_FLAG]: flags_1.TELEMETRY,
    [flags_1.DESTINATION_FLAG]: flags_1.DESTINATION,
};
exports.default = Export;
//# sourceMappingURL=export.js.map