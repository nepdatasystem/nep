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

package nep.util

import com.twopaths.dhis2.api.ValueType
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

/**
 * Utilities for use by the application
 */
class Utils {

    /**
     * Prepends the specified prefix to the specified suffix, separated by a dash
     *
     * @param prefix The prefix to prepend
     * @param suffix The suffix to prepend the prefix to
     * @return The result of the prefixing of the suffix
     */
    static String addPrefix(def prefix, def suffix) {
        return new StringBuilder().append(prefix).append(" - ").append(suffix)
    }

    /**
     * Strips the specified prefex from the given string
     *
     * @param prefix The prefix to remove
     * @param str The string to remove the prefix from
     * @return The result of the removal of the prefix
     */
    static String removePrefix(def prefix, def str) {
        return new String(str).replace(("${prefix} - " as String), "")
    }

    /**
     * Returns a data object extracted from Year / Month / Date fields
     *
     * @param data The data containing the date fields
     * @param yearMonthDayFields The field keys to retrieve
     * @return the extracted date
     */
    static def getDateFromYearMonthDateFields(def data, def yearMonthDayFields) {
        def fields = yearMonthDayFields.split(",") as List
        def yearField = fields[0]
        def monthField = fields[1]
        def dayField = fields[2]

        def year = yearField ? data[yearField] : 1
        def month = monthField ? data[monthField] : 1
        def day = dayField ? data[dayField] : 1

        DateTimeZone zoneUTC = DateTimeZone.UTC
        DateTimeZone.setDefault(zoneUTC)
        DateTime dt = new DateTime(year as int, month as int, day as int, 0, 0, 0)

        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd")
        return fmt.print(dt)
    }

    /**
     * Retrieves a formatted DateTime string in the format
     *      "dd/MM/yyyy h:mm a"
     * from a DateTime string in the format:
     *      "yyyy-MM-dd'T'HH:mm:ss.SSS"
     *
     * @param dateTimeString The string to extract the DateTime object from
     * @return The DateTime string in the format "dd/MM/yyyy h:mm a"
     */
    static def getDateTimeFromData(String dateTimeString) {

        DateTimeZone zoneUTC = DateTimeZone.UTC
        DateTimeZone.setDefault(zoneUTC)

        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
        DateTime dateTime = fmt.parseDateTime(dateTimeString)

        DateTimeFormatter outputFormat = DateTimeFormat.forPattern("dd/MM/yyyy h:mm a")
        String str = dateTime.toString(outputFormat)
        return str
    }

    /**
     * Retrieves a formatted Date string in the format
     *      "yyyy-MM-dd"
     * from a DateTime string in the format:
     *      "yyyy-MM-dd'T'HH:mm:ss.SSS"
     *
     * @param dateTimeString The string to extract the DateTime object from
     * @return The DateTime string in the format "yyyy-MM-dd"
     */
    static def getDateFromDateTime(String dateTimeString) {

        DateTimeZone zoneUTC = DateTimeZone.UTC
        DateTimeZone.setDefault(zoneUTC)

        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
        DateTime dateTime = fmt.parseDateTime(dateTimeString)

        DateTimeFormatter outputFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
        String str = dateTime.toString(outputFormat)
        return str
    }

    /**
     * Formats the raw category combo name by appending surrounding brackets.
     * Category combo names are bracketed in DHIS 2.
     *
     * @param rawCategoryComboName Raw category combo name
     * @return The formatted categoryComboName (bracketed)
     */
    static String formatCategoryComboName (def rawCategoryComboName) {
        return new StringBuilder().append("(").append(rawCategoryComboName).append(")")
    }

    /**
     * Extracts the raw category combo name from the formatted category combo name.
     * Category combo names are bracketed in DHIS 2.
     *
     * @param formattedCategoryComboName
     * @return The raw categoryComboName (without brackets)
     */
    static String extractRawCategoryComboName (def formattedCategoryComboName) {
        // only strip it if the first and last characters are open & close brackets
        if (formattedCategoryComboName.charAt(0) == '(' && formattedCategoryComboName.charAt(formattedCategoryComboName.length()-1) == ')') {
            return formattedCategoryComboName?.substring(1, formattedCategoryComboName.length()-1)
        } else {
            return formattedCategoryComboName
        }
    }

    /**
     * Takes a userRoles String e.g. "ROLE_DATA_MANAGER:Data Manager
     * and returns the roles
     *
     * @param userRoles The user roles to get role names for
     * @return the roles as a List
     */
    static def getRoleNames(def userRoles) {
        def roles = []
        (userRoles?.split(",") as List).each { entry ->
            def mapping = entry?.split(":") as List
            if (mapping && mapping.size() == 2) {
                roles << mapping[1]
            }
        }
        return roles
    }

    /**
     * Get the DHIS 2 valueType based on the passed in type.
     * The passed in type is the type from either the Stata or R generated codebooks.
     *
     * @param type The Stata or R type to convert to a DHIS 2 value type
     * @return DHIS 2 value type
     */
    static String getValueType(def type) {
        def valueType = null
        switch (type?.toLowerCase()) {
            case "numeric":
                valueType = ValueType.NUMBER.value()
                break
            case "integer":
            case "factor": // This is from R
            case "long":
                valueType = ValueType.INTEGER.value()
                break
            case "character": // This is from R
            case "string":
                valueType = ValueType.TEXT.value()
                break
        }

        return valueType
    }

}
