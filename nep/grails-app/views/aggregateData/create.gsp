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
      function submitForm(button) {
        // spinner is modal so will prevent user from double-clicking
        showSpinner();
      }

    </script>
</head>

<body>
<div class="actions">
    <div class="actions-create">
        <ol class="breadcrumb">
            <li class="active"><g:link controller="aggregateData" action="create"><g:message
                    code="dataset.create"/></g:link></li>
            <li><g:link controller="aggregateMetadata" action="index"><g:message
                    code="aggregate.upload.metadata"/></g:link></li>
            <li><g:link controller="aggregateMetadata" action="disaggregations"><g:message
                    code="aggregate.upload.disaggregations"/></g:link></li>
            <li><g:link controller="aggregateData" action="upload"><g:message
                    code="aggregate.upload.data"/></g:link></li>
        </ol>
    </div>

    <div class="actions-status">
        <g:render template="../checkStatusAggregate"/>
    </div>
</div>

<div class="ui-container">
    <h1><g:message code="dataset.create"/></h1>

    <div class="ui-main">
        <g:render template="/messages"/>
        <g:form controller="aggregateData" action="postCreate" method="POST" name="create-dataset-form">
            <div class="field-group">
                <label for="dataset-name"><g:message code="dataset.name"/></label>
                <input id="dataset-name" class="form-control" type="text" name="datasetName" value="${datasetName}"/>
            </div>

            <div class="field-group">
                <label for="short-name"><g:message code="label.shortname"/></label>
                <input id="short-name" class="form-control" type="text" name="shortName" value="${shortName}"/>
            </div>

            <div class="field-group">
                <label for="frequency"><g:message code="dataset.frequency"/></label>
                <g:select class="form-control" id="frequency" name="frequency" from='${frequencies}'
                          value="${frequency}" optionKey="key" optionValue="value"></g:select>
            </div>

            <div class="field-group">
                <label for="allow-future-periods"><g:message code="dataset.allow.future.periods"/></label>
                <g:select class="form-control" id="allow-future-periods" name="openFuturePeriods"
                          from='${openFuturePeriodsMap}'
                          value="${openFuturePeriods}" optionKey="key" optionValue="value"></g:select>
            </div>

            <div class="field-group">
                <label for="org-unit-level"><g:message code="dataset.org.unit.level"/></label>
                <g:select class="form-control" id="org-unit-level" name="orgUnitLevel" from='${organisationUnitLevels}'
                          value="${orgUnitLevel}" optionKey="key" optionValue="value"></g:select>
            </div>

            <div class="field-group">
                <g:submitButton class="btn btn-primary" name="submit" value="${message(code: 'label.create')}"
                                onclick="submitForm(this);"/>
            </div>
        </g:form>
        <g:link controller="aggregateData" action="list">${message(code: "aggregate.listDatasetsLink")}</g:link>
    </div>
    <g:render template="../helpSidebar" model="[helpPartialName: 'helpCreate']"/>
</div>

<div class="loading">
    <div class="spinner"></div>

    <div class="loading-text">
        <g:message code="common.creating"/>
    </div>
</div>
</body>
</html>
