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
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.NonTransientResourceException
import org.springframework.batch.item.ParseException
import org.springframework.batch.item.UnexpectedInputException

/**
 * Spring Batch reader of Survey Program Stages for deletion
 */
@Log4j
public class SurveyProgramStageDeletionReader implements ItemReader<Map<String,String>> {
    private def program
    private def programStages

    private int rowCount = 0
    private int totalCount = 0

    def auth

    /**
     * Reads a batch to pass to the writer
     *
     * @return The row of data to read to pass to the writer
     *
     * @throws Exception
     * @throws UnexpectedInputException
     * @throws ParseException
     * @throws NonTransientResourceException
     */
    @Override
    public Map<String, Object> read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (totalCount > 0) {
            log.debug "read, rowCount: ${rowCount}, totalCount: ${totalCount}"
            if (rowCount < totalCount) {

                def row = [programStage: programStages.get(rowCount)]

                rowCount++

                return row
            } else {
                return null
            }
        } else {
            log.debug "No data"
            return null
        }

    }

    /**
     * Resets all data for this reader
     */
    public void resetData() {
        this.auth = null
        this.program = null
        this.programStages = null
        this.totalCount = 0

        this.rowCount = 0
    }

    /**
     * Sets the data for reading program stages to delete
     *
     * @param auth DHIS 2 credentials
     * @param program program to delete program stages for
     * @param programStages program stages to delete
     * @param totalCount total number of program stages to delete
     */
    void setData(def auth, def program, def programStages, def totalCount) {

        log.debug "program.name: ${program.name}; num programStages: ${programStages?.size() ?: 0}"

        this.auth = auth

        this.program = program
        this.programStages = programStages
        this.totalCount = totalCount

        this.rowCount = 0

    }

    /**
     * Gets the total counts of program stages to delete
     *
     * @return total count of program stages to delete
     */
    public def getTotalCount() {
        return this.totalCount
    }
}
