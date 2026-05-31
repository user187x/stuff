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
exports.OpenShift = void 0;
const tslib_1 = require("tslib");
const execa = require("execa");
var OpenShift;
(function (OpenShift) {
    function isOpenShiftRunning() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { exitCode } = yield execa('oc', ['status', '--namespace', 'default'], { timeout: 60000, reject: false });
            return exitCode === 0;
        });
    }
    OpenShift.isOpenShiftRunning = isOpenShiftRunning;
    function getRouteHost(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const command = 'oc';
            const args = ['get', 'route', '--namespace', namespace, '-o', `jsonpath={range.items[?(.metadata.name=='${name}')]}{.spec.host}{end}`];
            const { stdout } = yield execa(command, args, { timeout: 60000 });
            return stdout.trim();
        });
    }
    OpenShift.getRouteHost = getRouteHost;
    function isRouteExist(name, namespace) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const command = 'oc';
            const args = ['get', 'route', '--namespace', namespace, '-o', `jsonpath={range.items[?(.metadata.name=='${name}')]}{.metadata.name}{end}`];
            const { stdout } = yield execa(command, args, { timeout: 60000 });
            return stdout.trim().includes(name);
        });
    }
    OpenShift.isRouteExist = isRouteExist;
})(OpenShift || (exports.OpenShift = OpenShift = {}));
//# sourceMappingURL=openshift.js.map