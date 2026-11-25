package com.instakill.funnyfy.api.rest;

import com.instakill.common.error.LlmServiceException;
import com.instakill.funnyfy.application.FunnyfyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FunnyfyRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FunnyfyService funnyfyService;

    @Test
    void funnyfyReturnsRewrittenCaption() throws Exception {
        when(funnyfyService.funnyfy("Just had my morning coffee."))
                .thenReturn("My bloodstream is now 90% espresso and 10% pure panic. ☕");

        mockMvc.perform(post("/api/funnyfy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Just had my morning coffee.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("My bloodstream is now 90% espresso and 10% pure panic. ☕"));
    }

    @Test
    void funnyfyAcceptsContentFieldAlias() throws Exception {
        when(funnyfyService.funnyfy("Debugging on a Friday."))
                .thenReturn("Playing hide and seek with a missing semicolon since 9 AM.");

        mockMvc.perform(post("/api/funnyfy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Debugging on a Friday.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Playing hide and seek with a missing semicolon since 9 AM."));
    }

    @Test
    void funnyfyRejectsBlankInputWith422() throws Exception {
        mockMvc.perform(post("/api/funnyfy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation Error"));
    }

    @Test
    void funnyfyReturns503WhenLlmServerIsDown() throws Exception {
        when(funnyfyService.funnyfy("Test caption"))
                .thenThrow(LlmServiceException.unavailable(
                        "Local LLM server is unreachable. Please ensure LM Studio is running on the configured port.",
                        new ConnectException("Connection refused")));

        mockMvc.perform(post("/api/funnyfy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Test caption\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(
                        "Local LLM server is unreachable. Please ensure LM Studio is running on the configured port."));
    }

    @Test
    void funnyfyReturns504WhenLlmTimesOut() throws Exception {
        when(funnyfyService.funnyfy("Slow caption"))
                .thenThrow(LlmServiceException.timeout(
                        "Local LLM request timed out. Please try again.",
                        new HttpTimeoutException("timed out")));

        mockMvc.perform(post("/api/funnyfy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Slow caption\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.detail").value("Local LLM request timed out. Please try again."));
    }
}
