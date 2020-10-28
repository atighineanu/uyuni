/**
 * Copyright (c) 2019 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.suse.manager.webui.controllers.contentmanagement.handlers;

import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.manager.contentmgmt.ContentManager;

import com.suse.manager.webui.controllers.contentmanagement.request.NewProjectRequest;
import com.suse.manager.webui.controllers.contentmanagement.request.ProjectPropertiesRequest;
import com.suse.utils.Json;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

import spark.Request;
import spark.Spark;

/**
 * Utility class to help the handling of the ProjectApiController
 */
public class ProjectHandler {
    private static final Gson GSON = Json.GSON;

    private ProjectHandler() { }

    /**
     * map request into the project properties  request bean
     * @param req the http request
     * @return project properties request bean
     */
    public static ProjectPropertiesRequest getProjectPropertiesRequest(Request req) {
        try {
            return GSON.fromJson(req.body(), ProjectPropertiesRequest.class);
        }
        catch (JsonParseException e) {
            throw Spark.halt(HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * map  request into the project request bean
     * @param req the http request
     * @return project request bean
     */
    public static NewProjectRequest getProjectRequest(Request req) {
        try {
            return GSON.fromJson(req.body(), NewProjectRequest.class);
        }
        catch (JsonParseException e) {
            throw Spark.halt(HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * map validate project properties request bean
     * @param projPropsRequest the project properties request bean
     * @param user the user
     * @return validation errors
     */
    public static List<String> validateProjectPropertiesRequest(
            ProjectPropertiesRequest projPropsRequest, User user
    ) {
        List<String> requestErrors = new ArrayList<>();

        String label = projPropsRequest.getLabel();
        String name = projPropsRequest.getName();
        var errors = new ArrayList<String>();
        if (StringUtils.isEmpty(label)) {
            errors.add("Label is required");
        }

        if (!ValidationUtils.isLabelValid(label)) {
            errors.add(
                    "Label must begin with a letter and must contain only lowercase letters, hyphens ('-')," +
                            " periods ('.'), underscores ('_'), and numerals."
            );
        }

        if (label.length() > 24) {
            errors.add("Label must not exceed 24 characters");
        }

        if (StringUtils.isEmpty(name)) {
            errors.add("Name is required");
        }

        if (name.length() > 128) {
            errors.add("Name must not exceed 128 characters");
        }
        requestErrors.addAll(errors);

        ContentManager.lookupProjectByNameAndOrg(projPropsRequest.getName(), user).ifPresent(cp -> {
            if (!cp.getLabel().equals(projPropsRequest.getLabel())) {
                requestErrors.add("Name already exists");
            }
        });


        return requestErrors;
    }

}
