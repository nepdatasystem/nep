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
import com.twopaths.dhis2.services.EnrollmentService
import com.twopaths.dhis2.services.OptionSetService
import com.twopaths.dhis2.services.OptionValueService
import com.twopaths.dhis2.services.OrganisationUnitService
import com.twopaths.dhis2.services.ProgramService
import com.twopaths.dhis2.services.TrackedEntityAttributeService
import com.twopaths.dhis2.services.TrackedEntityInstanceService
import nep.util.Utils

/**
 * Service to manage Survey Tracked Entity Instances
 */
@Transactional
class SurveyTrackedEntityInstanceService {

    TrackedEntityInstanceService trackedEntityInstanceService
    EnrollmentService enrollmentService
    ProgramService programService

    TrackedEntityAttributeService trackedEntityAttributeService
    OrganisationUnitService organisationUnitService
    OptionSetService optionSetService
    OptionValueService optionValueService

    /**
     * Import a single row of data
     *
     * @param auth DHIS 2 Credentials
     * @param surveyRow The row of data to import
     * @param programId The id of the associated program
     * @param programName The name of the associated program
     * @param trackedEntityId The id of the associated tracked entity
     * @param fields The fields of additional data to support the import of data
     * @param organisationUnitLookup The org unit lookup map
     * @param trackedEntityAttributeLookup The tracked entity attribute lookup map
     * @param optionSetLookup The option set lookup map
     * @return
     */
    public def importDataRow(def auth, def surveyRow, def programId, def programName, def trackedEntityId, def fields,
                             def organisationUnitLookup, def trackedEntityAttributeLookup, def optionSetLookup) {

        def errors = []

        // Get the orgUnitId
        def orgUnit = getOrganisationUnitId(auth, surveyRow, optionSetLookup, organisationUnitLookup, programName, fields.orgUnit)
        def orgUnitId = orgUnit?.orgUnitId

        // Ensure we have an orgUnitId
        if (orgUnitId) {
            // Get the id for the trackedEnityAttribute from the lookup
            def attributeId = trackedEntityAttributeLookup[Utils.addPrefix(programName, fields.program)]?.id
            if (!attributeId) {
                errors << [code: "survey.attribute.notFound", args: [Utils.addPrefix(programName, fields.program)]]
            }

            // Get the trackedEntityInstance (using the household id) if it already exists
            def trackedEntityInstanceId = getTrackedEntityInstanceId(auth, surveyRow, orgUnitId, attributeId, fields.program)

            // Result from DHIS2 import
            def trackedEntityInstanceResult

            // If the tracked entity instance exists, update
            if (trackedEntityInstanceId) {
                trackedEntityInstanceResult = createOrUpdateTrackedEntityInstance(auth, trackedEntityAttributeLookup, optionSetLookup, surveyRow, programName, orgUnitId, trackedEntityId, trackedEntityInstanceId)

                if (trackedEntityInstanceResult?.success) {
                    // Get the enrollment date from the surveyRow using the supplied fields
                    def enrollmentDate = Utils.getDateFromYearMonthDateFields(surveyRow, fields.yearMonthDay)

                    // Get the trackedEntityInstanceId (reference from the response from DHIS2)
                    trackedEntityInstanceId = trackedEntityInstanceResult.reference

                    // Get enrollment from tracked entity and check date
                    def enrollment = enrollmentService.findByOrgUnitAndTrackedEntityInstance(auth, orgUnitId, trackedEntityInstanceId)
                    log.debug "Update trackedEntityInstance, current enrollmentDate: ${enrollment.enrollmentDate}, new enrollmentDate ${enrollmentDate}"

                    def currentEnrollmentDate = Utils.getDateFromDateTime(enrollment?.enrollmentDate)

                    // Only need to update if date is different
                    if (currentEnrollmentDate && (currentEnrollmentDate != enrollmentDate)) {
                        // Set the new enrollmentDate
                        enrollment.enrollmentDate = enrollmentDate

                        // Update the enrollment
                        def enrollmentResult = updateEnrollment(auth, enrollment)

                        if (!enrollmentResult?.success) {
                            errors << [code: "survey.enrollment.notUpdated"]
                        }
                    }

                } else {
                    errors << [code: "survey.trackedEntityInstance.notUpdated"]
                }
            } else { // else, create a new tracked entity instance and enrollment

                // Create the tracked entity instance
                trackedEntityInstanceResult = createOrUpdateTrackedEntityInstance(auth, trackedEntityAttributeLookup, optionSetLookup, surveyRow, programName, orgUnitId, trackedEntityId)

                // Check the TrackedEntityInstance was created
                if (trackedEntityInstanceResult?.success) {
                    // Get the enrollment date from the surveyRow using the supplied fields
                    def enrollmentDate = Utils.getDateFromYearMonthDateFields(surveyRow, fields.yearMonthDay)

                    // Get the trackedEntityInstanceId (reference from the response from DHIS2)
                    trackedEntityInstanceId = trackedEntityInstanceResult.reference

                    // Create the enrollment
                    def enrollmentResult = createEnrollment(auth, trackedEntityInstanceId, programId, orgUnitId, enrollmentDate, enrollmentDate)

                    if (!enrollmentResult?.success) {
                        errors << [code: "survey.enrollment.notCreated"]
                    }
                } else {
                    errors << [code: "survey.trackedEntityInstance.notCreated"]
                }
            }
            // If there was an error here, add to errors
            if (trackedEntityInstanceResult?.errors) {
                errors.addAll(trackedEntityInstanceResult.errors)
            }
        } else {
            def orgUnitCode = surveyRow[fields.orgUnit]
            def orgUnitName = orgUnit.name
            def args = orgUnitCode
            if (orgUnitName) {
                args = "${args} - ${orgUnitName}"
            }
            errors << [code: "survey.orgUnit.notFound", args: [args]]
        }


        return errors
    }

    /**
     * Retrieve existing trackedEntityInstance id based on the attribute field/value supplied.
     *
     * @param surveyRow The row of data to import
     * @param orgUnitId The id of the org unit to find tracked entity instances for
     * @param attributeId The id of the attribute to find tracked entity instances for
     * @param surveyField The field to retrieve the value for
     * @return The id of the tracked entity instance found
     */
    private def getTrackedEntityInstanceId(auth, def surveyRow, def orgUnitId, def attributeId, def surveyField) {
        log.debug "getTrackedEntityInstanceId, orgUnitId: " + orgUnitId + ", attributeId: " + attributeId + ", surveyRowValue: " + surveyRow[surveyField]

        def trackedEntityInstanceId = trackedEntityInstanceService.getIdByOrgUnitAttributeAndValue(auth, orgUnitId, attributeId, surveyRow[surveyField])

        log.debug "trackedEntityInstanceId: " + trackedEntityInstanceId

        return trackedEntityInstanceId
    }

    /**
     * Create or update tracked entity instance
     *
     * @param trackedEntityAttributeLookup The tracked entity attribute lookup map
     * @param optionSetLookup The option set lookup map
     * @param surveyRow The row of data to import
     * @param programName The name of the associated program
     * @param orgUnitId The id of the org unit
     * @param trackedEntityId The id of the tracked entity
     * @param trackedEntityInstanceId The id of the tracked entity instance
     * @return result containing DHIS2 response and any errors
     */
    private def createOrUpdateTrackedEntityInstance(def auth, def trackedEntityAttributeLookup, def optionSetLookup,
                                                    def surveyRow, def programName, def orgUnitId, def trackedEntityId,
                                                    def trackedEntityInstanceId=null) {

        def jsonAttributesRow = [:]

        jsonAttributesRow << [trackedEntity: trackedEntityId, orgUnit: orgUnitId]
        def jsonAttributes = []
        def missingAttributes = []
        surveyRow.each { key, value ->
            def prefixedKey = Utils.addPrefix(programName, key.trim())
            def trackedEntityAttribute = trackedEntityAttributeLookup[prefixedKey]
            if (trackedEntityAttribute) {
                def type = trackedEntityAttribute.valueType
                if (value && value != "") {
                    def attributeValue = getAttributeValue(auth, trackedEntityAttribute, optionSetLookup, value.trim())
                    // Only if we have a value do we populate the attribute....this may need to change
                    if (attributeValue && attributeValue != "") {
                        try {
                            def id = trackedEntityAttribute?.id
                            jsonAttributes << [attribute: id, value: attributeValue]
                        } catch (Exception e) {
                            log.error "Exception: " + e
                        }
                    } else {
                        log.debug "No value for attribute: " + prefixedKey
                    }
                }
            } else {
                // Missing attribute, add the key to missingAttributes list
                log.error("No trackedEntityAttribute for: " + prefixedKey)
                missingAttributes << prefixedKey
            }
        }
        jsonAttributesRow << [attributes: jsonAttributes]

        log.debug "jsonAttributesRow: " + jsonAttributesRow
        log.debug "json: " + new JsonBuilder(jsonAttributesRow)

        // Send to DHIS 2
        def result
        // Create new TrackedEntityInstance
        if (!trackedEntityInstanceId) {
            result = trackedEntityInstanceService.create(auth, jsonAttributesRow)
        } else { // Update existing TrackedEntityInstance
            result = trackedEntityInstanceService.update(auth, jsonAttributesRow, trackedEntityInstanceId)
        }

        // If we have missing attributes, add error to result
        if (missingAttributes) {
            result.errors << [code: "survey.attributes.missing", args: [missingAttributes.join(',')]]
        }
        return result
    }

    /**
     * Create the enrollment for the trackedEntityInstance
     *
     * @param trackedEntityInstanceId The id of the tracked entity instance
     * @param programId The id of the program
     * @param orgUnitId The id of the org unit
     * @param enrollmentDate The enrollment date
     * @param incidentDate The incident date
     * @return The parsed Result object from the DHIS 2 API call
     */
    private def createEnrollment(def auth, def trackedEntityInstanceId, def programId, def orgUnitId,
                                 def enrollmentDate, def incidentDate) {

        // Enrollment data
        def enrollment = [
            trackedEntityInstance: trackedEntityInstanceId,
            program: programId,
            orgUnit: orgUnitId,
            enrollmentDate: enrollmentDate,
            incidentDate: incidentDate
        ]

        // Create the enrollment...
        def enrollmentResult = enrollmentService.create(auth, enrollment)
        log.debug "createEnrollment, enrollmentResult: " + enrollmentResult
        return enrollmentResult
    }

    /**
     * Update the enrollment for the trackedEntityInstance
     *
     * @param auth DHIS 2 Credentials
     * @param enrollment The enrollment to update
     * @return The parsed Result object from the DHIS 2 API call
     */
    private def updateEnrollment(def auth, def enrollment) {
        // Update the enrollment...
        def enrollmentResult = enrollmentService.update(auth, enrollment)
        log.debug "updateEnrollment, enrollmentResult: " + enrollmentResult
        return enrollmentResult
    }

    /**
     * Get the orgUnit for this row (based on the supplied org unit field)
     *
     * @param auth DHIS 2 Credentials
     * @param surveyRow The row of data to import
     * @param optionSetLookup The option set lookup map
     * @param organisationUnitLookup The org unit lookup map
     * @param prefix The prefix to use
     * @param orgUnitField The value to add the prefix to
     * @return The org unit found (name + id)
     */
    private def getOrganisationUnitId(def auth, def surveyRow, def optionSetLookup, def organisationUnitLookup,
                                      def prefix, def orgUnitField) {

        def optionCode = surveyRow[orgUnitField]
        log.debug "optionCode: " + optionCode

        def name

        optionSetLookup.each { key, value ->
            if (value.code == Utils.addPrefix(prefix, orgUnitField)) {
                // got the options, find the right one
                value.options.each { option ->
                    //log.debug "code: " + option.code + ", name: " + option.name
                    if (option.code == optionCode) {
                        name = option.name
                    }
                }
            }
        }

        def orgUnitId = organisationUnitLookup.get(name?.toLowerCase())

        log.debug "orgUnitId: " + orgUnitId

        return [name: name, orgUnitId: orgUnitId]
    }

    /**
     * Gets the attribute value for the supplied parameters
     *
     * @param auth DHIS 2 Credentials
     * @param trackedEntityAttribute The tracked entity attribute to get the value for
     * @param optionSetLookup The option set lookup map
     * @param value The value to match
     * @return The attribute value found if any
     */
    private def getAttributeValue(def auth, def trackedEntityAttribute, def optionSetLookup, def value) {
        //log.debug "trackedEntityAttribute: " + trackedEntityAttribute
        def attributeValue = value

        // Get the optionSetId from the trackedEntityAttribute
        def optionSetId = trackedEntityAttribute.optionSet?.id

        // If we have an optionSet associated with the trackedEntityAttribute, get the matching option
        if (optionSetId) {
            def optionSet = optionSetLookup[optionSetId]
            //log.debug "optionSet: " + optionSet

            // Get the option
            def matchingOption
            optionSet?.options?.each { option ->
                //log.debug "option: " + option
                if (option.code == value) {
                    matchingOption = option
                }
            }

            // If matching option get the code
            if (matchingOption) {
                //log.debug "attributeValue: " + attributeValue
                attributeValue = matchingOption.code
            } else { // need to create the missing option
                def optionValue = [code: value, name: ("" + value).padLeft(2, "0")]

                def optionValueResult = optionValueService.create(auth, optionValue)

                if (optionValueResult.success) {
                    def optionValueId = optionValueResult.lastImported

                    // Add the option to the optionSet
                    def addOptionResult = optionSetService.addOption(auth, optionSetId, optionValueId)

                    // Add the option to the optionSet in the optionSetLookup so it doesn't get created multiple times
                    optionValue << [id: optionValueId]
                    optionSet.options << optionValue

                    // value
                    attributeValue = optionValue.code
                } else {
                    return null
                }
            }
        }

        // Return the value, which could be the passed in value or the code from the optionSet options
        return attributeValue
    }
}
