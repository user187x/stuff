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
exports.OpenshiftTasks = void 0;
const openshift_1 = require("../../utils/openshift");
const common_tasks_1 = require("../common-tasks");
const utls_1 = require("../../utils/utls");
var OpenshiftTasks;
(function (OpenshiftTasks) {
    /**
     * Returns tasks list which perform preflight platform checks.
     */
    function getPreflightCheckTasks() {
        return [
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if oc is installed', 'oc not found', () => (0, utls_1.isCommandExists)('oc')),
            common_tasks_1.CommonTasks.getVerifyCommand('Verify if openshift is running', 'PLATFORM_NOT_READY: \'oc status\' command failed. Please login with \'oc login\' command and try again.', () => openshift_1.OpenShift.isOpenShiftRunning()),
        ];
    }
    OpenshiftTasks.getPreflightCheckTasks = getPreflightCheckTasks;
})(OpenshiftTasks || (exports.OpenshiftTasks = OpenshiftTasks = {}));
//# sourceMappingURL=openshift.js.map