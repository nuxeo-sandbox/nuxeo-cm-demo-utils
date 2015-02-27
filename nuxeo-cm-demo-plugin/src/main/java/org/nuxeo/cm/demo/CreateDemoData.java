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
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.collections.ScopeType;
import org.nuxeo.datademo.RandomDates;
import org.nuxeo.datademo.RandomFirstLastNames;
import org.nuxeo.datademo.RandomUSZips;
import org.nuxeo.datademo.RandomUSZips.USZip;
import org.nuxeo.datademo.tools.ToolsMisc;
import org.nuxeo.datademo.tools.TransactionInLoop;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener;

/**
 * Creates 100,000 cases, dispatched on 3 years
 * 
 * Very specific to the "CM-SHOWCASE" Studio project and its schemas, etc.
 * 
 * Also this is suppose to be about a one shot thing. Once the data is xreated
 * and you are happy with it, just dump the db and import it in a new database
 * (and run the "Update ALl Dates" utilities)
 *
 * @since 7.2
 */
public class CreateDemoData {

    private static final Log log = LogFactory.getLog(CreateDemoData.class);

    protected static final int HOW_MANY = 100000;

    protected static final Calendar TODAY = Calendar.getInstance();

    // This static final will be updated during setup
    protected static final Calendar TWO_MONTHS_AGO = Calendar.getInstance();

    protected Calendar startDate_3years;

    protected Calendar startDate_2years;

    protected Calendar startDate_1year;

    protected DateFormat YYYMMDD = new SimpleDateFormat("yyyy-MM-dd");

    protected static final int LOG_MODULO = 500;

    protected static String[] MAIN_US_STATES = { "TX", "NY", "CA", "PA", "IL",
            "OH", "MO", "MA", "FL" };

    protected static final int MAIN_US_STATES_MAX = MAIN_US_STATES.length - 1;

    static protected final String[] USERS = { "john", "john", "john", "john",
            "kate", "kate", "kate", "alan", "julie", "julie", "mike", "tom",
            "marie", "mat" };

    static protected final int USERS_MAX = USERS.length - 1;

    // 
    static protected final String[] KINDS = { "accident", "accident", "accident", "accident", "breakdown", "breakdown", "breakdown", "robbery", "robbery", "other" };

    static protected final int KINDS_MAX = KINDS.length - 1;

    protected boolean deletePreviousData = false;

    protected CoreSession session;

    protected int howMany;

    protected String parentPath;

    protected int commitModulo = 0;

    protected RandomFirstLastNames firstLastNames;

    protected RandomUSZips usZips;

    protected Calendar claimCreation;

    public void run(CoreSession inSession, DocumentModel inParent)
            throws IOException {

        session = inSession;
        parentPath = inParent.getPathAsString();

        setup();

        ToolsMisc.forceLogInfo(log, "Creation of " + howMany
                + " 'InsuranceClaim': start");
        deletePreviousIfNeeded();
        createData();
        ToolsMisc.forceLogInfo(log, "Creation of " + howMany
                + " 'InsuranceClaim': end");

        RandomFirstLastNames.release();
        RandomUSZips.release();
    }

    protected void setup() throws IOException {

        howMany = howMany <= 0 ? HOW_MANY : howMany;

        firstLastNames = RandomFirstLastNames.getInstance();
        usZips = RandomUSZips.getInstance();

        startDate_3years = Calendar.getInstance();
        startDate_3years.add(Calendar.YEAR, -3);

        startDate_2years = Calendar.getInstance();
        startDate_2years.add(Calendar.YEAR, -2);

        startDate_1year = Calendar.getInstance();
        startDate_1year.add(Calendar.YEAR, -1);
        
        Calendar twoMonthsAgo = (Calendar) TODAY.clone();
        twoMonthsAgo.add(Calendar.DATE, -60);
        TWO_MONTHS_AGO.set(TWO_MONTHS_AGO.get(Calendar.YEAR), TWO_MONTHS_AGO.get(Calendar.MONTH), TWO_MONTHS_AGO.get(Calendar.DAY_OF_MONTH));
    }

    protected void createData() {

        TransactionInLoop til = new TransactionInLoop(session);
        til.commitAndStartNewTransaction();
        til.setCommitModulo(commitModulo);
        ToolsMisc.forceLogInfo(log,
                "Creation of 'InsuranceClaim' with:\n    howMany: " + howMany
                        + "\n    commitModulo: " + til.getCommitModulo());

        for (int i = 0; i < howMany; i++) {
            DocumentModel oneDoc = createNewInsuranceClaim();

            oneDoc.putContextData(
                    DublinCoreListener.DISABLE_DUBLINCORE_LISTENER, true);
            // Make sure events are not triggered
            oneDoc.putContextData("UpdatingData_NoEventPlease", true);
            // Let's try this one?
            // oneDoc.putContextData(ScopeType.REQUEST,
            // "UpdatingData_NoEventPlease", true);
            til.saveDocumentAndCommitIfNeeded(oneDoc);

            // Update lifecycle after creation and save
            updateLifecycleState(oneDoc);

            if ((i % LOG_MODULO) == 0) {
                ToolsMisc.forceLogInfo(log, "InsuranceClaim creation: " + i
                        + "/" + howMany);
            }
        }

        til.commitAndStartNewTransaction();
    }

    protected DocumentModel createNewInsuranceClaim() {
        
        Calendar cal;

        DocumentModel claim = session.createDocumentModel(parentPath, "zetitl",
                "InsuranceClaim");

        claimCreation = buildCreationDate();
        claim.setPropertyValue("dc:created", claimCreation);

        claim.setPropertyValue("dublincore:creator", getRandomUser());
        
        // The state will be "Archived" for all cases older than 2 months
        if(claimCreation.before(TWO_MONTHS_AGO)) {
            Calendar modified = RandomDates.addDays(claimCreation, 3, 90, TWO_MONTHS_AGO);
        }
        
        cal = RandomDates.buildDate(claimCreation, 0, 5, true);
        claim.setPropertyValue("incl:incident_date", cal);
        
        claim.setPropertyValue("incl:incident_kind", KINDS[ToolsMisc.randomInt(0, KINDS_MAX)]);
        

        claim.setPropertyValue("pein:first_name",
                firstLastNames.getAFirstName(RandomFirstLastNames.GENDER.ANY));
        claim.setPropertyValue("pein:last_name", firstLastNames.getALastName());
        claim.setPropertyValue("pein:phone_main", randomUSPhoneNumber());
        setCityAndState(claim);

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

    protected void updateLifecycleState(DocumentModel oneClaim) {

        // All the cases of older than 2 months are closed and archived
        if(claimCreation.before(TWO_MONTHS_AGO)) {
            oneClaim.followTransition("to_Archived");
        } else {
            // . . .
        }
    }

    protected void deletePreviousIfNeeded() {

        if (deletePreviousData) {

            String nxql = "SELECT * FROM InsuranceClaim";
            DocumentModelList docs;

            ToolsMisc.forceLogInfo(log, "Deleting previous 'InsuranceClaim'...");
            TransactionInLoop til = new TransactionInLoop(session);
            til.commitAndStartNewTransaction();
            int count = 0;
            do {
                docs = session.query(nxql);
                if (docs.size() > 0) {
                    count += docs.size();
                    ToolsMisc.forceLogInfo(log, "    Deleting " + docs.size()
                            + " 'InsuranceClaim'");
                    for (DocumentModel oneDoc : docs) {
                        session.removeDocument(oneDoc.getRef());

                        til.incrementCounter();
                        til.commitOrRollbackIfNeeded();
                    }
                }
            } while (docs.size() > 0);
            til.commitAndStartNewTransaction();
            ToolsMisc.forceLogInfo(log,
                    "...Deleting previous 'InsuranceClaim' done: " + count
                            + " 'InsuranceClaim' deleted");

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

    public void setCommitModulo(int commitModulo) {
        this.commitModulo = commitModulo;
    }

}
