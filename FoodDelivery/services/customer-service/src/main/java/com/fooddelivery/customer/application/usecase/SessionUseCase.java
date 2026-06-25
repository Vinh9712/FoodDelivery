package com.fooddelivery.customer.application.usecase;

import com.fooddelivery.customer.api.dto.response.SessionResponse;
import com.fooddelivery.customer.application.command.GetSessionsQuery;
import com.fooddelivery.customer.application.command.RevokeOthersCommand;
import com.fooddelivery.customer.application.command.RevokeSessionCommand;

import java.util.List;

public interface SessionUseCase {
    List<SessionResponse> getSessions(GetSessionsQuery query);

    void revokeSession(RevokeSessionCommand command);

    void revokeOthers(RevokeOthersCommand command);
}
