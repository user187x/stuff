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
import { Command } from '@oclif/core';
export default class Update extends Command {
    static description: string;
    static examples: string[];
    static flags: {
        help: import("@oclif/core/lib/interfaces").BooleanFlag<void>;
        chenamespace: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        batch: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
        yes: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
        templates: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "che-operator-image": import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "che-operator-cr-patch-yaml": import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
        "skip-devworkspace-operator": import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
        "skip-kubernetes-health-check": import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
        "skip-version-check": import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
        telemetry: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "listr-renderer": import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
        "olm-channel": import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "package-manifest-name": import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "catalog-source-namespace": import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "catalog-source-name": import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "catalog-source-yaml": import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "catalog-source-image": import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "auto-update": import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
        "starting-csv": import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
    };
    run(): Promise<void>;
    private checkAbilityToUpdateCatalogSource;
    /**
     * Check whether chectl should proceed with update.
     * Asks user for confirmation (unless assume yes is provided).
     * Is applicable to operator installer only.
     * Returns true if chectl can/should proceed with update, false otherwise.
     */
    private checkAbilityToUpdateCheOperatorAndAskUser;
    /**
     * Checks if official operator image is replaced with a newer one.
     * Tags are allowed in format x.y.z or NEXT_TAG.
     * NEXT_TAG is considered the most recent.
     * For example:
     *  (7.22.1, 7.23.0) -> true,
     *  (7.22.1, 7.20.2) -> false,
     *  (7.22.1, NEXT_TAG) -> true,
     *  (NEXT_TAG, 7.20.2) -> false
     * @param oldTag old official operator image tag, e.g. 7.20.1
     * @param newTag new official operator image tag e.g. 7.22.0
     * @returns true if upgrade, false if downgrade
     * @throws error if tags are equal
     */
    private isUpgrade;
}
