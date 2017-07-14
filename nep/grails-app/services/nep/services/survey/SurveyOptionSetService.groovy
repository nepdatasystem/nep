/*
 * Copyright (c) 2014-2017. Institute for International Programs at Johns Hopkins University.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the NEP project, Institute for International Programs,
 * Johns Hopkins University nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package nep.services.survey

import com.twopaths.dhis2.api.ApiMergeMode
import com.twopaths.dhis2.services.MetadataService
import com.twopaths.dhis2.services.OptionSetService
import com.twopaths.dhis2.services.SystemIdService
import grails.transaction.Transactional
import nep.util.Utils
import org.springframework.context.MessageSource

/**
 * Service to manage Survey Option Sets
 */
@Transactional
class SurveyOptionSetService {

    OptionSetService optionSetService
    MetadataService metadataService
    MessageSource messageSource
    SystemIdService systemIdService

    private final def List OPTION_SET_FIELDS = [":all", "options[id,code,name]" ]

    /**
     * Creates or updates an option set with associated options
     *
     * @param auth DHIS 2 Credentials
     * @param programCategoryName The name of the program or program stage to use as a prefix
     * @param variableData The data to use for the create or update
     * @param programCategory The program category using this method (either program or program stage)
     * @return the DHIS 2 Connector API Result object from the metadata creation and/or update of the option set & options
     */
    public createOrUpdateOptionValuesAndOptionSet(auth, programCategoryName, variableData, ProgramCategory programCategory) {

        def allOptions = []
        boolean isUpdate = false

        // Get the optionSet by name if it exists
        def optionSet = getOptionSet(auth, programCategoryName, variableData)

        // If we have an existing optionSet
        if (optionSet) {

            isUpdate = true

            def existingOptions = optionSet?.options
            def existingOptionCodes = existingOptions?.collect { option -> option.code}

            log.debug "existingOptions: " + existingOptions
            def missingOptions = []
            def updateOptions = []

            // clone the options so that we're not mutating the variableData
            variableData.options.clone().each { option ->
                log.debug "option: " + option
                if (existingOptionCodes?.contains(option.code)) {
                    def existingOption = existingOptions?.find { it.code == option.code }
                    if (existingOption) {
                        // use all properties of the existing option and update the option name
                        def optionName = option.name ?: messageSource.getMessage(
                                "survey.metadata.optionSet.option.unlabeled", null, Locale.default)
                        option << existingOption
                        option.name = optionName
                        updateOptions << option
                    }
                } else {
                    missingOptions << option
                }
            }

            // If missing options, create
            if (missingOptions) {
                // Create the new option values
                allOptions.addAll(prepareOptionValuesCreate(auth, missingOptions))
            }

            // If existing options, update
            if (updateOptions) {
                log.debug "existingOptions: " + existingOptions + ", updateOptions: " + updateOptions

                allOptions.addAll(updateOptions)
            }

            // Update the optionSet
            optionSet = prepareOptionSetUpdate(optionSet, programCategoryName, variableData.name, variableData.type, allOptions)

        } else {

            isUpdate = false

            // Create the option values, with cloned variableData so it doesn't get mutated
            allOptions.addAll(prepareOptionValuesCreate(auth, variableData?.options?.clone()))

            // Create the option set with option values, with cloned variableData so it doesn't get mutated
            optionSet = prepareOptionSetCreate(auth, programCategoryName, variableData.clone(), allOptions)

        }

        Map metadata = [:]

        metadata << [options: allOptions]
        metadata << [optionSets : [optionSet]]

        def metadataResult = createOrUpdateMetadata(auth, metadata)

        if (!metadataResult.success) {
            log.error "Error ${isUpdate ? "updating" : "creating"} option set and/or options"
            metadataResult.errors << [
                    code: "survey.${programCategory.camelCaseName}.optionSet.${isUpdate ? "update" : "create"}.error",
                    args: [variableData.name]]
        } else {
            // need to set the optionSet id to the lastImported in order to send it back to the calling method
            metadataResult.lastImported = optionSet.id
        }

        return metadataResult

    }

    /**
     * Updates existing option set with updated option and option set data
     *
     * @param auth DHIS 2 Credentials
     * @param programCategoryName The name of the program or program stage to use as a prefix
     * @param variableData The data to use for the update
     * @param trackedEntityAttribute The associated tracked entity attribute
     * @param programCategory The program category using this method (either program or program stage)
     * @return the DHIS 2 Connector API Result object from the metadata update of the option set and options
     */
    public updateOptionValuesAndOptionSet(
            auth, programCategoryName, variableData, trackedEntityAttribute, ProgramCategory programCategory) {

        def optionSet = optionSetService.getById(auth, trackedEntityAttribute.optionSet?.id, OPTION_SET_FIELDS)
        def existingOptions = optionSet?.options
        def existingOptionCodes = existingOptions?.collect { option -> option.code}

        log.debug "existingOptionCodes: " + existingOptionCodes
        def missingOptions = []
        def updateOptions = []

        // clone the options so that we're not mutating the variableData
        variableData.options.clone().each { option ->
            log.debug "option: " + option
            if (existingOptionCodes?.contains(option.code)) {
                def existingOption = existingOptions?.find { it.code == option.code }
                if (existingOption) {
                    // use all properties of the existing option and update the option name
                    def optionName = option.name ?: messageSource.getMessage(
                            "survey.metadata.optionSet.option.unlabeled", null, Locale.default)
                    option << existingOption
                    option.name = optionName
                    updateOptions << option
                }
            } else {
                missingOptions << option
            }
        }

        log.debug "updateOptions: ${updateOptions}, missingOptions: ${missingOptions}"

        def allOptions = []

        // If missing options...
        if (missingOptions) {
            // Create the new option values
            allOptions.addAll(prepareOptionValuesCreate(auth, missingOptions))
        }

        // If update options...
        if (updateOptions) {
            log.debug "existingOptions: " + existingOptions + ", updateOptions: " + updateOptions

            allOptions.addAll(updateOptions)
        }

        // Prepare the optionSet for updating
        optionSet = prepareOptionSetUpdate(optionSet, programCategoryName, variableData.name, variableData.type, allOptions)

        // send all new and updated options, as well as the optionSet all in one metadata API call to help with performance
        Map metadata = [:]

        metadata << [options: allOptions]
        metadata << [optionSets : [optionSet]]

        def metadataResult = createOrUpdateMetadata(auth, metadata)

        if (!metadataResult.success) {
            log.error "Error updating options and/or option set"
            metadataResult.errors << [code: "survey.${programCategory.camelCaseName}.optionSet.update.error", args: [variableData.name]]
        } else {
            // need to set the optionSet id to the lastImported in order to send it back to the calling method
            metadataResult.lastImported = optionSet.id
        }

        return metadataResult
    }

    /**
     * Prepares the option set for creation by setting all relevant properties
     *
     * @param auth DHIS 2 Credentials
     * @param prefix The prefix to use for the option set
     * @param optionSetData The option set data
     * @param options The options to associate with the option set
     * @return the optionSet prepared for creation, with all relevant properties
     */
    private def prepareOptionSetCreate(def auth, def prefix, def optionSetData, def options) {
        log.debug "optionSetData: " + optionSetData


        // auto-generate UID for the option set
        def generatedId = systemIdService.getIds(auth, 1)[0]

        def optionSet = [
                id :  generatedId,
                // Need to prefix with the program or programStage name
                code : Utils.addPrefix(prefix, optionSetData.get("code")),
                name : Utils.addPrefix(prefix, optionSetData.get("name")),
                valueType : Utils.getValueType(optionSetData.get("type")),
                options : options
        ]

        return optionSet
    }

    /**
     * Get the option set by name from supplied data populated with associated options
     *
     * @param auth DHIS 2 Credentials
     * @param prefix The prefix to use
     * @param variableData The option set data
     * @return the options set found, populated with associated options
     */
    private def getOptionSet(def auth, def prefix, def variableData) {

        def optionSet = optionSetService.findByName(
                auth, Utils.addPrefix(prefix, variableData.get("name")), OPTION_SET_FIELDS)

        return optionSet
    }

    /**
     * Prepare the option set with any new option values and set relevent properties
     *
     * @param optionSet The option set to prepare
     * @param prefix The prefix to use
     * @param name The name of the option set
     * @param type The value type of the option set
     * @param options The options associated with the option set
     * @return The prepared Option Set
     */
    private def prepareOptionSetUpdate(def optionSet, def prefix, def name, def type, def options) {
        log.debug "optionSet: " + optionSet + ", options: " + options

        // Name may have changed
        optionSet << [name: Utils.addPrefix(prefix, name)]
        // Set the valueType
        optionSet << [valueType: Utils.getValueType(type)]

        if (options) {
            optionSet << [options: options]
        }

        return optionSet
    }

    /**
     * Prepare option values for creation by adding UIDs and cleansing the name
     *
     * @param auth DHIS 2 Credentials
     * @param optionValues The collection of option values
     * @return the prepared optionValues
     */
    private def prepareOptionValuesCreate(def auth, def optionValues) {
        log.debug "optionValues: " + optionValues

        // auto-generate UIDs for the options in order to bulk create them in one Metadata API call
        def generatedIds = optionValues.size() > 0 ? systemIdService.getIds(auth, optionValues.size()) : []

        optionValues.eachWithIndex { option, idx ->
            // create a programTrackedEntityAttribute with an inner trackedEntityAttribute
            option << [
                    id: generatedIds[idx],
                    name: option.name ?:
                            messageSource.getMessage("survey.metadata.optionSet.option.unlabeled", null, Locale.default)
            ]
        }

        return optionValues
    }

    /**
     * Create or update any metadata via the metadata api
     *
     * @param auth DHIS 2 Credentials
     * @param metadata The metadata to create or update
     * @return the DHIS 2 Connector API Result object from the metadata creation or update
     */
    private def createOrUpdateMetadata(def auth, Map metadata) {

        def metadataResult = metadataService.createOrUpdate(auth, metadata, [mergeMode: ApiMergeMode.MERGE.value()])

        if (!metadataResult.success) {
            log.error "Could not update metadata for ${metadata?.keySet().join(',')}"
        }

        return metadataResult
    }

}
