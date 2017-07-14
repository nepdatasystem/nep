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

import groovy.util.logging.Log4j
import nep.batch.AbstractBatchWriter
import nep.batch.NEPBatchProcessException
import nep.services.aggregate.AggregateDataSetDeletionService
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.item.ItemWriter

/**
 * Spring Batch writer of DataSet data for deletion
 */
@Log4j
class DataSetDataDeletionWriter extends AbstractBatchWriter implements ItemWriter<Map<String, Object>> {

    AggregateDataSetDeletionService aggregateDataSetDeletionService

    /**
     * Deletes the batch of items (DataSet data)
     *
     * @param items items to delete
     * @throws Exception
     */
    @Override
    public void write(List<? extends Map<String, Object>> items) throws Exception {

        def hasErrors = false

        def username = stepExecution.jobExecution.jobParameters.getString("username")
        def password = stepExecution.jobExecution.jobParameters.getString("password")
        def auth = [username: username, password: password]

        items.each { row ->

            def errors = []

            def dataElement = row.dataElement
            def dataSet = row.dataSet

            log.debug "write, dataElement: " + dataElement

            // Delete data set data for this row
            try {
                //
                errors.addAll(aggregateDataSetDeletionService.pruneDataElement(auth, dataElement?.id))

            } catch (Exception e) {
                errors << [code: "error.text", args: [e.getMessage()]]
            }

            if (errors) {
                errors.each { error ->
                    createJobExecutionError(error?.code, error?.args)
                }
            }
        }
        // Get the processed/read count plus the total count
        def writeCount = stepExecution.writeCount as Long
        // this is the first step, so get the first item in the array
        def totalCount =  stepExecution.jobExecution.jobParameters.getLong("totalCount[0]" as String)

        log.debug "writeCount/totalCount: " + writeCount + " / " + totalCount

        if (hasErrors)  {
            stepExecution.addFailureException(new NEPBatchProcessException(
                    "Data Set Deletion Job Failed due to errors in data set data deletion"))
            stepExecution.exitStatus = ExitStatus.FAILED
        }
    }

}
