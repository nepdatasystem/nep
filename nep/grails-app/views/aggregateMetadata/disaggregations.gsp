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

<%@ page import="grails.converters.JSON" %>
<html>
<head>
    <meta name="layout" content="nep"/>
    <g:javascript>
        function submitForm(button) {
            // spinner is modal so will prevent user from double-clicking
            showSpinner();
        }
        <g:applyCodec encodeAs="none">
            var dataSetIdsWithDisaggregations = ${dataSetIdsWithDisaggregations as JSON};
            var dataSetIdsWithMetadata = ${dataSetIdsWithMetadata as JSON};
        </g:applyCodec>

    </g:javascript>
</head>
<body>
<div class="actions">
    <div class="actions-create">
        <ol class="breadcrumb">
            <li class="completed"><g:link controller="aggregateData" action="create"><g:message code="dataset.create"/></g:link></li>
            <li class="completed"><g:link controller="aggregateMetadata" action="index"><g:message code="aggregate.upload.metadata"/></g:link></li>
            <li class="active"><g:link controller="aggregateMetadata" action="disaggregations"><g:message code="aggregate.upload.disaggregations"/></g:link></li>
            <li><g:link controller="aggregateData" action="upload"><g:message code="aggregate.upload.data"/></g:link></li>
        </ol>
    </div>
    <div class="actions-status">
        <g:render template="../checkStatusSurvey"/>
    </div>
</div>
<div class="ui-container">
    <h1><g:message code="aggregate.upload.disaggregations"/></h1>
    <div class="ui-main">
        <span class="optional"><g:message code="aggregate.skip.disaggregations.message"/></span>
        <g:render template="/messages" />
        <g:uploadForm controller="aggregateMetadata" action="uploadDisaggregations" method="POST" name="upload-dataset-disaggregations-form">
            <div class="field-group">
                <label for="data-set-disaggregations"><g:message code="aggregate.dataset"/></label>
                <g:select class="form-control" id="data-set-disaggregations" name="dataSetId" from='${dataSets}'
                          value="${params?.dataSetId}" optionKey="id" optionValue="name" noSelection="${['':message(code:'label.select')]}"></g:select>
            </div>
            <div id="data-set-disaggregations-select-dataset-info"><g:message code="aggregate.select.dataset"/></div>
            <div id="data-set-disaggregations-requires-metadata">
                <div class="field-group" id="data-set-disaggregations-re-upload-info">
                    <g:message code="aggregate.disaggregations.already.uploaded"/>
                </div>
                <div class="field-group">
                    <label for="disaggregations-file"><g:message code="aggregate.disaggregation.file"/></label>
                    <input id="disaggregations-file" type="file" name="disaggregationsFile" />
                </div>
                <div class="field-group">
                    <g:submitButton class="btn" name="submit" value="${message(code:'label.upload')}" onclick="submitForm(this);"/>
                    <g:link elementId="aggregate-disaggregations-skip-link" class="btn btn-secondary" controller="aggregateData" action="upload" params="[dataSetId : params.dataSetId]"><g:message code="aggregate.skip.disaggregations"/></g:link>
                </div>
            </div>
            <div id="data-set-disaggregations-requires-metadata-message">
                <div class="field-group">
                    <g:message code="aggregate.disaggregations.requires.metadata"></g:message>
                </div>
                <div class="field-group">
                    <g:link elementId="aggregate-metadata-link" class="btn btn-secondary" controller="aggregateMetadata" action="index" params="[dataSetId : params.dataSetId]"><g:message code="aggregate.upload.metadata"/></g:link>
                </div>
            </div>

        </g:uploadForm>
        <g:link controller="aggregateData" action="list">${message(code:"aggregate.listDatasetsLink")}</g:link>
    </div>

    <g:render template="../helpSidebar" model="[helpPartialName :'helpDisaggregations']"/>
</div>
<div class="loading">
    <div class="spinner"></div>
    <div class="loading-text">
        <g:message code="common.file.uploadInProgress"/>
    </div>
</div>
<g:javascript>
    $( document ).ready(function() {
        $('#data-set-disaggregations').trigger("change");
    });
</g:javascript>
</body>
</html>
