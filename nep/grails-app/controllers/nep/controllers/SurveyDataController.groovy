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
import groovy.util.logging.Log4j
import nep.services.survey.SurveyDeletionProcessorService
import nep.services.survey.SurveyUploadService
import nep.util.Utils
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.RequestContextUtils

import java.nio.file.Files

/**
 * Controller for Survey Data actions
 */
class SurveyDataController extends NEPController {

    def surveyProgramService
    def surveyProgramStageService
    def surveyProgramDeletionService
    def surveyValidationService
    def propertiesService
    def surveyUploadService
    def fileService

    // DHIS 2 Services
    def programService
    def trackedEntityService
    def organisationUnitLevelService

    // Job stuff
    def jobService
    def surveyDataProcessorService
    SurveyDeletionProcessorService surveyDeletionProcessorService

    def afterInterceptor = { model ->

        if (params.action == "list" || params.action == "delete") {
            // Contains programs and programStages
            def programAndStageData = surveyProgramService.listProgramAndStageData(getAuth())
            model << programAndStageData

            def uploadsData = [:]
            def otherUploadsData = [:]
            def programs = programAndStageData.programs
            programs.each { program ->
                def programId = program.id
                def uploads = surveyUploadService.getUploads(programId)
                uploadsData << uploads

                def otherUploads = surveyUploadService.getOtherUploads(programId)
                otherUploadsData << [(programId): otherUploads.uploads]
            }
            model << [uploadsData: uploadsData, otherUploadsData: otherUploadsData]
        } else {
            model << surveyProgramService.getProgramAndStageData(getAuth(), params.programId)
        }

        def trackedEntities = []
        if (params.programId) {
            trackedEntities << getTrackedEntity(params.programId)
            model << [trackedEntities: trackedEntities]
        }

        def programFields = surveyValidationService.getProgramFields()
        def programStageFields = surveyValidationService.getProgramStageFields()

        model << [programFields: programFields, programStageFields: programStageFields]
        model << [exampleSurveyFilesLink: resource(
                        dir:"assets/survey/${RequestContextUtils.getLocale(request)}",
                        file:g.message(code: 'survey.example.files.filename.help'))]

    }

    /**
     * Renders the Data Upload page
     *
     * @return
     */
    def index() {
        log.debug "params" + params

        def model = [params: params]

        render view: 'create', model: model
    }

    /**
     * Renders the create Survey page
     *
     * @return
     */
    def create() {

        def model = [params: params]
        model << [organisationUnitLevels: organisationUnitLevelService.getLookup(getAuth())]
        model << [trackedEntities: getTrackedEntities()?.trackedEntities ?: []]
        render view: 'create', model: model
    }

    /**
     * Handles the POST of the Create Survey page
     *
     * @param cmd The command object containing the survey creation data for validation and creation
     * @return
     */
    def submit(CreateSurveyCommand cmd) {
        log.debug "submit, params: " + params

        def model = [params: params]
        def errors = []
        def messages = []

        String roleString = propertiesService.getProperties().getProperty('nep.userRoles', null)
        def userRoles = Utils.getRoleNames(roleString)

        cmd.userRoles = userRoles

        if (cmd.validate()) {
            def programName = params.programName.trim().toUpperCase()

            def programData = [
                    programType        : "WITH_REGISTRATION",
                    name               : programName,
                    shortName          : programName.take(50),
                    displayName        : params.programDisplayName,
                    enrollmentDateLabel: g.message(code: 'survey.surveyDate'),
                    incidentDateLabel  : g.message(code: 'survey.surveyDate'),
                    trackedEntity      : [id: params.trackedEntityId],
                    // need to set the default categoryCombo
                    categoryCombo      : [id: surveyProgramService.getDefaultCategoryComboId(getAuth())]
            ]

            def orgUnitLevel = params.orgUnitLevel

            // Create program using supplied data
            def result = surveyProgramService.createProgram(getAuth(), programData, orgUnitLevel, userRoles)

            // If the program has been created (i.e. have lastImported returned)
            if (result.lastImported) {
                params << [programId: result.lastImported]

                params.programStageName_0 = params.programStageName_0?.toUpperCase()
                params.programStageName_1 = params.programStageName_1?.toUpperCase()
                params.programStageName_2 = params.programStageName_2?.toUpperCase()

                log.debug "programStage_0: " + params["programStage_0"]
                log.debug "programStage_1: " + params["programStage_1"]
                log.debug "programStage_2: " + params["programStage_2"]

                // For each program stage...of which there can be zero to three
                def programStageRange = 0..2
                programStageRange.each { index ->
                    if ((params["programStage_${index}"] as List).contains("true")) {
                        surveyProgramStageService.createProgramStage(getAuth(), params, index)
                    }
                }
                messages << g.message(code: 'survey.surveyCreated')

                // Created successfully, forward to metadata controller...?
                flash.messages = messages
                redirect controller: 'surveyMetadata', action: 'upload', params: params, model: [messages: messages]
            } else if (result.errors) {
                log.debug "apiErrors: " + result.errors
                def reason = ""
                result.errors.each { error ->
                    reason += g.message(code: error.code, args: error.args) + " "
                }
                errors << g.message(code: 'survey.surveyNotCreated', args: [reason])
            } else {
                def reason = g.message(code: 'error.text')
                errors << g.message(code: 'survey.surveyNotCreated', args: [reason])
            }
        } else {
            //errors << g.message(code: 'survey.supplyRequiredFields')
            cmd.errors.fieldErrors.each { error ->
                errors << g.message(error: error)
            }
        }

        model << [organisationUnitLevels: organisationUnitLevelService.getLookup(getAuth())]
        model << [trackedEntities: getTrackedEntities().trackedEntities]
        model << [messages: messages, errors: errors]

        render view: 'create', model: model
    }

    /**
     * Renders the list survey page
     *
     * @return
     */
    def list() {
        log.debug "list"

        render view: 'list'
    }

    /**
     * Handles Survey Deletion
     *
     * @return
     */
    def delete () {

        def messages = []
        def errors = []
        def programId = params["programId"]
        def programName = params["programName"]

        // deleteTypes are DELETE_TYPE_SURVEY, DELETE_TYPE_SURVEY_METADATA, DELETE_TYPE_SURVEY_DATA
        // DELETE_TYPE_SURVEY will delete data, metadata & survey (everything)
        // DELETE_TYPE_SURVEY_METADATA will delete data & metadata (not survey itself)
        // DELETE_TYPE_SURVEY_DATA (not metadata or survey)
        def deleteType = params["deleteType"]

        if (!programId) {
            errors << g.message(code: "survey.metadata.survey.delete.programId.missing")
        } else {

            log.debug "delete program ${programId}"

            def jobData = [programId: programId,
                           deleteType: deleteType
            ]

            // Kick off the deletion job
            def result = surveyDeletionProcessorService.process(auth, jobData)

            if (result.messages) {
                result.messages.each { message ->
                    messages << g.message(code: message.code, args: [message.args])
                }
            }
            if (result.errors) {
                result.errors.each { error ->
                    errors << g.message(code: error.code, args: [error.args])
                }
            } else {
                flash.messages = messages
                flash.jobExecution = result.jobExecution
                redirect controller: "surveyData", action: "deletionStatus", params: params
            }

        }

        def model = [
                errors: errors,
                messages: messages
        ]
        render view: 'list', model: model
    }

    /**
     * GET of the Upload Data page
     *
     * @return
     */
    def upload() {
        def model = [params: params]

        render view: 'upload', model: model
    }

    /**
     * Handles the POST of the data upload page.
     * Delegates to batch processing
     *
     * @return
     */
    def uploadSubmit(UploadSurveyDataCommand cmd) {

        log.debug "upload, params: " + params

        def model = [params: params]
        def errors = []
        def messages = []

        def programFields = surveyValidationService.getProgramFields()
        def programStageFields = surveyValidationService.getProgramStageFields()

        model << [programFields: programFields, programStageFields: programStageFields]

        if (cmd.validate()) {
            def uploadedProgramFile = request.getFile('programFile')
            def uploadedProgramStageFile_0 = request.getFile('programStageFile_0')
            def uploadedProgramStageFile_1 = request.getFile('programStageFile_1')
            def uploadedProgramStageFile_2 = request.getFile('programStageFile_2')

            def programFileToBeUploaded = false
            def programStageFilesToBeUploaded = false

            def temp = System.getProperty("java.io.tmpdir")

            def programFile
            def programStageFile_0
            def programStageFile_1
            def programStageFile_2

            // Program file
            if (uploadedProgramFile && !uploadedProgramFile?.empty) {
                programFile = new File(temp, uploadedProgramFile.getOriginalFilename())
                uploadedProgramFile.transferTo(programFile)
                programFileToBeUploaded = true
            }

            // Get programStages
            def programStageIds = [:]

            // Program Stage Files
            if (uploadedProgramStageFile_0 && !uploadedProgramStageFile_0?.empty) {
                programStageFile_0 = new File(temp, uploadedProgramStageFile_0.getOriginalFilename())
                uploadedProgramStageFile_0.transferTo(programStageFile_0)
                programStageIds << ["${params['programStageId_0']}": [file : programStageFile_0, idx : 0]]
                programStageFilesToBeUploaded = true
            }
            if (uploadedProgramStageFile_1 && !uploadedProgramStageFile_1?.empty) {
                programStageFile_1 = new File(temp, uploadedProgramStageFile_1.getOriginalFilename())
                uploadedProgramStageFile_1.transferTo(programStageFile_1)
                programStageIds << ["${params['programStageId_1']}": [file : programStageFile_1, idx : 1]]
                programStageFilesToBeUploaded = true
            }
            if (uploadedProgramStageFile_2 && !uploadedProgramStageFile_2?.empty) {
                programStageFile_2 = new File(temp, uploadedProgramStageFile_2.getOriginalFilename())
                uploadedProgramStageFile_2.transferTo(programStageFile_2)
                programStageIds << ["${params['programStageId_2']}": [file : programStageFile_2, idx : 2]]
                programStageFilesToBeUploaded = true
            }

            // If we have uploaded files
            if (programFileToBeUploaded || programStageFilesToBeUploaded) {

                // Respondent data needs to be loaded before any program stage data can be loaded
                def programDataPreviouslyLoaded = cmd.programId in surveyProgramService.findProgramsWithData(getAuth())

                if (!programFileToBeUploaded && !programDataPreviouslyLoaded) {
                    errors << g.message(code: "survey.data.upload.order")
                } else {

                    if (params.programId) {

                        def jobData = [:]

                        def program = surveyProgramService.getProgram(getAuth(), params.programId)

                        // Program / Respondent
                        if (params.trackedEntityId) {
                            if (programFile) {
                                // Strip off the program name from the programIdField to get the colum name in the data
                                def programIdField = Utils.removePrefix(program.name, params.programIdField)
                                programFields << [program: programIdField]

                                def verify = surveyValidationService.verifyDataFile(programFile, programFields)
                                if (verify.success) {
                                    if (programFile.exists()) {
                                        // Save the uploaded file
                                        surveyUploadService.saveFile(program.id, program.id, program.name, programFile, SurveyUploadService.DATA)

                                        def programData = [file: programFile, programId: params.programId, programName: program.name, trackedEntityId: params.trackedEntityId, fields: programFields]
                                        jobData << [programData: programData]
                                        messages << g.message(code: "survey.programDataUploaded", args: [program.trackedEntity?.name, program.name])
                                    } else {
                                        errors << g.message(code: "file.does.not.exist", args: [programFile])
                                    }
                                } else {
                                    def respondent = g.message(code: "survey.respondent")
                                    verify.errors?.each { error ->
                                        errors << g.message(code: error.code, args: [respondent] << error.args)
                                    }
                                }
                            } else {
                                log.debug "No Program / Respondent file"
                            }
                        }

                        // For each of the Program Stages / Databases
                        programStageIds.each { programStageId, programStageDetails ->

                            def index = programStageDetails["idx"]
                            def file = programStageDetails["file"]

                            // Get the required fields
                            def programIdField = Utils.removePrefix(program.name, params.programIdField)
                            def programStageIdField = params."programStageIdField_${index}"
                            def programStageProgramIdField = params."programStageProgramIdField_${index}"

                            def programStage = surveyProgramStageService.getProgramStage(getAuth(), programStageId)

                            if (programIdField && programStageIdField && programStageProgramIdField) {

                                programStageFields << [program: programIdField]

                                // String off the program stage name from the programStageIdField to get the column name in the data
                                programStageIdField = Utils.removePrefix(programStage.name, programStageIdField)
                                programStageFields << [programStage: programStageIdField]

                                // Strip off the program stage name from the programStageProgramIdField to get the column name in the data
                                programStageProgramIdField = Utils.removePrefix(programStage.name, programStageProgramIdField)
                                programStageFields << [programStageProgram: programStageProgramIdField]

                                def verify = surveyValidationService.verifyDataFile(file, programStageFields)
                                if (verify.success) {
                                    if (file.exists()) {
                                        // Save the uploaded file
                                        surveyUploadService.saveFile(program.id, programStage.id, programStage.name, file, SurveyUploadService.DATA)

                                        def programStageData = [programId: program.id, programName: program.name, programStageId: programStage.id, programStageName: programStage.name, file: file, fields: programStageFields]
                                        jobData << [("programStageData_${index}" as String): programStageData]
                                        messages << g.message(code: "survey.programStageDataUploaded", args: [programStage.name])
                                    } else {
                                        errors << g.message(code: "file.does.not.exist", args: [file])
                                    }
                                } else {
                                    def database = g.message(code: "survey.database")
                                    verify.errors?.each { error ->
                                        errors << g.message(code: error.code, args: [database] << error.args)
                                    }
                                }
                            } else {
                                errors << g.message(code: 'survey.programStage.supplyRequiredFieldsFor', args: [programStage.name])
                            }
                        }
                        log.debug "jobData: " + jobData

                        if (!errors) {
                            // Process only if we have some data....
                            if (jobData) {
                                def result = surveyDataProcessorService.process(getAuth(), jobData)
                                if (result.errors) {
                                    result.errors.each { error ->
                                        errors << g.message(code: error.code, args: [error.args])
                                    }
                                } else {
                                    flash.messages = messages
                                    flash.jobExecution = result.jobExecution
                                    redirect controller: "surveyData", action: "importStatus", params: params
                                }
                            }
                        }
                    }
                }
            } else {
                errors << g.message(code: "file.empty")
            }
        } else {
            errors << g.message(code: 'survey.supplyRequiredFields')
            cmd.errors.fieldErrors.each { error ->
                errors << g.message(error: error)
            }
        }

        model << [messages: messages, errors: errors]

        render view: 'upload', model: model
    }

    /**
     * Renders the Data Import Status page
     *
     * @return
     */
    def importStatus() {

        def model = [
                surveyDataJobExecutions: jobService.getExecutions("surveyDataJob")
        ]

        log.debug "format: " + request.format

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
     * Renders the data import errors page
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
     * Stops a data import job
     *
     * @return
     */
    def stop() {
        jobService.stop(params.long("jobExecutionId"))

        redirect action: "importStatus"
    }

    /**
     * Removes a data import job
     *
     * @return
     */
    def remove() {
        jobService.remove(params.long("jobExecutionId"))

        redirect action: "importStatus"
    }

    /**
     * Renders the Survey Deletion Status page
     *
     * @return
     */
    def deletionStatus() {

        def model = [
                surveyDeletionJobExecutions: jobService.getExecutions("surveyDeletionJob")
        ]

        log.debug "format: " + request.format

        request.withFormat {
            html {
                render view: 'deletionStatus', model: model
            }
            json {
                render model as JSON
            }
        }
    }

    /**
     * Renders the survey deletion errors page
     *
     * @return
     */
    def deletionErrors() {
        def model = [
                params      : params,
                importErrors: jobService.getExecutionErrors(params["jobExecutionId"], params["stepExecutionId"])
        ]

        render view: 'deletionErrors', model: model
    }

    /**
     * Stops a survey deletion job
     *
     * @return
     */
    def deletionStop() {
        jobService.stop(params.long("jobExecutionId"))

        redirect action: "deletionStatus"
    }

    /**
     * Removes a survey deletion job
     *
     * @return
     */
    def deletionRemove() {
        jobService.remove(params.long("jobExecutionId"))

        redirect action: "deletionStatus"
    }

    /**
     * Processes the uploading of files
     *
     * @return
     */
    def uploadFiles() {
        log.debug ">>> uploadFiles"

        def result = []
        def programId = params.programId
        request?.getFileNames()?.each {
            log.debug "filename: " + it
            def multipartFile = request.getFile(it)
            surveyUploadService.saveOtherFile(programId, multipartFile)
            result << [name: it, size: multipartFile.getSize()]
        }

        def resp = [files: result]
        render resp as JSON
    }

    /**
     * Handles the downloading of data
     *
     * @return
     */
    def downloadData() {
        log.debug "downloadData: type: ${params.type} ${params.programId}"
        // If an individual file, it should be a CSV so render with text/csv contentType
        if (params.type == "individual") {
            def uploads = surveyUploadService.getUploads(params.programId)
            def uploadsFolder = uploads.uploadsFolder
            def filename = uploads[(params.programId)].get((params.fileId))
            def file = new File("${uploadsFolder}/${params.programId}", params.fileId)
            response.setHeader("Content-disposition", "attachment; filename=${filename}")
            render(contentType: "text/csv", text: file.text)
        } else if (params.type == "all") { // All files, zip up and send as binary
            def uploads = surveyUploadService.getUploads(params.programId)
            def uploadsFolder = uploads.uploadsFolder
            def csvFiles = []
            uploads[(params.programId)]?.each { key, value ->
                if (key != "uploadsFolder") {
                    def file = new File("${uploadsFolder}/${params.programId}", key)
                    csvFiles << [name: value, bytes: file.bytes]
                }
            }
            def filename = "${params.programName}.zip"
            def zipFile = fileService.buildZipFile(uploadsFolder, csvFiles)
            response.setHeader("Content-disposition", "attachment; filename=${filename}")
            response.setContentType("APPLICATION/OCTET-STREAM")
            response.outputStream << zipFile
            response.outputStream.flush()
        }
    }

    /**
     * Handles the downloading of other data
     *
     * @return
     */
    def downloadOtherData() {
        log.debug "downloadData: type: ${params.type} ${params.programId}"
        // If an individual file, grab the file, get the content type and render
        if (params.type == "individual") {
            def otherUploadsData = surveyUploadService.getOtherUploads(params.programId)
            def otherUploadsFolder = otherUploadsData.otherFolder
            def filename = params.fileId
            def file = new File("${otherUploadsFolder}", filename)
            response.setHeader("Content-disposition", "attachment; filename=${filename}")
            def contentType = Files.probeContentType(file.toPath())
            render(contentType: contentType, file: file.newInputStream())
        } else if (params.type == "all") { // All files, zip up and send as binary
            def otherUploadsData = surveyUploadService.getOtherUploads(params.programId)
            def otherUploads = otherUploadsData?.uploads
            def otherUploadsFolder = otherUploadsData.otherFolder
            def otherFiles = []
            otherUploads?.each { value ->
                def file = new File("${otherUploadsFolder}", value)
                otherFiles << [name: value, bytes: file.bytes]
            }
            def filename = "${params.programName}-other.zip"
            def zipFile = fileService.buildZipFile(otherUploadsFolder, otherFiles)
            response.setHeader("Content-disposition", "attachment; filename=${filename}")
            response.setContentType("APPLICATION/OCTET-STREAM")
            response.outputStream << zipFile
            response.outputStream.flush()
        }
    }

    /**
     * Get trackedEntity for the program
     *
     * @param programId The id of the program to get the tracked entity for
     * @return The tracked entity found if any
     */
    private def getTrackedEntity(def programId) {

        def program = programService.get(getAuth(), programId, [fields: ":all,trackedEntity[id,name]"])

        def trackedEntity = program?.trackedEntity

        return trackedEntity
    }

    /**
     * Get all tracked entities
     *
     * @return All the tracked entities in the system
     */
    private def getTrackedEntities() {

        def trackedEntities = trackedEntityService.findAll(getAuth())

        return trackedEntities
    }

    /**
     * Handles exceptions
     *
     * @param exception The exception to handle
     * @return
     */
    def exception(final Exception exception) {
        StringWriter stack = new StringWriter()
        exception.printStackTrace(new PrintWriter(stack))
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
}

/**
 * The Create Survey Command Object for validation and creation
 */
@Log4j
@grails.validation.Validateable
class CreateSurveyCommand {
    String programName
    String programDisplayName
    String orgUnitLevel
    String userRoles

    Boolean programStage_0
    Boolean programStage_1
    Boolean programStage_2

    String programStageId_0
    String programStageId_1
    String programStageId_2

    String programStageDescription_0
    String programStageDescription_1
    String programStageDescription_2

    static constraints = {
        programName(blank: false, nullable: false)
        programDisplayName(blank: false, nullable: false)
        orgUnitLevel(blank: false, nullable: false)
        userRoles(blank: false, nullable: false)

        programStage_0(blank: true, nullable: true, validator: { val, obj ->
            log.debug "val: " + val + " obj.programStageDescription_0: " + obj.programStageDescription_0
            log.debug "obj.programStageDescription_0: " + obj.programStageDescription_0
            def valid = (!val || obj.programStageDescription_0)
            log.debug "0, valid: " + valid
            if (!valid) {
                return ["survey.programStage.missingRequiredFields", 1]
            }
        })
        programStage_1(blank: true, nullable: true, validator: { val, obj ->
            log.debug "val: " + val + " obj.programStageDescription_1: " + obj.programStageDescription_1
            log.debug "obj.programStageDescription_1: " + obj.programStageDescription_1
            def valid = (!val || obj.programStageDescription_1)
            log.debug "1, valid: " + valid
            if (!valid) {
                return ["survey.programStage.missingRequiredFields", 2]
            }
        })
        programStage_2(blank: true, nullable: true, validator: { val, obj ->
            log.debug "val: " + val + " obj.programStageDescription_2: " + obj.programStageDescription_2
            log.debug "obj.programStageDescription_2: " + obj.programStageDescription_2
            def valid = (!val || obj.programStageDescription_2)
            log.debug "2, valid: " + valid
            if (!valid) {
                return ["survey.programStage.missingRequiredFields", 3]
            }
        })

        // IDs should all be different
        programStageId_0(blank: true, nullable: true, validator: { val, obj ->
            def valid = (!obj.programStage_0 || (val && obj.programStage_0))
            if (!valid) {
                return ["survey.programStage.id.required", 1]
            }
            def allDifferent = (!val || (val != obj.programStageId_1 && val != obj.programStageId_2))
            if (!allDifferent) {
                return ["survey.programStage.ids.duplicated"]
            }
        })
        programStageId_1(blank: true, nullable: true, validator: { val, obj ->
            def valid = (!obj.programStage_1 || (val && obj.programStage_1))
            if (!valid) {
                return ["survey.programStage.id.required", 2]
            }
            def allDifferent = (!val || (val != obj.programStageId_0 && val != obj.programStageId_2))
            if (!allDifferent) {
                return ["survey.programStage.ids.duplicated"]
            }
        })
        programStageId_2(blank: true, nullable: true, validator: { val, obj ->
            def valid = (!obj.programStage_2 || (val && obj.programStage_2))
            if (!valid) {
                return ["survey.programStage.id.required", 3]
            }
            def allDifferent = (!val || (val != obj.programStageId_0 && val != obj.programStageId_1))
            if (!allDifferent) {
                return ["survey.programStage.ids.duplicated"]
            }
        })

        programStageDescription_0(blank: true, nullable: true, validator: { val, obj ->
            def valid = (!obj.programStage_0 || (val && obj.programStage_0))
            if (!valid) {
                return ["survey.programStage.description.required", 1]
            }
        })
        programStageDescription_1(blank: true, nullable: true, validator: { val, obj ->
            def valid = (!obj.programStage_1 || (val && obj.programStage_1))
            if (!valid) {
                return ["survey.programStage.description.required", 2]
            }
        })
        programStageDescription_2(blank: true, nullable: true, validator: { val, obj ->
            def valid = (!obj.programStage_2 || (val && obj.programStage_2))
            if (!valid) {
                return ["survey.programStage.description.required", 3]
            }
        })
    }
}

/**
 * The Upload Survey Data Command object for validation and upload
 */
@grails.validation.Validateable
class UploadSurveyDataCommand {
    String programId
    String trackedEntityId
    String trackedEntityName
    String programIdField

    String programStageId_0
    String programStageId_1
    String programStageId_2

    String programStageName_0
    String programStageName_1
    String programStageName_2

    String programStageIdField_0
    String programStageIdField_1
    String programStageIdField_2

    String programStageProgramIdField_0
    String programStageProgramIdField_1
    String programStageProgramIdField_2

    MultipartFile programStageFile_0
    MultipartFile programStageFile_1
    MultipartFile programStageFile_2

    static constraints = {
        programId(nullable: false)
        trackedEntityId(nullable: false)
        trackedEntityName(nullable: true)
        // nullable is false, but putting custom validator on in order to send tracked entity name to validation error message
        programIdField(nullable: true, validator: { val, obj ->
            def valid = (obj.programIdField)
            if (!valid) {
                return ["nep.controllers.UploadSurveyDataCommand.programIdField.nullable.error", obj.trackedEntityName]
            }
        })

        // PROGRAM STAGE 0
        programStageId_0(nullable: true, validator: { val, obj ->
            def valid = (
                    (!obj.programStageIdField_0 && !obj.programStageProgramIdField_0 && !obj.programStageFile_0?.getSize()) ||
                            (obj.programStageIdField_0 && obj.programStageProgramIdField_0 && obj.programStageFile_0?.getSize())
            )
            if (!valid) {
                return ["survey.programIdProgramIdStageAndFile.required", obj.trackedEntityName, obj.programStageName_0]
            }
        })

        // PROGRAM STAGE 1
        programStageId_1(nullable: true, validator: { val, obj ->
            def valid = (
                    (!obj.programStageIdField_1 && !obj.programStageProgramIdField_1 && !obj.programStageFile_1?.getSize()) ||
                            (obj.programStageIdField_1 && obj.programStageProgramIdField_1 && obj.programStageFile_1?.getSize())
            )
            if (!valid) {
                return ["survey.programIdProgramIdStageAndFile.required", obj.trackedEntityName, obj.programStageName_1]
            }
        })

        // PROGRAM STAGE 2
        programStageId_2(nullable: true, validator: { val, obj ->
            def valid = (
                    (!obj.programStageIdField_2 && !obj.programStageProgramIdField_2 && !obj.programStageFile_2?.getSize()) ||
                            (obj.programStageIdField_2 && obj.programStageProgramIdField_2 && obj.programStageFile_2?.getSize())
            )
            if (!valid) {
                return ["survey.programIdProgramIdStageAndFile.required", obj.trackedEntityName, obj.programStageName_2]
            }
        })

        programStageName_0(nullable: true)
        programStageName_1(nullable: true)
        programStageName_2(nullable: true)

        programStageIdField_0(nullable: true)
        programStageIdField_1(nullable: true)
        programStageIdField_2(nullable: true)

        programStageProgramIdField_0(nullable: true)
        programStageProgramIdField_1(nullable: true)
        programStageProgramIdField_2(nullable: true)

        programStageFile_0(nullable: true)
        programStageFile_1(nullable: true)
        programStageFile_2(nullable: true)
    }
}
