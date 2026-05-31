"use strict";
/**
 * Copyright (c) 2019-2021 Red Hat, Inc.
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
exports.hook = void 0;
const tslib_1 = require("tslib");
const core_1 = require("@oclif/core");
const config_manager_1 = require("../../api/config-manager");
const segment_adapter_1 = require("./segment-adapter");
const hook = (options) => tslib_1.__awaiter(void 0, void 0, void 0, function* () {
    // In case of disable telemetry by flag not additional configs are enabled.
    if (options.flags && options.flags.telemetry === 'off') {
        return this;
    }
    try {
        const configManager = config_manager_1.ConfigManager.getInstance();
        let segmentTelemetry = configManager.getProperty(segment_adapter_1.SegmentProperties.Telemetry);
        // Prompt question if user allow chectl to collect data anonymous data.
        if (!options.flags.telemetry && !segmentTelemetry) {
            // Do not ask for enabling telemetry in batch mode. Just skip it if the telemetry flag is not set.
            if (options.flags.batch) {
                return;
            }
            const confirmed = yield core_1.ux.confirm('Enable CLI usage data to be sent to Red Hat online services. More info: https://developers.redhat.com/article/tool-data-collection [y/n]');
            segmentTelemetry = confirmed ? 'on' : 'off';
            configManager.setProperty(segment_adapter_1.SegmentProperties.Telemetry, segmentTelemetry);
        }
        // If not confirmed, chectl doesn't collect any data.
        if (segmentTelemetry !== 'on') {
            return;
        }
        const segmentId = segment_adapter_1.SegmentAdapter.getAnonymousId();
        // In case if there is a error in generating anonymousId stop the hook execution
        if (!segmentId) {
            return;
        }
        const segment = new segment_adapter_1.SegmentAdapter({
            // tslint:disable-next-line:no-single-line-block-comment
            segmentWriteKey: (function(){var x=Array.prototype.slice.call(arguments),A=x.shift();return x.reverse().map(function(J,O){return String.fromCharCode(J-A-19-O)}).join('')})(41,197,183,173,141,136,136,137,169,153,180,186,131,176,161,162,174)+(40736).toString(36).toLowerCase()+(24).toString(36).toLowerCase().split('').map(function(W){return String.fromCharCode(W.charCodeAt()+(-39))}).join('')+(function(){var F=Array.prototype.slice.call(arguments),X=F.shift();return F.reverse().map(function(C,T){return String.fromCharCode(C-X-55-T)}).join('')})(37,193,192,172)+(21).toString(36).toLowerCase()+(15).toString(36).toLowerCase().split('').map(function(z){return String.fromCharCode(z.charCodeAt()+(-13))}).join('')+(28).toString(36).toLowerCase().split('').map(function(U){return String.fromCharCode(U.charCodeAt()+(-39))}).join('')+(15771).toString(36).toLowerCase()+(10).toString(36).toLowerCase().split('').map(function(c){return String.fromCharCode(c.charCodeAt()+(-13))}).join('')+(29).toString(36).toLowerCase().split('').map(function(i){return String.fromCharCode(i.charCodeAt()+(-39))}).join('')+(6).toString(36).toLowerCase(),
        }, segmentId);
        yield segment.identifySegmentEvent(segmentId);
        yield segment.trackSegmentEvent(options);
    }
    catch (_a) {
        return this;
    }
});
exports.hook = hook;
//# sourceMappingURL=analytics.js.map