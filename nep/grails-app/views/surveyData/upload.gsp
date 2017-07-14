%{--
  - Copyright (c) 2014-2017. Institute for International Programs at Johns Hopkins University.
  - All rights reserved.
  -
  - Redistribution and use in source and binary forms, with or without
  - modification, are permitted provided that the following conditions are met:
  - Redistributions of source code must retain the above copyright notice, this
  - list of conditions and the following disclaimer.
  - Redistributions in binary form must reproduce the above copyright notice,
  - this list of conditions and the following disclaimer in the documentation
  - and/or other materials provided with the distribution.
  - Neither the name of the NEP project, Institute for International Programs,
  - Johns Hopkins University nor the names of its contributors may
  - be used to endorse or promote products derived from this software without
  - specific prior written permission.
  -
  - THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
  - ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  - WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  - DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
  - ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  - (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  - LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
  - ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  - (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  - SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  -
  --}%
<html>
<head>
    <meta name="layout" content="nep"/>
    <script type="text/javascript">
        function reload() {
            var programId = $('#programId').val();
            console.debug("programId", programId);

            window.location.href = "<%=request.getContextPath()%>/${controllerName}/upload?programId=" + programId;
        }
        function submitForm(button) {
            // spinner is modal so will prevent user from double-clicking
            showSpinner();
        }
    </script>
</head>

<body>
<div class="actions short-spacing">
    <div class="actions-create">
        <ol class="breadcrumb">
            <li class="completed"><g:link controller="surveyData" action="create">${message(code: "survey.createSurvey")}</g:link></li>
            <li class="completed"><g:link controller="surveyMetadata" action="upload">${message(code: "survey.uploadMetadata")}</g:link></li>
            <li class="active"><g:link controller="surveyData" action="upload">${message(code: "survey.uploadData")}</g:link></li>
        </ol>
    </div>
    <div class="actions-status">
        <g:render template="../checkStatusSurvey"/>
    </div>
</div>

<div class="ui-container">
    <h1><g:message code="survey.uploadData"/></h1>
    <div class="ui-main">
        <g:render template="/messages"/>
        <g:if test="${programs}">
            <g:uploadForm controller="surveyData" action="uploadSubmit" method="POST" name="upload-survey-data-form">
                <div class="field-group">
                    <label for="programId"><g:message code="survey.survey"/></label>
                    <g:select class="form-control" id="programId" value="${params.programId}" optionKey="id" optionValue="name" name="programId"
                              from="${programs}" noSelection="${['': message(code: 'label.select')]}" onchange="reload()"/>
                </div>

                <g:if test="${params.programId}">
                    <g:if test="${trackedEntities}">
                        <h2 for="trackedEntityId"><g:message code="survey.respondent"/>
                            <span class="tooltip">
                                <span class="tooltip-content"><g:message code="survey.trackedEntity"/></span>
                            </span>
                        </h2>

                        <div class="field-group">
                            <g:each in="${trackedEntities}" var="trackedEntity">
                                <g:hiddenField name="trackedEntityId" value="${trackedEntity?.id}"/>
                                <g:hiddenField name="trackedEntityName" value="${trackedEntity?.name}"/>
                                <div class="field-group">
                                    <label for="codebook-file">${trackedEntity?.name} <g:message code="survey.data"/></label>

                                    <div class="field-group small">
                                        <!--Field that uniquely identifies data-->
                                        <div class="help-text" title="<g:message code="survey.program.selectUniqueRespondentField" args="${[trackedEntity?.name ?: "${message(code: 'survey.respondent')}"]}"/>">
                                            <g:message code="survey.program.selectUniqueRespondentField" args="${[trackedEntity?.name ?: "${message(code: 'survey.respondent')}"]}"/>:
                                        </div>

                                        <g:select class="form-control large" id="program-id-field" name="programIdField" from="${trackedEntityAttributes}"
                                                  value="${params.programIdField}"
                                                  optionKey="code"
                                                  optionValue="${{ it.code }}"
                                                  noSelection="${['': message(code: 'label.select')]}"/>
                                    </div>

                                    <g:if test="${programHasData?.contains(params.programId as String)}">
                                        <g:message code="survey.data.respondent.previouslyUploaded" args="[trackedEntity?.name]"/>
                                    </g:if>
                                    <g:set var="trackedEntityDisabled" value="disabled"/>
                                    <g:if test="${programHasMetadata?.contains(params.programId as String)}">
                                        <g:set var="trackedEntityDisabled" value=""/>
                                    </g:if>
                                    <g:else>
                                        <g:message code="survey.metadata.respondent.notUploaded" args="[trackedEntity?.name]"/>
                                    </g:else>
                                    <input id="codebook-file" type="file" name="programFile" accept=".csv" ${trackedEntityDisabled}/>
                                </div>
                            </g:each>
                        </div>
                    </g:if>
                    <g:if test="${programStages}">

                        <div class="field-group">

                            <h2 for="programStageId"><g:message code="survey.database"/>
                                <span class="tooltip">
                                    <span class="tooltip-content"><g:message code="survey.programStage"/></span>
                                </span>
                            </h2>

                            <g:each in="${programStages}" var="programStage" status="i">
                                <g:hiddenField name="programStageId_${i}" value="${programStage.programStage.id}"/>
                                <g:hiddenField name="programStageName_${i}" value="${programStage.programStage.name}"/>

                                <div class="field-group">

                                    <label for="codebook-file">${programStage.programStage.name} <g:message code="survey.data"/></label>

                                    <div class="field-group small single-row">

                                        <!--Field that uniquely identifies data-->
                                        <div class="help-text" title="<g:message code="survey.programStage.selectUniqueDatabaseField"/>">
                                            <g:message code="survey.programStage.selectUniqueDatabaseField"/>:</div>
                                        <g:select class="form-control form-control large" id="program-id-field" name="programStageIdField_${i}"
                                                  value="${params['programStageIdField_' + i]}"
                                                  from="${programStage.dataElements}" optionKey="code" optionValue="${{ it.code }}"
                                                  noSelection="${['': message(code: 'label.select')]}"/>
                                    </div>

                                    <div class="field-group small single-row">
                                        <!--Field that references Respondent data-->
                                        <g:set var="trackedEntityName" value="${trackedEntities?.size() > 0 ? trackedEntities.get(0)?.name : null}"/>
                                        <div class="help-text" title="<g:message code="survey.programStage.selectRespondentReferenceField" args="${[trackedEntityName]}"/>"><g:message
                                                code="survey.programStage.selectRespondentReferenceField" args="${[trackedEntityName]}"/>:</div>
                                        <g:select class="form-control large" id="programStage-ProgramIdField-${i}" name="programStageProgramIdField_${i}"
                                                  value="${params['programStageProgramIdField_' + i]}"
                                                  from="${programStage.dataElements}" optionKey="code" optionValue="${{ it.code }}"
                                                  noSelection="${['': message(code: 'label.select')]}"/>
                                    </div>

                                    <div class="field-group clear">
                                        <g:if test="${programStageHasData?.contains(programStage.programStage.id as String)}">
                                            <g:message code="survey.data.database.previouslyUploaded"/>
                                        </g:if>
                                        <g:set var="programStageDisabled" value="${trackedEntityDisabled}"/>

                                        <g:if test="${programStageHasMetadata?.contains(programStage.programStage.id as String)}"></g:if>
                                        <g:else>
                                            <g:set var="programStageDisabled" value="disabled"/>
                                            <g:message code="survey.metadata.database.notUploaded"/>
                                        </g:else>
                                        <input id="codebook-file" type="file" name="programStageFile_${i}" accept=".csv" ${programStageDisabled}/>
                                    </div>
                                </div>

                            </g:each>
                        </div>
                    </g:if>

                    <g:if test="${trackedEntities || programStages}">
                        <div class="field-group">
                            <g:submitButton class="btn" name="submit" value="${message(code: 'survey.upload')}" onclick="submitForm(this);"/>
                        </div>
                    </g:if>
                </g:if>
            </g:uploadForm>
        </g:if>
        <g:else>
            No programs
        </g:else>
        <g:link controller="surveyData" action="list">${message(code: "survey.listSurveys")}</g:link>
    </div>

    <div class="sidebar">
        <g:set var="trackedEntityName" value="${trackedEntities?.size() > 0 ? trackedEntities.get(0)?.name : null}"/>
        <h3><g:message code="help.contextual.title"/></h3>
        <p>${dataCSVHeadings}</p>
        <g:render template="helpUpload" model="[trackedEntityName: trackedEntityName]"/>
        <g:link controller="help" action="index"><g:message code="help.view.all"/></g:link>
    </div>

</div>


<div class="loading">
    <div class="spinner"></div>

    <div class="loading-text">
        <g:message code="common.file.uploadInProgress"/>
    </div>
</div>
</body>
</html>
