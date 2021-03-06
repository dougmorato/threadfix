////////////////////////////////////////////////////////////////////////
//
//     Copyright (c) 2009-2015 Denim Group, Ltd.
//
//     The contents of this file are subject to the Mozilla Public License
//     Version 2.0 (the "License"); you may not use this file except in
//     compliance with the License. You may obtain a copy of the License at
//     http://www.mozilla.org/MPL/
//
//     Software distributed under the License is distributed on an "AS IS"
//     basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//     License for the specific language governing rights and limitations
//     under the License.
//
//     The Original Code is ThreadFix.
//
//     The Initial Developer of the Original Code is Denim Group, Ltd.
//     Portions created by Denim Group, Ltd. are Copyright (C)
//     Denim Group, Ltd. All Rights Reserved.
//
//     Contributor(s): Denim Group, Ltd.
//
////////////////////////////////////////////////////////////////////////
package com.denimgroup.threadfix.plugins.intellij.rest;

import com.denimgroup.threadfix.data.entities.Application;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ApplicationsMap {

    private final Map<String,Map<String, String>> map =
            new HashMap<String,Map<String, String>>();

    private void addTeam(String team) {
        if (!map.containsKey(team)) {
            map.put(team, new HashMap<String, String>());
        }
    }

    void addApp(Application.Info applicationInfo) {
        addTeam(applicationInfo.organizationName);
        map.get(applicationInfo.organizationName).put(
                applicationInfo.applicationName,
                applicationInfo.applicationId);
    }

    public Set<String> getTeams() {
        return new TreeSet<String>(map.keySet());
    }

    public Set<String> getApps(String team) {
        return new TreeSet<String>(map.get(team).keySet());
    }

    public String getId(String team, String app) {
        return map.get(team).get(app);
    }
}