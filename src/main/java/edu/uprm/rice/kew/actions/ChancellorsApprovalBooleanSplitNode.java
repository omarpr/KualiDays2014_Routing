/**
 * Copyright 2005-2011 The Kuali Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.opensource.org/licenses/ecl2.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uprm.rice.kew.actions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jdom.Document;
import org.jdom.Element;
import org.kuali.kra.bo.Unit;
import org.kuali.kra.bo.UnitAdministrator;
import org.kuali.kra.infrastructure.KraServiceLocator;
import org.kuali.kra.proposaldevelopment.bo.ProposalPerson;
import org.kuali.kra.proposaldevelopment.bo.ProposalPersonRole;
import org.kuali.kra.service.KcPersonService;
import org.kuali.kra.service.UnitService;
import org.kuali.rice.core.api.util.xml.XmlHelper;
import org.kuali.rice.kew.engine.RouteContext;
import org.kuali.rice.kew.engine.RouteHelper;
import org.kuali.rice.kew.engine.node.SplitNode;
import org.kuali.rice.kew.engine.node.SplitResult;

/**
 * This is a SplitNode that will decide in the Chancellors Approval is needed for the Proposal. 
 * 
 * @author Omar Soto Fortuño <omar.soto2@upr.edu>
 * University of Puerto Rico, Mayagüez
 * edu.uprm.rice.kew.actions.ChancellorsApprovalBooleanSplitNode
 */
public class ChancellorsApprovalBooleanSplitNode implements SplitNode {
	private static org.apache.commons.logging.Log LOG = org.apache.commons.logging.LogFactory.getLog(ChancellorsApprovalBooleanSplitNode.class);
 
	private UnitService unitService;
 	private KcPersonService kcPersonService;
 	
 	private String unitAdministratorCode = "3";

	private static final String PERSONID = "personId";
	private static final String PERSONROLE = "proposalPersonRoleId";
		 
    /**
     * This method will look up the document being routed, if it is an instance of ResearchDocumentBase
     * it will call answerSplitNodeQuestion on it passing the name of the route node.  The default implementation (currently)
     * throws an UnsupportedOperationException for any input. If one wishes to support the SplitNode for a given document, the
     * method should be overridden and return boolean T/F based on which of the branches ( always names "True" and "False" ) 
     * KEW should route to based upon the name of the split node.
     * 
     * @see org.kuali.rice.kew.engine.node.SimpleNode#process(org.kuali.rice.kew.engine.RouteContext, org.kuali.rice.kew.engine.RouteHelper)
     */
    
    public SplitResult process(RouteContext context, RouteHelper helper) throws Exception {
    	 return this.booleanToSplitResult(isChancellorsApprovalNeeded(context));
    }
    
    private boolean isChancellorsApprovalNeeded(RouteContext context) { 
		 Collection<Element> personnels = retrieveKeyPersonnel(context);
		 Collection<Unit> units = getUnitService().getUnits();
		 List<UnitAdministrator> unitAdmins;
		 String piPersonId = "";
		 Boolean isDeanship, isMain;
		 
		 // Get the PI
		 for (Element keyPerson : personnels) {
			if (keyPerson.getChildText(PERSONROLE).equals(ProposalPersonRole.PRINCIPAL_INVESTIGATOR)) {
				piPersonId = keyPerson.getChildText(PERSONID);
			}
		 }
		 
		 if (!piPersonId.isEmpty()) {
			 for (Unit u : units) {
				 unitAdmins = u.getUnitAdministrators();
				 for (UnitAdministrator unitAdministrator : unitAdmins) {
					 isDeanship = getUnitLevel(unitAdministrator.getUnitNumber()) == 3;
					 isMain = unitAdministrator.getUnitAdministratorType().getUnitAdministratorTypeCode().equals(unitAdministratorCode);
					 
					 if (isMain && piPersonId.equals(unitAdministrator.getPersonId()) && isDeanship) {
						 return true;
					 }
				 }
			 }
		 }
		 
		 return false;
    }
    
    /**
     * Converts a boolean value to SplitResult where the branch name is "True" or "False" based on the value of the given boolean
     * @param b a boolean to convert to a SplitResult
     * @return the converted SplitResult
     */
    protected SplitResult booleanToSplitResult(boolean b) {
        List<String> branches = new ArrayList<String>();
        final String branchName = b ? "chancellorNeeded" : "chancellorNotNeeded";
        branches.add(branchName);
        return new SplitResult(branches);
    }
    
	@SuppressWarnings({ "unchecked", "unchecked" })
	private Collection<Element> retrieveKeyPersonnel(RouteContext context) {
	    Document document = XmlHelper.buildJDocument(context.getDocumentContent().getDocument());
	   
	    Collection<Element> personnels = XmlHelper.findElements(document.getRootElement(), ProposalPerson.class.getName());
	    return personnels;
	}
	
    protected KcPersonService getKcPersonService() {
        if (kcPersonService == null) {
            kcPersonService = KraServiceLocator.getService(KcPersonService.class);
        }
        return kcPersonService;
    }
    
    private UnitService getUnitService() {
    	if (this.unitService == null) {
    		this.unitService = KraServiceLocator.getService(UnitService.class);
    	}
    	
        return unitService;
    }
    
	/*
	 * Important Levels:
	 * 	- Level 1 = University
	 *  - Level 2 = Campus
	 *  - Level 3 = College / Deanship
	 *  - Level 4 = Department
	 */
	private int getUnitLevel(String unitNumber) {
		Unit u = getUnitService().getUnit(unitNumber);
		int count = 1;
		
		while ((u = u.getParentUnit()) != null) count++;
		
		return count;
	}

}
