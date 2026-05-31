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
exports.EclipseCheInstallerFactory = void 0;
const context_1 = require("../../../context");
const eclipse_che_olm_installer_1 = require("./eclipse-che-olm-installer");
const eclipse_che_operator_installer_1 = require("./eclipse-che-operator-installer");
/**
 * Installer factory.
 */
class EclipseCheInstallerFactory {
    static getInstaller() {
        const ctx = context_1.CheCtlContext.get();
        if (ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
            return new eclipse_che_olm_installer_1.EclipseCheOlmInstaller();
        }
        return new eclipse_che_operator_installer_1.EclipseCheOperatorInstaller();
    }
}
exports.EclipseCheInstallerFactory = EclipseCheInstallerFactory;
//# sourceMappingURL=eclipse-che-installer-factory.js.map