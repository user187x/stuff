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
export declare class CheLogsReader {
    private kubeHelper;
    constructor();
    /**
     * Reads logs from pods that match a given selector.
     */
    readPodLog(namespace: string, podLabelSelector: string | undefined, directory: string, follow: boolean): Promise<void>;
    /**
     * Reads containers logs inside pod that match a given selector.
     */
    private readNamespacedPodLog;
    /**
     * Reads all namespace events and store into a file.
     */
    readNamespaceEvents(namespace: string, directory: string, follow: boolean): Promise<void>;
    private formatEvent;
    private watchNamespacedPods;
    /**
     * Indicates if pod matches given labels.
     */
    private matchLabels;
    /**
     * Returns containers names.
     */
    private getContainers;
    /**
     * Reads pod log from a specific container of the pod.
     */
    private doReadNamespacedPodLog;
    private doCreateLogFile;
}
