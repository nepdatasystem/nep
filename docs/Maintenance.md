NEP Custom Data Import App - Application Maintenance
====================================================

Maintaining the **NEP Custom Data Import App** codebase involves straightforward Groovy/Grails programming. 

Internationalization / Translations
-----------------------------------

The **NEP Custom Data Import App** is available in English, French and Portuguese, using the built in 
[Grails Internationalization support](http://docs.grails.org/2.4.5/guide/i18n.html).
All language used in the application is contained in the following three language files:

```bash
/nep/grails-app/i18n/messages_en.properties 
/nep/grails-app/i18n/messages_fr.properties 
/nep/grails-app/i18n/messages_pt.properties 
```

When creating new functionality, ensure each message property is present and translated into each of the language files.
  
Note that diacritics may need to be escaped in these files. There is some strange behaviour in Grails in regards to
the need for escaping apostrophes for messages that have arguments, but not for messages with no arguments. Therefore,
apostrophes need to escaped with a second apostrophe for messages having arguments.

Please see 
[related post](http://www.componentix.com/blog/31/disappearing-apostrophes-in-localized-messages-of-grails-application)

Coding DHIS 2 Upgrades
----------------------
When upgrading the codebase to be compatible with a new version of DHIS 2, development effort will vary depending mainly
upon the scope of changes made within DHIS 2, specifically in regards to the WEB API.

Your first step will be to review the upgrade and release notes supplied by the DHIS 2 team on the 
[DHIS 2 website](https://www.dhis2.org/).
 
Note any documented changes to the WEB API and update the codebase in a new branch for the version accordingly. The 
majority of the changes will likely need to be made directly in the DHIS2Connector Grails Plugin.

You will need to be mindful of:

1. Domain model changes
    * EG: in the 2.25 upgrade, the association between dataSet and dataElement was changed and required rewriting of 
    related portions of the application, including SQL View definitions. 
2. WEB API Request changes
3. WEB API Response changes

Our experience has been that these changes are not typically fully documented, so a codebase version upgrade often
involves comprehensive testing of the entire system and discovering differences / issues on a trial-and-error basis.

When rolling out the version upgrade, ensure the nep.properties file property 

```bash
dhis2.api.version
```

is updated to the appropriate version number.

See an example upgrade rollout process: [DHIS 2 version 2.25 Upgrade From 2.24](DHIS2-v-2.25-Upgrade.md)
