package com.adhdpdf.study;

import com.adhdpdf.study.auth.TokenCodec;
import com.adhdpdf.study.auth.UserSession;
import com.adhdpdf.study.auth.UserSessionRepository;
import com.adhdpdf.study.llm.SummarizationFailureReason;
import com.adhdpdf.study.profile.AppUser;
import com.adhdpdf.study.profile.AppUserRepository;
import com.adhdpdf.study.profile.UserPreferenceRepository;
import com.adhdpdf.study.quiz.QuizCacheRepository;
import com.adhdpdf.study.session.StudySection;
import com.adhdpdf.study.session.StudySectionRepository;
import com.adhdpdf.study.session.StudySession;
import com.adhdpdf.study.session.StudySessionRepository;
import com.adhdpdf.study.session.StudySessionStore;
import com.adhdpdf.study.upload.StoredUploadResult;
import com.adhdpdf.study.upload.UploadedFile;
import com.adhdpdf.study.upload.UploadedFileRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:user_profiles_auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "study.auth.cookie-name=study_session",
        "study.auth.cookie-secure=false"
})
class UserProfilesAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private StudySectionRepository studySectionRepository;

    @Autowired
    private StudySessionStore studySessionStore;

    @Autowired
    private QuizCacheRepository quizCacheRepository;

    @MockBean
    private TokenCodec tokenCodec;

    @BeforeEach
    void setUp() {
        quizCacheRepository.deleteAll();
        studySectionRepository.deleteAll();
        studySessionRepository.deleteAll();
        uploadedFileRepository.deleteAll();
        userSessionRepository.deleteAll();
        userPreferenceRepository.deleteAll();
        appUserRepository.deleteAll();

        when(tokenCodec.newOpaqueToken()).thenReturn(
                "login-token",
                "session-token",
                "login-token-2",
                "session-token-2",
                "login-token-3",
                "session-token-3");
        when(tokenCodec.newLoginCode()).thenReturn("123456");
        when(tokenCodec.hash(ArgumentMatchers.anyString())).thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
    }

    @Test
    void postOpenSection_isMappedAndRequiresAuth() throws Exception {
        mockMvc.perform(post("/session/any-id/sections/1/open"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postOpenSection_unknownSessionReturnsNotFound() throws Exception {
        Instant now = Instant.now();
        AppUser user = appUserRepository.save(new AppUser("opensec@example.com", now));
        userSessionRepository.save(new UserSession(user, "hash-opensec-token", now.plusSeconds(3600), now));
        Cookie cookie = new Cookie("study_session", "opensec-token");

        mockMvc.perform(post("/session/nonexistent-session-id/sections/0/open").cookie(cookie))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void anonymousDeviceCreatesAndRestoresProfileWithSecret() throws Exception {
        String anonymousId = "anon_abcdefghijklmnopqrstuvwxyzABCDEFGH1234567890";
        String deviceSecret = "abcdefghijklmnopqrstuvwxyzABCDEFGH1234567890abc";
        String otherSecret = "abcdefghijklmnopqrstuvwxyzABCDEFGH1234567890xyz";

        String sessionCookie = mockMvc.perform(post("/auth/device")
                        .contentType("application/json")
                        .content("{\"anonymousId\":\"" + anonymousId + "\",\"deviceSecret\":\"" + deviceSecret
                                + "\",\"displayName\":\"Dina\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("study_session"))
                .andExpect(jsonPath("$.user.anonymousId").value(anonymousId))
                .andExpect(jsonPath("$.user.displayName").value("Dina"))
                .andReturn()
                .getResponse()
                .getCookie("study_session")
                .getValue();

        mockMvc.perform(get("/me").cookie(new Cookie("study_session", sessionCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Dina"))
                .andExpect(jsonPath("$.anonymousId").value(anonymousId));

        mockMvc.perform(post("/auth/device")
                        .contentType("application/json")
                        .content("{\"anonymousId\":\"" + anonymousId + "\",\"deviceSecret\":\"" + otherSecret + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    void sessionsAreOwnedAndResumePositionIsPersisted() throws Exception {
        Instant now = Instant.now();
        AppUser sarah = appUserRepository.save(new AppUser("sarah@example.com", now));
        AppUser rabab = appUserRepository.save(new AppUser("rabab@example.com", now));
        userSessionRepository.save(new UserSession(sarah, "hash-sarah-session", now.plusSeconds(3600), now));
        userSessionRepository.save(new UserSession(rabab, "hash-rabab-session", now.plusSeconds(3600), now));

        UploadedFile upload = uploadedFileRepository.save(new UploadedFile(
                sarah,
                new StoredUploadResult("stored.pdf", "focus.pdf", 12L, "uploads/stored.pdf", true, "text", false),
                now));
        StudySession session = StudySession.fromStudySections(List.of(
                new StudySection("Original 1", "Summary 1", SummarizationFailureReason.NONE),
                new StudySection("Original 2", "Summary 2", SummarizationFailureReason.NONE)));
        studySessionStore.save(sarah, upload, session);

        Cookie sarahCookie = new Cookie("study_session", "sarah-session");
        Cookie rababCookie = new Cookie("study_session", "rabab-session");

        mockMvc.perform(get("/session/" + session.getId() + "/current").cookie(sarahCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentIndex").value(0));

        mockMvc.perform(get("/session/" + session.getId() + "/current").cookie(rababCookie))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/session/" + session.getId() + "/next").cookie(sarahCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentIndex").value(1));

        assertThat(studySessionRepository.findByPublicIdAndUser_Id(session.getId(), sarah.getId()).orElseThrow()
                .getCurrentIndex()).isEqualTo(1);

        mockMvc.perform(get("/me").cookie(sarahCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeTarget.sessionId").value(session.getId()))
                .andExpect(jsonPath("$.resumeTarget.currentIndex").value(1));
    }

}
