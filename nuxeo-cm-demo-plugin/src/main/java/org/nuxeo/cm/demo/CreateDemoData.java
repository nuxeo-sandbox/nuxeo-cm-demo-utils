/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Thibaud Arguillere
 */
package org.nuxeo.cm.demo;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.datademo.LifecycleHandler;
import org.nuxeo.datademo.RandomDates;
import org.nuxeo.datademo.RandomFirstLastNames;
import org.nuxeo.datademo.RandomUSZips;
import org.nuxeo.datademo.RandomUSZips.USZip;
import org.nuxeo.datademo.tools.ToolsMisc;
import org.nuxeo.datademo.tools.TransactionInLoop;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.lifecycle.LifeCycleException;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.core.work.api.Work.Progress;
import org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener;
import org.nuxeo.ecm.platform.uidgen.UIDSequencer;
import org.nuxeo.runtime.api.Framework;

/**
 * Creates 100,000 cases by default, dispatched on 3 years
 * 
 * Very specific to the "CM-SHOWCASE" Studio project and its schemas, etc.
 * 
 * Also this is supposed to be about a one shot thing. Once the data is created
 * and you are happy with it, just dump the db and import it in a new database
 * (and run the "Update All Dates" utilities)
 *
 * @since 7.2
 */
/*
 * ================= WARNING WARNING WARNING WARNING WARNING =================
 * About changing the lifecycle state, we are using code that bypasses a lot of
 * controls (so we avoid event sent, etc.).
 * ============================================================================
 */
public class CreateDemoData {

    private static final Log log = LogFactory.getLog(CreateDemoData.class);

    protected static final int HOW_MANY = 100000;

    protected static final int YIELD_TO_BG_WORK_MODULO = 1000;

    // Every yieldToBgWorkModulo, we sleep until there are max
    // MIN_BG_WORKERS_FOR_SLEEP active workers
    public static int MAX_BG_WORKERS_BEFORE_SLEEP = 10;

    protected static final Calendar TODAY = Calendar.getInstance();

    protected static Calendar ONE_MONTH_AGO = Calendar.getInstance();

    protected static Calendar TWO_MONTHS_AGO = Calendar.getInstance();

    protected static Calendar THREE_MONTHS_AGO = Calendar.getInstance();

    protected Calendar startDate_3years;

    protected Calendar startDate_2years;

    protected Calendar startDate_1year;

    protected DateFormat yyyyMMdd = new SimpleDateFormat("yyyyMMdd");

    protected static final int LOG_MODULO = 500;

    protected int logModulo = LOG_MODULO;

    protected static String[] MAIN_US_STATES = { "TX", "NY", "NY", "NY", "NY",
            "CA", "CA", "CA", "PA", "IL", "OH", "MO", "MA", "MA", "FL", "FL",
            "FL" };

    protected static final int MAIN_US_STATES_MAX = MAIN_US_STATES.length - 1;

    protected static final String[] USERS = { "john", "john", "john", "john",
            "kate", "kate", "kate", "alan", "julie", "julie", "mike", "tom",
            "marie", "mat" };

    protected static final int USERS_MAX = USERS.length - 1;

    protected static final String[] LOCATION_STREETS = { "St", "St", "St",
            "St", "Av", "Av", "Bd", "Bd", "Dr" };

    protected static final int LOCATION_STREETS_MAX = LOCATION_STREETS.length - 1;

    protected static final String[] KINDS = { "accident", "accident",
            "accident", "accident", "breakdown", "breakdown", "breakdown",
            "robbery", "robbery", "other" };

    protected static final int KINDS_MAX = KINDS.length - 1;

    // Filled during setup
    protected static final HashMap<String, String> KIND_PREFIX = new HashMap<String, String>();

    protected static final String[] WHY_REJECTED = { "Overdue Submission",
            "Overdue Submission", "Overdue Submission", "Unknown Contract",
            "Unknown Contract", "No Coverage for State", "Missing Info" };

    protected static final int WHY_REJECTED_MAX = WHY_REJECTED.length - 1;

    protected boolean deletePreviousData = true;

    protected CoreSession session;

    protected int howMany = 0;

    protected String parentPath;

    protected int commitModulo = 0;

    protected int yieldToBgWorkModulo = YIELD_TO_BG_WORK_MODULO;

    protected RandomFirstLastNames firstLastNames;

    protected RandomUSZips usZips;

    protected LifecycleHandler lifeCycleWithOnSite;

    protected LifecycleHandler lifeCycleNoOnSite;

    protected Calendar claimCreation;

    /*
     * Depending on the lifecycle, we can have more or less info. For example, a
     * value for the estimate and the repaid amount
     */
    protected static String[] statesFor3To2Months;

    protected static String[] statesFor2To1Month;

    protected static String[] statesForLastMonth;

    protected static UIDSequencer uidSequencer = Framework.getService(UIDSequencer.class);

    protected AbstractWork worker = null;

    public CreateDemoData(CoreSession inSession, DocumentModel inParent) {

        session = inSession;
        parentPath = inParent.getPathAsString();
        howMany = HOW_MANY;
    }

    public CreateDemoData(CoreSession inSession, DocumentModel inParent,
            int inHowMany) {

        session = inSession;
        parentPath = inParent.getPathAsString();
        howMany = inHowMany <= 0 ? HOW_MANY : inHowMany;
    }

    protected void doLogAndWorkerStatus(String inWhat) {

        ToolsMisc.forceLogInfo(log, inWhat);

        if (worker != null) {
            worker.setStatus(inWhat);
        }
    }

    public void run() throws IOException, DocumentException, LifeCycleException {

        setup();

        doLogAndWorkerStatus("Creation of " + howMany
                + " 'InsuranceClaim': start");
        deletePreviousIfNeeded();
        createData();
        doLogAndWorkerStatus("Creation of " + howMany
                + " 'InsuranceClaim': end");

        RandomFirstLastNames.release();
        RandomUSZips.release();
    }

    protected void setup() throws IOException {

        howMany = howMany <= 0 ? HOW_MANY : howMany;

        firstLastNames = RandomFirstLastNames.getInstance();
        usZips = RandomUSZips.getInstance();

        setupLifeCycleDef();

        setupStatesStats();

        // Must be the same as the IncidentKindPrefix vocabulary
        KIND_PREFIX.put("accident", "ACC");
        KIND_PREFIX.put("other", "OTH");
        KIND_PREFIX.put("breakdown", "BDN");
        KIND_PREFIX.put("robbery", "ROB");
        KIND_PREFIX.put("building-fire", "BFI");

        startDate_3years = Calendar.getInstance();
        startDate_3years.add(Calendar.YEAR, -3);

        startDate_2years = Calendar.getInstance();
        startDate_2years.add(Calendar.YEAR, -2);

        startDate_1year = Calendar.getInstance();
        startDate_1year.add(Calendar.YEAR, -1);

        Calendar someMonthsAgo = (Calendar) TODAY.clone();
        someMonthsAgo.add(Calendar.MONTH, -1);
        ONE_MONTH_AGO.set(someMonthsAgo.get(Calendar.YEAR),
                someMonthsAgo.get(Calendar.MONTH),
                someMonthsAgo.get(Calendar.DAY_OF_MONTH));

        someMonthsAgo.add(Calendar.MONTH, -1);
        TWO_MONTHS_AGO.set(someMonthsAgo.get(Calendar.YEAR),
                someMonthsAgo.get(Calendar.MONTH),
                someMonthsAgo.get(Calendar.DAY_OF_MONTH));

        someMonthsAgo.add(Calendar.MONTH, -1);
        THREE_MONTHS_AGO.set(someMonthsAgo.get(Calendar.YEAR),
                someMonthsAgo.get(Calendar.MONTH),
                someMonthsAgo.get(Calendar.DAY_OF_MONTH));

    }

    protected void setupLifeCycleDef() {

        ArrayList<String> states = new ArrayList<String>();
        states.add("Received");
        states.add("CheckContract");
        states.add("Opened");
        states.add("Completed");
        states.add("ExpertOnSiteNeeded");
        states.add("Evaluated");
        states.add("DecisionMade");
        states.add("Archived");

        ArrayList<String> transitions = new ArrayList<String>();
        transitions.add("to_CheckContract");
        transitions.add("to_Opened");
        transitions.add("to_Completed");
        transitions.add("to_ExpertOnSiteNeeded");
        transitions.add("to_Evaluated");
        transitions.add("to_DecisionMade");
        transitions.add("to_Archived");

        String[] strStates = new String[states.size()];
        states.toArray(strStates);
        String[] strTransitions = new String[transitions.size()];
        transitions.toArray(strTransitions);
        lifeCycleWithOnSite = new LifecycleHandler(strStates, strTransitions);

        states.remove("ExpertOnSiteNeeded");
        strStates = new String[states.size()];
        states.toArray(strStates);
        transitions.remove("to_ExpertOnSiteNeeded");
        strTransitions = new String[transitions.size()];
        transitions.toArray(strTransitions);
        lifeCycleNoOnSite = new LifecycleHandler(strStates, strTransitions);

    }

    protected void createData() throws DocumentException, LifeCycleException {

        TransactionInLoop til = new TransactionInLoop(session);
        til.commitAndStartNewTransaction();
        til.setCommitModulo(commitModulo);
        ToolsMisc.forceLogInfo(log,
                "Creation of 'InsuranceClaim' with:\n    howMany: " + howMany
                        + "\n    commitModulo: " + til.getCommitModulo());

        for (int i = 1; i <= howMany; i++) {
            DocumentModel theClaim = createNewInsuranceClaim();

            // Update lifecycle _after_ creation and save because the lowlevel
            // routines expect the document to already exist in the db beore
            // being able to change the lifecycle state
            // Notice that we also update the document depending on the
            // lifecycle: valuation for example
            theClaim.refresh();
            disableListeners(theClaim);
            theClaim = updateLifecycleStateAndRelatedData(theClaim);
            // Now save the document itself
            disableListeners(theClaim);
            theClaim = til.saveDocumentAndCommitIfNeeded(theClaim);

            if ((i % logModulo) == 0) {
                doLogAndWorkerStatus("InsuranceClaim creation: " + i + "/"
                        + howMany);
                if (worker != null) {
                    worker.setProgress(new Progress(i, howMany));
                }
            }

            // Also, when creating a lot of Claims, we want to let background
            // work to be able to breath a bit.
            if ((i % yieldToBgWorkModulo) == 0) {
                MiscUtils.waitForBackgroundWorkCompletion(
                        MAX_BG_WORKERS_BEFORE_SLEEP, 5000,
                        CreateDataDemoWork.CATEGORY_CREATE_DATA_DEMO);
            }
        }

        til.commitAndStartNewTransaction();
    }

    protected DocumentModel createNewInsuranceClaim() {

        Calendar someCalValue;
        String someStr, title;
        Calendar lastModif;

        // To create the title, we need the date and the kind first.
        claimCreation = buildCreationDate();
        String kind = KINDS[ToolsMisc.randomInt(0, KINDS_MAX)];
        title = yyyyMMdd.format(claimCreation.getTime());
        String kindPrefix = KIND_PREFIX.get(kind);
        title += "-" + kindPrefix;
        title += "-"
                + uidSequencer.getNext(kindPrefix
                        + claimCreation.get(Calendar.YEAR));

        DocumentModel claim = session.createDocumentModel(parentPath, title,
                "InsuranceClaim");

        claim.setPropertyValue("dc:title", title);
        claim.setPropertyValue("incl:incident_id", title);
        claim.setPropertyValue("dc:created", claimCreation);
        claim.setPropertyValue("dc:creator", getRandomUser());

        // The state will be "Archived" for all cases older than 2 months
        if (claimCreation.before(THREE_MONTHS_AGO)) {
            lastModif = RandomDates.addDays(claimCreation, 3, 90, TODAY);
        } else {
            lastModif = RandomDates.addDays(claimCreation,
                    ToolsMisc.randomInt(3, 90), true);
        }
        claim.setPropertyValue("dc:modified", lastModif);
        claim.setPropertyValue("dc:lastContributor", getRandomUser());

        claim.setPropertyValue("incl:incident_kind", kind);

        someCalValue = RandomDates.buildDate(claimCreation, 0, 2, true);
        claim.setPropertyValue("incl:incident_date", someCalValue);

        // Sometime, the claim is not created the day it was received?
        if (ToolsMisc.randomInt(1, 100) < 7) {
            claim.setPropertyValue("incl:date_received",
                    RandomDates.buildDate(claimCreation, 1, 2, true));
        } else {
            claim.setPropertyValue("incl:date_received", claimCreation);
        }

        // Contract info
        someCalValue = RandomDates.buildDate(claimCreation, 30, 100, true);
        claim.setPropertyValue("incl:contract_start", someCalValue);
        someCalValue.add(Calendar.YEAR, 1);
        someCalValue.add(Calendar.DATE, -1);
        claim.setPropertyValue("incl:contract_end", someCalValue);
        someStr = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        someStr = someStr.substring(0, 6) + "-"
                + ToolsMisc.randomInt(3112, 24653) + someStr.substring(6, 6);
        claim.setPropertyValue("incl:contract_id", someStr);

        // Person info
        claim.setPropertyValue("pein:first_name",
                firstLastNames.getAFirstName(RandomFirstLastNames.GENDER.ANY));
        claim.setPropertyValue("pein:last_name", firstLastNames.getALastName());
        claim.setPropertyValue("pein:phone_main", randomUSPhoneNumber());
        // incidentlocation is a fake address. Something like "1234 MARIE St"
        someStr = ""
                + ToolsMisc.randomInt(1, 10000)
                + " "
                + firstLastNames.getAFirstName(RandomFirstLastNames.GENDER.ANY)
                + " "
                + LOCATION_STREETS[ToolsMisc.randomInt(0, LOCATION_STREETS_MAX)];
        claim.setPropertyValue("incl:incident_location", someStr);
        setCityAndState(claim);

        claim.setPropertyValue("incl:due_date",
                RandomDates.buildDate(claimCreation, 15, 40, false));

        // Disable DublinCore
        claim.putContextData(DublinCoreListener.DISABLE_DUBLINCORE_LISTENER,
                true);
        // Make sure events are not triggered
        claim.putContextData("UpdatingData_NoEventPlease", true);
        claim = session.createDocument(claim);

        return claim;

    }

    protected Calendar buildCreationDate() {
        // A progression 15% first year, 22% the year after
        // So year-3 = 27% of the total, year-2 = 32 and current year = 41
        int r = ToolsMisc.randomInt(1, 100);
        Calendar creation;
        if (r < 28) {
            creation = (Calendar) startDate_3years.clone();
        } else if (r < 59) {
            creation = (Calendar) startDate_2years.clone();
        } else {
            creation = (Calendar) startDate_1year.clone();
        }

        creation.add(Calendar.DATE, ToolsMisc.randomInt(0, 365));

        return creation;
    }

    /*
     * Just a shortcut
     */
    protected String getRandomUser() {

        return USERS[ToolsMisc.randomInt(0, USERS_MAX)];

    }

    protected void setCityAndState(DocumentModel inClaim) {

        USZip zip;
        // The "main_states" are at least 30% of the total (they will be more
        // because they will also be picked up in the else part)
        if (ToolsMisc.randomInt(1, 10) > 7) {
            zip = usZips.getAZip(MAIN_US_STATES[ToolsMisc.randomInt(0,
                    MAIN_US_STATES_MAX)]);
        } else {
            zip = usZips.getAZip();
        }

        inClaim.setPropertyValue("incl:incident_city", zip.city);
        inClaim.setPropertyValue("incl:incident_us_state", zip.state);

    }

    protected void setupStatesStats() {

        // 2-3 months ago
        // Few "Received", more "Evaluated/DecisionMade/Archived"
        statesFor3To2Months = new String[100];
        for (int i = 0; i < 100; i++) {
            if (i < 9) { // 9% Received
                statesFor3To2Months[i] = "Received";
            } else if (i < 18) { // 9% Opened
                statesFor3To2Months[i] = "Opened";
            } else if (i < 39) { // 21% Completed
                statesFor3To2Months[i] = "Completed";
            } else if (i < 46) { // 7% "ExertOnSite"
                statesFor3To2Months[i] = "ExpertOnSiteNeeded";
            } else if (i < 64) { // 18% Evaluated
                statesFor3To2Months[i] = "Evaluated";
            } else if (i < 80) { // 16% DecisionMade
                statesFor3To2Months[i] = "DecisionMade";
            } else { // 20% Archived
                statesFor3To2Months[i] = "Archived";
            }
        }

        // 1-2 months ago
        // More "Received", more completed/evaluated. Some DecisionMade/Archived
        statesFor2To1Month = new String[100];
        for (int i = 0; i < 100; i++) {
            if (i < 17) { // 17% Received
                statesFor2To1Month[i] = "Received";
            } else if (i < 32) { // 15% Opened
                statesFor2To1Month[i] = "Opened";
            } else if (i < 50) { // 18% Completed
                statesFor2To1Month[i] = "Completed";
            } else if (i < 58) { // 8% "ExertOnSite"
                statesFor2To1Month[i] = "ExpertOnSiteNeeded";
            } else if (i < 77) { // 19% Evaluated
                statesFor2To1Month[i] = "Evaluated";
            } else if (i < 88) { // 11% DecisionMade
                statesFor2To1Month[i] = "DecisionMade";
            } else { // 12% Archived
                statesFor2To1Month[i] = "Archived";
            }
        }

        // Last month
        // Here, we can have more new claims
        // Always very few "CheckContract". Say 3%
        statesForLastMonth = new String[100];
        for (int i = 0; i < 100; i++) {
            if (i < 23) { // 23% Received
                statesForLastMonth[i] = "Received";
            } else if (i < 27) { // 4% "CheckContract"
                statesForLastMonth[i] = "CheckContract";
            } else if (i < 45) { // 18% Opened
                statesForLastMonth[i] = "Opened";
            } else if (i < 80) { // 35% Completed
                statesForLastMonth[i] = "Completed";
            } else if (i < 84) { // 4% "ExertOnSite"
                statesForLastMonth[i] = "ExpertOnSiteNeeded";
            } else if (i < 91) { // 7% Evaluated
                statesForLastMonth[i] = "Evaluated";
            } else if (i < 96) { // 5% DecisionMade
                statesForLastMonth[i] = "DecisionMade";
            } else { // 4% Archived
                statesForLastMonth[i] = "Archived";
            }
        }
    }

    protected DocumentModel updateLifecycleStateAndRelatedData(
            DocumentModel oneClaim) throws DocumentException,
            LifeCycleException {

        int r = ToolsMisc.randomInt(1, 100);
        String newState = "Opened";
        // All the cases older than 3 months are closed and archived, some were
        // rejected
        if (claimCreation.before(THREE_MONTHS_AGO)) {
            if (r > 4) {
                newState = "Archived";
            } else {
                newState = "Rejected";
            }
        } else {
            String[] statsToUse;
            if (claimCreation.before(TWO_MONTHS_AGO)) {
                statsToUse = statesFor3To2Months;
            } else {
                if (claimCreation.before(ONE_MONTH_AGO)) {
                    statsToUse = statesFor2To1Month;
                } else {
                    statsToUse = statesForLastMonth;
                }
            }
            newState = statsToUse[ToolsMisc.randomInt(0, 99)];
        }
        LifecycleHandler.directSetCurrentLifecycleState(session, oneClaim,
                newState);

        oneClaim.refresh();
        if (newState.equals("ExpertOnSiteNeeded")) {
            oneClaim.setPropertyValue("incl:valuation_on_site", true);
        }

        if (newState.equals("Archived")) {
            oneClaim.setPropertyValue("incl:date_closed",
                    oneClaim.getPropertyValue("dc:modified"));
            oneClaim.setPropertyValue("incl:ready_to_archive", true);
        }

        if (newState.equals("Rejected")) {
            oneClaim.setPropertyValue("incl:why_rejected",
                    WHY_REJECTED[ToolsMisc.randomInt(0, WHY_REJECTED_MAX)]);
        } else {
            if (lifeCycleWithOnSite.compareStates(newState, "Evaluated") >= 0) {
                // Say we had around 30% of expert on site?
                oneClaim.setPropertyValue("incl:valuation_on_site",
                        ToolsMisc.randomInt(1, 100) < 35);

                double repaid = ToolsMisc.randomInt(100, 10000);
                oneClaim.setPropertyValue("incl:repaid_amount", repaid);
                // In 27% we had good estimates
                r = ToolsMisc.randomInt(1, 100);
                if (r < 28) {
                    oneClaim.setPropertyValue("incl:valuation_estimates",
                            repaid);
                } else {
                    // We are between, say +/- 20% error
                    // In 60% of the case we underestimated the thing
                    r = ToolsMisc.randomInt(5, 22);
                    double value = (repaid * (((double) r) / 100));
                    if (ToolsMisc.randomInt(1, 100) > 42) {
                        value = repaid - value;
                    } else {
                        value = repaid + value;
                    }
                    oneClaim.setPropertyValue("incl:valuation_estimates", value);
                }

                oneClaim.setPropertyValue("incl:date_decision_sent",
                        oneClaim.getPropertyValue("dc:modified"));

            }
        }
        return oneClaim;

    }

    protected void disableListeners(DocumentModel inClaim) {
        // Disable DublinCore
        inClaim.putContextData(DublinCoreListener.DISABLE_DUBLINCORE_LISTENER,
                true);
        // Make sure events are not triggered in the CM-SHOWCASE project
        inClaim.putContextData("UpdatingData_NoEventPlease",
                "whatever-just-not-null");
    }

    protected void deletePreviousIfNeeded() {

        if (deletePreviousData) {

            String nxql = "SELECT * FROM InsuranceClaim";
            DocumentModelList docs;

            doLogAndWorkerStatus("Deleting previous 'InsuranceClaim'...");
            TransactionInLoop til = new TransactionInLoop(session);
            til.commitAndStartNewTransaction();
            int count = 0;
            do {
                docs = session.query(nxql);
                if (docs.size() > 0) {
                    count += docs.size();
                    doLogAndWorkerStatus("    Deleting " + docs.size()
                            + " 'InsuranceClaim'");
                    for (DocumentModel oneDoc : docs) {
                        session.removeDocument(oneDoc.getRef());

                        til.incrementCounter();
                        til.commitOrRollbackIfNeeded();
                    }
                }
            } while (docs.size() > 0);
            til.commitAndStartNewTransaction();
            doLogAndWorkerStatus("...Deleting previous 'InsuranceClaim' done: "
                    + count + " 'InsuranceClaim' deleted");

        }
    }

    protected String randomUSPhoneNumber() {

        return "(" + ToolsMisc.randomInt(111, 999) + ") "
                + ToolsMisc.randomInt(111, 999) + "-"
                + ToolsMisc.randomInt(1111, 9999);

    }

    public void setDeletePreviousData(boolean deletePreviousData) {
        this.deletePreviousData = deletePreviousData;
    }

    public void setCommitModulo(int inValue) {
        this.commitModulo = inValue;
    }

    public void setLogModulo(int inValue) {
        logModulo = inValue > 0 ? inValue : LOG_MODULO;
    }

    public void setWorker(AbstractWork inValue) {
        worker = inValue;
    }

    public int getYieldToBgWorkModulo() {
        return yieldToBgWorkModulo;
    }

    public void setYieldToBgWorkModulo(int inValue) {
        yieldToBgWorkModulo = inValue > 0 ? inValue : YIELD_TO_BG_WORK_MODULO;
    }

}
