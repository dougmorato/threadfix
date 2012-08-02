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
package com.denimgroup.threadfix.data.dao;

import java.util.List;

import com.denimgroup.threadfix.data.entities.Scan;
import com.denimgroup.threadfix.data.entities.ScanCloseVulnerabilityMap;
import com.denimgroup.threadfix.data.entities.ScanReopenVulnerabilityMap;
import com.denimgroup.threadfix.data.entities.ScanRepeatFindingMap;

/**
 * Basic DAO class for the Scan entity.
 * 
 * @author mcollins
 */
public interface ScanDao {

	/**
	 * @return
	 */
	List<Scan> retrieveAll();

	/**
	 * 
	 * @param applicationId
	 * @return
	 */
	List<Scan> retrieveByApplicationIdList(List<Integer> applicationIdList);

	/**
	 * @param id
	 * @return
	 */
	Scan retrieveById(int id);

	/**
	 * @param scan
	 */
	void saveOrUpdate(Scan scan);

	/**
	 * 
	 * @param scan
	 */
	void delete(Scan scan);
	
	/**
	 * Delete a close map. These are saved by cascade but not always deleted that way.
	 * @param scan
	 */
	void deleteMap(ScanCloseVulnerabilityMap map);
	
	/**
	 * Delete a reopen map. These are saved by cascade but not always deleted that way.
	 * @param scan
	 */
	void deleteMap(ScanReopenVulnerabilityMap map);
	
	/**
	 * Delete a duplicate map. These are saved by cascade but not always deleted that way.
	 * @param scan
	 */
	void deleteMap(ScanRepeatFindingMap map);

	/**
	 * 
	 * @param scanId
	 * @return
	 */
	long getFindingCount(Integer scanId);
}
