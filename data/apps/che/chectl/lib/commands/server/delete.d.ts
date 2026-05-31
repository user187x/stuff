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
export default class Delete extends Command {
    static description: string;
    static flags: {
        help: import("@oclif/core/lib/interfaces").BooleanFlag<void>;
        chenamespace: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "delete-all": import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
        "delete-namespace": import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
        "listr-renderer": import("@oclif/core/lib/interfaces").OptionFlag<string, import("@oclif/core/lib/interfaces").CustomOptions>;
        telemetry: import("@oclif/core/lib/interfaces").OptionFlag<string | undefined, import("@oclif/core/lib/interfaces").CustomOptions>;
        "skip-kubernetes-health-check": import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
        batch: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
        yes: import("@oclif/core/lib/interfaces").BooleanFlag<boolean>;
    };
    run(): Promise<void>;
    isDeletionConfirmed(flags: any): Promise<boolean>;
}
