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
 * Service to parse a survey codebook (metadata) into DHIS 2
 */
@Transactional
class SurveyCodebookParserService {

    private static String VARIABLE_NAME = "VARIABLE_NAME"
    private static String VARIABLE_LABEL = "VARIABLE_LABEL"
    private static String ANSWER_LABEL = "ANSWER_LABEL"
    private static String ANSWER_CODE = "ANSWER_CODE"
    private static String VARIABLE_TYPE = "VARIABLE_TYPE"
    private static String OPEN_ENDED = "OPEN_ENDED"

    PropertiesService propertiesService
    def variables = [:]
    def types = [:]

    /**
     * Sets up all the variables from the configuration for a specific instance
     *
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
                    variables << [("${keyValuePair[0]}"?.trim() as String): keyValuePair[1]?.trim().toLowerCase()]
                }
            }
        }
        log.debug "nep.survey.codebook.variables: ${variables}"

        def typesString = propertiesService.getProperties().getProperty("nep.survey.codebook.types", null)
        if (!typesString) {
            log.error "Config property: nep.survey.codebook.types has not been defined in nep.properties"
        } else {
            (typesString?.split(",") as List).each { entry ->
                def keyValuePair = entry?.split(":") as List
                if (keyValuePair?.size() == 2) {
                    types<< [("${keyValuePair[0]}" as String): keyValuePair[1]]
                }
            }
        }
        log.debug "nep.survey.codebook.types: ${types}"
    }

    /**
     * Parses a file into a codebook map of variable data by variable code
     *
     * @param file The file to parse
     * @return The parsed codebook map of variable data by variable code
     */
    private def parse(File file) {

        def codebook = []

        def reader = file.toCsvMapReader()
        def codebookData = []
        reader.each { map ->
            codebookData << map
        }
        log.debug "codebookData: " + codebookData

        def variableData = null
        def options = null
        codebookData.each { rowData ->
            // Update row of data to case insensitive map
            Map<String, String> row = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
            row.putAll(rowData)
            def variableName = row[variables[VARIABLE_NAME]]?.trim()
            def variableLabel = row[variables[VARIABLE_LABEL]]?.trim()
            def variableType = row[variables[VARIABLE_TYPE]]?.trim()

            if (variableName != "") {
                if (variableData) {
                    if (options) {
                        variableData << [options: options]
                    }
                    codebook << ["${variableData.code}".toString().trim(), variableData]
                }
                // Get the basic data
                variableData = [code: variableName?.trim(), name: variableLabel?.trim(), type: variableType?.trim()]

                // If "Open ended", we have no answers
                if (row[variables[ANSWER_LABEL]]?.equalsIgnoreCase(types[OPEN_ENDED])) {
                    options = null
                } else if (!row[variables[ANSWER_LABEL]]){
                    options = []
                }
            } else if (options != null) {
                options << [code: row[variables[ANSWER_CODE]].trim(), name: row[variables[ANSWER_LABEL]].trim()]
            }
        }

        // Last row...
        if (variableData) {
            if (options) {
                variableData << [options: options]
            }
            codebook << ["${variableData.code}".toString().trim(), variableData]
        }

        log.debug "codebook: " + codebook
        return codebook
    }
}
