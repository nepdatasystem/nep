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
import com.twopaths.dhis2.services.SystemIdService
import grails.transaction.Transactional
import com.twopaths.dhis2.api.Result
import com.twopaths.dhis2.services.OptionSetService
import com.twopaths.dhis2.services.TrackedEntityAttributeService
import nep.util.Utils
import org.springframework.context.MessageSource

/**
 * Service to manage Survey Program Metadata
 */
@Transactional
class SurveyProgramMetadataService {

    TrackedEntityAttributeService trackedEntityAttributeService
    SystemIdService systemIdService
    MetadataService metadataService
    SurveyOptionSetService surveyOptionSetService

    MessageSource messageSource

    private static String SUM = "SUM"

    /**
     * Create metadata from the supplied codebook
     *
     * @param auth DHIS 2 Credentials
     * @param program The program to create metadata for
     * @param codebookRow One row of codebook data to create metadata for
     * @return errors if any
     */
    def createProgramMetadata(def auth, def program, def codebookRow) {
        log.debug "program.name: " + program.name
        def programTrackedEntityAttributeIds = []

        def errors = []

        def variableName = codebookRow[0]
        def variableData = codebookRow[1]
        log.debug "variableName: ${variableName}, variableData: ${variableData}"

        def result = createProgramTrackedEntityAttribute(auth, program.name, variableData, programTrackedEntityAttributeIds, program.trackedEntity)
        log.debug "result: ${result}"
        if (!result?.success) {
            errors.addAll(result.errors)
        }

        log.debug "programTrackedEntityAttributeIds: " + programTrackedEntityAttributeIds

        def addTrackedEntityAttributesToProgramResult = addTrackedEntityAttributesToProgram(auth, program, programTrackedEntityAttributeIds)
        if (!addTrackedEntityAttributesToProgramResult?.success) {
            if (addTrackedEntityAttributesToProgramResult.errors) {
                errors.addAll(addTrackedEntityAttributesToProgramResult.errors)
            } else {
                // if no errors but not success, adding an unknown error
                // this will be obscure to a user, but this is a strange edge case that we can't really recover from anyway.
                // better than showing it was successful.
                errors << [code: "dhis2.error", args: ["programTrackedEntityAttributes", messageSource.getMessage(
                        "error.title", null, Locale.default)]]
            }
        }

        return errors
    }

    /**
     * Adds all the tracked entity attributes to a program via the metadata service
     *
     * @param auth DHIS 2 Credentials
     * @param program The program to add tracked entity attributes to
     * @param programTrackedEntityAttributeIds The ids of the existing program tracked entity attributes to add
     * @return the DHIS 2 Connector API Result object
     */
    private def addTrackedEntityAttributesToProgram(def auth, def program, def programTrackedEntityAttributeIds) {

        // metadata map to post to API
        def metadata = [:]
        // list of program tracked entity attributes to create
        def newProgramTrackedEntityAttributes = []
        // make a map that has the existing inner trackedEntityAttributeID as the key to match on to see if it exists,
        // to be able to look up the existing programTrackedEntityAttributeID.
        // eg: the programTrackedEntityAttributes look like this:
        // [{"id":"u8EOgwUYb0a","trackedEntityAttribute":{"id":"jJuJ2pv8EXc"}},... ]
        def existingProgramTrackedEntityAttributesMap = program.programTrackedEntityAttributes.collectEntries {[(it.trackedEntityAttribute?.id) : (it.id)]}

        def trackedEntityInstanceIdsToAdd = []
        programTrackedEntityAttributeIds.each { id ->
            // if this tracked entity attribute doesn't already exist for the program, we need to add it,
            if (!existingProgramTrackedEntityAttributesMap.get(id)) {
                trackedEntityInstanceIdsToAdd.add(id)
            }
        }

        // we only add any that are new, we do not delete any that may have been removed
        // as that could cause corrupted data, and may not even be possible if there are associated data points

        // auto-generate UIDs for the programTrackedEntityAttributes in order to assign them to the program in one Metadata API call
        def generatedIds = trackedEntityInstanceIdsToAdd.size() > 0 ? systemIdService.getIds(auth, trackedEntityInstanceIdsToAdd.size()) : []

        trackedEntityInstanceIdsToAdd.eachWithIndex { programTrackedEntityAttributeId, idx ->
            // create a programTrackedEntityAttribute with an inner trackedEntityAttribute
            newProgramTrackedEntityAttributes << [
                    id: generatedIds[idx],
                    displayInList: true,
                    allowFutureDate: false,
                    mandatory: false,
                    trackedEntityAttribute: [id: programTrackedEntityAttributeId]
            ]
        }

        // create all the new programTrackedEntityAttributes. No need to update existing ones
        if (newProgramTrackedEntityAttributes.size() > 0) {
            metadata.put("programTrackedEntityAttributes", newProgramTrackedEntityAttributes)
        }

        def programTrackedEntityAttributeIdList = program.programTrackedEntityAttributes ?: []
        generatedIds.each { id ->
            programTrackedEntityAttributeIdList << [id : id]
        }

        program << [programTrackedEntityAttributes: programTrackedEntityAttributeIdList]

        metadata.put ("programs", [program])

        def result = metadataService.createOrUpdate(auth, metadata, [mergeMode: ApiMergeMode.MERGE.value()])

        return result
    }

    /**
     * Creates a program tracked entity attribute
     *
     * @param auth DHIS 2 Credentials
     * @param programName The program to create a tracked entity attribute to
     * @param variableData The data to use to create the Program Tracked Entity Attribute
     * @param programTrackedEntityAttributeIds List of program tracked Entity attribute ids. This will be mutated
     * @return the DHIS 2 Connector API Result object from creating the tracked entity attribute result
     */
    private def createProgramTrackedEntityAttribute(def auth, def programName, def variableData, def programTrackedEntityAttributeIds, def trackedEntity) {

        def attributeCode = variableData.code
        def attributeName = variableData.name

        log.debug "attributeCode: " + attributeCode
        log.debug "attributeName: " + attributeName

        // Get the attribute if it exists
        def trackedEntityAttribute = getTrackedEntityAttribute(auth, programName, attributeCode)
        log.debug "trackedEntityAttribute: " + trackedEntityAttribute

        def trackedEntityAttributeResult

        // Ensure we have an attribute name in the metadata
        if (attributeName) {
            if (!trackedEntityAttribute) {
                // Create new TrackedEntityAttribute (and option set / options if required)
                trackedEntityAttributeResult = createNewTrackedEntityAttribute(auth, programName, variableData, programTrackedEntityAttributeIds, trackedEntity)
            } else {
                // Update existing TrackedEntityAttribute
                trackedEntityAttributeResult = updateExistingTrackedEntityAttribute(auth, trackedEntityAttribute, programName, variableData, programTrackedEntityAttributeIds, trackedEntity)
            }
        } else {
            return new Result().setSuccess(false).setErrors(
                    [code: "survey.metadata.respondent.attribute.name.missing",
                     args: [attributeCode, trackedEntity?.name ?: messageSource.getMessage( "survey.respondent", null, Locale.default)]])
        }

        return trackedEntityAttributeResult
    }

    /**
     * Create a new TrackedEntityAttribute and option set / options if required
     *
     * @param programName The program to create a new tracked entity attribute for
     * @param variableData The data to use for creating the tracked entity attribute
     * @param programTrackedEntityAttibutes List of program tracked entity attributes. This will be mutated
     * @return the DHIS 2 Connector API Result object for the created tracked entity attribute
     */
    private def createNewTrackedEntityAttribute(def auth, def programName, def variableData, def programTrackedEntityAttributeIds, def trackedEntity) {

        log.debug "createNewTrackedEntityAttribute, variableData: ${variableData}"
        def trackedEntityAttributeResult
        def attributeId

        // Get the required DHIS2 type
        def valueType = Utils.getValueType(variableData.type)

        // If we have options
        if (variableData.options) {

            // Check if we already have an optionSet of the same name
            def createOptionSetResult = surveyOptionSetService.createOrUpdateOptionValuesAndOptionSet(
                    auth, programName, variableData, ProgramCategory.Program)

            def optionSetId
            if (createOptionSetResult.success) {
                optionSetId = createOptionSetResult?.lastImported


                // Option Set with uid
                trackedEntityAttributeResult = createTrackedEntityAttribute(auth, programName, variableData, valueType, optionSetId)

                // If success...
                if (trackedEntityAttributeResult.success) {
                    // Get the id so we can use this
                    attributeId = trackedEntityAttributeResult?.lastImported
                } else {
                    return trackedEntityAttributeResult
                }
            } else {
                return createOptionSetResult
            }
        } else {

            if (valueType) {
                // Numeric
                trackedEntityAttributeResult = createTrackedEntityAttribute(auth, programName, variableData, valueType)

                // If success...
                if (trackedEntityAttributeResult.success) {
                    // Get the id so we can use this
                    attributeId = trackedEntityAttributeResult?.lastImported
                } else {
                    return trackedEntityAttributeResult
                }
            } else {
                return new Result().setSuccess(false).setErrors(
                        [code: "survey.metadata.respondent.type.notFound",
                         args: [variableData.type, trackedEntity?.name ?: messageSource.getMessage( "survey.respondent", null, Locale.default)]])
            }
        }

        // Add the attribute id to the list of ids to be assigned to the program
        if (attributeId) {
            programTrackedEntityAttributeIds << attributeId
        }

        return trackedEntityAttributeResult
    }

    /**
     * Update existing TrackedEntityAttribute with option set / options if required
     *
     * @param trackedEntityAttribute The tracked entity to update
     * @param programName The associated program
     * @param variableData The data to update the tracked entity attribute with
     * @param programTrackedEntityAttributeIds List of program tracked entity attribute ids (will be mutated)
     * @param trackedEntity Associated tracked entity
     * @return the DHIS 2 Connector API Result object for the updated tracked entity attribute
     */
    private def updateExistingTrackedEntityAttribute (def auth, def trackedEntityAttribute, def programName,
                                                     def variableData, def programTrackedEntityAttributeIds,
                                                      def trackedEntity) {

        log.debug "updateExistingTrackedEntityAttribute, trackedEntityAttribute: ${trackedEntityAttribute}"
        def trackedEntityAttributeId
        def optionSetValue = trackedEntityAttribute.get('optionSetValue')
        log.debug "trackedEntityAttribute.get('optionSetValue'): " + optionSetValue

        def trackedEntityAttributeResult

        // Currently an optionSet
        if (optionSetValue && trackedEntityAttribute.optionSet?.id) {
            // If we have options in the metadata
            if (variableData.options) {

                // Update the TrackedEntityAttribute
                trackedEntityAttributeResult = updateTrackedEntityAttribute(auth, programName, variableData,
                        trackedEntityAttribute, trackedEntityAttribute.valueType, trackedEntityAttribute.optionSet.id)

                if (trackedEntityAttributeResult.success) {
                    // Get the id so we can use this
                    trackedEntityAttributeId = trackedEntityAttributeResult?.lastImported
                } else {
                    return trackedEntityAttributeResult
                }

                // Add any missing options
                def optionSetResult = surveyOptionSetService.updateOptionValuesAndOptionSet(
                        auth, programName, variableData, trackedEntityAttribute, ProgramCategory.Program)

                if (optionSetResult.success) {
                    trackedEntityAttributeId = trackedEntityAttribute?.id
                    trackedEntityAttributeResult = new Result().setSuccess(true)
                } else {
                    return optionSetResult
                }
            } else {  // Might be that we need to change the types

                // Change the valueType
                def valueType = Utils.getValueType(variableData.type)

                if (valueType) {
                    // Update the TrackedEntityAttribute
                    trackedEntityAttributeResult = updateTrackedEntityAttribute(auth, programName, variableData, trackedEntityAttribute, valueType)

                    if (trackedEntityAttributeResult.success) {
                        // Get the id so we can use this
                        trackedEntityAttributeId = trackedEntityAttributeResult?.lastImported
                    } else {
                        return trackedEntityAttributeResult
                    }
                } else {
                    return new Result().setSuccess(false).setErrors([
                            code: "survey.metadata.respondent.type.notFound",
                            args: [variableData.type, trackedEntity?.name ?: messageSource.getMessage( "survey.respondent", null, Locale.default)]])
                }
            }
        } else { // Isn't currently an optionSet

            // If we have options in the metadata
            if (variableData.options) {

                def optionSetResult = surveyOptionSetService.createOrUpdateOptionValuesAndOptionSet(
                        auth, programName, variableData, ProgramCategory.Program)

                def optionSetId
                if (optionSetResult.success) {
                    optionSetId = optionSetResult.lastImported

                    // Option Set type
                    def valueType =  Utils.getValueType(variableData.type)

                    // Option Set with uid
                    trackedEntityAttributeResult = updateTrackedEntityAttribute(auth, programName, variableData, trackedEntityAttribute, valueType, optionSetId)

                    if (trackedEntityAttributeResult.success) {
                        //  Get the id so we can use this
                        trackedEntityAttributeId = trackedEntityAttributeResult?.lastImported
                    } else {
                        return trackedEntityAttributeResult
                    }
                } else {
                    return optionSetResult
                }
            } else {  // Might be that we need to change the types

                // Change the valueType
                def valueType = Utils.getValueType(variableData.type)

                if (valueType) {
                    // Update the DataElement
                    trackedEntityAttributeResult = updateTrackedEntityAttribute(auth, programName, variableData, trackedEntityAttribute, valueType)

                    if (trackedEntityAttributeResult.success) {
                        //  Get the id so we can use this
                        trackedEntityAttributeId = trackedEntityAttributeResult?.lastImported
                    } else {
                        return trackedEntityAttributeResult
                    }
                } else {
                    return new Result().setSuccess(false).setErrors(
                            [code: "survey.metadata.respondent.type.notFound",
                             args: [variableData.type, trackedEntity?.name ?: messageSource.getMessage( "survey.respondent", null, Locale.default)]])
                }
            }
        }

        if (trackedEntityAttributeId) {
            programTrackedEntityAttributeIds << trackedEntityAttributeId
        }

        log.debug "programTrackedEntityAttributeIds: ${programTrackedEntityAttributeIds}"
        return trackedEntityAttributeResult
    }

    /**
     * Get the TrackedEntityAttribute using the programName & attributeCode
     *
     * @param auth DHIS 2 Credentials
     * @param programName The name of the program to look up
     * @param attributeCode The code of the attribute to look up
     * @return the Tracked Entity
     */
    private def getTrackedEntityAttribute(def auth, def programName, def attributeCode) {

        def code = Utils.addPrefix(programName,  attributeCode)

        def te = trackedEntityAttributeService.findByCode(auth, code)

        log.debug "getTrackedEntityAttribute, code: " + code + ", trackedEntityAttribute: " + te

        return te
    }

    /**
     * Create the trackedEntityAttribute
     *
     * @param auth DHIS 2 Credentials
     * @param programName The name of the program to create the tracked entity attribute for
     * @param variableData The associated data for the tracked entity attribute
     * @param valueType The value type of the tracked entity attribute
     * @param optionSetUid The UID of the associated option set if any
     * @return the DHIS 2 Connector API Result object for the created Tracked Entity Attribute
     */
    private def createTrackedEntityAttribute(def auth, def programName, def variableData, def valueType, def optionSetUid=null) {
        // Create JSON for attribute
        def trackedEntityAttribute = [
            code: Utils.addPrefix(programName, variableData.code),
            name: Utils.addPrefix(programName, variableData.name),
            shortName: Utils.addPrefix(programName, Utils.addPrefix(variableData.code, variableData.name)).take(50),
            description: variableData.name,
            valueType: valueType,
            aggregationType: SUM
        ]

        // Assign the option set to the tracked entity attribute if it exists
        if (optionSetUid) {
            trackedEntityAttribute << [optionSet: [id: optionSetUid]]
        }

        // Create the attribute and assign the uid from the option set
        def trackedEntityAttributeResult = trackedEntityAttributeService.create(auth, trackedEntityAttribute)

        log.debug "trackedEntityAttributeResult: " + trackedEntityAttributeResult
        return trackedEntityAttributeResult
    }

    /**
     * Updates the Tracked Entity Attribute
     *
     * @param auth DHIS 2 Credentials
     * @param programName The name of the program to update the tracked entity attribute for
     * @param variableData The associated data for the tracked entity attribute
     * @param trackedEntityAttribute The tracked entity attribute to update
     * @param valueType The value type of the tracked entity attribute
     * @param optionSetUid The UID of the associated option set if any
     * @return the DHIS 2 Connector API Result object for the updated tracked entity attribute
     */
    private def updateTrackedEntityAttribute(def auth, def programName, def variableData, def trackedEntityAttribute,
                                             def valueType, def optionSetUid=null) {

        // Update the name, shortName and description
        trackedEntityAttribute << [name: Utils.addPrefix(programName, variableData.name)]
        trackedEntityAttribute << [shortName: Utils.addPrefix(programName, Utils.addPrefix(variableData.code, variableData.name)).take(50)]
        trackedEntityAttribute << [description: Utils.addPrefix(programName, variableData.name)]
        trackedEntityAttribute << [aggregationType: SUM]
        // Set the valueType
        trackedEntityAttribute << [valueType: valueType]

        if (!optionSetUid) {
            //  Remove the optionSet assignment
            trackedEntityAttribute << [optionSetValue: false]
            trackedEntityAttribute << [optionSet: null]
        } else {
            // Set the optionSet
            trackedEntityAttribute << [optionSetValue: true]
            trackedEntityAttribute << [optionSet: [id: optionSetUid]]
        }
        // Update
        def result = trackedEntityAttributeService.update(auth, trackedEntityAttribute)

        log.debug "updateTrackedEntityAttribute, result: " + result
        return result
    }

}
