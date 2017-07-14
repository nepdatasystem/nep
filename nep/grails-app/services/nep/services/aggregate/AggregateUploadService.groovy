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

package nep.services.aggregate

import grails.transaction.Transactional
import org.springframework.web.multipart.commons.CommonsMultipartFile

/**
 * Service to handle the Aggregate Data File Uploads.
 * These are the original files uploaded to the system that will be saved on the file system for reference and retrieval.
 */
@Transactional
class AggregateUploadService {

    def UPLOADS_FOLDER = "aggregate_uploads"
    def INFO_FILENAME = "uploads.properties"

    public static final String METADATA = "metadata"
    public static final String DISAGGREGATIONS = "disaggregations"
    public static final String DATA = "data"
    public static final String ARCHIVE = "archive"
    public static final String OTHER = "other"

    def propertiesService

    /**
     * Save the file in a folder as follows: <NEP_HOME>/survey-uploads/<data set id>
     * Files are saved using the program id / program stage id and a properties file
     * use to map the id to the filename
     *
     * @param dataSetId The id of the data set to save the file for
     * @param dataSetName The name of the data set to save the file for
     * @param file The file to save
     * @param fileType The type of the file to save
     * @return if the save was successful
     */
    def saveFile(dataSetId, file, fileType) {

        def nepHome = propertiesService.getNepHome()

        // Create the uploads folder if it does not exist
        createFolder(nepHome, UPLOADS_FOLDER)

        def uploadsFolder = "${nepHome}/${UPLOADS_FOLDER}"

        // Check that program folder exists, if not, create it
        createFolder(uploadsFolder, dataSetId)

        // Copy/save the file
        copyUploadedFile(uploadsFolder, dataSetId, file, fileType)

        // Get uploads properties
        def uploadsProperties = getUploadsProperties(uploadsFolder, dataSetId)

        // Set the property for this file
        uploadsProperties.setProperty("${fileType}.${dataSetId}", file.originalFilename)

        // Save the uploads properties file
        saveUploadsProperties(uploadsFolder, dataSetId, uploadsProperties)

        return true
    }

    /**
     * Saves a file that was not automatically generated but was uploaded by a user
     *
     * @param dataSetId The id of the dataset to upload the file to
     * @param file The file to upload
     * @return If the save was successful or not
     */
    def saveOtherFile(def dataSetId, def file) {
        def nepHome = propertiesService.getNepHome()

        // Create the program folder if it does not exist
        createFolder(nepHome, UPLOADS_FOLDER)

        // Create the uploads folder if it does not exist
        createFolder(nepHome, UPLOADS_FOLDER)

        def uploadsFolder = "${nepHome}/${UPLOADS_FOLDER}"

        // Check that dataSEt folder exists, if not, create it
        createFolder(uploadsFolder, dataSetId)

        def dataSetFolder = "${uploadsFolder}/${dataSetId}"

        // Create the other folder
        createFolder(dataSetFolder, OTHER)

        def otherFolder = "${dataSetFolder}/${OTHER}"

        // Save file to the OTHER folder
        def savedFile = new File(otherFolder, file.getOriginalFilename())
        file.transferTo(savedFile)

        return true
    }

    /**
     * Get the uploaded files for this data set id
     *
     * @param dataSetId The id of the data set to get uploaded files for
     * @return The uploaded files for this data set
     */
    def getUploads(def dataSetId) {

        def nepHome = propertiesService.getNepHome()

        def uploadsFolder = "${nepHome}/${UPLOADS_FOLDER}"

        def properties = getUploadsProperties(uploadsFolder, dataSetId)

        def uploadsData = [:]

        if (properties) {
            uploadsData << [uploadsFolder: uploadsFolder]

            def uploads = [:]
            def customSorter = { a, b, order=['metadata', 'disaggregations', 'data'] ->
                order.indexOf( a.substring(0, a.indexOf(".")) ) <=> order.indexOf( b.substring(0, b.indexOf(".")) )
            }
            def propertyNames = properties?.propertyNames().sort(customSorter)
            propertyNames.each { name ->
                uploads << [(name.toString()): properties.getProperty(name)]
            }
            uploadsData << [(dataSetId): uploads]
        }

        return uploadsData
    }

    /**
     * Get other uploaded files for a dataSet
     *
     * @param dataSetId The id of the dataset to retrieve uploaded files for
     * @return Other uploaded files for this data set
     */
    def getOtherUploads(def dataSetId) {

        def nepHome = propertiesService.getNepHome()

        def uploadsFolder = "${nepHome}/${UPLOADS_FOLDER}"

        def otherUploadsData = [:]

        def dataSetFolder = "${uploadsFolder}/${dataSetId}"

        def otherFolder = "${dataSetFolder}/${OTHER}"

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
     * Creates a subfolder with the specified name under the specified parent folder
     *
     * @param parentFolder The path to the parent folder to create a new folder under
     * @param folderName The name of the new folder to create
     * @return If the creation was successful
     */
    private def createFolder(def parentFolder, def folderName) {

        File folder = new File(parentFolder, folderName)
        if (!folder.exists()) {
            folder.mkdir()
        }
        return true
    }

    /**
     * Saves then copies the specified file with a timestampted copy for versioning
     *
     * @param nepHome The path to the nep home folder
     * @param dataSetId The id of the dataset to copy this file for
     * @param file The file to copy
     * @param fileType The type of the file to copy
     * @return if the copy was successful
     */
    private def copyUploadedFile(def nepHome, def dataSetId, CommonsMultipartFile file, def fileType) {

        def savedFile = new File("${nepHome}/${dataSetId}", "${fileType}.${dataSetId}")
        savedFile << file.bytes

        // Also save a timestamped copy of the file
        createFolder("${nepHome}/${dataSetId}", ARCHIVE)
        def timestamp = new Date().format("yyyy-MM-dd-HH:mm:ss")
        def timestampedFile =  new File("${nepHome}/${dataSetId}/${ARCHIVE}", "${fileType}.${dataSetId}.${timestamp}")
        timestampedFile << file.bytes

        return true
    }

    /**
     * Saves the upload properties to the appropriate folder for the specified data set
     *
     * @param uploadsFolder The folder for uploads
     * @param dataSetId The id of the dataset to save the properties for
     * @param properties The properties to save
     * @return If the save was successful or not
     */
    private def saveUploadsProperties(def uploadsFolder, def dataSetId, def properties) {

        File propertiesFile = new File("${uploadsFolder}/${dataSetId}", INFO_FILENAME)

        properties.store(propertiesFile.newWriter("UTF-8"), null)

        return true
    }

    /**
     * Gets the uploads properties for the specified data set
     *
     * @param uploadsFolder The upload properties folder to find the properties for
     * @param dataSetId The id of the data set to get the uploads properties for
     * @return The properties found
     */
    private def getUploadsProperties(def uploadsFolder, def dataSetId) {

        def properties = new Properties()

        File propertiesFile = new File("${uploadsFolder}/${dataSetId}", INFO_FILENAME)

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
