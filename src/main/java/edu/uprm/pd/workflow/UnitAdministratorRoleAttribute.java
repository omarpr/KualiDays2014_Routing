package edu.uprm.pd.workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.kuali.rice.kew.api.identity.Id;
import org.kuali.rice.kew.api.identity.PrincipalId;
import org.kuali.rice.kew.api.rule.RoleName;
import org.kuali.rice.kew.engine.RouteContext;
import org.kuali.rice.kew.routeheader.DocumentContent;
import org.kuali.rice.kew.rule.GenericRoleAttribute;
import org.kuali.rice.kew.rule.QualifiedRoleName;
import org.kuali.rice.kew.rule.ResolvedQualifiedRole;

/**
 * @author Omar Soto Fortuño <omar.soto2@upr.edu>, Jeff Covey
 * University of Puerto Rico, Mayagüez
 * UPRM Special Routing
 */
@SuppressWarnings("unchecked")
public class UnitAdministratorRoleAttribute extends GenericRoleAttribute {
	private static final long serialVersionUID = 2187135270043141363L;
	
	private KcPersonService kcPersonService;

	@SuppressWarnings("unused")
	private static final Log LOG = LogFactory.getLog(UnitAdministratorRoleAttribute.class);
	private static final String HOMEUNIT = "homeUnit";
	private static final String PERSONID = "personId";
	private static final String PERSONROLE = "proposalPersonRoleId";

	// Default Values 
	private boolean getHomeUnit = true;
	private String roleName = "Department Head";
	private String unitAdministratorCode = "3";
	private String unitAdministratorAssistantCode = "9";
	
	private List<String> unitIds;
	private List<String> unitNames;
	private Map<String, Integer> unitMembersCount;
	private List<String> roleNames;
	
	UnitService unitService;
	
	private String strAnnotation;
	
	private String piPersonId = "";
	
	public List<String> getQualifiedRoleNames(String roleName,
			DocumentContent documentContent) {
		// RoleName!Unit Administrator Code!TRUE FOR PARENT
		if (roleName != null) {
			String[] info = roleName.split("!");

			if (info.length != 3) {
				LOG.warn("Not enough parameters in the roleName.");
			} else {
				this.roleName = info[0];
				this.unitAdministratorCode = info[1];
				if (info[2].toUpperCase().equals("FALSE")) {
					getHomeUnit = false;
				}
			}
		}
		
		this.roleNames = new ArrayList<String>();
		
		loadUnitRequiredApprovals(documentContent.getRouteContext());
		
		for (String unitName : unitNames) {
			this.roleNames.add(this.roleName.toUpperCase() + "; " + unitName);
		}

		return this.roleNames;
	}
	
	public List<RoleName> getRoleNames() {
		RoleName role = RoleName.Builder.create("org.kuali.kra.workflow.UnitAdministratorRoleAttribute", roleName, roleName).build();
		return Collections.singletonList(role);
	}
	
	@Override
	public Map<String, String> getProperties() {
		// intentionally unimplemented...not intending on using this attribute
		// client-side
		return null;
	}
	
    private UnitService getUnitService() {
    	if (this.unitService == null) {
    		this.unitService = KraServiceLocator.getService(UnitService.class);
    	}
    	
        return unitService;
    }
	
	@Override
	protected List<Id> resolveRecipients(RouteContext routeContext, QualifiedRoleName qualifiedRoleName) {
		loadUnitRequiredApprovals(routeContext);
		
		List<Id> members = new ArrayList<Id>();
		List<String> assistants = new ArrayList<String>();
		String homeUnit = unitIds.get(roleNames.indexOf(qualifiedRoleName.getBaseRoleName()));
		Id personId; 
		
		Boolean isMain, isAssistant, isDeanship, isOnlyOneMember = (unitMembersCount.get(homeUnit) != null) ? (unitMembersCount.get(homeUnit) == 1) : false;
		
	    List<UnitAdministrator> unitAdministrators = getUnitService().retrieveUnitAdministratorsByUnitNumber(homeUnit);
	    for (UnitAdministrator unitAdministrator : unitAdministrators ) {
	    	
	    	if (StringUtils.isNotBlank(unitAdministrator.getPersonId())) {
	    		personId = new PrincipalId(unitAdministrator.getPersonId());
	    		isDeanship = getUnitLevel(unitAdministrator.getUnitNumber()) == 3;
	    		isMain = unitAdministrator.getUnitAdministratorType().getUnitAdministratorTypeCode().equals(unitAdministratorCode);
	    		isAssistant = unitAdministrator.getUnitAdministratorType().getUnitAdministratorTypeCode().equals(unitAdministratorAssistantCode);

	    		// If the OFFICIAL Dean is the PI we will not send a request to him and his delegate,
	    		// the chancellor must approve.
	    		//LOG.info("M: " + isMain + " personID: '" + unitAdministrator.getPersonId() + "' piPersonID: '" + piPersonId + "' Deanship: " + isDeanship + " OnlyMember: " + isOnlyOneMember);
	    		if (isMain && piPersonId.equals(unitAdministrator.getPersonId()) && isDeanship && isOnlyOneMember) {
	    			members.clear();
	    			return members;
	    		}
	    		
	    		if (!members.contains(personId) && (isMain || isAssistant)) {
	    			members.add(personId);
	    			
	    			if (isAssistant) {
	    				assistants.add(getKcPersonService().getKcPersonByPersonId(unitAdministrator.getPersonId()).getFullName());
	    			}
	    		}
	    	}
	    }

    	strAnnotation = (assistants.size() > 0) ? this.roleName.toUpperCase() + " ASSISTANTS: " + StringUtils.join(assistants, ", ") : "";

		return members;
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
	
	private String getNearestUnitAtLevel(String unitNumber, int level) {
		Unit u = getUnitService().getUnit(unitNumber);
		
		do {
			if (getUnitLevel(u.getUnitNumber()) == level) {
				return u.getUnitNumber();
			}
		} while ((u = u.getParentUnit()) != null);

		return unitNumber;
	}
	
	@Override
	protected ResolvedQualifiedRole resolveQualifiedRole(RouteContext routeContext, QualifiedRoleName qualifiedRoleName) {
		List<Id> recipients = resolveRecipients(routeContext, qualifiedRoleName);
		ResolvedQualifiedRole rqr = new ResolvedQualifiedRole(getLabelForQualifiedRoleName(qualifiedRoleName), recipients);
		
		if (strAnnotation != null && !strAnnotation.isEmpty()) {
			rqr.setAnnotation(strAnnotation);
		}
		
		return rqr;
	}
	
	protected void loadUnitRequiredApprovals(RouteContext routeContext) {
		if (unitIds == null || unitNames == null) {
			this.unitIds = new ArrayList<String>();
			this.unitNames = new ArrayList<String>();
			this.unitMembersCount = new HashMap<String, Integer>();
			
			Collection<Element> personnels = retrieveKeyPersonnel(routeContext);
			String homeUnit;
	
			for(Element keyPerson : personnels) {
				if (getHomeUnit) {
					homeUnit = keyPerson.getChildText(HOMEUNIT);
				} else {
					homeUnit = getUnitService().getUnit(keyPerson.getChildText(HOMEUNIT)).getParentUnit().getUnitNumber();
				}
				
				if (keyPerson.getChildText(PERSONROLE).equals(ProposalPersonRole.PRINCIPAL_INVESTIGATOR)) {
					piPersonId = keyPerson.getChildText(PERSONID);
				}
				
				String tempUnit = getNearestUnitAtLevel(homeUnit, 3);
				
				if (!unitIds.contains(homeUnit)) {
					unitIds.add(homeUnit);
					unitNames.add(getUnitService().getUnit(homeUnit).getUnitName());
				}
				
				if (!unitMembersCount.containsKey(tempUnit)) {
					unitMembersCount.put(tempUnit, 1);
				} else {
					unitMembersCount.put(tempUnit, unitMembersCount.get(tempUnit) + 1);
				}
			}
		}
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
	
}
