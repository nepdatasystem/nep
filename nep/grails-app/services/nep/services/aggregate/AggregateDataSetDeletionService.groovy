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

import com.twopaths.dhis2.services.CategoryComboService
import com.twopaths.dhis2.services.CategoryOptionComboService
import com.twopaths.dhis2.services.DataElementService
import com.twopaths.dhis2.services.DataPruningService
import com.twopaths.dhis2.services.DataSetService
import com.twopaths.dhis2.services.IndicatorService
import com.twopaths.dhis2.services.MetadataService
import grails.transaction.Transactional

/**
 * Service to manage aggregate data set deletions
 */
@Transactional
class AggregateDataSetDeletionService {

    DataSetService dataSetService
    DataPruningService dataPruningService
    DataElementService dataElementService
    CategoryComboService categoryComboService
    CategoryOptionComboService categoryOptionComboService
    IndicatorService indicatorService
    MetadataService metadataService

    /*
     * This method will delete everything related to a Data Set
     * - data
     * - metadata including disaggregations
     * - Data Set
     *
     * @param auth DHIS 2 credentials
     * @param dataSetId The id of the data set to delete data for
     * @return Array of errors if any
     */
    def delete(def auth, def dataSetId) {

        def errors = []

        // 1. Delete all the data for the data set
        errors.addAll(deleteDatasetData(auth, dataSetId))
        if (!(errors.size() > 0)) {
            // 2. Delete all the metadata for the data set
            errors.addAll(deleteDatasetMetadata(auth, dataSetId))

            if (!(errors.size() > 0)) {
                // 3. Delete the actual data set
                errors.addAll(deleteDataSet(auth, dataSetId))
            }
        }

        return errors

    }

    /**
     * Deletes all data associated with the specified data set
     *
     * @param auth DHIS 2 credentials
     * @param dataSetId The id of the data set to delete data for
     * @return Array of errors if any
     */
    def deleteDatasetData (auth, dataSetId) {

        def errors = []

        def dataSet = dataSetService.get(auth, dataSetId, ["id", "name", "dataSetElements[id,dataElement[id]]"])

        if (dataSet) {

            def dataSetHasData = dataSetService.dataSetHasData(auth, dataSet.id)

            if (dataSetHasData) {

                def dataElements = dataSet.dataSetElements.collect { it.dataElement }

                dataElements.each { dataElement ->
                    // Delete by pruning with new prune functionality from 2.25
                    errors.addAll(pruneDataElement(auth, dataElement.id))
                }
            }

            if (errors.size() > 0) {
                errors << [code: "aggregate.dataset.delete.data.failure", args: [dataSet.name, dataSetId]]
            }
        }
        else {
            errors << [code: "aggregate.dataset.delete.data.dataSet.not.found", args: [dataSetId]]
        }

        return errors
    }

    /**
     * Deletes all the metadata associated with a Data Set including Disaggregations
     *
     * @param auth DHIS 2 credentials
     * @param dataSetId The id of the data set to delete data for
     * @return Array of errors if any
     */
    def deleteDatasetMetadata(auth, dataSetId) {
        def errors = []

        def dataSet = dataSetService.get(auth, dataSetId,
                ["id", "name", "dataSetElements[id,dataElement[id,categoryCombo[id,categoryOptionCombos[id]]]]"])

        if (dataSet) {

            // if there are no associated data elements, then no metadata to delete
            if (dataSet.dataSetElements) {

                if (dataSetService.dataSetHasData(auth, dataSet.id)) {
                    errors << [code: "aggregate.metadata.dataset.delete.has.data", args: [dataSet.name, dataSetId]]

                } else {

                    def defaultCategoryComboId = categoryComboService.getDefaultCategoryComboId(auth, true)

                    // data set elements get deleted with the data set
                    def dataElements = dataSet.dataSetElements.collect { it.dataElement }

                    dataElements.each { dataElement ->
                        // delete any associated indicators
                        errors.addAll(deleteIndicatorsForDataElement(auth, dataElement.id))

                        if (!(errors.size() > 0)) {
                            // now delete the data element
                            errors.addAll(deleteDataElement(auth, dataElement.id))
                        }
                    }

                    if (!(errors.size() > 0)) {
                        // now go through and delete all the unique categoryCombos that are not the default one
                        // do this for whole data set instead of per data element to avoid errors when attempting to
                        // delete categoryCombos that have already been deleted
                        // There is a risk that some of these have been orphaned if there were any errors above in the
                        // deletion of the data elements, but this order of deletion needs to happen
                        def categoryCombosToDelete = dataElements?.collect {
                            it.categoryCombo
                        }?.findAll { categoryCombo ->
                            categoryCombo?.id != defaultCategoryComboId
                        }?.unique { categoryCombo ->
                            categoryCombo?.id
                        }
                        categoryCombosToDelete.each { categoryCombo ->

                            errors.addAll(deleteCategoryCombo(auth, categoryCombo))
                        }
                    }
                    if (errors.size() > 0) {
                        errors << [code: "aggregate.metadata.dataset.delete.failure", args: [dataSet.name, dataSetId]]
                    }
                }

            } // end if dataSetMembers exist

        } else {
            errors << [code: "aggregate.metadata.dataset.delete.dataSet.not.found", args: [dataSetId]]
        }

        return errors
    }

    /**
     * Deletes all Indicators that include the specified Data Element
     * Note that other data elements may have also been referenced by the indicator
     *
     * @param auth DHIS 2 credentials
     * @param dataElementID The data element id for which to delete indicators
     * @return Array of errors if any
     */
    def deleteIndicatorsForDataElement(auth, dataElementID) {

        def errors = []

        def indicators = indicatorService.findAllByDataElement(auth, dataElementID, ["id", "name"])
        if (indicators?.size() > 0) {
            indicators.each { indicator ->
                def indicatorDeleteResult = indicatorService.delete(auth, indicator?.id)
                if (!indicatorDeleteResult.success) {
                    log.error("error deleting indicator ${indicator.id} : ${indicator.name}")
                    errors.addAll(indicatorDeleteResult.errors)
                }
            }
        }

        return errors
    }

    /**
     * Deletes the actual data set object
     * Note that all metadata and data must already be deleted before using this method, or it will throw errors
     *
     * @param auth DHIS 2 credentials
     * @param dataSetId The id of the data set to delete
     * @return Array of errors if any
     */
     def deleteDataSet(auth, dataSetId) {

         def errors = []

         def dataSet = dataSetService.get(auth, dataSetId, ["id", "name"])

         if (dataSet) {

             def dataSetDeleteResult = dataSetService.delete(auth, dataSetId)

             if (!dataSetDeleteResult.success) {
                 log.error("error deleting dataSet ${dataSetId}")
                 errors.addAll(dataSetDeleteResult.errors)
             }

             if (errors.size() > 0) {
                 errors << [code: "dataset.not.deleted", args: [dataSet.name, dataSetId]]
             }
         } else {
             errors << [code: "aggregate.dataset.delete.dataSet.not.found", args: [dataSetId]]
         }

         return errors
    }

    /**
     * Deletes a Category Combo and all associated category option combos
     *
     * @param auth DHIS 2 credentials
     * @param categoryCombo the category combo to delete
     * @return Array of errors if any
     */
    public def deleteCategoryCombo(auth, categoryCombo) {

        def errors = []

        // first need to delete the associated categoryOptionCombos
        if (categoryCombo?.categoryOptionCombos?.size() > 0) {

            def metadata = [
                    categoryOptionCombos: categoryCombo?.categoryOptionCombos
            ]

            def categoryOptionCombosResult = metadataService.delete(auth, metadata)
            if (!categoryOptionCombosResult.success) {
                log.error("error deleting category option combos for category combo ${categoryCombo.id}")
                errors.addAll(categoryOptionCombosResult.errors)
                errors << [code: "aggregate.metadata.dataset.delete.category.combo.failure", args: [categoryCombo.name]]
            }
        }

        // now delete category combo
        // attempting to send this as one payload/transaction with the categoryOptionCombos to the metadata service
        // causes a DHIS 2 server error, so need to do in separate calls
        def categoryComboDeleteResult = categoryComboService.delete(auth, categoryCombo?.id)

        if (!categoryComboDeleteResult.success) {
            log.error("error deleting categoryCombo ${categoryCombo?.id}")
            errors.addAll(categoryComboDeleteResult.errors)
        }

        return errors
    }

    /**
     * Deletes a single category option combo
     * Note: not used as we are bulk deleting via the metadata api
     *
     * @param auth DHIS 2 credentials
     * @param categoryOptionCombo the category option combo to delete
     * @return Array of errors if any
     */
    private def deleteCategoryOptionCombo(auth, categoryOptionCombo) {

        def errors = []

        def categoryOptionCombosDeleteResult = categoryOptionComboService.delete(auth, categoryOptionCombo?.id)
        if (!categoryOptionCombosDeleteResult.success) {
            log.error("error deleting categoryOptionCombo ${categoryOptionCombo?.id}")
            errors.addAll(categoryOptionCombosDeleteResult.errors)
        }

        return errors
    }

    /**
     * Deletes the actual data element itself
     *
     * @param auth DHIS 2 credentials
     * @param dataElementId the id of the data element to delete
     * @return Array of errors if any
     */
    private def deleteDataElement(auth, dataElementId) {

        def errors = []

        // delete the Data Element itself
        def resultDataElementDelete = dataElementService.delete(auth, dataElementId)
        if (resultDataElementDelete.success) {
            log.debug("successfully deleted data element ${dataElementId}")
        } else {
            log.error("error deleting dataElement ${dataElementId}")
            errors.addAll(resultDataElementDelete.errors)
        }

        return errors
    }

    /**
     * Deletes (prunes) all data for the specified data element
     *
     * @param auth DHIS 2 credentials
     * @param dataElementId the id of the data element to delete all data for
     * @return Array of errors if any
     */
    public def pruneDataElement(auth, dataElementId) {

        def errors = []

        def resultDataPruning = dataPruningService.pruneDataElement(auth, dataElementId)
        if (!resultDataPruning.success) {
            log.error("error pruning dataElement ${dataElementId}")
            errors.addAll(resultDataPruning.errors)
        }

        return errors
    }
}
