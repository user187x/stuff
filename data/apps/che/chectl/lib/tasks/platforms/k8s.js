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
exports.K8sTasks = void 0;
const context_1 = require("../../context");
const flags_1 = require("../../flags");
const common_tasks_1 = require("../common-tasks");
const utls_1 = require("../../utils/utls");
var K8sTasks;
(function (K8sTasks) {
    function getPeflightCheckTasks() {
        const flags = context_1.CheCtlContext.getFlags();
        return [
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if kubectl is installed', 'kubectl not found', () => (0, utls_1.isCommandExists)('kubectl')),
            common_tasks_1.CommonTasks.getVerifyCommand('Verify domain is set', `--${flags_1.DOMAIN_FLAG} flag needs to be defined`, () => Boolean(flags[flags_1.DOMAIN_FLAG])),
        ];
    }
    K8sTasks.getPeflightCheckTasks = getPeflightCheckTasks;
})(K8sTasks || (exports.K8sTasks = K8sTasks = {}));
//# sourceMappingURL=k8s.js.map