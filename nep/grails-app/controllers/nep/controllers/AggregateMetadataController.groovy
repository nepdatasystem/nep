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

import nep.services.aggregate.AggregateUploadService
import org.springframework.web.servlet.support.RequestContextUtils

/**
 * Controller for Aggregate Metadata actions
 */
class AggregateMetadataController extends NEPController {

    def aggregateMetadataService
    def dataSetService
    def aggregateDataValidationService
    def aggregateUploadService

    /**
     * Renders the metadata upload page
     *
     * @return
     */
    def index() {
        def model = [:]
        def dataSets = dataSetService.findAllWithReuploadInfo(getAuth())
        def dataSetsWithMetadata = dataSets.findAll { it.hasMetadata == true }
        def dataSetIdsWithMetadata = dataSetsWithMetadata?.id

        model << [dataSets : dataSets,
                  aggregateMetadataRequiredCSVHeadings : aggregateMetadataService.requiredMetadataColumnHeadings.values().join(", "),
                  aggregateMetadataOptionalCSVHeadings: aggregateMetadataService.optionalMetadataColumnHeadings.values().join(", "),
                  dataSetIdsWithMetadata : dataSetIdsWithMetadata,
                  exampleDataSetFilesLink: resource(
                          dir:"assets/aggregateData/${RequestContextUtils.getLocale(request)}",
                          file:g.message(code: 'aggregate.dataset.example.files.filename.help'))
        ]

        render view: 'index', model: model

    }

    /**
     * Renders the Disaggregations page
     * @return
     */
    def disaggregations() {
        def model = [:]
        def dataSets = dataSetService.findAllWithReuploadInfo(getAuth())
        def dataSetsWithDisaggregations = dataSets.findAll { it.hasDisaggregations == true }
        def dataSetIdsWithDisaggregations = dataSetsWithDisaggregations?.id
        def dataSetsWithMetadata = dataSets.findAll { it.hasMetadata == true }
        def dataSetIdsWithMetadata = dataSetsWithMetadata?.id
        model << [
                dataSets : dataSets,
                aggregateDisaggregationsCSVHeadings : aggregateMetadataService.disaggregationColumnHeadings.values().join(", "),
                dataSetIdsWithDisaggregations : dataSetIdsWithDisaggregations,
                dataSetIdsWithMetadata : dataSetIdsWithMetadata,
                exampleDataSetFilesLink: resource(
                        dir:"assets/aggregateData/${RequestContextUtils.getLocale(request)}",
                        file:g.message(code: 'aggregate.dataset.example.files.filename.help'))
        ]

        render view: 'disaggregations', model: model
    }

    /**
     * Handles the POST of the disaggregations file upload
     *
     * @param cmd The command object containing the disaggregations data for validation and uploading
     * @return
     */
    def uploadDisaggregations(UploadAggregateDisaggregationsCommand cmd) {
        log.debug "upload disaggregations"

        log.debug "upload, params: " + params

        def model = [:]
        def errors = []
        def messages = []

        if (cmd.validate()) {
            def uploadedDisaggregationsFile = request.getFile('disaggregationsFile')
            if (uploadedDisaggregationsFile.empty) {
                errors << g.message(code:"aggregate.disaggregations.file.empty")
            } else {
                def verify = aggregateDataValidationService.verifyFile(uploadedDisaggregationsFile, aggregateMetadataService.disaggregationColumnHeadings.values())
                if (!verify.success) {
                    errors << g.message(code: "aggregate.disaggregations.file.missing.fields", args: [verify.missingFields.join(', ')])
                } else {
                    // Save the uploaded file
                    aggregateUploadService.saveFile(params.dataSetId, uploadedDisaggregationsFile, AggregateUploadService.DISAGGREGATIONS)

                    def fileUploadErrors = aggregateMetadataService.processDissagregationsFile(getAuth(), uploadedDisaggregationsFile.inputStream, params["dataSetId"])
                    if (fileUploadErrors?.size() > 0) {
                        fileUploadErrors.each { error ->
                            errors << g.message(code: error.code, args: error.args)
                        }
                    }
                    messages << g.message(code: "aggregate.disaggregations.uploaded")
                }
            }

        } else {
            errors << g.message(code: 'aggregate.disaggregations.supplyRequiredFields')
        }

        if (errors) {
            // If there are errors, show them on the same page
            def dataSets = dataSetService.findAllWithReuploadInfo(getAuth())
            def dataSetsWithDisaggregations = dataSets.findAll { it.hasDisaggregations == true }
            def dataSetIdsWithDisaggregations = dataSetsWithDisaggregations?.id
            def dataSetsWithMetadata = dataSets.findAll { it.hasMetadata == true }
            def dataSetIdsWithMetadata = dataSetsWithMetadata?.id
            model << [
                    dataSetId : params["dataSetId"],
                    dataSets : dataSets,
                    messages: messages,
                    errors: errors,
                    aggregateDisaggregationsCSVHeadings : aggregateMetadataService.disaggregationColumnHeadings.values().join(", "),
                    dataSetIdsWithDisaggregations : dataSetIdsWithDisaggregations,
                    dataSetIdsWithMetadata : dataSetIdsWithMetadata,
                    exampleDataSetFilesLink: resource(
                            dir:"assets/aggregateData/${RequestContextUtils.getLocale(request)}",
                            file:g.message(code: 'aggregate.dataset.example.files.filename.help'))
            ]
            render view: 'disaggregations', model: model
        } else {
            // If there are no errors, redirect to the next controller (metadata upload)
            flash.messages = messages
            redirect controller: "aggregateData", action: "upload", params: params
        }
    }

    /**
     * Handles the POST of the metadata upload
     *
     * @param cmd The command object containing the Metadata Upload data for verification and processing
     * @return
     */
    def uploadMetadata(UploadAggregateMetaDataCommand cmd) {
        log.debug "upload metadata"

        // get the program and program stage
        log.debug "upload, params: " + params

        def model = [:]
        def errors = []
        def messages = []

        if (cmd.validate()) {
            def uploadedMetadataFile = request.getFile('metadataFile')
            if (uploadedMetadataFile.empty) {
                errors << g.message(code:"aggregate.metadata.file.empty")
            } else {
                // verify file has proper headings
                def verify = aggregateDataValidationService.verifyFile(uploadedMetadataFile, aggregateMetadataService.requiredMetadataColumnHeadings.values())
                if (!verify.success) {
                    errors << g.message(code: "aggregate.metadata.file.missing.fields", args: [verify.missingFields.join(', ')])
                } else {
                    // Save the uploaded file
                    aggregateUploadService.saveFile(params.dataSetId, uploadedMetadataFile, AggregateUploadService.METADATA)

                    def fileUploadErrors = aggregateMetadataService.processMetadataFile(getAuth(), uploadedMetadataFile.inputStream, params["dataSetId"])
                    if (fileUploadErrors?.size() > 0) {
                        fileUploadErrors.each { error ->
                            errors << g.message(code: error.code, args: error.args)
                        }
                    }
                    messages << g.message(code: "aggregate.metadata.uploaded")
                }
            }
        } else {
            errors << g.message(code: 'aggregate.metadata.supplyRequiredFields')
        }

        if (errors) {
            // If there are errors, show them on the same page
            def dataSets = dataSetService.findAllWithReuploadInfo(getAuth())
            def dataSetsWithMetadata = dataSets.findAll { it.hasMetadata == true }
            def dataSetIdsWithMetadata = dataSetsWithMetadata?.id
            model << [
                    dataSetId : params["dataSetId"],
                    dataSets : dataSets,
                    messages: messages,
                    errors: errors,
                    aggregateMetadataRequiredCSVHeadings : aggregateMetadataService.requiredMetadataColumnHeadings.values().join(", "),
                    aggregateMetadataOptionalCSVHeadings : aggregateMetadataService.optionalMetadataColumnHeadings.values().join(", "),
                    dataSetIdsWithMetadata : dataSetIdsWithMetadata,
                    exampleDataSetFilesLink: resource(
                            dir:"assets/aggregateData/${RequestContextUtils.getLocale(request)}",
                            file:g.message(code: 'aggregate.dataset.example.files.filename.help'))
            ]
            render view: 'index', model: model
        } else {
            // If there are no errors, redirect to the next controller (metadata upload)
            flash.messages = messages
            redirect controller: "aggregateMetadata", action: "disaggregations", params: params
        }
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
 * The Upload Aggregate Metadata command object to be used for validation and uploading
 */
@grails.validation.Validateable
class UploadAggregateMetaDataCommand {
    String dataSetId

    static constraints = {
        dataSetId(blank: false)
    }
}

/**
 * The Upload Aggregate Disaggregations command object to be used for validation and uploading
 */
@grails.validation.Validateable
class UploadAggregateDisaggregationsCommand {
    String dataSetId

    static constraints = {
        dataSetId(blank: false)
    }
}
