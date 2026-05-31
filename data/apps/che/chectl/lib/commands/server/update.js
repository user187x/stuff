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
const tslib_1 = require("tslib");
const core_1 = require("@oclif/core");
const core_2 = require("@oclif/core");
const semver = require("semver");
const context_1 = require("../../context");
const eclipse_che_installer_factory_1 = require("../../tasks/installers/eclipse-che/eclipse-che-installer-factory");
const flags_1 = require("../../flags");
const eclipse_che_1 = require("../../tasks/installers/eclipse-che/eclipse-che");
const constants_1 = require("../../constants");
const command_utils_1 = require("../../utils/command-utils");
const common_tasks_1 = require("../../tasks/common-tasks");
const utls_1 = require("../../utils/utls");
const kube_client_1 = require("../../api/kube-client");
const che_1 = require("../../utils/che");
class Update extends core_1.Command {
    run() {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const { flags } = yield this.parse(Update);
            const ctx = yield context_1.CheCtlContext.initAndGet(flags, this);
            yield this.config.runHook(constants_1.DEFAULT_ANALYTIC_HOOK_NAME, { command: Update.id, flags });
            if (!flags[flags_1.BATCH_FLAG] && ctx[context_1.CliContext.CLI_IS_CHECTL]) {
                yield (0, command_utils_1.askForChectlUpdateIfNeeded)();
            }
            (0, flags_1.checkFlagsCompatability)(flags);
            const eclipseCheInstallerInstaller = eclipse_che_installer_factory_1.EclipseCheInstallerFactory.getInstaller();
            // PreUpdate tasks
            const preUpdateTasks = (0, utls_1.newListr)();
            preUpdateTasks.add(common_tasks_1.CommonTasks.getTestKubernetesApiTasks());
            preUpdateTasks.add(eclipseCheInstallerInstaller.getPreUpdateTasks());
            // Update tasks
            const updateTasks = (0, utls_1.newListr)();
            updateTasks.add(eclipseCheInstallerInstaller.getUpdateTasks());
            // PostUpdate tasks
            const postUpdateTasks = (0, utls_1.newListr)();
            postUpdateTasks.add(common_tasks_1.CommonTasks.getPrintHighlightedMessagesTask());
            try {
                yield preUpdateTasks.run(ctx);
                if (!ctx[context_1.InfrastructureContext.IS_OPENSHIFT]) {
                    if (!(yield this.checkAbilityToUpdateCheOperatorAndAskUser(flags))) {
                        return;
                    }
                }
                else {
                    if (!(yield this.checkAbilityToUpdateCatalogSource(flags))) {
                        return;
                    }
                }
                yield updateTasks.run(ctx);
                yield postUpdateTasks.run(ctx);
                this.log((0, command_utils_1.getCommandSuccessMessage)());
            }
            catch (err) {
                this.error((0, command_utils_1.wrapCommandError)(err));
            }
            if (!flags[flags_1.BATCH_FLAG]) {
                (0, command_utils_1.notifyCommandCompletedSuccessfully)();
            }
        });
    }
    checkAbilityToUpdateCatalogSource(flags) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const ctx = context_1.CheCtlContext.get();
            ctx[context_1.EclipseCheContext.CREATE_CATALOG_SOURCE_AND_SUBSCRIPTION] = false;
            const kubeClient = kube_client_1.KubeClient.getInstance();
            const subscription = yield kubeClient.getOperatorSubscription(eclipse_che_1.EclipseChe.SUBSCRIPTION, ctx[context_1.EclipseCheContext.OPERATOR_NAMESPACE]);
            if (subscription) {
                const catalogSource = yield kubeClient.getCatalogSource(subscription.spec.source, subscription.spec.sourceNamespace);
                if (ctx[context_1.EclipseCheContext.CHANNEL] !== subscription.spec.channel ||
                    ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAME] !== subscription.spec.source ||
                    ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAMESPACE] !== subscription.spec.sourceNamespace ||
                    ctx[context_1.EclipseCheContext.PACKAGE_NAME] !== subscription.spec.name ||
                    ctx[context_1.EclipseCheContext.CATALOG_SOURCE_IMAGE] !== (catalogSource === null || catalogSource === void 0 ? void 0 : catalogSource.spec.image) ||
                    !che_1.Che.isRedHatCatalogSources(ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAME])) {
                    core_2.ux.info('CatalogSource and Subscription will be updated              :');
                    core_2.ux.info('-------------------------------------------------------------');
                    core_2.ux.info(`Current channel                 : ${subscription.spec.channel}`);
                    core_2.ux.info(`Current catalog source          : ${subscription.spec.source}`);
                    core_2.ux.info(`Current catalog source namespace: ${subscription.spec.sourceNamespace}`);
                    if (!che_1.Che.isRedHatCatalogSources(catalogSource === null || catalogSource === void 0 ? void 0 : catalogSource.metadata.name) && (catalogSource === null || catalogSource === void 0 ? void 0 : catalogSource.spec.image)) {
                        core_2.ux.info(`Current catalog source image    : ${catalogSource.spec.image}`);
                    }
                    core_2.ux.info(`Current package name            : ${subscription.spec.name}`);
                    ctx[context_1.EclipseCheContext.CREATE_CATALOG_SOURCE_AND_SUBSCRIPTION] = true;
                }
            }
            else {
                core_2.ux.info('Subscription will be created  :');
                ctx[context_1.EclipseCheContext.CREATE_CATALOG_SOURCE_AND_SUBSCRIPTION] = true;
            }
            if (ctx[context_1.EclipseCheContext.CREATE_CATALOG_SOURCE_AND_SUBSCRIPTION]) {
                core_2.ux.info('-------------------------------------------------------------');
                core_2.ux.info(`New channel                     : ${ctx[context_1.EclipseCheContext.CHANNEL]}`);
                core_2.ux.info(`New catalog source              : ${ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAME]}`);
                core_2.ux.info(`New catalog source namespace    : ${ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAMESPACE]}`);
                if (!che_1.Che.isRedHatCatalogSources(ctx[context_1.EclipseCheContext.CATALOG_SOURCE_NAME]) && ctx[context_1.EclipseCheContext.CATALOG_SOURCE_IMAGE]) {
                    core_2.ux.info(`New catalog source image        : ${ctx[context_1.EclipseCheContext.CATALOG_SOURCE_IMAGE]}`);
                }
                core_2.ux.info(`New package name                : ${ctx[context_1.EclipseCheContext.PACKAGE_NAME]}`);
                if (!flags[flags_1.BATCH_FLAG] && !flags[flags_1.ASSUME_YES_FLAG] && !(yield core_2.ux.confirm('If you want to continue - press Y'))) {
                    core_2.ux.info('Update cancelled by user.');
                    return false;
                }
            }
            return true;
        });
    }
    /**
     * Check whether chectl should proceed with update.
     * Asks user for confirmation (unless assume yes is provided).
     * Is applicable to operator installer only.
     * Returns true if chectl can/should proceed with update, false otherwise.
     */
    checkAbilityToUpdateCheOperatorAndAskUser(flags) {
        return tslib_1.__awaiter(this, void 0, void 0, function* () {
            const ctx = context_1.CheCtlContext.get();
            core_2.ux.info(`Existing ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator: ${ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE]}`);
            core_2.ux.info(`New ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator     : ${ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE]}`);
            if (ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE_NAME] === eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NAME && ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_NAME] === eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NAME) {
                // Official images
                if (ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE] === ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE]) {
                    if (ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_TAG] === eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NEXT_TAG) {
                        core_2.ux.info(`Updating current ${eclipse_che_1.EclipseChe.PRODUCT_NAME} ${eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NEXT_TAG} version to a new one.`);
                        return true;
                    }
                    if (flags[flags_1.CHE_OPERATOR_CR_PATCH_YAML_FLAG]) {
                        // Despite the operator image is the same, CR patch might contain some changes.
                        core_2.ux.info(`Patching existing ${eclipse_che_1.EclipseChe.PRODUCT_NAME} installation.`);
                        return true;
                    }
                    else {
                        core_2.ux.info(`${eclipse_che_1.EclipseChe.PRODUCT_NAME} is already up to date.`);
                        return false;
                    }
                }
                if (this.isUpgrade(ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE_TAG], ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_TAG])) {
                    // Upgrade
                    const currentChectlVersion = (0, utls_1.getProjectVersion)();
                    if (!ctx[context_1.CliContext.CLI_IS_DEV_VERSION] && (ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_TAG] === eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NEXT_TAG || semver.lt(currentChectlVersion, ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_TAG]))) {
                        // Upgrade is not allowed
                        if (ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_TAG] === eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NEXT_TAG) {
                            core_2.ux.warn(`Stable ${(0, utls_1.getProjectName)()} cannot update stable ${eclipse_che_1.EclipseChe.PRODUCT_NAME} to ${eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NEXT_TAG} version`);
                        }
                        else {
                            core_2.ux.warn(`It is not possible to update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} to a newer version using the current '${currentChectlVersion}' version of chectl. Please, update '${(0, utls_1.getProjectName)()}' to a newer version using command '${(0, utls_1.getProjectName)()} update' and then try again.`);
                        }
                        return false;
                    }
                    // Upgrade allowed
                    if (ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_TAG] === eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NEXT_TAG) {
                        core_2.ux.info(`You are going to update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} ${ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE_TAG]} to ${eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NEXT_TAG} version.`);
                    }
                    else {
                        core_2.ux.info(`You are going to update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} ${ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE_TAG]} to ${ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_TAG]}`);
                    }
                }
                else {
                    // Downgrade
                    core_2.ux.error('Downgrading is not supported.', { exit: 1 });
                }
            }
            else {
                // At least one of the images is custom
                // Print message
                if (ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE] === ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE]) {
                    // Despite the image is the same it could be updated image, replace anyway.
                    core_2.ux.info(`You are going to replace ${eclipse_che_1.EclipseChe.PRODUCT_NAME} operator image ${ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE]}.`);
                }
                else if (ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE_NAME] !== eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NAME && ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE_NAME] !== eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NAME) {
                    // Both images are custom
                    core_2.ux.info(`You are going to update ${ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE]} to ${ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE]}`);
                }
                else {
                    // One of the images is offical
                    if (ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE_NAME] === eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NAME) {
                        // Update from offical to custom image
                        core_2.ux.info(`You are going to update official ${ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE]} image with user provided one: ${ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE]}`);
                    }
                    else { // ctx[OperatorImageUpgradeContext.NEW_IMAGE_NAME] === DEFAULT_CHE_OPERATOR_IMAGE_NAME
                        // Update from custom to official image
                        core_2.ux.info(`You are going to update user provided image ${ctx[context_1.OperatorImageUpgradeContext.DEPLOYED_IMAGE]} with official one: ${ctx[context_1.OperatorImageUpgradeContext.NEW_IMAGE]}`);
                    }
                }
            }
            if (!flags[flags_1.BATCH_FLAG] && !flags[flags_1.ASSUME_YES_FLAG] && !(yield core_2.ux.confirm('If you want to continue - press Y'))) {
                core_2.ux.info('Update cancelled by user.');
                return false;
            }
            return true;
        });
    }
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
    isUpgrade(oldTag, newTag) {
        if (oldTag === newTag) {
            throw new Error(`Tags are the same: ${newTag}`);
        }
        let isUpdate = false;
        try {
            isUpdate = semver.gt(newTag, oldTag);
        }
        catch (error) {
            // not to fail unexpectedly
            core_2.ux.debug(`Failed to compare versions '${newTag}' and '${oldTag}': ${error}`);
        }
        // if newTag is NEXT_TAG it is upgrade
        // if oldTag is NEXT_TAG it is downgrade
        // otherwise just compare new and old tags
        // Note, that semver lib doesn't handle text tags and throws an error in case NEXT_TAG is provided for comparation.
        return newTag === eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NEXT_TAG || (oldTag !== eclipse_che_1.EclipseChe.OPERATOR_IMAGE_NEXT_TAG && isUpdate);
    }
}
Update.description = `Update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} server.`;
Update.examples = [
    `# Update ${eclipse_che_1.EclipseChe.PRODUCT_NAME}:\n` +
        'chectl server:update',
    `\n# Update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} in \'eclipse-che\' namespace:\n` +
        'chectl server:update -n eclipse-che',
    `\n# Update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} and update its configuration in the custom resource:\n` +
        `chectl server:update --${flags_1.CHE_OPERATOR_CR_PATCH_YAML_FLAG} patch.yaml`,
    `\n# Update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} from the provided channel:\n` +
        'chectl server:update --olm-channel next',
    `\n# Update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} from the provided CatalogSource and channel:\n` +
        'chectl server:update --olm-channel fast --catalog-source-name MyCatalogName --catalog-source-namespace MyCatalogNamespace',
    `\n# Create CatalogSource based on provided image and update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} from it:\n` +
        'chectl server:update --olm-channel latest --catalog-source-image MyCatalogImage',
    `\n# Create a CatalogSource defined in yaml file and update ${eclipse_che_1.EclipseChe.PRODUCT_NAME} from it:\n` +
        'chectl server:update --olm-channel stable --catalog-source-yaml PATH_TO_CATALOG_SOURCE_YAML',
];
Update.flags = {
    help: core_1.Flags.help({ char: 'h' }),
    [flags_1.CHE_NAMESPACE_FLAG]: flags_1.CHE_NAMESPACE,
    [flags_1.BATCH_FLAG]: flags_1.BATCH,
    [flags_1.ASSUME_YES_FLAG]: flags_1.ASSUME_YES,
    [flags_1.TEMPLATES_FLAG]: flags_1.TEMPLATES,
    [flags_1.CHE_OPERATOR_IMAGE_FLAG]: flags_1.CHE_OPERATOR_IMAGE,
    [flags_1.CHE_OPERATOR_CR_PATCH_YAML_FLAG]: flags_1.CHE_OPERATOR_CR_PATCH_YAML,
    [flags_1.SKIP_DEV_WORKSPACE_FLAG]: flags_1.SKIP_DEV_WORKSPACE,
    [flags_1.SKIP_KUBE_HEALTHZ_CHECK_FLAG]: flags_1.SKIP_KUBE_HEALTHZ_CHECK,
    [flags_1.SKIP_VERSION_CHECK_FLAG]: flags_1.SKIP_VERSION_CHECK,
    [flags_1.TELEMETRY_FLAG]: flags_1.TELEMETRY,
    [flags_1.LISTR_RENDERER_FLAG]: flags_1.LISTR_RENDERER,
    // OLM flags
    [flags_1.OLM_CHANNEL_FLAG]: flags_1.OLM_CHANNEL,
    [flags_1.PACKAGE_MANIFEST_FLAG]: flags_1.PACKAGE_MANIFEST,
    [flags_1.CATALOG_SOURCE_NAMESPACE_FLAG]: flags_1.CATALOG_SOURCE_NAMESPACE,
    [flags_1.CATALOG_SOURCE_NAME_FLAG]: flags_1.CATALOG_SOURCE_NAME,
    [flags_1.CATALOG_SOURCE_YAML_FLAG]: flags_1.CATALOG_SOURCE_YAML,
    [flags_1.CATALOG_SOURCE_IMAGE_FLAG]: flags_1.CATALOG_SOURCE_IMAGE,
    [flags_1.AUTO_UPDATE_FLAG]: flags_1.AUTO_UPDATE,
    [flags_1.STARTING_CSV_FLAG]: flags_1.STARTING_CSV,
};
exports.default = Update;
//# sourceMappingURL=update.js.map