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

import grails.transaction.NotTransactional
import grails.transaction.Transactional

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.springframework.batch.core.JobParametersBuilder

/**
 * Service to process aggregate data from csv file for import into DHIS 2
 */
@Transactional
class AggregateDataProcessorService {

    def aggregateDataReader

    def aggregateDataJob
    def jobLauncher
    def jobService

    def propertiesService
    def dataSetService

    /**
     * Processes the supplied data file for import into DHIS 2 associated with the supplied Data Set id.
     * Kicks off a batch process.
     *
     * @param auth DHIS 2 Credentials
     * @param file The file to process (upload/import)
     * @param dataSetId The id of the data set to associate the data to
     * @return The Result of the processing (map with errors if any, and the jobExecutionId from the batch process)
     */
    @NotTransactional
    def process(def auth, def file, def dataSetId) {

        def result = [:]

        def csvHeadings = getDataColumnHeadings()

        def reader = new InputStreamReader(file.inputStream).toCsvMapReader()

        def dataSet = dataSetService.get(auth, dataSetId)

        def dataSetName = dataSet?.name

        def errors = []

        // Only allow one job of this type to run concurrently
        if (!jobService.isRunning("aggregateDataJob", dataSetName)) {

            // Job parameters
            def jobParametersBuilder = new JobParametersBuilder()
            DateTimeFormatter outputFormat = DateTimeFormat.forPattern("dd/MM/yyyy h:mm a")
            String dateTime = new DateTime().toString(outputFormat)

            // The dataSetId for the job exectuion is the name
            jobParametersBuilder.addString("dataSetId", dataSetName).addLong("time", System.currentTimeMillis())

            // Reset the data for the aggregate data
            aggregateDataReader.resetData()

            // If we hve data...
            if (reader) {
                // ...set the data
                aggregateDataReader.setData(reader, dataSet, csvHeadings)

                def totalCount = aggregateDataReader.getTotalCount()
                jobParametersBuilder.addLong("totalCount[0]", totalCount as Long).addString("fileName[0]", file.getOriginalFilename())
            }

            // Add the username/password to the job parameters
            jobParametersBuilder.addString("username", auth.username).addString("password", auth.password)

            // Run the job
            def jobExecution = jobLauncher.run(aggregateDataJob, jobParametersBuilder.toJobParameters())
            result << [jobExecution: jobExecution]
        } else { // Job already running...return error message
            errors << [code: "aggregate.dataJob.alreadyRunning", args: [dataSetName]]
            errors << [code: "aggregate.dataJob.clickImportStatus"]
            result << [errors: errors]
        }

        return result
    }

    /**
     * Retrieves the configured column headings for the data import file
     * @return The configured column headings for the data import file
     */
    def getDataColumnHeadings() {

                return [
                        DATE_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.date"),
                        DATA_ELEMENT_VARIABLE_NAME_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.dataElement.name"),
                        DATA_ELEMENT_VARIABLE_UID_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.dataElement.uid"),
                        PERIOD_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.period"),
                        ORG_UNIT_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.orgUnit"),
                        CATEGORY_OPTION_COMBO_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.categoryOptionCombo"),
                        DATA_VALUE_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.dataValue")
                ]
            }

}
