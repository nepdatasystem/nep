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
 * Spring Batch reader of Survey Program Stage Metadata for deletion
 */
@Log4j
public class SurveyProgramStageMetadataDeletionReader implements ItemReader<Map<String,String>> {
    private def program
    private def programStage
    private def dataElements

    private int rowCount = 0
    private int totalCount = 0

    Integer programStageNumber

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
            log.debug "read, programStageNumber: ${programStageNumber}, rowCount: ${rowCount}, totalCount: ${totalCount}"
            if (rowCount < totalCount) {

                def dataElement = dataElements?.get(rowCount)
                def row = [dataElement: dataElement]

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
     * Resets all the data (instance variables)
     */
    public void resetData() {
        this.auth = null
        this.program = null
        this.programStage = null
        this.dataElements = null
        this.totalCount = 0

        this.rowCount = 0
    }

    /**
     * Sets the data for reading program stage metadata to delete
     *
     * @param auth DHIS 2 credentials
     * @param program program to delete program stage metadata for
     * @param programStage program stage to delete metadata for
     * @param totalCount total count to delete
     */
    void setData(def auth, def program, def programStage, def totalCount) {

        log.debug "program.name: " + program.name

        this.auth = auth

        this.program = program
        this.programStage = programStage
        this.dataElements = programStage?.programStageDataElements?.collect { psde ->
            psde.dataElement
        }
        this.totalCount = totalCount

        this.rowCount = 0

    }

    /**
     * Retrieves the total count of records
     *
     * @return The total count of records
     */
    public def getTotalCount() {
        return this.totalCount
    }
}
