/*
 * (C) Copyright ${year} Nuxeo SA (http://nuxeo.com/) and others.
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

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.CoreSession;

/**
 * @author Thibaud Arguillere
 */
@Operation(id = UpdateDemoDataOp.ID, category = Constants.CAT_SERVICES, label = "Update Demo Data", description = "UPdates the demo data: change the dates, etc. Make sure you are logged in with enought right for this")
public class UpdateDemoDataOp {

    public static final String ID = "UpdateDemoData";

    @Context
    protected CoreSession session;

    @OperationMethod
    public void run() throws Exception {

        UpdateDemoData udd = new UpdateDemoData(session);

        udd.run();
    }

}
