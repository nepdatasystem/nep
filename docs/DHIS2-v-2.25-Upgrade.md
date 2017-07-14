DHIS 2 Version 2.25 Upgrade From 2.24
=====================================

When upgrading DHIS 2 from version 2.24 to 2.25, you will need to coordinate the upgrading of DHIS 2 itself with the
 upgrade of the **NEP Custom Data Import App to 2.25**. 
  
Please refer to the [DHIS 2 version 2.25 Upgrade Instructions](https://www.dhis2.org/225-upgrade) on the 
[DHIS 2 website](https://www.dhis2.org).
 
 
1\. Build and Install the DHIS2Connector Grails plugin from the Connector-v2.25 branch

2\. Build the **NEP Custom Data Import App to 2.25** from the nep-v2.25 branch

3\. Log into the Web Server Machine

4\. Ensure no users are accessing the instance by checking the access logs for activity

```bash
tail -f /var/log/tomcat7/localhost_access_log.<current_date>.txt
```

5\. Backup the DHIS 2 database

6\. Download the DHIS 2 v 2.25 war file

```bash
wget https://www.dhis2.org/download/releases/2.25/dhis.war -O dhis225.war
```

7\. Drop SQL Views From Within DHIS 2 UI

```bash
DHIS2 -> Data Administration -> Maintenance - Drop SQL Views
```

8\. Stop Tomcat

```bash
sudo service tomcat7 stop
```

9\. Deploy DHIS2 v 2.25 WAR

Copy the war and clear out the existing extracted war (these instructions assume the application is running 
in the root)

```bash
rm -fr /var/lib/tomcat7/webapps/ROOT*
cp dhis225.war /var/lib/tomcat7/webapps/ROOT.war
chown tomcat:tomcat /var/lib/tomcat7/webapps/ROOT.war             
```
   
10\. Update DHIS 2 Database with 2.25 DB Script

See https://github.com/dhis2/dhis2-utils/blob/master/resources/sql/upgrade-225.sql

Connect to your dhis2 database and run the following script:

```sql
-- 2.25 upgrade script
-- Add deleted column to datavalue table
-- This will be handled by DHIS 2, however running this script will be much faster in comparison for large datavalue tables
begin;
-- 1) Drop indexes and foreign keys
alter table datavalue drop constraint datavalue_pkey;
drop index in_datavalue_lastupdated;
-- 2) Add deleted column, set to false, set to not-null and create index
alter table datavalue add column deleted boolean;
update datavalue set deleted = false where deleted is null;
alter table datavalue alter column deleted set not null;
create index in_datavalue_deleted on datavalue(deleted);
-- 3) Recreate indexes and foreign keys
alter table datavalue add constraint datavalue_pkey primary key(dataelementid, periodid, sourceid, categoryoptioncomboid, attributeoptioncomboid);
create index in_datavalue_lastupdated on datavalue(lastupdated);
end;
```

11\. Update nep.properties

```bash
    vi /opt/nep_home/nep.properties
    # update line:
    dhis2.api.version=2.25
```

12\. Deploy NEP war and restart Tomcat 

Copy built war file to Tomcat webapps folder then start Tomcat

```bash
cp nep.war /var/lib/tomcat7/webapps/
sudo service tomcat7 start
```

13\. Update & Execute SQL views in DHIS 2

The relationship between DataElements and DataSets have changed in 2.25. 

The custom SQL view definition for "Data Sets With Data" needs to be updated in DHIS 2:

```bash
DHIS2 -> Data Administration -> SQL View 
```

Click on "Data Sets With Data" → edit

```sql
SELECT DISTINCT ds.uid 
FROM datasetelement dse INNER JOIN dataset ds ON dse.datasetid=ds.datasetid 
WHERE EXISTS (SELECT 1 FROM datavalue dv WHERE dv.dataelementid=dse.dataelementid);
```

Execute all views

```bash
DHIS2 -> Data Administration -> Maintenance -> SQL View - click on each view and execute
```
