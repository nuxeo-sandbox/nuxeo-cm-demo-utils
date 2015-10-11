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

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;

/**
 * 
 */
@Operation(id = CreateDemoDataWorkHanlerOp.ID, category = Constants.CAT_SERVICES, label = "InsuranceClaims: Create Data Work Handler", description = "Possible values are: <b>pause</b>, <b>resume</b> or <b>stop</b>")
public class CreateDemoDataWorkHanlerOp {

    public static final String ID = "CreateDemoDataWorkHanlerOp";

    private static final Log log = LogFactory.getLog(CreateDemoDataWorkHanlerOp.class);

    @Param(name = "action", required = true)
    protected String action;

    @OperationMethod
    public void run() throws IOException {

        action = action == null ? "" : action.toLowerCase();

        synchronized (CreateDataDemoWork.LOCK) {
            CreateDataDemoWork work = CreateDataDemoWork.getInstance();
            if (work == null) {
                log.warn("The is no "
                        + CreateDataDemoWork.CATEGORY_CREATE_DATA_DEMO
                        + " running");
            } else {

                if (action.equals("pause")) {
                    work.pause();
                } else if (action.equals("resume")) {
                    work.resume();
                } else if (action.equals("stop")) {
                    work.stop();
                } else {
                    throw new IllegalArgumentException(
                            "INvalid action. Should be 'pause', 'resume' or 'stop'");
                }
            }
        }

    }

}
