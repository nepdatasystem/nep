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

import com.twopaths.dhis2.services.ChartService
import com.twopaths.dhis2.services.DashboardItemService
import com.twopaths.dhis2.services.DataElementService
import com.twopaths.dhis2.services.EventChartService
import com.twopaths.dhis2.services.EventReportService
import com.twopaths.dhis2.services.EventService
import com.twopaths.dhis2.services.MapService
import com.twopaths.dhis2.services.MetadataService
import com.twopaths.dhis2.services.ProgramDataElementService
import com.twopaths.dhis2.services.ProgramIndicatorService
import com.twopaths.dhis2.services.ProgramRuleService
import com.twopaths.dhis2.services.ProgramRuleVariableService
import com.twopaths.dhis2.services.ProgramService
import com.twopaths.dhis2.services.ProgramStageService
import com.twopaths.dhis2.services.ProgramTrackedEntityAttributeService
import com.twopaths.dhis2.services.ReportTableService
import com.twopaths.dhis2.services.TrackedEntityAttributeService
import com.twopaths.dhis2.services.TrackedEntityInstanceService
import com.twopaths.dhis2.services.UserRoleService
import grails.transaction.Transactional
import nep.services.aggregate.AggregateDataSetDeletionService

/**
 * Service to manage survey program deletion
 */
@Transactional
class SurveyProgramDeletionService {

    SurveyProgramService surveyProgramService
    AggregateDataSetDeletionService aggregateDataSetDeletionService
    ProgramService programService
    ProgramStageService programStageService
    UserRoleService userRoleService
    ProgramTrackedEntityAttributeService programTrackedEntityAttributeService
    TrackedEntityAttributeService trackedEntityAttributeService
    TrackedEntityInstanceService trackedEntityInstanceService
    DataElementService dataElementService
    EventService eventService
    ProgramDataElementService programDataElementService
    ProgramIndicatorService programIndicatorService
    ProgramRuleService programRuleService
    ProgramRuleVariableService programRuleVariableService

    ReportTableService reportTableService
    ChartService chartService
    MapService mapService
    EventReportService eventReportService
    EventChartService eventChartService
    DashboardItemService dashboardItemService

    MetadataService metadataService

    final static int DATA_PAGING_SIZE = 200

    /**
     * This method will delete everything related to a Survey (Program)
     * - data
     * - metadata
     * - Survey (Program) & Program Stages
     *
     * @param auth DHIS 2 credentials
     * @param programId The id of the program to delete
     * @return Array of errors if any
     */
    def delete(def auth, def programId) {

        log.debug "delete program ${programId}"

        def errors = []

        // 1. Delete all the data for the survey and related program stages
        errors.addAll(deleteSurveyData(auth, programId))
        if (!(errors.size() > 0)) {
            // 2. Delete all the metadata for the survey and related program stages
            errors.addAll(deleteSurveyMetadata(auth, programId))

            if (!(errors.size() > 0)) {
                // 3. Delete the actual survey and related program stages
                errors.addAll(deleteSurvey(auth, programId))
            }
        }

        return errors

    }

    /**
     * Deletes a Survey (Program) and its related Program Stages
     * Note that this method should only be called if all related data and metadata has already been deleted
     *
     * @param auth DHIS 2 credentials
     * @param programId The id of the program to delete
     * @return Array of errors if any
     */
    def deleteSurvey (def auth, def programId) {

        log.debug "delete survey ${programId}"

        def errors = []

        // also retrieve programTrackedEntityAttributes as a sanity check that the metadata has already been deleted
        // This isn't foolproof, but the javadocs for this method states that all related data and metadata needs to
        // already be deleted, and inner errors will be thrown if they exist.
        def program = surveyProgramService.getProgram(auth, programId, [
                "id", "name", "programStages[id]","userRoles[id],programTrackedEntityAttributes[id]"
        ])


        if (program) {
            // if there is metadata (if it has program tracked entity attributes), cannot delete survey, have to delete
            // metadata first
            if (program.programTrackedEntityAttributes?.size() > 0) {

                errors << [code: "survey.not.deleted.has.metadata", args: [program.name, programId]]

            } else {

                // first delete all the associated program stages
                if (program.programStages) {

                    program.programStages.each { programStage ->
                        errors.addAll(deleteProgramStage(auth, programStage.id))
                    }

                }
                if (!(errors.size() > 0)) {
                    // now delete the program itself
                    errors.addAll(deleteProgram(auth, program))
                }
            }
            if (errors.size() > 0) {
                errors << [code: "survey.not.deleted", args: [program.name, programId]]
            }
        }

        return errors
    }

    /**
     * Deletes all the metadata associated with a Survey (Program) including all the metadata associated with
     * its Program Stages, and any associated Indicators, Program Indicators, Program Rules, and Program Rule Variables
     *
     * Note that this method should only be called if all related metadata has already been deleted
     *
     * @param auth DHIS 2 credentials
     * @param programId The id of the program to delete metadata for
     * @return Array of errors if any
     */
    def deleteSurveyMetadata (def auth, def programId) {

        log.debug "delete survey metadata for program ${programId}"

        def errors = []

        def program = surveyProgramService.getProgram(auth, programId, [
                "id", "name", "programStages[id]","programIndicators[id]", "programRules[id]", "programRuleVariables[id]",
                "programTrackedEntityAttributes[id,trackedEntityAttribute[id,optionSet[id,name,options[id]]]]"
        ])

        if (program) {

            // can't delete metadata if there are data
            if (surveyProgramService.programHasData(auth, programId)) {
                errors << [code: "survey.metadata.not.deleted.has.data", args: [program.name, programId]]
            } else {

                def programDataElements = getProgramDataElementsForProgram(auth, program.id)

                errors.addAll(deleteProgramReportsAndSupportingMetadata (auth, program, programDataElements))

                if (!(errors.size() > 0)) {
                    // programStageDataElements are deleted automatically when underlying data element is deleted
                    if (program.programStages) {
                        program.programStages.each { programStage ->
                            errors.addAll(deleteProgramStageMetadata(auth, programStage.id))
                        }

                    } // end if programStages exist
                    if (!(errors.size() > 0)) {
                        if (program.programTrackedEntityAttributes) {
                            errors.addAll(deleteProgramTrackedEntityAttributes(auth,
                                    programId, program.name, program.programTrackedEntityAttributes))
                        }

                    }
                }
                if (errors.size() > 0) {
                    errors << [code: "survey.metadata.not.deleted", args: [program.name, programId]]
                }
            }
        }
        else {
            errors << [code: "survey.metadata.survey.delete.program.not.found", args: [programId]]
        }

        return errors

    }

    /**
     * Deletes all the Reports and supporting metadata associated with a Survey (Program) including
     * any associated Indicators, Program Indicators, Program Rules, and Program Rule Variables
     *
     * @param auth DHIS 2 credentials
     * @param programId The id of the program to delete reports and supporting metadata for
     * @return Array of errors if any
     */
    public def deleteProgramReportsAndSupportingMetadata (def auth, def program, def programDataElements) {

        def errors = []

        // delete all ProgramIndicators
        errors.addAll(deleteProgramIndicators(auth, program))

        if (!(errors.size() > 0)) {

            // delete all ProgramRules
            errors.addAll(deleteProgramRules(auth, program))

            if (!(errors.size() > 0)) {

                // delete all ProgramRuleVariables
                errors.addAll(deleteProgramRuleVariables(auth, program))

                if (!(errors.size() > 0)) {

                    // delete all related reports for this program
                    errors.addAll(deleteReports(auth, program.id, programDataElements, program.programTrackedEntityAttributes))

                    if (!(errors.size() > 0)) {
                        /*
                         * Need to delete programDataElements first so the deletion of programStageDataElements can work
                         * assumption that all programDataElements have dataElements that are associated with specific programStages
                         * and therefore will be deleted as part of programStageDeletion
                         * programDataElements appear to be created as part of the analytics process that DHIS 2 runs
                         * Example API call to retrieve programDataElements for a program:
                         * -> <server>/dhis/api/25/programDataElements?fields=program[id,name],dataElement[id,name]&paging=false
                         */
                        errors.addAll(deleteProgramDataElements(auth, programDataElements))
                    }
                }
            }
        }

        return errors
    }

    /**
     * Deletes all the data associated with a Survey (Program) including all the data associated with its Program Stages
     *
     * @param auth DHIS 2 credentials
     * @param programId The id of the program to delete data for
     * @return Array of errors if any
     */
    def deleteSurveyData (def auth, def programId) {
        log.debug "delete survey data for program ${programId}"

        def errors = []
        def program = surveyProgramService.getProgram(auth, programId, [
                "id", "name", "programStages[id,name]","organisationUnits[id]"
        ])

        if (program) {
            // first delete all the program stage data
            if (program.programStages) {
                program.programStages.each { programStage ->
                    errors.addAll(deleteProgramStageData(auth, program.id, programStage))
                }
            }
            if (!(errors.size() > 0)) {
                // next delete all the program data
                errors.addAll(deleteProgramData (auth, program))

            }

        }

        return errors
    }

    /*
     *------------------------------------------------------
     * Program / Program Stage Deletion
     *------------------------------------------------------
     */

    /**
     * Deletes the actual Program, and unassigns the associated user roles first.
     * Assumes all other associated objects have already been deleted.
     * If all associated objects have not been deleted first, this will throw errors
     *
     * @param auth DHIS 2 credentials
     * @param program Program to delete
     * @return Array of errors if any
     */
    public def deleteProgram(auth, program) {

        def errors = []

        program.userRoles.each { userRole ->
            errors.addAll(unassignProgramFromUserRole(auth, program.id, userRole))
        }
        if (!(errors.size() > 0)) {
            def programDeleteResult = programService.delete(auth, program.id)
            if (!programDeleteResult.success) {
                log.error("error deleting program ${program.id}")
                errors.addAll(programDeleteResult.errors)
            }
        }
        return errors
    }

    /**
     * Unassigns the specified program from the specified user role
     *
     * @param auth DHIS 2 credentials
     * @param programId The id of the program to unassign from the user role
     * @param userRole The userRole to unassign
     * @return Array of errors if any
     */
    private def unassignProgramFromUserRole(auth, programId, userRole) {

        def errors = []

        def userRoleResult = userRoleService.unassignProgramFromUserRole(auth, userRole, programId)

        if (!userRoleResult.success) {
            log.error("error unassigning program ${programId} from user role ${userRole.id}")
            errors.addAll(userRoleResult.errors)
        }

        return errors
    }

    /**
     * Deletes the Program Stage itself
     *
     * @param auth DHIS 2 credentials
     * @param programStageID ID of the program stage to delete
     * @return Array of errors if any
     */
    public def deleteProgramStage(auth, programStageID) {

        def errors = []

        def programStageDeleteResult = programStageService.delete(auth, programStageID)

        if (!programStageDeleteResult.success) {
            log.error("error deleting programStage ${programStageID}")
            errors.addAll(programStageDeleteResult.errors)
        }

        return errors
    }

    /*
     *------------------------------------------------------
     * Metadata Deletion
     *------------------------------------------------------
     */

    /**
     * Deletes all program indicators for the specified program
     *
     * @param auth DHIS 2 credentials
     * @param program program of which to delete program indicators
     * @return Array of errors if any
     */
    public def deleteProgramIndicators(auth, program) {

        def errors = []

        program?.programIndicators?.each { programIndicator ->
            errors.addAll(deleteProgramIndicator(auth, programIndicator.id))
        }

        if (errors.size() > 0) {
            errors << [code: "survey.program.indicators.not.deleted", args: [program?.name, program?.id]]
        }
        return errors
    }

    /**
     * Deletes all program rules for the specified program
     *
     * @param auth DHIS 2 credentials
     * @param program program of which to delete program rules
     * @return Array of errors if any
     */
    public def deleteProgramRules(auth, program) {
        def errors = []

        program?.programRules?.each { programRule ->
            errors.addAll(deleteProgramRule(auth, programRule.id))
        }
        if (errors.size() > 0) {
            errors << [code: "survey.program.rules.not.deleted", args: [program?.name, program?.id]]
        }

        return errors
    }

    /**
     * Deletes all program rule variables for the specified program.
     * Unfortunately these need to explicitly be deleted since they are not deleted automatically when the program
     * rule is deleted.
     *
     * @param auth DHIS 2 credentials
     * @param program Program to delete program rule variables for
     * @return Array of errors if any
     */
    public def deleteProgramRuleVariables(auth, program) {
        def errors = []

        program?.programRuleVariables?.each { programRuleVariable ->
            errors.addAll(deleteProgramRuleVariable(auth, programRuleVariable.id))
        }
        if (errors.size() > 0) {
            errors << [code: "survey.program.rule.variables.not.deleted", args: [program?.name, program?.id]]
        }

        return errors
    }

    /**
     * Finds a list of programDataElements for the specified program
     *
     * @param auth DHIS 2 credentials
     * @param programId The id of the program to get programDataElementsFor
     * @return List of found program data elements
     */
    public def getProgramDataElementsForProgram(def auth, def programId) {
        def queryParams = [
                fields: "id",
                filter: "program.id:eq:${programId}"
        ]

        return programDataElementService.find (auth, queryParams)
    }

    /*
     * --------------------------------------
     * Start Report Functionality
     * --------------------------------------
     */

    /**
     * Deletes all reports related to the specified program, program data elements and program tracked entity attributes.
     * This includes:
     * - Report Tables
     * - Charts
     * - Maps
     * - Event Reports
     * - Event Charts
     * - Dashboard Items
     *
     * @param auth DHIS 2 credentials
     * @param programDataElements List of program data elements to delete associated reports for
     * @param programTrackedEntityAttributes List of program tracked entity attributes to delete associated reports for
     * @return Array of errors if any
     */
    public def deleteReports(def auth, def programId, def programDataElements = [], def programTrackedEntityAttributes = []) {

        def errors = []

        // Maps
        // Delete all maps with underlying mapViews associated with programDataElements
        errors.addAll(deleteMapsForProgramDataElements(auth, programDataElements))

        // Delete all maps associated with programAttributes
        errors.addAll(deleteMapsForProgramAttributes(auth, programTrackedEntityAttributes))

        // Delete all maps associated with the program itself
        errors.addAll(deleteMapsForProgramId(auth, programId))

        // Charts
        // Delete all charts associated with programDataElements
        errors.addAll(deleteChartsForProgramDataElements(auth, programDataElements))

        // Delete all charts associated with programAttributes
        errors.addAll(deleteChartsForProgramAttributes(auth, programTrackedEntityAttributes))

        // Report Tables
        // Delete all report tables associated with programDataElements
        errors.addAll(deleteReportTablesForProgramDataElements(auth, programDataElements))

        // Delete all report tables associated with programAttributes
        errors.addAll(deleteReportTablesForProgramAttributes(auth, programTrackedEntityAttributes))

        // Delete all eventReports associated with the program
        errors.addAll(deleteEventReportsForProgram(auth, programId))

        // Delete all eventCharts associated with the program
        errors.addAll(deleteEventChartsForProgram(auth, programId))

        return errors
    }
    /**
     * Deletes all reportTables associated with the supplied list of programDataElements.
     * Note that reportTables could have associations with other programs and data sets, and will be deleted regardless.
     *
     * @param auth DHIS 2 credentials
     * @param programDataElements List of programDataElements to delete associated reportTables for
     * @return Array of errors if any
     */
    private def deleteReportTablesForProgramDataElements(def auth, def programDataElements = []) {
        def errors = []

        def reportTablesToDelete = []
        programDataElements?.each { programDataElement ->
            reportTablesToDelete.addAll(reportTableService.findByProgramDataElementId(auth, programDataElement.id, ["id", "name"]))
        }

        //flatten this to only be for unique report table ids
        reportTablesToDelete = reportTablesToDelete?.unique { reportTable ->
            reportTable?.id
        }

        reportTablesToDelete?.each {reportTable ->
            errors.addAll(deleteReportTable(auth, reportTable?.id))
        }

        return errors
    }

    /**
     * Deletes all charts associated with the supplied list of programDataElements.
     * Note that charts could have associations with other programs and data sets, and will be deleted regardless.
     *
     * @param auth DHIS 2 credentials
     * @param programDataElements List of programDataElements to delete associated charts for
     * @return Array of errors if any
     */
    private def deleteChartsForProgramDataElements(def auth, def programDataElements = []) {
        def errors = []

        def chartsToDelete = []
        programDataElements?.each { programDataElement ->
            chartsToDelete.addAll(chartService.findByProgramDataElementId(auth, programDataElement.id, ["id", "name"]))
        }

        //flatten this to only be for unique chart ids
        chartsToDelete = chartsToDelete?.unique { chart ->
            chart?.id
        }

        chartsToDelete?.each {chart ->
            errors.addAll(deleteChart(auth, chart?.id))
        }

        return errors
    }

    /**
     * Deletes all maps associated with the supplied list of programDataElements.
     * Note that maps could have associations with other programs and data sets, and will be deleted regardless.
     *
     * @param auth DHIS 2 credentials
     * @param programDataElements List of programDataElements to delete associated maps for
     * @return Array of errors if any
     */
    private def deleteMapsForProgramDataElements(def auth, def programDataElements = []) {
        def errors = []

        def mapsToDelete = []
        programDataElements?.each { programDataElement ->
            mapsToDelete.addAll(mapService.findByProgramDataElementId(auth, programDataElement.id, ["id", "name"]))
        }

        //flatten this to only be for unique map ids
        mapsToDelete = mapsToDelete?.unique { map ->
            map?.id
        }

        mapsToDelete?.each {map ->
            errors.addAll(deleteMap(auth, map?.id))
        }

        return errors
    }


    /**
     * Deletes all reportTables associated with the supplied list of programAttributes.
     * Note that reportTables could have associations with other programs and data sets, and will be deleted regardless.
     *
     * @param auth DHIS 2 credentials
     * @param programAttributes List of programAttributes to delete associated reportTables for
     * @return Array of errors if any
     */
    private def deleteReportTablesForProgramAttributes(def auth, def programAttributes = []) {
        def errors = []

        def reportTablesToDelete = []
        programAttributes?.each { programAttribute ->
            reportTablesToDelete.addAll(reportTableService.findByProgramAttributeId(auth, programAttribute.id, ["id"]))
        }

        //flatten this to only be for unique report table ids
        reportTablesToDelete = reportTablesToDelete?.unique { reportTable ->
            reportTable?.id
        }

        reportTablesToDelete?.each {reportTable ->
            errors.addAll(deleteReportTable(auth, reportTable?.id))
        }

        return errors
    }


    /**
     * Deletes all charts associated with the supplied list of programAttributes.
     * Note that charts could have associations with other programs and data sets, and will be deleted regardless.
     *
     * @param auth DHIS 2 credentials
     * @param programAttributes List of programAttributes to delete associated charts for
     * @return Array of errors if any
     */
    private def deleteChartsForProgramAttributes(def auth, def programAttributes = []) {
        def errors = []

        def chartsToDelete = []
        programAttributes?.each { programAttribute ->
            chartsToDelete.addAll(chartService.findByProgramAttributeId(auth, programAttribute.id, ["id"]))
        }

        //flatten this to only be for unique chart ids
        chartsToDelete = chartsToDelete?.unique { chart ->
            chart?.id
        }

        chartsToDelete?.each {chart ->
            errors.addAll(deleteChart(auth, chart?.id))
        }

        return errors
    }


    /**
     * Deletes all maps associated with the supplied list of programAttributes.
     * Note that maps could have associations with other programs and data sets, and will be deleted regardless.
     *
     * @param auth DHIS 2 credentials
     * @param programAttributes List of programAttributes to delete associated maps for
     * @return Array of errors if any
     */
    private def deleteMapsForProgramAttributes(def auth, def programAttributes = []) {
        def errors = []

        def mapsToDelete = []
        programAttributes?.each { programAttribute ->
            mapsToDelete.addAll(mapService.findByProgramAttributeId(auth, programAttribute.id, ["id"]))
        }

        //flatten this to only be for unique map ids
        mapsToDelete = mapsToDelete?.unique { map ->
            map?.id
        }

        mapsToDelete?.each {map ->
            errors.addAll(deleteMap(auth, map?.id))
        }

        return errors
    }

    /**
     * Delete all maps associated with the specified program
     *
     * @param auth DHIS 2 credentials
     * @param programId The id of the program to delete Maps for
     * @return Array of errors if any
     */
    private def deleteMapsForProgramId(def auth, def programId) {
        def errors = []

        def mapsToDelete = mapService.findByProgramId(auth, programId, ["id"])

        mapsToDelete?.each {map ->
            errors.addAll(deleteMap(auth, map?.id))
        }

        return errors
    }

    /**
     * Deletes all event reports for the specified program
     *
     * @param auth DHIS 2 credentials
     * @param programId Id of the program to delete event reports for
     * @return Array of errors if any
     */
    private def deleteEventReportsForProgram(def auth, def programId) {
        def errors = []

        def eventReportsToDelete = eventReportService.findByProgramId(auth, programId, ["id"])

        eventReportsToDelete?.each {eventReport ->
            errors.addAll(deleteEventReport(auth, eventReport?.id))
        }

        return errors
    }

    /**
     * Deletes all event charts for the specified program
     *
     * @param auth DHIS 2 credentials
     * @param programId Id of the program to delete event charts for
     * @return Array of errors if any
     */
    private def deleteEventChartsForProgram(def auth, def programId) {
        def errors = []

        def eventChartsToDelete = eventChartService.findByProgramId(auth, programId, ["id"])

        eventChartsToDelete?.each {eventChart ->
            errors.addAll(deleteEventChart(auth, eventChart?.id))
        }

        return errors
    }

    /**
     * Deletes the specified dashboard item
     *
     * @param auth DHIS 2 credentials
     * @param dashboardItemId Id of the dashboard item to delete
     * @return Array of errors if any
     */
    private def deleteDashboardItem(auth, dashboardItemId) {

        def errors = []

        def dashboardItemResult = dashboardItemService.delete(auth, dashboardItemId)
        if (!dashboardItemResult.success) {
            log.error("error deleting dashboardItem ${dashboardItemId}")
            errors.addAll(dashboardItemResult.errors)
        }

        return errors
    }

    /**
     * Deletes the specified report table and any related dashboard items
     *
     * @param auth DHIS 2 credentials
     * @param reportTableId Id of the report table to delete
     * @return Array of errors if any
     */
    private def deleteReportTable(auth, reportTableId) {

        def errors = []

        //First Delete related Dashboard Items
        def dashboardItemsToDelete = dashboardItemService.findByReportTableId(auth, reportTableId, ["id"])
        dashboardItemsToDelete?.each { dashboardItem ->
            errors.addAll(deleteDashboardItem(auth, dashboardItem?.id))
        }

        if (!(errors.size() > 0)) {
            def reportTableResult = reportTableService.delete(auth, reportTableId)
            if (!reportTableResult.success) {
                log.error("error deleting reportTable ${reportTableId}")
                errors.addAll(reportTableResult.errors)
            }
        }

        return errors
    }

    /**
     * Deletes the specified chart and any related dashboard items
     *
     * @param auth DHIS 2 credentials
     * @param chartId Id of the chart to delete
     * @return Array of errors if any
     */
    private def deleteChart(auth, chartId) {

        def errors = []

        //First Delete related Dashboard Items
        def dashboardItemsToDelete = dashboardItemService.findByChartId(auth, chartId, ["id"])
        dashboardItemsToDelete?.each { dashboardItem ->
            errors.addAll(deleteDashboardItem(auth, dashboardItem?.id))
        }

        if (!(errors.size() > 0)) {
            def chartResult = chartService.delete(auth, chartId)
            if (!chartResult.success) {
                log.error("error deleting chart ${chartId}")
                errors.addAll(chartResult.errors)
            }
        }

        return errors
    }

    /**
     * Deletes the specified map, and any related dashboard items
     *
     * @param auth DHIS 2 credentials
     * @param mapId Id of the map to delete
     * @return Array of errors if any
     */
    private def deleteMap(auth, mapId) {

        def errors = []

        //First Delete related Dashboard Items
        def dashboardItemsToDelete = dashboardItemService.findByMapId(auth, mapId, ["id"])
        dashboardItemsToDelete?.each { dashboardItem ->
            errors.addAll(deleteDashboardItem(auth, dashboardItem?.id))
        }

        if (!(errors.size() > 0)) {
            def mapResult = mapService.delete(auth, mapId)
            if (!mapResult.success) {
                log.error("error deleting map ${mapId}")
                errors.addAll(mapResult.errors)
            }
        }
        return errors
    }

    /**
     * Deletes the specified event report
     *
     * @param auth DHIS 2 credentials
     * @param eventReportId The id of the event report to delete
     * @return Array of errors if any
     */
    private def deleteEventReport(auth, eventReportId) {

        def errors = []

        def eventReportResult = eventReportService.delete(auth, eventReportId)
        if (!eventReportResult.success) {
            log.error("error deleting eventReport ${eventReportId}")
            errors.addAll(eventReportResult.errors)
        }

        return errors
    }

    /**
     * Deletes the specified event chart
     *
     * @param auth DHIS 2 credentials
     * @param eventChartId The id of the event chart to delete
     * @return Array of errors if any
     */
    private def deleteEventChart(auth, eventChartId) {

        def errors = []

        def eventChartResult = eventChartService.delete(auth, eventChartId)
        if (!eventChartResult.success) {
            log.error("error deleting eventChart ${eventChartId}")
            errors.addAll(eventChartResult.errors)
        }

        return errors
    }

    /*
     * --------------------------------------
     * End Report Functionality
     * --------------------------------------
     */

    /**
     * Deletes all metadata associated with the specified program stage.
     * This includes:
     * - data elements
     * - indicators containing related data elements
     * - option sets
     * - options
     *
     * @param auth DHIS 2 credentials
     * @param programStageId Id of the program stage to delete metadata for
     * @return Array of errors if any
     */
    public def deleteProgramStageMetadata(auth, programStageId) {

        def errors = []

        def query = [fields: "id,name,programStageDataElements[id,dataElement[id,optionSet[id,name,options[id]]]]"]

        def programStage = programStageService.get(auth, programStageId, query)

        programStage.programStageDataElements.each { programStageDataElement ->
            // programStageDataElements are deleted automatically when underlying data element is deleted
            errors.addAll(deleteDataElementAndRelatedData(auth, programStageDataElement.dataElement))
        }

        if (errors.size() > 0) {
            errors << [code: "survey.program.stage.metadata.not.deleted", args: [programStage.name, programStage.id]]
        }

        return errors
    }

    /**
     * Deletes all supplied programDataElements
     *
     * @param auth DHIS 2 credentials
     * @param programDataElements Array of programDataElements to delete
     * @return Array of errors if any
     */
    private def deleteProgramDataElements (auth, programDataElements) {

        def errors = []

        if (programDataElements?.size() > 0) {

            def metadata = [
                    programDataElements: programDataElements
            ]

            def programDataElementsDeleteResult = metadataService.delete(auth, metadata)

            if (!programDataElementsDeleteResult.success) {
                log.error("error deleting programDataElements")
                errors.addAll(programDataElementsDeleteResult.errors)
            }
        }

        if (errors.size() > 0) {
            errors << [code: "survey.program.data.elements.not.deleted", args: [program.name, program.id]]
        }

        return errors
    }

    /**
     * Deletes the specified programTrackedEntityAttribute, as well as the underlying trackedEntityAttribute and related
     * option sets and options
     *
     * This method is not used because switched to bulk deletion instead
     *
     * @param auth DHIS 2 credentials
     * @param programTrackedEntityAttribute programTrackedEntityAttribute to delete
     * @return Array of errors if any
     */
    private def deleteProgramTrackedEntityAttribute (auth, programTrackedEntityAttribute) {

        def errors = []

        def programTrackedEntityAttributeResult = programTrackedEntityAttributeService.delete(auth, programTrackedEntityAttribute.id)
        if (!programTrackedEntityAttributeResult.success) {
            log.error("error deleting programTrackedEntityAttribute ${programTrackedEntityAttribute.id}")
            errors.addAll(programTrackedEntityAttributeResult.errors)
        }
        if (programTrackedEntityAttribute?.trackedEntityAttribute) {
            errors.addAll(deleteTrackedEntityAttribute (auth, programTrackedEntityAttribute.trackedEntityAttribute))
        } else {
            log.error("No associated Tracked Entity Attribute for ProgramTrackedEntityAttribute ${programTrackedEntityAttribute?.id}")
        }

        return errors
    }

    /**
     * Deletes the specified programTrackedEntityAttributes, as well as the underlying trackedEntityAttributes & related
     * option sets and options
     *
     * @param auth DHIS 2 credentials
     * @param programId Id of the program to delete programTrackedEntityAttributes for
     * @param programName Name of the program to delete programTrackedEntityAttributes for
     * @param programTrackedEntityAttributes list of programTrackedEntityAttributes to delete
     * @return Array of errors if any
     */
    public def deleteProgramTrackedEntityAttributes (auth, programId, programName, programTrackedEntityAttributes) {

        def errors = []

        if (programTrackedEntityAttributes?.size() > 0) {

            // only need to post id to delete, strip out the extra object fields
            def programTrackedEntityAttributesToDelete = []

            programTrackedEntityAttributes.collect { ptea ->
                ptea.id
            }.each { pteaId ->
                programTrackedEntityAttributesToDelete << [id: pteaId]
            }

            // extract the list of trackedEntityAttributes from the programTrackedEntityAttributes
            def trackedEntityAttributesToDelete = programTrackedEntityAttributes.collect { ptea ->
                ptea.trackedEntityAttribute
            }
            // there were some legacy programs with multiple programTrackedEntityAttributes per trackedEntityAttribute,
            // so need to flatten to only unique ones for deletion
            trackedEntityAttributesToDelete = trackedEntityAttributesToDelete.unique { trackedEntityAttribute ->
                trackedEntityAttribute?.id
            }

            // first need to delete the program association with the trackedEntityAttributes in order to delete those after
            errors.addAll(bulkDeleteProgramTrackedEntityAttributes(auth, programTrackedEntityAttributesToDelete))

            if (errors.size() > 0) {
                errors << [code: "survey.program.tracked.entity.attributes.not.deleted", args: [programName, programId]]
            } else {
                // now delete underlying trackedEntityAttributes and associated optionSets / options
                trackedEntityAttributesToDelete.each { trackedEntityAttribute ->
                    errors.addAll(deleteTrackedEntityAttribute(auth, trackedEntityAttribute))
                }
            }
        }

        return errors
    }

    /**
     * Bulk deletes the specified array of programTrackedEntityAttributes
     *
     * @param auth DHIS 2 credentials
     * @param programTrackedEntityAttributes Array of programTrackedEntityAttributes to delete
     * @return Array of errors if any
     */
    public def bulkDeleteProgramTrackedEntityAttributes(def auth, def programTrackedEntityAttributes) {

        def errors = []

        def metadata = [
                programTrackedEntityAttributes: programTrackedEntityAttributes
        ]

        def programTrackedEntityAttributesResult = metadataService.delete(auth, metadata)

        if (!programTrackedEntityAttributesResult.success) {
            log.error("error deleting programTrackedEntityAttributes")
            errors.addAll(programTrackedEntityAttributesResult.errors)
        }

        return errors
    }

    /**
     * Deletes the specified trackedEntityAttribute and related option sets and options
     *
     * @param auth DHIS 2 credentials
     * @param trackedEntityAttribute trackedEntityAttribute to delete
     * @return Array of errors if any
     */
    public def deleteTrackedEntityAttribute (def auth, def trackedEntityAttribute) {

        def errors = []

        // Have to delete the Tracked Entity Attribute first before deleting the option set
        def trackedEntityAttributeResult = trackedEntityAttributeService.delete(auth, trackedEntityAttribute?.id)
        if (!trackedEntityAttributeResult.success) {
            log.error("error deleting trackedEntityAttribute ${trackedEntityAttribute.id}")
            errors.addAll(trackedEntityAttributeResult.errors)
        }

        if (trackedEntityAttribute?.optionSet?.id) {
            // delete the optionSet and related options
            errors.addAll(deleteOptionSet(auth, trackedEntityAttribute.optionSet))
        }

        return errors
    }

    /**
     * Deletes the supplied optionSet and all associated options, all as part of one transaction.
     * If any option or the optionSet itself cannot be deleted, the entire transaction will fail.
     *
     * @param auth DHIS 2 credentials
     * @param optionSet option set to delete
     * @return Array of errors if any
     */
    private def deleteOptionSet (def auth, def optionSet) {

        def errors = []

        def metadata = [
                options: optionSet.options,
                optionSets: [optionSet]
        ]

        def optionSetResult = metadataService.delete(auth, metadata)
        if (!optionSetResult.success) {
            log.error("error deleting option set ${optionSet.id}")
            errors.addAll(optionSetResult.errors)

            errors << [code: "survey.program.optionSet.delete.error", args: [optionSet.name]]

        }

        return errors
    }

    /**
     * Deletes the specified data element and all related data. This includes:
     * - data element
     * - indicators containing specified data element
     * - related option set
     * - related options
     * @param auth DHIS 2 credentials
     * @param dataElement
     * @return Array of errors if any
     */
    public def deleteDataElementAndRelatedData (def auth, def dataElement) {

        def errors = []

        // delete any associated indicators
        errors.addAll(aggregateDataSetDeletionService.deleteIndicatorsForDataElement(auth, dataElement.id))

        // now delete the data element
        errors.addAll(deleteDataElement(auth, dataElement.id))

        // data element may or may not have an option set
        if (dataElement.optionSet) {
            errors.addAll(deleteOptionSet(auth, dataElement.optionSet))
        }

        return errors
    }

    /**
     * Deletes the specified data element
     *
     * @param auth DHIS 2 credentials
     * @param dataElementId Id of the data element to delete
     * @return Array of errors if any
     */
    private def deleteDataElement (auth, dataElementId) {

        def errors = []

        // delete the Data Element itself
        def resultDataElementDelete = dataElementService.delete(auth, dataElementId)
        if (resultDataElementDelete.success) {
            log.debug("successfully deleted data element ${dataElementId}")
        } else {
            log.error("error deleting dataElement ${dataElementId}")
            errors.addAll(resultDataElementDelete.errors)
        }

        return errors
    }

    /**
     * Deletes the specified program indicator
     *
     * @param auth DHIS 2 credentials
     * @param programIndicatorId Id of the program indicator to delete
     * @return Array of errors if any
     */
    private def deleteProgramIndicator (auth, programIndicatorId) {

        def errors = []

        def resultProgramIndicatorDelete = programIndicatorService.delete(auth, programIndicatorId)
        if (resultProgramIndicatorDelete.success) {
            log.debug("successfully deleted programIndicator ${programIndicatorId}")
        } else {
            log.error("error deleting programIndicator ${programIndicatorId}")
            errors.addAll(resultProgramIndicatorDelete.errors)        }


        return errors
    }

    /**
     * Deletes the specified program rule
     *
     * @param auth DHIS 2 credentials
     * @param programRuleId Id of the program rule to delete
     * @return Array of errors if any
     */
    private def deleteProgramRule (auth, programRuleId) {

        def errors = []

        def resultProgramRuleDelete = programRuleService.delete(auth, programRuleId)
        if (resultProgramRuleDelete.success) {
            log.debug("successfully deleted programRule ${programRuleId}")
        } else {
            log.error("error deleting programRule ${programRuleId}")
            errors.addAll(resultProgramRuleDelete.errors)
        }

        return errors
    }

    /**
     * Deletes the specified program rule variable
     *
     * @param auth DHIS 2 credentials
     * @param programRuleVariableId Id of the program rule variable to delete
     * @return Array of errors if any
     */
    private def deleteProgramRuleVariable (auth, programRuleVariableId) {

        def errors=[]

        def resultProgramRuleVariableDelete = programRuleVariableService.delete(auth, programRuleVariableId)
        if (resultProgramRuleVariableDelete.success) {
            log.debug("successfully deleted programRuleVariable ${programRuleVariableId}")
        } else {
            log.error("error deleting programRuleVariable ${programRuleVariableId}")
            errors.addAll(resultProgramRuleVariableDelete.errors)
        }

        return errors
    }

    /*
     *------------------------------------------------------
     * Data Deletion
     *------------------------------------------------------
     */

    /**
     * Deletes all data for the specified program.
     * This includes:
     * - all trackedEntityInstances and related data
     *
     * @param auth DHIS 2 credentials
     * @param program Program to delete data for
     * @return Array of errors if any
     */
    private def deleteProgramData (auth, program) {

        def errors = []

        if (surveyProgramService.programHasData(auth, program.id)) {
            errors.addAll(deleteTrackedEntityInstances(auth, program))
        }
        if (errors.size() > 0) {
            errors << [code: "survey.data.not.deleted", args: [program.name, program.id]]
        }
        return errors
    }

    /**
     * Deletes all data for the specified program stage.
     * This includes:
     * - all events and related data
     *
     * @param auth DHIS 2 credentials
     * @param programId Id of the program related to this program stage
     * @param programStage Program stage to delete data for
     * @return Array of errors if any
     */
    private def deleteProgramStageData (auth, programId, programStage) {

        def errors = []

        def hasData = surveyProgramService.programStageHasData(auth, programStage.id)

        // Is there data? If so, delete associated events
        if (hasData) {
            errors.addAll(deleteEventsForProgramStage (auth, programId, programStage.id))
        }

        if (errors.size() > 0) {
            errors << [code: "survey.program.stage.data.not.deleted", args: [programStage.name, programStage.id]]
        }

        return errors
    }

    /**
     * Deletes all tracked entity instances and related data for the specified program.
     * Because this could be a very large amount of data, it is done in pages of size DATA_PAGING_SIZE until all
     * data is deleted.
     *
     * @param auth DHIS 2 credentials
     * @param program program to delete tracked entity instances for
     * @return Array of errors if any
     */
    private def deleteTrackedEntityInstances (def auth, def program) {

        def errors = []

        program.organisationUnits?.each { ou ->
            errors.addAll(deleteProgramDataForProgramAndOrgUnit (auth, program, ou))
        }

        return errors

    }

    /**
     * Deletes program data for the specified program and org unit
     *
     * @param auth DHIS 2 credentials
     * @param program The program to delete program data for
     * @param orgUnit The org unit to delete program data for
     * @return errors if any
     */
    public def deleteProgramDataForProgramAndOrgUnit (def auth, def program, def orgUnit) {

        def errors = []

        // loop through each org unit to get associated tracked entity instances (ou required parameter for query)
        def query = [
                fields: "trackedEntityInstance",
                program: program.id,
                ou: orgUnit?.id,
                skipPaging: "false",
                pageSize: 0,
                page: 1,
                //this is just for debugging
                totalPages: true
        ]
        def trackedEntityInstances
        // first get the total count and number of pages
        def trackedEntityInstancesCountResult = trackedEntityInstanceService.findByQuery(auth, query)
        def totalCount = trackedEntityInstancesCountResult?.pager?.total

        if (totalCount > 0) {
            def numPages = (int) Math.ceil(totalCount / DATA_PAGING_SIZE)
            // set the page size for query and deletion
            query << [pageSize: DATA_PAGING_SIZE]

            // keep looping through all the pages from the last page first until we get to the first page
            // Each loop iteration the total page count should decrease by 1 as we are deleting a page of data,
            // assuming no errors
            for (int pageNum = numPages; pageNum >= 1; pageNum--) {
                query << [page: pageNum]
                //keep looping through a page at a time and deleting until there are no tracked entity instances to delete
                def trackedEntityInstancesResult = trackedEntityInstanceService.findByQuery(auth, query)
                trackedEntityInstances = trackedEntityInstancesResult?.trackedEntityInstances
                if (trackedEntityInstances?.size() > 0) {
                    errors.addAll(bulkDeleteTrackedEntityInstances(auth, trackedEntityInstances))
                }
            }
        }

        return errors

    }

    /**
     * Bulk deletes all specified tracked entity instances and related data.
     *
     * @param auth DHIS 2 credentials
     * @param trackedEntityInstances List of trackedEntityInstances to delete
     * @return Array of errors if any
     */
    private def bulkDeleteTrackedEntityInstances (def auth, ArrayList<Map<String,String>> trackedEntityInstances) {

        def errors = []

        def trackedEntityInstancesBulkDeleteResult = trackedEntityInstanceService.bulkDelete(auth, trackedEntityInstances)
        if (!trackedEntityInstancesBulkDeleteResult.success) {
            log.error("error deleting tracked entity instances")
            errors.addAll(trackedEntityInstancesBulkDeleteResult.errors)
        }

        return errors
    }


    /**
     * Deletes the specified tracked entity instance
     * Note: Not currently used as we are bulk deleting instead
     *
     * @param auth DHIS 2 credentials
     * @param trackedEntityInstanceId Id of the tracked entity instance to delete
     * @return Array of errors if any
     */
    private def deleteTrackedEntityInstance (def auth, def trackedEntityInstanceId) {

        def errors = []

        def trackedEntityInstanceResult = trackedEntityInstanceService.delete (auth, trackedEntityInstanceId)
        if (!trackedEntityInstanceResult.success) {
            log.error("error deleting tracked entity instance ${trackedEntityInstanceId}")
            errors.addAll(trackedEntityInstanceResult.errors)
        }

        return errors
    }

    /**
     * Deletes all events for the specified program stage.
     * Because this could be a very large amount of data, it is done in pages of size DATA_PAGING_SIZE until all
     * data is deleted.
     *
     * @param auth DHIS 2 credentials
     * @param programId Id of the program the program stage is related to
     * @param programStageId Id of the program stage to delete events for
     * @return Array of errors if any
     */
    private def deleteEventsForProgramStage (def auth, def programId, def programStageId) {

        def errors = []

        def totalCount = getTotalEventCountForProgramStageEvents(auth, programId, programStageId)
        if (totalCount > 0) {
            def numPages = (int) Math.ceil(totalCount / DATA_PAGING_SIZE)

            // keep looping through all the pages from the last page first until we get to the first page
            // Each loop iteration the total page count should decrease by 1 as we are deleting a page of data,
            // assuming no errors
            for (int pageNum = numPages; pageNum >= 1; pageNum--) {
                def events = getEventsForProgramStageByPage(auth, programId, programStageId, DATA_PAGING_SIZE, pageNum)

                if (events?.size() > 0) {
                    errors.addAll(bulkDeleteProgramStageEvents(auth, events, programStageId))
                }
            }
        }

        return errors
    }

    /**
     * Retrieves 1 page of events for the specified program stage with the specified page size
     *
     * @param auth DHIS 2 credentials
     * @param programId Id of the program the program stage is related to
     * @param programStageId Id of the program stage to get event paging info for
     * @param pageSize Size of the page of events to retrieve
     * @param pageNum The page number to retrieve
     * @return list of events found
     */
    public def getEventsForProgramStageByPage(def auth, def programId, def programStageId, def pageSize, def pageNum = 1) {

        def query = [
                fields: "event",
                skipPaging: "false",
                pageSize: pageSize,
                page: pageNum,
                //this is just for debugging
                totalPages: true
        ]

        def resultEvents = eventService.findByProgramAndProgramStageId(auth, programId, programStageId, query)
        def events = resultEvents?.events

        return events
    }

    /**
     * Determines total count of events for the specified program stage
     *
     * @param auth DHIS 2 credentials
     * @param programId Id of the program the program stage is related to
     * @param programStageId Id of the program stage to get event paging info for
     * @return total count of events for specified program stage
     */
    public def getTotalEventCountForProgramStageEvents (def auth, def programId, def programStageId) {
        // setting page size to zero will not return any events but will return the pager info with the total count
        def query = [
                skipPaging: "false",
                totalPages: true,
                pageSize: 0,
                page: 1
        ]

        def resultEvents = eventService.findByProgramAndProgramStageId(auth, programId, programStageId, query)

        def totalCount = resultEvents?.pager?.total

        return totalCount

    }

    /**
     * Bulk deletes the specified events and underlying data for the specified program stage
     *
     * @param auth DHIS 2 credentials
     * @param events Array of events to delete
     * @param programStageId Id of the program stage to delete events for
     * @return Array of errors if any
     */
    public def bulkDeleteProgramStageEvents(auth, events, programStageId) {
        def errors = []

        def resultBulkDeleteEvents = eventService.bulkDelete(auth, events)

        if (!resultBulkDeleteEvents.success) {
            log.error("error bulk deleting events for program stage ${programStageId}")
            errors.addAll(resultBulkDeleteEvents.errors)
        }
        return errors
    }

}
