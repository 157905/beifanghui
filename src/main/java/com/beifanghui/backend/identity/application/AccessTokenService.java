package com.beifanghui.backend.identity.application;

import com.beifanghui.backend.identity.domain.AccessSession;
import com.beifanghui.backend.identity.domain.IdentityPrincipal;

public interface AccessTokenService {

    AccessSession issue(IdentityPrincipal principal);

    AccessSession find(String accessToken);

    void revoke(String accessToken);
}
