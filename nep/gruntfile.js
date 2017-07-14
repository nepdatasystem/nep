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

'use strict';

module.exports = function(grunt) {

    grunt.loadNpmTasks('grunt-sass');
    grunt.loadNpmTasks('grunt-autoprefixer');
    grunt.loadNpmTasks('grunt-contrib-cssmin');
    grunt.loadNpmTasks('grunt-contrib-watch');

    // Configurable paths
    var config = {
        app: 'grails-app/assets'
    };

    grunt.initConfig({

        config: config,

        // Reference package.json
        pkg: grunt.file.readJSON('package.json'),

        // Grunt-sass
        sass: {
            app: {
                // Takes every file that ends with .scss from the scss
                // directory and compile them into the css directory.
                // Also changes the extension from .scss into .css.
                // Note: file name that begins with _ are ignored automatically
                files: [{
                    expand: true,
                    cwd: '<%= config.app %>/scss',
                    src: ['*.scss'],
                    dest: '<%= config.app %>/stylesheets',
                    ext: '.css'
                }]
            },
            options: {
                includePaths: [
                    'bower_components/susy/sass',
                    'bower_components/breakpoint-sass/stylesheets'
                ],
                sourceMap: true,
                outputStyle: 'nested',
                imagePath: '<%= config.app %>/images'
            }
        },

        // Auto Prefixer
        autoprefixer: {
            dist: {
                options: {
                    browsers: ['> 1%', 'last 3 versions', 'Firefox ESR', 'Opera 12.1']
                },
                files: {
                    '<%= config.app %>/stylesheets/main.css': ['<%= config.app %>/stylesheets/main.css']
                }
            }
        },

        // Minify CSS
        cssmin: {
            combine: {
                files: {
                    '<%= config.app %>/stylesheets/main.css': ['<%= config.app %>/stylesheets/main.css']
                },
            },
        },

        // Watch
        watch: {
            sass: {
                files: '<%= config.app %>/scss/**/*.scss',
                tasks: ['sass'],
            },

            csspostprocess: {
                files: '<%= config.app %>/stylesheets/main.css',
                tasks: ['autoprefixer', 'cssmin'],
            },

            livereload: {
                options: {livereload: true},
                files: ['<%= config.app %>/stylesheets/*.css', '<%= config.app %>/javascripts/*.js'],
            },
        },

    });

    grunt.registerTask('default', ['watch']);
};
