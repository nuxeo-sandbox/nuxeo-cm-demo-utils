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
 * 
 *
 * @since 7.1
 */
public class CreateDataDemoWork extends AbstractWork {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(CreateDataDemoWork.class);

    public static final String CATEGORY_CREATE_DATA_DEMO = "CreateCMDataDemo";

    protected final DocumentModel parentDoc;
    
    protected int howMany = 0;
    
    protected int commitModulo = 0;
    
    protected int logModulo = 0;

    public CreateDataDemoWork(DocumentModel inParent) {
        super();
        parentDoc = inParent;
    }

    @Override
    public String getTitle() {
        return "CM: Create Demo Data in " + parentDoc.getPathAsString();
    }

    @Override
    public void work() {
        
        boolean withError = false;
        setStatus("Creating data demo");
        CoreSession session = initSession();
        
        CreateDemoData cdd = new CreateDemoData(session, parentDoc, howMany);
        cdd.setCommitModulo(commitModulo);
        cdd.setLogModulo(logModulo);
        cdd.setWorker(this);
        try {
            cdd.run();
        } catch (IOException | DocumentException | LifeCycleException e) {
            withError = true;
            log.error("Data-demo creation interrupted with an error", e);
        }
        
        String status = "Creating data demo: Done";
        if(withError) {
            status += " (with error. Check the log for details)";
        }
        setStatus(status);
        
    }

    @Override
    public String getCategory() {
        return CATEGORY_CREATE_DATA_DEMO;
    }
    
    public void setCommitModulo(int inValue) {
        commitModulo = inValue;
    }
    
    public void setLogModulo(int inValue) {
        logModulo = inValue;
    }
    
    public void setHowMany(int inValue) {
        howMany = inValue;
    }

}
