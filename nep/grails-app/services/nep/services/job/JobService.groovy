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

package nep.services.job

import nep.batch.JobExecutionError
import org.springframework.batch.core.BatchStatus

/**
 * Service to handle all NEP Spring Batch Jobs
 */
class JobService {

    def jobExplorer
    def jobOperator
    def deleteJobService

    /**
     * Gets all executions for the supplied job name
     *
     * @param jobName The name of the job to get executions for
     * @return The found executions for the specified job name
     */
    def getExecutions(def jobName) {
        def executions = []

        // pagination may be necessary here if number of instances is large
        def jobInstances = jobExplorer.getJobInstances(jobName, 0, Integer.MAX_VALUE)
        jobInstances.each { jobInstance ->

            def jobExecutions = jobExplorer.getJobExecutions(jobInstance)
            def jobExecution = jobExecutions ? jobExecutions.first() : null

            if (jobExecution) {

                def stepExecutions = jobExecution?.getStepExecutions()

                def steps = []
                stepExecutions?.eachWithIndex { stepExecution, i ->
                    steps << [
                        startTime: stepExecution.startTime,
                        endTime: stepExecution.endTime,
                        totalCount: stepExecution.jobExecution.jobParameters.getLong("totalCount[${i}]" as String),
                        fileName: stepExecution.jobExecution.jobParameters.getString("fileName[${i}]" as String),
                        processedCount: stepExecution.writeCount,
                        stepExecutionId: stepExecution.id,
                        errorCount: getExecutionErrorsCount(jobExecution.id, stepExecution.id)
                    ]
                }

                executions << [
                    dataSetId:jobExecution.jobParameters.getString("dataSetId"),
                    fileName:jobExecution.jobParameters.getString("fileName"),
                    instanceId:jobInstance.instanceId,
                    jobExecutionId:jobExecution.id,
                    startTime:jobExecution.startTime,
                    endTime:jobExecution.endTime,
                    status:jobExecution.status,
                    steps: steps
                ]
            }
        }

        return executions
    }

    /**
     * Determines if the specified job name is running or not
     *
     * @param jobName The name of the job to check to see if it's running
     * @param dataSetId The id of the associated data set
     * @return If the specified job is running or not
     */
    def isRunning(def jobName, def dataSetId) {
        return jobExplorer.getJobInstances(jobName, 0, Integer.MAX_VALUE).any{ jobInstance ->
            jobExplorer.getJobExecutions(jobInstance).any { jobExecution ->
                //jobExecution.jobParameters.getString("dataSetId") == dataSetId && BatchStatus.STARTED == jobExecution?.status
                BatchStatus.STARTED == jobExecution?.status
            }
        }
    }

    /**
     * Retrieves execution errors if any for the specified job execution and step execution
     *
     * @param jobExecutionId The id of the job execution to get errors for
     * @param stepExecutionId The id of the step execution to get errors for
     * @return errors found if any
     */
    def getExecutionErrors(def jobExecutionId, def stepExecutionId) {
        JobExecutionError.findAllByJobExecutionIdAndStepExecutionId(jobExecutionId, stepExecutionId, [sort: "lineNumber"])
    }

    /**
     * Retrieves the number of execution errors for the specified job execution and step execution
     * @param jobExecutionId The id of the job execution to get the error count for
     * @param stepExecutionId The id of the step execution to get the error count for
     * @return error count
     */
    def getExecutionErrorsCount(def jobExecutionId, def stepExecutionId) {
        JobExecutionError.countByJobExecutionIdAndStepExecutionId(jobExecutionId, stepExecutionId)
    }

    /**
     * Stops the specified job
     *
     * @param jobExecutionId Id of the job execution to stop
     * @return if the stopping was successful or not
     */
    def stop(def jobExecutionId) {
        jobOperator.stop(jobExecutionId)
    }

    /**
     * Removes the specified job
     *
     * @param jobExecutionId The id of the job execution to remove
     * @return
     */
    def remove(def jobExecutionId) {
        // Delete the JobExecutionErrors
        JobExecutionError.executeUpdate("delete from JobExecutionError jee where jee.jobExecutionId = " + jobExecutionId)

        // Delete from Spring Batch tables...
        deleteJobService.delete(jobExecutionId)
    }
}
