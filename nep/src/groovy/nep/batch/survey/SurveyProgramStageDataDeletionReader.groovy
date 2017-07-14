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
import nep.services.survey.SurveyProgramDeletionService
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.NonTransientResourceException
import org.springframework.batch.item.ParseException
import org.springframework.batch.item.UnexpectedInputException

/**
 * Spring Batch reader of Survey Program Stage Data for deletion
 */
@Log4j
public class SurveyProgramStageDataDeletionReader implements ItemReader<Map<String,String>> {
    private def program
    private def programStage

    private int pageNum = 0
    private int totalCount = 0
    private int numPages = 0
    private int pageSize = 0

    Integer programStageNumber

    def auth

    SurveyProgramDeletionService surveyProgramDeletionService

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
            log.debug "read, programStageNumber: ${programStageNumber}, pageCount: ${pageNum}, totalCount: ${totalCount}"
            // Start at the last page and go down to page 1
            // This way we wont be attempting to delete records that we've previously tried that may have failed
            if (pageNum >= 1) {
                def row = [:]

                def events = surveyProgramDeletionService.getEventsForProgramStageByPage(
                        auth, program?.id, programStage?.id, surveyProgramDeletionService.DATA_PAGING_SIZE,
                        pageNum
                )

                row << [data: events]

                // Additional data
                row << [additionalData: [program: program,
                                         programStage: programStage]]

                pageNum--

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
        this.totalCount = 0
        this.numPages = 0
        this.pageSize = 0

        this.pageNum = 0
    }

    /**
     * Sets the data for this reader
     *
     * @param auth DHIS 2 credentials
     * @param program The program to delete program stage data for
     * @param programStage the program stage to delete data for
     * @param totalCount the total count to delete
     * @param numPages total number of pages to delete
     * @param pageSize page size
     */
    void setData(def auth, def program, def programStage, totalCount, numPages, pageSize) {

        log.debug "program.name: " + program.name

        this.auth = auth

        this.program = program
        this.programStage = programStage
        this.totalCount = totalCount
        this.numPages = numPages
        this.pageSize = pageSize

        this.pageNum = numPages

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
