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
exports.getCommandSuccessMessage = getCommandSuccessMessage;
exports.wrapCommandError = wrapCommandError;
exports.notifyCommandCompletedSuccessfully = notifyCommandCompletedSuccessfully;
exports.askForChectlUpdateIfNeeded = askForChectlUpdateIfNeeded;
const tslib_1 = require("tslib");
const core_1 = require("@oclif/core");
const notifierModule = require("node-notifier");
const utls_1 = require("./utls");
// Support both CJS (notifier has .notify) and ESM interop (notifier.default)
const notifier = typeof notifierModule.notify === 'function' ?
    notifierModule :
    notifierModule.default;
const context_1 = require("../context");
const fs = require("node:fs");
const eclipse_che_1 = require("../tasks/installers/eclipse-che/eclipse-che");
const execa = require("execa");
const path = require("node:path");
const chectl_version_1 = require("./chectl-version");
/**
 * Returns command success message with execution time.
 */
function getCommandSuccessMessage() {
    const ctx = context_1.CheCtlContext.get();
    if (ctx[context_1.CliContext.CLI_COMMAND_START_TIME]) {
        if (!ctx[context_1.CliContext.CLI_COMMAND_END_TIME]) {
            ctx[context_1.CliContext.CLI_COMMAND_END_TIME] = Date.now();
        }
        const workingTimeInSeconds = Math.round((ctx[context_1.CliContext.CLI_COMMAND_END_TIME] - ctx[context_1.CliContext.CLI_COMMAND_START_TIME]) / 1000);
        const minutes = Math.floor(workingTimeInSeconds / 60);
        const seconds = (workingTimeInSeconds - minutes * 60) % 60;
        const minutesToStr = minutes.toLocaleString([], { minimumIntegerDigits: 2 });
        const secondsToStr = seconds.toLocaleString([], { minimumIntegerDigits: 2 });
        return `Command ${ctx[context_1.CliContext.CLI_COMMAND_ID]} has completed successfully in ${minutesToStr}:${secondsToStr}.`;
    }
    return `Command ${ctx[context_1.CliContext.CLI_COMMAND_ID]} has completed successfully.`;
}
/**
 * Wraps error into command error.
 */
function wrapCommandError(error) {
    const ctx = context_1.CheCtlContext.get();
    const logDirectory = ctx[context_1.CliContext.CLI_COMMAND_LOGS_DIR];
    let commandErrorMessage = `Command ${ctx[context_1.CliContext.CLI_COMMAND_ID]} failed with the error: ${error.message} See details: ${ctx[context_1.CliContext.CLI_ERROR_LOG]}.`;
    if (logDirectory && !isDirEmpty(logDirectory)) {
        commandErrorMessage += ` ${eclipse_che_1.EclipseChe.PRODUCT_NAME} logs: ${logDirectory}.`;
    }
    return (0, utls_1.newError)(commandErrorMessage, error);
}
function notifyCommandCompletedSuccessfully() {
    notifier.notify({
        title: 'chectl',
        message: getCommandSuccessMessage(),
    });
}
function askForChectlUpdateIfNeeded() {
    return tslib_1.__awaiter(this, void 0, void 0, function* () {
        const ctx = context_1.CheCtlContext.get();
        if (yield chectl_version_1.CheCtlVersion.isCheCtlUpdateAvailable(ctx[context_1.CliContext.CLI_CACHE_DIR])) {
            core_1.ux.info(`A more recent version of chectl is available. To deploy the latest version of ${eclipse_che_1.EclipseChe.PRODUCT_NAME}, update the chectl tool first.`);
            if (yield core_1.ux.confirm('Do you want to update chectl now? [y/n]')) {
                const bin = path.join(__dirname, '..', '..', 'bin', (0, utls_1.getProjectName)());
                yield execa(bin, ['update'], { stdout: 'inherit', stderr: 'inherit', timeout: 60000 });
                core_1.ux.exit(0);
            }
        }
    });
}
function isDirEmpty(dirname) {
    try {
        return fs.readdirSync(dirname).length === 0;
        // Fails in case if directory doesn't exist
    }
    catch (_a) {
        return true;
    }
}
//# sourceMappingURL=command-utils.js.map