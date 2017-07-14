NEP Custom Data Import App - Development Environment Setup
==========================================================

Setting up the **NEP Custom Data Import App** for development will involve the same steps outlined in the 
 [Server Setup Documentation](Server-Setup.md). 
 
Please follow those steps first. NGINX setup is optional for local development.

In addition to the server setup steps, you will need to install the following:

* Grails 2.4.5

If you will be doing CSS changes, you will also need to install the following:

* Node.js

### Installing the DHIS2Connector Grails Plugin
DHIS 2 services have been extracted out into a Grails Plugin:

```bash
DHIS2Connector
```

which resides in the dhis2-connector github repository. 

Clone this repository, ensuring you are using the correct DHIS 2 version (2.24 or 2.25).
Navigate to the DHIS2Connector plugin folder and run the following commands:

```bash
grails clean
grails compile
grails maven-install
```

This will install the plugin into your local maven repository and allow it to be referenced by the 
**NEP Custom Data Import App**.

To update the version of the plugin, edit the version value in the following file:

```bash
/dhis2-connector/DHIS2Connector/DHIS2ConnectorGrailsPlugin.groovy
```

You will need to update the plugins section of BuildConfig.groovy to use this new version and also refresh your 
dependencies in the NEP project to pick up the plugin.

Without updating the version, you may encounter issues with picking up a new version of the plugin when it has changed.
This may be resolved by updating the version number in application.properties of the plugin when there are code changes.  
Then update the version referenced in the BuildConfig.groovy file of the app using the plugin.

### Building and Running the **NEP Custom Data Import App**
You will need to define NEP_HOME system variable when running the app.

Build and run the NEP Custom Data Import App by running the following grails command from the /nep directory:

```bash
grails -Dgrails.server.port.http=<server port if applicable> run-app -DNEP_HOME="<nep_home_dir>"
```

eg:

```bash
grails -Dgrails.server.port.http=8090 run-app -DNEP_HOME="/opt/nep_home"
```

### NEP CSS Development

The NEP project uses SASS for CSS compilation. In order to compile the CSS, we use Grunt to build and Bower to manage 
dependencies. Both tools requires Node. If you do not have Node installed, Download it here:

```bash
https://nodejs.org/en/download/
```

If you do not have Grunt and Bower installed, you can install it via Node:

```bash
npm install -g grunt-cli bower
```

Once Grunt and Bower are installed, navigate to the projects root folder and run 

```bash
npm install
```

followed by

```bash
bower install
```
 
Once all packages have been installed run

```bash
grunt watch
```

which will poll for changes to the SASS files and compile them into CSS.
