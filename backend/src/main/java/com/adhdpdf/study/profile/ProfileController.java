package com.adhdpdf.study.profile;

import com.adhdpdf.study.auth.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProfileController {

    private final CurrentUserService currentUserService;
    private final ProfileService profileService;

    public ProfileController(CurrentUserService currentUserService, ProfileService profileService) {
        this.currentUserService = currentUserService;
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public UserProfileResponse me() {
        return profileService.profile(currentUserService.requireUser());
    }

    @PatchMapping("/me/profile")
    public UserProfileResponse updateProfile(@RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(currentUserService.requireUser(), request);
    }

    @PatchMapping("/me/preferences")
    public UserProfileResponse updatePreferences(@RequestBody UpdatePreferencesRequest request) {
        return profileService.updatePreferences(currentUserService.requireUser(), request);
    }

    @GetMapping("/me/sessions")
    public List<UserStudySessionSummary> sessions() {
        return profileService.recentSessions(currentUserService.requireUser());
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        boolean removed = profileService.deleteStudySession(currentUserService.requireUser(), sessionId);
        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
