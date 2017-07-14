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

import com.twopaths.dhis2.api.Result
import com.twopaths.dhis2.services.CategoryComboService
import com.twopaths.dhis2.services.OrganisationUnitService
import com.twopaths.dhis2.services.ProgramService
import com.twopaths.dhis2.services.ProgramStageService
import com.twopaths.dhis2.services.SqlViewService
import com.twopaths.dhis2.services.UserRoleService
import grails.converters.JSON
import grails.transaction.Transactional
import nep.services.PropertiesService
import nep.util.Utils

/**
 * Service to manage Survey Programs
 */
@Transactional
class SurveyProgramService {

    PropertiesService propertiesService

    ProgramService programService
    ProgramStageService programStageService
    OrganisationUnitService organisationUnitService
    UserRoleService userRoleService
    SqlViewService sqlViewService
    CategoryComboService categoryComboService

    /**
     * Create the program and assign the program to the user role
     *
     * @param auth DHIS 2 Credentials
     * @param programData The data to use to create the program
     * @param orgUnitLevel The level of org units to associate with this program
     * @param roles User roles to associate with this program
     * @return the DHIS 2 Connector API Result object for the creation
     */
    def createProgram(def auth, def programData, def orgUnitLevel, def roles) {

        // Add the
        def organisationUnits = organisationUnitService.findByLevel(auth, orgUnitLevel).collect {orgUnitId -> [id: orgUnitId]}
        programData << [organisationUnits: organisationUnits]

        // Create the program
        def result = programService.create(auth, programData)

        if (result.success) {
            def notFoundRoles = []
            roles.each { roleName ->
                def role = userRoleService.findByRole(auth, roleName)
                if (role) {
                    userRoleService.assignProgramToUserRole(auth, role, result.lastImported)
                } else {
                    notFoundRoles << roleName
                }
            }

            // No roles...
            if (notFoundRoles.size() > 0) {
                result = new Result(errors: [[code: "userRoles.missing", args: roles]])
            }
        }

        log.debug "createProgram, result: " + result
        return result
    }

    /**
     * Retrieves the Id of the DHIS 2 default category combo.
     * This often needs to be used to post with objects that do not explicitly have a category combo set.
     *
     * @param auth DHIS 2 Credentials
     * @return The Id of the default category combo
     */
    def getDefaultCategoryComboId(def auth) {
        return categoryComboService.getDefaultCategoryComboId(auth)
    }

    /**
     * Get the program
     *
     * @param auth DHIS 2 Credentials
     * @param programId The id of the program to get
     * @param fields The list of fields to return in the Program
     * @return The found program if any
     */
    def getProgram(def auth, def programId, ArrayList<String> fields = [":all", "trackedEntity[id,name]", "programTrackedEntityAttributes[id,trackedEntityAttribute[id]]"]) {

        def queryParams = [:]

        if (fields?.size() > 0) {
            queryParams.put("fields", fields.join(','))
        }

        def program = programService.get(auth, programId, queryParams)

        log.debug "getProgram, program: " + program

        return program
    }

    /**
     * Gets all programs in DHIS 2
     *
     * @param auth DHIS 2 Credentials
     * @return List of programs if any
     */
    def getPrograms(def auth) {

        def programs = []

        programs << programService.findAll(auth)

        programs = programs.get(0)

        programs?.programs?.each { program ->
            program.created =  Utils.getDateTimeFromData(program.created)
            program.lastUpdated = Utils.getDateTimeFromData(program.lastUpdated)
        }

        return programs
    }

    /**
     * Get trackedEntity for the program
     *
     * @param auth DHIS 2 Credentials
     * @param programId the id of the program to get the tracked entity for
     * @param query The map of query parameters to use to retrieve the program
     * @return The tracked entity found if any
     */
    def getTrackedEntity(def auth, def programId, def query=[:]) {

        def program = programService.get(auth, programId, query)

        def trackedEntity = program?.trackedEntity

        return trackedEntity
    }

    /**
     * Retrieves a map of all program and program stages and if they have or don't have
     * data and metadata
     *
     * @param auth DHIS 2 Credentials
     * @return a map of all program and program stages indicating if they do or do not have data and metadata
     */
    def listProgramAndStageData(def auth) {

        def model = [:]

        // Get the programs and stages for that program (or all program Stages if no programId supplied)
        def programsAndStages = getProgramsAndStages(auth)

        model << [programs: programsAndStages.programs, programStages: programsAndStages.programStages ?: []]

        // Lists of programs/programStages that have metadata/data
        def programHasMetadata = []
        def programHasData = []
        def programStageHasMetadata = []
        def programStageHasData = []

        // Programs
        programsAndStages.programs.each { program ->
            log.debug "program: " + program
            // If we have a program, check if it has data
            if (program) {
                // Program metadata
                if (program?.programTrackedEntityAttributes?.size() > 0) {
                    log.debug "program ${program.name} has attributes assigned"
                    programHasMetadata << ("${program.id}" as String)
                }
            }
        }
        programHasData = findProgramsWithData(auth)
        model << [programHasMetadata: programHasMetadata]
        model << [programHasData: programHasData]

        // Program Stages
        def programStages = programsAndStages.programStages
        programStages?.each { programStage ->
            // ProgramStage metadata
            if (programStage?.programStage?.programStageDataElements?.size() > 0) {
                log.debug "programStage ${programStage.name} has metadata"
                programStageHasMetadata << ("${programStage.id}" as String)
            }
        }
        programStageHasData = findProgramStagesWithData(auth)
        model << [programStageHasMetadata: programStageHasMetadata]
        model << [programStageHasData: programStageHasData]

        log.debug "programHasMetadata: " + programHasMetadata
        log.debug "programHasData: " + programHasData
        log.debug "programStageHasMetadata: " + programStageHasMetadata
        log.debug "programStageHasData: " + programStageHasData

        return model
    }

    /**
     * Gets program and program stage data for the specified program
     *
     * @param auth DHIS 2 Credentials
     * @param programId Id of the program to retrieve data for
     * @return a map of information about the specified program and associated stages (has data / has metadata)
     */
    def getProgramAndStageData(def auth, def programId) {

        def model = [:]
        // Get the programs and stages for that program (or all program Stages if no programId supplied)
        def programAndStages = getProgramAndStages(auth, programId)
        model << [programs: programAndStages.programs, program: programAndStages.program, trackedEntityAttributes: programAndStages.trackedEntityAttributes, programStages: programAndStages.programStages ?: []]

        // Lists of programs/programStages that have metadata/data
        def programHasMetadata = []
        def programHasData = []
        def programStageHasMetadata = []
        def programStageHasData = []

        def program = programAndStages.program
        log.debug "program: " + program
        // If we have a program, check if it has data
        if (program) {
            // Program metadata
            if (program?.programTrackedEntityAttributes?.size() > 0) {
                log.debug "program ${program.name} has attributes assigned"
                programHasMetadata << ("${program.id}" as String)
            }

            // Program Stages
            def programStages = programAndStages.programStages
            programStages?.each { programStage ->
                // ProgramStage metadata
                if (programStage?.programStage?.programStageDataElements?.size() > 0) {
                    programStageHasMetadata << ("${programStage?.programStage?.id}" as String)
                }
            }
        }
        programHasData = findProgramsWithData(auth)
        programStageHasData = findProgramStagesWithData(auth)

        model << [programHasMetadata: programHasMetadata]
        model << [programHasData: programHasData]
        model << [programStageHasMetadata: programStageHasMetadata]
        model << [programStageHasData: programStageHasData]

       log.debug "programHasMetadata: " + programHasMetadata
       log.debug "programHasData: " + programHasData
       log.debug "programStageHasMetadata: " + programStageHasMetadata
       log.debug "programStageHasData: " + programStageHasData

        return model
    }

    /**
     * Gets all programs and program stages
     *
     * @param auth DHIS 2 Credentials
     * @return a map of programs and program stages
     */
    def getProgramsAndStages(def auth) {

        def programsAndStages = [:]

        // Get all programs
        def programs = programService.findAll(auth, [fields: ":all,id,name,user[name],created,programStages[id,name],programTrackedEntityAttributes"])?.programs
        programsAndStages << [programs: programs?.sort{it.name}]

        def programStages = programStageService.findAll(auth, [fields: ":all,id,name,programStageDataElements"])?.programStages
        programsAndStages << [programStages: programStages?.sort {it.created}]

        return programsAndStages
    }

    /**
     * Gets program and associated program stages for the specified program
     *
     * @param auth DHIS 2 Credentials
     * @param programId Id of the program to retrieve data for
     * @return Map of program and associated program stages and related collections
     */
    def getProgramAndStages(def auth, def programId) {

        def programAndStages = [:]

        // Get all programs //:all,programTrackedEntityAttributes[trackedEntityAttribute[id,code,name]]
        def programsFindAll = programService.findAll(auth, [fields: ":all,programStages[id,name,programStageDataElements[dataElement[id,code,name]]],programTrackedEntityAttributes[trackedEntityAttribute[id,code,name]]"])
        def programs = programsFindAll?.programs
        programAndStages << [programs: programs?.sort{it.name}]

        def program = programs?.find {it.id == programId}
        if (program) {
            def trackedEntityAttributes = program?.programTrackedEntityAttributes?.collect {it.trackedEntityAttribute}?.sort { it.code }
            programAndStages << [program: program, trackedEntityAttributes: trackedEntityAttributes]
        }

        def programStages = []
        program?.programStages?.each {
            def query = [fields: ":all,programStageDataElements[dataElement[id,code,name]]"]
            def programStage = programStageService.get(auth, it.id, query)
            def programStageMap = [programStage: programStage]
            def dataElements = programStage?.programStageDataElements?.collect { it.dataElement }?.sort { it.code }

            log.debug "dataElements:" + dataElements

            programStageMap << [dataElements: dataElements]
            programStages << programStageMap
        }

        // Sort the program stages
        programStages?.sort { it.programStage.created }

        programAndStages << [programStages: programStages]

        return programAndStages
    }

    /**
     * Determines if the specified program has data or not
     *
     * @param auth DHIS 2 Credentials
     * @param programID The id of the program to check for data
     * @return If the program has data or not (boolean)
     */
    boolean programHasData (def auth, def programID) {
        def dbRows = findProgramsWithData (auth, ["uid" : programID])

        return (dbRows?.size() > 0)
    }

    /**
     * Finds all programs that have data
     *
     * @param auth DHIS 2 Credentials
     * @param criteria The map of criteria for this query
     * @return List of programs that contain data
     */
    def findProgramsWithData (def auth, def criteria = [:]) {

        def sqlViewName = propertiesService.getProperties().getProperty('nep.sqlview.programs.with.data.name', null)

        if (!sqlViewName) {
            throw new Exception("No application property specified for 'nep.sqlview.programs.with.data.name'. " +
            "Please create this property and create corresponding view in DHIS 2, then restart the application")
        }
        def sqlView = sqlViewService.findByName(auth, sqlViewName)
        if (!sqlView) {
            throw new Exception("Unable to find sql view with name ${sqlViewName}. Please ensure this has been created in DHIS 2")
        }
        // need to execute the view first in case the actual underlying db view was deleted
        sqlViewService.executeView(auth, sqlView.id)
        def data = sqlViewService.getViewData(auth, sqlView.id, criteria)

        def programsWithData = data?.flatten { it as String}

        log.debug "programsWithData: " + programsWithData

        return programsWithData
    }

    /**
     * Determines if the specified program stage has data or not
     *
     * @param auth DHIS 2 Credentials
     * @param programStageID The id of the program stage to check for data
     * @return If the program stage has data or not (boolean)
     */
    boolean programStageHasData (def auth, def programStageID) {
        def dbRows = findProgramStagesWithData (auth, ["uid" : programStageID])

        return (dbRows?.size() > 0)
    }

    /**
     * Finds all program stages that have associated data
     *
     * @param auth DHIS 2 Credentials
     * @param criteria The map of criteria for this query
     * @return A list of program stages that contain data
     */
    def findProgramStagesWithData (def auth, def criteria = [:]) {

        def sqlViewName = propertiesService.getProperties().getProperty('nep.sqlview.program.stages.with.data.name', null)

        if (!sqlViewName) {
            throw new Exception("No application property specified for 'nep.sqlView.programStages.with.data.name'. " +
            "Please create this property and create corresponding view in DHIS 2, then restart the application")
        }
        def sqlView = sqlViewService.findByName(auth, sqlViewName)
        if (!sqlView) {
            throw new Exception("Unable to find sql view with name ${sqlViewName}. Please ensure this has been created in DHIS 2")
        }
        // need to execute the view first in case the actual underlying db view was deleted
        sqlViewService.executeView(auth, sqlView.id)
        def data = sqlViewService.getViewData(auth, sqlView.id, criteria)

        def programStagesWithData = data?.flatten { it as String }

        log.debug "programStagesWithData: " + programStagesWithData

        return programStagesWithData
    }


}
