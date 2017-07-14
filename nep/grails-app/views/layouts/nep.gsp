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

<%@page import="org.springframework.web.servlet.support.RequestContextUtils"%>
<%@page import="com.twopaths.dhis2.api.ApiVersion"%>
<!DOCTYPE html>
<!--  lang:${RequestContextUtils.getLocale(request)} -->
<html>
    <head>
        <title><g:message code="nep" /></title>
        <link rel="apple-touch-icon" sizes="57x57" href="${resource(dir:'images',file:'apple-icon-57x57.png')}">
        <link rel="apple-touch-icon" sizes="60x60" href="${resource(dir:'images',file:'apple-icon-60x60.png')}">
        <link rel="apple-touch-icon" sizes="72x72" href="${resource(dir:'images',file:'apple-icon-72x72.png')}">
        <link rel="apple-touch-icon" sizes="76x76" href="${resource(dir:'images',file:'apple-icon-76x76.png')}">
        <link rel="apple-touch-icon" sizes="114x114" href="${resource(dir:'images',file:'apple-icon-114x114.png')}">
        <link rel="apple-touch-icon" sizes="120x120" href="${resource(dir:'images',file:'apple-icon-120x120.png')}">
        <link rel="apple-touch-icon" sizes="144x144" href="${resource(dir:'images',file:'apple-icon-144x144.png')}">
        <link rel="apple-touch-icon" sizes="152x152" href="${resource(dir:'images',file:'apple-icon-152x152.png')}">
        <link rel="apple-touch-icon" sizes="180x180" href="${resource(dir:'images',file:'apple-icon-180x180.png')}">
        <link rel="icon" type="image/png" sizes="192x192" href="${resource(dir:'images',file:'android-icon-192x192.png')}">
        <link rel="icon" type="image/png" sizes="32x32" href="${resource(dir:'images',file:'favicon-32x32.png')}">
        <link rel="icon" type="image/png" sizes="96x96" href="${resource(dir:'images',file:'favicon-96x96.png')}">
        <link rel="icon" type="image/png" sizes="16x16" href="${resource(dir:'images',file:'favicon-16x16.png')}">
        <link rel="manifest" href="/manifest.json">
        <meta name="msapplication-TileColor" content="#ffffff">
        <meta name="msapplication-TileImage" content="/ms-icon-144x144.png">
        <meta name="theme-color" content="#ffffff">
        <link rel="shortcut icon" href="${resource(dir:'images',file:'favicon.ico')}" type="image/x-icon" />
        <asset:stylesheet src="main.css"/>
        <script type="text/javascript">
        var contextPath = "${request.contextPath}";
        </script>
        <asset:javascript src="main.js"/>
        <g:layoutHead/>
    </head>
    <body>
        <div class="ui-wrap">
            <g:if test="${controllerName != 'login'}">
            <header role="header">
                <a class="logo" href="${request.contextPath}">
                    <asset:image src="NEP-logo.svg"/>
                </a>
                <sec:ifLoggedIn>
                <ul class="nav" role="nav">

                    <li><a href="${params.dhis2Url}"><g:message code="menu.back.to.dhis2" /></a></li>

                    <sec:ifAllGranted roles="ROLE_DATA_MANAGER">
                    <g:set var="surveyClass" value=""/>
                    <g:if test="${controllerName == 'surveyData' || controllerName == 'surveyMetadata'}">
                        <g:set var="surveyClass" value="active"/>
                    </g:if>
                    <li class="${surveyClass}">
                        <g:link controller="surveyData" action="list"><g:message code="survey.surveyData" /></g:link>
                    </li>

                    <g:set var="aggregateClass" value=""/>
                    <g:if test="${controllerName == 'aggregateData' || controllerName == 'aggregateMetadata'}">
                        <g:set var="aggregateClass" value="active"/>
                    </g:if>
                    <li class="${aggregateClass}">
                        <g:link controller="aggregateData" action="list"><g:message code="aggregate.data" /></g:link>
                    </li>
                    </sec:ifAllGranted>
                    <li>
                        <g:link controller="logout"><g:message code="login.logout"/></g:link>
                    </li>
                </ul>
                </sec:ifLoggedIn>
                <div class="version">NEP v-${grails.util.Metadata.current.getApplicationVersion()} |
                DHIS 2 API v-${ApiVersion.get(grailsApplication.config.dhis2.api.version)?.value() ?: ApiVersion.DHIS2_DEFAULT_VERSION.value()}</div>
            </header>
            </g:if>
            <g:layoutBody/>
        </div>
    </body>
</html>
