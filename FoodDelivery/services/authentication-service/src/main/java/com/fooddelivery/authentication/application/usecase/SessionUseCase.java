package com.fooddelivery.authentication.application.usecase;

import com.fooddelivery.authentication.api.dto.response.SessionResponse;
import com.fooddelivery.authentication.application.command.GetSessionsQuery;
import com.fooddelivery.authentication.application.command.RevokeOthersCommand;
import com.fooddelivery.authentication.application.command.RevokeSessionCommand;

import java.util.List;

public interface SessionUseCase {
    List<SessionResponse> getSessions(GetSessionsQuery query);

    void revokeSession(RevokeSessionCommand command);

    void revokeOthers(RevokeOthersCommand command);
}
