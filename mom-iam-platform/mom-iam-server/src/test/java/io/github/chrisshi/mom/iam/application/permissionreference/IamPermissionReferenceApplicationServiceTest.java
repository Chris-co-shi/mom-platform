package io.github.chrisshi.mom.iam.application.permissionreference;

import io.github.chrisshi.mom.iam.api.IamPermissionReferenceContracts.PermissionReferenceStatus;
import io.github.chrisshi.mom.iam.application.permissionreference.port.IamPermissionReferenceQueryPort;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Permission Code 批量权威校验用例测试。 */
class IamPermissionReferenceApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void mustDeduplicatePreserveOrderAndMapAllStatuses() {
        IamPermissionReferenceQueryPort port = mock(IamPermissionReferenceQueryPort.class);
        when(port.findStatusesByCodes(anyCollection())).thenReturn(Map.of(
                "iam:user:read", IamRecordStatus.ENABLED,
                "iam:user:write", IamRecordStatus.DISABLED));
        var service = new IamPermissionReferenceApplicationService(
                port, Clock.fixed(NOW, ZoneOffset.UTC));

        var response = service.validate(List.of(
                "IAM:USER:READ", "iam:user:write", "iam:user:read", "wms:stock:read"));

        assertThat(response.checkedAt()).isEqualTo(NOW);
        assertThat(response.results()).extracting(result -> result.permissionCode())
                .containsExactly("iam:user:read", "iam:user:write", "wms:stock:read");
        assertThat(response.results()).extracting(result -> result.status())
                .containsExactly(PermissionReferenceStatus.ENABLED,
                        PermissionReferenceStatus.DISABLED,
                        PermissionReferenceStatus.UNKNOWN);
        verify(port).findStatusesByCodes(anyCollection());
    }

    @Test
    void emptyInputMustNotQueryDatabaseAndOversizedInputMustFail() {
        IamPermissionReferenceQueryPort port = mock(IamPermissionReferenceQueryPort.class);
        var service = new IamPermissionReferenceApplicationService(
                port, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(service.validate(List.of()).results()).isEmpty();
        verify(port, never()).findStatusesByCodes(anyCollection());

        List<String> oversized = new ArrayList<>();
        for (int index = 0; index <= IamPermissionReferenceApplicationService.MAX_CODES; index++) {
            oversized.add("d:r" + index + ":read");
        }
        assertThatThrownBy(() -> service.validate(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多 1000");
    }
}
