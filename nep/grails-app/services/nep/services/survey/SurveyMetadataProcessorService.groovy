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
import nep.batch.survey.SurveyProgramMetadataReader
import nep.batch.survey.SurveyProgramStageMetadataReader
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.springframework.batch.core.JobParametersBuilder

/**
 * Service to batch process the import of survey metadata
 */
@Transactional
class SurveyMetadataProcessorService {

    SurveyProgramMetadataReader surveyProgramMetadataReader
    SurveyProgramStageMetadataReader surveyProgramStageMetadataReader_0
    SurveyProgramStageMetadataReader surveyProgramStageMetadataReader_1
    SurveyProgramStageMetadataReader surveyProgramStageMetadataReader_2

    def surveyMetadataJob
    def jobLauncher
    def jobService

    /**
     * Processes the survey metadata import by batch
     *
     * @param auth DHIS 2 Credentials
     * @param jobData The survey metadata for the batch import job
     * @return The Result of the processing (map with errors if any, and the jobExecutionId from the batch process)
     */
    @NotTransactional
    def process(def auth, def jobData) {

        def result = [:]

        // Get the dataSetId from the program or program stage
        def dataSetId = jobData?.programMetadata?.program?.name ?:
                jobData.programStageMetadata_0?.program?.name ?:
                jobData.programStageMetadata_1?.program?.name ?:
                jobData.programStageMetadata_2?.program?.name

        // Cant run job if data or metadata job is already running
        if (!jobService.isRunning("surveyDataJob", dataSetId) && !jobService.isRunning("surveyMetadataJob", dataSetId)) {

            // Job parameters
            def jobParametersBuilder = new JobParametersBuilder()
            DateTimeFormatter outputFormat = DateTimeFormat.forPattern("dd/MM/yyyy h:mm a")
            String dateTime = new DateTime().toString(outputFormat)
            jobParametersBuilder.addString("dataSetId", dataSetId).addLong("time", System.currentTimeMillis())

            // Reset the data for each of the steps...
            surveyProgramMetadataReader.resetData()
            surveyProgramStageMetadataReader_0.resetData()
            surveyProgramStageMetadataReader_1.resetData()
            surveyProgramStageMetadataReader_2.resetData()

            // Set the data for each of the steps...
            // Program / TrackedEntity Data (Households)
            if (jobData.programMetadata) {
                def programMetadata = jobData.programMetadata

                // Set the survey data and other required fields...
                surveyProgramMetadataReader.setData(programMetadata.file, programMetadata.program, programMetadata.trackedEntityId, programMetadata.fields)
                def totalCount = surveyProgramMetadataReader.getTotalCount()
                jobParametersBuilder.addLong("totalCount[0]", totalCount as Long).addString("fileName[0]", programMetadata.file.name)
            }

            // ProgramStage metadata 0
            if (jobData.programStageMetadata_0) {
                def programStageMetadata = jobData.programStageMetadata_0
                surveyProgramStageMetadataReader_0.setData(programStageMetadata.file, programStageMetadata.program, programStageMetadata.programStage, programStageMetadata.fields)
                def totalCount = surveyProgramStageMetadataReader_0.getTotalCount()
                jobParametersBuilder.addLong("totalCount[1]", totalCount as Long).addString("fileName[1]", programStageMetadata.file.name)
            }

            // ProgramStage metadata 1
            if (jobData.programStageMetadata_1) {
                def programStageMetadata = jobData.programStageMetadata_1
                surveyProgramStageMetadataReader_1.setData(programStageMetadata.file, programStageMetadata.program, programStageMetadata.programStage, programStageMetadata.fields)
                def totalCount = surveyProgramStageMetadataReader_1.getTotalCount()
                jobParametersBuilder.addLong("totalCount[2]", totalCount as Long).addString("fileName[2]", programStageMetadata.file.name)
            }

            // ProgramStage metadata 2
            if (jobData.programStageMetadata_2) {
                def programStageMetadata = jobData.programStageMetadata_2
                surveyProgramStageMetadataReader_2.setData(programStageMetadata.file, programStageMetadata.program, programStageMetadata.programStage, programStageMetadata.fields)
                def totalCount = surveyProgramStageMetadataReader_2.getTotalCount()
                jobParametersBuilder.addLong("totalCount[3]", totalCount as Long).addString("fileName[3]", programStageMetadata.file.name)
            }

            // Add the username/password to the job parameters
            jobParametersBuilder.addString("username", auth.username).addString("password", auth.password)

            // Run the job
            def jobExecution = jobLauncher.run(surveyMetadataJob, jobParametersBuilder.toJobParameters())
            result << [jobExecution: jobExecution]
        } else { // Job already running...return error message
            def errors = []
            if (jobService.isRunning("surveyDataJob", dataSetId)) {
                errors << [code: "survey.dataJob.alreadyRunning", args: [dataSetId]]
            } else if (jobService.isRunning("surveyMetadataJob", dataSetId)) {
                errors << [code: "survey.metadataJob.alreadyRunning", args: [dataSetId]]
            }
            errors << [code: "survey.metadataJob.clickImportStatus"]
            result << [errors: errors]
        }

        return result
    }
}
