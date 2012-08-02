////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2011 Denim Group, Ltd.
//
//     The contents of this file are subject to the Mozilla Public License
//     Version 1.1 (the "License"); you may not use this file except in
//     compliance with the License. You may obtain a copy of the License at
//     http://www.mozilla.org/MPL/
//
//     Software distributed under the License is distributed on an "AS IS"
//     basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//     License for the specific language governing rights and limitations
//     under the License.
//
//     The Original Code is Vulnerability Manager.
//
//     The Initial Developer of the Original Code is Denim Group, Ltd.
//     Portions created by Denim Group, Ltd. are Copyright (C)
//     Denim Group, Ltd. All Rights Reserved.
//
//     Contributor(s): Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.service;

import java.util.List;

import org.springframework.validation.BindingResult;

import com.denimgroup.threadfix.data.entities.Application;
import com.denimgroup.threadfix.data.entities.Vulnerability;
import com.denimgroup.threadfix.webapp.controller.TableSortBean;

/**
 * @author bbeverly
 * 
 */
public interface ApplicationService {

	/**
	 * @return
	 */
	List<Application> loadAll();

	/**
	 * @return
	 */
	List<Application> loadAllActive();

	/**
	 * @param applicationId
	 * @return
	 */
	Application loadApplication(int applicationId);

	/**
	 * @param applicationName
	 * @return
	 */
	Application loadApplication(String applicationName);

	/**
	 * @param application
	 */
	void storeApplication(Application application);

	/**
	 * @param applicationId
	 */
	void deleteById(int applicationId);

	/**
	 * Prepare the application for deletion.
	 * 
	 * @param application
	 */
	void deactivateApplication(Application application);

	/**
	 * 
	 * @param application
	 * @param result
	 * @return true if the defect tracker has changed, false otherwise
	 */
	boolean validateApplicationDefectTracker(Application application,
			BindingResult result);

	/**
	 * This method is used to validate incoming REST application parameters.
	 * @param application
	 * @return
	 */
	boolean checkApplication(Application application);
	
	/**
	 * Removes WAF rules from an Application if the WAF has changed or been removed.
	 * Don't save the Application after using this method as it does not handle removing
	 * WAF rules from the application because the application may be out of session,
	 * causing it to throw an error when you try to access the WAF rules at all.
	 * @param application
	 */
	void updateWafRules(Application application, Integer dbApplicationWafId);
	
	/**
	 * Performs necessary checks and keeps the controller layer clean.
	 * @param application
	 * @param result
	 */
	void validateAfterEdit(Application application, BindingResult result);
	
	/**
	 * Performs necessary checks and keeps the controller layer clean.
	 * @param application
	 * @param result
	 */
	void validateAfterCreate(Application application, BindingResult result);
	
	/**
	 * If the project root has been updated, this method updates the associated vulns.
	 * @param application
	 */
	void updateProjectRoot(Application application);

	/**
	 * 
	 * @param appId
	 * @return
	 */
	List<Vulnerability> getVulnTable(int appId, TableSortBean bean);

	/**
	 * 
	 * @param appId
	 * @param bean
	 * @return
	 */
	long getCount(Integer appId, TableSortBean bean);

}
