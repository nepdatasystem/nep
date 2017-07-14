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

package nep.batch.survey

import groovy.util.logging.Log4j
import nep.batch.AbstractBatchWriter
import nep.batch.NEPBatchProcessException
import nep.services.survey.SurveyProgramDeletionService
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.item.ItemWriter

/**
 * Spring Batch writer of Survey Program Stage Metadata for deletion
 */
@Log4j
public class SurveyProgramStageMetadataDeletionWriter extends AbstractBatchWriter implements ItemWriter<Map<String, Object>> {

    SurveyProgramDeletionService surveyProgramDeletionService

    Integer programStageNumber

    /**
     * Deletes the batch of items (Survey Program Stage Metadata)
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

            // write errors row by row
            def errors = []

            def dataElement = row.dataElement

            log.debug "write, programStageNumber: ${programStageNumber}, dataElement to delete: " + dataElement?.name

            // delete batch
            try {
                // programStageDataElements are deleted automatically when underlying data element is deleted
                errors.addAll(surveyProgramDeletionService.deleteDataElementAndRelatedData(auth, dataElement))

            } catch (Exception e) {
                log.error "write, exception occurred: " + e.message, e
                errors << [code: "error.text", args: [e.message]]
            }

            if (errors) {
                hasErrors = true
                errors.each { error ->
                    createJobExecutionError(error?.code, error?.args)
                }
            }
        }


        // Get the processed/read count plus the total count
        def writeCount = stepExecution.writeCount as Long
        def totalCount
        if (stepExecution.getStepName() == "deleteProgramStageMetadata_0") {
            // this is the sixth step, so get the sixth item in the array
            totalCount = stepExecution.jobExecution.jobParameters.getLong("totalCount[5]" as String)
        } else if (stepExecution.getStepName() == "deleteProgramStageMetadata_1") {
            // this is the seventh step, so get the seventh item in the array
            totalCount = stepExecution.jobExecution.jobParameters.getLong("totalCount[6]" as String)
        } else if (stepExecution.getStepName() == "deleteProgramStageMetadata_2") {
            // this is the eighth step, so get the eighth item in the array
            totalCount = stepExecution.jobExecution.jobParameters.getLong("totalCount[7]" as String)
        }

        log.debug "writeCount/totalCount: " + writeCount + " / " + totalCount

        if (hasErrors)  {
            stepExecution.addFailureException(new NEPBatchProcessException(
                    "Survey Deletion Job Failed due to errors in program stage ${programStageNumber} metadata deletion"))
            stepExecution.exitStatus = ExitStatus.FAILED
        }
    }

}