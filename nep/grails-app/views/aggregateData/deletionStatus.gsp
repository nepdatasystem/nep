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
    <h1><g:message code="dataSet.deletionStatus.title"/></h1>
    <div class="ui-main ui-full-width">
        <g:render template="/messages" />

        <g:if test="${dataSetDeletionJobExecutions}">
            <table>
                <thead>
                    <tr>
                        <th>${message(code: "importStatus.dataSetName")}</th>
                        <th>${message(code: "importStatus.startDate")}</th>
                        <th>${message(code: "importStatus.endData")}</th>
                        <th>${message(code: "importStatus.status")}</th>
                        <th>${message(code: "importStatus.deletionStep")}</th>
                        <th>${message(code: "importStatus.processedItems")}</th>
                        <th>${message(code: "importStatus.errors")}</th>
                        <th>${message(code: "importStatus.action")}</th>
                    </tr>
                </thead>

                <g:each in="${dataSetDeletionJobExecutions}" var="execution">
                    <tr class="${execution.jobExecutionId == flash?.jobExecution?.id ? 'active' : ''}">
                        <td>
                            <strong>${execution.dataSetId}</strong>
                        </td>
                        <td>
                            <g:formatDate format="dd/MM/yyyy h:mm a" date="${execution.startTime}"/>
                        </td>
                        <td>
                            <g:formatDate format="dd/MM/yyyy h:mm a" date="${execution.endTime}"/>
                        </td>
                        <td>
                            <g:set var="status" value="${(execution.status as String).toLowerCase()}"/>
                            <span class="upload-${status}">
                            ${message(code: "importStatus." + status)}
                            </span>
                        </td>
                        <td>
                            ${execution.fileName}
                        </td>
                        <td></td>
                        <td></td>
                        <td>
                            <g:if test="${execution.status==org.springframework.batch.core.BatchStatus.STARTED}">
                            <div class="upload-stop">
                                <g:link controller="aggregateData" action="deletionStop" params="[jobExecutionId:execution.jobExecutionId]">${message(code: "importStatus.stop")}</g:link>
                            </div>
                            </g:if>
                            <g:if test="${execution.status==org.springframework.batch.core.BatchStatus.COMPLETED || execution.status==org.springframework.batch.core.BatchStatus.FAILED || execution.status==org.springframework.batch.core.BatchStatus.STOPPED || execution.status==org.springframework.batch.core.BatchStatus.STOPPING}">
                            <div class="upload-remove">
                                <g:link controller="aggregateData" action="deletionRemove" params="[jobExecutionId:execution.jobExecutionId]">${message(code: "importStatus.remove")}</g:link>
                            </div>
                            </g:if>
                        </td>
                    </tr>
                    <g:each in="${execution.steps}" var="step">
                    <g:if test="${step.totalCount > 0}">
                    <tr class="${execution.jobExecutionId == flash?.jobExecution?.id ? 'active' : ''}">
                        <td></td>
                        <td></td>
                        <td></td>
                        <td></td>
                        <td>${step.fileName}</td>
                        <td><strong>${step.processedCount}</strong> ${message(code: "importStatus.of")} <strong>${step.totalCount}</strong></td>
                        <td>
                            <g:link controller="aggregateData" action="deletionErrors" params="[fileName: step.fileName, jobExecutionId:execution.jobExecutionId, stepExecutionId:step.stepExecutionId]">
                            %{-- ${message(code: "importStatus.errors")}:  --}%
                            ${step.errorCount}</g:link>
                        </td>
                        <td></td>
                    </tr>
                    </g:if>
                    </g:each>
                </g:each>
            </table>
        </g:if>
        <g:else>
            ${message(code: "importStatus.noDataSetDeletionJobs")}
        </g:else>
        <div>
        <div class="import-refresh"><i class="icon-sync"></i><g:link action="deletionStatus" params="params">${message(code: "importStatus.refresh")}</g:link></div>
        <g:link controller="aggregateData" action="list">${message(code:"aggregate.datasets.list")}</g:link>
        </div>
    </div>
</div>

</body>
</html>
