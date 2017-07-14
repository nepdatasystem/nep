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
<meta name="layout" content="nep" />
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
                <li class="active"><g:link controller="surveyMetadata" action="upload">${message(code: "survey.uploadMetadata")}</g:link></li>
                <li><g:link controller="surveyData" action="upload">${message(code: "survey.uploadData")}</g:link></li>
            </ol>
        </div>
        <div class="actions-status">
            <g:render template="../checkStatusSurvey"/>
        </div>
    </div>

    <div class="ui-container">
        <h1><g:message code="survey.uploadMetadata"/></h1>
        <div class="ui-main">
            <g:render template="/messages" />

            <g:if test="${programs}">
                <g:uploadForm controller="surveyMetadata" action="uploadSubmit" method="POST" accept-charset="UTF-8" name="upload-survey-metadata-form">
                    <div class="field-group">
                        <label for="programId"><g:message code="survey.survey"/></label>
                        <g:select class="form-control" id="programId" value="${params.programId}" optionKey="id" optionValue="name" name="programId"
                                from="${programs}" noSelection="${['':message(code:'label.select')]}" onchange="reload()" />
                    </div>

                    <g:if test="${program}">
                        <g:if test="${trackedEntities}">
                            <div class="field-group">
                                <h2 for="trackedEntityId"><g:message code="survey.respondent"/>
                                    <span class="tooltip">
                                        <span class="tooltip-content"><g:message code="survey.trackedEntity"/></span>
                                    </span>
                                </h2>
                                <g:each in="${trackedEntities}" var="trackedEntity">
                                    <g:hiddenField name="trackedEntityId" value="${trackedEntity?.id}"/>
                                    <div class="field-group">
                                        <label for="codebook-file">${trackedEntity?.name} <g:message code="survey.codebook" /></label>
                                        <g:if test="${programHasMetadata?.contains(params.programId)}">
                                        <g:message code="survey.metadata.respondent.previouslyUploaded" args="[trackedEntity?.name]"/>
                                        </g:if>
                                        <input id="codebook-file" type="file" name="programFile" accept=".csv" />
                                    </div>
                                </g:each>
                            </div>
                        </g:if>

                        <g:if test="${programStages}">
                            <h2 for="programStageId"><g:message code="survey.database"/>
                                <span class="tooltip">
                                    <span class="tooltip-content"><g:message code="survey.programStage"/></span>
                                </span>
                            </h2>
                            <div class="field-group">
                                <g:each in="${programStages}" var="programStage" status="i">
                                    <g:hiddenField name="programStageId_${i}" value="${programStage.programStage.id}" />
                                    <div class="field-group">
                                        <label for="codebook-file">${programStage.programStage.name} <g:message code="survey.codebook" /></label>
                                        <g:if test="${programStageHasMetadata?.contains(programStage.programStage.id)}">
                                            <g:message code="survey.metadata.database.previouslyUploaded"/>
                                        </g:if>
                                        <input id="codebook-file" type="file" name="programStageFile_${i}" accept=".csv" />
                                    </div>
                                </g:each>
                            </div>
                        </g:if>

                        <g:if test="${trackedEntities || programStages}">
                            <div class="field-group">
                                <g:submitButton class="btn" name="submit" value="${message(code: 'survey.upload')}"
                                                onclick="submitForm(this);" />
                            </div>
                        </g:if>
                    </g:if>

                </g:uploadForm>
            </g:if>
            <g:else>
                No programs
            </g:else>
            <g:link controller="surveyData" action="list">${message(code:"survey.listSurveys")}</g:link>
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
