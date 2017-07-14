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
<meta name="layout" content="nep" />
<title><g:message code="login.denied.title" /></title>
</head>

<body>
    <div class="ui-main login">
        <a class="logo" href="${request.contextPath}">
            <asset:image src="NEP-logo.svg"/>
        </a>
        <div class="alert alert-danger">
            <strong class='fheader'>
                <g:message code="login.denied.title" />
            </strong>

            <g:if test='${flash.message}'>
                <div class='login_message'>
                    ${flash.message}
                </div>
            </g:if>

            <div class='errors'>
                <g:message code="login.denied.message" />
            </div>
        </div>
        <ul role="nav">

            <li><a href="${params.dhis2Url}"><g:message code="menu.back.to.dhis2" /></a></li>
            <li>
                <g:link controller="logout"><g:message code="login.logout"/></g:link>
            </li>
        </ul>
    </div>
<script type='text/javascript'>
<!--
(function() {
	document.forms['loginForm'].elements['j_username'].focus();
})();
// -->
s</script>
</body>
</html>
