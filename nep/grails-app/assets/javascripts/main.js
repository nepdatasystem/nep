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

// This is a manifest file that'll be compiled into application.js.
//
// Any JavaScript file within this directory can be referenced here using a relative path.
//
// You're free to add application-wide JavaScript to this file, but it's generally better
// to create separate JavaScript files as needed.
//
//= require jquery
//= require_self

// if (typeof jQuery !== 'undefined') {
// 	(function($) {
// 		$('#spinner').ajaxStart(function() {
// 			$(this).fadeIn();
// 		}).ajaxStop(function() {
// 			$(this).fadeOut();
// 		});
// 	})(jQuery);
// }

$(function(){

	if ($('#programStage_0').is(':checked')) {
		$("#programStage_0_fields").show();
	}

	if ($('#programStage_1').is(':checked')) {
		$("#programStage_1_fields").show();
	}

    if ($('#programStage_2').is(':checked')) {
        $("#programStage_2_fields").show();
    }

    $('#programStage_0').click(function() {
        if( $(this).is(':checked')) {
            $("#programStage_0_fields").show();
        } else {
            $("#programStage_0_fields").hide();
        }
    });
    $('#programStage_1').click(function() {
        if( $(this).is(':checked')) {
            $("#programStage_1_fields").show();
        } else {
            $("#programStage_1_fields").hide();
        }
    });
    $('#programStage_2').click(function() {
        if( $(this).is(':checked')) {
            $("#programStage_2_fields").show();
        } else {
            $("#programStage_2_fields").hide();
        }
    });

    $('.exception-toggle').click(function() {
        $(this).toggleClass('open');
        $('.alert-exception').toggleClass('hidden');
    });

    $('#data-set-metadata').change(function() {
        if (!this.value) {
            // no selection
            $('#data-set-metadata-select-dataset-info').show();
            $('#data-set-metadata-re-upload-info').hide();
            $('#data-set-metadata-upload-fields').hide();
        } else if ($.inArray(this.value, dataSetIdsWithMetadata) >= 0) {
            // there are metadata
            $('#data-set-metadata-select-dataset-info').hide();
            $('#data-set-metadata-re-upload-info').show();
            $('#data-set-metadata-upload-fields').show();
        } else {
            // there are no metadata
            $('#data-set-metadata-select-dataset-info').hide();
            $('#data-set-metadata-re-upload-info').hide();
            $('#data-set-metadata-upload-fields').show();
        }
    });

    $('#data-set-disaggregations').change(function() {
        if (!this.value) {
            $('#data-set-disaggregations-select-dataset-info').show();
            $('#data-set-disaggregations-re-upload-info').hide();
            $('#data-set-disaggregations-requires-metadata-message').hide();
            $('#data-set-disaggregations-requires-metadata').hide();
        } else {
            $('#data-set-disaggregations-select-dataset-info').hide();
            if ($.inArray(this.value, dataSetIdsWithDisaggregations) >= 0) {

                $('#data-set-disaggregations-re-upload-info').show();
            } else {
                $('#data-set-disaggregations-re-upload-info').hide();
            }
            // if there is no metadata uploaded, hide buttons, hide file upload, and show notice about metadata
            if ($.inArray(this.value, dataSetIdsWithMetadata) >= 0) {
                $('#data-set-disaggregations-requires-metadata').show();
                $('#data-set-disaggregations-requires-metadata-message').hide();
                updateLinkDataSetId($('#aggregate-disaggregations-skip-link'), this.value);
            } else {
                $('#data-set-disaggregations-requires-metadata').hide();
                $('#data-set-disaggregations-requires-metadata-message').show();
                updateLinkDataSetId($('#aggregate-metadata-link'), this.value);
            }
        }
    });

    $('#data-set-data').change(function() {
        $('#data-set-data-re-upload-info').hide();
        $('#aggregate.data.reupload.status.error-label').hide();
        if (!this.value) {
            $('#data-set-data-select-dataset-info').show();
            $('#data-set-data-requires-metadata-message').hide();
            $('#data-set-data-requires-metadata').hide();
        } else {
            $('#data-set-data-select-dataset-info').hide();
            // if there is no metadata uploaded, hide buttons, hide file upload, and show notice about metadata
            if ($.inArray(this.value, dataSetIdsWithMetadata) >= 0) {
                $('#data-set-data-requires-metadata').show();
                $('#data-set-data-requires-metadata-message').hide();
                if ($.inArray(this.value, dataSetIdsWithData) >= 0) {
                    $('#data-set-data-re-upload-info').show();
                }
            } else {
                $('#data-set-data-requires-metadata').hide();
                $('#data-set-data-requires-metadata-message').show();
                updateLinkDataSetId($('#aggregate-metadata-link'), this.value);
            }
        }
    });

    $('[data-trigger="dropdown"]').click(function(e) {
        var dropdown = $(this).closest('.dropdown');

        e.preventDefault();

        dropdown.find('.form-check-input').prop('checked', false).attr("disabled", false);
        dropdown.find('.dropdown-delete').addClass('is-disabled');

        if (dropdown.hasClass('is-open')) {
            dropdown.removeClass('is-open');
        } else {
            $('.dropdown').removeClass('is-open');
            dropdown.addClass('is-open');
        }
    });

    /**
     * Set the programId, programName and deleteType to be submitted to the controller for deleting the survey/metadata/data
     *
     * @param dropdown
     * @param deleteType
     */
    function setSurveyDeletionParameters(dropdown, deleteType) {
        var programId = dropdown.find('[data-trigger="programId"]').val();
        var programName = dropdown.find('[data-trigger="programName"]').val();
        var modal = $('[data-item="modal"]');
        modal.find('[data-trigger="programId"]').val(programId);
        modal.find('[data-trigger="programName"]').val(programName);
        modal.find('[data-trigger="deleteType"]').val(deleteType);
        modal.find('#programNameModal').text(programName);
    }

    /**
     * Set the dataSetId, dataSetName and deleteType to be submitted to the controller for deleting the data set/metadata/data
     *
     * @param dropdown
     * @param deleteType
     */
    function setDataSetDeletionParameters(dropdown, deleteType) {
        var dataSetId = dropdown.find('[data-trigger="dataSetId"]').val();
        var dataSetName = dropdown.find('[data-trigger="dataSetName"]').val();
        var modal = $('[data-item="modal"]');
        modal.find('[data-trigger="dataSetId"]').val(dataSetId);
        modal.find('[data-trigger="dataSetName"]').val(dataSetName);
        modal.find('[data-trigger="deleteType"]').val(deleteType);
        modal.find('#dataSetNameModal').text(dataSetName);
    }

    $('[data-trigger="survey"]').on('change', function() {
        var dropdown = $(this).closest('.dropdown');

        if($(this).is(':checked')) {
            dropdown.find('[data-trigger="survey-metadata"]').prop('checked', true).attr("disabled", true);
            dropdown.find('[data-trigger="survey-data"]').prop('checked', true).attr("disabled", true);

            $('[data-warning]').each(function() {
                $(this).show();
            });
            setSurveyDeletionParameters(dropdown, "survey");
        } else {
            dropdown.find('[data-trigger="survey-metadata"]').prop('checked', false).attr("disabled", false);
            dropdown.find('[data-trigger="survey-data"]').prop('checked', false).attr("disabled", false);

            $('[data-warning]').each(function() {
                $(this).hide();
            });
        }
    });

    $('[data-trigger="survey-metadata"]').on('change', function() {
        var dropdown = $(this).closest('.dropdown');

        if($(this).is(':checked')) {
            dropdown.find('[data-trigger="survey-data"]').prop('checked', true).attr("disabled", true);
            $('[data-warning="survey-metadata"]').show();
            $('[data-warning="survey-data"]').show();
            setSurveyDeletionParameters(dropdown, "survey-metadata");
        } else {
            dropdown.find('[data-trigger="survey-data"]').prop('checked', false).attr("disabled", false);
            $('[data-warning="survey-metadata"]').hide();
            $('[data-warning="survey-data"]').hide();
        }
    });

      $('[data-trigger="survey-data"]').on('change', function() {
        var dropdown = $(this).closest('.dropdown');

        if($(this).is(':checked')) {
          $('[data-warning="survey-data"]').show();
            setSurveyDeletionParameters(dropdown, "survey-data");
        } else {
           $('[data-warning="survey-data"]').hide();
        }
    });

    $('[data-trigger="dataSet"]').on('change', function() {
        var dropdown = $(this).closest('.dropdown');

        if($(this).is(':checked')) {
            dropdown.find('[data-trigger="dataSet-metadata"]').prop('checked', true).attr("disabled", true);
            dropdown.find('[data-trigger="dataSet-data"]').prop('checked', true).attr("disabled", true);

            $('[data-warning]').each(function() {
                $(this).show();
            });
            setDataSetDeletionParameters(dropdown, "dataSet");
        } else {
            dropdown.find('[data-trigger="dataSet-metadata"]').prop('checked', false).attr("disabled", false);
            dropdown.find('[data-trigger="dataSet-data"]').prop('checked', false).attr("disabled", false);

            $('[data-warning]').each(function() {
                $(this).hide();
            });
        }
    });

    $('[data-trigger="dataSet-metadata"]').on('change', function() {
        var dropdown = $(this).closest('.dropdown');

        if($(this).is(':checked')) {
            dropdown.find('[data-trigger="dataSet-data"]').prop('checked', true).attr("disabled", true);
            $('[data-warning="dataSet-metadata"]').show();
            $('[data-warning="dataSet-data"]').show();
            setDataSetDeletionParameters(dropdown, "dataSet-metadata");
        } else {
            dropdown.find('[data-trigger="dataSet-data"]').prop('checked', false).attr("disabled", false);
            $('[data-warning="dataSet-metadata"]').hide();
            $('[data-warning="dataSet-data"]').hide();
        }
    });

    $('[data-trigger="dataSet-data"]').on('change', function() {
        var dropdown = $(this).closest('.dropdown');

        if($(this).is(':checked')) {
            $('[data-warning="dataSet-data"]').show();
            setDataSetDeletionParameters(dropdown, "dataSet-data");
        } else {
            $('[data-warning="dataSet-data"]').hide();
        }
    });

    $('.form-check-input').on('change', function() {
        var checked = $(this).closest('.dropdown').find('.form-check-input:checked').length;

        if(checked > 0) {
            $(this).closest('.dropdown').find('.dropdown-delete').removeClass('is-disabled')
        } else {
            $(this).closest('.dropdown').find('.dropdown-delete').addClass('is-disabled')
        }
    });

    $('[data-trigger="modal"]').click(function() {
        $('[data-item="modal"]').addClass('is-visible');
        $('body').addClass('is-locked').find('.modal__overlay').addClass('is-visible');
        $('body').css('padding-right', window.getScrollbarWidth());
    });

    $('[data-trigger="close"]').click(function() {
        setTimeout(function(){

           $('.dropdown').removeClass('is-open');
           $('.dropdown').find('.form-check-input').prop('checked', false);
           $('.dropdown').find('.dropdown-delete').addClass('is-disabled');

            $('[data-item="modal"]').removeClass('is-visible');
            $('body').removeClass('is-locked').find('.modal__overlay').removeClass('is-visible');
            $('body').removeAttr('style');

           $('[data-warning]').each(function() {
             $(this).hide();
           });

        }, 100);
    });

    $('.modal__overlay').click(function() {
        $(this).removeClass('is-visible');
        $('body').removeClass('is-locked').find('[data-item="modal"].is-visible').removeClass('is-visible');
    });
});

function updateLinkDataSetId(anchorObject, dataSetId) {
    var url = anchorObject.attr('href');
    url = url.substr(0, url.indexOf('dataSetId')) + 'dataSetId=' + dataSetId;
    anchorObject.attr('href', url);
}

function showSpinner() {
	$('.loading').addClass('visible');
}

(function() {
  'use strict';
  var getScrollbarWidth, scrollbarWidth;

  scrollbarWidth = null;

  getScrollbarWidth = function(recalculate) {
    var div1, div2;
    if (recalculate == null) {
      recalculate = false;
    }
    if ((scrollbarWidth != null) && !recalculate) {
      return scrollbarWidth;
    }
    if (document.readyState === 'loading') {
      return null;
    }
    div1 = document.createElement('div');
    div2 = document.createElement('div');
    div1.style.width = div2.style.width = div1.style.height = div2.style.height = '100px';
    div1.style.overflow = 'scroll';
    div2.style.overflow = 'hidden';
    document.body.appendChild(div1);
    document.body.appendChild(div2);
    scrollbarWidth = Math.abs(div1.scrollHeight - div2.scrollHeight);
    document.body.removeChild(div1);
    document.body.removeChild(div2);
    return scrollbarWidth;
  };

  if (typeof define === 'function' && define.amd) {
    define([], function() {
      return getScrollbarWidth;
    });
  } else if (typeof exports !== 'undefined') {
    module.exports = getScrollbarWidth;
  } else {
    this.getScrollbarWidth = getScrollbarWidth;
  }

}).call(this);
