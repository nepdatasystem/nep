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

<%@ page import="org.springframework.batch.core.BatchStatus"%>
<html>
<head>
<meta name="layout" content="nep" />
<title></title>
</head>
<body>
<div class="actions short-spacing">
    <div class="actions-create">
        <ol class="breadcrumb">
            <li class="completed"><g:link controller="aggregateData" action="create"><g:message code="dataset.create"/></g:link></li>
            <li class="completed"><g:link controller="aggregateMetadata" action="index"><g:message code="aggregate.upload.metadata"/></g:link></li>
            <li class="completed"><g:link controller="aggregateMetadata" action="disaggregations"><g:message code="aggregate.upload.disaggregations"/></g:link></li>
            <li class="completed"><g:link controller="aggregateData" action="upload"><g:message code="aggregate.upload.data"/></g:link></li>
        </ol>
    </div>
    <div class="actions-status">
        <g:render template="../checkStatusAggregate"/>
    </div>
</div>
<div class="ui-container">
    <h1><g:message code="deletionErrors.title"/></h1>
    <div class="ui-main">
        <h2>${params.fileName}</h2>
        <g:if test="${importErrors}">
        <table>
            <thead>
            <tr>
                <th>${message(code: "importErrors.lineNumber")}</th>
                <th>${message(code: "importErrors.message")}</th>
            </tr>
            </thead>
            <g:each in="${importErrors}" var="jobExecutionError">
                <tr>
                    <td>${jobExecutionError.lineNumber}</td>
                    <td>${message(code: jobExecutionError.code, args: jobExecutionError.argsFromJson)}</td>
                </tr>
            </g:each>
        </table>
        </g:if>
        <g:else>
            ${message(code: "importErrors.noErrors")}
        </g:else>
        <div>
        <g:link action="deletionStatus" params="params">${message(code: "importErrors.back")}</g:link>
        </div>
    </div>
</div>
</body>
</html>
