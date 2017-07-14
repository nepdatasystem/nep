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

package nep.services.aggregate

import com.twopaths.dhis2.services.CategoryComboService
import com.twopaths.dhis2.services.DataSetService
import grails.transaction.NotTransactional
import grails.transaction.Transactional
import nep.batch.aggregate.DataSetCategoryComboDeletionReader
import nep.batch.aggregate.DataSetDataDeletionReader
import nep.batch.aggregate.DataSetDeletionReader
import nep.batch.aggregate.DataSetDataElementDeletionReader
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.context.MessageSource

/**
 * Service to process aggregate data set deletions
 */
@Transactional
class AggregateDataSetDeletionProcessorService {

    // Data Deletion
    DataSetDataDeletionReader dataSetDataDeletionReader

    // Metadata Deletion
    DataSetDataElementDeletionReader dataSetDataElementDeletionReader
    DataSetCategoryComboDeletionReader dataSetCategoryComboDeletionReader

    // Data Set Deletion
    DataSetDeletionReader dataSetDeletionReader

    def dataSetDeletionJob
    def jobLauncher
    def jobService

    MessageSource messageSource

    AggregateDataSetDeletionService aggregateDataSetDeletionService
    DataSetService dataSetService
    CategoryComboService categoryComboService

    // DELETE_TYPE_DATA_SET will delete data, metadata & data set (everything)
    final static String DELETE_TYPE_DATA_SET = "dataSet"
    // DELETE_TYPE_DATA_SET_METADATA will delete data & metadata only (not data set itself)
    final static String DELETE_TYPE_DATA_SET_METADATA = "dataSet-metadata"
    // DELETE_TYPE_DATA_SET_DATA will delete data only (not metadata or data set)
    final static String DELETE_TYPE_DATA_SET_DATA = "dataSet-data"

    /**
     * Sets up and starts the data set deletion job, deleting data, metadata and/or the data set based on the parameters
     * passed in with the jobData
     *
     * @param auth DHIS 2 credentials
     * @param jobData Map of job parameters (dataSetId and deleteType)
     * @return The Map result of the job setup (jobExecution and errors if any)
     */
    @NotTransactional
    def process(def auth, def jobData) {

        def result = [:]
        def messages = []
        def errors = []

        def dataSetId = jobData?.dataSetId
        def deleteType = jobData?.deleteType

        def dataSet = dataSetService.get(auth, dataSetId, ["id", "name", "dataSetElements[id,dataElement[id]]"])

        def dataSetName = dataSet?.name

        log.debug "jobData: ${jobData}"

        if (dataSet) {

            // Cant run job if data or deletion job is already running
            if (!jobService.isRunning("aggregateDataJob", dataSetName) &&
                    !jobService.isRunning("dataSetDeletionJob", dataSetName)) {

                // Set up the Job

                // Job parameters
                def jobParametersBuilder = new JobParametersBuilder()
                DateTimeFormatter outputFormat = DateTimeFormat.forPattern("dd/MM/yyyy h:mm a")

                jobParametersBuilder.addString("dataSetId", dataSetName).addLong("time", System.currentTimeMillis())

                // Add the username/password to the job parameters
                jobParametersBuilder.addString("username", auth.username).addString("password", auth.password)

                // 1. Add steps to delete all the data for the data set
                // This will be done for all deleteTypes
                setUpDataDeletionSteps (auth, dataSet, jobParametersBuilder)

                // 2. Add steps to delete all the metadata for the data set if this was included
                // in the specified deleteType
                if (deleteType == DELETE_TYPE_DATA_SET || deleteType == DELETE_TYPE_DATA_SET_METADATA) {

                    setUpMetadataDeletionSteps (auth, dataSet.id, jobParametersBuilder)
                }

                // 3. Add steps to delete data set itself if this was included in the specified deleteType
                if (deleteType == DELETE_TYPE_DATA_SET) {

                    setUpDataSetDeletionSteps(dataSet.id, jobParametersBuilder)
                }

                // Run the job
                def jobExecution = jobLauncher.run(dataSetDeletionJob, jobParametersBuilder.toJobParameters())
                result << [jobExecution: jobExecution]

            } else { // Job already running...return error message
                if (jobService.isRunning("aggregateDataJob", dataSetName)) {
                    errors << [code: "aggregate.dataJob.alreadyRunning", args: [dataSetName]]
                    errors << [code: "aggregate.dataJob.clickImportStatus"]
                } else if (jobService.isRunning("dataSetDeletionJob", dataSetName)) {
                    errors << [code: "aggregate.deletionJob.alreadyRunning", args: [dataSetName]]
                    errors << [code: "aggregate.deletionJob.clickDeletionStatus"]
                }
                result << [errors: errors]
            }

        } else {
            // no dataSet
            errors << [code: "aggregate.dataset.delete.dataSet.not.found", args: [dataSetId]]
        }

        result << [errors: errors]
        result << [messages: messages]

        return result
    }

    /**
     * Sets up the job steps for deleting all data set data
     *
     * @param auth DHIS 2 credentials
     * @param dataSet The dataSet to delete data for
     * @param jobParametersBuilder The parent jobParametersBuilder object which will be mutated with additional parameters
     */
    private void setUpDataDeletionSteps(def auth, def dataSet, def jobParametersBuilder) {

        // Reset the data ...
        dataSetDataDeletionReader.resetData()

        // data set deletion by pruning data elements
        if (dataSetService.dataSetHasData(auth, dataSet.id)) {
            dataSetDataDeletionReader.setData(dataSet)
            def totalCount = dataSetDataDeletionReader.getTotalCount()
            def dataSetDataDeletionMessage = messageSource.getMessage("aggregate.dataset.deletion.dataset.data.title", null, Locale.default)
            // this is the first step, so add the first item in the array
            jobParametersBuilder.addLong("totalCount[0]", totalCount as Long).addString("fileName[0]", "${dataSetDataDeletionMessage}")
        }
    }

    /**
     * Sets up the job steps for deleting all data set metadata
     *
     * @param auth DHIS 2 credentials
     * @param dataSetId The id of the data set to delete metadata for
     * @param jobParametersBuilder The parent jobParametersBuilder object which will be mutated with additional parameters
     */
    private void setUpMetadataDeletionSteps(def auth, def dataSetId, def jobParametersBuilder) {

        // Deletion of Data Elements and related Indicators
        dataSetDataElementDeletionReader.resetData()

        def dataSet = dataSetService.get(auth, dataSetId,
                ["id", "name", "dataSetElements[id,dataElement[id,categoryCombo[id,categoryOptionCombos[id]]]]"])


        def dataElements = dataSet?.dataSetElements?.collect {it.dataElement}
        def dataSetDataElementsTotalCount = dataElements?.size() ?: 0

        dataSetDataElementDeletionReader.setData(auth, dataSet, dataElements, dataSetDataElementsTotalCount)

        def dataSetDataElementsDeletionMessage = messageSource.getMessage(
                "aggregate.dataset.deletion.dataset.metadata.data.elements.title", null, Locale.default)

        jobParametersBuilder.addLong(
                // this is the second step, so add the second item in the array
                "totalCount[1]", dataSetDataElementsTotalCount as Long)
                .addString("fileName[1]", "${dataSetDataElementsDeletionMessage}")


        // Deletion of Category Combos and related Category Option Combos
        def defaultCategoryComboId = categoryComboService.getDefaultCategoryComboId(auth, true)

        dataSetCategoryComboDeletionReader.resetData()

        def categoryCombosToDelete = dataElements?.collect {
            it.categoryCombo
        }?.findAll { categoryCombo ->
            categoryCombo?.id != defaultCategoryComboId
        }?.unique { categoryCombo ->
            categoryCombo?.id
        }

        def dataSetCategoryCombosTotalCount = categoryCombosToDelete?.size() ?: 0

        dataSetCategoryComboDeletionReader.setData(auth, dataSet, categoryCombosToDelete, dataSetCategoryCombosTotalCount)

        def dataSetCategoryCombosDeletionMessage = messageSource.getMessage(
                "aggregate.dataset.deletion.dataset.metadata.category.combos.title", null, Locale.default)

        jobParametersBuilder.addLong(
                // this is the third step, so add the third item in the array
                "totalCount[2]", dataSetCategoryCombosTotalCount as Long)
                .addString("fileName[2]", "${dataSetCategoryCombosDeletionMessage}")

    }

    /**
     * Sets up the job steps for deleting data set itself
     *
     * @param dataSetId The id of the data set to delete
     * @param jobParametersBuilder The parent jobParametersBuilder object which will be mutated with additional parameters
     */
    private void setUpDataSetDeletionSteps(def dataSetId, def jobParametersBuilder) {

        dataSetDeletionReader.resetData()

        dataSetDeletionReader.setData(dataSetId)
        def dataSetDeletionMessage = messageSource.getMessage(
                "aggregate.dataset.deletion.dataset.title", null, Locale.default)
        // this is the fourth step, so add the fourth item in the array
        jobParametersBuilder.addLong(
                "totalCount[3]", 1 as Long).addString("fileName[3]", "${dataSetDeletionMessage}")

    }

}
