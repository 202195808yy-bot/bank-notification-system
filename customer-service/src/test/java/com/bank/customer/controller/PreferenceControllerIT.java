package com.bank.customer.controller;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class PreferenceControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void updatePreferences_ShouldPersistAndReturn() throws Exception {
        String json = "[{\"eventType\":\"TRANSACTION\",\"channels\":\"[\\\"SMS\\\"]\",\"enabled\":true}]";
        mockMvc.perform(put("/api/preferences")
                        .header("X-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/preferences").header("X-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("TRANSACTION"));
    }
}