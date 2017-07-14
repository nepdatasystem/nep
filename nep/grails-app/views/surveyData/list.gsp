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

<%@ page import="nep.util.Utils" %>
<html>
<head>
<meta name="layout" content="nep"/>
<asset:stylesheet src="jQuery-File-Upload-9.11.2/css/jquery.fileupload.css"/>
<script type="text/javascript">
window.appContext = '${request.contextPath}';
function downloadFiles(programId, programName) {
    var select = $("select[name='"+programId+"']");
    var selectedIndex = select.prop("selectedIndex");
    var optionsLength = select.find("option").length;
    var value = select.val();
    if (selectedIndex === 0) {
        // do nothing
    } else if (value.indexOf("metadata-data:") === 0) { // metadata / data
        if (value.indexOf("metadata-data:all") === 0) {
            // download all metadata/data
            window.open(appContext + "/" + "surveyData/downloadData?type=all&programId=" + programId + "&programName=" + programName);
        } else {
            // download individual
            var individual = value.substring("metadata-data:".length);
            window.open(appContext + "/" + "surveyData/downloadData?type=individual&programId=" + programId + "&fileId=" + individual);
        }
    } else if (value.indexOf("other:") === 0) { // other
        if (value.indexOf("other:all") === 0) {
            // download all other
            window.open(appContext + "/" + "surveyData/downloadOtherData?type=all&programId=" + programId + "&programName=" + programName);
        } else {
            // download individual
            var individual = value.substring("other:".length);
            window.open(appContext + "/" + "surveyData/downloadOtherData?type=individual&programId=" + programId + "&fileId=" + individual);
        }
    }
}
</script>
</head>
<body>
    <div class="actions">
        <div class="actions-create">
            <h1><g:message code="survey.surveyData"/></h1>
            <g:render template="menu" />
        </div>
        <div class="actions-status">
            <g:render template="../checkStatusSurvey"/>
        </div>
    </div>
    <div class="ui-container">
        <div class="ui-main ui-full-width">
            <g:if test="${programs && programs?.size() != 0}">
            <h2><g:message code="survey.existingSurveys"/></h2>
            <table>
                <thead>
                    <tr>
                        <th><g:message code="survey.surveyName"/>
                            <span class="tooltip">
                                <span class="tooltip-content"><g:message code="survey.programName"/></span>
                            </span>
                        </th>
                        <th><g:message code="survey.databases"/>
                            <span class="tooltip">
                                <span class="tooltip-content"><g:message code="survey.programStages"/></span>
                            </span>
                        </th>
                        <th><g:message code="survey.user"/></th>
                        <th><g:message code="survey.created"/></th>
                        <th><g:message code="survey.metadata"/></th>
                        <th><g:message code="survey.data"/></th>
                        <th><g:message code="common.otherFiles"/></th>
                    </tr>
                </thead>
                <tbody>
                    <g:each in="${programs}" var="program">
                    <g:set var="hasMetadata" value="${programHasMetadata?.contains(program.id)}"/>
                    <g:set var="hasData" value="${programHasData?.contains(program.id)}"/>
                    <tr>
                        <td><strong>${program.name}</strong></td>
                        <td>
                            <g:each in="${program.programStages}" var="programStage">
                            <g:set var="hasMetadata" value="${hasMetadata || programStageHasMetadata?.contains(programStage.id)}"/>
                            <g:set var="hasData" value="${hasData || programStageHasData?.contains(programStage.id)}"/>
                            ${programStage.name}<br/>
                            </g:each>
                        <td>${program.user.name}</td>
                        <td>${Utils.getDateTimeFromData(program.created)}</td>
                        <td>
                            <g:if test="${hasMetadata}">
                            <g:link controller="surveyMetadata" action="upload" params="${[programId: program.id]}"><g:message code="common.reUpload"/></g:link>
                            </g:if>
                            <g:else>
                            <g:link controller="surveyMetadata" action="upload" params="${[programId: program.id]}"><g:message code="common.upload"/></g:link>
                            </g:else>
                        </td>
                        <td>
                            <g:if test="${hasMetadata}">
                            <g:if test="${hasData}">
                            <g:link controller="surveyData" action="upload" params="${[programId: program.id]}"><g:message code="common.reUpload"/></g:link>
                            </g:if>
                            <g:else>
                            <g:link controller="surveyData" action="upload" params="${[programId: program.id]}"><g:message code="common.upload"/></g:link>
                            </g:else>
                            </g:if>
                        </td>
                        <td class="repository">
                            <span class="btn btn-success fileinput-button">
                                <i class="icon-plus icon-white"></i>
                                <span><g:message code="common.upload"/></span>
                                <input class="fileupload" type="file" name="files[]" data-url="<g:createLink action="uploadFiles" params="[programId: program.id]"/>" multiple/>
                            </span>
                            <span class="btn btn-progress hidden">
                                <span class="progress">0%</span>
                            </span>
                            <g:if test="${uploadsData[(program.id)] || otherUploadsData[(program.id)]}">
                                <select class="form-control" name="${program.id}" onchange="downloadFiles('${program.id}', '${program.name}')">
                                    <option><g:message code="common.download"/></option>
                                    <optgroup label="<g:message code="common.metadataAndDataFiles"/>">
                                        <g:if test="${!uploadsData[(program.id)]}">
                                            <option disabled><g:message code="common.none"/></option>
                                        </g:if>
                                        <g:each in="${uploadsData[(program.id)]}" var="id, name">
                                            <g:if test="${id != 'uploadsFolder'}">
                                                <option value="metadata-data:${id}">${uploadsData[(program.id)].get((id))}</option>
                                            </g:if>
                                        </g:each>
                                        <g:if test="${uploadsData[(program.id)]}">
                                            <option value="metadata-data:all"><g:message code="common.allMetadataAndDataFiles"/></option>
                                        </g:if>
                                    </optgroup>

                                    <optgroup label="<g:message code="common.otherFiles"/>">
                                        <g:if test="${!otherUploadsData[(program.id)]}">
                                            <option disabled><g:message code="common.none"/></option>
                                        </g:if>
                                        <g:each in="${otherUploadsData[(program.id)]}" var="name">
                                            <option value="other:${name}">${name}</option>
                                        </g:each>
                                        <g:if test="${otherUploadsData[(program.id)]}">
                                            <option value="other:all"><g:message code="common.allOtherFiles"/></option>
                                        </g:if>
                                    </optgroup>
                                </select>
                            </g:if>
                        </td>
                    </tr>
                    </g:each>
                </tbody>
            </table>
            </g:if>
            <g:else>
            <h2><g:message code="survey.noSurveys"/></h2>
            </g:else>
        </div>
    </div>

<asset:javascript src="jQuery-File-Upload-9.11.2/js/vendor/jquery.ui.widget.js"/>
<asset:javascript src="jQuery-File-Upload-9.11.2/js/jquery.iframe-transport.js"/>
<asset:javascript src="jQuery-File-Upload-9.11.2/js/jquery.fileupload.js"/>
<script>
    $(function () {
        $('.fileupload').each(function() {
            $(this).fileupload({
                dropZone: $(this),
                dataType: 'json',
                start: function() { // start is called before all files are uploaded
                    // Hide the file input and show the progress
                    $(this).parent().hide();
                    $(this).parent().parent().find('.btn-progress').show();
                },
                progressall: function (e, data) { // progressall is called to show the upload progress for all files
                    // Calculate the progress
                    var progress = parseInt(data.loaded / data.total * 100, 10);
                    $(this).parent().parent().find('.progress').html(progress + '%');
                },
                done: function (e, data) { // done uploading all files
                    // Redirect to this page to refresh the dropdowns
                    window.location.href = "<g:createLink action="list"/>";
                }
            });
        });
    });
</script>
</body>
</html>
