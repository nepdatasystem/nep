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

});

function updateLinkDataSetId(anchorObject, dataSetId) {
    var url = anchorObject.attr('href');
    url = url.substr(0, url.indexOf('dataSetId')) + 'dataSetId=' + dataSetId;
    anchorObject.attr('href', url);
}

function showSpinner() {
	$('.loading').addClass('visible');
}
