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
import groovy.sql.Sql
import groovy.util.logging.Log4j

import org.springframework.batch.core.repository.dao.AbstractJdbcBatchMetadataDao
import org.springframework.util.StringUtils


/**
 * Service to delete step and job executions
 */
@Log4j
class DeleteJobService {
    /**
     * SQL statements removing step and job executions compared to a given date.
     */
    private static final String  SQL_DELETE_BATCH_STEP_EXECUTION_CONTEXT = "DELETE FROM %PREFIX%STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID IN (SELECT STEP_EXECUTION_ID FROM %PREFIX%STEP_EXECUTION WHERE JOB_EXECUTION_ID = :jobExecutionId)"
    private static final String  SQL_DELETE_BATCH_STEP_EXECUTION         = "DELETE FROM %PREFIX%STEP_EXECUTION WHERE JOB_EXECUTION_ID = :jobExecutionId"
    private static final String  SQL_DELETE_BATCH_JOB_EXECUTION_CONTEXT  = "DELETE FROM %PREFIX%JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID = :jobExecutionId"
    private static final String  SQL_DELETE_BATCH_JOB_EXECUTION_PARAMS   = "DELETE FROM %PREFIX%JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID = :jobExecutionId"
    private static final String  SQL_SELECT_BATCH_JOB_INSTANCES          = "SELECT JOB_INSTANCE_ID FROM %PREFIX%JOB_EXECUTION WHERE JOB_EXECUTION_ID = :jobExecutionId"
    private static final String  SQL_DELETE_BATCH_JOB_EXECUTION          = "DELETE FROM %PREFIX%JOB_EXECUTION WHERE JOB_EXECUTION_ID = :jobExecutionId"
    private static final String  SQL_DELETE_BATCH_JOB_INSTANCE           = "DELETE FROM %PREFIX%JOB_INSTANCE WHERE JOB_INSTANCE_ID = :jobInstanceId"

    /**
     * Default value for the table prefix property.
     */
    private static final String  DEFAULT_TABLE_PREFIX                    = AbstractJdbcBatchMetadataDao.DEFAULT_TABLE_PREFIX

    /**
     * Default value for the data retention (in month)
     */
    private static final Integer DEFAULT_RETENTION_MONTH                 = 6

    private String               tablePrefix                             = DEFAULT_TABLE_PREFIX

    // Datasource for the batch tables
    def dataSource

    /**
     * Delete all batch job data for the supplied jobExecutionId
     *
     * @param jobExecutionId
     * @return
     */
    public def delete(def jobExecutionId) {
        int totalCount = 0

        def sql = new Sql(dataSource)

        // JOB_EXECUTION_CONTEXT
        int rowCount = sql.executeUpdate(getQuery(SQL_DELETE_BATCH_STEP_EXECUTION_CONTEXT), [jobExecutionId: jobExecutionId])
        log.info "Deleted rows number from the BATCH_STEP_EXECUTION_CONTEXT table: "+ rowCount
        totalCount += rowCount

        rowCount = sql.executeUpdate(getQuery(SQL_DELETE_BATCH_STEP_EXECUTION), [jobExecutionId: jobExecutionId])
        log.info "Deleted rows number from the BATCH_STEP_EXECUTION table: " + rowCount
        totalCount += rowCount

        rowCount = sql.executeUpdate(getQuery(SQL_DELETE_BATCH_JOB_EXECUTION_CONTEXT), [jobExecutionId: jobExecutionId])
        log.info "Deleted rows number from the BATCH_JOB_EXECUTION_CONTEXT table: " + rowCount
        totalCount += rowCount

        def jobInstanceIds = sql.rows(getQuery(SQL_SELECT_BATCH_JOB_INSTANCES), [jobExecutionId: jobExecutionId])
        log.info "Got jobInstanceIds from the BATCH_JOB_EXECUTION table: " + jobInstanceIds

        rowCount = sql.executeUpdate(getQuery(SQL_DELETE_BATCH_JOB_EXECUTION_PARAMS), [jobExecutionId: jobExecutionId])
        log.info "Deleted rows number from the BATCH_JOB_EXECUTION_PARAMS table: " + rowCount
        totalCount += rowCount

        rowCount = sql.executeUpdate(getQuery(SQL_DELETE_BATCH_JOB_EXECUTION), [jobExecutionId: jobExecutionId])
        log.info "Deleted rows number from the BATCH_JOB_EXECUTION table: " + rowCount
        totalCount += rowCount

        if (jobInstanceIds) {
            jobInstanceIds.each {
                def jobInstanceId = it.job_instance_id
                rowCount = sql.executeUpdate(getQuery(SQL_DELETE_BATCH_JOB_INSTANCE), [jobInstanceId: jobInstanceId])
                log.info "Deleted rows number from the BATCH_JOB_INSTANCE table: " + rowCount
                totalCount += rowCount
            }
        }

        return
    }

    /**
     * Utility to get a query from the supplied base by replacing %PREFIX% with the configured tablePrefix
     *
     * @param base The base to get the query for
     * @return The query
     */
    protected String getQuery(String base) {
        def thing =  StringUtils.replace(base, "%PREFIX%", tablePrefix)
        return thing
    }
}
