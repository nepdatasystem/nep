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

package nep.batch

import groovy.json.JsonOutput
import groovy.util.logging.Log4j
import org.springframework.batch.core.StepExecution

/**
 * Abstract Spring Batch writer to provide utility methods for NEP batch processing writers
 */
@Log4j
public abstract class AbstractBatchWriter {

    StepExecution stepExecution

    /**
     * Creates a job execution error with the supplied code and args
     *
     * @param code Code for the error in the message bundle
     * @param args Args for the error in the  message bundle
     * @return the jobExecutionError
     */
    protected JobExecutionError createJobExecutionError(def code, def args=null) {

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
