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

import com.twopaths.dhis2.api.ApiMergeMode
import grails.transaction.Transactional
import nep.util.Utils

/**
 * Service to manage Metadata for Aggregate Data
 */
@Transactional
class AggregateMetadataService {

    final DOMAIN_TYPE = 'AGGREGATE'
    final DIMENSION_TYPE = 'DISAGGREGATION'

    final static String DATE = "DATE"
    final static String TEXT = "TEXT"
    final static String INTEGER = "INTEGER"
    final static String INTEGER_POSITIVE = "INTEGER_POSITIVE"
    final static String INTEGER_ZERO_OR_POSITIVE = "INTEGER_ZERO_OR_POSITIVE"
    final static String NUMBER = "NUMBER"
    final static String SUM = "SUM"

    def categoryService
    def categoryOptionService
    def categoryComboService
    def categoryOptionComboService

    def dataElementService
    def dataSetService

    def propertiesService

    /**
     * Processes a row from the aggregate data disaggregations import csv file for processing (upload / import)
     *
     * @param auth DHIS 2 Credentials
     * @param fileInputStream The input stream of the disaggregations file to process
     * @param dataSetId The id of the dataset to process disaggregations for
     * @return errors if any from the processing
     */
    def processDissagregationsFile(def auth, def fileInputStream, def dataSetId) {

        def csvHeadings = getDisaggregationColumnHeadings()

        def reader = new InputStreamReader(fileInputStream).toCsvMapReader()

        def dataSet = dataSetService.get(auth, dataSetId)

        def errors = []

        reader.eachWithIndex {row, idx ->
            errors.addAll(processDisaggregation(auth, row, idx+1, dataSet, csvHeadings))
        }
        return errors
    }

    /**
     * Processes a row from the aggregate metadata import csv file for processing (upload / import)
     *
     * @param auth DHIS 2 Credentials
     * @param fileInputStream The input stream of the metadata file to process
     * @param dataSetId The id of the dataset to process disaggregations for
     * @return errors if any from the processing
     */
    def processMetadataFile(def auth, def fileInputStream, def dataSetId) {

        def csvHeadings = getAllMetadataColumnHeadings()

        def reader = new InputStreamReader(fileInputStream).toCsvMapReader()

        def dataSet = dataSetService.get(auth, dataSetId)

        def errors = []

        reader.eachWithIndex {row, idx ->
            errors.addAll(processMetadata(auth, row, idx+1, dataSet, csvHeadings))
        }
        return errors
    }

    /**
     * Processes a row from the aggregate data disaggregations import csv file for processing (upload / import)
     *
     * @param auth DHIS 2 Credentials
     * @param rowData The row of data from the csv file
     * @param rowNum The row number of this row of data
     * @param dataSet The data set that these disaggregations will be imported into
     * @param csvHeadings The csv headings configured for the disaggregations import csv for reference from the row data map
     * @return errors from the process if any
     */
    private def processDisaggregation(def auth, def rowData, def rowNum, def dataSet, def csvHeadings) {

        def errors = []

        // Update row of data to case insensitive map
        Map<String, String> row = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        row.putAll(rowData);

        try {

            def dataElementName = row[csvHeadings["DISAGGREGATION_DATA_ELEMENT_CSV_HEADING"]]
            def categoryComboName = row[csvHeadings["DISAGGREGATION_CATEGORY_COMBO_CSV_HEADING"]]
            def categoryName = row[csvHeadings["DISAGGREGATION_CATEGORY_CSV_HEADING"]]
            def categoryOptionName = row[csvHeadings["DISAGGREGATION_CATEGORY_OPTION_CSV_HEADING"]]

            // find data element
            if (!dataElementName?.trim()) {
                log.warn("data element name not supplied for row ${row}")
                errors << [code: "aggregate.disaggregations.dataElement.name.empty", args: [rowNum, row.values().join(',')]]
                return errors
            } else {
                dataElementName = dataElementName.trim()
            }
            def dataElement = dataElementService.findByName(
                    auth,
                    Utils.addPrefix(dataSet.name, dataElementName),
                    [dataElementService.FIELD_CATEGORY_COMBO])
            if (dataElement == null) {
                log.warn("data element [${dataElementName}] not found for row ${row}")
                errors << [code: "aggregate.disaggregations.dataElement.name.notFound", args: [rowNum, dataElementName]]
                return errors
            }

            // Create the category option if it does not exist
            def categoryOptionId = categoryOptionService.findByName(auth, categoryOptionName)?.id
            if (categoryOptionId == null) {
                def categoryOptionResult = categoryOptionService.create(auth, ["name": categoryOptionName, "shortName": categoryOptionName, "code": categoryOptionName])
                if (categoryOptionResult?.success) {
                    categoryOptionId = categoryOptionResult.lastImported
                } else {
                    def details = "-"
                    if (categoryOptionResult?.conflicts) {
                        details = categoryOptionResult.conflicts.collect { it.value }.join(', ')
                    }
                    errors << [code: "aggregate.disaggregations.categoryOption.create.error", args: [rowNum, categoryOptionName, dataElementName, details]]
                    return errors
                }
            }

            // Create the category if it does not exist
            def categoryId = categoryService.findByName(auth, categoryName)?.id
            if (categoryId == null) {
                def categoryResult = categoryService.create(auth, ["name": categoryName, "code": categoryName, "dataDimensionType":DIMENSION_TYPE])
                if (categoryResult?.success) {
                    categoryId = categoryResult.lastImported
                } else {
                    def details = "-"
                    if (categoryResult?.conflicts) {
                        details = categoryResult.conflicts.collect { it.value }.join(', ')
                    }
                    errors << [code: "aggregate.disaggregations.category.create.error", args: [rowNum, categoryName, dataElementName, details]]
                    return errors
                }
            }
            // not worth it to check to see if it's already been assigned, it can be assigned multiple times with no negative effects
            def assignCategoryOptionToCategoryResult = categoryService.assignCategoryOptionToCategory(auth, categoryId, categoryOptionId)
            if (!assignCategoryOptionToCategoryResult?.success) {
                log.warn "could not assign ${categoryOptionName} to ${categoryName} for ${dataElementName}"
                def details = "-"
                if (assignCategoryOptionToCategoryResult?.conflicts) {
                    details = assignCategoryOptionToCategoryResult.conflicts.collect { it.value }.join(', ')
                }
                errors << [code: "aggregate.disaggregations.assign.categoryOptionToCategory.error", args: [rowNum, categoryOptionName, categoryName, dataElementName, details]]
                return errors
            }

            if (!categoryComboName?.trim()) {
                errors << [code: "aggregate.disaggregations.categoryCombo.empty", args: [rowNum, dataElementName]]
                return errors
            }

            def categoryComboId
            // see if there is a category combo already created
            if (dataElement.categoryCombo?.name == Utils.addPrefix(dataSet.name, categoryComboName)) {
                categoryComboId = dataElement.categoryCombo.id
            } else {
                //the category combo was not properly set up as part of the metadata import
                log.warn "category combo ${categoryComboName} was not set up properly in metadata import for data element ${dataElementName}"
                errors << [code: "aggregate.disaggregations.categoryCombo.error", args: [rowNum, categoryComboName, dataElementName]]
                return errors
            }

            // not worth it to check to see if it's already been assigned, it can be assigned multiple times with no negative effects
            def assignCategoryToCategoryComboResult = categoryComboService.assignCategoryToCategoryCombo(auth, categoryComboId, categoryId)
            if (!assignCategoryToCategoryComboResult?.success) {
                log.warn "could not assign ${categoryName} to ${categoryComboName} for ${dataElementName}"
                def details = "-"
                if (assignCategoryToCategoryComboResult?.conflicts) {
                    details = assignCategoryToCategoryComboResult.conflicts.collect { it.value }.join(', ')
                }
                errors << [code: "aggregate.disaggregations.assign.categoryToCategoryCombo.error", args: [rowNum, categoryName, categoryComboName, dataElementName, details]]
                return errors
            }

            // create a categoryOptionCombo from scratch every time
            def categoryOptionComboId
            def categoryOptionComboResult = categoryOptionComboService.create(auth,
                    ["name": categoryOptionName, "categoryCombo" : ["id" : categoryComboId]])
            if (categoryOptionComboResult?.success) {
                categoryOptionComboId = categoryOptionComboResult.lastImported
            } else {
                //the category option combo was not properly set up as part of the metadata import
                log.warn "category option combo ${categoryOptionName} was not set up properly in metadata import for data element ${dataElementName}"
                def details = "-"
                if (categoryOptionComboResult?.conflicts) {
                    details = categoryOptionComboResult.conflicts.collect { it.value }.join(', ')
                }
                errors << [code: "aggregate.disaggregations.categoryOptionCombo.create.error", args: [rowNum, categoryOptionName, dataElementName, details]]
                return errors
            }

            def assignCategoryOptionToCategoryOptionComboResult = categoryOptionComboService.assignCategoryOptionToCategoryOptionCombo(auth, categoryOptionComboId, categoryOptionId)
            if (!assignCategoryOptionToCategoryOptionComboResult?.success) {
                log.warn "could not assign ${categoryOptionName} to CategoryOptionCombo for ${dataElementName}"
                def details = "-"
                if (assignCategoryOptionToCategoryOptionComboResult?.conflicts) {
                    details = assignCategoryOptionToCategoryOptionComboResult.conflicts.collect { it.value }.join(', ')
                }
                errors << [code: "aggregate.disaggregations.assign.categoryOptionToCategoryOptionCombo.error", args: [rowNum, categoryOptionName, dataElementName, details]]
                return errors
            }

        } catch (Exception e) {
            def rowValues = ""
            if (row?.values()) {
                rowValues = row.values().join(',')
            }
            errors << [code: "aggregate.disaggregations.unspecified.error", args: [rowNum, rowValues, e.message]]
        }

        return errors
    }

    /**
     * Processes a row from the aggregate data metadata import csv file for processing (upload / import)
     *
     * @param auth DHIS 2 Credentials
     * @param rowMap The row (map) of metadata from the csv file
     * @param rowNum The row number of this row of metadata
     * @param dataSet The data set that this metadata will be imported into
     * @param csvHeadings The csv headings configured for the metadata import csv for reference from the rowMap
     * @return errors from the process if any
     */
    private def processMetadata (def auth, def rowMap, def rowNum, def dataSet, def csvHeadings) {

        def errors = []

        Map<String, String> row = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        row.putAll(rowMap);
        try {

            def dataElementName = row[csvHeadings["METADATA_DATA_ELEMENT_NAME_CSV_HEADING"]]

            // can't create data element without name
            if (!dataElementName?.trim()) {
                log.warn("data element name not supplied for row ${row}")
                errors << [code: "aggregate.metadata.dataElement.name.empty", args: [rowNum, row.values().join(',')]]
                return errors
            } else {
                dataElementName = dataElementName.trim()
            }
            def valueType = getValueType(row[csvHeadings["METADATA_VALUE_TYPE_CSV_HEADING"]], row[csvHeadings["METADATA_NUMBER_TYPE_CSV_HEADING"]])

            def code = row[csvHeadings["METADATA_CODE_CSV_HEADING"]]
            // dataElementShortName is not required
            def dataElementShortName = row[csvHeadings["METADATA_DATA_ELEMENT_SHORT_NAME_CSV_HEADING"]]
            // description is not required
            def description = row[csvHeadings["METADATA_DESCRIPTION_CSV_HEADING"]]
            // formName is not required
            def formName = row[csvHeadings["METADATA_FORM_NAME_CSV_HEADING"]]
            def aggregationType = row[csvHeadings["METADATA_AGGREGATION_OPERATOR_CSV_HEADING"]]
            def categoryCombinationName = row[csvHeadings["METADATA_CATEGORY_COMBINATION_CSV_HEADING"]]

            // use default category combo as default
            def categoryCombinationMap = ["name": "default"]
            if (categoryCombinationName?.trim()) {
                categoryCombinationName = categoryCombinationName.trim()
                def categoryCombo = categoryComboService.findByName(auth, Utils.addPrefix(dataSet.name, categoryCombinationName))
                if (categoryCombo == null) {
                    categoryCombo = ["name"     : Utils.addPrefix(dataSet.name, categoryCombinationName),
                                     "code": Utils.addPrefix(dataSet.name, categoryCombinationName),
                                     "dataDimensionType": DIMENSION_TYPE]
                    def categoryComboResult = categoryComboService.create(
                            auth,
                            categoryCombo)
                    if (categoryComboResult?.success){
                        categoryCombo << [id: categoryComboResult.lastImported]
                    } else {
                        def details = "-"
                        if (categoryComboResult?.conflicts) {
                            details = categoryComboResult.conflicts.collect { it.value }.join(', ')
                        }
                        errors << [code: "aggregate.metadata.categoryCombo.error", args: [rowNum, dataElementName, details]]
                        return errors
                    }
                }
                if (categoryCombo?.id) {
                    categoryCombinationMap = ["id": categoryCombo.id]
                } else {
                    errors << [code: "aggregate.metadata.categoryCombo.error", args: [rowNum, dataElementName, ""]]
                    return errors
                }
            }

            // default to "sum" if not supplied
            aggregationType = aggregationType?.trim() ?: SUM

            dataElementShortName = dataElementShortName?.trim() ?: dataElementName

            def dataElementNameWithPrefix = Utils.addPrefix(dataSet.name, dataElementName)
            def dataElementShortNameWithPrefix = Utils.addPrefix(dataSet.name, dataElementShortName)

            boolean isCreate = false

            def dataElement = dataElementService.findByName(
                    auth,
                    dataElementNameWithPrefix,
                    [dataElementService.ALL_FIELDS])

            if (!dataElement) {
                // This is a create
                isCreate = true
                dataElement = [
                        name: dataElementNameWithPrefix,
                ]
            }
            dataElement.put("shortName", dataElementShortNameWithPrefix.take(50))
            dataElement.put("domainType", DOMAIN_TYPE)
            dataElement.put("valueType", valueType)
            dataElement.put("aggregationType", aggregationType)
            dataElement.put("categoryCombo", categoryCombinationMap)
            // Set zeroIsSignificant to true (to allow zeros) for all DataElements
            dataElement.put("zeroIsSignificant", true)

            if (code?.trim()) {
                dataElement.put("code", Utils.addPrefix(dataSet.name, code.trim()).take(50))
            } else {
                if (dataElement.containsKey("code")) {
                    dataElement.remove("code")
                }
            }

            if (description?.trim()) {
                dataElement.put("description", description.trim())
            } else {
                if (dataElement.containsKey("description")) {
                    dataElement.remove("description")
                }
            }
            if (formName?.trim()) {
                dataElement.put("formName", formName.trim())
            } else {
                if (dataElement.containsKey("formName")) {
                    dataElement.remove("formName")
                }
            }

            def dataElementResult
            if (isCreate) {
                dataElementResult = dataElementService.create(auth, dataElement)
            } else {
                dataElementResult = dataElementService.update(auth, dataElement, ApiMergeMode.REPLACE)
            }

            if (dataElementResult?.success) {
                // it was successful
                dataElement.id = dataElementResult.lastImported
            } else {
                def details = "-"
                if (dataElementResult?.conflicts) {
                    details = dataElementResult.conflicts.collect { it.value }.join(', ')
                }
                def msgCode = isCreate ? "aggregate.metadata.cannot.create.dataElement" : "aggregate.metadata.cannot.update.dataElement"
                errors << [code: msgCode, args: [rowNum, dataElementName, details]]
                return errors
            }

            // not worth it to check to see if it's already been assigned, it can be assigned multiple times with no negative effects
            def assignDataSetToDataElementResult = dataElementService.assignDataSetToDataElement(auth, dataElement.id, dataSet.id)
            if (!assignDataSetToDataElementResult?.success) {
                log.warn "could not assign ${dataSet} to ${dataElementName}"
                def details = "-"
                if (assignDataSetToDataElementResult?.conflicts) {
                    details = assignDataSetToDataElementResult.conflicts.collect { it.value }.join(', ')
                }
                errors << [code: "aggregate.metadata.assign.dataSetToDataElement.error", args: [rowNum, dataSet, dataElementName, details]]
                return errors
            }

        } catch (Exception e) {
            def rowValues = ""
            if (row?.values()) {
                rowValues = row.values().join(',')
            }
            errors << [code: "aggregate.metadata.unspecified.error", args: [rowNum, rowValues, e.message]]
        }
        return errors
    }

    /**
     * Retrieves the configured column headings for the disaggregations CSV
     *
     * @return The column headings for the disaggregations
     */
    def getDisaggregationColumnHeadings() {
        return [
                DISAGGREGATION_DATA_ELEMENT_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.disaggregation.dataElement"),
                DISAGGREGATION_CATEGORY_COMBO_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.disaggregation.categoryCombo"),
                DISAGGREGATION_CATEGORY_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.disaggregation.category"),
                DISAGGREGATION_CATEGORY_OPTION_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.disaggregation.categoryOption")
        ]
    }

    /**
     * Retrieves all possible configured (required + optional) metadata column headings for the metadata CSV
     *
     * @return All possible configured (required + optional) metadata column headings for the metadata CSV
     */
    def getAllMetadataColumnHeadings() {
        return getRequiredMetadataColumnHeadings() + getOptionalMetadataColumnHeadings()
    }

    /**
     * Retrieves all required configured metadata column headings for the metadata CSV
     *
     * @return All required configured metadata column headings for the metadata CSV
     */
    def getRequiredMetadataColumnHeadings() {
        return [
                METADATA_DATA_ELEMENT_NAME_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.metadata.dataElement.name"),
                METADATA_CODE_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.metadata.dataElement.code"),
                METADATA_VALUE_TYPE_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.metadata.valueType"),
                METADATA_NUMBER_TYPE_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.metadata.numberType"),
                METADATA_AGGREGATION_OPERATOR_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.metadata.aggregationOperator"),
                METADATA_CATEGORY_COMBINATION_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.metadata.categoryCombination")
        ]
    }

    /**
     * Retrieves all optional configured metadata column headings for the metadata CSV
     *
     * @return All optional configured metadata column headings for the metadata CSV
     */
    def getOptionalMetadataColumnHeadings() {
        return [
                METADATA_DATA_ELEMENT_SHORT_NAME_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.metadata.dataElement.shortName"),
                METADATA_DESCRIPTION_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.metadata.dataElement.description"),
                METADATA_FORM_NAME_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.metadata.dataElement.formName"),
        ]
    }

    /**
     * Get the DHIS 2 valueType based on the supplied valueType & numberType
     *
     * @param valueType
     * @param numberType
     * @return The DHIS 2 value type
     */
    private def getValueType(def valueType, def numberType) {

        def dhis2ValueType
        switch (valueType?.trim().toLowerCase()) {
            case "date":
                dhis2ValueType = DATE
                break
            case "string":
                dhis2ValueType = TEXT
                break
            case "int":
                dhis2ValueType = getValueTypeFromNumberType(numberType)
                break
            default:
                dhis2ValueType = TEXT
        }
        return dhis2ValueType
    }

    /**
     * Get the DHIS 2 numberType (valueType)
     *
     * @param numberType
     * @return The DHIS 2 numberType (valueType)
     */
    private def getValueTypeFromNumberType(def numberType) {
        def dhis2ValueType
        switch (numberType?.trim().toLowerCase()) {
            case "posint":
                dhis2ValueType = INTEGER_POSITIVE
                break
            case "zeropositiveint":
                dhis2ValueType = INTEGER_ZERO_OR_POSITIVE
                break
            case "number":
                dhis2ValueType = NUMBER
                break
            default:
                dhis2ValueType = INTEGER
        }
        return dhis2ValueType
    }
}
