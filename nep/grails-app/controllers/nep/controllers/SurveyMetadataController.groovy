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

package nep.controllers

import grails.converters.JSON
import nep.services.survey.SurveyUploadService
import org.springframework.web.servlet.support.RequestContextUtils

/**
 * Controller for Survey Metadata actions
 */
class SurveyMetadataController extends NEPController {

    def surveyProgramMetadataService
    def surveyProgramStageMetadataService
    def surveyProgramService
    def surveyProgramStageService

    // DHIS2 Services
    def surveyValidationService
    def surveyUploadService

    // Job Stuff
    def jobService
    def surveyMetadataProcessorService

    def afterInterceptor = { model ->

        model << surveyProgramService.getProgramAndStageData(getAuth(), params.programId)

        def trackedEntities = []
        if (params.programId) {
            def trackedEntity = surveyProgramService.getTrackedEntity(getAuth(), params.programId, [fields: "trackedEntity[id,name]"])
            if (trackedEntity) {
                trackedEntities << trackedEntity
            }
        }
        model << [trackedEntities: trackedEntities]

        def programFields = surveyValidationService.getProgramFields()
        def programStageFields = surveyValidationService.getProgramStageFields()
        def programColumnHeadings = surveyValidationService.getProgramColumnHeadings().values().join(", ")

        model << [
                programFields: programFields,
                programStageFields: programStageFields,
                programColumnHeadings: programColumnHeadings,
                exampleSurveyFilesLink: resource(
                        dir:"assets/survey/${RequestContextUtils.getLocale(request)}",
                        file:g.message(code: 'survey.example.files.filename.help'))
        ]
    }

    /**
     * GET of the Upload Metadata page
     *
     * @return
     */
    def upload() {

        def model = [params: params]

        render view: 'upload', model: model
    }

    /**
     * Handles the POST of the upload metadata page
     *
     * @param cmd The command object containing the metadata for validation and uploading
     * @return
     */
    def uploadSubmit(UploadMetadataCommand cmd) {

        log.debug "upload"

        // get the program and program stage
        log.debug "upload, params: " + params

        def model = [params: params]
        def messages = []
        def errors = []

        def programFields = surveyValidationService.getProgramFields()

        def programStageFields = surveyValidationService.getProgramStageFields()

        model << [programFields: programFields, programStageFields: programStageFields]

        if (cmd.validate()) {
            def uploadedProgramFile = request.getFile('programFile')
            def uploadedProgramStageFile_0 = request.getFile('programStageFile_0')
            def uploadedProgramStageFile_1 = request.getFile('programStageFile_1')
            def uploadedProgramStageFile_2 = request.getFile('programStageFile_2')

            def uploadedFiles = false
            def temp = System.getProperty("java.io.tmpdir")

            def programFile
            def programStageFile_0
            def programStageFile_1
            def programStageFile_2

            // Program file
            if (uploadedProgramFile && !uploadedProgramFile?.empty) {
                programFile = new File(temp, uploadedProgramFile.getOriginalFilename())
                uploadedProgramFile.transferTo(programFile)
                uploadedFiles = true
            }

            // Get programStages
            def programStageIds = [:]

            // Program Stage Files
            if (uploadedProgramStageFile_0 && !uploadedProgramStageFile_0?.empty) {
                programStageFile_0 = new File(temp, uploadedProgramStageFile_0.getOriginalFilename())
                uploadedProgramStageFile_0.transferTo(programStageFile_0)
                programStageIds << ["${params['programStageId_0']}": [file : programStageFile_0, idx : 0]]
                uploadedFiles = true
            }
            if (uploadedProgramStageFile_1 && !uploadedProgramStageFile_1?.empty) {
                programStageFile_1 = new File(temp, uploadedProgramStageFile_1.getOriginalFilename())
                uploadedProgramStageFile_1.transferTo(programStageFile_1)
                programStageIds << ["${params['programStageId_1']}": [file : programStageFile_1, idx : 1]]
                uploadedFiles = true
            }
            if (uploadedProgramStageFile_2 && !uploadedProgramStageFile_2?.empty) {
                programStageFile_2 = new File(temp, uploadedProgramStageFile_2.getOriginalFilename())
                uploadedProgramStageFile_2.transferTo(programStageFile_2)
                programStageIds << ["${params['programStageId_2']}": [file : programStageFile_2, idx : 2]]
                uploadedFiles = true
            }

            // If we have uploaded files...
            if (uploadedFiles) {
                if (params.programId) {

                    def jobData = [:]

                    def program = surveyProgramService.getProgram(getAuth(), params.programId)

                    // Program
                    if (params.trackedEntityId) {
                        if (programFile) {
                            // Not sure what validation we're going to be able to do here....
                            def verify = surveyValidationService.verifyMetadataFile(programFile, programFields)
                            if (verify.success) {

                                // Save the uploaded file
                                surveyUploadService.saveFile(program.id, program.id, program.name, programFile, SurveyUploadService.METADATA)

                                def programMetadata = [file: programFile, program: program, trackedEntityId: params.trackedEntityId, fields: programFields]
                                jobData << [programMetadata: programMetadata]
                                messages << g.message(code: "survey.programMetadataUploaded", args: [program.trackedEntity?.name, program.name])
                            } else {
                                verify.errors?.each { error ->
                                    errors << g.message(code: error.code, args: [g.message(code: "survey.program"), error.args])
                                }
                            }
                        }
                    }

                    // For each program stage
                    programStageIds.each { programStageId, programStageDetails ->

                        def index = programStageDetails["idx"]
                        def file = programStageDetails["file"]

                        if (file) {
                            log.debug "programStageId: " + programStageId
                            if (programStageId) {
                                def programStage = surveyProgramStageService.getProgramStage(getAuth(), programStageId)
                                log.debug "programStage.name: " + programStage?.name

                                def verify = surveyValidationService.verifyMetadataFile(file, programStageFields)
                                if (verify.success) {
                                    // Save the uploaded file
                                    surveyUploadService.saveFile(program.id, programStage.id, programStage.name, file, SurveyUploadService.METADATA)

                                    def programStageMetadata = [program: program, programStage: programStage, file: file, fields: programStageFields]
                                    jobData << [("programStageMetadata_${index}" as String): programStageMetadata]
                                    messages << g.message(code: "survey.programStageMetadataUploaded", args: [programStage.name])
                                } else {
                                    verify.errors?.each { error ->
                                        errors << g.message(code: error.code, args: [g.message(code: "survey.programStage.name", args: [programStage.name]), error.args])
                                    }
                                }
                            }
                        }
                    }

                    // If we have no errors, we can forward onto the metadata import status controller
                    if (!errors) {
                        // Process only if we have job data
                        if (jobData) {
                            def result = surveyMetadataProcessorService.process(getAuth(), jobData)
                            if (result.errors) {
                                result.errors.each { error ->
                                    errors << g.message(code: error.code, args: [error.args])
                                }
                            } else {
                                flash.messages = messages
                                flash.jobExecution = result.jobExecution
                                redirect controller: "surveyMetadata", action: "importStatus", params: params
                            }
                        }
                    }
                }
            } else {
                errors << g.message(code: "file.empty")
            }
        } else {
            errors << g.message(code: 'survey.supplyRequiredFields')
        }

        model << [messages: messages, errors: errors]

        render view: 'upload', model: model
    }

    /**
     * Handles exceptions
     *
     * @param exception The exception to handle
     * @return
     */
    def exception(final Exception exception) {
        StringWriter stack = new StringWriter();
        exception.printStackTrace(new PrintWriter(stack));
        log.error "Exception: " + exception + ":" + stack
        forward action: 'error', model: [exception: stack]
    }

    /**
     * Renders the error page
     * @return
     */
    def error() {
        render view: 'error'
    }

    /**
     * Renders the metadata Import Status page
     *
     * @return
     */
    def importStatus() {

        def model = [
                surveyDataJobExecutions: jobService.getExecutions("surveyMetadataJob")
        ]

        log.debug  "format: " + request.format

        request.withFormat {
            html {
                render view: 'importStatus', model: model
            }
            json {
                render model as JSON
            }
        }
    }

    /**
     * Renders the metadata import errors page
     *
     * @return
     */
    def importErrors() {
        def model = [
                params      : params,
                importErrors: jobService.getExecutionErrors(params["jobExecutionId"], params["stepExecutionId"])
        ]

        render view: 'importErrors', model: model
    }

    /**
     * Stops a metadata import job
     *
     * @return
     */
    def stop() {
        jobService.stop(params.long("jobExecutionId"))

        redirect action: "importStatus"
    }

    /**
     * Removes a metadata import job
     *
     * @return
     */
    def remove() {
        jobService.remove(params.long("jobExecutionId"))

        redirect action: "importStatus"
    }
}

/**
 * The Upload Metadata Command object for validation and uploading
 */
@grails.validation.Validateable
class UploadMetadataCommand {
    String programId
    String trackedEntityId

    static constraints = {
        programId(blank: false)
        trackedEntityId(blank: false)
    }
}

