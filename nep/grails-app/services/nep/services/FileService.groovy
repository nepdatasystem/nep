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

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Service to manage Files
 */
@Transactional
class FileService {

    /**
     * Builds a zip file from the specified csv files into the specified folder
     *
     * @param uploadsFolder The uploads folder to use for the zip file
     * @param inputCsvs The csv files to include in the zip file
     * @return The zip file
     */
    def buildZipFile(def uploadsFolder, def inputCsvs) {
        //println inputCsvs
        ByteArrayOutputStream bos = new ByteArrayOutputStream()
        ZipOutputStream zipData = new ZipOutputStream(new BufferedOutputStream(bos))
        inputCsvs.each() { it ->
            log.info " ... adding ${it.name}"
            zipData.putNextEntry(new ZipEntry(it.name))
            zipData.write(it.bytes, 0, it.bytes.size())
            zipData.closeEntry()
        }

        zipData.close()
        return bos.toByteArray()
    }
}
