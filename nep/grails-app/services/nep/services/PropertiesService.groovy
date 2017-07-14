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

package nep.services

import grails.transaction.Transactional

/**
 * Service to manage instance-specific properties contained within the nep.properties file
 */
@Transactional
class PropertiesService {

    /**
     * Gets all the properties contained within the nep.properties file
     * @return all the properties contained within the nep.properties file
     */
    def getProperties() {

        def properties = new Properties()

        def nep_home = getNepHome()

        if (!nep_home) {
            log.error "!!! Please define System / Environment Property: NEP_HOME !!!"
        } else {
            File propertiesFile = new File(nep_home, 'nep.properties')

            InputStream inputStream = new FileInputStream(propertiesFile);
            try {
                Reader reader = new InputStreamReader(inputStream, "UTF-8");
                try {
                    properties.load(reader);
                } finally {
                    reader.close();
                }
            } finally {
               inputStream.close();
            }
        }

        return properties
    }

    /**
     * Returns the instance-specific nep home directory, which will contain the nep.properties file
     *
     * @return The instance-specific nep home directory, which will contain the nep.properties file
     */
    def getNepHome() {
        def nep_home = System.getProperty("NEP_HOME")

        if (!nep_home) {
            nep_home = System.getenv("NEP_HOME")
        }

        return nep_home
    }
}
