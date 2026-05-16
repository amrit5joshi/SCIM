package com.amrit.scim.controller;

import com.amrit.scim.dto.ScimEmail;
import com.amrit.scim.dto.ScimName;
import com.amrit.scim.dto.ScimUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link UserController}.
 * <p>
 * We use {@code @SpringBootTest} (full application context) + {@code @AutoConfigureMockMvc}
 * so the entire Spring Security filter chain is active — this gives us realistic
 * auth testing without spinning up a real HTTP server. The H2 in-memory DB
 * (configured in {@code application-test.properties}) is used instead of MySQL.
 * <p>
 * Each test is wrapped in {@code @Transactional} so the DB is rolled back
 * after every test method — no test can pollute the next one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerTest {

    private static final String BASE_URL   = "/scim/v2/Users";
    private static final String VALID_TOKEN = "Bearer test-token-12345";
    private static final String BAD_TOKEN   = "Bearer wrong-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Helper: build a valid ScimUser request body
    // -------------------------------------------------------------------------

    private ScimUser buildUser(String userName) {
        return ScimUser.builder()
                .userName(userName)
                .externalId("ext-001")
                .name(ScimName.builder()
                        .givenName("Alice")
                        .familyName("Smith")
                        .formatted("Alice Smith")
                        .build())
                .emails(List.of(ScimEmail.builder()
                        .value("alice@example.com")
                        .type("work")
                        .primary(true)
                        .build()))
                .active(true)
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /scim/v2/Users
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /Users — happy path returns 201 with id and schemas")
    void createUser_happyPath() throws Exception {
        String body = objectMapper.writeValueAsString(buildUser("alice@example.com"));

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userName").value("alice@example.com"))
                .andExpect(jsonPath("$.schemas[0]")
                        .value("urn:ietf:params:scim:schemas:core:2.0:User"))
                .andExpect(jsonPath("$.meta.resourceType").value("User"))
                .andExpect(jsonPath("$.meta.location").value(containsString("/scim/v2/Users/")));
    }

    @Test
    @DisplayName("POST /Users — 401 when Authorization header is missing")
    void createUser_noToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(buildUser("alice@example.com"));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.schemas[0]")
                        .value("urn:ietf:params:scim:api:messages:2.0:Error"))
                .andExpect(jsonPath("$.status").value("401"));
    }

    @Test
    @DisplayName("POST /Users — 401 when bearer token is wrong")
    void createUser_badToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(buildUser("alice@example.com"));

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", BAD_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("401"));
    }

    @Test
    @DisplayName("POST /Users — 409 when userName already exists")
    void createUser_duplicateUserName_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(buildUser("duplicate@example.com"));

        // Create once — should succeed
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Create again with same userName — should fail
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.schemas[0]")
                        .value("urn:ietf:params:scim:api:messages:2.0:Error"))
                .andExpect(jsonPath("$.status").value("409"))
                .andExpect(jsonPath("$.scimType").value("uniqueness"));
    }

    @Test
    @DisplayName("POST /Users — 400 when multiple emails have primary=true")
    void createUser_multiplePrimaryEmails_returns400() throws Exception {
        ScimUser user = buildUser("multi-primary@example.com");
        user.setEmails(List.of(
                ScimEmail.builder().value("a@example.com").type("work").primary(true).build(),
                ScimEmail.builder().value("b@example.com").type("home").primary(true).build()
        ));
        String body = objectMapper.writeValueAsString(user);

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.scimType").value("invalidValue"))
                .andExpect(jsonPath("$.detail").value(containsString("primary=true")));
    }

    @Test
    @DisplayName("POST /Users — 400 when userName is blank")
    void createUser_missingUserName_returns400() throws Exception {
        ScimUser badUser = ScimUser.builder().userName("").build();
        String body = objectMapper.writeValueAsString(badUser);

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.scimType").value("invalidValue"));
    }

    // -------------------------------------------------------------------------
    // GET /scim/v2/Users/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /Users/{id} — happy path returns 200 with correct user")
    void getUser_happyPath() throws Exception {
        // First create a user to get its id
        String body = objectMapper.writeValueAsString(buildUser("getme@example.com"));
        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String responseJson = createResult.getResponse().getContentAsString();
        String id = objectMapper.readTree(responseJson).get("id").asText();

        // Now fetch by id
        mockMvc.perform(get(BASE_URL + "/" + id)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.userName").value("getme@example.com"));
    }

    @Test
    @DisplayName("GET /Users/{id} — 404 when user does not exist")
    void getUser_notFound_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/non-existent-uuid")
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("404"))
                .andExpect(jsonPath("$.schemas[0]")
                        .value("urn:ietf:params:scim:api:messages:2.0:Error"));
    }

    // -------------------------------------------------------------------------
    // GET /scim/v2/Users  (list + filter)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /Users — happy path returns ListResponse envelope")
    void listUsers_happyPath() throws Exception {
        // Create one user so the list is not empty
        String body = objectMapper.writeValueAsString(buildUser("list@example.com"));
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemas[0]")
                        .value("urn:ietf:params:scim:api:messages:2.0:ListResponse"))
                .andExpect(jsonPath("$.totalResults").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.Resources").isArray());
    }

    @Test
    @DisplayName("GET /Users?filter=userName eq — returns matching user")
    void listUsers_withFilter_returnsMatchingUser() throws Exception {
        String body = objectMapper.writeValueAsString(buildUser("filter-me@example.com"));
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .param("filter", "userName eq \"filter-me@example.com\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.Resources[0].userName").value("filter-me@example.com"));
    }

    @Test
    @DisplayName("GET /Users?filter=<unsupported> — returns 400 invalidFilter")
    void listUsers_unsupportedFilter_returns400() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .param("filter", "emails.value eq \"x@x.com\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.scimType").value("invalidFilter"));
    }

    // -------------------------------------------------------------------------
    // PUT /scim/v2/Users/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PUT /Users/{id} — happy path updates and returns 200")
    void replaceUser_happyPath() throws Exception {
        // Create
        String createBody = objectMapper.writeValueAsString(buildUser("put-me@example.com"));
        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        // Replace with updated givenName
        ScimUser updated = buildUser("put-me@example.com");
        updated.setName(ScimName.builder()
                .givenName("UpdatedName")
                .familyName("Smith")
                .formatted("UpdatedName Smith")
                .build());
        String updateBody = objectMapper.writeValueAsString(updated);

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name.givenName").value("UpdatedName"));
    }

    // -------------------------------------------------------------------------
    // DELETE /scim/v2/Users/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /Users/{id} — happy path returns 204")
    void deleteUser_happyPath() throws Exception {
        // Create
        String body = objectMapper.writeValueAsString(buildUser("delete-me@example.com"));
        MvcResult createResult = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        // Delete
        mockMvc.perform(delete(BASE_URL + "/" + id)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isNoContent());

        // Confirm gone
        mockMvc.perform(get(BASE_URL + "/" + id)
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /Users/{id} — 404 when user does not exist")
    void deleteUser_notFound_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/does-not-exist")
                        .header("Authorization", VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("404"));
    }
}
