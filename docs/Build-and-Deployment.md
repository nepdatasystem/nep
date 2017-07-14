NEP Custom Data Import App - Build and Deployment 
=================================================
A prerequisite to building the **NEP Custom Data Import App** is the installation of the DHIS2Connector Grails Plugin. 
See [Instructions for the installation of the DHIS2Connector Grails Plugin](DHIS2-connector-plugin.md).

You can simply build the deployable application war file from your local development machine with the command

```bash
grails war
```

If you are building for a different environment than production, you will need to explicitly specify the environment, 
eg:

```bash
grails -Dgrails.env=qa war
```

However, it is preferable that you would set up a Continuous Integration server such as [Jenkins](https://jenkins.io/) 
to manage your builds.
 
The built war file will be available at the following path:

```bash
/nep/target/nep-<version>.war
```

Once the war file is built, place the war file in Tomcat's webapp directory, with the appropriate name.
 
EG: If you are serving the application from the root URL for the webserver, the war will be called:

```bash
ROOT.war
```

If you are serving the application from a subcontext such as /nep, the war will be named accordingly: 

```bash
nep.war
```
