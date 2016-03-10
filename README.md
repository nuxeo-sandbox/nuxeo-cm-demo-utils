# About nuxeo-cm-demo-utils

A plug-in for the Case Management demo (cm.cloud.nuxeo.com)

## List of Features (Details below)

* [Weather History](#weather-history), an example of calling an external WebService
* [Polymer Dashboard](#polymer-dashboard), for quick stats on cases
* [Update Demo Data](#update-demo-data), updates claim data to make it more recent


### Weather History

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


### Polymer Dashboard

This dashboard displays quick stats about `InsuranceClaim` documents that are in the current `Queue` document. It displays the stats by comparying the `ìncl:due_date` field with the current date:

* "Past due": % of not-completed claims whose `due_date` > today
* "on Schedule": % of not completed claims whose `due_date` >= today
* "Done": % of completed claims (whatever the `due_date`)

**IMPORTANT #1**: The dashboard is displayed using a `.xhtml` widget template, which is displayed (in the Studio configuraiton) in a Tab, for a `Queue` document. It will work only in this context, since it is using fields of the current `Queue` document to query the claims.

**IMPORTANT #2**: Because it uses "today", "now", make sure you have updated the demo data (see "Update Demo Data"), or you will have only "Past due" or "Completed" elements.

The `.xhtml` file uses a Polymer element defined in the `nuxeo-cm-demo-elements` plug-in.


### Update Demo Data

The plug-in has a class (and an Operation, usable in Studio, which calls this class) which:

* Updates all the existing claims: Change their creation date, creation user, modification info, due_date, etc., so the data looks recent. _WARNING_: Title and claim-id are changed, since it uses the date of the case and the code makes it more recent.
* Creates a claim with more information, and updates its lifecycle state to `DecisionMade`, so it is usable with the Template Rendering plug-in.


## Build

[![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-cm-demo-utils-master)](https://qa.nuxeo.org/jenkins/view/sandbox/job/Sandbox/job/sandbox_nuxeo-cm-demo-utils-master/)

_Note_: This project expects specific schemas to be implemented in your solution and depends on the Studio project deployed on cm.cloud.nuxeo.com => If you plan to use it (and not just look at the code) then make sure your own projects have the same schemas and fields (`InsuranceClaim` schema with expected lifecycle states, ...)

Assuming maven is correctly setup on your computer:

First build the [nuxeo-datademo](https://github.com/nuxeo-sandbox/nuxeo-datademo) plug-in. Then build the `nuxeo-cm-demo-utils` package.

    cd /path/to/nuxeo-cm-demo-utils
    mvn package

The plug-in will be placed at is in `nuxeo-cm-demo-mp/target/`, named nuxeo-cm-demo-utils-{version}.zip

## Support

**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into platform, not maintained here.


## Licensing

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)


## About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris.

More information is available at [www.nuxeo.com](http://www.nuxeo.com).
