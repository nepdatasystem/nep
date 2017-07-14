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
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.NonTransientResourceException
import org.springframework.batch.item.ParseException
import org.springframework.batch.item.UnexpectedInputException

/**
 * Spring Batch reader of Survey Program Stage Metadata for import
 */
@Log4j
public class SurveyProgramStageMetadataReader implements ItemReader<Map<String, Object>> {

    private def program
    private def programStage
    private def fields

    private int rowCount = 0
    private int totalCount = 0
    private List data

    Integer programStageNumber

    def surveyCodebookParserService

    /**
     *
     * @param stepExecution
     */
    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        log.debug "beforeStep"
    }

    /**
     * Reads a batch to pass to the writer
     *
     * @return The row of data to read to pass to the writer
     * @throws Exception
     * @throws UnexpectedInputException
     * @throws ParseException
     * @throws NonTransientResourceException
     */
    @Override
    public Map<String, Object> read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {

        if (data) {
            log.debug "read, programStageNumber: ${programStageNumber}, rowCount: ${rowCount}, data.size(): ${data.size()}"
            if (rowCount < data.size()) {
                def row = [:]

                // Data is the row itself
                def dataRow = data.get(rowCount++)
                row << [data: dataRow]
                log.debug "read, dataRow: ${dataRow}"

                // Additional data
                row << [additionalData: [program: program, programStage: programStage, fields: fields]]

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
        this.program = null
        this.programStage = null
        this.rowCount = 0
        this.totalCount = 0
        this.data = null
        this.fields = null
    }

    /**
     * Sets the data for this reader
     *
     * @param codebookFile The codebook file containing all the data to import
     * @param program The program to import into
     * @param programStage The program stage to import into
     * @param fields Supporting data for the import
     */
    public void setData(File codebookFile, def program, def programStage, def fields) {

        this.program = program
        this.programStage = programStage

        // Get the codebook
        def codebook = codebookFile ? surveyCodebookParserService.parse(codebookFile) : null

        this.rowCount = 0
        this.totalCount = codebook ? codebook.size() : 0
        this.data = codebook

        this.fields = fields
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
