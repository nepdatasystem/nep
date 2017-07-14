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

package nep.batch.aggregate

import groovy.json.JsonOutput
import groovy.util.logging.Log4j
import nep.batch.JobExecutionError
import nep.services.aggregate.AggregateDataService
import com.twopaths.dhis2.services.ResourceTableService

import org.springframework.batch.core.StepExecution
import org.springframework.batch.item.ItemWriter
import org.springframework.context.MessageSource

/**
 * Spring Batch writer of Aggregate Data for import
 */
@Log4j
class AggregateDataWriter implements ItemWriter<Map<String, Object>> {

    StepExecution stepExecution

    AggregateDataService aggregateDataService
    ResourceTableService resourceTableService

    /**
     * Writes the batch of items (Aggregate Data)
     *
     * @param items items to write
     * @throws Exception
     */
    @Override
    public void write(List<? extends Map<String, Object>> items) throws Exception {

        def username = stepExecution.jobExecution.jobParameters.getString("username")
        def password = stepExecution.jobExecution.jobParameters.getString("password")
        def auth = [username: username, password: password]

        items.each { row ->
            def data = row.data
            def additionalData = row.additionalData

            log.debug "write, data: " + data
            try {
                // Send to DHIS2...
                def errors = aggregateDataService.processDataRow(auth, data, stepExecution.writeCount, additionalData.dataSet, additionalData.csvHeadings)

                if (errors) {
                    errors.each { error ->
                        createJobExecutionError(error?.code, error?.args)
                    }
                }
            } catch (Exception e) {
                createJobExecutionError("error.text", e.getMessage())
            }
        }

        // Get the processed/read count plus the total count
        def writeCount = stepExecution.writeCount as Long
        // this is the first step, so get the first item in the array
        def totalCount =  stepExecution.jobExecution.jobParameters.getLong("totalCount[0]" as String)
        log.debug "writeCount/totalCount: " + writeCount + " / " + totalCount

        // If we're at the last row, need to run the analytics...
        if (writeCount == (totalCount - 1)) {
            def analyticsTablesResponse = resourceTableService.generateAnalyticsTables(auth)

            log.debug( "Response from generateAnalyticsTables: ${analyticsTablesResponse}")

            if (!analyticsTablesResponse?.success == true) {
                log.error("Unable to generate analytics tables. Please check logs")
                createJobExecutionError("importStatus.analytics.error")
            }

        }
    }

    /**
     * Creates and saves a job execution error
     *
     * @param code The message bundle code of the job execution error
     * @param args The message bundle args of the job execution error
     * @return
     */
    private JobExecutionError createJobExecutionError(def code, def args=null) {

        def jsonArgs = args ? JsonOutput.toJson(args) : null
        def error = new JobExecutionError(
                jobExecutionId: stepExecution.jobExecutionId,
                stepExecutionId: stepExecution.id,
                lineNumber: stepExecution.readCount + 1,
                code: code,
                args: jsonArgs
        )
        error.save()

        if (error.hasErrors()) {
            log.error "Could not save JobExecutionError, errors: " + error.errors
        }
    }
}
