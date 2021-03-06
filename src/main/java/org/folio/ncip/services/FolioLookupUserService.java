package org.folio.ncip.services;


import org.apache.log4j.Logger;
import org.extensiblecatalog.ncip.v2.service.AgencyId;
import org.extensiblecatalog.ncip.v2.service.AgencyUserPrivilegeType;
import org.extensiblecatalog.ncip.v2.service.AuthenticationInput;
import org.extensiblecatalog.ncip.v2.service.ElectronicAddress;
import org.extensiblecatalog.ncip.v2.service.ElectronicAddressType;
import org.extensiblecatalog.ncip.v2.service.LookupUserInitiationData;
import org.extensiblecatalog.ncip.v2.service.LookupUserResponseData;
import org.extensiblecatalog.ncip.v2.service.LookupUserService;
import org.extensiblecatalog.ncip.v2.service.NameInformation;
import org.extensiblecatalog.ncip.v2.service.PersonalNameInformation;
import org.extensiblecatalog.ncip.v2.service.PhysicalAddress;
import org.extensiblecatalog.ncip.v2.service.PhysicalAddressType;
import org.extensiblecatalog.ncip.v2.service.Problem;
import org.extensiblecatalog.ncip.v2.service.ProblemType;
import org.extensiblecatalog.ncip.v2.service.RemoteServiceManager;
import org.extensiblecatalog.ncip.v2.service.ServiceContext;
import org.extensiblecatalog.ncip.v2.service.StructuredAddress;
import org.extensiblecatalog.ncip.v2.service.StructuredPersonalUserName;
import org.extensiblecatalog.ncip.v2.service.UserAddressInformation;
import org.extensiblecatalog.ncip.v2.service.UserAddressRoleType;
import org.extensiblecatalog.ncip.v2.service.UserId;
import org.extensiblecatalog.ncip.v2.service.UserOptionalFields;
import org.extensiblecatalog.ncip.v2.service.UserPrivilege;
import org.extensiblecatalog.ncip.v2.service.UserPrivilegeStatus;
import org.extensiblecatalog.ncip.v2.service.UserPrivilegeStatusType;
import org.folio.ncip.Constants;
import org.folio.ncip.FolioRemoteServiceManager;
import org.folio.ncip.domain.Account;
import org.folio.ncip.domain.DroolsResponse;
import org.folio.ncip.domain.Loan;
import org.folio.ncip.domain.Patron;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.rule.Rule;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;




public class FolioLookupUserService  extends FolioNcipService  implements LookupUserService  {
	
	 private static final Logger logger = Logger.getLogger(FolioLookupUserService.class);
	 public long reqTimeoutMs;
	 public JsonObject obj;
	 private KieContainer kieContainer;
	 private Properties ncipProperties;
	 

	 
	 
     public LookupUserResponseData performService(LookupUserInitiationData initData,
			 ServiceContext serviceContext,
             RemoteServiceManager serviceManager) {
		     logger.info("data passed into performService  : ");
		     logger.info(initData.toString());
			 LookupUserResponseData responseData = new LookupUserResponseData();
			 this.ncipProperties = ((FolioRemoteServiceManager)serviceManager).getNcipProperties();
			 this.kieContainer = ((FolioRemoteServiceManager)serviceManager).getKieContainer();

			 UserId userId = retrieveUserId(initData);
		     
		     
		       try {
		        	validateUserId(userId);
		       }
		       catch(Exception exception) {
		        	if (responseData.getProblems() == null) responseData.setProblems(new ArrayList<Problem>());
		        	Problem p = new Problem(new ProblemType(Constants.LOOKUP_USER_VALIDATION_PROBLEM),Constants.LOOKUP_USER_VALIDATION_PROBLEM,exception.getMessage(),exception.getMessage());
		        	responseData.getProblems().add(p);
		        	return responseData;
		        } 

			  try {
				//THE SERVICE MANAGER CALLS THE OKAPI APIs
				 JsonObject patronDetailsAsJson = ((FolioRemoteServiceManager)serviceManager).lookupUser(userId);
				 if (patronDetailsAsJson == null) {
					  ProblemType problemType = new ProblemType("");
					  Problem p = new Problem(problemType,Constants.USERID,Constants.USER_NOT_FOUND);
			    	  responseData.setProblems(new ArrayList<Problem>());
			    	  responseData.getProblems().add(p);
			    	  return responseData;
				 }
				 responseData =constructResponse(initData,patronDetailsAsJson);
				 logger.info("API LOOKUP RESULTS...");
				 logger.info(patronDetailsAsJson.toString());

			 } catch (Exception e) {
				 logger.error("error during performService:");
				 logger.error(e.toString());
				 ProblemType problemType = new ProblemType("");
				 Problem p = new Problem(problemType,Constants.GENERAL_ERROR, Constants.LOOKUP_USER_FAILED + e.toString());
		    	 responseData.setProblems(new ArrayList<Problem>());
		    	 responseData.getProblems().add(p);
		    	 return responseData;
			}
			 return responseData;
		 
	
		   
	 }

	 private LookupUserResponseData constructResponse(LookupUserInitiationData initData,JsonObject userDetails) throws Exception {
		 
		 LookupUserResponseData responseData = new LookupUserResponseData();
		 try {
			 
			 String agencyId = initData.getInitiationHeader().getFromAgencyId().getAgencyId().getValue();
			  if (responseData.getUserOptionalFields()==null)
		        	responseData.setUserOptionalFields(new UserOptionalFields());
			  
			 if (initData.getNameInformationDesired()) {
				 responseData.getUserOptionalFields().setNameInformation(this.retrieveName(userDetails));
			 }
			 
			 if (initData.getUserIdDesired())
				 responseData.setUserId(initData.getUserId());
			 
			  if (initData.getUserAddressInformationDesired())
		        	responseData.getUserOptionalFields().setUserAddressInformations(this.retrieveAddress(userDetails));
			   
			  if (initData.getUserPrivilegeDesired()) {
		        	responseData.getUserOptionalFields().setUserPrivileges(this.retrievePrivileges(userDetails,agencyId));
			        responseData.getUserOptionalFields().getUserPrivileges().add(this.retrieveBorrowingPrvilege(userDetails,agencyId));
			  }
		 }
		 catch(Exception e) {
			 logger.error("error during constructing lookup user construct response:");
			 logger.error(e.toString());
			 throw e;
		 }
		 return responseData;
	 }


	    private UserPrivilege retrieveBorrowingPrvilege(JsonObject jsonObject,String agencyId) throws Exception{
	    	UserPrivilege up = new UserPrivilege();
	    	up.setUserPrivilegeDescription("User Status");
	    	up.setAgencyId( new AgencyId(agencyId));
	    	up.setAgencyUserPrivilegeType(new AgencyUserPrivilegeType(null,"STATUS"));
	    	UserPrivilegeStatus ups = new UserPrivilegeStatus();
	    	String patronBorrowingStatus = checkRules(jsonObject);
	    	ups.setUserPrivilegeStatusType(new UserPrivilegeStatusType(null,patronBorrowingStatus));
	    	up.setUserPrivilegeStatus(ups);
	    	return up;
	    }
	    
	    
	    private String checkRules(JsonObject jsonObject) throws Exception {
	    	try {

		    	Patron patron = new Patron();
		    	JsonArray loans = jsonObject.getJsonArray("loans");
		    	for (int i = 0 ; i < loans.size(); i++) {
		    	        JsonObject obj = loans.getJsonObject(i);
		    	        Loan loan = new Loan();
		    	        loan.setId(obj.getString("id"));
		    	        patron.getLoans().add(loan);
		    	}
		    	JsonArray fines = jsonObject.getJsonArray("accounts");
		    	for (int i = 0 ; i < fines.size(); i++) {
		    	        JsonObject obj = fines.getJsonObject(i);
		    	        Account account = new Account();
		    	        account.setRemaining(obj.getDouble("remaining"));
		    	        patron.getAccounts().add(account);
		    	}
		    	
		    	//do any manual blocks exist?
		    	JsonArray blocks = jsonObject.getJsonArray("manualblocks");
		    	Iterator  i = blocks.iterator();
		    	while (i.hasNext()) {
		    		JsonObject block = (JsonObject) i.next();
		    		if (block.getBoolean(Constants.BORROWING_BLOCK)) return Constants.BLOCKED;
		    		if (block.getBoolean(Constants.REQUEST_BLOCK)) return Constants.BLOCKED;;
	
		    	}
		    	
		    	//IS THE PATRON ACTIVE
		    	if (!jsonObject.getBoolean(Constants.ACTIVE)) return Constants.BLOCKED;

		    	//CHECK NCIP CIRC RULES
		    	KieSession ksession = kieContainer.newKieSession();
		    	ksession.insert(patron);
		    	DroolsResponse droolsResponse = new DroolsResponse();
		    	ksession.insert(droolsResponse);
		    	int firedRules = ksession.fireAllRules();
		    	logger.info("RULES FIRED: " + firedRules);
		    	logger.info("PATRON CAN BORROW" + patron.canBorrow());
		    	KieBase kieBase = ksession.getKieBase();
		    	Collection<KiePackage> kiePackages = kieBase.getKiePackages();
		    	for( KiePackage kiePackage: kiePackages ){
		    	    for( Rule rule: kiePackage.getRules() ){ 
		    	        logger.info( rule.getName() );
		    	    }
		    	}

		     return patron.canBorrow() ? Constants.OK : Constants.BLOCKED;
	    	}
	    	catch(Exception e) {
	    		logger.error("error during checkRules");
	    		logger.error(e.getLocalizedMessage());
	    		throw new Exception("  Error during checkRules.  Looking at blocks, loans, and fines.  " , e);
	    	}
	    }
	    
	    

	 
	    private ArrayList<UserPrivilege> retrievePrivileges(JsonObject jsonObject,String agencyId) {
	    	String patronType = jsonObject.getString("group");
	    	String patronHomeLibrary = jsonObject.getString("code");
	    	if (patronHomeLibrary == null) patronHomeLibrary = this.ncipProperties.getProperty(agencyId.toLowerCase() + ".library" );
	    	ArrayList<UserPrivilege> list = new ArrayList<UserPrivilege>();;
	    	list.add(this.retrievePrivilegeFor(patronType, "User Profile", "PROFILE",agencyId));
	    	list.add(this.retrievePrivilegeFor(patronHomeLibrary,"User Library","LIBRARY",agencyId));
	    	return list;
	    }
	    
	    private UserPrivilege retrievePrivilegeFor(String userPrivilegeStatusTypeString, String descriptionString, String agencyUserPrivilegeTypeString,String agencyId) {
	    	
	    	UserPrivilege up = new UserPrivilege();
	    	up.setUserPrivilegeDescription(descriptionString);
	    	up.setAgencyId( new AgencyId(agencyId));
	    	up.setAgencyUserPrivilegeType(new AgencyUserPrivilegeType(null,agencyUserPrivilegeTypeString));
	    	UserPrivilegeStatus ups = new UserPrivilegeStatus();
	    	ups.setUserPrivilegeStatusType(new UserPrivilegeStatusType(null,userPrivilegeStatusTypeString));
	    	up.setUserPrivilegeStatus(ups);
	    	return up;
	    }
	  
	   private NameInformation retrieveName(JsonObject jsonObject) {
		    JsonObject personal = jsonObject.getJsonObject("personal"); //TODO constants
	    	String firstName = personal.getString("lastName");
	    	String lastName = personal.getString("firstName");
	    	NameInformation nameInformation = new NameInformation();
	    	PersonalNameInformation personalNameInformation = new PersonalNameInformation();
	    	StructuredPersonalUserName structuredPersonalUserName = new StructuredPersonalUserName();
	    	structuredPersonalUserName.setGivenName(firstName);
	    	structuredPersonalUserName.setSurname(lastName);
	    	personalNameInformation.setUnstructuredPersonalUserName(firstName + " " + lastName);
	    	personalNameInformation.setStructuredPersonalUserName(structuredPersonalUserName);
	    	nameInformation.setPersonalNameInformation(personalNameInformation);
	    	return nameInformation;
	    }
	  
	  
	   private ArrayList<UserAddressInformation> retrieveAddress(JsonObject jsonObject) {
	    	ArrayList<UserAddressInformation> list = new ArrayList<UserAddressInformation>();
	    	JsonObject personal = jsonObject.getJsonObject(Constants.PERSONAL);
	    	JsonArray arrayOfAddresses = personal.getJsonArray("addresses");
	    	if (arrayOfAddresses == null || arrayOfAddresses.isEmpty()) return list;
	    	for(int i = 0; i < arrayOfAddresses.size(); i++) {
	    		   JsonObject jsonAddress = arrayOfAddresses.getJsonObject(i);
	    		   list.add(retrievePhysicalAddress(jsonAddress));
	    	}
	    	list.add(retrieveEmail(jsonObject));
	    	list.add(retrieveTelephoneNumber(jsonObject));
	    	return list;
	    }
	  
	  
	    private UserAddressInformation retrieveTelephoneNumber(JsonObject jsonObject) {
	    	
		    JsonObject personal = jsonObject.getJsonObject(Constants.PERSONAL); //TODO constants
	    	String phoneNumber = personal.getString("phone");
	    	if (phoneNumber != null) {
	    		ElectronicAddress phone = new ElectronicAddress();
	    		phone.setElectronicAddressData(phoneNumber);
	    		phone.setElectronicAddressType(new ElectronicAddressType("TEL")); //TODO constants
	    		UserAddressInformation uai = new UserAddressInformation();
	    		uai.setUserAddressRoleType(new UserAddressRoleType(Constants.CAMPUS)); //TODO: constants, what should this be?  
	    		uai.setElectronicAddress(phone);
	    		return uai;
	    	}
	    	else return null;
	    }
	    
	    private UserAddressInformation retrieveEmail(JsonObject jsonObject) {
	 
		    JsonObject personal = jsonObject.getJsonObject(Constants.PERSONAL); 
	    	String emailAddress = personal.getString("email"); //TODO constants
	    	ElectronicAddress email = new ElectronicAddress();
	    	email.setElectronicAddressData(emailAddress);
	    	email.setElectronicAddressType(new ElectronicAddressType("electronic mail address")); //TODO CONSTANT
	    	UserAddressInformation uai = new UserAddressInformation();
	    	uai.setUserAddressRoleType(new UserAddressRoleType("OTH")); //TODO CONSTANT
	    	uai.setElectronicAddress(email);
	    	return uai;
	    }
	    
	    private UserAddressInformation retrievePhysicalAddress(JsonObject jsonObject) {
	    	
	    	//TODO constants
	    	String streetAddresss = jsonObject.getString("addressLine1");
	    	String city = jsonObject.getString("city");
	        UserAddressInformation uai = new UserAddressInformation();
	        PhysicalAddress pa = new PhysicalAddress();
	        StructuredAddress sa = new StructuredAddress();
	        sa.setLine1(streetAddresss);
	        sa.setLocality(city);
	    	pa.setStructuredAddress(sa);
	    	uai.setUserAddressRoleType(new UserAddressRoleType(Constants.CAMPUS)); 
	    	pa.setPhysicalAddressType(new PhysicalAddressType(null,"Postal Address")); //TODO
	    	uai.setPhysicalAddress(pa);
	    	return uai;
	    }
	    
		 private String retrieveAuthenticationInputTypeOf(String type,LookupUserInitiationData initData) {
		    	if (initData.getAuthenticationInputs() == null) return null;
		    	String authenticationID = null;
		    	for (AuthenticationInput authenticationInput : initData.getAuthenticationInputs()) {
		    		if (authenticationInput.getAuthenticationInputType().getValue().equalsIgnoreCase(type)) {
		    			authenticationID = authenticationInput.getAuthenticationInputData();
		    			break;
		    		}
		    	}
		    	if (authenticationID != null && authenticationID.equalsIgnoreCase("")) authenticationID = null;
		    	return authenticationID;
		 }
		 
		private UserId retrieveUserId(LookupUserInitiationData initData) {
		    	UserId uid = null;
		    	String uidString = null;
		    	if (initData.getUserId() != null) {
		    		uid = initData.getUserId();
		    	}
		    	else {
		    		//TRY Barcode Id
		    		uidString = this.retrieveAuthenticationInputTypeOf(Constants.AUTH_BARCODE,initData);
		    		//TRY User Id
		    		if (uidString == null) {
		    			uidString = this.retrieveAuthenticationInputTypeOf(Constants.AUTH_UID, initData);
		    		}
		    	}
		    	if (uidString != null) {
		    		uid = new UserId();
		    		uid.setUserIdentifierValue(uidString);
		    	}
		    	return uid;
		}

}
