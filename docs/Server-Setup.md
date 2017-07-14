NEP Custom Data Import App - Server Setup
=========================================

The **NEP Custom Data Import App** relies on a running DHIS 2 Instance running a compatible version. The current 
versions supported by the NEP Custom Data Import app are:

- 2.24
- 2.25

Please see the [DHIS 2 Downloads page](https://www.dhis2.org/downloads) to download DHIS 2 and follow their 
[documentation](https://www.dhis2.org/documentation) to install and configure it prior to setting up and 
installing the **NEP Custom Data Import App**.

The **NEP Custom Data Import App** can either be installed on the same server as the DHIS 2 instance or on a different
server.

Server Infrastructure
---------------------

The **NEP Custom Data Import App** requires the following to be installed on the server:

- Java (tested with 1.8.31)
- PostgreSQL (tested with 9.4)
- Tomcat (tested with Tomcat 7)
- NGINX (recommended)

Application Installation and Configuration
------------------------------------------
Install and configure the **NEP Custom Data Import App** by completing the following steps:

### Database Setup

The **NEP Custom Data Import App** relies on an underlying database which is used solely for storage of batch job
information. All other data is stored in the DHIS 2 data store via the DHIS 2 Web API. The application has been tested 
with PostgreSQL 9.4.

First create the nep database

```sql
createdb nep
psql -c "CREATE USER nep WITH PASSWORD '<password here>';" nep
psql -c "GRANT ALL PRIVILEGES ON DATABASE nep to nep" nep
```
Ensure that the password you have used is updated in the following file for the corresponding environment:

```bash
/nep/grails-app/conf/DataSource.groovy
```

### Application Configuration 
The **NEP Custom Data Import App** is configured by settings in the nep.properties file. A copy of the file which is set
to defaults for a locally run instance can be found here

```bash
/nep/grails-app/conf/nep.properties
```
Country-specific settings are here:

```bash
/nep/grails-app/conf/data/<country>/nep.<country>.properties
```

1\. Make a directory on the server which will be the home directory for the **NEP Custom Data Import App**

* eg: /opt/nep_home
    
2\. Place a copy of the nep.properties file in the NEP home directory

Fix permissions for the file

```bash
chown tomcat:tomcat NEP_HOME/nep.properties
``` 

(note - if you are running tomcat as a different user other than 'tomcat', use that other user instead)

3\. Update the nep.properties file with configuration specific to your instance.

Ensure that the two first settings are set to where your target DHIS 2 instance is running. eg:

````bash
dhis2.server=http://myserver.com
dhis2.context=/dhis  
# if there is no sub context path, leave this blank:  
# dhis2.context=  
````

Ensure that the DHIS 2 version is set with the corresponding DHIS 2 version you are using:

```bash
dhis2.api.version=2.24
```

Configure the names you would like for the DHIS 2 sql views. These need to match what you will create in the     below section for DHIS 2 Configuration.

Eg:

```bash
nep.sqlview.datasets.with.data.name=Data Sets With Data  
nep.sqlview.programs.with.data.name=Programs With Data  
nep.sqlview.program.stages.with.data.name=Program Stages With Data  
```

4\. Expose the NEP home directory to the webserver. These instructions assume the use of Tomcat 7:

* edit /usr/share/tomcat7/conf/tomcat7.conf
* add NEP_HOME='/opt/nep_home'
* if instance is to be in a different language, add lang=\<lang\>
* eg: 
    * for Mali (French):
    lang=fr
    * for Mozambique (Portuguese)
    lang=pt 

5\. Specify environment if this is a QA environment

* add -Dgrails.env=qa to JAVA_OPTS if QA server
* Configuration will otherwise default to production


### DHIS 2 Configuration
A few extra steps of DHIS 2 configuration need to happen with DHIS 2 itself to accommodate the 
**NEP Custom Data Import App**.

#### Set up of Organisation Units
Set up or import organisation unit structure for specific country.

* If you wish to use the organisation unit structure used with the NEP Platform, do the following:
    * Use the Import-Export DHIS 2 app and load the file from nep/nep/grails-app/conf/data/\<country>/\<countryOrgUnits>.xml
    * Choose the DHIS2 Import option - "Meta-Data Import"
        * Note: If you wish, you can set the import option "Dry run = Yes" if you want to just see if it would work, then if all good, repeat with "Dry run = No".
    * Ensure Org Unit Levels are properly set up in DHIS 2
        * English level names:
            * Level 1 - Country
            * Level 2 - Region
            * Level 3 - District
            * Level 4 - Facility
            * Level 5 - ReportingUnit
        * Portuguese level names:
            * Level 1 - País
            * Level 2 - Região
            * Level 3 - Distrito
        * French level names:
            * Level 1 - Pays
            * Level 2 - Région
            * Level 3 - Cercle
            * Level 4 - District

You can provide locale-specific labels for english and french/portuguese for each level

#### Users And Roles
The DHIS 2 user role that is required for access to the **NEP Custom Data Import App** is: Data Manager 
(or whatever has been specified in the nep.properties file).

Eg: 

```bash
nep.userRoles=ROLE_DATA_MANAGER:Data Manager
```
The base user roles (Superuser, Data Manager) with their associated permissions have been committed here: 

```bash
/nep/grails-app/conf/data/Malawi/malawiUserRoles.xml
```
* Log into your DHIS 2 instance and either import these roles via the metadata import DHIS 2 app, or manually create and 
set them up to match the permissions in the committed file
* Update the admin user to have the Data Manager role and permission for the root org unit (country level)
* Ensure any users you create in DHIS 2 that you wish to grant access to the **NEP Custom Data Import App** have been 
assigned the Data Manager role you created

#### Tracked Entity Setup
Assuming that Household surveys will be imported via the , you will need to set up a Household tracked entity in DHIS 2.

Log into your DHIS 2 instance and insert the following new Tracked Entity using the DHIS 2 App by navigating to:

```bash
Programs / Attributes -> Tracked Entity
```

```bash
Household:
Name: Household
Description: Household surveys tracking births and women
```

#### Set Up Custom Sql Views
The **NEP Custom Data Import App** depends on three custom SQL views that need to be set up within DHIS 2. 

Log into your DHIS 2 instance and create the following three views by navigiating to:

```bash
 Data Administration -> SQL View -> Add New
```

Ensure you use the correct views below for the version of DHIS 2 that you are using.

1\. nep.sqlview.datasets.with.data.name from nep.properties ("Data Sets With Data"):

**For Version 2.24:**
```sql
SELECT DISTINCT ds.uid   
   FROM datasetmembers dsm   
   INNER JOIN dataset ds ON dsm.datasetid=ds.datasetid   
   WHERE EXISTS   
    (SELECT 1 FROM datavalue dv WHERE dv.dataelementid=dsm.dataelementid);   
```

***OR* For Version 2.25:**

```sql
SELECT DISTINCT ds.uid   
   FROM datasetelement dse   
   INNER JOIN dataset ds ON dse.datasetid=ds.datasetid    
 WHERE EXISTS    
   (SELECT 1 FROM datavalue dv WHERE dv.dataelementid=dse.dataelementid);  
```
    
2\. nep.sqlview.programs.with.data.name ("Programs With Data")

```sql
SELECT DISTINCT pr.uid    
   FROM program pr   
   INNER JOIN programinstance pi ON pr.programid = pi.programid   
   INNER JOIN trackedentityinstance tei ON pi.trackedentityinstanceid = tei.trackedentityinstanceid;  
```

3\. nep.sqlview.program.stages.with.data.name from nep.properties ("Program Stages With Data"):

```sql
SELECT DISTINCT ps.uid   
   FROM programstage ps   
   INNER JOIN programstageinstance psi ON ps.programstageid = psi.programstageid;   
```

After each creation, select each one and execute the sql view.

### NGINX Configuration

If using NGINX, update the ngnix config to allow for a longer timeout due to long upload times
 
Edit /etc/nginx/nginx.conf and add this line:

```bash
proxy_read_timeout 600s;
```   
 
Restart NGINX
```bash
service nginx restart
```   

### Deployment of the NEP Custom Data Import App
To deploy the **NEP Custom Data Import App**, place the built war (Web Application Resource or Web application ARchive)
file into the Tomcat webapps directory and start Tomcat.

Please see [Build and Deployment](Build-and-Deployment.md) for instructions on building the 
**NEP Custom Data Import App** war file.

Install NEP DHIS 2 App 
----------------------
The NEP DHIS 2 App is the launcher DHIS 2 App for NEP Custom Data Import App Within DHIS 2.

1\. Locate the NEP DHIS 2 App which is in zip file format. The built zip files for each language are here:

* /nep-data-import/nep-data-import-en.zip
* /nep-data-import/nep-data-import-fr.zip
* /nep-data-import/nep-data-import-pt.zip

1a\. Alternately if you need to modify the application, you can build it from source. You will need to have gradle 
installed to do this. See [Gradle Installation Instructions](https://gradle.org/install). 

There is a gradle build script to create the app for each country(language).  

Run the following from nep/nep-data-import:
    
```bash
gradle build -Plang=en  
gradle build -Plang=fr  
gradle build -Plang=pt  
```

Alternatively, a comma separated list of languages can be supplied

```bash
gradle build -Plang=en,fr,pt 
```

It creates zip files in the project root as follows:

* nep-data-import-en.zip  
* nep-data-import-fr.zip  
* nep-data-import-pt.zip  

2\. Install the app into the DHIS 2 instance:

* Log into DHIS 2
* Navigate to Apps -> App Management
* Upload App package (ZIP) by choosing applicable language zip file created in previous step
* Ensure NEP Data Import App is now installed and available.
* Ensure launching app redirects user to NEP Data Import App.
 
