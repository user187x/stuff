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
exports.PlatformTasks = void 0;
const core_1 = require("@oclif/core");
const crc_1 = require("./crc");
const docker_desktop_1 = require("./docker-desktop");
const k8s_1 = require("./k8s");
const microk8s_1 = require("./microk8s");
const minikube_1 = require("./minikube");
const openshift_1 = require("./openshift");
const context_1 = require("../../context");
const flags_1 = require("../../flags");
const utls_1 = require("../../utils/utls");
/**
 * Platform specific tasks.
 */
var PlatformTasks;
(function (PlatformTasks) {
    function getPreflightCheckTasks() {
        const flags = context_1.CheCtlContext.getFlags();
        if (!flags[flags_1.PLATFORM_FLAG]) {
            return {
                title: 'Platform preflight checklist',
                task: () => {
                    core_1.ux.error('Platform is required', { exit: 1 });
                },
            };
        }
        else if (flags[flags_1.PLATFORM_FLAG] === 'openshift') {
            return {
                title: 'Openshift preflight checklist',
                task: (_ctx) => (0, utls_1.newListr)(openshift_1.OpenshiftTasks.getPreflightCheckTasks()),
            };
        }
        else if (flags[flags_1.PLATFORM_FLAG] === 'crc') {
            return {
                title: 'OpenShift Local preflight checklist',
                task: () => (0, utls_1.newListr)(crc_1.CRCTasks.getPreflightCheckTasks()),
            };
            // platform-factory.ts BEGIN CHE ONLY
        }
        else if (flags[flags_1.PLATFORM_FLAG] === 'minikube') {
            return {
                title: 'Minikube preflight checklist',
                task: () => (0, utls_1.newListr)(minikube_1.MinikubeTasks.getPreflightCheckTasks()),
            };
        }
        else if (flags[flags_1.PLATFORM_FLAG] === 'microk8s') {
            return {
                title: 'MicroK8s preflight checklist',
                task: () => (0, utls_1.newListr)(microk8s_1.MicroK8sTasks.getPeflightCheckTasks()),
            };
        }
        else if (flags[flags_1.PLATFORM_FLAG] === 'k8s') {
            return {
                title: 'Kubernetes preflight checklist',
                task: () => (0, utls_1.newListr)(k8s_1.K8sTasks.getPeflightCheckTasks()),
            };
        }
        else if (flags[flags_1.PLATFORM_FLAG] === 'docker-desktop') {
            return {
                title: 'Docker Desktop preflight checklist',
                task: () => (0, utls_1.newListr)(docker_desktop_1.DockerDesktopTasks.getPreflightCheckTasks()),
            };
            // platform-factory.ts END CHE ONLY
        }
        else {
            return {
                title: 'Platform preflight checklist',
                task: () => {
                    core_1.ux.error(`Platform ${flags[flags_1.PLATFORM_FLAG]} is not supported yet ¯\\_(ツ)_/¯`, { exit: 1 });
                },
            };
        }
    }
    PlatformTasks.getPreflightCheckTasks = getPreflightCheckTasks;
    function getConfigureApiServerForDexTasks() {
        const flags = context_1.CheCtlContext.getFlags();
        if (flags[flags_1.PLATFORM_FLAG] === 'minikube') {
            return minikube_1.MinikubeTasks.configureApiServerForDex();
        }
        else {
            core_1.ux.error(`It is not possible to configure API server for ${flags[flags_1.PLATFORM_FLAG]}.`, { exit: 1 });
        }
    }
    PlatformTasks.getConfigureApiServerForDexTasks = getConfigureApiServerForDexTasks;
})(PlatformTasks || (exports.PlatformTasks = PlatformTasks = {}));
//# sourceMappingURL=platform-tasks.js.map