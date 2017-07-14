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

import groovy.json.JsonBuilder

import com.twopaths.dhis2.services.DataElementService
import com.twopaths.dhis2.services.EnrollmentService
import com.twopaths.dhis2.services.EventService
import com.twopaths.dhis2.services.OptionSetService
import com.twopaths.dhis2.services.OptionValueService
import com.twopaths.dhis2.services.ProgramStageService
import com.twopaths.dhis2.services.TrackedEntityAttributeService
import com.twopaths.dhis2.services.TrackedEntityInstanceService

import nep.util.Utils

/**
 * Service to manage Survey Program Stages
 */
@Transactional
class SurveyProgramStageService {

    DataElementService dataElementService
    OptionSetService optionSetService
    OptionValueService optionValueService
    EventService eventService
    TrackedEntityAttributeService trackedEntityAttributeService
    TrackedEntityInstanceService trackedEntityInstanceService
    ProgramStageService programStageService
    EnrollmentService enrollmentService

    /**
     * Imports a row of data into a program stage
     *
     * @param auth DHIS 2 Credentials
     * @param surveyRow The row of data to import
     * @param programId The id of the associated program
     * @param programName The name of the associated program
     * @param programStageId The id of the associated program stage
     * @param programStageName The name of the associated program stage
     * @param fields The fields of additional data to support the import of data
     * @param organisationUnitLookup The org unit lookup map
     * @param dataElementLookup The data element lookup map
     * @param optionSetLookup The option set lookup map
     * @return list of errors if any
     */
    def importDataRow(
            def auth, def surveyRow, def programId, def programName, def programStageId, def programStageName,
            def fields, def organisationUnitLookup, def dataElementLookup, def optionSetLookup) {

        // Create the program stage
        def errors = createProgramStageData(auth, dataElementLookup, organisationUnitLookup, optionSetLookup,
                surveyRow, programId, programName, programStageId, programStageName, fields)

        log.debug "importDataRow, errors: " + errors

        return errors
    }

    /**
     * Creates a program stage in DHIS 2
     *
     * @param auth DHIS 2 Credentials
     * @param params The parameters to extract the program stage details from
     * @param index The program stage index for this program stage (index in the collection of program stages)
     */
    def createProgramStage(def auth, def params, def index) {

        def programStage = [:]

        // Get the indexed parameters
        programStage << [
                name               : params["programStageName_${index}"],
                displayName        : params["programStageName_${index}"],
                description        : params["programStageDescription_${index}"],
                repeatable         : params["programStageRepeatable_${index}"],
                excecutionDateLabel: params["programStageReportDateDescription_${index}"],
                program            : [id: params.programId]
        ]

        def result = programStageService.create(auth, programStage)
    }

    /**
     * Get the ProgramStage
     *
     * @param auth DHIS 2 Credentials
     * @param programStageId The id of the program stage to get
     * @return The program stage found if any
     */
    def getProgramStage(def auth, def programStageId) {
        def programStage = programStageService.get(auth, programStageId)
        log.debug "programStage: " + programStage
        return programStage
    }

    /**
     * Creates program stage data for the row of survey data supplied
     *
     * @param auth DHIS 2 Credentials
     * @param dataElementLookup The data element lookup map
     * @param organisationUnitLookup The org unit lookup map
     * @param optionSetLookup The option set lookup map
     * @param surveyRow The row of data to import
     * @param programId The id of the associated program
     * @param programName The name of the associated program
     * @param programStageId The id of the associated program stage
     * @param programStageName The name of the associated program stage
     * @param fields The fields of additional data to support the import of data
     * @return list of errors if any
     */
    private def createProgramStageData(def auth, def dataElementLookup, def organisationUnitLookup, def optionSetLookup,
                                       def surveyRow, def programId, def programName, def programStageId,
                                       programStageName, def fields) {

        def errors = []
        def event = [:]

        // Get the orgUnit, return error if it cannot be found
        def orgUnitId = getOrganisationUnit(surveyRow, optionSetLookup, organisationUnitLookup, programStageName,
                fields.orgUnit)

        // If we have found an orgUnit
        if (orgUnitId) {
            def programAttribute = fields.program
            def programAttributeValue = surveyRow[fields.programStageProgram]
            log.debug "programAttribute: " + programAttribute + ", programAttributeValue: " + programAttributeValue
            log.debug "programStageId: " + programStageId

            // Get the TrackedEntityInstance, if it cannot be found, return an error
            def trackedEntityInstanceId = getTrackedEntityInstanceId(auth, orgUnitId, programName, programAttribute,
                    programAttributeValue)

            // If we have a trackedEntityInstance
            if (trackedEntityInstanceId) {
                // Need to check if we already have an event for this programStage & trackedEntity and unique id for
                // program stage data (e.g. women's/children's id)
                def eventId = getEventId(auth, programStageId, trackedEntityInstanceId, programStageName,
                        fields.programStage, surveyRow[fields.programStage])
                log.debug "eventId: " + eventId

                // program and program stage need to be supplied
                event << [
                        program              : programId,
                        programStage         : programStageId,
                        trackedEntityInstance: trackedEntityInstanceId,
                        orgUnit              : orgUnitId,
                        status               : "ACTIVE"
                ]

                def dataValues = []
                def missingDataElements = []

                // Set the dataValues
                setDataValues(auth, surveyRow, dataElementLookup, optionSetLookup, programStageName, dataValues,
                        missingDataElements)

                log.debug "dataValues: " + dataValues
                event << [dataValues: dataValues]

                if (missingDataElements) {
                    errors << [code: "survey.dataElements.missing", args: missingDataElements]
                }

                // Need to get the enrollment to get the date
                def enrollment = enrollmentService.findByOrgUnitAndTrackedEntityInstance(auth, orgUnitId,
                        trackedEntityInstanceId)

                def eventDate
                if (enrollment) {
                    // Get the event date using the defined date fields
                    eventDate = Utils.getDateFromDateTime(enrollment.enrollmentDate)
                } else {
                    log.error "No event date as no enrollment"
                    errors << [code: "survey.enrollment.notFound"]
                }

                // Set the event date and due date to the date of the event
                event << [eventDate: eventDate, dueDate: eventDate]

                log.debug "event: " + event
                log.debug "json: " + new JsonBuilder(event)

                if (!eventId) {
                    // Send to DHIS 2
                    def eventResult = eventService.create(auth, event)
                    if (!eventResult?.success) {
                        errors << [code: "survey.event.notCreated"]
                        errors.addAll(eventResult.errors)
                    }
                } else {
                    def eventResult = eventService.update(auth, event, eventId)
                    if (!eventResult?.success) {
                        errors << [code: "survey.event.notUpdated"]
                        errors.addAll(eventResult.errors)
                    }
                }
            } else {
                errors << [code: "survey.trackedEntity.notFound", args: [programAttribute, programAttributeValue]]
            }
        } else {
            def orgUnitCode = surveyRow[fields.orgUnit]
            errors << [code: "survey.orgUnit.notFound", args: [orgUnitCode]]
        }

        // Return errors
        return errors
    }

    /**
     * Extract fields/values from row
     *
     * @param auth DHIS 2 Credentials
     * @param surveyRow The row of data to import
     * @param dataElementLookup The data element lookup map
     * @param optionSetLookup The option set lookup map
     * @param programStageName The name of the associated program stage
     * @param dataValues The list of data values
     * @param missingDataElements list of missing data elements
     * @return
     */
    private def setDataValues(def auth, def surveyRow, def dataElementLookup, def optionSetLookup, def programStageName,
                              def dataValues, def missingDataElements) {
        surveyRow.each { key, value ->
            def prefixedKey = Utils.addPrefix(programStageName, key.trim())
            def dataElement = dataElementLookup[prefixedKey]
            log.debug "prefixedKey: " + prefixedKey + ", dataElement: " + dataElement
            if (dataElement) {
                def type = dataElement.valueType
                if (value && value != "") {
                    def dataElementValue = getDataElementValue(auth, dataElement, optionSetLookup, value.trim())
                    // Only if we have a value do we populate the data values....this may need to change
                    if (dataElementValue != "") {
                        def id = dataElement.id
                        log.debug "id: " + id
                        dataValues << [dataElement: id, value: dataElementValue]
                        //log.debug "dataValues: " + dataValues
                    } else {
                        log.debug "No value for dataElement: " + prefixedKey
                    }
                }
            } else {
                log.error("No dataElement for: " + prefixedKey)
                missingDataElements << prefixedKey
            }
        }
    }

    /**
     * Gets the event id for the supplied parameters so it can be updated or created if it doesn't exist
     *
     * @param auth DHIS 2 Credentials
     * @param programStageId The id of the programs stage
     * @param trackedEntityInstanceId The id of the tracked entity instance
     * @param programStageName The name of the program stage
     * @param attributeCode The code of the attribute
     * @param attributeValue The value of the attribute
     * @return The id of the event if found
     */
    private def getEventId(def auth, def programStageId, def trackedEntityInstanceId, def programStageName,
                           def attributeCode, def attributeValue) {

        // Get all events for this programStage and trackedEntityInstance
        def events = eventService.findByProgramStageIdAndTrackedEntityInstanceId(auth, programStageId, trackedEntityInstanceId)

        // Need to get the eventId
        def eventId

        // If we have events, iterate through and look for a match for the current id
        // This is so we can update the event if it exists, or create a new one
        if (events?.events?.size() > 0) {

            // Get the attributeId from the attribute code
            def attributeId = dataElementService.findByCode(auth, Utils.addPrefix(programStageName, attributeCode))?.id

            // For each event, check for matching dataValue
            events?.events?.each { e ->
                def matchingDataElement = e.dataValues?.find {
                    it.dataElement == attributeId && it.value == attributeValue
                }

                // If we have a match, set the eventId
                if (matchingDataElement) {
                    eventId = e.event
                }
            }
        }

        log.debug "getEventId, programStageId: " + programStageId + ", trackedEntityInstanceId: " +
                trackedEntityInstanceId + ", eventId:  " + eventId
        return eventId
    }

    /**
     * Gets the tracked entity instance id for the supplied parameters
     *
     * @param auth DHIS 2 Credentials
     * @param orgUnit The associated org unit
     * @param programName The name of the program
     * @param attributeCode The code for the attribute
     * @param attributeValue The value for the attribute
     * @return The id of the tracked entity instance if found
     */
    private def getTrackedEntityInstanceId(def auth, def orgUnit, def programName, def attributeCode, def attributeValue) {

        def attributeId = trackedEntityAttributeService.getIdFromCode(auth, Utils.addPrefix(programName, attributeCode))
        log.debug "attributeId: ${attributeId}"

        def trackedEntityInstanceId

        if (attributeId) {
            trackedEntityInstanceId = trackedEntityInstanceService.getIdByOrgUnitAttributeAndValue(auth, orgUnit,
                    attributeId, attributeValue)
        }
        log.debug "trackedEntityInstanceId: ${trackedEntityInstanceId}"

        trackedEntityInstanceId
    }

    /**
     * Gets the organisation unit for the specified parameters
     *
     * @param surveyRow The row of data to import
     * @param optionSetLookup The option set lookup map
     * @param organisationUnitLookup The organisation unit lookup map
     * @param prefix Prefix to use with the data
     * @param orgUnitField The org unit field
     * @return The org unit found
     */
    private def getOrganisationUnit(def surveyRow, def optionSetLookup, def organisationUnitLookup, def prefix,
                                    def orgUnitField) {

        def optionCode = surveyRow[orgUnitField]
        log.debug "optionCode: " + optionCode

        def name

        optionSetLookup.each { key, value ->
            if (value.code == Utils.addPrefix(prefix, orgUnitField)) {
                // got the options, find the right one
                value.options.each { option ->
                    if (option.code == optionCode) {
                        name = option.name
                    }
                }
            }
        }

        def orgUnit = organisationUnitLookup.get(name?.toLowerCase())

        log.debug "orgUnit: " + orgUnit

        return orgUnit
    }

    /**
     * Gets the data element value for the supplied data element matching the supplied option code value
     *
     * @param auth DHIS 2 Credentials
     * @param dataElement The data element to get the value for
     * @param optionSetLookup The option set lookup map
     * @param value the value of the option code to use for matching
     * @return The data element value
     */
    private def getDataElementValue(def auth, def dataElement, def optionSetLookup, def value) {
        //log.debug "dataElement: " + dataElement
        def dataElementValue = value

        // Get the optionSetId from the dataElement
        def optionSetId = dataElement.optionSet?.id

        // If we have an optionSet associated with the dataElement, get the matching option
        if (optionSetId) {
            def optionSet = optionSetLookup[optionSetId]
            //log.debug "optionSet: " + optionSet

            // Get the option
            def matchingOption
            optionSet?.options.each { option ->
                //log.debug "option: " + option
                if (option.code == value) {
                    matchingOption = option
                }
            }

            // If matching option get the code
            if (matchingOption) {
                //log.debug "dataElementValue: " + dataElementValue
                dataElementValue = matchingOption.code
            } else { // Need to create the missing option
                def optionValue = [code: value, name: ("" + value).padLeft(2, "0")]

                def optionValueResult = optionValueService.create(auth, optionValue)

                if (optionValueResult.success) {
                    def optionValueId = optionValueResult.lastImported

                    // Add the option to the optionSet
                    optionSetService.addOption(auth, optionSetId, optionValueId)

                    // Add the option to the optionSet in the optionSetLookup so it doesn't get created multiple times
                    optionValue << [id: optionValueId]
                    optionSet.options << optionValue

                    // value
                    dataElementValue = optionValue.code
                } else {
                    return null
                }
            }
        }

        // Return the value, which could be the passed in value or the code from the optionSet options
        dataElementValue
    }
}
