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

import grails.transaction.NotTransactional
import grails.transaction.Transactional
import nep.batch.survey.SurveyProgramDataReader
import nep.batch.survey.SurveyProgramStageDataReader

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.springframework.batch.core.JobParametersBuilder

/**
 * Service to batch process the import of survey data
 */
@Transactional
class SurveyDataProcessorService {

    SurveyProgramDataReader surveyProgramDataReader
    SurveyProgramStageDataReader surveyProgramStageDataReader_0
    SurveyProgramStageDataReader surveyProgramStageDataReader_1
    SurveyProgramStageDataReader surveyProgramStageDataReader_2

    def surveyDataJob
    def jobLauncher
    def jobService

    def trackedEntityAttributeService
    def dataElementService
    def organisationUnitService
    def optionSetService

    /**
     * Processes the survey data import by batch
     *
     * @param auth DHIS 2 Credentials
     * @param jobData The survey data for the batch import job
     * @return The Result of the processing (map with errors if any, and the jobExecutionId from the batch process)
     */
    @NotTransactional
    def process(def auth, def jobData) {

        def result = [:]

        // Get the dataSetId from the program or program stage
        def dataSetId = jobData?.programData?.programName ?:
                        jobData.programStageData_0?.programName ?:
                        jobData.programStageData_1?.programName ?:
                        jobData.programStageData_2?.programName

        log.debug "jobData: ${jobData}"

        // Only allow one job of this type to run concurrently
        if (!jobService.isRunning("surveyDataJob", dataSetId) &&
                !jobService.isRunning("surveyMetadataJob", dataSetId) &&
                !jobService.isRunning("surveyDeletionJob", dataSetId)) {

            // Get the tracked entity attribute lookup...this is a map of codes -> ids
            def trackedEntityAttributeLookup = trackedEntityAttributeService.getLookup(auth)
            //log.debug "trackedEntityAttributeLookup: " + trackedEntityAttributeLookup

            def organisationUnitLookup = organisationUnitService.getLookup(auth)
            //log.debug "organisationUnitLookup: " + organisationUnitLookup

            def optionSetLookup = optionSetService.getLookup(auth)
            //log.debug "optionSetLookup: " + optionSetLookup

            def dataElementLookup = dataElementService.getLookup(auth)
            //log.debug "dataElementLookup: " + dataElementLookup

            // Job parameters
            def jobParametersBuilder = new JobParametersBuilder()
            DateTimeFormatter outputFormat = DateTimeFormat.forPattern("dd/MM/yyyy h:mm a")
            String dateTime = new DateTime().toString(outputFormat)
            jobParametersBuilder.addString("dataSetId", dataSetId).addLong("time", System.currentTimeMillis())

            // Reset the data for each of the steps...
            surveyProgramDataReader.resetData()
            surveyProgramStageDataReader_0.resetData()
            surveyProgramStageDataReader_1.resetData()
            surveyProgramStageDataReader_2.resetData()

            // Set the data for each of the steps...
            // Program / TrackedEntity Data (Households)
            if (jobData.programData) {
                def programData = jobData.programData
                // Set the survey data and other required fields...
                surveyProgramDataReader.setData(programData.file, programData.programId, programData.programName, programData.trackedEntityId, programData.fields, organisationUnitLookup, trackedEntityAttributeLookup, optionSetLookup)
                def totalCount = surveyProgramDataReader.getTotalCount()
                jobParametersBuilder.addLong("totalCount[0]", totalCount as Long).addString("fileName[0]", programData.file.name)
            }

            // ProgramStage data 0
            if (jobData.programStageData_0) {
                def programStageData = jobData.programStageData_0
                surveyProgramStageDataReader_0.setData(programStageData.file, programStageData.programId, programStageData.programName, programStageData.programStageId, programStageData.programStageName, programStageData.fields, organisationUnitLookup, dataElementLookup, optionSetLookup)
                def totalCount = surveyProgramStageDataReader_0.getTotalCount()
                jobParametersBuilder.addLong("totalCount[1]", totalCount as Long).addString("fileName[1]", programStageData.file.name)
            }

            // ProgramStage data 1
            if (jobData.programStageData_1) {
                def programStageData = jobData.programStageData_1
                surveyProgramStageDataReader_1.setData(programStageData.file, programStageData.programId, programStageData.programName, programStageData.programStageId, programStageData.programStageName, programStageData.fields, organisationUnitLookup, dataElementLookup, optionSetLookup)
                def totalCount = surveyProgramStageDataReader_1.getTotalCount()
                jobParametersBuilder.addLong("totalCount[2]", totalCount as Long).addString("fileName[2]", programStageData.file.name)
            }

            // ProgramStage data 2
            if (jobData.programStageData_2) {
                def programStageData = jobData.programStageData_2
                surveyProgramStageDataReader_2.setData(programStageData.file, programStageData.programId, programStageData.programName, programStageData.programStageId, programStageData.programStageName, programStageData.fields, organisationUnitLookup, dataElementLookup, optionSetLookup)
                def totalCount = surveyProgramStageDataReader_2.getTotalCount()
                jobParametersBuilder.addLong("totalCount[3]", totalCount as Long).addString("fileName[3]", programStageData.file.name)
            }

            // Add the username/password to the job parameters
            jobParametersBuilder.addString("username", auth.username).addString("password", auth.password)

            // Run the job
            def jobExecution = jobLauncher.run(surveyDataJob, jobParametersBuilder.toJobParameters())
            result << [jobExecution: jobExecution]
        } else { // Job already running...return error message
            def errors = []

            if (jobService.isRunning("surveyDataJob", dataSetId)) {
                errors << [code: "survey.dataJob.alreadyRunning", args: [dataSetId]]
                errors << [code: "survey.dataJob.clickImportStatus"]
            } else if (jobService.isRunning("surveyMetadataJob", dataSetId)) {
                errors << [code: "survey.metadataJob.alreadyRunning", args: [dataSetId]]
                errors << [code: "survey.metadataJob.clickImportStatus"]
            } else if (jobService.isRunning("surveyDeletionJob", dataSetId)) {
                errors << [code: "survey.deletionJob.alreadyRunning", args: [dataSetId]]
                errors << [code: "survey.deletionJob.clickDeletionStatus"]
            }

            result << [errors: errors]
        }

        return result
    }
}
