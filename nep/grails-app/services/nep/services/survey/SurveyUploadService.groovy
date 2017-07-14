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

package nep.services.survey

import grails.transaction.Transactional

import java.nio.file.Files

/**
 * Service to manage a Survey Upload
 */
@Transactional
class SurveyUploadService {

    def UPLOADS_FOLDER = "survey_uploads"
    def INFO_FILENAME = "uploads.properties"

    public static final String METADATA = "metadata"
    public static final String DATA = "data"
    public static final String ARCHIVE = "archive"
    public static final String OTHER = "other"

    def propertiesService

    /**
     * Save the file in a folder as follows: <NEP_HOME>/survey-uploads/<program id>
     * Files are saved using the program id / program stage id and a properties file
     * use to map the id to the filename
     *
     * @param programId Id of the program
     * @param programOrStageId Id of the program or program stage
     * @param programOrStageName Name of the program or program stage
     * @param programOrStageFile The file to save
     * @param fileType The type of file this is
     * @return
     */
    def saveFile(programId, programOrStageId, programOrStageName, programOrStageFile, fileType) {

        def nepHome = propertiesService.getNepHome()

        // Create the uploads folder if it does not exist
        createFolder(nepHome, UPLOADS_FOLDER)

        def uploadsFolder = "${nepHome}/${UPLOADS_FOLDER}"

        // Check that program folder exists, if not, create it
        createFolder(uploadsFolder, programId)

        // Copy/save the file
        copyUploadedFile(uploadsFolder, programId, programOrStageId, programOrStageFile, fileType)

        // Get uploads properties
        def uploadsProperties = getUploadsProperties(uploadsFolder, programId)

        // Set the property for this file
        uploadsProperties.setProperty("${fileType}.${programOrStageId}", programOrStageFile.name)

        // Save the uploads properties file
        saveUploadsProperties(uploadsFolder, programId, uploadsProperties)
    }

    /**
     * Save other files
     *
     * @param programId The id of the associated program
     * @param file The file to save
     * @return
     */
    def saveOtherFile(def programId, def file) {
        def nepHome = propertiesService.getNepHome()

        // Create the program folder if it does not exist
        createFolder(nepHome, UPLOADS_FOLDER)

        // Create the uploads folder if it does not exist
        createFolder(nepHome, UPLOADS_FOLDER)

        def uploadsFolder = "${nepHome}/${UPLOADS_FOLDER}"

        // Check that program folder exists, if not, create it
        createFolder(uploadsFolder, programId)

        def programFolder = "${uploadsFolder}/${programId}"

        // Create the other folder
        createFolder(programFolder, OTHER)

        def otherFolder = "${programFolder}/${OTHER}"

        // Save file to the OTHER folder
        def savedFile = new File(otherFolder, file.getOriginalFilename())
        file.transferTo(savedFile)
    }

    /**
     * Get the uploaded files for this program id
     *
     * @param programId The id of the program to get files for
     * @return The uploads found
     */
    def getUploads(def programId) {

        def nepHome = propertiesService.getNepHome()

        def uploadsFolder = "${nepHome}/${UPLOADS_FOLDER}"

        def properties = getUploadsProperties(uploadsFolder, programId)

        def uploadsData = [:]

        if (properties) {
            uploadsData << [uploadsFolder: uploadsFolder]

            def uploads = [:]
            def customSorter = { a, b, order=['metadata','data'] ->
                order.indexOf( a.substring(0, a.indexOf(".")) ) <=> order.indexOf( b.substring(0, b.indexOf(".")) )
            }
            def propertyNames = properties?.propertyNames().sort(customSorter)
            propertyNames.each { name ->
                uploads << [(name.toString()): properties.getProperty(name)]
            }
            uploadsData << [(programId): uploads]
        }

        return uploadsData
    }

    /**
     * Get other uploaded files for a program
     *
     * @param programId The id of the program to get other uploaded files for
     * @return Other uploaded file data if found
     */
    def getOtherUploads(def programId) {

        def nepHome = propertiesService.getNepHome()

        def uploadsFolder = "${nepHome}/${UPLOADS_FOLDER}"

        def otherUploadsData = [:]

        def programFolder = "${uploadsFolder}/${programId}"

        def otherFolder = "${programFolder}/${OTHER}"

        otherUploadsData << [otherFolder: otherFolder]

        def uploads = []
        File otherFolderFile = new File(otherFolder)
        if (otherFolderFile.exists()) {
            otherFolderFile.listFiles()?.each { file ->
                uploads << file.name
            }
            uploads.sort()
        }
        // Add the uploads
        otherUploadsData << [uploads: uploads]

        return otherUploadsData
    }

    /**
     * Creates a folder under the specified parent folder
     *
     * @param parentFolder The parent folder to create a folder under
     * @param folderName The name of the folder to create
     * @return
     */
    private def createFolder(def parentFolder, def folderName) {

        File folder = new File(parentFolder, folderName)
        if (!folder.exists()) {
            folder.mkdir()
        }
    }

    /**
     * Copies an uploaded file
     *
     * @param nepHome The home directory for this NEP instance
     * @param programId The id of the program
     * @param programOrStageId The id of the program or program stage
     * @param programOrStageFile The file to copy
     * @param fileType The type of file
     * @return
     */
    private def copyUploadedFile(def nepHome, def programId, def programOrStageId, def programOrStageFile, def fileType) {

        def savedFile = new File("${nepHome}/${programId}", "${fileType}.${programOrStageId}")

        // Delete the file if it exists
        if (savedFile.exists()) {
            savedFile.delete();
        }
        savedFile << programOrStageFile.text

        // Also save a timestamped copy of the file
        createFolder("${nepHome}/${programId}", ARCHIVE)
        def timestamp = new Date().format("yyyy-MM-dd-HH:mm:ss")
        def timestampedFile =  new File("${nepHome}/${programId}/${ARCHIVE}", "${fileType}.${programOrStageId}.${timestamp}")
        // Delete the file if it exists
        if (timestampedFile.exists()) {
            timestampedFile.delete()
        }
        timestampedFile << programOrStageFile.text
    }

    /**
     * Saves the uploads properties
     *
     * @param uploadsFolder The folder to save properties for
     * @param programId The id of the program
     * @param properties The properties to save
     * @return
     */
    private def saveUploadsProperties(def uploadsFolder, def programId, def properties) {

        File propertiesFile = new File("${uploadsFolder}/${programId}", INFO_FILENAME)

        properties.store(propertiesFile.newWriter("UTF-8"), null)
    }

    /**
     * Gets the uploads properties
     *
     * @param uploadsFolder The uploads folder
     * @param programId The id of the Program
     * @return The properties found
     */
    private def getUploadsProperties(def uploadsFolder, def programId) {

        def properties = new Properties()

        File propertiesFile = new File("${uploadsFolder}/${programId}", INFO_FILENAME)

        if (propertiesFile.exists()) {
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
}
