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

import com.twopaths.api.ApiService
import com.twopaths.dhis2.api.ApiVersion
import com.twopaths.dhis2.services.LoginService
import grails.util.Environment
import grails.util.Holders
import nep.batch.aggregate.AggregateDataReader
import nep.batch.aggregate.AggregateDataWriter
import nep.batch.survey.SurveyProgramDataReader
import nep.batch.survey.SurveyProgramDataWriter
import nep.batch.survey.SurveyProgramMetadataReader
import nep.batch.survey.SurveyProgramMetadataWriter
import nep.batch.survey.SurveyProgramStageDataReader
import nep.batch.survey.SurveyProgramStageDataWriter
import nep.batch.survey.SurveyProgramStageMetadataReader
import nep.batch.survey.SurveyProgramStageMetadataWriter
import nep.batch.survey.SurveyProgramDataDeletionReader
import nep.batch.survey.SurveyProgramDataDeletionWriter
import nep.batch.survey.SurveyProgramStageDataDeletionReader
import nep.batch.survey.SurveyProgramStageDataDeletionWriter
import nep.batch.survey.SurveyProgramReportAndSupportingMetadataDeletionReader
import nep.batch.survey.SurveyProgramReportAndSupportingMetadataDeletionWriter
import nep.batch.survey.SurveyProgramMetadataDeletionReader
import nep.batch.survey.SurveyProgramMetadataDeletionWriter
import nep.batch.survey.SurveyProgramStageMetadataDeletionReader
import nep.batch.survey.SurveyProgramStageMetadataDeletionWriter
import nep.batch.survey.SurveyProgramStageDeletionReader
import nep.batch.survey.SurveyProgramStageDeletionWriter
import nep.batch.survey.SurveyProgramDeletionReader
import nep.batch.survey.SurveyProgramDeletionWriter
import nep.batch.aggregate.DataSetDataDeletionReader
import nep.batch.aggregate.DataSetDataDeletionWriter
import nep.batch.aggregate.DataSetDataElementDeletionReader
import nep.batch.aggregate.DataSetDataElementDeletionWriter
import nep.batch.aggregate.DataSetCategoryComboDeletionReader
import nep.batch.aggregate.DataSetCategoryComboDeletionWriter
import nep.batch.aggregate.DataSetDeletionReader
import nep.batch.aggregate.DataSetDeletionWriter
import nep.springsecurity.CustomAuthenticationProvider
import org.springframework.web.servlet.i18n.SessionLocaleResolver

// Place your Spring DSL code here
beans = {

    xmlns batch:"http://www.springframework.org/schema/batch"

    switch(Environment.current.getName()) {
        case ['qa', 'prod', 'production']:
            importBeans('classpath:batch.xml')
            break
        case ['dev', 'development']:
            importBeans('file:grails-app/conf/spring/batch.xml')
            break
    }

    // ------------------------------------------
    // AGGREGATE JOB
    // ------------------------------------------
    batch.job(id: 'aggregateDataJob') {
        batch.step(id: 'importAggregateData') {
            batch.tasklet() {
                batch.chunk(
                    reader: 'aggregateDataReader',
                    writer: 'aggregateDataWriter',
                    'commit-interval': '1',
                    'retry-policy': 'neverRetryPolicy',
                    'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }
    }

    aggregateDataReader(AggregateDataReader)

    aggregateDataWriter(AggregateDataWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        aggregateDataService = ref('aggregateDataService')
        resourceTableService = ref('resourceTableService')
    }

    // ------------------------------------------
    // SURVEY JOBS
    // ------------------------------------------

    // ------------------------------------------
    // Survey Data Import
    // ------------------------------------------

    batch.job(id: 'surveyDataJob') {
        batch.step(id: 'importProgramData', next: 'importProgramStageData_0') {
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramDataReader',
                        writer: 'surveyProgramDataWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                        )
            }
        }
        batch.step(id: 'importProgramStageData_0', next: 'importProgramStageData_1') {
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageDataReader_0',
                        writer: 'surveyProgramStageDataWriter_0',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                        )
            }
        }
        batch.step(id: 'importProgramStageData_1', next: 'importProgramStageData_2') {
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageDataReader_1',
                        writer: 'surveyProgramStageDataWriter_1',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                        )
            }
        }
        batch.step(id: 'importProgramStageData_2') {
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageDataReader_2',
                        writer: 'surveyProgramStageDataWriter_2',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }
    }

    // Program
    surveyProgramDataReader(SurveyProgramDataReader)
    surveyProgramDataWriter(SurveyProgramDataWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyTrackedEntityInstanceService = ref('surveyTrackedEntityInstanceService')
        resourceTableService = ref('resourceTableService')
    }

    // Program Stage 0
    surveyProgramStageDataReader_0(SurveyProgramStageDataReader) { bean ->
        programStage = 0
    }
    surveyProgramStageDataWriter_0(SurveyProgramStageDataWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramStageService = ref('surveyProgramStageService')
        resourceTableService = ref('resourceTableService')
        programStage = 0
    }


    // Program Stage 1
    surveyProgramStageDataReader_1(SurveyProgramStageDataReader) { bean ->
        programStage = 0
    }
    surveyProgramStageDataWriter_1(SurveyProgramStageDataWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramStageService = ref('surveyProgramStageService')
        resourceTableService = ref('resourceTableService')
        programStage = 1
    }

    // Program Stage 2
    surveyProgramStageDataReader_2(SurveyProgramStageDataReader) { bean ->
        programStage = 2
    }
    surveyProgramStageDataWriter_2(SurveyProgramStageDataWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramStageService = ref('surveyProgramStageService')
        resourceTableService = ref('resourceTableService')
        programStage = 2
    }

    // ------------------------------------------
    // Survey Metadata Import
    // ------------------------------------------

    batch.job(id: 'surveyMetadataJob') {
        batch.step(id: 'importProgramMetadata', next: 'importProgramStageMetadata_0') {
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramMetadataReader',
                        writer: 'surveyProgramMetadataWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }
        batch.step(id: 'importProgramStageMetadata_0', next: 'importProgramStageMetadata_1') {
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageMetadataReader_0',
                        writer: 'surveyProgramStageMetadataWriter_0',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }
        batch.step(id: 'importProgramStageMetadata_1', next: 'importProgramStageMetadata_2') {
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageMetadataReader_1',
                        writer: 'surveyProgramStageMetadataWriter_1',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }
        batch.step(id: 'importProgramStageMetadata_2') {
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageMetadataReader_2',
                        writer: 'surveyProgramStageMetadataWriter_2',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }
    }

    // Program Metadata
    surveyProgramMetadataReader(SurveyProgramMetadataReader) { bean ->
        surveyCodebookParserService = ref('surveyCodebookParserService')
    }
    surveyProgramMetadataWriter(SurveyProgramMetadataWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramMetadataService = ref('surveyProgramMetadataService')
    }

    // Program Metadata Stage 0
    surveyProgramStageMetadataReader_0(SurveyProgramStageMetadataReader) { bean ->
        surveyCodebookParserService = ref('surveyCodebookParserService')
        programStageNumber = 0
    }
    surveyProgramStageMetadataWriter_0(SurveyProgramStageMetadataWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramStageService = ref('surveyProgramStageService')
        surveyProgramStageMetadataService = ref('surveyProgramStageMetadataService')
        programStageNumber = 0
    }

    // Program Metadata Stage 1
    surveyProgramStageMetadataReader_1(SurveyProgramStageMetadataReader) { bean ->
        surveyCodebookParserService = ref('surveyCodebookParserService')
        programStageNumber = 1
    }
    surveyProgramStageMetadataWriter_1(SurveyProgramStageMetadataWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramStageService = ref('surveyProgramStageService')
        surveyProgramStageMetadataService = ref('surveyProgramStageMetadataService')
        programStageNumber = 1
    }

    // Program Metadata Stage 2
    surveyProgramStageMetadataReader_2(SurveyProgramStageMetadataReader) { bean ->
        surveyCodebookParserService = ref('surveyCodebookParserService')
        programStageNumber = 2
    }
    surveyProgramStageMetadataWriter_2(SurveyProgramStageMetadataWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramStageService = ref('surveyProgramStageService')
        surveyProgramStageMetadataService = ref('surveyProgramStageMetadataService')
        programStageNumber = 2
    }


    // ------------------------------------------
    // Survey Deletion
    // ------------------------------------------
    batch.job(id: 'surveyDeletionJob') {
        // -------------
        // Survey Data Deletion
        // -------------
        // Program Stage Data Deletion
        batch.step(id: 'deleteProgramStageData_0') {
            batch.fail(
                on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteProgramStageData_1'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageDataDeletionReader_0',
                        writer: 'surveyProgramStageDataDeletionWriter_0',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }
        batch.step(id: 'deleteProgramStageData_1') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteProgramStageData_2'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageDataDeletionReader_1',
                        writer: 'surveyProgramStageDataDeletionWriter_1',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }
        batch.step(id: 'deleteProgramStageData_2') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteProgramData'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageDataDeletionReader_2',
                        writer: 'surveyProgramStageDataDeletionWriter_2',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }
        // Program Data Deletion
        batch.step(id: 'deleteProgramData') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteProgramReports'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramDataDeletionReader',
                        writer: 'surveyProgramDataDeletionWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }

        // -------------
        // Survey Metadata Deletion
        // -------------

        batch.step(id: 'deleteProgramReports') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteProgramStageMetadata_0'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramReportAndSupportingMetadataDeletionReader',
                        writer: 'surveyProgramReportAndSupportingMetadataDeletionWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }

        batch.step(id: 'deleteProgramStageMetadata_0') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteProgramStageMetadata_1'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageMetadataDeletionReader_0',
                        writer: 'surveyProgramStageMetadataDeletionWriter_0',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }

        batch.step(id: 'deleteProgramStageMetadata_1') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteProgramStageMetadata_2'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageMetadataDeletionReader_1',
                        writer: 'surveyProgramStageMetadataDeletionWriter_1',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }

        batch.step(id: 'deleteProgramStageMetadata_2') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteProgramMetadata'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageMetadataDeletionReader_2',
                        writer: 'surveyProgramStageMetadataDeletionWriter_2',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }

        batch.step(id: 'deleteProgramMetadata') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteProgramStages'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramMetadataDeletionReader',
                        writer: 'surveyProgramMetadataDeletionWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }
        // -------------
        // Survey Deletion
        // -------------

        batch.step(id: 'deleteProgramStages') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteProgram'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramStageDeletionReader',
                        writer: 'surveyProgramStageDeletionWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }

        batch.step(id: 'deleteProgram') {
            batch.tasklet() {
                batch.chunk(
                        reader: 'surveyProgramDeletionReader',
                        writer: 'surveyProgramDeletionWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }

    }

    // Program Stage Deletion
    surveyProgramStageDeletionReader (SurveyProgramStageDeletionReader) { bean ->
    }
    surveyProgramStageDeletionWriter (SurveyProgramStageDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
    }

    // Program Deletion
    surveyProgramDeletionReader (SurveyProgramDeletionReader) { bean ->
    }
    surveyProgramDeletionWriter (SurveyProgramDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
    }

    // Program Report Deletion
    surveyProgramReportAndSupportingMetadataDeletionReader (SurveyProgramReportAndSupportingMetadataDeletionReader) { bean ->
    }

    surveyProgramReportAndSupportingMetadataDeletionWriter (SurveyProgramReportAndSupportingMetadataDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
        surveyProgramService = ref('surveyProgramService')
    }

    // Program Stage 0 Metadata Deletion
    surveyProgramStageMetadataDeletionReader_0 (SurveyProgramStageMetadataDeletionReader) { bean ->
        programStageNumber = 0
    }
    surveyProgramStageMetadataDeletionWriter_0 (SurveyProgramStageMetadataDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
        programStageNumber = 0
    }

    // Program Stage 1 Metadata Deletion
    surveyProgramStageMetadataDeletionReader_1 (SurveyProgramStageMetadataDeletionReader) { bean ->
        programStageNumber = 1
    }
    surveyProgramStageMetadataDeletionWriter_1 (SurveyProgramStageMetadataDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
        programStageNumber = 1
    }

    // Program Stage 2 Metadata Deletion
    surveyProgramStageMetadataDeletionReader_2 (SurveyProgramStageMetadataDeletionReader) { bean ->
        programStageNumber = 2
    }
    surveyProgramStageMetadataDeletionWriter_2 (SurveyProgramStageMetadataDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
        programStageNumber = 2
    }

    // Program Metadata Deletion
    surveyProgramMetadataDeletionReader (SurveyProgramMetadataDeletionReader) { bean ->
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
    }
    surveyProgramMetadataDeletionWriter (SurveyProgramMetadataDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
    }

    // Program Stage 0 Data Deletion
    surveyProgramStageDataDeletionReader_0 (SurveyProgramStageDataDeletionReader) { bean ->
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
        programStageNumber = 0
    }
    surveyProgramStageDataDeletionWriter_0 (SurveyProgramStageDataDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
        programStageNumber = 0
    }

    // Program Stage 1 Data Deletion
    surveyProgramStageDataDeletionReader_1 (SurveyProgramStageDataDeletionReader) { bean ->
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
        programStageNumber = 1
    }
    surveyProgramStageDataDeletionWriter_1 (SurveyProgramStageDataDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
        programStageNumber = 1
    }

    // Program Stage 2 Data Deletion
    surveyProgramStageDataDeletionReader_2 (SurveyProgramStageDataDeletionReader) { bean ->
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
        programStageNumber = 2
    }
    surveyProgramStageDataDeletionWriter_2 (SurveyProgramStageDataDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
        programStageNumber = 2
    }

    // Program Data Deletion
    surveyProgramDataDeletionReader (SurveyProgramDataDeletionReader) { bean ->
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
    }
    surveyProgramDataDeletionWriter (SurveyProgramDataDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        surveyProgramDeletionService = ref('surveyProgramDeletionService')
    }

    // ------------------------------------------
    // Data Set Deletion
    // ------------------------------------------
    batch.job(id: 'dataSetDeletionJob') {
        // -------------
        // Data Set Data Deletion
        // -------------

        // Data Set Data Deletion
        batch.step(id: 'deleteDataSetData') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteDataSetDataElements'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'dataSetDataDeletionReader',
                        writer: 'dataSetDataDeletionWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }

        // -------------
        // Data Set Metadata Deletion
        // -------------

        batch.step(id: 'deleteDataSetDataElements') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteDataSetCategoryCombos'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'dataSetDataElementDeletionReader',
                        writer: 'dataSetDataElementDeletionWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }

        batch.step(id: 'deleteDataSetCategoryCombos') {
            batch.fail(
                    on: 'FAILED'
            )
            batch.next(
                    on: '*',
                    to: 'deleteDataSet'
            )
            batch.tasklet() {
                batch.chunk(
                        reader: 'dataSetCategoryComboDeletionReader',
                        writer: 'dataSetCategoryComboDeletionWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }


        // -------------
        // Data Set Deletion
        // -------------

        batch.step(id: 'deleteDataSet') {
            batch.tasklet() {
                batch.chunk(
                        reader: 'dataSetDeletionReader',
                        writer: 'dataSetDeletionWriter',
                        'commit-interval': '1' ,
                        'retry-policy': 'neverRetryPolicy',
                        'skip-policy': 'alwaysSkipItemSkipPolicy'
                )
            }
        }

    }

    // Data Set Deletion
    dataSetDeletionReader (DataSetDeletionReader) { bean ->
    }
    dataSetDeletionWriter (DataSetDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        aggregateDataSetDeletionService = ref('aggregateDataSetDeletionService')
    }

    // Data Set Metadata Deletion
    // Data Elements Deletion
    dataSetDataElementDeletionReader (DataSetDataElementDeletionReader) { bean ->
    }
    dataSetDataElementDeletionWriter (DataSetDataElementDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        aggregateDataSetDeletionService = ref('aggregateDataSetDeletionService')
    }

    // Category Combos Deletion
    dataSetCategoryComboDeletionReader (DataSetCategoryComboDeletionReader) { bean ->
    }
    dataSetCategoryComboDeletionWriter (DataSetCategoryComboDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        aggregateDataSetDeletionService = ref('aggregateDataSetDeletionService')
    }

    // Data Set Data Deletion
    dataSetDataDeletionReader (DataSetDataDeletionReader) { bean ->
    }
    dataSetDataDeletionWriter (DataSetDataDeletionWriter) { bean ->
        bean.scope = 'step'
        stepExecution = '#{stepExecution}'
        aggregateDataSetDeletionService = ref('aggregateDataSetDeletionService')
    }

    // ------------------------------------------
    // Spring Security
    // ------------------------------------------

    customAuthenticationProvider(CustomAuthenticationProvider) {
        loginService = ref('loginService')
        userService = ref('userService')
        propertiesService = ref('propertiesService')
    }

    // LocaleResolver (defaults to supplied language or defaultLang)
    def lang = System.getProperty("lang")
    if (!lang) {
        def config = Holders.config
        lang = System.getenv("lang") ?: config.nep.defaultLang
    }
    println "language: " + lang
    localeResolver(SessionLocaleResolver) {
        defaultLocale = new Locale(lang)
        Locale.setDefault(Locale.ENGLISH)
    }

    // ------------------------------------------
    // API Service
    // ------------------------------------------
    apiService(ApiService) {
        apiResultParserFactoryService = ref('apiResultParserFactoryService')
        server = '${dhis2.server}'
        context = '${dhis2.context}'
        globalApiVersion = (grailsApplication.config.dhis2.api.version) ? ApiVersion.get(grailsApplication.config.dhis2.api.version) : null
    }

    // ------------------------------------------
    // Login Service
    // ------------------------------------------
    loginService(LoginService) {
        server = '${dhis2.server}'
        context = '${dhis2.context}'
    }
}
