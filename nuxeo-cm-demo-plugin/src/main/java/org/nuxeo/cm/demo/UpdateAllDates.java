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
 *     thibaud
 */
package org.nuxeo.cm.demo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * 
 *
 * @since 7.1
 */
public class UpdateAllDates {
    
    private static final Log log = LogFactory.getLog(UpdateAllDates.class);
    
    CoreSession session;
    
    int diffInDays;
        
    public UpdateAllDates(CoreSession inSession, Date inLastUpdate) {
        
        session = inSession;
        
        long diffInMs = Calendar.getInstance().getTimeInMillis() - inLastUpdate.getTime();
        int diffInDays = (int) TimeUnit.DAYS.convert(diffInMs, TimeUnit.MILLISECONDS);
        if(diffInMs < 86400000 || diffInDays < 1) {
            diffInDays = 0;
        }
    }
    
    
    public void run() {
        
        if(diffInDays < 1) {
            log.error("Date received is in the future or less than one day: No update done");
            return;
        }
        
        MiscUtils.forceLogInfo(log, "\n--------------------\nIncrease all dates by " + diffInDays + " days\n--------------------");
        
        /*
        MiscUtils.forceLogInfo(log, "Updating dublincore dates for all and every documents...");
        updateDublincoreDates();
        
        MiscUtils.forceLogInfo(log, "Updating dates for claims and related documents...");
        updateInsuranceClaimDates();
        
        MiscUtils.forceLogInfo(log, "Updating dates for workflows...");
        updateWorkflowDates();
        */
        
        String nxql;
        DocumentModelList allDocs;
        ArrayList<String> xpaths;
        
        SchemaManager sm = Framework.getLocalService(SchemaManager.class);
        DocumentType [] allTypes = sm.getDocumentTypes();
        for(DocumentType dt : allTypes) {
            Collection<Schema> schemas = dt.getSchemas();
            xpaths = new ArrayList<String>();
            
            for(Schema schema : schemas) {
                for(Field field : schema.getFields()) {
                    Type t = field.getType();
                    if(t.isSimpleType() && t.getName().equals("date")) {
                        xpaths.add("" + field.getName());
                    }
                }
            }
            
            if(xpaths.size() > 0) {
                MiscUtils.forceLogInfo(log, "Update dates for documents of type: " + dt.getName());
                nxql = "SELECT * FROM " + dt.getName();
                allDocs = session.query(nxql);
                updateDocs(allDocs, xpaths);
            }
            
        }
        
    }
    
    protected void updateWorkflowDates() {
        
        String nxql;
        DocumentModelList allDocs;
        ArrayList<String> xpaths;
        
        SchemaManager sm = Framework.getLocalService(SchemaManager.class);
        DocumentType [] allTypes = sm.getDocumentTypes();
        for(DocumentType dt : allTypes) {
            Collection<Schema> schemas = dt.getSchemas();
            xpaths = new ArrayList<String>();
            
            for(Schema schema : schemas) {
                for(Field field : schema.getFields()) {
                    Type t = field.getType();
                    if(t.isSimpleType()) {
                        if(t.getName().equals("date")) {
                            xpaths.add("" + field.getName());
                        }
                    }
                }
            }
            
            if(xpaths.size() > 0) {
                MiscUtils.forceLogInfo(log, "Update dates for documents of type: " + dt.getName());
                nxql = "SELECT * FROM " + dt.getName();
                allDocs = session.query(nxql);
                updateDocs(allDocs, xpaths);
            }
            
        }
    }

    
    /*
     * (unused)
     */
    protected void updateInsuranceClaimDates() {
        
        // --------------------------------------------------
        MiscUtils.forceLogInfo(log, "Update dates: InsuranceClaims");
        String nxql = "SELECT * FROM Document WHERE ecm:primaryType = 'InsuranceClaim'";
        DocumentModelList allDocs = session.query(nxql);
        
        ArrayList<String> xpaths = new ArrayList<String>();
        xpaths.add("incl:contract_end");
        xpaths.add("incl:contract_start");
        xpaths.add("incl:date_closed");
        xpaths.add("incl:date_decision_sent");
        xpaths.add("incl:date_received");
        xpaths.add("incl:date_request_valuation");
        xpaths.add("incl:due_date");
        xpaths.add("incl:due_date");
        updateDocs(allDocs, xpaths);
        

        // --------------------------------------------------
        MiscUtils.forceLogInfo(log, "Update dates: PersonInfos");
        nxql = "SELECT * FROM Document WHERE ecm:primaryType = 'PersonInfos'";
        allDocs = session.query(nxql);
        
        xpaths = new ArrayList<String>();
        xpaths.add("pein:date_of_birth");
        updateDocs(allDocs, xpaths);
        
    }
    
    /*
     * (unused)
     */
    protected void updateDublincoreDates() {
        
        // Ok, now, we basically add diffInMs to all relevant dates (creation, modification, ...)
        String nxql = "SELECT * FROM Document";
        DocumentModelList allDocs = session.query(nxql);

        ArrayList<String> xpaths = new ArrayList<String>();
        xpaths.add("dc:created");
        xpaths.add("dc:modified");
        xpaths.add("dc:issued");
        xpaths.add("dc:valid");
        xpaths.add("dc:expired");
        updateDocs(allDocs, xpaths);
        
    }
    
    /**
     * Update all date fields whose xpaths are passed in <code>inXPaths</code>.
     * <p>
     * No control/check if the document has the correct schema.
     * 
     * @param inDocs
     * @param inXPaths
     *
     * @since 7.2
     */
    protected void updateDocs(DocumentModelList inDocs, ArrayList<String> inXPaths) {
        
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        
        int count = 0;
        for(DocumentModel oneDoc : inDocs) {
            
            for(String xpath : inXPaths) {
                updateDate(oneDoc, xpath);
            }
            
            // Save without dublincore and custom events (in the Studio project)
            oneDoc.putContextData(DublinCoreListener.DISABLE_DUBLINCORE_LISTENER, true);
            oneDoc.putContextData("UpdatingData_NoEventPlease", true);
            oneDoc = session.saveDocument(oneDoc);
            count += 1;
            if((count % 10) == 0) {
                TransactionHelper.commitOrRollbackTransaction();
                TransactionHelper.startTransaction();
            }
            if((count % 500) == 0) {
                MiscUtils.forceLogInfo(log, "" + count);
            }
        }

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        
    }
    
    protected void updateDate(DocumentModel inDoc, String inXPath) {
        
        Calendar d = (Calendar) inDoc.getPropertyValue(inXPath);
        if(d != null) {
            d.add(Calendar.DATE, diffInDays);
            inDoc.setPropertyValue(inXPath, d);
        }
    }
    
}
