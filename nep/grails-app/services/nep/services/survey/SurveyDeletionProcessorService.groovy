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

import com.twopaths.dhis2.services.ProgramStageService
import grails.transaction.NotTransactional
import grails.transaction.Transactional
import nep.batch.survey.SurveyProgramDataDeletionReader
import nep.batch.survey.SurveyProgramDeletionReader
import nep.batch.survey.SurveyProgramMetadataDeletionReader
import nep.batch.survey.SurveyProgramReportAndSupportingMetadataDeletionReader
import nep.batch.survey.SurveyProgramStageDataDeletionReader
import nep.batch.survey.SurveyProgramStageDeletionReader
import nep.batch.survey.SurveyProgramStageMetadataDeletionReader
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.context.MessageSource

/**
 * Service to process survey deletions
 */
@Transactional
class SurveyDeletionProcessorService {

    // Data Deletion
    SurveyProgramDataDeletionReader surveyProgramDataDeletionReader
    SurveyProgramStageDataDeletionReader surveyProgramStageDataDeletionReader_0
    SurveyProgramStageDataDeletionReader surveyProgramStageDataDeletionReader_1
    SurveyProgramStageDataDeletionReader surveyProgramStageDataDeletionReader_2

    // Metadata Deletion
    SurveyProgramReportAndSupportingMetadataDeletionReader surveyProgramReportAndSupportingMetadataDeletionReader
    SurveyProgramStageMetadataDeletionReader surveyProgramStageMetadataDeletionReader_0
    SurveyProgramStageMetadataDeletionReader surveyProgramStageMetadataDeletionReader_1
    SurveyProgramStageMetadataDeletionReader surveyProgramStageMetadataDeletionReader_2
    SurveyProgramMetadataDeletionReader surveyProgramMetadataDeletionReader

    // Program & Program Stage Deletion
    SurveyProgramStageDeletionReader surveyProgramStageDeletionReader
    SurveyProgramDeletionReader surveyProgramDeletionReader

    def surveyDeletionJob
    def jobLauncher
    def jobService

    MessageSource messageSource

    SurveyProgramService surveyProgramService
    SurveyProgramDeletionService surveyProgramDeletionService
    ProgramStageService programStageService

    // DELETE_TYPE_SURVEY will delete data, metadata & survey (everything)
    final static String DELETE_TYPE_SURVEY = "survey"
    // DELETE_TYPE_SURVEY_METADATA will delete data & metadata only (not survey itself)
    final static String DELETE_TYPE_SURVEY_METADATA = "survey-metadata"
    // DELETE_TYPE_SURVEY_DATA will delete data only (not metadata or survey)
    final static String DELETE_TYPE_SURVEY_DATA = "survey-data"

    /**
     * Sets up and starts the survey deletion job, deleting data, metadata and/or the survey based on the parameters
     * passed in with the jobData
     *
     * @param auth DHIS 2 credentials
     * @param jobData Map of job parameters (programId and deleteType)
     * @return The Map result of the job setup (jobExecution and errors if any)
     */
    @NotTransactional
    def process(def auth, def jobData) {

        def result = [:]
        def messages = []
        def errors = []

        def programId = jobData?.programId
        def deleteType = jobData?.deleteType

        def program = surveyProgramService.getProgram(auth, programId, [
                "id", "name", "programStages[id,name]","organisationUnits[id]"
        ])

        def programName = program?.name

        log.debug "jobData: ${jobData}"

        if (program) {

            // Cant run job if data or metadata job or deletion job is already running
            if (!jobService.isRunning("surveyDataJob", programName) &&
                    !jobService.isRunning("surveyMetadataJob", programName) &&
                    !jobService.isRunning("surveyDeletionJob", programName)) {

                // Set up the Job

                // Job parameters
                def jobParametersBuilder = new JobParametersBuilder()
                DateTimeFormatter outputFormat = DateTimeFormat.forPattern("dd/MM/yyyy h:mm a")

                jobParametersBuilder.addString("dataSetId", programName).addLong("time", System.currentTimeMillis())

                // Add the username/password to the job parameters
                jobParametersBuilder.addString("username", auth.username).addString("password", auth.password)

                // 1. Add steps to delete all the data for the survey and related program stages
                // This will be done for all deleteTypes
                setUpDataDeletionSteps (auth, program, jobParametersBuilder)

                // 2. Add steps to delete all the metadata for the survey & related program stages if this was included
                // in the specified deleteType
                if (deleteType == DELETE_TYPE_SURVEY || deleteType == DELETE_TYPE_SURVEY_METADATA) {

                    setUpMetadataDeletionSteps (auth, program.id, jobParametersBuilder)
                }

                // 3. Add steps to delete survey and program stages themselves if this was included in the
                // specified deleteType
                if (deleteType == DELETE_TYPE_SURVEY) {

                    setUpProgramDeletionSteps(auth, program.id, jobParametersBuilder)
                }

                // Run the job
                def jobExecution = jobLauncher.run(surveyDeletionJob, jobParametersBuilder.toJobParameters())
                result << [jobExecution: jobExecution]

            } else { // Job already running...return error message
                if (jobService.isRunning("surveyDataJob", programName)) {
                    errors << [code: "survey.dataJob.alreadyRunning", args: [programName]]
                    errors << [code: "survey.dataJob.clickImportStatus"]
                } else if (jobService.isRunning("surveyMetadataJob", programId)) {
                    errors << [code: "survey.metadataJob.alreadyRunning", args: [programName]]
                    errors << [code: "survey.metadataJob.clickImportStatus"]
                } else if (jobService.isRunning("surveyDeletionJob", programId)) {
                    errors << [code: "survey.deletionJob.alreadyRunning", args: [programName]]
                    errors << [code: "survey.deletionJob.clickDeletionStatus"]
                }

            }
        } else {
            // no program
            errors << [code: "survey.program.survey.delete.program.not.found", args: [programId]]
        }

        result << [errors: errors]
        result << [messages: messages]

        return result
    }

    /**
     * Sets up the job steps for deleting all program and program stage metadata
     *
     * @param auth DHIS 2 credentials
     * @param program The program to delete metadata for
     * @param jobParametersBuilder The parent jobParametersBuilder object which will be mutated with additional parameters
     */
    private void setUpMetadataDeletionSteps(def auth, def programId, def jobParametersBuilder) {

        surveyProgramReportAndSupportingMetadataDeletionReader.resetData()
        surveyProgramStageMetadataDeletionReader_0.resetData()
        surveyProgramStageMetadataDeletionReader_1.resetData()
        surveyProgramStageMetadataDeletionReader_2.resetData()
        surveyProgramMetadataDeletionReader.resetData()

        def program = surveyProgramService.getProgram(auth, programId, [
                "id", "name", "programStages[id]","programIndicators[id]", "programRules[id]", "programRuleVariables[id]",
                "programTrackedEntityAttributes[id,trackedEntityAttribute[id,optionSet[id,name,options[id]]]]"
        ])

        // Reports, Program Indicator & Program Rule deletion
        //keeping this simple by doing all report deletion in one batch read/write
        def reportDeletionTotalCount = 1
        surveyProgramReportAndSupportingMetadataDeletionReader.setData(auth, program, reportDeletionTotalCount)
        def programReportDeletionMessage = messageSource.getMessage(
                "survey.deletion.program.report.metadata.title", null, Locale.default)
        // this is the fifth step, so add the fourth item in the array
        jobParametersBuilder.addLong(
                "totalCount[4]", reportDeletionTotalCount as Long).addString("fileName[4]", "${programReportDeletionMessage}")

        // Program Stage Metadata Deletion

        if (program.programStages) {

            // ProgramStage 0 metadata deletion
            if (program.programStages?.size >= 1) {

                def programStageId = program.programStages.get(0)?.id

                def query = [fields: "id,name,programStageDataElements[id,dataElement[id,optionSet[id,name,options[id]]]]"]

                def programStage = programStageService.get(auth, programStageId, query)

                def totalCount = programStage?.programStageDataElements?.size() ?: 0

                surveyProgramStageMetadataDeletionReader_0.setData(auth, program, programStage, totalCount)

                def programStageMetadataDeletionMessage = messageSource.getMessage(
                        "survey.deletion.program.stage.metadata.title", [programStage?.name] as Object[], Locale.default)
                // this is the sixth step, so add the sixth item in the array
                jobParametersBuilder.addLong("totalCount[5]", totalCount as Long).addString("fileName[5]", "${programStageMetadataDeletionMessage}")

            }
            // ProgramStage 1 metadata deletion
            if (program.programStages?.size >= 2) {

                def programStageId = program.programStages.get(1)?.id

                def query = [fields: "id,name,programStageDataElements[id,dataElement[id,optionSet[id,name,options[id]]]]"]

                def programStage = programStageService.get(auth, programStageId, query)

                def totalCount = programStage?.programStageDataElements?.size() ?: 0

                surveyProgramStageMetadataDeletionReader_1.setData(auth, program, programStage, totalCount)

                def programStageMetadataDeletionMessage = messageSource.getMessage(
                        "survey.deletion.program.stage.metadata.title", [programStage?.name] as Object[], Locale.default)
                // this is the seventh step, so add the seventh item in the array
                jobParametersBuilder.addLong("totalCount[6]", totalCount as Long).addString("fileName[6]", "${programStageMetadataDeletionMessage}")

            }
            // ProgramStage 2 metadata deletion
            if (program.programStages?.size >= 3) {

                def programStageId = program.programStages.get(2)?.id

                def query = [fields: "id,name,programStageDataElements[id,dataElement[id,optionSet[id,name,options[id]]]]"]

                def programStage = programStageService.get(auth, programStageId, query)

                def totalCount = programStage?.programStageDataElements?.size() ?: 0

                surveyProgramStageMetadataDeletionReader_2.setData(auth, program, programStage, totalCount)

                def programStageMetadataDeletionMessage = messageSource.getMessage(
                        "survey.deletion.program.stage.metadata.title", [programStage?.name] as Object[], Locale.default)
                // this is the eighth step, so add the eighth item in the array
                jobParametersBuilder.addLong("totalCount[7]", totalCount as Long).addString("fileName[7]", "${programStageMetadataDeletionMessage}")

            }

            // Program Metadata Deletion

            def programTrackedEntityAttributes = program.programTrackedEntityAttributes

            // Some tracked entity attributes have more than one associated programTrackedEntityAttributes
            def trackedEntityAttributes = programTrackedEntityAttributes?.collect { ptea ->
                ptea.trackedEntityAttribute
            }?.unique { trackedEntityAttribute ->
                trackedEntityAttribute.id
            }

            def programMetadataTotalCount = trackedEntityAttributes?.size() ?: 0
            surveyProgramMetadataDeletionReader.setData(
                    auth, program, programTrackedEntityAttributes, trackedEntityAttributes, programMetadataTotalCount)

            def programMetadataDeletionMessage = messageSource.getMessage(
                    "survey.deletion.program.metadata.title", null, Locale.default)

            // this is the ninth step, so add the ninth item in the array
            jobParametersBuilder.addLong(
                    "totalCount[8]", programMetadataTotalCount as Long).addString("fileName[8]", "${programMetadataDeletionMessage}")

        }

    }

    /**
     * Sets up the job steps for deleting all program and program stages themselves
     *
     * @param auth DHIS 2 credentials
     * @param program The program to delete
     * @param jobParametersBuilder The parent jobParametersBuilder object which will be mutated with additional parameters
     */
    private void setUpProgramDeletionSteps(def auth, def programId, def jobParametersBuilder) {

        surveyProgramStageDeletionReader.resetData()
        surveyProgramDeletionReader.resetData()

        def program = surveyProgramService.getProgram(auth, programId, [
                "id", "name", "programStages[id]","userRoles[id]"
        ])

        // Program Stage Metadata Deletion
        def programStageDeletionTotalCount = program?.programStages?.size() ?: 0

        if (programStageDeletionTotalCount > 0) {
            surveyProgramStageDeletionReader.setData(
                    auth, program, program.programStages, programStageDeletionTotalCount)
            def programStagesDeletionMessage = messageSource.getMessage(
                    "survey.deletion.program.stages.title", null, Locale.default)
            // this is the tenth step, so add the tenth item in the array
            jobParametersBuilder.addLong(
                    "totalCount[9]", programStageDeletionTotalCount as Long)
                    .addString("fileName[9]", "${programStagesDeletionMessage}")
        }

        // Program Deletion
        surveyProgramDeletionReader.setData(auth, program)
        def programDeletionMessage = messageSource.getMessage(
                "survey.deletion.program.title", null, Locale.default)
        // this is the eleventh step, so add the eleventh item in the array
        jobParametersBuilder.addLong(
                "totalCount[10]", 1 as Long).addString("fileName[10]", "${programDeletionMessage}")

    }

    /**
     * Sets up the job steps for deleting all program and program stage data
     *
     * @param auth DHIS 2 credentials
     * @param program The program to delete data for
     * @param jobParametersBuilder The parent jobParametersBuilder object which will be mutated with additional parameters
     */
    private void setUpDataDeletionSteps(def auth, def program, def jobParametersBuilder) {

        // Program Stage Data Deletion
        // Reset the data for each of the steps...
        surveyProgramStageDataDeletionReader_0.resetData()
        surveyProgramStageDataDeletionReader_1.resetData()
        surveyProgramStageDataDeletionReader_2.resetData()
        surveyProgramDataDeletionReader.resetData()

        def pageSize = surveyProgramDeletionService.DATA_PAGING_SIZE

        // ProgramStage 0 data deletion by batch of [pageSize]
        if (program.programStages?.size >= 1) {
            def programStage = program.programStages.get(0)
            if (surveyProgramService.programStageHasData(auth, programStage.id)) {

                def totalCount = surveyProgramDeletionService.getTotalEventCountForProgramStageEvents(auth, program.id, programStage?.id)
                def numPages = (int) Math.ceil(totalCount / pageSize)

                surveyProgramStageDataDeletionReader_0.setData(auth, program, programStage, totalCount, numPages, pageSize)

                def programStageDataDeletionMessage = messageSource.getMessage(
                        "survey.deletion.program.stage.data.title", [programStage?.name] as Object[], Locale.default)
                // this is the first step, so get the first item in the array
                jobParametersBuilder.addLong("totalCount[0]", numPages as Long).addString("fileName[0]", "${programStageDataDeletionMessage}")
            }
        }

        // ProgramStage 1 data deletion by batch of [pageSize]
        if (program.programStages?.size >= 2) {
            def programStage = program.programStages.get(1)
            if (surveyProgramService.programStageHasData(auth, programStage.id)) {
                def totalCount = surveyProgramDeletionService.getTotalEventCountForProgramStageEvents(auth, program.id, programStage?.id)
                def numPages = (int) Math.ceil(totalCount / surveyProgramDeletionService.DATA_PAGING_SIZE)

                surveyProgramStageDataDeletionReader_1.setData(auth, program, programStage, totalCount, numPages, pageSize)

                def programStageDataDeletionMessage = messageSource.getMessage(
                        "survey.deletion.program.stage.data.title", [programStage?.name] as Object[], Locale.default)
                // this is the second step, so add the second item in the array
                jobParametersBuilder.addLong("totalCount[1]", numPages as Long).addString("fileName[1]", "${programStageDataDeletionMessage}")
            }
        }

        // ProgramStage 2 data deletion by batch of [pageSize]
        if (program.programStages?.size >= 3) {
            def programStage = program.programStages.get(2)
            if (surveyProgramService.programStageHasData(auth, programStage.id)) {
                def totalCount = surveyProgramDeletionService.getTotalEventCountForProgramStageEvents(auth, program.id, programStage?.id)
                def numPages = (int) Math.ceil(totalCount / pageSize)

                surveyProgramStageDataDeletionReader_2.setData(auth, program, programStage, totalCount, numPages, pageSize)

                def programStageDataDeletionMessage = messageSource.getMessage(
                        "survey.deletion.program.stage.data.title", [programStage?.name] as Object[], Locale.default)
                // this is the third step, so add the third item in the array
                jobParametersBuilder.addLong("totalCount[2]", numPages as Long).addString("fileName[2]", "${programStageDataDeletionMessage}")
            }

        }

        // Program data deletion by org unit
        if (surveyProgramService.programHasData(auth, program.id)) {
            surveyProgramDataDeletionReader.setData(program)
            def totalCount = surveyProgramDataDeletionReader.getTotalCount()
            def programDataDeletionMessage = messageSource.getMessage("survey.deletion.program.data.title", null, Locale.default)
            // this is the fourth step, so add the fourth item in the array
            jobParametersBuilder.addLong("totalCount[3]", totalCount as Long).addString("fileName[3]", "${programDataDeletionMessage}")
        }
    }
}
