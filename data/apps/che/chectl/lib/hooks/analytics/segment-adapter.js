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
exports.SegmentAdapter = exports.SegmentProperties = void 0;
const tslib_1 = require("tslib");
const core_1 = require("@oclif/core");
const countries_and_timezones_1 = require("countries-and-timezones");
const fs = require("fs-extra");
const lodash_1 = require("lodash");
const os = require("node:os");
const osLocale = require("os-locale");
const path = require("node:path");
const uuid_1 = require("uuid");
const utls_1 = require("../../utils/utls");
const getos = require("getos");
const node_util_1 = require("node:util");
const Analytics = require('analytics-node');
var SegmentProperties;
(function (SegmentProperties) {
    SegmentProperties.Telemetry = 'segment.telemetry';
})(SegmentProperties || (exports.SegmentProperties = SegmentProperties = {}));
/**
 * Class with help methods which help to connect segment and send telemetry data.
 */
class SegmentAdapter {
    constructor(segmentConfig, segmentId) {
        const { segmentWriteKey } = segmentConfig, options = tslib_1.__rest(segmentConfig, ["segmentWriteKey"]);
        this.segment = new Analytics(segmentWriteKey, options);
        this.id = segmentId;
    }
    /**
     * Returns anonymous id to identify and track chectl events in segment
     * Check if exists an anonymousId in file: $HOME/.redhat/anonymousId and if not generate new one in this location
     */
    static getAnonymousId() {
        const anonymousIdPath = path.join(os.homedir(), '.redhat', 'anonymousId');
        let anonymousId = (0, uuid_1.v4)();
        try {
            if (fs.existsSync(anonymousIdPath)) {
                anonymousId = fs.readFileSync(anonymousIdPath, 'utf8');
            }
            else {
                if (!fs.existsSync(anonymousIdPath)) {
                    fs.mkdirSync(path.join(os.homedir(), '.redhat'));
                }
                fs.writeFileSync(anonymousIdPath, anonymousId, { encoding: 'utf8' });
            }
        }
        catch (error) {
            core_1.ux.debug(`Failed to store anonymousId ${error}`);
        }
        return anonymousId.trim();
    }
    /**
     * Identify anonymous user in segment before start to track
     * @param anonymousId Unique identifier
     */
    identifySegmentEvent(anonymousId) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            this.segment.identify({
                anonymousId,
                traits: yield this.getSegmentIdentifyTraits(),
            });
        });
    }
    /**
     * Create a segment track object which includes command properties and some chectl filtred properties
     * @param options chectl information like command or flags.
     * @param segmentID chectl ID generated only if telemetry it is 'on'
     */
    trackSegmentEvent(options) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            this.segment.track({
                anonymousId: this.id,
                event: options.command.replace(':', ' '),
                context: yield this.getSegmentEventContext(),
                properties: Object.assign(Object.assign({}, (0, lodash_1.pick)(options.flags, ['platform', 'installer'])), { command: options.command, version: (0, utls_1.getProjectVersion)() }),
                // Property which indicate segment will integrate with all configured destinations.
                integrations: {
                    All: true,
                },
            });
        });
    }
    // Returns basic info about identification in segment
    getSegmentIdentifyTraits() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return {
                timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                os_name: os.platform(),
                os_version: os.release(),
                os_distribution: this.getDistribution(),
                locale: osLocale.sync().replace('_', '-'),
            };
        });
    }
    /**
     * Returns segment event context. Include platform info or countries from where the app was executed
     * More info: https://segment.com/docs/connections/spec/common/#context
     */
    getSegmentEventContext() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            return {
                ip: '0.0.0.0',
                locale: osLocale.sync().replace('_', '-'),
                app: {
                    name: (0, utls_1.getProjectName)(),
                },
                os: {
                    name: os.platform(),
                    version: os.release(),
                },
                location: {
                    country: this.getCountry(Intl.DateTimeFormat().resolvedOptions().timeZone),
                },
                timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
            };
        });
    }
    getDistribution() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            if (os.platform() === 'linux') {
                try {
                    const platform = yield (0, node_util_1.promisify)(getos)();
                    return platform.dist;
                }
                catch (_a) {
                    return;
                }
            }
            return;
        });
    }
    getCountry(timeZone) {
        const tz = (0, countries_and_timezones_1.getTimezone)(timeZone);
        if (tz && (tz === null || tz === void 0 ? void 0 : tz.countries)) {
            return tz.countries[0];
        }
        // Probably UTC timezone
        return 'ZZ'; // Unknown country
    }
}
exports.SegmentAdapter = SegmentAdapter;
//# sourceMappingURL=segment-adapter.js.map