nuxeo-cm-demo-utils
===================

A plug-in for the Case Management demo (cm.cloud.nuxeo.com)

## List of Features (details below)
* [WeatherHistory](#weatherhistory), an example of calling an external WebService
* [JavaScript Dashboard](#javascript-dashboard), for quick stats on cases
* [Update Demo Data](#update-demo-data), for quick stats on cases

## Build

_Note_: You can also get the .zip of the market place package in the "Releases" part of this project (on GitHub)

_Other Note_: This project is part of the Nuxeo's continuous integration system. As so, it declares a dependency against the Studio project deployed on cm.cloud.nuxeo.com => You may want to remove this dependency before building the plug-in. To remove this dependency, open `cm-demo-utils/pom.xml` and remove (or comment) the following lines:

  ```
  <dependency>
    <groupId>nuxeo-studio</groupId>
    <artifactId>cm-showcase-nux</artifactId>
    <version>0.0.0-SNAPSHOT</version>
  </dependency>
  ```

Assuming maven is correctly setup on your computer:

    ```
    cd /path/to/nuxeo-cm-demo-utils
    mvn package
    # The plug-in is in /target, named nuxeo-cm-demo-utils-{version}.jar
    ```

## WeatherHistory
This operation receives the name of a city and a date, and fills a specific
field of the current document with weather informations.

This is an example, a Proof Of Concept, to be run with Nuxeo's InsuranceClaim
demo. The purpose is to give an example of calling an external service from
the server. Here, we get the weather, but it shows how you could get any
information from any service. Typical example would be to retrieve data from
another application, running in the same system: Get a person information,
get a contract information, ...

WARNING and IMPORTANT

* This is a typical POC demo. We don't do a lot of error-check here, to let the code as much readable as possible: No check on the JSON nodes, no check if the schema is valid, ...
* Also: We update a specific field in a specific schema, and then we save the document

	=> Current document _must have_ the `incl` schema and the `incl:incident_weather` String field

* **Fill the Bing and Forecast.io keys with your own keys in nuxeo.conf**. You need to add two entries in nuxeo.conf:
    ```
    demo.weatherhistory.forecastio.key=YOUR forecast.io KEY HERE
    demo.weatherhistory.bing.key=YOUR Bing KEY HERE 
    ```


    Getting a code from these services is easy and free, as of "today", 2013-11-25

    => _The operation throws an error  if no keys are found_.

## JavaScript Dashboard
This dashboard displays quick stats about `InsuranceClaim` documents that are in the current `Queue`  document. It displays the stats by comparying the ìncl:due_date` field with the current date:

* "Past due": % of not-completed claims whose `due_date` > today
* "on Schedule": % of not completed claims whose `due_date` >= today
* "Done": % of completed claims (whatever the `due_date`

**IMPORTANT #1**: The dashboard is displayed using a `.xhtml` widget template, which is displayed (in the Studio configuraiton) in a Tab, for a `Queue` document. It will work only in this context, since it is using fields of the current `Queue` document to query the claims.

**IMPORTANT #2**: Because it uses "today", "now', make sure you have updated the demo data (see "Update Demo Data"), or you will have only "Past due" or "Completed" elements

The `.xhtml` file is very small, everything is done using JavaScript, in one REST call + Parsing of the result.


## Update Demo Data
The plug-in has a class (and an Operation, usable in Studio, which calls this class) which:

* Updates all the existing claims: Change their creation date, creation user, modification info, due_date, etc., so the data looks recent. _WARNING_: Title and claim-id are changed, since it uses the date of the case and the code makes it more recent.
* Creates a claim with more information, and updates its lifecycle state to `DecisionMade`, so it is usable with the Template Rendering plug-in.

## About Nuxeo

Nuxeo provides a modular, extensible Java-based [open source software platform for enterprise content management](http://www.nuxeo.com/en/products/ep) and packaged applications for [document management](http://www.nuxeo.com/en/products/document-management), [digital asset management](http://www.nuxeo.com/en/products/dam) and [case management](http://www.nuxeo.com/en/products/case-management). Designed by developers for developers, the Nuxeo platform offers a modern architecture, a powerful plug-in model and extensive packaging capabilities for building content applications.

More information on: <http://www.nuxeo.com/>
