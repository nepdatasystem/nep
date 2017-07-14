/*
 * Copyright (c) 2014-2017. Institute for International Programs at Johns Hopkins University.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the NEP project, Institute for International Programs,
 * Johns Hopkins University nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package nep.springsecurity

import com.twopaths.dhis2.services.LoginService
import com.twopaths.dhis2.services.UserService
import nep.services.PropertiesService
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * A Custom Authentication Provider for this app, based on DHIS 2 authentication
 */
public class CustomAuthenticationProvider implements AuthenticationProvider {

    LoginService loginService
    UserService userService
    PropertiesService propertiesService

    def messageSource

    /**
     * Authenticates
     *
     * @param authentication authentication to authenticate
     * @return Authentication
     * @throws AuthenticationException
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName()
        String password = authentication.getCredentials().toString()

        // use the credentials to try to authenticate against the third party system
        if (authenticateAgainstDHIS2(username, password)) {

            // get the user roles
            List<GrantedAuthority> grantedAuths = getGrantedAuthorities(username, password)

            // Return auth token
            return new UsernamePasswordAuthenticationToken(username, password, grantedAuths)
        } else {
            throw new BadCredentialsException("Unable to auth against third party systems")
        }
    }

    /**
     * If the given class supports authentication
     *
     * @param authentication Authentication
     * @return if the given class supports authentication
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class)
    }

    /**
     * Authenticate against DHIS2
     *
     * @param username Username to authenticate
     * @param password Password for authentication
     * @return true/false if the user was authenticated
     */
    private def authenticateAgainstDHIS2(def username, def password) {
        loginService.authenticate(username, password)
    }


    /**
     * Get the user roles for the supplied username
     *
     * @param username Username to get granted authorities for
     * @param password Password to get granted authorities for
     * @return List<GrantedAuthority> list of user roles / GrantedAuthority
     */
    private def getGrantedAuthorities(def username, def password) {

        def grantedAuths = []

        def userRoles = userService.findUserRoles([username: username, password: password])

        def dhis2Roles = propertiesService.getProperties().get("nep.userRoles")?.split(",")

        def dhis2RolesMapping = [:]
        dhis2Roles.each { mapping ->
            def mappingSplit = mapping.split(":")
            dhis2RolesMapping << [(mappingSplit[1] as String): mappingSplit[0]]
        }
        log.debug("dhis2RolesMapping: " + dhis2RolesMapping)

        userRoles.each { userRole ->
            def role = dhis2RolesMapping[userRole]
            if (role) {
                grantedAuths << new SimpleGrantedAuthority(role)
            }
        }

        log.debug "grantedAuths: " + grantedAuths
        return grantedAuths
    }
}
