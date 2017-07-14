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
import nep.services.survey.SurveyProgramStageMetadataService
import nep.services.survey.SurveyProgramStageService
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

    // Aggregate Job
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

    // Survey Jobs
    // Survey Data
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

    // Survey Metadata
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

    // Spring Security
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

    // API Service
    apiService(ApiService) {
        apiResultParserFactoryService = ref('apiResultParserFactoryService')
        server = '${dhis2.server}'
        context = '${dhis2.context}'
        globalApiVersion = (grailsApplication.config.dhis2.api.version) ? ApiVersion.get(grailsApplication.config.dhis2.api.version) : null
    }

    loginService(LoginService) {
        server = '${dhis2.server}'
        context = '${dhis2.context}'
    }
}
