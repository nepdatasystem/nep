<html>
<head>
<meta name="layout" content="nep"/>
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

<asset:stylesheet src="jQuery-File-Upload-9.11.2/css/jquery.fileupload.css"/>
<script type="text/javascript">
window.appContext = '${request.contextPath}';
function downloadFiles(dataSetId, dataSetName) {
    var select = $("select[name='" + dataSetId + "']");
    var selectedIndex = select.prop("selectedIndex");
    var optionsLength = select.find("option").length;
    var value = select.val();
    if (selectedIndex === 0) {
        // do nothing
    } else if (value.indexOf("metadata-data:") === 0) { // metadata / data
        if (value.indexOf("metadata-data:all") === 0) {
            // download all metadata / data
            window.open(appContext + "/" + "aggregateData/downloadData?type=all&dataSetId=" + dataSetId + "&dataSetName=" + dataSetName);
        } else {
            // download individual
            var individual = value.substring("metadata-data:".length);
            window.open(appContext + "/" + "surveyData/downloadData?type=individual&dataSetId=" + dataSetId + "&fileId=" + individual);
        }
    } else if (value.indexOf("other:") === 0) { // other
        if (value.indexOf("other:all") === 0) {
            // download all other
            window.open(appContext + "/" + "aggregateData/downloadOtherData?type=all&dataSetId=" + dataSetId + "&dataSetName=" + dataSetName);
        } else {
            // download individual
            var individual = value.substring("other:".length);
            window.open(appContext + "/" + "aggregateData/downloadOtherData?type=individual&dataSetId=" + dataSetId + "&fileId=" + individual);
        }
    }
}
</script>
</head>
<body>
<div class="actions">
    <div class="actions-create">
        <h1><g:message code="aggregate.data" /></h1>
        <g:render template="/messages" />
        <g:render template="menu" />
    </div>
    <div class="actions-status">
        <g:render template="../checkStatusAggregate"/>
    </div>
</div>
<div class="ui-container">
    <div class="ui-main ui-full-width">
        <g:if test="${dataSets && dataSets?.size() != 0}">
            <h2><g:message code="aggregate.datasets.list"/></h2>
            <table>
                <thead>
                <tr>
                    <th><g:message code="dataset.name"/></th>
                    <th><g:message code="label.shortname"/></th>
                    <th><g:message code="dataset.user"/></th>
                    <th><g:message code="dataset.created.time"/></th>
                    <!--<th><g:message code="dataset.updated"/></th>-->
                    <th><g:message code="dataset.metadata"/></th>
                    <th><g:message code="dataset.disaggregations"/></th>
                    <th><g:message code="dataset.data"/></th>
                    <th><g:message code="common.otherFiles"/></th>
                    <th><g:message code="common.delete" /></th>
                </tr>
                </thead>
                <tbody>
                <g:each in="${dataSets}" var="dataSet">
                    <tr>
                        <td>${dataSet.name}</td>
                        <td>${dataSet.shortName}</td>
                        <td>${dataSet.user.name}</td>
                        <td>${dataSet.created}</td>
                        <!--<td>${dataSet.lastUpdated}</td>-->
                        <td>
                            <g:link controller="aggregateMetadata" action="index" params="[dataSetId : dataSet.id]">
                                <g:if test="${dataSet.hasMetadata}">
                                    <g:message code="common.reUpload" />
                                </g:if>
                                <g:else>
                                    <g:message code="common.upload" />
                                </g:else>
                            </g:link>
                        </td>
                        <td>
                            <g:if test="${dataSet.hasMetadata}">
                                <g:link controller="aggregateMetadata" action="disaggregations" params="[dataSetId : dataSet.id]">
                                    <g:if test="${dataSet.hasDisaggregations}">
                                        <g:message code="common.reUpload" />
                                    </g:if>
                                    <g:else>
                                        <g:message code="common.upload" />
                                    </g:else>
                                </g:link>
                            </g:if>
                        </td>
                        <td>
                            <g:if test="${dataSet.hasMetadata}">
                                <g:link controller="aggregateData" action="upload" params="[dataSetId : dataSet.id]">
                                    <g:if test="${dataSet.hasData}">
                                        <g:message code="common.reUpload" />
                                    </g:if>
                                    <g:else>
                                        <g:message code="common.upload" />
                                    </g:else>

                                </g:link>
                            </g:if>
                        </td>
                        <td class="repository">
                            <span class="btn btn-success fileinput-button">
                                <i class="icon-plus icon-white"></i>
                                <span><g:message code="common.upload"/></span>
                                <input class="fileupload" type="file" name="files[]" data-url="<g:createLink action="uploadFiles" params="[dataSetId: dataSet.id]"/>" multiple/>
                            </span>
                            <span class="btn btn-progress hidden">
                                <span class="progress">0%</span>
                            </span>
                            <g:if test="${uploadsData[(dataSet.id)] || otherUploadsData[(dataSet.id)]}">
                                <select class="form-control" name="${dataSet.id}" onchange="downloadFiles('${dataSet.id}', '${dataSet.name}')">
                                    <option><g:message code="common.download"/></option>
                                    <optgroup label="<g:message code="common.metadataAndDataFiles"/>">
                                        <g:if test="${!uploadsData[(dataSet.id)]}">
                                            <option disabled><g:message code="common.none"/></option>
                                        </g:if>
                                        <g:each in="${uploadsData[(dataSet.id)]}" var="id, name">
                                            <g:if test="${id != 'uploadsFolder'}">
                                                <option value="metadata-data:${id}">${uploadsData[(dataSet.id)].get((id))}</option>
                                            </g:if>
                                        </g:each>
                                        <g:if test="${uploadsData[(dataSet.id)]}">
                                            <option value="metadata-data:all"><g:message code="common.allMetadataAndDataFiles"/></option>
                                        </g:if>
                                    </optgroup>

                                    <optgroup label="<g:message code="common.otherFiles"/>">
                                        <g:if test="${!otherUploadsData[(dataSet.id)]}">
                                            <option disabled><g:message code="common.none"/></option>
                                        </g:if>
                                        <g:each in="${otherUploadsData[(dataSet.id)]}" var="name">
                                            <option value="other:${name}">${name}</option>
                                        </g:each>
                                        <g:if test="${otherUploadsData[(dataSet.id)]}">
                                            <option value="other:all"><g:message code="common.allOtherFiles"/></option>
                                        </g:if>
                                    </optgroup>
                                </select>
                            </g:if>
                        </td>
                        <td>
                            <div class="dropdown">
                                <button class="btn btn-secondary" data-trigger="dropdown">
                                    <g:message code="common.delete" /><span class="caret">&#9660;</span>
                                </button>
                                <div class="dropdown-menu">
                                    <input type="hidden" name="dataSetId" value="${dataSet.id}" data-trigger="dataSetId"/>
                                    <input type="hidden" name="dataSetName" value="${dataSet.name}" data-trigger="dataSetName"/>
                                    <div class="form-check">
                                      <label class="form-check-label">
                                        <input class="form-check-input" data-trigger="dataSet" type="checkbox" value="">
                                          <g:message code="dataset.delete" />
                                      </label>
                                    </div>
                                    <div class="form-check">
                                      <label class="form-check-label">
                                        <input class="form-check-input" data-trigger="dataSet-metadata" type="checkbox" value="">
                                          <g:message code="dataset.delete.metadata" />
                                      </label>
                                    </div>
                                    <div class="form-check">
                                      <label class="form-check-label">
                                        <input class="form-check-input" data-trigger="dataSet-data" type="checkbox" value="">
                                          <g:message code="dataset.delete.data" />
                                      </label>
                                    </div>
                                    <div class="dropdown-actions">
                                        <a class="dropdown-close" data-trigger="dropdown" href="#"><i class="icon-close"></i> <g:message code="common.close" /></a>
                                        <a class="dropdown-delete is-disabled" data-trigger="modal" href="#"><i class="icon-delete"></i> <g:message code="common.delete" /></a>
                                    </div>
                                </div>
                            </div>
                        </td>
                    </tr>
                </g:each>
                </tbody>
            </table>
        </g:if>
        <g:else>
            <h2><g:message code="dataset.noDataSets"/></h2>
        </g:else>
    </div>

    <div class="modal" data-item="modal">
        <div class="modal__inner">
            <div class="close" data-trigger="close"></div>
            <div class="modal__content">
                <h2><g:message code="aggregate.dataset.deletion.warning.title" /> - <span id="dataSetNameModal"></span></h2>
                <h3 class="modal-warning"><i class="icon-warning"></i><g:message code="common.warning" /></h3>
                <div class="alert-warning" data-warning="dataSet" style="display:none">
                    <p><span class="modal-warning__heading"><g:message code="aggregate.dataset.deletion.warning.dataset.intro" /></span>
                        <ul>
                            <li><g:message code="aggregate.dataset" /></li>
                        </ul>
                     </p>
                </div>
                <div class="alert-warning" data-warning="dataSet-metadata" style="display:none">
                    <p><span class="modal-warning__heading"><g:message code="aggregate.dataset.deletion.warning.dataset.metadata.intro" /></span>
                        <ul>
                            <li><g:message code="aggregate.dataset.deletion.warning.dataset.metadata.list.indicators" /></li>
                            <li><g:message code="aggregate.dataset.deletion.warning.dataset.metadata.list.data.set.elements" /></li>
                            <li><g:message code="aggregate.dataset.deletion.warning.dataset.metadata.list.data.elements" /></li>
                            <li><g:message code="aggregate.dataset.deletion.warning.dataset.metadata.list.data.element.category.combos" /></li>
                        </ul>
                    </p>
                </div>
                <div class="alert-warning" data-warning="dataSet-data" style="display:none">
                    <p><span class="modal-warning__heading"><g:message code="aggregate.dataset.deletion.warning.dataset.data.intro" /></span>
                        <ul>
                            <li><g:message code="aggregate.dataset.deletion.warning.dataset.data.list.data.values" /></li>
                        </ul>
                    </p>
                </div>
                <div>
                    <p class="modal-warning"><strong><g:message code="common.deletion.warning.cannot.undo" /></strong></p>
                    <p><g:message code="common.deletion.warning.confirm" /></p>
                </div>
                <g:form url="[action: 'delete', controller: 'aggregateData']">
                    <input type="hidden" name="dataSetId" data-trigger="dataSetId"/>
                    <input type="hidden" name="dataSetName" data-trigger="dataSetName"/>
                    <input type="hidden" name="deleteType" data-trigger="deleteType"/>
                    <button class="btn btn-primary" onclick="this.disabled=true;showSpinner();this.parentNode.submit();"><g:message code="common.delete" /></button>
                    <button class="btn btn-secondary" data-trigger="close" onclick="return false;"><g:message code="common.cancel" /></button>
                </g:form>
            </div>
        </div>
    </div>

    <div class="modal__overlay"></div>

    <div class="loading">
        <div class="spinner"></div>
        <div class="loading-text">
            <g:message code="common.delete.in.progress"/>
        </div>
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
                    console.debug("done");
                    // Redirect to this page to refresh the dropdowns
                    window.location.href = "<g:createLink action="list"/>";
                }
            });
        });
    });
</script>
</body>
</html>
