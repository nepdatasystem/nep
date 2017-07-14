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

<%@ page import="org.springframework.web.servlet.support.RequestContextUtils" %>
<ul class="checklist">
    <li><g:message code="survey.data.file.csv.headings" args="[trackedEntityName ?: message(code: 'survey.respondent'), programFields.required]" /></li>
    <li><g:message code="survey.data.database.respondent.id.help" args="[trackedEntityName ?: message(code: 'survey.respondent'), trackedEntityName ?: message(code: 'survey.respondent')]" /></li>
    <li><g:message code="survey.data.file.csv.headings" args="[message(code: 'survey.database'), programStageFields.required]" /></li>
    <li><g:message code="survey.data.database.database.id.help" args="[message(code: 'survey.database'), trackedEntityName ?: message(code: 'survey.respondent'), message(code: 'survey.database')]" /></li>
    <li><g:message code="survey.data.hierarchy.help" args="[message(code: 'survey.database'), message(code: 'survey.database')]" /></li>
    <li><g:message code="survey.data.upload.order" args="[trackedEntityName ?: message(code: 'survey.respondent')]" /></li>
    <li>
        <a href="${exampleSurveyFilesLink}">
            <g:message code="survey.example.files.download.help" />
        </a>
    </li>
</ul>
