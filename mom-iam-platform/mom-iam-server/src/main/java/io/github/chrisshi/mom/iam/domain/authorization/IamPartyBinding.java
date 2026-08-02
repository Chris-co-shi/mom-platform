package io.github.chrisshi.mom.iam.domain.authorization;

import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;

import java.util.Objects;

/** 用户与外部 Party 的稳定引用关系。 */
public record IamPartyBinding(
        String id,
        PartyType partyType,
        String partyId,
        IamRecordStatus status,
        long version) {

    public IamPartyBinding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(status, "status");
    }

    public boolean enabled() {
        return status == IamRecordStatus.ENABLED;
    }
}
