package com.fooddelivery.authentication.api.controller;

import com.fooddelivery.commonweb.response.ApiResponse;
import com.fooddelivery.authentication.api.dto.response.SessionResponse;
import com.fooddelivery.authentication.application.command.GetSessionsQuery;
import com.fooddelivery.authentication.application.command.RevokeOthersCommand;
import com.fooddelivery.authentication.application.command.RevokeSessionCommand;
import com.fooddelivery.authentication.application.usecase.SessionUseCase;
import com.fooddelivery.authentication.config.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({ "/api/v1/sessions", "/sessions" })
public class SessionController {

    private final SessionUseCase sessionUseCase;

    public SessionController(SessionUseCase sessionUseCase) {
        this.sessionUseCase = sessionUseCase;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getSessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        GetSessionsQuery query = new GetSessionsQuery(principal.userId());
        List<SessionResponse> sessions = sessionUseCase.getSessions(query);
        return ResponseEntity.ok(ApiResponse.ok(sessions, "Sessions retrieved"));
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        RevokeSessionCommand command = new RevokeSessionCommand(sessionId, principal.userId());
        sessionUseCase.revokeSession(command);
        return ResponseEntity.ok(ApiResponse.ok(null, "Session revoked"));
    }

    @DeleteMapping("/others")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> revokeOthers(
            @AuthenticationPrincipal UserPrincipal principal) {
        RevokeOthersCommand command = new RevokeOthersCommand(principal.userId());
        sessionUseCase.revokeOthers(command);
        return ResponseEntity.ok(ApiResponse.ok(null, "Other sessions revoked"));
    }
}
