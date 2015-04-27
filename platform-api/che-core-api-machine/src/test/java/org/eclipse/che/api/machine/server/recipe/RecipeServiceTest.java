/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.machine.server.recipe;

import com.google.common.collect.FluentIterable;
import com.jayway.restassured.response.Response;

import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.ApiExceptionMapper;
import org.eclipse.che.api.core.rest.shared.dto.ServiceError;
import org.eclipse.che.api.machine.server.PermissionsImpl;
import org.eclipse.che.api.machine.server.RecipeImpl;
import org.eclipse.che.api.machine.server.dao.RecipeDao;
import org.eclipse.che.api.machine.shared.Recipe;
import org.eclipse.che.api.machine.shared.dto.GroupDescriptor;
import org.eclipse.che.api.machine.shared.dto.NewRecipe;
import org.eclipse.che.api.machine.shared.dto.PermissionsDescriptor;
import org.eclipse.che.api.machine.shared.dto.RecipeDescriptor;
import org.eclipse.che.api.machine.shared.dto.RecipeUpdate;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.user.UserImpl;
import org.eclipse.che.dto.server.DtoFactory;
import org.everrest.assured.EverrestJetty;
import org.everrest.core.Filter;
import org.everrest.core.GenericContainerRequest;
import org.everrest.core.RequestFilter;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_NAME;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_PASSWORD;
import static org.everrest.assured.JettyHttpServer.SECURE_PATH;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * @author Eugene Voevodin
 */
@Listeners(value = {EverrestJetty.class, MockitoTestNGListener.class})
public class RecipeServiceTest {

    static final EnvironmentFilter  FILTER  = new EnvironmentFilter();
    static final ApiExceptionMapper MAPPER  = new ApiExceptionMapper();
    static final String             USER_ID = "user123";
    static final LinkedList<String> ROLES   = new LinkedList<>(asList("user"));

    @Mock
    RecipeDao          recipeDao;
    @Mock
    PermissionsChecker permissionsChecker;
    @InjectMocks
    RecipeService      service;

    @Filter
    public static class EnvironmentFilter implements RequestFilter {

        public void doFilter(GenericContainerRequest request) {
            EnvironmentContext.getCurrent().setUser(new UserImpl("user", USER_ID, "token", ROLES, false));
        }
    }

    @Test
    public void shouldThrowForbiddenExceptionOnCreateRecipeWithNullBody() {
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .when()
                                         .post(SECURE_PATH + "/recipe");

        assertEquals(response.getStatusCode(), 403);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Recipe required");
    }

    @Test
    public void shouldThrowForbiddenExceptionOnCreateRecipeWithNewRecipeWhichDoesNotHaveType() {
        final NewRecipe newRecipe = newDto(NewRecipe.class).withScript("FROM ubuntu\n");

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newRecipe)
                                         .when()
                                         .post(SECURE_PATH + "/recipe");

        assertEquals(response.getStatusCode(), 403);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Recipe type required");
    }

    @Test
    public void shouldThrowForbiddenExceptionOnCreateRecipeWithNewRecipeWhichDoesNotHaveScript() {
        final NewRecipe newRecipe = newDto(NewRecipe.class).withType("docker");

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newRecipe)
                                         .when()
                                         .post(SECURE_PATH + "/recipe");

        assertEquals(response.getStatusCode(), 403);
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), "Recipe script required");
    }

    @Test
    public void shouldCreateNewRecipe() {
        final GroupDescriptor group = newDto(GroupDescriptor.class).withName("public").withAcl(asList("read"));
        final NewRecipe newRecipe = newDto(NewRecipe.class).withType("docker")
                                                           .withScript("FROM ubuntu\n")
                                                           .withTags(asList("java", "mongo"))
                                                           .withPermissions(newDto(PermissionsDescriptor.class).withGroups(asList(group)));

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newRecipe)
                                         .when()
                                         .post(SECURE_PATH + "/recipe");

        assertEquals(response.getStatusCode(), 201);
        verify(recipeDao).create(any(Recipe.class));
        final RecipeDescriptor descriptor = unwrapDto(response, RecipeDescriptor.class);
        assertNotNull(descriptor.getId());
        assertEquals(descriptor.getCreator(), USER_ID);
        assertEquals(descriptor.getScript(), newRecipe.getScript());
        assertEquals(descriptor.getTags(), newRecipe.getTags());
        assertEquals(descriptor.getPermissions(), newRecipe.getPermissions());
    }

    @Test
    public void shouldNotBeAbleToCreateNewRecipeWithPublicSearchPermissionForUser() {
        final GroupDescriptor group = newDto(GroupDescriptor.class).withName("public").withAcl(asList("read", "search"));
        final NewRecipe newRecipe = newDto(NewRecipe.class).withType("docker")
                                                           .withScript("FROM ubuntu\n")
                                                           .withPermissions(newDto(PermissionsDescriptor.class).withGroups(asList(group)));

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newRecipe)
                                         .when()
                                         .post(SECURE_PATH + "/recipe");

        assertEquals(response.getStatusCode(), 403);
        final ServiceError error = unwrapDto(response, ServiceError.class);
        assertEquals(error.getMessage(), "User " + USER_ID + " doesn't have access to use 'public: search' permission");
    }

    @Test
    public void shouldBeAbleToCreateNewRecipeWithPublicSearchPermissionForSystemAdmin() {
        ROLES.add("system/admin");

        final GroupDescriptor group = newDto(GroupDescriptor.class).withName("public").withAcl(asList("read", "search"));
        final NewRecipe newRecipe = newDto(NewRecipe.class).withType("docker")
                                                           .withScript("FROM ubuntu\n")
                                                           .withPermissions(newDto(PermissionsDescriptor.class).withGroups(asList(group)));

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(newRecipe)
                                         .when()
                                         .post(SECURE_PATH + "/recipe");

        assertEquals(response.getStatusCode(), 201);
        final RecipeDescriptor descriptor = unwrapDto(response, RecipeDescriptor.class);
        assertEquals(descriptor.getPermissions(), newRecipe.getPermissions());

        ROLES.remove("system/admin");
    }

    @Test
    public void shouldBeAbleToGetRecipeScript() throws ServerException {
        final Map<String, List<String>> users = Collections.singletonMap(USER_ID, asList("read", "write"));
        final Recipe recipe = new RecipeImpl().withCreator("other-user")
                                              .withId("recipe123")
                                              .withScript("FROM ubuntu\n")
                                              .withPermissions(new PermissionsImpl(users, null));
        when(recipeDao.getById(recipe.getId())).thenReturn(recipe);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "read")).thenReturn(true);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .when()
                                         .get(SECURE_PATH + "/recipe/" + recipe.getId());

        assertEquals(response.getStatusCode(), 200);
        assertEquals(response.getBody().print(), recipe.getScript());
    }

    @Test
    public void shouldThrowForbiddenExceptionWhenUserDoesNotHaveReadAccessToRecipeScript() throws ServerException {
        final Recipe recipe = new RecipeImpl().withCreator("someone2")
                                              .withId("recipe123")
                                              .withScript("FROM ubuntu\n");
        when(recipeDao.getById(recipe.getId())).thenReturn(recipe);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "read")).thenReturn(false);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .when()
                                         .get(SECURE_PATH + "/recipe/" + recipe.getId());

        assertEquals(response.getStatusCode(), 403);
        final String expMessage = format("User %s doesn't have access to recipe %s", USER_ID, recipe.getId());
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expMessage);
    }

    @Test
    public void shouldBeAbleToGetRecipe() throws ServerException {
        final Map<String, List<String>> users = Collections.singletonMap(USER_ID, asList("read", "write"));
        final Recipe recipe = new RecipeImpl().withCreator("someone2")
                                              .withId("recipe123")
                                              .withType("docker")
                                              .withScript("FROM ubuntu\n")
                                              .withTags(asList("java", "mognodb"))
                                              .withPermissions(new PermissionsImpl(users, null));
        when(recipeDao.getById(recipe.getId())).thenReturn(recipe);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "read")).thenReturn(true);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .when()
                                         .get(SECURE_PATH + "/recipe/" + recipe.getId() + "/json");

        assertEquals(response.getStatusCode(), 200);
        final RecipeDescriptor descriptor = unwrapDto(response, RecipeDescriptor.class);
        assertEquals(descriptor.getId(), recipe.getId());
        assertEquals(descriptor.getType(), recipe.getType());
        assertEquals(descriptor.getScript(), recipe.getScript());
        assertEquals(descriptor.getTags(), recipe.getTags());
        assertEquals(descriptor.getCreator(), recipe.getCreator());
        assertEquals(PermissionsImpl.fromDescriptor(descriptor.getPermissions()), recipe.getPermissions());
    }

    @Test
    public void shouldThrowForbiddenExceptionWhenUserDoesNotHaveReadAccessToRecipe() throws ServerException {
        final Recipe recipe = new RecipeImpl().withCreator("someone2")
                                              .withId("recipe123")
                                              .withScript("FROM ubuntu\n");
        when(recipeDao.getById(recipe.getId())).thenReturn(recipe);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "read")).thenReturn(false);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .when()
                                         .get(SECURE_PATH + "/recipe/" + recipe.getId() + "/json");

        assertEquals(response.getStatusCode(), 403);
        final String expMessage = format("User %s doesn't have access to recipe %s", USER_ID, recipe.getId());
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expMessage);
    }

    @Test
    public void shouldBeAbleToGetCreatedRecipes() {
        final Recipe recipe1 = new RecipeImpl().withId("id1")
                                               .withCreator(USER_ID)
                                               .withType("docker")
                                               .withScript("script1 content");
        final Recipe recipe2 = new RecipeImpl().withId("id2")
                                               .withCreator(USER_ID)
                                               .withType("docker")
                                               .withScript("script2 content");
        when(recipeDao.getByCreator(USER_ID)).thenReturn(asList(recipe1, recipe2));

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .when()
                                         .get(SECURE_PATH + "/recipe");

        assertEquals(response.getStatusCode(), 200);
        assertEquals(unwrapDtoList(response, RecipeDescriptor.class).size(), 2);
    }

    @Test
    public void shouldBeAbleToSearchRecipes() {
        final Recipe recipe1 = new RecipeImpl().withId("id1")
                                               .withCreator(USER_ID)
                                               .withType("docker")
                                               .withScript("script1 content")
                                               .withTags(asList("java"));
        final Recipe recipe2 = new RecipeImpl().withId("id2")
                                               .withCreator(USER_ID)
                                               .withType("docker")
                                               .withScript("script2 content")
                                               .withTags(asList("java", "mongodb"));
        when(recipeDao.search(asList("java", "mongodb"), "docker")).thenReturn(asList(recipe1, recipe2));

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .when()
                                         .queryParameter("tags", asList("java", "mongodb"))
                                         .queryParameter("type", "docker")
                                         .get(SECURE_PATH + "/recipe/list");

        assertEquals(response.getStatusCode(), 200);
        assertEquals(unwrapDtoList(response, RecipeDescriptor.class).size(), 2);
    }

    @Test
    public void shouldBeAbleToRemoveRecipe() throws ServerException {
        final Recipe recipe = new RecipeImpl().withId("id")
                                              .withCreator(USER_ID)
                                              .withType("docker")
                                              .withScript("script1 content")
                                              .withTags(asList("java"));
        when(recipeDao.getById(recipe.getId())).thenReturn(recipe);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "write")).thenReturn(true);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .when()
                                         .delete(SECURE_PATH + "/recipe/" + recipe.getId());

        assertEquals(response.getStatusCode(), 204);
        verify(recipeDao).remove(recipe.getId());
    }

    @Test
    public void shouldNotBeAbleToRemoveRecipeIfUserDoesNotHaveWritePermission() throws ServerException {
        final Recipe recipe = new RecipeImpl().withId("id")
                                              .withCreator(USER_ID)
                                              .withType("docker")
                                              .withScript("script1 content")
                                              .withTags(asList("java"));
        when(recipeDao.getById(recipe.getId())).thenReturn(recipe);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "write")).thenReturn(false);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .when()
                                         .delete(SECURE_PATH + "/recipe/" + recipe.getId());

        assertEquals(response.getStatusCode(), 403);
        final String expMessage = format("User %s doesn't have access to recipe %s", USER_ID, recipe.getId());
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expMessage);
    }

    @Test
    public void shouldBeAbleToUpdateRecipe() throws ServerException {
        final Recipe recipe = new RecipeImpl().withId("id")
                                              .withCreator(USER_ID)
                                              .withType("docker")
                                              .withScript("script1 content")
                                              .withTags(asList("java"));
        when(recipeDao.getById(recipe.getId())).thenReturn(recipe);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "write")).thenReturn(true);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "update_acl")).thenReturn(true);

        //prepare update
        GroupDescriptor group = newDto(GroupDescriptor.class).withName("public").withAcl(asList("read"));
        RecipeUpdate update = newDto(RecipeUpdate.class).withType("new-type")
                                                        .withScript("new script content")
                                                        .withTags(asList("java", "mongodb"))
                                                        .withPermissions(newDto(PermissionsDescriptor.class).withGroups(asList(group)));

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(update)
                                         .when()
                                         .put(SECURE_PATH + "/recipe/" + recipe.getId());

        assertEquals(response.getStatusCode(), 200);
        final RecipeDescriptor descriptor = unwrapDto(response, RecipeDescriptor.class);
        assertEquals(descriptor.getType(), update.getType());
        assertEquals(descriptor.getScript(), update.getScript());
        assertEquals(descriptor.getTags(), update.getTags());
        assertEquals(descriptor.getPermissions(), update.getPermissions());
    }

    @Test
    public void shouldThrowForbiddenExceptionWhenUserDoesNotHaveAccessToUpdateRecipe() throws ServerException {
        final Recipe recipe = new RecipeImpl().withId("id")
                                              .withCreator(USER_ID)
                                              .withType("docker")
                                              .withScript("script1 content")
                                              .withTags(asList("java"));
        when(recipeDao.getById(recipe.getId())).thenReturn(recipe);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "write")).thenReturn(false);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .when()
                                         .put(SECURE_PATH + "/recipe/" + recipe.getId());

        assertEquals(response.getStatusCode(), 403);
        final String expMessage = format("User %s doesn't have access to update recipe %s", USER_ID, recipe.getId());
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expMessage);
    }

    @Test
    public void shouldThrowForbiddenExceptionWhenUserDoesNotHaveAccessToUpdateRecipePermissions() throws ServerException {
        final Recipe recipe = new RecipeImpl().withId("id")
                                              .withCreator(USER_ID)
                                              .withType("docker")
                                              .withScript("script1 content")
                                              .withTags(asList("java"));
        when(recipeDao.getById(recipe.getId())).thenReturn(recipe);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "write")).thenReturn(true);
        when(permissionsChecker.hasAccess(recipe, USER_ID, "update_acl")).thenReturn(false);

        //prepare update
        GroupDescriptor group = newDto(GroupDescriptor.class).withName("public").withAcl(asList("read"));
        RecipeUpdate update = newDto(RecipeUpdate.class).withType("new-type")
                                                        .withScript("new script content")
                                                        .withTags(asList("java", "mongodb"))
                                                        .withPermissions(newDto(PermissionsDescriptor.class).withGroups(asList(group)));

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType("application/json")
                                         .body(update)
                                         .when()
                                         .put(SECURE_PATH + "/recipe/" + recipe.getId());

        assertEquals(response.getStatusCode(), 403);
        final String expMessage = format("User %s doesn't have access to update recipe %s permissions", USER_ID, recipe.getId());
        assertEquals(unwrapDto(response, ServiceError.class).getMessage(), expMessage);
    }

    private static <T> T newDto(Class<T> clazz) {
        return DtoFactory.getInstance().createDto(clazz);
    }

    private static <T> T unwrapDto(Response response, Class<T> dtoClass) {
        return DtoFactory.getInstance().createDtoFromJson(response.getBody().print(), dtoClass);
    }

    private static <T> List<T> unwrapDtoList(Response response, Class<T> dtoClass) {
        return FluentIterable.from(DtoFactory.getInstance().createListDtoFromJson(response.body().print(), dtoClass)).toList();
    }
}
