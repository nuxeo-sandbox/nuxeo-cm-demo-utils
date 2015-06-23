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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import org.nuxeo.ecm.core.work.api.Work.Progress;
import org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener;
import org.nuxeo.ecm.platform.uidgen.UIDSequencer;
import org.nuxeo.runtime.api.Framework;

/**
 * Creates cases (100,000 by default), dispatched on 3 years
 * 
 * Very specific to the "CM-SHOWCASE" Studio project and its schemas, etc.
 * 
 * Also this is supposed to be about a one shot thing. Once the data is created
 * and you are happy with it, just dump the db and import it in a new database
 * (and run the "Update All Dates" utilities)
 * 
 * It is a single threaded creation because we are no really concerned with
 * performance here. Still, we don't want to overload nuxeo, the db, the cpus,
 * ... with the background work running (full text indexing, elastic search
 * indexing, ...). This is why we regularly yield to the background jobs. This
 * permits to avoid timeout errors for some of these threads.
 * 
 * ================= WARNING WARNING WARNING WARNING WARNING =================
 * About changing the lifecycle state, we are using code that bypasses a lot of
 * controls (so we avoid event sent, etc.).
 * ============================================================================
 *
 * @since 7.2
 */
public class CreateDemoData {

    private static final Log log = LogFactory.getLog(CreateDemoData.class);

    protected static final int DEFAULT_HOW_MANY = 100000;

    protected static final int DEFAULT_YIELD_TO_BG_WORK_MODULO = 500;

    protected static final int DEFAULT_COMMIT_MODULO = 50;

    protected static final int DEFAULT_LOG_MODULO = 250;

    protected static final boolean DEFAULT_DELETE_PREVIOUS_CLAIMS = true;

    protected static final int DEFAULT_SLEEP_MODULO = 300;

    protected static final int DEFAULT_SLEEP_DURATION_MS = 500;

    protected static final int DEFAULT_SLEEP_DURATION_AFTER_COMMIT = 100;

    // Every yieldToBgWorkModulo, we sleep until there are max
    // MIN_BG_WORKERS_FOR_SLEEP active workers
    public static int MAX_BG_WORKERS_BEFORE_SLEEP = 20;

    public static enum RUNNING_STATUS {
        PAUSED, RUNNING, STOPPED, UNKNOWN
    };

    protected RUNNING_STATUS status = RUNNING_STATUS.UNKNOWN;

    protected static final Calendar TODAY = Calendar.getInstance();

    protected static Calendar ONE_MONTH_AGO = Calendar.getInstance();

    protected static Calendar TWO_MONTHS_AGO = Calendar.getInstance();

    protected static Calendar THREE_MONTHS_AGO = Calendar.getInstance();

    protected static Calendar ONE_WEEK_AGO;

    protected Calendar startDate_3years;

    protected Calendar startDate_2years;

    protected Calendar startDate_1year;

    protected DateFormat yyyyMMdd = new SimpleDateFormat("yyyyMMdd");

    protected int logModulo = DEFAULT_LOG_MODULO;

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

    protected boolean deletePreviousClaims = DEFAULT_DELETE_PREVIOUS_CLAIMS;

    protected CoreSession session;

    protected int howMany = 0;

    protected int countCreated;

    protected String parentPath;

    protected int commitModulo = 0;

    protected int yieldToBgWorkModulo = DEFAULT_YIELD_TO_BG_WORK_MODULO;

    protected int sleepModulo = DEFAULT_SLEEP_MODULO;

    protected int sleepDurationMs = DEFAULT_SLEEP_DURATION_MS;

    protected int sleepDurationAfterCommit = DEFAULT_SLEEP_DURATION_AFTER_COMMIT;

    protected RandomFirstLastNames firstLastNames;

    protected RandomUSZips usZips;

    protected LifecycleHandler lifeCycleWithOnSite;

    protected LifecycleHandler lifeCycleNoOnSite;

    protected Calendar claimCreation;

    protected File fileUSZipsForCM;

    /*
     * Depending on the lifecycle, we can have more or less info. For example, a
     * value for the estimate and the repaid amount
     */
    protected static String[] statesFor3To2Months;

    protected static String[] statesFor2To1Month;

    protected static String[] statesForLastMonth;

    protected static UIDSequencer uidSequencer = Framework.getService(UIDSequencer.class);

    protected AbstractWork worker = null;

    protected long creationStartTime;

    public CreateDemoData(CoreSession inSession, DocumentModel inParent) {

        session = inSession;
        parentPath = inParent.getPathAsString();
        howMany = DEFAULT_HOW_MANY;
    }

    public CreateDemoData(CoreSession inSession, DocumentModel inParent,
            int inHowMany) {

        session = inSession;
        parentPath = inParent.getPathAsString();
        howMany = inHowMany <= 0 ? DEFAULT_HOW_MANY : inHowMany;
    }

    protected void doLogAndWorkerStatus(String inWhat) {

        ToolsMisc.forceLogInfo(log, inWhat);

        if (worker != null) {
            worker.setStatus(inWhat);
        }
    }

    public void run() throws IOException, DocumentException, LifeCycleException {

        long startTime, endTime, deletionDuration, creationDuration;

        if (!checkEnvironment()) {
            doLogAndWorkerStatus("Environment is not correctly setup (Template Rendering, ...), no data is created.");
            return;
        }

        setup();

        status = RUNNING_STATUS.RUNNING;
        countCreated = 0;

        doLogAndWorkerStatus("Creation of " + howMany
                + " 'InsuranceClaim': start");

        startTime = System.currentTimeMillis();
        deletePreviousIfNeeded();
        endTime = System.currentTimeMillis();
        deletionDuration = endTime - startTime;

        creationStartTime = System.currentTimeMillis();
        createData();
        RandomFirstLastNames.release();
        RandomUSZips.release();
        if (fileUSZipsForCM != null && fileUSZipsForCM.exists()) {
            fileUSZipsForCM.delete();
        }
        endTime = System.currentTimeMillis();
        creationDuration = endTime - creationStartTime;

        String logStr = "Creation of " + howMany + " 'InsuranceClaim': end ("
                + countCreated + " created)";
        if (worker != null) {
            worker.setStatus(logStr);
        }

        logStr += "\n    Duration: "
                + MiscUtils.millisecondsToToTimeFormat(deletionDuration
                        + creationDuration);
        logStr += "\n        Deletion: "
                + MiscUtils.millisecondsToToTimeFormat(deletionDuration);
        logStr += "\n        Creation: "
                + MiscUtils.millisecondsToToTimeFormat(creationDuration);
        ToolsMisc.forceLogInfo(log, logStr);

        status = RUNNING_STATUS.STOPPED;

    }

    public void pause() {
        status = RUNNING_STATUS.PAUSED;
    }

    public void resume() {
        status = RUNNING_STATUS.RUNNING;
    }

    public void stop() {
        status = RUNNING_STATUS.STOPPED;
    }

    public RUNNING_STATUS getStatus() {
        return status;
    }

    protected boolean checkEnvironment() {

        boolean ok = true;

        String nxql = "SELECT * FROM TemplateSource WHERE dc:title = 'Claim Report'";
        try {
            DocumentModelList docs = session.query(nxql);
            if (docs == null || docs.size() < 1) {
                ok = false;
            }
        } catch (Exception e) {
            ok = false;
        }
        if (!ok) {
            log.error("Cannot get the Claim Report template. Install the Template Rendering plug-in and the Claim Report template");
        }

        return ok;
    }

    protected void setup() throws IOException {

        howMany = howMany <= 0 ? DEFAULT_HOW_MANY : howMany;

        firstLastNames = RandomFirstLastNames.getInstance();

        setupCitiesAndStates();

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

        ONE_WEEK_AGO = Calendar.getInstance();
        ONE_WEEK_AGO.add(Calendar.DATE, -7);

    }

    /*
     * We use our custom ZIP files (with more New York, Orlando, ...
     * 
     * nuxeo-datademo can't read file that is in _my_ jar, we must duplicate it.
     * 
     * (and we don't try-catch, let's fail in case of problem)
     */
    protected void setupCitiesAndStates() throws IOException {

        InputStream in = null;
        OutputStream out = null;

        fileUSZipsForCM = File.createTempFile("cmdemo", ".txt");
        fileUSZipsForCM.createNewFile();

        in = getClass().getResourceAsStream("/files/US-zips-special-cm.txt");
        out = new FileOutputStream(fileUSZipsForCM);
        byte[] buffer = new byte[4096];
        int length;
        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }
        in.close();
        out.close();
        usZips = RandomUSZips.getInstance(fileUSZipsForCM.getAbsolutePath());
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

        til.setSleepDurationAfterCommit(sleepDurationAfterCommit);

        String logInfo = "Creation of InsuranceClaim documents:";
        logInfo += "\n    howMany: " + howMany;
        logInfo += "\n    commitModulo: " + commitModulo;
        logInfo += "\n    logModulo: " + logModulo;
        logInfo += "\n    yieldToBgWorkModulo: " + yieldToBgWorkModulo;
        logInfo += "\n    sleepDurationAfterCommit: "
                + sleepDurationAfterCommit;
        logInfo += "\n    sleepModulo: " + sleepModulo;
        logInfo += "\n    sleepDurationMs: " + sleepDurationMs;
        ToolsMisc.forceLogInfo(log, logInfo);

        countCreated = 0;
        for (int i = 1; i <= howMany; i++) {

            // Create and save the claim
            DocumentModel theClaim = createNewInsuranceClaim();

            // Handle lifecycle and related data (valuation_on_site, ...) that
            // depends on the lifecycle state
            // disableListeners(theClaim);
            theClaim = updateLifecycleStateAndRelatedData(theClaim);

            // Save and possibly commit the transaction
            disableListeners(theClaim);
            theClaim = til.saveDocumentAndCommitIfNeeded(theClaim);
            countCreated += 1;

            // Log
            if ((i % logModulo) == 0) {
                logInfo = "InsuranceClaim creation: " + i + "/" + howMany;
                if ((i % 1000) == 0) {
                    long theDuration = System.currentTimeMillis()
                            - creationStartTime;
                    logInfo += ", duration: "
                            + MiscUtils.millisecondsToToTimeFormat(theDuration);
                }
                doLogAndWorkerStatus(logInfo);
                if (worker != null) {
                    worker.setProgress(new Progress(i, howMany));
                }
            }

            // Also, when creating a lot of Claims, we want to let background
            // work (full text indexing, ...) to finish, or we will have way to
            // more of them and it will fail
            if ((i % yieldToBgWorkModulo) == 0) {
                MiscUtils.waitForBackgroundWorkCompletion(
                        MAX_BG_WORKERS_BEFORE_SLEEP, 0,
                        CreateDataDemoWork.CATEGORY_CREATE_DATA_DEMO);
            }

            // Now we also want to give time to the database maybe?
            if ((i % sleepModulo) == 0) {
                try {
                    Thread.sleep(sleepDurationMs);
                } catch (InterruptedException e) {
                    // ignore
                }
            }

            switch (status) {
            case PAUSED:
                doLogAndWorkerStatus("Creation paused, waiting for new status");
                do {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                } while (status == RUNNING_STATUS.PAUSED);

                if (status == RUNNING_STATUS.STOPPED) {
                    doLogAndWorkerStatus("Creation stopped");
                    i = howMany + 1;
                } else if (status == RUNNING_STATUS.RUNNING) {
                    doLogAndWorkerStatus("Creation resumed");
                } else {
                    throw new RuntimeException(
                            "The status should be 'paused', 'stopped' or 'running'");
                }
                break;

            case STOPPED:
                doLogAndWorkerStatus("Creation stopped");
                i = howMany + 1;
                break;
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

        claim.setPropertyValue("incl:tag_created_for_demo", true);

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
        setCityStateLatAndLong(claim);

        claim.setPropertyValue("incl:due_date",
                RandomDates.buildDate(claimCreation, 15, 40, false));

        disableListeners(claim);
        claim = session.createDocument(claim);

        return claim;

    }

    protected Calendar buildCreationDate() {
        // A progression 15% first year, 22% the year after
        // So year-3 = 27% of the total, year-2 = 32 and current year = 41
        int r = ToolsMisc.randomInt(1, 100);
        Calendar creation;

        if (r < 28) {
            creation = RandomDates.buildDate(startDate_3years, 1, 365, false);
        } else if (r < 59) {
            creation = RandomDates.buildDate(startDate_2years, 1, 365, false);
        } else {
            // Just a bit more in the last week.
            if (ToolsMisc.randomInt(1, 100) > 95) {
                creation = RandomDates.buildDate(ONE_WEEK_AGO, 0, 7, false);
            } else {
                creation = RandomDates.buildDate(startDate_1year, 1, 365, false);
            }
        }

        return creation;
    }

    /*
     * Just a shortcut
     */
    protected String getRandomUser() {

        return USERS[ToolsMisc.randomInt(0, USERS_MAX)];

    }

    /*
     * As of today, we don't set lat./long. based on an exact address because
     * our addresses are fake. So we just store the lat./long. of the city
     * itself.
     */
    protected void setCityStateLatAndLong(DocumentModel inClaim) {

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
        inClaim.setPropertyValue("incl:incident_latitude", zip.latitude);
        inClaim.setPropertyValue("incl:incident_longitude", zip.longitude);

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
                // Say we had around 30% of "expert on site"
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

        if (deletePreviousClaims) {

            String nxql = "SELECT * FROM InsuranceClaim WHERE ecm:path STARTSWITH '"
                    + parentPath + "'";
            DocumentModelList docs;

            doLogAndWorkerStatus("Deleting previous InsuranceClaim...");
            TransactionInLoop til = new TransactionInLoop(session);
            til.commitAndStartNewTransaction();
            int count = 0;
            do {
                docs = session.query(nxql, 200);
                if (docs.size() > 0) {
                    count += docs.size();
                    for (DocumentModel oneDoc : docs) {
                        session.removeDocument(oneDoc.getRef());

                        til.incrementCounter();
                        til.commitOrRollbackIfNeeded();
                    }
                    doLogAndWorkerStatus("    InsuranceClaim: " + count
                            + " deleted...");
                }
            } while (docs.size() > 0);
            til.commitAndStartNewTransaction();
            doLogAndWorkerStatus("...Deleting previous InsuranceClaim done: "
                    + count + " 'InsuranceClaim' deleted");

        }
    }

    protected String randomUSPhoneNumber() {

        return "(" + ToolsMisc.randomInt(111, 999) + ") "
                + ToolsMisc.randomInt(111, 999) + "-"
                + ToolsMisc.randomInt(1111, 9999);

    }

    public void setDeletePreviousClaims(boolean deletePreviousClaims) {
        this.deletePreviousClaims = deletePreviousClaims;
    }

    public void setCommitModulo(int inValue) {
        this.commitModulo = inValue;
    }

    public void setLogModulo(int inValue) {
        logModulo = inValue > 0 ? inValue : DEFAULT_LOG_MODULO;
    }

    public void setWorker(AbstractWork inValue) {
        worker = inValue;
    }

    public int getYieldToBgWorkModulo() {
        return yieldToBgWorkModulo;
    }

    public void setYieldToBgWorkModulo(int inValue) {
        yieldToBgWorkModulo = inValue > 0 ? inValue
                : DEFAULT_YIELD_TO_BG_WORK_MODULO;
    }

    public int getSleepModulo() {
        return sleepModulo;
    }

    public void setSleepModulo(int inValue) {
        sleepModulo = inValue < 1 ? DEFAULT_SLEEP_MODULO : inValue;
    }

    public int getSleepDurationMs() {
        return sleepDurationMs;
    }

    public void setSleepDurationMs(int inValue) {
        sleepDurationMs = inValue < 1 ? DEFAULT_SLEEP_DURATION_MS : inValue;
    }

    public int getSleepDurationAfterCommit() {
        return sleepDurationAfterCommit;
    }

    public void setSleepDurationAfterCommit(int inValue) {
        sleepDurationAfterCommit = inValue < 1 ? DEFAULT_SLEEP_DURATION_AFTER_COMMIT
                : inValue;
    }

}
