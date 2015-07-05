/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thibaud Arguillere (nuxeo)
 */

package org.nuxeo.cm.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.core.collectors.DocumentModelCollector;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.runtime.api.Framework;

/**
 * @author Thibaud Arguillere
 * @since 5.8
 */
/**
 * This operation receives the name of a city and a date (format YYYY-MM-DDTHH:MM:SS), and fills a specific field of the
 * current document with weather informations. This is an example, a Proof Of Concept, to be run with Nuxeo's
 * InsuranceClaim demo. The purpose is to give an example of calling an external service from the server. Here, we get
 * the weather, but it shows how you could get any information from any service. Typical example would be to retrieve
 * data from another application, running in the same system: get a person information, get a contract information, ...
 * WARNING and IMPORTANT 1/ Typical POC demo. We don't do a lot of error-check here, to let the code as much readable as
 * possible: No check on the JSON nodes, no check if the schema is valid, ... 2/ Also: We update a specific field in a
 * specific schema, and then we save the document => Current document *must* exist and *must* have a String field
 * incl:incident_weather 3/ You need external system keys to get: -> The latitude/longitude given the name of a city =>
 * Done with a BING key (we use dev.virtualearth.net service) -> The weather info for this location at this date => Done
 * with a forecast.io key To avoid hardcoding the keys in this code, we use configuration entries in nuxeo.conf. So you
 * need to add the following to nuxeo.conf of the server: demo.weatherhistory.forecastio.key=YOUR forecast.io KEY HERE
 * demo.weatherhistory.bing.key=YOUR Bing KEY HERE (and of course, restart the server after changing the conf.) Getting
 * a dev. code from these services is easy and free (as of today, 2013-11-11) The super-amazing ;-> algorithm is: 1/ Get
 * the latitude/longitude for a city 2/ Build the forecast URL 3/ Connect and get the data 4/ Extract data from the JSON
 * string 5/ Update the incl:incident_weather property of the document
 */
@Operation(id = WeatherHistory.ID, category = Constants.CAT_DOCUMENT, label = "WeatherHistory", description = "Receives the name of a city and a date (YYYY-MM-DD), and fills the incl:incident_weather field.<BR/>Important: This is an example of calling an external service (2 actually, Bing and Forecast.io) from the server, to be adapted to your needs. This example requires that current document has a incl:incident_weather field.")
public class WeatherHistory {

    public static final String ID = "WeatherHistory";

    /**
     * The keys in nuxeo.conf
     */
    public static final String INSCLAIM_DEMO_FORECAST_CONF_KEYNAME = "demo.weatherhistory.forecastio.key";

    public static final String INSCLAIM_DEMO_BING_CONF_KEY = "demo.weatherhistory.bing.key";

    /**
     * URL to be used with the service: api.forecast.io/forecast/dev_key/latitude,longitude,date
     */
    public static final String FORECAST_URL = "https://api.forecast.io/forecast/%s/%s,%s";

    /**
     * URL to be used to get latitude-longitude
     */
    public static final String BING_URL = "http://dev.virtualearth.net/REST/v1/Locations?locality=%s&maxResults=1&key=%s";

    public static final String FIELD_TO_UPDATE = "incl:incident_weather";

    @Context
    protected CoreSession session;

    @Param(name = "city", required = true)
    protected String city;

    @Param(name = "dateStr", required = true)
    protected String dateStr;

    /**
     * Handling dev. keys for forecast.io and bing
     */
    private String _devKeyForecastio = "";

    private String _devKeyBing = "";

    private boolean _devKeyChecked = false;

    private boolean _devKeysOK = false;

    void _getServicesKeysFromConf() {

        if (!_devKeyChecked) {
            _devKeyForecastio = Framework.getProperty(INSCLAIM_DEMO_FORECAST_CONF_KEYNAME);
            _devKeyBing = Framework.getProperty(INSCLAIM_DEMO_BING_CONF_KEY);

            _devKeysOK = _devKeyForecastio != null && _devKeyBing != null;
            _devKeyChecked = true;
        }
    }

    @OperationMethod(collector = DocumentModelCollector.class)
    public DocumentModel run(DocumentModel input) throws PropertyException, IOException {
        String weather = "(unknown)";

        _getServicesKeysFromConf();

        if (!_devKeysOK) {
            throw new ClientException(
                    "forecast.io key and/or bing key are missing or invalid. Are they correctly set-up in nuxeo.conf?");
        }

        if (city != null && city != "" && dateStr != null && dateStr != "") {// We
                                                                             // don'
                                                                             // check
                                                                             // if
                                                                             // the
                                                                             // date
                                                                             // is
                                                                             // well
                                                                             // formatted

            String latLong = getFormattedLatitudeLongitudeForCity(city);

            // latLong empty means the city was not found
            if (latLong != "") {

                String query = String.format(FORECAST_URL, _devKeyForecastio, latLong, dateStr);

                try {
                    URL url = new URL(query);

                    BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                    StringBuffer sb = new StringBuffer();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        sb.append(inputLine);
                    }

                    // Extract JSON
                    // (Should check if a node if null before using it)
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(sb.toString());
                    JsonNode metadata = rootNode.get("daily");

                    JsonNode daily = metadata.get("data");
                    JsonNode theData = daily.get(0);

                    weather = "";
                    JsonNode aNode = null;

                    aNode = theData.get("summary");
                    weather += "Summary: " + aNode.getValueAsText();

                    aNode = theData.get("windBearing");
                    weather += "\nWind Bearing: " + aNode.getValueAsText();

                    aNode = theData.get("windSpeed");
                    weather += "\nWindSpeed: " + aNode.getValueAsText();

                    aNode = theData.get("humidity");
                    weather += "\nHumidity: " + aNode.getValueAsText();

                    in.close();

                } catch (IOException e) {
                    if (e.getMessage().indexOf("Server returned HTTP response code: 400") < 0) {
                        throw new RuntimeException(e);
                    }
                } finally {
                    // ...cleanup...
                }
            }
        }

        // Save doc
        input.setPropertyValue(FIELD_TO_UPDATE, weather);
        session.saveDocument(input);

        return input;
    }

    /**
     * Same for calling BING to get latitude/longitude
     */
    public String getFormattedLatitudeLongitudeForCity(String inCity) throws MalformedURLException {
        String latlong = "";

        inCity = inCity.replace(" ", "%20");

        if (inCity != "") {
            String query = String.format(BING_URL, inCity, _devKeyBing);

            try {
                URL url = new URL(query);

                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                StringBuffer sb = new StringBuffer();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    sb.append(inputLine);
                }
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(sb.toString());
                JsonNode metadata = rootNode.get("resourceSets");

                JsonNode firstResSet = metadata.get(0);
                JsonNode allRes = firstResSet.get("resources");
                JsonNode firstRes = allRes.get(0);
                JsonNode point = firstRes.get("point");
                JsonNode coord = point.get("coordinates");

                latlong = coord.get(0).getValueAsText();
                latlong += ",";
                latlong += coord.get(1).getValueAsText();

                in.close();

            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                // ...cleanup...
            }
        }

        return latlong;
    }

}
