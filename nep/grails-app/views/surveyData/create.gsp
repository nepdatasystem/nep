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
      function updateProgramStageName() {
        var programName = $('#programName').val();
        $('#programName').val(programName.toUpperCase());
        $('#programStageName_0').val((programName + " " + $('#programStageId_0').val()).toUpperCase());
        $('#programStageName_1').val((programName + " " + $('#programStageId_1').val()).toUpperCase());
        $('#programStageName_2').val((programName + " " + $('#programStageId_2').val()).toUpperCase());
      }

      function submitForm(button) {
        // spinner is modal so will prevent user from double-clicking
        showSpinner();
      }

      $(document).ready(function() {
        $('#create-survey-form').keypress(function(e) {
          if (e.keyCode == '13') {
            e.preventDefault();
          }
        });

        updateProgramStageName();
      });
    </script>
</head>

<body>
<div class="actions short-spacing">
    <div class="actions-create">
        <ol class="breadcrumb">
            <li class="active"><g:link controller="surveyData"
                                       action="create">${message(code: "survey.createSurvey")}</g:link></li>
            <li><g:link controller="surveyMetadata"
                        action="upload">${message(code: "survey.uploadMetadata")}</g:link></li>
            <li><g:link controller="surveyData" action="upload">${message(code: "survey.uploadData")}</g:link></li>
        </ol>
    </div>

    <div class="actions-status">
        <g:render template="../checkStatusSurvey"/>
    </div>
</div>

<div class="ui-container">
    <h1><g:message code="survey.createSurvey"/></h1>

    <div class="ui-main">
        <g:render template="/messages"/>
        <h2><g:message code="survey.survey"/>
            <span class="tooltip">
                <span class="tooltip-content"><g:message code="survey.program"/></span>
            </span>
        </h2>
        <g:form controller="surveyData" action="submit" method="GET" name="create-survey-form">

            <div class="field-group">
                <label for="programName"><g:message code="survey.name"/></label>
                <g:textField class="form-control" id="programName" name="programName"
                             onchange="updateProgramStageName()" value="${params?.programName}"/>
            </div>

            <div class="field-group">
                <label for="programDisplayName"><g:message code="survey.description"/></label>
                <g:textField class="form-control" id="programDisplayName" name="programDisplayName"
                             value="${params?.programDisplayName}"/>
            </div>

            <div class="field-group">
                <label for="trackedEntityId"><g:message code="survey.respondent"/>
                    <span class="tooltip">
                        <span class="tooltip-content"><g:message code="survey.trackedEntity"/></span>
                    </span>
                </label>${trackeEntities}
                <g:if test="${trackedEntities?.size()}">
                    <g:select class="form-control" id="trackedEntityId" value="${params?.trackedEntityId}"
                              optionKey="id" optionValue="name" name="trackedEntityId"
                              from="${trackedEntities}"/>
                </g:if>
                <g:else>
                    <input tpe="select" class="form-control" id="trackedEntityId" name="trackedEntityId"
                           onchange="reload()"/>
                </g:else>
            </div>

            <div class="field-group">
                <label for="org-unit-level"><g:message code="survey.orgUnitLevel"/></label>
                <g:select class="form-control" id="org-unit-level" value="${params?.orgUnitLevel}" name="orgUnitLevel"
                          from='${organisationUnitLevels}'
                          optionKey="key" optionValue="value"></g:select>
            </div>

            <h2><g:message code="survey.databases"/>
                <span class="tooltip">
                    <span class="tooltip-content"><g:message code="survey.programStages"/></span>
                </span>
            </h2>

            <!-- PROGRAM STAGE 0 -->
            <div class="field-group">
                <label for="programStage_0">
                    <g:checkBox class="field-expand-toggle" name="programStage_0" value="true"
                                checked="${params?.programStage_0}"/> <g:message
                            code="survey.database"/> 1</label>
                <input type="hidden" name="programStage_0" value="false"/>
            </div>

            <div id="programStage_0_fields">
                <div class="field-group smalls single-row">
                    <label for="programStageName_0"><g:message code="survey.name"/></label>
                    <g:textField class="form-control" readonly="readonly" name="programStageName_0"/>
                </div>

                <div class="field-group small single-row">
                    <label for="programStageId_0"><g:message code="survey.id"/>
                        <span class="tooltip">
                            <span class="tooltip-content"><g:message code="survey.programStages.id.help"/></span>
                        </span>
                    </label>
                    <g:textField class="form-control tiny" name="programStageId_0" maxlength="2"
                                 onchange="updateProgramStageName()" value="${params.programStageId_0}"/>
                </div>

                <div class="field-group clear">
                    <label for="programStageDescription_0"><g:message code="survey.description"/></label>
                    <g:textField class="form-control" name="programStageDescription_0"
                                 value="${params?.programStageDescription_0}"/>
                </div>
            </div>
            <g:hiddenField name="programStageRepeatable_0" value="true"/>
            <g:hiddenField name="programStageReportDateDescription_0" value="${message(code: 'survey.surveyDate')}"/>

            <!-- PROGRAM STAGE 1 -->
            <div class="field-group">
                <label for="programStage_1">
                    <g:checkBox class="field-expand-toggle" name="programStage_1" value="true"
                                checked="${params?.programStage_1}"/> <g:message
                            code="survey.database"/> 2</label>
                <input type="hidden" name="programStage_1" value="false"/>
            </div>

            <div id="programStage_1_fields">
                <div class="field-group small single-row">
                    <label for="programStageName_1"><g:message code="survey.name"/></label>
                    <g:textField class="form-control" readonly="readonly" name="programStageName_1"/>
                </div>

                <div class="field-group small single-row">
                    <label for="programStageId_1"><g:message code="survey.id"/>
                        <span class="tooltip">
                            <span class="tooltip-content"><g:message code="survey.programStages.id.help"/></span>
                        </span>
                    </label>
                    <g:textField class="form-control tiny" name="programStageId_1" maxlength="2"
                                 onchange="updateProgramStageName()" value="${params.programStageId_1}"/>
                </div>

                <div class="field-group clear">
                    <label for="programStageDescription_1"><g:message code="survey.description"/></label>
                    <g:textField class="form-control" name="programStageDescription_1"
                                 value="${params?.programStageDescription_1}"/>
                </div>
            </div>
            <g:hiddenField name="programStageRepeatable_1" value="true"/>
            <g:hiddenField name="programStageReportDateDescription_1" value="${message(code: 'survey.surveyDate')}"/>

            <!-- PROGRAM STAGE 2 -->
            <div class="field-group">
                <label for="programStage_2">
                    <g:checkBox class="field-expand-toggle" name="programStage_2" value="true"
                                checked="${params?.programStage_2}"/> <g:message
                            code="survey.database"/> 3</label>
                <input type="hidden" name="programStage_2" value="false"/>
            </div>

            <div id="programStage_2_fields">
                <div class="field-group small single-row">
                    <label for="programStageName_2"><g:message code="survey.name"/></label>
                    <g:textField class="form-control" readonly="readonly" name="programStageName_2"/>
                </div>

                <div class="field-group small single-row">
                    <label for="programStageId_1"><g:message code="survey.id"/>
                        <span class="tooltip">
                            <span class="tooltip-content"><g:message code="survey.programStages.id.help"/></span>
                        </span>
                    </label>
                    <g:textField class="form-control tiny" name="programStageId_2" maxlength="2"
                                 onchange="updateProgramStageName()" value="${params.programStageId_2}"/>
                </div>

                <div class="field-group clear">
                    <label for="programStageDescription_2"><g:message code="survey.description"/></label>
                    <g:textField class="form-control" name="programStageDescription_2"
                                 value="${params?.programStageDescription_2}"/>
                </div>
            </div>
            <g:hiddenField name="programStageRepeatable_2" value="true"/>
            <g:hiddenField name="programStageReportDateDescription_2" value="${message(code: 'survey.surveyDate')}"/>

            <!-- SUBMIT -->
            <g:submitButton class="btn" name="create" value="${message(code: 'label.create')}"
                            onclick="submitForm(this);"/>

        </g:form>
        <g:link controller="surveyData" action="list">${message(code: "survey.listSurveys")}</g:link>
    </div>

    <div class="sidebar">
        <h3><g:message code="help.contextual.title"/></h3>

        <p>${dataCSVHeadings}</p>
        <g:render template="helpCreate"/>
        <g:link controller="help" action="index"><g:message code="help.view.all"/></g:link>
    </div>
</div>

<div class="loading">
    <div class="spinner"></div>

    <div class="loading-text">
        <g:message code="common.creating"/>
    </div>
</div>
</body>
</html>
