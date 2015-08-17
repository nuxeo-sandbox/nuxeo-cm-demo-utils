/*
 * (C) Copyright ${year} Nuxeo SA (http://nuxeo.com/) and contributors.
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.datademo.UpdateAllDates;
import org.nuxeo.datademo.tools.DocumentsCallback;
import org.nuxeo.datademo.tools.DocumentsWalker;
import org.nuxeo.datademo.tools.ListenersDisabler;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * 
 */
@Operation(id = UpdateAllDatesOp.ID, category = Constants.CAT_SERVICES, label = "CM Demo: Update All Dates", description = "Update all date fields. IMPORTANT: This operation should run in an asynchronous event")
public class UpdateAllDatesOp {

    public static final String ID = "CMDemo.UpdateAllDates";

    private static final Log log = LogFactory.getLog(UpdateAllDatesOp.class);

    protected DateFormat _yyyyMMdd = new SimpleDateFormat("yyyyMMdd");

    @Context
    protected CoreSession session;

    @Param(name = "numberOfDays", required = true)
    protected long numberOfDays;

    @Param(name = "listenersToDisable", required = false)
    protected String listenersToDisable = "";

    @OperationMethod
    public void run() {

        log.warn("WARNING: Still under dev/test/not sure we'll ever be using it.");

        UpdateAllDates uad = new UpdateAllDates(session, (int) numberOfDays);

        String[] listenersNames = null;
        if (StringUtils.isNotBlank(listenersToDisable)) {
            listenersToDisable.replaceAll(" ", "");
            listenersNames = listenersToDisable.split(",");
            for (String oneName : listenersNames) {
                uad.addListenerToDisable(oneName.trim());
            }
        }

        uad.run();

        log.warn("Update Claim IDs and titles...");

        ListenersDisabler ld = null;
        if (listenersNames != null && listenersNames.length > 0) {
            ld = new ListenersDisabler();
            for (String oneName : listenersNames) {
                ld.addListener(oneName);
            }
            ld.disableListeners();
        }

        // Update all title/claim IDs (based on date)
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        String nxql = "SELECT * FROM InsuranceClaim";
        DocumentsWalker dw = new DocumentsWalker(session, nxql, 1000);
        UpdateDataWalkerCallback cb = new UpdateDataWalkerCallback();
        dw.runForEachDocument(cb);

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        if (ld != null) {
            ld.restoreListeners();
        }
        log.warn("Update Claim IDs and titles done.");

        log.warn("");
        log.warn("Here, depending on disabled listeners, you should reindex");
        log.warn("");

    }

    protected class UpdateDataWalkerCallback implements DocumentsCallback {

        long documentCount = 0;

        ReturnStatus lastReturnStatus;

        @Override
        public ReturnStatus callback(List<DocumentModel> inDocs) {

            throw new RuntimeException("Should not be here. We are walking doc by doc");
        }

        @Override
        public ReturnStatus callback(DocumentModel inDoc) {

            documentCount += 1;
            try {
                doUpdateDoc(inDoc);
            } catch (Exception e) {
                log.error("Error while updating a document", e);
                return ReturnStatus.STOP;
            }

            if ((documentCount % 100) == 0) {
                TransactionHelper.commitOrRollbackTransaction();
                TransactionHelper.startTransaction();
            }

            if ((documentCount % 250) == 0) {
                log.warn("Updated: " + documentCount);
            }

            return ReturnStatus.CONTINUE;

        }

        @Override
        public void init() {
            // Unused here
        }

        @Override
        public void end(ReturnStatus inLastReturnStatus) {
            lastReturnStatus = inLastReturnStatus;
        }

        public long getDocumentCount() {
            return documentCount;
        }

    }

    protected void doUpdateDoc(DocumentModel inDoc) {

        Calendar created = (Calendar) inDoc.getPropertyValue("dc:created");

        String createdStr = _yyyyMMdd.format(created.getTime());
        String title = (String) inDoc.getPropertyValue("dc:title");

        title = createdStr + title.substring(8);
        inDoc.setPropertyValue("dc:title", title);
        inDoc.setPropertyValue("incl:incident_id", title);

        inDoc.putContextData(DublinCoreListener.DISABLE_DUBLINCORE_LISTENER, true);
        session.saveDocument(inDoc);

    }

}
