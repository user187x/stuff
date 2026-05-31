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
exports.DevWorkspaceInstallerFactory = void 0;
const context_1 = require("../../../context");
const devworkspace_olm_installer_1 = require("./devworkspace-olm-installer");
const devworkspace_operator_installer_1 = require("./devworkspace-operator-installer");
/**
 * Installer factory.
 */
class DevWorkspaceInstallerFactory {
    static getInstaller() {
        const ctx = context_1.CheCtlContext.get();
        if (ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
            return new devworkspace_olm_installer_1.DevWorkspaceOlmInstaller();
        }
        return new devworkspace_operator_installer_1.DevWorkspaceOperatorInstaller();
    }
}
exports.DevWorkspaceInstallerFactory = DevWorkspaceInstallerFactory;
//# sourceMappingURL=dev-workspace-installer-factory.js.map