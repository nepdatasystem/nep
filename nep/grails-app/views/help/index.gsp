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
</head>
    <body>
        <div class="ui-main is-short">
            <h1><g:message code="help.contextual.title" /></h1>
            <h2><g:message code="survey.surveyData" /></h2>
            <h3><g:message code="survey.createSurvey" /></h3>
            <g:render template="../surveyData/helpCreate"/>
            <h3><g:message code="survey.uploadMetadata" /></h3>
            <g:render template="../surveyMetadata/helpUpload"/>
            <h3><g:message code="survey.uploadData" /></h3>
            <g:render template="../surveyData/helpUpload"/>
            <h2><g:message code="aggregate.data" /></h2>
            <h3><g:message code="dataset.create" /></h3>
            <g:render template="../aggregateData/helpCreate"/>
            <h3><g:message code="aggregate.upload.metadata" /></h3>
            <g:render template="../aggregateMetadata/helpMetadata"/>
            <h3><g:message code="aggregate.upload.disaggregations" /></h3>
            <g:render template="../aggregateMetadata/helpDisaggregations"/>
            <h3><g:message code="aggregate.upload.data" /></h3>
            <g:render template="../aggregateData/helpUpload"/>
        </div>
    </body>
</html>
