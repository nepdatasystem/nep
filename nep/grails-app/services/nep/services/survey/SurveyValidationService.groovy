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

import javax.annotation.PostConstruct

import nep.services.PropertiesService

/**
 * Service to validate csv files
 */
@Transactional
class SurveyValidationService {

    private static String VARIABLE_NAME = "VARIABLE_NAME"
    private static String VARIABLE_LABEL = "VARIABLE_LABEL"
    private static String ANSWER_LABEL = "ANSWER_LABEL"
    private static String ANSWER_CODE = "ANSWER_CODE"
    private static String VARIABLE_TYPE = "VARIABLE_TYPE"

    def variables = [:]

    PropertiesService propertiesService

    /**
     * Initializes this class with instance-specific properties contained in the nep.properties file
     * @return
     */
    @PostConstruct
    def init() {
        def variablesString = propertiesService.getProperties().getProperty("nep.survey.codebook.variables", null)
        if (!variablesString) {
            log.error "Config property: nep.survey.codebook.variables has not been defined in nep.properties"
        } else {
            (variablesString?.split(",") as List).each { entry ->
                def keyValuePair = entry?.split(":") as List
                if (keyValuePair?.size() == 2) {
                    variables << [(("${keyValuePair[0]}" as String).trim()): keyValuePair[1].trim().toLowerCase()]
                }
            }
        }
        log.debug "nep.survey.codebook.variables: " + variables
    }

    /**
     * Verifies that the data file is well formed
     *
     * @param file The file to verify
     * @param fields The fields to verify
     * @return a map of verification stats (success, errors if any)
     */
    def verifyDataFile(def file, def fields) {

        def reader = file.toCsvMapReader()
        def columnHeaders = reader.first()

        def headers = [:]
        columnHeaders.each { key, value ->
            headers << [("${key}" as String): value.trim()]
        }

        def missingFields = []
        def verify = [success: true]
        (fields.required.split(",") as List).each { field ->
            if (!headers.containsKey(field.trim())) {
                missingFields << "${field.trim()}"
            }
        }

        if (missingFields) {
            verify.success = false
            verify.errors = [[code: "survey.data.file.csv.headings", args: missingFields]]
        }
        log.debug "verify: " + verify
        return verify
    }

    /**
     * Verifies that the metadata file is well formed
     *
     * @param file The file to verify
     * @param fields The fields to verify
     * @return a map of verification stats (success, errors if any)
     */
    def verifyMetadataFile(def file, def fields) {

        def csvMapReader = file.toCsvMapReader()
        def verify = [success: true]
        def headings = []
        def missingHeadings = []
        def missingFields = []

        def firstRow = true
        // Get all of the variable names so we can check for missing ones
        def variableNames = []
        csvMapReader.each { rowData ->
            // Update row of data to case insensitive map
            Map<String, String> row = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            row.putAll(rowData);
            if (row[variables[VARIABLE_NAME]]) {
                variableNames << row[variables[VARIABLE_NAME]].trim()
            }
            if (firstRow) {
                headings = row.keySet().collect { it -> it.toLowerCase() }
                firstRow = false
            }
        }

        // Verify that the file contains the correct column headings
        variables.each { key, value ->
            if (!headings.contains(value.toLowerCase())) {
                missingHeadings << value
            }
        }


        // Check for any fields missing from the required fields
        log.debug "variableNames: " + variableNames
        (fields.required.split(",") as List).each { field ->
            if (!variableNames.contains(field.trim())) {
                missingFields << "${field.trim()}"
            }
        }

        // Missing column headings
        if (missingHeadings) {
            verify.success = false
            verify.errors = [[code: "survey.metadata.file.csv.headings", args: missingHeadings.join(",")]]
        } else if (missingFields) {         // Missing fields in first column (VARIABLE_NAME)
            verify.success = false
            verify.errors = [[code: "survey.metadata.file.csv.variables", args: missingFields.join(",")]]
        }

        log.debug "verify: " + verify
        return verify
    }

    /**
     * Retrieves the instance-specific program fields
     *
     * @return the instance-specific program fields
     */
    def getProgramFields() {
        def fields = [:]
        def requiredFields = propertiesService.getProperties().getProperty("nep.survey.program.required")
        fields << [required: requiredFields]
        fields << [yearMonthDay: propertiesService.getProperties().getProperty("nep.survey.program.yearMonthDay")]
        fields << [orgUnit:  propertiesService.getProperties().getProperty("nep.survey.program.orgUnit")]
        return fields
    }

    /**
     * Retrieves the instance-specific program stage fields
     *
     * @return the instance-specific program stage fields
     */
    def getProgramStageFields() {
        def fields = [:]
        def requiredFields = propertiesService.getProperties().getProperty("nep.survey.programStage.required")
        fields << [required: requiredFields]
        fields << [yearMonthDay: propertiesService.getProperties().getProperty("nep.survey.programStage.yearMonthDay")]
        fields << [orgUnit:  propertiesService.getProperties().getProperty("nep.survey.programStage.orgUnit")]
        return fields
    }

    /**
     * Retrieves the instance-specific program column headings
     *
     * @return the instance-specific program column headings
     */
    def getProgramColumnHeadings () {
        def variables = [:]
        def variablesString = propertiesService.getProperties().getProperty("nep.survey.codebook.variables", null)
        if (!variablesString) {
            log.error "Config property: nep.survey.codebook.variables has not been defined in nep.properties"
        } else {
            (variablesString?.split(",") as List).each { entry ->
                def keyValuePair = entry?.split(":") as List
                if (keyValuePair?.size() == 2) {
                    variables << [("${keyValuePair[0]}"?.trim() as String): keyValuePair[1]?.trim()]
                }
            }
        }
        return variables
    }
}
