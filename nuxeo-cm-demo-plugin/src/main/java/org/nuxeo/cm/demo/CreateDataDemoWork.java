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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.lifecycle.LifeCycleException;
import org.nuxeo.ecm.core.work.AbstractWork;

/**
 * The misc. values (howMany, commitModulo, ...) cannot be modified after the
 * creation of data has started. No error is thrown, but an error is logged
 *
 * @since 7.1
 */
public class CreateDataDemoWork extends AbstractWork {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(CreateDataDemoWork.class);

    public static final String CATEGORY_CREATE_DATA_DEMO = "CreateCMDataDemo";

    protected final DocumentModel parentDoc;
    
    protected boolean deletePreviousClaims = CreateDemoData.DEFAULT_DELETE_PREVIOUS_CLAIMS;

    protected int howMany = CreateDemoData.DEFAULT_HOW_MANY;

    protected int commitModulo = CreateDemoData.DEFAULT_COMMIT_MODULO;

    protected int logModulo = CreateDemoData.DEFAULT_LOG_MODULO;

    protected int yieldToBgWorkModulo = CreateDemoData.DEFAULT_YIELD_TO_BG_WORK_MODULO;

    protected int sleepDurationAfterCommit = CreateDemoData.DEFAULT_SLEEP_DURATION_AFTER_COMMIT;

    protected int sleepModulo = CreateDemoData.DEFAULT_SLEEP_MODULO;

    protected int sleepDurationMs = CreateDemoData.DEFAULT_SLEEP_DURATION_MS;

    protected boolean started = false;
    
    protected boolean isRunning = false;
    
    protected CreateDemoData createDemoData;
    
    private static CreateDataDemoWork instance;
    
    public static final String LOCK = "CreateDataDemoWork";
    
    public static CreateDataDemoWork getInstance() {
        return instance;
    }
    
    public static CreateDataDemoWork getInstance(DocumentModel inParent) {
        
        if(instance == null) {
            synchronized(LOCK) {
                if(instance == null) {
                    instance = new CreateDataDemoWork(inParent);
                }
            }
        }
        
        return instance;
    }
    

    private CreateDataDemoWork(DocumentModel inParent) {
        super();
        parentDoc = inParent;
    }

    @Override
    public String getTitle() {
        return "CM: Create Demo Data in " + parentDoc.getPathAsString();
    }

    @Override
    public void work() {
        
        isRunning = true;

        boolean withError = false;
        setStatus("Creating data demo");
        
        CoreSession session = initSession();

        createDemoData = new CreateDemoData(session, parentDoc, howMany);
        createDemoData.setDeletePreviousClaims(deletePreviousClaims);
        createDemoData.setCommitModulo(commitModulo);
        createDemoData.setLogModulo(logModulo);
        createDemoData.setYieldToBgWorkModulo(yieldToBgWorkModulo);
        
        createDemoData.setWorker(this);
        try {
            started = true;
            createDemoData.run();
        } catch (IOException | DocumentException | LifeCycleException e) {
            withError = true;
            log.error("Data-demo creation interrupted with an error", e);
        }

        String status = "Creating data demo: Done";
        if (withError) {
            status += " (with error. Check the log for details)";
        }
        setStatus(status);
        started = false;
        
        isRunning = false;
        instance = null;
    }

    @Override
    public String getCategory() {
        return CATEGORY_CREATE_DATA_DEMO;
    }
    
    public void setDeletePreviousClaims(boolean inValue) {

        if (started) {
            log.error("Cannot change the value of deletePreviousClaims because creation of data is running.");
        } else {
            deletePreviousClaims = inValue;
        }
    }

    public void setHowMany(int inValue) {
        if (started) {
            log.error("Cannot change the value of howMany because creation of data is running.");
        } else {
            howMany = inValue;
        }
    }

    public void setCommitModulo(int inValue) {
        if (started) {
            log.error("Cannot change the value of commitModulo because creation of data is running.");
        } else {
            commitModulo = inValue;
        }
    }

    public void setLogModulo(int inValue) {
        if (started) {
            log.error("Cannot change the value of logModulo because creation of data is running.");
        } else {
            logModulo = inValue;
        }
    }

    public void setYieldToBgWorkModulo(int inValue) {

        if (started) {
            log.error("Cannot change the value of yieldToBgWorkModulo because creation of data is running.");
        } else {
            yieldToBgWorkModulo = inValue;
        }
    }

    public void setSleepDurationAfterCommit(int inValue) {
        sleepDurationAfterCommit = inValue;
    }

    public void setSleepModulo(int inValue) {
        sleepModulo = inValue;
    }

    public void setSleepDurationMs(int inValue) {
        sleepDurationMs = inValue;
    }
    
    public void pause() {
        if(createDemoData != null) {
            createDemoData.pause();
        }
    }
    
    public void resume() {
        if(createDemoData != null) {
            createDemoData.resume();
        }
    }
    
    public void stop() {
        if(createDemoData != null) {
            createDemoData.stop();
        }
    }
    
    public CreateDemoData.RUNNING_STATUS getRunningStatus() {
        if(createDemoData != null) {
            return createDemoData.getStatus();
        }
        
        return CreateDemoData.RUNNING_STATUS.UNKNOWN;
    }

}
