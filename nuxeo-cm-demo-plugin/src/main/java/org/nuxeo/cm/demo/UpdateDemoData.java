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
 *     thibaud
 */
package org.nuxeo.cm.demo;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.datademo.RandomFirstLastNames;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener;
import org.nuxeo.ecm.platform.uidgen.UIDSequencer;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @author Thibaud Arguillere
 * @since 5.9.2 This class takes the InsuranceClaim and update them so the demo looks better. For example, when the data
 *        is reset every night from the same original backup, you find yourself with claims created beginning of 2013
 *        while you are in 2014 and you want stats from "the last month" => you will find nothing (or just the 1-2 cases
 *        you created during the demo) We update all data and set the dates to a random date form the last 3 months,
 *        with a bit more for the last month. ============================================================ IMPORTANT
 *        ============================================================ 1/ We don't want workflows to be started, mails
 *        to be sent, etc. So, to avoid some event handlers defined in the Studio project to be triggered, we use the
 *        following strategy: - The code defines a contextData property for the document:
 *        oneDoc.putContextData("UpdatingData_NoEventPlease", true); - In the project, the event handlers that must
 *        *not* be triggered when updating the data have the following EL condition:
 *        Document.doc.getContextData("UpdatingData_NoEventPlease") == null 2/ We create (if needed) a "nice claim" ( a
 *        claim with more info, so we can use the Template Rendering plug-in to display the case as pdf. => THE CODE
 *        CREATES IT IN "/Insurance Claims/Claims" => These domain/folder *mus* exist.
 *        ============================================================ THIS CODE HANDLES THE FOLLOWING:
 *        ============================================================ dc:created Half of the claims in the past month
 *        If a claim is "Archived", creation date is set between 15-30 days ago The other half: 2-3 months ago
 *        dc:modified dc:lastContributor dc:contributors These one, thanks to the act that it is possible to disable the
 *        dublincore listener. In this code, we disable it for each document:
 *        oneDoc.putContextData(DublinCoreListener.DISABLE_DUBLINCORE_LISTENER, true); (this is a super great feature)
 *        incl:contract_start/incl:contract_end One year, and "today" is in this range incl:date_opened is the creation
 *        data incl:due_date Same as Studio rule (cf. chain IC-AC-Claim-OnDocumentCreated) incl:date_closed Only if the
 *        claim is archived Set to 0-5 days ago dc:title Updated to reflect the changes in the creation date WARNING:
 *        the path cannot be changed, but it is ok. ============================================================ IT DOES
 *        NOT HANDLE: ============================================================ Dates of workflows (due dates of
 *        tasks for example) ================= WARNING WARNING WARNING WARNING WARNING ================= About changing
 *        the lifecycle state, we are using code that bypasses a lot of controls (so we avoid event sent, etc.).
 *        ============================================================================
 */
public class UpdateDemoData {

    private static final Log log = LogFactory.getLog(UpdateDemoData.class);

    static protected final int kSAVE_SESSION_MODULO = 25;

    // Acting as a constant. Not handling "deleted".
    static protected Map<String, Integer> _kSTATES = null;

    static protected final int kLFS_RECEIVED = 0;

    static protected final int kLFS_CHECK_CONTRACT = 1;

    static protected final int kLFS_OPENED = 2;

    static protected final int kLFS_COMPLETED = 3;

    static protected final int kLFS_EXPERT_ON_SITE_NEEDED = 4;

    static protected final int kLFS_EVALUATED = 5;

    static protected final int kLFS_DECISION_MADE = 6;

    static protected final int kLFS_ARCHIVED = 7;

    // This list should be loaded dynamically
    static protected final String[] kUSERS = { "john", "john", "john", "john", "kate", "kate", "kate", "alan", "julie",
            "julie", "mike" };

    static protected final int kMAX_FOR_USERS_RANDOM = kUSERS.length - 1;

    static private final String kNICE_CLAIM_PARENT_PATH = "/Insurance Claims/Claims";

    static private final String kNICE_CLAIM_FIELD = "dc:format";

    static private final String kNICE_CLAIM_FIELD_VALUE = "Nice claim for template rendering";

    static private final String[] kCITIES = { "New York", "New York", "New York", "Los Angeles", "Los Angeles",
            "Atlanta", "Seattle", "Boston", "Orlando" };

    static private final int kCITIES_MAX = kCITIES.length - 1;

    // Based on "AccidentTypologie" vocabulary
    static private final String[] ACC_TYPOLOGY = { "City/Parking", "City/Parking", "City/Parking", "City/Parking",
            "City/Parking", "City/Crossroads", "City/Crossroads", "City/Crossroads", "City/Avenue",

            "Country/Tunnel", "Country/Tunnel", "Country/Tunnel", "Country/Traffic Circle",

            "Highway/Ramp Access", "Highway/Ramp Access", "Highway/Ramp Access", "Highway/Gas Station",
            "Highway/Gas Station" };

    static private final int ACC_TYPOLOGY_MAX = ACC_TYPOLOGY.length - 1;

    // WARNING: UPDATE THIS citiesAndStates IF YOU CHANGE kCITIES
    static private HashMap<String, String> citiesAndStates;

    protected DateFormat _yyyyMMdd = new SimpleDateFormat("yyyy-MM-dd");

    protected Calendar _today = Calendar.getInstance();

    protected CoreSession _session;

    protected int _saveCounter = 0;

    RandomFirstLastNames randomPeopleNames;

    // SHould be temporary, the time for everybody to upgrade to latest Studio project
    boolean hasAccidentTypology = false;

    public UpdateDemoData(CoreSession inSession) throws IOException {
        _session = inSession;
        _setup();
    }

    public void run() throws Exception {

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        _UpdateData();

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

    private int _randomInt(int inMin, int inMax) {
        // No error check here
        return inMin + (int) (Math.random() * ((inMax - inMin) + 1));
    }

    private void _saveDocument(DocumentModel inDoc) {
        _session.saveDocument(inDoc);

        if ((++_saveCounter % kSAVE_SESSION_MODULO) == 0) {
            _doLog("Commiting the last " + kSAVE_SESSION_MODULO
                    + " InsuranceClaim (and their children.) Total InsuranceClaim handled: " + _saveCounter);
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    protected void _setup() throws IOException {
        _kSTATES = new HashMap<String, Integer>();
        _kSTATES.put("received", kLFS_RECEIVED);
        _kSTATES.put("checkcontract", kLFS_CHECK_CONTRACT);
        _kSTATES.put("opened", kLFS_OPENED);
        _kSTATES.put("completed", kLFS_COMPLETED);
        _kSTATES.put("expertonsiteneeded", kLFS_EXPERT_ON_SITE_NEEDED);
        _kSTATES.put("evaluated", kLFS_EVALUATED);
        _kSTATES.put("decisionmade", kLFS_DECISION_MADE);
        _kSTATES.put("archived", kLFS_ARCHIVED);

        citiesAndStates = new HashMap<String, String>();
        citiesAndStates.put("New York", "NY");
        citiesAndStates.put("Los Angeles", "CA");
        citiesAndStates.put("Atlanta", "GA");
        citiesAndStates.put("Seattle", "WA");
        citiesAndStates.put("Boston", "MA");
        citiesAndStates.put("Orlando", "FL");

        randomPeopleNames = RandomFirstLastNames.getInstance();

        SchemaManager sm = Framework.getLocalService(SchemaManager.class);
        Schema schema = sm.getSchema("InsuranceClaim");
        hasAccidentTypology = schema.getField("typology") != null;
        log.warn("Has incl:typology field: " + hasAccidentTypology);

    }

    protected int _lifecycleStateStrToInt(String inLCS) {
        Integer val = _kSTATES.get(inLCS);

        return val == null ? -1 : val;
    }

    // A wrapper. Someday, we should change this to it's own log file
    // In the meantime, just use "warn", because we don't want to
    // stop the server, change log level to debug, start server, etc.
    protected void _doLog(String inWhat) {
        log.warn(inWhat);
    }

    private Calendar _buildDate(Calendar inDate, int inDays) {
        Calendar d = (Calendar) inDate.clone();

        d.add(Calendar.DATE, inDays);
        if (d.after(_today)) {
            d = (Calendar) _today.clone();
        }

        return d;
    }

    private Calendar _buildDate(Calendar inDate, int inDays, boolean inOkIfAfterToday) {
        Calendar d = (Calendar) inDate.clone();

        d.add(Calendar.DATE, inDays);
        if (d.after(_today) && !inOkIfAfterToday) {
            d = (Calendar) _today.clone();
        }

        return d;
    }

    // Title update (replace 20130903 - for example - with the correct date)
    // Unfortunately, we have some cases which start with YYYY-MM-DD (instead
    // of the dashes-free version, YYYYMMDD), so we must handle that (and remove
    // dashes)
    // Does not save the document, just update the fields
    private void _updateTitle(DocumentModel inDoc, String dateStr) throws PropertyException {
        String title = (String) inDoc.getPropertyValue("dc:title");
        if (title.charAt(4) == '-') {
            title = dateStr.replaceAll("-", "") + title.substring(7);
        } else {
            title = dateStr.replaceAll("-", "") + title.substring(8);
        }
        inDoc.setPropertyValue("dc:title", title);
        inDoc.setPropertyValue("incl:incident_id", title);
    }

    // Does not save the document
    private void _updateModificationInfo(DocumentModel inDoc, String inUser, Calendar inDate) throws PropertyException,
            ClientException {
        inDoc.setPropertyValue("dc:lastContributor", inUser);

        // Handling the list of contributors: The following is a
        // copy/paste from...
        // nuxeo-platform-dublincore/src/main/java/org/nuxeo/ecm/
        // platform/dublincore/service/DublinCoreStorageService.java
        // ... with very little change (no try-catch for example)
        String[] contributorsArray;
        contributorsArray = (String[]) inDoc.getProperty("dublincore", "contributors");
        List<String> contributorsList = new ArrayList<String>();
        if (contributorsArray != null && contributorsArray.length > 0) {
            contributorsList = Arrays.asList(contributorsArray);
            // make it resizable
            contributorsList = new ArrayList<String>(contributorsList);
        }
        if (!contributorsList.contains(inUser)) {
            contributorsList.add(inUser);
            String[] contributorListIn = new String[contributorsList.size()];
            contributorsList.toArray(contributorListIn);
            inDoc.setProperty("dublincore", "contributors", contributorListIn);
            inDoc.setPropertyValue("dc:contributors", contributorListIn);
        }
        inDoc.setPropertyValue("dc:modified", inDate);
    }

    /*
     * Just to try-catch on Thread.sleep, so we don't have to throw or catch an InterruptedException
     */
    private void _wait(long inMs) {
        try {
            Thread.sleep(inMs);
        } catch (InterruptedException e) {
            // Should never be here: This thread is not the main threda. No
            // chance!
        }
    }

    /*
     * Update the demo data
     */
    private void _UpdateData() throws Exception {

        // First, we want to delete the "Nice Claim". Because updating it is
        // useless
        // and also because it generates some "ausitCoreListener" error about
        // the
        // thing not finding the document
        _niceClaimDelete();

        // Now, get all the claims
        String nxql = "SELECT * FROM InsuranceClaim";
        nxql += " WHERE ecm:isCheckedInVersion = 0";
        nxql += " AND ecm:currentLifeCycleState != 'deleted'";
        DocumentModelList allDocs = _session.query(nxql);

        _doLog("Update demo data: " + allDocs.size() + "document(s) to update");

        // ============================================================
        // Update existing cases
        // ============================================================
        for (DocumentModel oneDoc : allDocs) {
            Calendar aDate, startDate, creationDate, modifDate;
            String creator;

            updateLifecycleState(oneDoc);

            int lfs = _lifecycleStateStrToInt(oneDoc.getCurrentLifeCycleState().toLowerCase());
            String creationDateStr;

            // Half in previous month
            creationDate = (Calendar) _today.clone();
            switch (lfs) {
            case kLFS_ARCHIVED:
                creationDate.add(Calendar.DATE, _randomInt(30, 90) * -1);
                break;

            case kLFS_RECEIVED:
                creationDate.add(Calendar.DATE, _randomInt(2, 20) * -1);
                break;

            case kLFS_CHECK_CONTRACT:
                creationDate.add(Calendar.DATE, _randomInt(5, 30) * -1);
                break;

            case kLFS_OPENED:
                creationDate.add(Calendar.DATE, _randomInt(20, 40) * -1);
                break;

            case kLFS_COMPLETED:
                creationDate.add(Calendar.DATE, _randomInt(20, 40) * -1);
                break;

            case kLFS_EVALUATED:
                creationDate.add(Calendar.DATE, _randomInt(50, 80) * -1);
                break;

            case kLFS_DECISION_MADE:
                creationDate.add(Calendar.DATE, _randomInt(30, 90) * -1);
                break;

            default:
                creationDate.add(Calendar.DATE, _randomInt(31, 90) * -1);
                break;
            }

            creationDateStr = _yyyyMMdd.format(creationDate.getTime());
            oneDoc.setPropertyValue("dc:created", creationDate);
            oneDoc.setPropertyValue("incl:date_received", creationDate);

            // We don't want to build the exact same due_date as the Studio
            // project does
            // because we want mixed due_date for our JavaScript dashboard
            /*
             * String claimKind = (String) oneDoc.getPropertyValue("incl:incident_kind");
             * if(claimKind.equals("building-fire")) { aDate = _buildDate(creationDate, 90); } else
             * if(claimKind.equals("breakdown")) { aDate = _buildDate(creationDate, 10); } else { aDate =
             * _buildDate(creationDate, 30); }
             */
            // Say about 25% are past due (not always: If the case is closed,
            // archived
            // the dashboard does not even look at the due_date)
            if (_randomInt(1, 4) == 1) {
                aDate = _buildDate(_today, _randomInt(1, 10) * -1);
            } else {
                aDate = _buildDate(_today, _randomInt(1, 30), true);
            }
            oneDoc.setPropertyValue("incl:due_date", aDate);

            creator = kUSERS[_randomInt(0, kMAX_FOR_USERS_RANDOM)];
            oneDoc.setPropertyValue("dc:creator", creator);

            // Update the title and incident_id
            _updateTitle(oneDoc, creationDateStr);

            // Opening date of the case to this date, eventually 1-3 days before
            // "incl:date_received"
            startDate = (Calendar) creationDate.clone();
            int daysBefore = _randomInt(0, 3);
            if (daysBefore != 0) {
                startDate.add(Calendar.DATE, daysBefore * -1);
            }
            oneDoc.setPropertyValue("incl:incident_date", startDate);

            // Update contract start/end date. Say a range of one year with
            // today
            // in this range
            aDate = _buildDate(creationDate, _randomInt(30, 180) * -1);
            oneDoc.setPropertyValue("incl:contract_start", aDate);
            aDate.add(Calendar.DATE, 365);
            oneDoc.setPropertyValue("incl:contract_end", aDate);

            // If the case is closed, change the date of the closing
            // We also set the modifDate here because it is == closing date if
            // accurate
            // For Kibana stats, have modif dates in the last 3 months
            // IMPORTANT: This will not be accurate with the creation date,
            // it may happen the creation date becomes > modification
            modifDate = _buildDate(_today, _randomInt(0, 90) * -1);
            /*
             * if (lfs == kLFS_ARCHIVED) { aDate = _buildDate(_today, _randomInt(5, 90) * -1);
             * oneDoc.setPropertyValue("incl:date_closed", aDate); modifDate = (Calendar) aDate.clone(); } else { // Let
             * say it was modified recently... modifDate = _buildDate(_today, _randomInt(0, 10) * -1); }
             */
            _updateModificationInfo(oneDoc, kUSERS[_randomInt(0, kMAX_FOR_USERS_RANDOM)], modifDate);

            // Update first/last names
            oneDoc.setPropertyValue("pein:first_name", randomPeopleNames.getAFirstName(RandomFirstLastNames.GENDER.ANY));
            oneDoc.setPropertyValue("pein:last_name", randomPeopleNames.getALastName());

            String city = kCITIES[_randomInt(0, kCITIES_MAX)];
            oneDoc.setPropertyValue("incl:incident_city", city);
            oneDoc.setPropertyValue("incl:incident_us_state", citiesAndStates.get(city));

            if (hasAccidentTypology) {
                String kind = (String) oneDoc.getPropertyValue("incl:incident_kind");
                if (kind != null && kind.equals("accident")) {
                    oneDoc.setPropertyValue("incl:typology", ACC_TYPOLOGY[_randomInt(0, ACC_TYPOLOGY_MAX)]);
                }
            }

            // Now update some info of the children, if any
            DocumentModelList children = _session.getChildren(oneDoc.getRef());
            for (DocumentModel oneChild : children) {
                // Did not dig into the problem, but calling...
                // oneChild.setPropertyValue("dc:created", creationDate);
                // ...leads to a "property not found" error. The workaround
                // I found was about using setProperty() instead.
                oneChild.setProperty("dublincore", "created", creationDate);
                oneChild.setProperty("dublincore", "creator", creator);
                aDate = _buildDate(creationDate, _randomInt(0, 10));
                oneChild.setProperty("dublincore", "modified", aDate);
                oneChild.setProperty("dublincore", "lastContributor", kUSERS[_randomInt(0, kMAX_FOR_USERS_RANDOM)]);
                _session.saveDocument(oneChild);

                // Don't want to handle MailFolder (which is, actually, not used
                // in the demo)
                // We also don't handle recursivity, sub-sub-folders, etc.
                if (oneChild.isFolder() && oneChild.getType().toLowerCase().indexOf("mail") < 0) {
                    DocumentModelList grandChildren = _session.getChildren(oneChild.getRef());
                    for (DocumentModel oneGrandChild : grandChildren) {
                        if (oneChild.isFolder()) {
                            oneGrandChild.setPropertyValue("dc:created", creationDate);
                            // Nothing more
                        } else {
                            aDate = (Calendar) creationDate.clone();
                            aDate.add(Calendar.DATE, _randomInt(0, 30));
                            if (aDate.after(_today)) {
                                aDate = (Calendar) _today.clone();
                                aDate.add(Calendar.DATE, _randomInt(0, 3) * -1);
                            }
                            oneGrandChild.setPropertyValue("dc:created", aDate);
                            oneGrandChild.setProperty("dublincore", "creator", creator);
                            oneGrandChild.setProperty("dublincore", "modified", creationDate);
                            oneGrandChild.setProperty("dublincore", "lastContributor",
                                    kUSERS[_randomInt(0, kMAX_FOR_USERS_RANDOM)]);
                            oneGrandChild.putContextData(DublinCoreListener.DISABLE_DUBLINCORE_LISTENER, true);
                        }
                        _session.saveDocument(oneGrandChild);
                    }
                }
            }

            oneDoc.putContextData(DublinCoreListener.DISABLE_DUBLINCORE_LISTENER, true);
            // Make sure events are not triggered
            oneDoc.putContextData("UpdatingData_NoEventPlease", true);
            _saveDocument(oneDoc);
        } // for (DocumentModel oneDoc : allDocs)

        _doLog("Last commit. Total InsuranceClaim handled: " + _saveCounter);
        _session.save();

        // ============================================================
        // Create a nice case
        // ============================================================
        _niceClaimCreate();

        _session.save();
        _doLog("End of update demo data");
    }

    protected void updateLifecycleState(DocumentModel inDoc) {

        // ACTUALLY, NO. We consider that CreateDemoData has done the job already
        if (System.currentTimeMillis() != 0) { // Wich means "always" (want to keep the code below without comments or
                                               // having to go in git history)
            return;
        }

        String current = inDoc.getCurrentLifeCycleState();
        // We keep 5% of "Received"?
        if (current.equals("Received") && _randomInt(1, 20) > 1) {
            inDoc.putContextData(DublinCoreListener.DISABLE_DUBLINCORE_LISTENER, true);
            inDoc.putContextData("UpdatingData_NoEventPlease", true);
            inDoc.followTransition("to_CheckContract");

            int r = _randomInt(1, 100);
            // 57% of Archived +> we are at 62%
            if (r > 43) {
                inDoc.followTransition("to_Opened");
                inDoc.followTransition("to_Completed");
                inDoc.followTransition("to_Evaluated");
                inDoc.followTransition("to_DecisionMade");
                inDoc.followTransition("to_Archived");
            } else if (r > 7) { // 7% stay in CheckContract => we are at 69%
                inDoc.followTransition("to_Opened");
                if (r > 12) {
                    inDoc.followTransition("to_Completed");
                }
                if (r > 35) {
                    inDoc.followTransition("to_Evaluated");
                }
                // We ignore DesisionMade
            }
        }

    }

    /*
     * Centralize the way we find the nice claim
     */
    protected DocumentModelList _niceClaimQuery() {
        String nxql;

        nxql = "SELECT * FROM InsuranceClaim";
        nxql += " WHERE ecm:isCheckedInVersion = 0";
        nxql += " AND ecm:currentLifeCycleState = 'DecisionMade'";
        nxql += " AND " + kNICE_CLAIM_FIELD + " = '" + kNICE_CLAIM_FIELD_VALUE + "'";

        return _session.query(nxql);
    }

    protected void _niceClaimDelete() {
        DocumentModelList allDocs = _niceClaimQuery();

        int count = allDocs.size();
        if (count != 0) {
            _doLog("Deleting previous Nice claim(s)...");
            for (int i = 0; i < count; i++) {
                _session.removeDocument(allDocs.get(i).getRef());
            }
            _session.save();
            _doLog("...Nice claim(s) deleted");
        }
    }

    /*
     * ============================================================ Create a nice case
     * ============================================================ Create a full, completed claim that will be used to
     * show the template rendering To identify the nice claim, we have a ugly trick: We store an information in the
     * kNICE_CLAIM_FIELD field. We delete an existing one if any, to rebuild it. We wait one second before each
     * modification when changing the lifecycle state so the audit log will be correctly sort. This means creating the
     * nice claim takes basically all the time ;-).
     */
    protected void _niceClaimCreate() throws Exception {
        Calendar aDate, creationDate;
        String user, title;
        int count;
        DocumentModel niceClaim = null;

        _session.save();
        _wait(1000);

        DocumentModelList allDocs = _niceClaimQuery();
        count = allDocs.size();
        if (count != 0) {
            _doLog("Deleting previous Nice claim(s)...");

            for (int i = 0; i < count; i++) {
                _session.removeDocument(allDocs.get(i).getRef());
            }
            _session.save();
            _wait(2000);
            _doLog("...Nice claim(s) deleted");
        }

        creationDate = _buildDate(_today, -20);

        // We first create the claim and let the Studio config on events
        // take the hand ("empty doc created" and "document created")
        // (setting some values, required by the chains ran by these handlers)
        // We also handle a unique path for this nice claim
        UIDSequencer svc = Framework.getService(UIDSequencer.class);
        title = _yyyyMMdd.format(creationDate.getTime()) + "-ACC-" + Integer.toString(svc.getNext("ACC"));
        niceClaim = _session.createDocumentModel(kNICE_CLAIM_PARENT_PATH,
                "claim-" + _yyyyMMdd.format(creationDate.getTime()) + "-" + Integer.toString(svc.getNext("NiceClaim")),
                "InsuranceClaim");

        niceClaim.setPropertyValue("dc:title", title);
        niceClaim.setPropertyValue("incl:incident_id", title);
        niceClaim.setPropertyValue("incl:incident_kind", "accident");
        niceClaim.setPropertyValue("incl:contract_id", "045-781-245");
        niceClaim.setPropertyValue("incl:incident_date", creationDate);

        // Don't forget this one ;->
        niceClaim.setPropertyValue(kNICE_CLAIM_FIELD, kNICE_CLAIM_FIELD_VALUE);

        niceClaim = _session.createDocument(niceClaim);
        _session.saveDocument(niceClaim);
        _session.save();

        // Now update all infos
        niceClaim.setPropertyValue("dc:created", creationDate);
        niceClaim.setPropertyValue("incl:date_received", creationDate);
        user = kUSERS[_randomInt(0, kMAX_FOR_USERS_RANDOM)];
        niceClaim.setPropertyValue("dc:creator", user);
        _updateModificationInfo(niceClaim, user, creationDate);
        _updateTitle(niceClaim, _yyyyMMdd.format(creationDate.getTime()));

        niceClaim.setPropertyValue("pein:first_name", "Alan");
        niceClaim.setPropertyValue("pein:last_name", "Thecase");
        niceClaim.setPropertyValue("pein:phone_main", "(123)-456-7890");

        aDate = _buildDate(_today, _randomInt(30, 180) * -1);
        niceClaim.setPropertyValue("incl:contract_start", aDate);
        aDate.add(Calendar.DATE, 365);
        niceClaim.setPropertyValue("incl:contract_end", aDate);

        niceClaim.setPropertyValue("incl:due_date", _buildDate(_today, -2));
        niceClaim.setPropertyValue("incl:incident_description",
                "I was driving the speed limit, and this car just came out of the parking lot and hit my car, on the front-left.");
        niceClaim.setPropertyValue("incl:incident_location", "1234 Nth 56 Street");
        niceClaim.setPropertyValue("incl:incident_city", "New York");
        niceClaim.setPropertyValue("incl:incident_weather",
                "Summary: Rain in the evening.\nWind Bearing: 88\nWindSpeed: 7.89\nHumidity: 0.81");
        niceClaim.setPropertyValue("incl:repaid_amount", 260.0);
        niceClaim.setPropertyValue("incl:valuation_comments", "");
        niceClaim.setPropertyValue("incl:valuation_estimates", 260.0);
        niceClaim.setPropertyValue("incl:valuation_on_site", false);

        niceClaim.putContextData("UpdatingData_NoEventPlease", true);
        _session.saveDocument(niceClaim);
        _session.save();

        // And now, loop so we see the claim was modified 15-20 times
        // in the mean time, the lifecycle state is modified. We try
        // to make the audit lopk about ok...
        // NOTE: Well, this does not work very well: The audit uses the system
        // date of course, not dc:modified.
        // So, I keep this code, but for nice display of the audit, it is likely
        // that a the nxp_logs table of the db should be changed manually...
        // (or via a shell script started from here for example)
        String[] transitions = { "to_CheckContract", "to_Opened", "to_Completed", "to_Evaluated", "to_DecisionMade" };
        int statsIdx = -1;
        aDate = (Calendar) creationDate.clone();
        _doLog("Updating the Nice Claim");
        // _doLog("Updating the Nice Claim, writing one second for each modif. Please, be patient (18-20 seconds)");
        int maxModifLoop = 18;
        for (int i = 1; i < maxModifLoop; i++) {
            aDate = _buildDate(aDate, i);
            _updateModificationInfo(niceClaim, kUSERS[_randomInt(0, kMAX_FOR_USERS_RANDOM)], aDate);
            _session.saveDocument(niceClaim);
            _session.save();
            // _wait(1000);
            _doLog(i + "/" + maxModifLoop);

            if ((i % 3) == 0) {
                statsIdx += 1;
                if (statsIdx < transitions.length) {
                    _updateModificationInfo(niceClaim, kUSERS[_randomInt(0, kMAX_FOR_USERS_RANDOM)], aDate);
                    niceClaim.putContextData(DublinCoreListener.DISABLE_DUBLINCORE_LISTENER, true);
                    niceClaim.putContextData("UpdatingData_NoEventPlease", true);
                    niceClaim.followTransition(transitions[statsIdx]);
                    _session.saveDocument(niceClaim);
                    _session.save();
                    // _wait(1000);
                    _doLog(i + "/" + maxModifLoop + " (lifecycle: Following \"" + transitions[statsIdx] + "\")");
                }
            }
        }
        _session.save();

        _doLog("The \"nice claim\" is: " + niceClaim.getTitle());
        _doLog("Path: " + niceClaim.getPathAsString() + "@view_documents");
    }
}
