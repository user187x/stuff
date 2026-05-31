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
export declare const ECLIPSE_CHE_INCUBATOR_ORG = "che-incubator";
export declare const CHECTL_REPO = "chectl";
export interface TagInfo {
    name: string;
    commit: {
        sha: string;
        url: string;
    };
    zipball_url: string;
}
export declare class CheGithubClient {
    private readonly octokit;
    constructor();
    /**
     * Finds the latest tag of format x.y.z, where x,y and z are numbers.
     * @param tags repository tags list returned by octokit
     */
    getLatestTag(tags: TagInfo[]): TagInfo;
    /**
     * Sorts given tags. First is the latest.
     * All tags should use semantic versioning in form x.y.z, where x,y and z are numbers.
     * If a tag is not in the descrbed above format, it will be ignored.
     * @param tags list of tags to sort
     */
    private sortSemanticTags;
    private getCommitData;
    /**
     * Returns date of the given commit
     * @param owner oerganization of the repository
     * @param repo repository name
     * @param commitId ID of commit to get date for
     */
    getCommitDate(owner: string, repo: string, commitId: string): Promise<string>;
}
