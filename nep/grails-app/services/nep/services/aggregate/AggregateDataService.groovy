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
import nep.util.Utils

import javax.annotation.PostConstruct
import java.text.SimpleDateFormat

/**
 * Service to manage import of aggregate data into DHIS 2
 */
@Transactional
class AggregateDataService {

    def categoryComboService
    def dataElementService
    def organisationUnitService

    def dataValueService
    def dataSetService
    def resourceTableService

    def propertiesService
    def dateParser = new SimpleDateFormat("yyyy-MM-dd")
    def periodParserYear = new SimpleDateFormat("yyyy")
    def periodParserYearMonth = new SimpleDateFormat("yyyyMM")

    /**
     * Set up initial global properties
     *
     * @return
     */
    @PostConstruct
    def init() {
        dateParser.lenient = false
    }

    /**
     * Retrieves the id of the default category combo.
     * Prior versions of the DHIS 2 API would automatically set the default category combo if none was supplied,
     * but now we have to explicitly set the default for the API call to work.
     *
     * @param auth DHIS 2 Credentials
     * @return The id of the default category combo
     */
    def getDefaultCategoryComboId(def auth) {
        return categoryComboService.getDefaultCategoryComboId(auth, true)
    }

    /**
     * Processes a row from the aggregate data import csv file for processing (upload / import)
     *
     * @param auth DHIS 2 Credentials
     * @param rowData The row of data from the csv file
     * @param rowNum The row number of this row of data
     * @param dataSet The data set that this data will be imported into
     * @param csvHeadings The csv headings configured for the data import csv for reference from the row data map
     * @return errors from the process if any
     */
    def processDataRow(def auth, def rowData, def rowNum, def dataSet, def csvHeadings) {

        def errors = []

        // Update row of data to case insensitive map
        Map<String, String> row = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        row.putAll(rowData);

        try {

            def orgUnitCode = row[csvHeadings["ORG_UNIT_CSV_HEADING"]]
            def period = row[csvHeadings["PERIOD_CSV_HEADING"]]
            def dataElementRawName = row[csvHeadings["DATA_ELEMENT_VARIABLE_NAME_CSV_HEADING"]]
            def dataElementUID = row[csvHeadings["DATA_ELEMENT_VARIABLE_UID_CSV_HEADING"]]
            def value = row[csvHeadings["DATA_VALUE_CSV_HEADING"]]
            def categoryOptionComboName = row[csvHeadings["CATEGORY_OPTION_COMBO_CSV_HEADING"]]
            def completeDate = row[csvHeadings["DATE_CSV_HEADING"]]
            boolean hasDisaggregation = categoryOptionComboName?.trim()

            if (!completeDate?.trim()) {
                log.error("Date is not supplied for data row ${row.toString()}")
                errors << [code: "aggregate.data.date.empty", args: [getRowValues(row, csvHeadings).join(',')]]
                return errors
            } else {
                completeDate = completeDate.trim()
                try {
                    dateParser.parse(completeDate)
                } catch (Exception e1) {
                    log.error("Date is not a valid date: ${completeDate} for data row ${row.toString()}")
                    errors << [code: "aggregate.data.date.invalid", args: [completeDate, getRowValues(row, csvHeadings).join(',')]]
                    return errors
                }
            }
            if (!period?.trim()) {
                log.error("Period is not supplied for data row ${row.toString()}")
                errors << [code: "aggregate.data.period.empty", args: [getRowValues(row, csvHeadings).join(',')]]
            } else {
                period = period.trim()
                // Period can either be in the format yyyy or yyyyMM
                try {
                    periodParserYear.parse(period)
                } catch (Exception e1) {
                    try {
                        periodParserYearMonth.parse(period)
                    } catch (Exception e2) {
                        log.error("Period is not a valid period: ${period} for data row ${row.toString()}")
                        errors << [code: "aggregate.data.period.invalid", args: [period, getRowValues(row, csvHeadings).join(',')]]
                        return errors
                    }
                }
            }
            def dataValue = [
                    completeDate: completeDate,
                    dataSet     : dataSet.id,
                    period      : period,
                    // this will contain data element, category option combo, and value itself
                    dataValues  : []
            ]
            def dataValues = [:]
            // DATA ELEMENT
            if (!dataElementRawName?.trim()) {
                log.error("data element name is not supplied for data row ${row.toString()}")
                errors << [code: "aggregate.data.dataElement.name.empty", args: [getRowValues(row, csvHeadings).join(',')]]
                return errors
            }
            def dataElementNameWithPrefix = Utils.addPrefix(dataSet.name, dataElementRawName.trim())

            def dataElementFields = [
                    dataElementService.FIELD_ID,
                    dataElementService.FIELD_VALUE_TYPE]
            if (hasDisaggregation) {
                dataElementFields << dataElementService.FIELD_CATEGORY_COMBO
            }
            def dataElement = dataElementService.findByName(auth, dataElementNameWithPrefix, dataElementFields)
            if (!dataElement) {
                log.error("data element not found for name ${dataElementRawName}")
                errors << [code: "aggregate.data.dataElement.name.notFound", args: [dataElementRawName.trim()]]
                return errors
            } else {
                dataValues << [dataElement: dataElement.id]
            }

            // ORGANISATION UNIT
            if (!orgUnitCode?.trim()) {
                log.error("org unit is not supplied for data row ${row.toString()}")
                errors << [code: "aggregate.data.orgUnit.code.empty", args: [getRowValues(row, csvHeadings).join(',')]]
                return errors
            }

            def orgUnit = organisationUnitService.findByCode(auth, orgUnitCode.trim().toLowerCase())
            if (!orgUnit) {
                log.error("no org unit found for code ${orgUnitCode}")
                errors << [code: "aggregate.data.orgUnit.code.notFound", args: [orgUnitCode.trim()]]
                return errors
            } else {
                dataValue << [orgUnit: orgUnit.id]
            }

            // DATA VALUE
            if (!value?.trim()) {
                log.error("value is not supplied for data row ${row.toString()}")
                errors << [code: "aggregate.data.dataValue.empty", args: [getRowValues(row, csvHeadings).join(',')]]
                return errors
            }
            if (dataElement.valueType in ["NUMBER", "INTEGER", "INTEGER_POSITIVE", "INTEGER_NEGATIVE", "INTEGER_ZERO_OR_POSITIVE"]) {
                if (!value.trim().replaceAll(",", "").isNumber()) {
                    log.error("value ${value} is not a number")
                    errors << [code: "aggregate.data.dataValue.nan", args: [value.trim()]]
                    return errors
                } else {
                    value = value.replaceAll(",", "")
                }
            }
            dataValues << [value: value.trim()]

            //CATEGORY OPTION COMBO (optional)
            if (hasDisaggregation) {
                def categoryCombo = dataElement.categoryCombo
                // refresh with full set of fields
                def possibleCategoryOptionCombos = categoryComboService.get(auth, categoryCombo.id, [categoryComboService.FIELD_CATEGORY_OPTION_COMBOS]).categoryOptionCombos
                def categoryOptionCombos = possibleCategoryOptionCombos.findAll {
                    it.get("name") == categoryOptionComboName.trim()
                }
                // there should only be one match
                switch (categoryOptionCombos.size()) {
                    case 1:
                        dataValues << [categoryOptionCombo: categoryOptionCombos[0].id]
                        break
                    case 0:
                        errors << [code: "aggregate.data.categoryOptionCombos.none", args: [categoryOptionComboName]]
                        return errors
                        break
                    default:
                        errors << [code: "aggregate.data.categoryOptionCombos.multiple", args: [categoryOptionComboName]]
                        return errors
                        break
                }
            }

            dataValue.dataValues << dataValues
            log.debug "<<< dataValue: " + dataValue

            def result = dataValueService.create(auth, dataValue)

            // If this failed, get errors if they exist
            if (!result.success) {
                errors << [code: "aggregate.data.create.error"]
                if (result.errors) {
                    errors.addAll(result.errors)
                }
            }
        } catch (Exception e) {
            def rowValues = ""
            if (row?.values()) {
                rowValues = getRowValues(row, csvHeadings).join(',')
            }
            errors << [code: "aggregate.data.unspecified.error", args: [rowValues, e.message]]
        }

        return errors
    }

    /**
     * Retrieves the configured csv column headings for the aggregate data import file
     * @return The configured csv column headings for the aggregate data import file
     */
    def getDataColumnHeadings() {

        return [
                DATE_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.date"),
                DATA_ELEMENT_VARIABLE_NAME_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.dataElement.name"),
                DATA_ELEMENT_VARIABLE_UID_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.dataElement.uid"),
                PERIOD_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.period"),
                ORG_UNIT_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.orgUnit"),
                CATEGORY_OPTION_COMBO_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.categoryOptionCombo"),
                DATA_VALUE_CSV_HEADING : propertiesService.getProperties().getProperty("nep.aggregate.data.dataValue")
        ]
    }

    /**
     * Display the values in the correct order (the order of the csvHeadings map)
     *
     * @param row The row
     * @param csvHeadings The csvHeadings for the row
     * @return The row values in the correct order (the order of the csvHeadings map)
     */
    def getRowValues(def row, def csvHeadings) {
        def rowValues = []
        csvHeadings.each { key, heading ->
            rowValues << row.get(heading)
        }
        rowValues
    }
}
