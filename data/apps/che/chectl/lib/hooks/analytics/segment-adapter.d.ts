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
export interface SegmentConfig {
    segmentWriteKey: string;
    flushAt?: number;
    flushInterval?: number;
}
export interface Flags {
    platform?: string;
    installer?: string;
}
export declare namespace SegmentProperties {
    const Telemetry = "segment.telemetry";
}
/**
 * Class with help methods which help to connect segment and send telemetry data.
 */
export declare class SegmentAdapter {
    private readonly segment;
    private readonly id;
    constructor(segmentConfig: SegmentConfig, segmentId: string);
    /**
     * Returns anonymous id to identify and track chectl events in segment
     * Check if exists an anonymousId in file: $HOME/.redhat/anonymousId and if not generate new one in this location
     */
    static getAnonymousId(): string | undefined;
    /**
     * Identify anonymous user in segment before start to track
     * @param anonymousId Unique identifier
     */
    identifySegmentEvent(anonymousId: string): Promise<void>;
    /**
     * Create a segment track object which includes command properties and some chectl filtred properties
     * @param options chectl information like command or flags.
     * @param segmentID chectl ID generated only if telemetry it is 'on'
     */
    trackSegmentEvent(options: {
        command: string;
        flags: any;
    }): Promise<void>;
    private getSegmentIdentifyTraits;
    /**
     * Returns segment event context. Include platform info or countries from where the app was executed
     * More info: https://segment.com/docs/connections/spec/common/#context
     */
    private getSegmentEventContext;
    private getDistribution;
    private getCountry;
}
