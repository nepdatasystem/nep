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
import nep.services.aggregate.AggregateUploadService
import nep.util.Utils
import org.springframework.web.servlet.support.RequestContextUtils

import java.nio.file.Files

/**
 * Controller for Aggregate Data actions
 */
class AggregateDataController extends NEPController {

    def dataSetService
    def aggregateDataService
    def organisationUnitLevelService
    def organisationUnitService
    def userRoleService
    def aggregateDataValidationService
    def aggregateUploadService
    def fileService

    // Job stuff
    def jobService
    def aggregateDataProcessorService

    /**
     * GET of the Upload Data page
     *
     * @return
     */
    def upload() {
        def model = [:]
        def dataSets = dataSetService.findAllWithReuploadInfo(getAuth())
        def dataSetsWithData = dataSets.findAll { it.hasData == true }
        def dataSetIdsWithData = dataSetsWithData?.id
        def dataSetsWithMetadata = dataSets.findAll { it.hasMetadata == true }
        def dataSetIdsWithMetadata = dataSetsWithMetadata?.id

        model << [
                dataSets                : dataSets,
                aggregateDataCSVHeadings: aggregateDataService.dataColumnHeadings.values().join(", "),
                dataSetIdsWithData      : dataSetIdsWithData,
                dataSetIdsWithMetadata  : dataSetIdsWithMetadata,
                exampleDataSetFilesLink: resource(
                        dir:"assets/aggregateData/${RequestContextUtils.getLocale(request)}",
                        file:g.message(code: 'aggregate.dataset.example.files.filename.help'))
        ]

        render view: 'upload', model: model
    }

    /**
     * POSTS the upload page and processes the data upload
     *
     * @param cmd The command object containing the data to validate and upload
     * @return
     */
    def postUpload(UploadAggregateDataCommand cmd) {

        log.debug "uploading Data"

        log.debug "upload, params: " + params

        def model = [:]
        def errors = []
        def messages = []
        def result = [:]

        if (cmd.validate()) {

            def uploadedDataFile = request.getFile('dataFile')
            if (uploadedDataFile.empty) {
                errors << g.message(code: "aggregate.data.file.empty")
            } else {
                // verify file has proper headings
                def verify = aggregateDataValidationService.verifyFile(uploadedDataFile, aggregateDataService.dataColumnHeadings.values())
                if (!verify.success) {
                    errors << g.message(code: "aggregate.data.file.missing.fields", args: [verify.missingFields.join(', ')])
                } else {
                    // Save the uploaded file
                    aggregateUploadService.saveFile(params.dataSetId, uploadedDataFile, AggregateUploadService.DATA)

                    result = aggregateDataProcessorService.process(getAuth(), uploadedDataFile, params["dataSetId"])

                    if (result?.errors) {
                        result.errors?.each { error ->
                            errors << g.message(code: error.code, args: error.args)
                        }
                    }
                    messages << g.message(code: "aggregate.data.uploaded")
                }
            }

        } else {
            errors << g.message(code: 'aggregate.data.supplyRequiredFields')
        }
        if (errors) {
            def dataSets = dataSetService.findAllWithReuploadInfo(getAuth())
            def dataSetsWithData = dataSets.findAll { it.hasData == true }
            def dataSetIdsWithData = dataSetsWithData?.id
            def dataSetsWithMetadata = dataSets.findAll { it.hasMetadata == true }
            def dataSetIdsWithMetadata = dataSetsWithMetadata?.id
            model << [
                    dataSetId               : params["dataSetId"],
                    dataSets                : dataSets,
                    messages                : messages,
                    errors                  : errors,
                    aggregateDataCSVHeadings: aggregateDataService.dataColumnHeadings.values().join(", "),
                    dataSetIdsWithData      : dataSetIdsWithData,
                    dataSetIdsWithMetadata  : dataSetIdsWithMetadata,
                    exampleDataSetFilesLink: resource(
                            dir:"assets/aggregateData/${RequestContextUtils.getLocale(request)}",
                            file:g.message(code: 'aggregate.dataset.example.files.filename.help'))
            ]

            render view: 'upload', model: model
        } else {
            flash.messages = messages
            flash.jobExecutionId = result.jobExecutionId
            redirect controller: "aggregateData", action: "importStatus", params: params
        }
    }

    /**
     * GET of the Create Data Set page
     *
     * @return
     */
    def create() {
        def model = [:]

        model << [
                organisationUnitLevels: organisationUnitLevelService.getLookup(getAuth()),
                openFuturePeriodsMap  : dataSetService.openFuturePeriods,
                frequencies           : dataSetService.frequencies,
                openFuturePeriods     : "true",
                frequency             : "Yearly"
        ]


        render view: 'create', model: model
    }

    /**
     * POST of the Create Data Set page
     *
     * @param cmd The Command object containing the data to validate and use for dataset creation
     * @return
     */
    def postCreate(CreateDataSetCommand cmd) {

        log.debug "creating DataSet"

        log.debug "upload, params: " + params

        def model = [:]
        def errors = []
        def messages = []

        def result

        if (cmd.validate()) {

            def dataSetName = params['datasetName']

            def dataSet = [
                    name             : dataSetName,
                    shortName        : params['shortName'],
                    periodType       : params['frequency'],
                    openFuturePeriods: params['openFuturePeriods'],
                    categoryCombo    : [id: aggregateDataService.getDefaultCategoryComboId(getAuth())]
            ]

            log.debug("level: " + params['orgUnitLevel'])
            def orgUnitIds = organisationUnitService.findByLevel(getAuth(), params['orgUnitLevel'])
            dataSet << [organisationUnits: orgUnitIds.collect { orgUnitId -> [id: orgUnitId] }]

            result = dataSetService.create(getAuth(), dataSet)

            if (result.success) {

                messages << g.message(code: "dataset.created", args: [dataSetName, result.lastImported])

                // Assign role to dataset
                String roleString = propertiesService.getProperties().getProperty('nep.userRoles', null)
                def roles = Utils.getRoleNames(roleString)

                def addedRoles = []
                def notFoundRoles = []
                roles.each { roleName ->
                    def role = userRoleService.findByRole(getAuth(), roleName)
                    if (role) {
                        userRoleService.assignDataSetToUserRole(getAuth(), role, result.lastImported)
                        addedRoles << roleName
                    } else {
                        notFoundRoles << roleName
                    }
                }
                if (addedRoles.size() > 0) {
                    messages << g.message(code: "dataset.roles.added", args: [addedRoles.toString()])
                }

                if (notFoundRoles.size() > 0) {
                    errors << g.message(code: "dataset.roles.not.found", args: [notFoundRoles.toString()])
                }
            } else {
                log.error "Error Creating dataSet: ${dataSetName}"
                errors << g.message(code: "dataset.not.created", args: [dataSetName])
                if (result.errors) {
                    result.errors.each { error ->
                        errors << g.message(code: error.code, args: error.args)
                    }
                }
            }
        } else {
            errors << g.message(code: 'dataset.supplyRequiredFields')
        }

        if (errors) {
            model << [
                    organisationUnitLevels: organisationUnitLevelService.getLookup(getAuth()),
                    openFuturePeriodsMap  : dataSetService.openFuturePeriods,
                    frequencies           : dataSetService.frequencies,
                    messages              : messages,
                    errors                : errors,
                    datasetName           : params['datasetName'],
                    shortName             : params['shortName'],
                    frequency             : params['frequency'],
                    openFuturePeriods     : params['openFuturePeriods'],
                    orgUnitLevel          : params['orgUnitLevel']
            ]
            render view: 'create', model: model

        } else {
            // If there are no errors, redirect to the next controller (metadata upload)
            flash.messages = messages
            params << [dataSetId: result.lastImported]
            redirect controller: "aggregateMetadata", action: "index", params: params

        }
    }

    /**
     * Renders the Data Import Status page
     *
     * @return
     */
    def importStatus() {

        def model = [
                aggregateDataJobExecutions: jobService.getExecutions("aggregateDataJob")
        ]
        println "format: " + request.format
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
     * Processes the uploading of files
     *
     * @return
     */
    def uploadFiles() {
        log.debug ">>> uploadFiles"

        def result = []
        def dataSetId = params.dataSetId
        request?.getFileNames()?.each {
            log.debug "filename: " + it
            def multipartFile = request.getFile(it)
            aggregateUploadService.saveOtherFile(dataSetId, multipartFile)
            result << [name: it, size: multipartFile.getSize()]
        }

        def resp = [files: result]
        render resp as JSON
    }

    /**
     * Renders the list data set page
     *
     * @return
     */
    def list() {
        def model = [:]
        def dataSets = dataSetService.findAllWithReuploadInfo(getAuth())
        dataSets.each { dataSet ->
            dataSet.created = Utils.getDateTimeFromData(dataSet.created)
            dataSet.lastUpdated = Utils.getDateTimeFromData(dataSet.lastUpdated)
        }
        model << [dataSets: dataSets]

        def uploadsData = [:]
        def otherUploadsData = [:]
        dataSets.each { dataSet ->
            def dataSetId = dataSet.id
            def uploads = aggregateUploadService.getUploads(dataSetId)
            uploadsData << uploads

            def otherUploads = aggregateUploadService.getOtherUploads(dataSetId)
            otherUploadsData << [(dataSetId): otherUploads.uploads]
        }
        model << [uploadsData: uploadsData, otherUploadsData: otherUploadsData]

        render view: 'list', model: model
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

    /**
     * Handles the downloading of data
     *
     * @return
     */
    def downloadData() {
        log.debug "downloadData: type: ${params.type} ${params.dataSetId}"
        // If an individual file, it should be a CSV so render with text/csv contentType
        if (params.type == "individual") {
            def uploads = aggregateUploadService.getUploads(params.dataSetId)
            def uploadsFolder = uploads.uploadsFolder
            def filename = uploads[(params.dataSetId)].get((params.fileId))
            def file = new File("${uploadsFolder}/${params.dataSetId}", params.fileId)
            response.setHeader("Content-disposition", "attachment; filename=${filename}")
            render(contentType: "text/csv", text: file.text)
        } else if (params.type == "all") { // All files, zip up and send as binary
            def uploads = aggregateUploadService.getUploads(params.dataSetId)
            def uploadsFolder = uploads.uploadsFolder
            def csvFiles = []
            uploads[(params.dataSetId)]?.each { key, value ->
                if (key != "uploadsFolder") {
                    def file = new File("${uploadsFolder}/${params.dataSetId}", key)
                    csvFiles << [name: value, bytes: file.bytes]
                }
            }
            def filename = "${params.dataSetName}.zip"
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
        log.debug "downloadData: type: ${params.type} ${params.dataSetId}"
        // If an individual file, grab the file, get the content type and render
        if (params.type == "individual") {
            def otherUploadsData = aggregateUploadService.getOtherUploads(params.dataSetId)
            def otherUploadsFolder = otherUploadsData.otherFolder
            def filename = params.fileId
            def file = new File("${otherUploadsFolder}", filename)
            response.setHeader("Content-disposition", "attachment; filename=${filename}")
            def contentType = Files.probeContentType(file.toPath())
            render(contentType: contentType, file: file.newInputStream())
        } else if (params.type == "all") { // All files, zip up and send as binary
            def otherUploadsData = aggregateUploadService.getOtherUploads(params.dataSetId)
            def otherUploads = otherUploadsData?.uploads
            def otherUploadsFolder = otherUploadsData.otherFolder
            def otherFiles = []
            otherUploads?.each { value ->
                def file = new File("${otherUploadsFolder}", value)
                otherFiles << [name: value, bytes: file.bytes]
            }
            def filename = "${params.dataSetName}-other.zip"
            def zipFile = fileService.buildZipFile(otherUploadsFolder, otherFiles)
            response.setHeader("Content-disposition", "attachment; filename=${filename}")
            response.setContentType("APPLICATION/OCTET-STREAM")
            response.outputStream << zipFile
            response.outputStream.flush()
        }
    }

}

/**
 * The Create DataSet command object to be used for validation and creation
 */
@grails.validation.Validateable
class CreateDataSetCommand {
    String datasetName
    String shortName
    String frequency
    String openFuturePeriods
    String orgUnitLevel

    static constraints = {
        datasetName(blank: false, maxSize: 230)
        shortName(blank: false, maxSize: 50)
        frequency(blank: false)
        openFuturePeriods(blank: false)
        orgUnitLevel(blank: false)
    }
}

/**
 * The UploadData Command object to be used for validation and uploading
 */
@grails.validation.Validateable
class UploadAggregateDataCommand {
    String dataSetId

    static constraints = {
        dataSetId(blank: false)
    }
}
