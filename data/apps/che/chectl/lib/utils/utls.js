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
exports.base64Decode = base64Decode;
exports.sleep = sleep;
exports.newError = newError;
exports.getProjectName = getProjectName;
exports.getProjectVersion = getProjectVersion;
exports.safeLoadFromYamlFile = safeLoadFromYamlFile;
exports.getEmbeddedTemplatesDirectory = getEmbeddedTemplatesDirectory;
exports.addTrailingSlash = addTrailingSlash;
exports.getImageNameAndTag = getImageNameAndTag;
exports.newListr = newListr;
exports.isPartOfEclipseChe = isPartOfEclipseChe;
exports.isCheFlavor = isCheFlavor;
exports.isCommandExists = isCommandExists;
const tslib_1 = require("tslib");
const fs = require("fs-extra");
const os = require("node:os");
const yaml = require("js-yaml");
const path = require("node:path");
const context_1 = require("../context");
const ListrModule = require("listr");
const flags_1 = require("../flags");
// Support both CJS (Listr is the constructor) and ESM interop (Listr.default)
const Listr = typeof ListrModule === 'function' ? ListrModule : ListrModule.default;
const eclipse_che_1 = require("../tasks/installers/eclipse-che/eclipse-che");
const constants_1 = require("../constants");
const commandExists = require("command-exists");
const execa = require("execa");
const pkjson = require('../../package.json');
function base64Decode(arg) {
    return Buffer.from(arg, 'base64').toString('ascii');
}
function sleep(ms) {
    return new Promise(resolve => {
        setTimeout(resolve, ms);
    });
}
function newError(message, cause) {
    const error = new Error(message);
    error.cause = cause;
    error.stack += `\nCause: ${cause.stack}`;
    return error;
}
function getProjectName() {
    return pkjson.name;
}
function getProjectVersion() {
    return pkjson.version;
}
function safeLoadFromYamlFile(filePath) {
    return yaml.load(fs.readFileSync(filePath).toString());
}
function getEmbeddedTemplatesDirectory() {
    // Embedded templates are located in the templates directory that is in the project/installation root:
    // chectl
    //  |- templates
    //  |- src
    //  |   |- utls.ts
    //  |  ...
    //  |- lib
    //  |   |- util.js
    // ... ...
    // __dirname is
    //   project_root/src if dev mode,
    //   installation_root/lib if run from an installed location
    return path.join(__dirname, '..', '..', 'templates');
}
function addTrailingSlash(url) {
    if (url.endsWith('/')) {
        return url;
    }
    return url + '/';
}
function getImageNameAndTag(image) {
    let imageName;
    let imageTag;
    if (image.includes('@')) {
        // Image is referenced via a digest
        const index = image.indexOf('@');
        imageName = image.slice(0, Math.max(0, index));
        imageTag = image.slice(Math.max(0, index + 1));
    }
    else {
        // Image is referenced via a tag
        const lastColonIndex = image.lastIndexOf(':');
        if (lastColonIndex === -1) {
            // Image name without a tag
            imageName = image;
            imageTag = 'latest';
        }
        else {
            const beforeLastColon = image.slice(0, Math.max(0, lastColonIndex));
            const afterLastColon = image.slice(Math.max(0, lastColonIndex + 1));
            if (afterLastColon.includes('/')) {
                // The colon is for registry port and not for a tag
                imageName = image;
                imageTag = 'latest';
            }
            else {
                // The colon separates image name from the tag
                imageName = beforeLastColon;
                imageTag = afterLastColon;
            }
        }
    }
    return [imageName, imageTag];
}
function newListr(tasks, collapse = false) {
    const flags = context_1.CheCtlContext.getFlags();
    const options = { renderer: flags[flags_1.LISTR_RENDERER_FLAG], collapse };
    return new Listr(tasks, options);
}
function isPartOfEclipseChe(resource) {
    var _a, _b;
    return ((_b = (_a = resource === null || resource === void 0 ? void 0 : resource.metadata) === null || _a === void 0 ? void 0 : _a.labels) === null || _b === void 0 ? void 0 : _b['app.kubernetes.io/part-of']) === 'che.eclipse.org';
}
function isCheFlavor() {
    return eclipse_che_1.EclipseChe.CHE_FLAVOR === constants_1.CHE;
}
function isCommandExists(commandName) {
    return tslib_1.__awaiter(this, void 0, void 0, function* () {
        if (commandExists.sync(commandName)) {
            return true;
        }
        // commandExists.sync fails if not executable command exists in the same directory.
        // Double check without accessing local file.
        // The check above there is for backward compatability.
        const whereCommand = os.platform() === 'win32' ? 'where' : 'whereis';
        try {
            yield execa(whereCommand, [commandName]);
            return true;
        }
        catch (_a) { }
        return false;
    });
}
//# sourceMappingURL=utls.js.map