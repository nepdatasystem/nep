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

import grails.transaction.Transactional
import com.twopaths.dhis2.api.Result
import com.twopaths.dhis2.services.DataElementService
import com.twopaths.dhis2.services.ProgramStageDataElementService
import com.twopaths.dhis2.services.ProgramStageService
import nep.util.Utils

/**
 * Service to manage program stage metadata
 */
@Transactional
class SurveyProgramStageMetadataService {

    DataElementService dataElementService
    ProgramStageService programStageService
    ProgramStageDataElementService programStageDataElementService
    SurveyOptionSetService surveyOptionSetService

    private static String TRACKER = "TRACKER"
    private static String SUM = "SUM"

    /**
     * Creates all the program stage metadata
     *
     * @param auth DHIS 2 Credentials
     * @param programStage program stage to create metadata for
     * @param codebookRow One row of codebook metadata to create program stage metadata for
     * @return errors if any
     */
    def createProgramStageMetadata(def auth, def programStage, def codebookRow) {

        log.debug "programStage.name: " + programStage.name
        log.debug "codebookRow: " + codebookRow

        def dataElementIds = []

        def errors = []

        def variableName = codebookRow[0]
        def variableData = codebookRow[1]

        // create optionSet
        def createProgramStageEntryResult = createProgramStageEntry(auth, programStage.name, variableData, dataElementIds)
        if (!createProgramStageEntryResult?.success) {
            errors.addAll(createProgramStageEntryResult.errors)
        }

        log.debug "dataElementIds: " + dataElementIds
        def addDataElementsToProgramStageResult = addDataElementsToProgramStage(auth, programStage, dataElementIds)
        if (!addDataElementsToProgramStageResult?.success) {
            errors.addAll(addDataElementsToProgramStageResult.errors)
        }

        return errors
    }

    /**
     * Adds the data elements to the program stage
     *
     * @param auth DHIS 2 Credentials
     * @param programStage The program stage to add data elements to
     * @param dataElementIds List of data element ids to add to the program stage
     * @return the DHIS 2 Connector API Result object from the adding of the data elements to the program stage
     */
    def addDataElementsToProgramStage(def auth, def programStage, def dataElementIds) {
        def programStageData = [:]
        programStageData << [id: programStage.id, name: programStage.name]

        // Get the current dataElements assigned to the program stage
        def currentDataElementIds = programStageService.findAllProgramStageDataElements(auth, programStage.id)?.programStageDataElements?.collect { programStageDataElement -> programStageDataElement?.dataElement?.id}

        // Iterate through the created/updated DataElements
        dataElementIds.each { dataElementId ->

            // If this DataElement is not already assigned to ProgramStage, assign
            if (!currentDataElementIds.contains(dataElementId)) {

                // Create a ProgramStageDataElement
                def programStageDataElement = [
                    dataElement: [
                        id: dataElementId
                    ]
                ]

                // Get the id of the created ProgramStageDataElement
                def programStageDataElementId = programStageDataElementService.create(auth, programStageDataElement)?.lastImported

                // If programStageDataElement created...
                if (programStageDataElementId) {
                    // Assign to programStage
                    programStageService.assignProgramStageDataElement(auth, programStage.id, programStageDataElementId)
                }
            }
        }

        // Return Result with success
        return new Result().setSuccess(true)
    }

    /**
     * Creates the Program Stage entry (data element)
     *
     * @param auth DHIS 2 Credentials
     * @param programStageName name of the program stage to create the data element for
     * @param variableData The data to use to create the data element
     * @param dataElementIds List of data element ids to create (will be mutated)
     * @return the DHIS 2 Connector API Result object from the creation or update of the data element
     */
    private def createProgramStageEntry(def auth, def programStageName, def variableData, def dataElementIds) {

        def dataElementCode = variableData.code
        def dataElementName = variableData.name

        log.debug "dataElementCode: " + dataElementCode
        log.debug "dataElementName: " + dataElementName

        // Get the data element if it exists
        def dataElement = getDataElement(auth, programStageName, dataElementCode)

        def dataElementResult

        // Ensure we have a dataElement name in the metadata
        if (dataElementName) {
            if (!dataElement) {
                dataElementResult = createNewDataElement(auth, programStageName, variableData, dataElementIds)
            } else {
                dataElementResult = updateExistingDataElement(auth, dataElement, programStageName, variableData, dataElementIds)
            }
        } else {
            return new Result().setSuccess(false).setErrors([code: "survey.metadata.database.dataElement.name.missing", args: [dataElementCode]])
        }

        return dataElementResult
    }

    /**
     * Creates a new data element
     *
     * @param auth DHIS 2 Credentials
     * @param programStageName Name of the program stage to associate the data element with
     * @param variableData The data to use for the data element creation
     * @param dataElementIds List of data elements to create (will be mutated)
     * @return the DHIS 2 Connector API Result object from the creation of the data element
     */
    private def createNewDataElement(def auth, def programStageName, def variableData, def dataElementIds) {

        def dataElementResult
        def dataElementId

        // Set the valueType
        def valueType = Utils.getValueType(variableData.type)

        // If we have options
        if (variableData.options) {

            def createOptionSetResult = surveyOptionSetService.createOrUpdateOptionValuesAndOptionSet(
                    auth, programStageName, variableData, ProgramCategory.ProgramStage)

            def optionSetId
            if (createOptionSetResult.success) {
                optionSetId = createOptionSetResult?.lastImported

                // Data Element with uid
                dataElementResult = createDataElement(auth, programStageName, variableData, valueType, optionSetId)

                if (dataElementResult.success) {
                    dataElementId = dataElementResult?.lastImported
                } else {
                    return dataElementResult
                }
            } else {
                return createOptionSetResult
            }
        } else {

            if (valueType) {
                // Numeric
                dataElementResult = createDataElement(auth, programStageName, variableData, valueType)

                if (dataElementResult.success) {
                    dataElementId  = dataElementResult.lastImported
                } else {
                    return dataElementResult
                }
            } else {
                return new Result().setSuccess(false).setErrors([code: "survey.metadata.database.type.notFound", args: [variableData.type, variableData.name]])
            }
        }

        if (dataElementId) {
            dataElementIds << dataElementId
        }

        return dataElementResult
    }

    /**
     * Update existing DataElement with option set / options if required

     * @param auth DHIS 2 Credentials
     * @param dataElement The data element to update
     * @param programStageName The name of the program stage to update the data element for
     * @param variableData The data to use for the data element updating
     * @param dataElementIds List of data element ids to update (will be mutated)
     * @return the DHIS 2 Connector API Result object from the updating of the data element
     */
    private def updateExistingDataElement(def auth, def dataElement, def programStageName, def variableData, def dataElementIds) {

        def dataElementId
        def optionSetValue = dataElement.get('optionSetValue')
        log.debug "optionSetValue: " + optionSetValue

        def dataElementResult

        // Currently an optionSet
        if (optionSetValue && dataElement.optionSet?.id) {
            // If we have options in the metadata
            if (variableData.options) {

                // Update the DataElement
                dataElementResult = updateDataElement(auth, programStageName, variableData, dataElement, dataElement.valueType, dataElement.optionSet.id)

                if (dataElementResult?.success) {
                    // Get the id so we can use this
                    dataElementId = dataElementResult?.lastImported
                } else {
                    return dataElementResult
                }

                // Update the option set and option values with any missing options
                def optionSetResult = surveyOptionSetService.updateOptionValuesAndOptionSet(
                        auth, programStageName, variableData, dataElement, ProgramCategory.ProgramStage)

                if (optionSetResult.success) {
                    dataElementId = dataElement?.id
                    dataElementResult = new Result().setSuccess(true)
                } else {
                    return optionSetResult
                }
            } else {  // Might be that we need to change the types
                // Change the valueType
                def valueType = Utils.getValueType(variableData.type)

                if (valueType) {
                    // Update the DataElement
                    dataElementResult = updateDataElement(auth, programStageName, variableData, dataElement, valueType)

                    if (dataElementResult?.success) {
                        // Get the id so we can use this
                        dataElementId = dataElementResult?.lastImported
                    } else {
                        return dataElementResult
                    }
                } else {
                    return new Result().setSuccess(false).setErrors([code: "survey.metadata.database.type.notFound", args: [variableData.type, variableData.name]])
                }
            }
        } else { // Isn't currently an optionSet
            // If we have options in the metadata
            if (variableData.options) {

                def optionSetResult = surveyOptionSetService.createOrUpdateOptionValuesAndOptionSet(
                        auth, programStageName, variableData, ProgramCategory.ProgramStage)

                def optionSetId
                if (optionSetResult.success) {
                    optionSetId = optionSetResult.lastImported

                    // Option Set type
                    def valueType = Utils.getValueType(variableData.type)

                    // Option Set with uid
                    dataElementResult = updateDataElement(auth, programStageName, variableData, dataElement, valueType, optionSetId)
                    if (dataElementResult?.success) {
                        dataElementId = dataElement?.lastImported
                    } else {
                        return dataElementResult
                    }
                } else {
                    return optionSetResult
                }
            } else {  // Might be that we need to change the types

                // Change the valueType
                def valueType = Utils.getValueType(variableData.type)

                if (valueType) {
                    // Update the DataElement
                    dataElementResult = updateDataElement(auth, programStageName, variableData, dataElement, valueType)
                    if (dataElementResult?.success) {
                        dataElementId = dataElementResult?.lastImported
                    } else {
                        return dataElementResult
                    }
                } else {
                    return new Result().setSuccess(false).setErrors([code: "survey.metadata.database.type.notFound", args: [variableData.type, variableData.name]])
                }
            }
        }

        if (dataElementId) {
            dataElementIds << dataElementId
        }

        return dataElementResult
    }

    /**
     * Get the DataElement using the programStageName & dataElementCode
     *
     * @param auth DHIS 2 Credentials
     * @param programStageName The name of the associated program stage
     * @param dataElementCode The code of the data element to get
     * @return the data element
     */
    private def getDataElement(def auth, def programStageName, def dataElementCode) {

        def code = Utils.addPrefix(programStageName,  dataElementCode)

        def de = dataElementService.findByCode(auth, code)

        log.debug "getDataElement, code: " + code + ", dataElement: " + de

        return de
    }

    /**
     * Create the dataElement
     *
     * @param auth DHIS 2 Credentials
     * @param programStageName The name of the associated program stage
     * @param variableData The data to use for the data element creation
     * @param valueType The value type of the data element
     * @param optionSetUid The UID of the associated option set
     * @return the DHIS 2 Connector API Result object from the creation of the data element
     */
    private def createDataElement(def auth, def programStageName, def variableData, def valueType,def optionSetUid=null) {
        // Create JSON for attribute
        def dataElement = [
            code: Utils.addPrefix(programStageName, variableData.code),
            name: Utils.addPrefix(programStageName, variableData.name),
            shortName: Utils.addPrefix(programStageName, Utils.addPrefix(variableData.code, variableData.name)).take(50),
            description: variableData.name,
            domainType: TRACKER,
            valueType: valueType,
            aggregationType: SUM,
            categoryCombo: [name: 'default'],
            zeroIsSignificant: true
        ]

        // Assign the option set to the data element if it exists
        if (optionSetUid) {
            dataElement << [optionSet: [id: optionSetUid]]
        }

        // Create the attribute and assign the uid from the option set
        def created = dataElementService.create(auth, dataElement)

        return created
    }

    /**
     * Updates a data element
     *
     * @param auth DHIS 2 Credentials
     * @param programStageName The name of the associated program stage
     * @param variableData The data to use for the data element updating
     * @param dataElement The data element to update
     * @param valueType The value type of the data element
     * @param optionSetUid The UID of the associated option set
     * @return the DHIS 2 Connector API Result object from the updating of the data element
     */
    private def updateDataElement(def auth, def programStageName, def variableData, def dataElement, def valueType, def optionSetUid=null) {

        // Set the name, short name and description
        dataElement << [name: Utils.addPrefix(programStageName, variableData.name)]
        dataElement << [shortName: Utils.addPrefix(programStageName, Utils.addPrefix(variableData.code, variableData.name)).take(50)]
        dataElement << [description: variableData.name]
        dataElement << [aggregationType: SUM]

        // Set the type
        dataElement << [valueType: valueType]

        if (!optionSetUid) {
            //  Remove the optionSet assignment
            dataElement << [optionSetValue: false]
            dataElement << [optionSet: null]
        } else {
            dataElement << [optionSetValue: true]
            dataElement << [optionSet: [id: optionSetUid]]
        }
        // Update
        def result = dataElementService.update(auth, dataElement)

        log.debug "updateDataElement, result: " + result
        return result
    }
}
