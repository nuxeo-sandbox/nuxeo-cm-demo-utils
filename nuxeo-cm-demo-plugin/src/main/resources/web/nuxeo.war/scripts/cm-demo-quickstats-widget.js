/* cm-demo-quickstats-widget.js
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
var gMainDivId,
	gDashboardMainObj;

/*	Pseudo-constants
 *
 *	This is a quick implementation, we did not take the time
 *	to create "real", non modifiable constants.
 */
var kFIELD_DUE_DATE = "incl:due_date";

var gTotalId = "cmdqsTotal_" + ("" + Math.random()).replace("0.", ""),
	gPastDueId = "cmdqsPastDue_" + ("" + Math.random()).replace("0.", ""),
	gDoneId = "cmdqsDone_" + ("" + Math.random()).replace("0.", ""),
	gOnScheduleId = "cmdqsOnSchedule_" + ("" + Math.random()).replace("0.", "");

function drawDashboard(inDocId, inIncidentKind, inState, inDivId) {
	
	gMainDivId = inDivId;
	gDashboardMainObj = jQuery("#" + gMainDivId);
	
	// Clear all
	gDashboardMainObj.empty();
	
	// Setup the main title
	_drawDashboardMainTitle(gDashboardMainObj);
	
	gDashboardMainObj.append("<div class='cmdqs_fonts no_claims'>Fetching stats...</div>");
	
	// ======================================

	// Do the same query as the Content View for Queue folder...
	//		ecm:mixinType != 'HiddenInNavigation'
	//		AND ecm:isCheckedInVersion = 0 
	//		AND ecm:primaryType='InsuranceClaim'
	//		AND ecm:currentLifeCycleState LIKE ?
	//		AND  incl:incident_kind LIKE ?
	// ... with dynamic parameters being "%" if the filed is NULL or empty.
	// and % must be escaped in the URL.
	if(!inIncidentKind || inIncidentKind == "") {
		inIncidentKind = "%25";//%";
	}
	if(!inState || inState == "") {
		inState = "%25";//%";
	}
	var queryStr = "SELECT * FROM InsuranceClaim"
					+ " WHERE ecm:mixinType != 'HiddenInNavigation'"
					+ " AND ecm:isCheckedInVersion = 0"
					+ " AND ecm:currentLifeCycleState LIKE '" + inState + "'"
					+ " AND incl:incident_kind LIKE  '" + inIncidentKind + "'";

	// Real life example would probably calculate stats on the server and just get the results
	// because since 5.9.3, it is easy to aggregate values with queryAndFetch() (this requires
	// a bit of Java code). here, we will pasre the JSON and it's ok
	queryStr = queryStr.trim() + "&pageSize=500";
	var theURL = "/nuxeo/site/api/v1/path///@search?query=" + queryStr;
	
	console.log(theURL);

	jQuery.ajax({
		url: theURL + "&pagesize = 3",
		contentType: "application/json+nxrequest",
		headers: {	"X-NXDocumentProperties": "InsuranceClaim"
				 }
	})
	.done(function(inData, inStatusText, inXHR) {
		_do_drawTheDashboard(inData, inStatusText, inXHR);
	})
	.fail(function(inXHR, inStatusText, inErrorText) {
		var div;
		
		gDashboardMainObj.empty();
		_drawDashboardMainTitle(gDashboardMainObj);
		
		div = "<div id='cmdqs_xhr_error' class='cmdqs_fonts cmdqs_xhr_error'>"
				+ "<p style='font-weight:bold'>An error occured</p>"
				+ "<p style='align: left'><b>Status</b>: " + inStatusText +" </p>"
				+ "<p style='align: left'><b>Error</b>: " + inErrorText +" </p>"
				+ "</div>";
		
		gDashboardMainObj.append( div );
	});
}

function _drawDashboardMainTitle(inJQueryObj) {
	inJQueryObj.append("<div id='cmdqs_stats_main_title' class='cmdqs_fonts cmdqs_main_title'>Due Date Overview</div>");
}

function _drawBarTotal(inDashboardObject, inDivId, inCount) {
	var div = "<div class='cmdqs_fonts cmdqs_main_class cmdqs_total_container'>";
			div += "<div id='" + inDivId +"' class='cmdqs_fonts cmdqs_main_result cmdqs_total'>";
				//div += "<div class='cmdqs_total'>" + inCount + "  Claims" + "</div>";
				div += inCount + "  Claim" + (inCount <2 ? "" : "s");
			div += "</div>";
		div += "</div>";

	inDashboardObject.append(div);
}

function _drawOneBar(inDashboardObject, inDivId, inLabel, inClass, inValue, inTotal) {
	var label = "",
		cssClasses = "",
		widthStyle = "",
		width = 0,
		spanStyle = "",
		containerClass = "";

	if(inTotal <= 0) {
		width = 0;
	} else {
		width = Math.round((inValue/inTotal) * 100);
		
	}
	label = inLabel + ": " + inValue + " (" + width + "%)";

	// We want to draw the beginning of the color
	if(width < 1) {
		width = 1;
	}
	// This draws the "progress bar":
	widthStyle = "style='width:" + width + "%;'"

	var div = "<div class='cmdqs_fonts cmdqs_main_class cmdqs_one_result_container'>";
			div += "<div class='cmdqs_one_bar cmdqs_one_result_label'>" + label + "</div>";
			div += "<div id='" + inDivId +"' class='cmdqs_fonts cmdqs_one_bar " + inClass +"' " + widthStyle + "/>";
		div += "</div>";

	inDashboardObject.append(div);
}

function _drawSummaryBars(inDashboardObject, inStats) {
	var w1, w2, w3;
	// This is interesting only when there are a bit of each.
	// In current demo data, it is most of the time 0 or 100, or 3, 1 and 97.
	// So, if this is the case, we just do nothing
	if( inStats.countClaims == 0
		|| (inStats.countPastDue/inStats.countClaims) > 0.9
		|| (inStats.countDone/inStats.countClaims) > 0.9
		|| (inStats.countOnSchedule/inStats.countClaims) > 0.9) {
		// Nothing
	} else {
		w1 = Math.round((inStats.countPastDue/inStats.countClaims) * 100);
		w2 = Math.round((inStats.countDone/inStats.countClaims) * 100);
		w3 = Math.round((inStats.countOnSchedule/inStats.countClaims) * 100);
		var div = "<div class='cmdqs_fonts cmdqs_main_class summary_stats'>";
				div += "<div class='cmdqs_pastDue summary_subdiv' style='width:" + w1 + "%' />";
				div += "<div class='cmdqs_done summary_subdiv' style='width:" + w2 +"%;' />";
				div += "<div class='cmdqs_onSchedule summary_subdiv' style='width:" + w3 + "%;' />";
			div += "</div>";

		inDashboardObject.append(div);
	}
}

/*	_do_drawTheDashboard
 *	
 */
function _do_drawTheDashboard(inData, inStatusText, inXHR) {
	var div,
		today,
		stats = {
			countClaims		: 0,
			countPastDue	: 0,
			countDone	: 0,
			countOnSchedule	: 0
		};
	
	// Clear all
	gDashboardMainObj.empty();
	
	// Setup the main title
	_drawDashboardMainTitle(gDashboardMainObj);

	// If no claim is found, drawing is quite easy ;->
	stats.countClaims = inData.entries.length;
	if(stats.countClaims === 0) {
		gDashboardMainObj.append("<div class='cmdqs_fonts no_claims'>(No Claims)</div>");
		return;
	}

	// Warning: Reading date on the current client. Should be ok in this example,
	// no time difference here.
	// We compare just dates, not times
	today = (new Date()).toISOString().substr(0, 10);

	// Calculate values
	inData.entries.forEach( function(inClaim) {
		var props = inClaim.properties,
			dueDate,
			state = inClaim.state;

		// ======================================== Get the values in local variables
		if(props[kFIELD_DUE_DATE]) {
			dueDate = (new Date(props[kFIELD_DUE_DATE])).toISOString().substr(0, 10);
		}
		
		// ======================================== Calculate values
		if(state === "DecisionMade" || state === "Archived") {
			stats.countDone += 1;
		} else if(today > dueDate) {
			stats.countPastDue += 1;
		} else {
			stats.countOnSchedule += 1;
		}
		
	}); // inData.entries.forEach

	_drawBarTotal(gDashboardMainObj, gTotalId, stats.countClaims);
	_drawOneBar(gDashboardMainObj, gPastDueId, "Past due", "cmdqs_pastDue", stats.countPastDue, stats.countClaims);
	_drawOneBar(gDashboardMainObj, gDoneId, "Done", "cmdqs_done", stats.countDone, stats.countClaims);
	_drawOneBar(gDashboardMainObj, gOnScheduleId, "On schedule", "cmdqs_onSchedule", stats.countOnSchedule, stats.countClaims);

	// Draw the summary bar
	_drawSummaryBars(gDashboardMainObj, stats);
}

//--EOF--

