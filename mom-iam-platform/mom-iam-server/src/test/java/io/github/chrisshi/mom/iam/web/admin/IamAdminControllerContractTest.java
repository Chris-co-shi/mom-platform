package io.github.chrisshi.mom.iam.web.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminCommands;
import io.github.chrisshi.mom.iam.web.admin.audit.IamSecurityAuditController;
import io.github.chrisshi.mom.iam.web.admin.client.IamClientAdminController;
import io.github.chrisshi.mom.iam.web.admin.role.IamRoleAdminController;
import io.github.chrisshi.mom.iam.web.admin.session.IamSessionAdminController;
import io.github.chrisshi.mom.iam.web.admin.user.IamUserAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** IAM Admin 已发布路由、分页默认值和 JSON 命令字段契约测试。 */
class IamAdminControllerContractTest {
    private static final List<Class<?>> CONTROLLERS = List.of(
            IamUserAdminController.class,
            IamRoleAdminController.class,
            IamSessionAdminController.class,
            IamClientAdminController.class,
            IamSecurityAuditController.class);

    @Test
    void publishedRoutesMustRemainUnchanged() {
        assertThat(routes()).containsExactlyInAnyOrder(
                "GET /users", "GET /users/{userId}",
                "GET /users/{userId}/authorizations", "POST /users",
                "PUT /users/{userId}", "PUT /users/{userId}/status",
                "POST /users/{userId}/unlock", "POST /users/{userId}/credential-reset",
                "DELETE /users/{userId}", "PUT /users/{userId}/roles",
                "PUT /users/{userId}/factory-scopes", "PUT /users/{userId}/mobile-access",
                "PUT /users/{userId}/party-binding", "GET /roles",
                "GET /roles/{roleId}/permissions", "POST /roles",
                "PUT /roles/{roleId}", "PUT /roles/{roleId}/permissions",
                "GET /permissions", "GET /sessions",
                "POST /sessions/{sessionId}/revoke",
                "POST /users/{userId}/sessions/revoke",
                "GET /security-audit", "GET /oauth-clients",
                "PUT /oauth-clients/{clientId}/status");
        CONTROLLERS.forEach(type ->
                assertThat(type.getAnnotation(RequestMapping.class).value())
                        .containsExactly("/api/iam/admin"));
    }

    @Test
    void explicitSuccessStatusesMustRemainStable() {
        assertThat(status("createUser").value())
                .isEqualTo(org.springframework.http.HttpStatus.CREATED);
        assertThat(status("createRole").value())
                .isEqualTo(org.springframework.http.HttpStatus.CREATED);
        assertThat(status("deleteUser").value())
                .isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        assertThat(status("revokeSession").value())
                .isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
    }

    @Test
    void paginationDefaultsMustRemainStable() {
        assertThat(requestParamDefaults("users"))
                .containsEntry("limit", "50").containsEntry("offset", "0");
        assertThat(requestParamDefaults("roles"))
                .containsEntry("limit", "50").containsEntry("offset", "0");
        assertThat(requestParamDefaults("permissions"))
                .containsEntry("limit", "100").containsEntry("offset", "0");
        assertThat(requestParamDefaults("sessions"))
                .containsEntry("limit", "50").containsEntry("offset", "0");
        assertThat(requestParamDefaults("securityAudit"))
                .containsEntry("limit", "100").containsEntry("offset", "0");
    }

    @Test
    void requestRecordFieldsMustRemainStable() {
        assertRecord(IamAdminCommands.CreateUser.class,
                "username:String", "displayName:String", "userType:UserType",
                "initialPassword:String", "partyType:PartyType", "partyId:String");
        assertRecord(IamAdminCommands.UpdateUser.class,
                "displayName:String", "version:Long");
        assertRecord(IamAdminCommands.StatusChange.class,
                "status:IamRecordStatus", "version:Long", "reason:String");
        assertRecord(IamAdminCommands.VersionedReason.class,
                "version:Long", "reason:String");
        assertRecord(IamAdminCommands.PasswordReset.class,
                "temporaryPassword:String", "version:Long", "reason:String");
        assertRecord(IamAdminCommands.RoleAssignment.class,
                "roleIds:Set", "version:Long", "reason:String");
        assertRecord(IamAdminCommands.FactoryScopeChange.class,
                "factoryIds:Set", "version:Long", "reason:String");
        assertRecord(IamAdminCommands.MobileAccessChange.class,
                "enabled:boolean", "version:Long", "reason:String");
        assertRecord(IamAdminCommands.PartyRebind.class,
                "partyType:PartyType", "partyId:String", "version:Long", "reason:String");
        assertRecord(IamAdminCommands.CreateRole.class,
                "code:String", "name:String",
                "applicableUserType:UserType", "description:String");
        assertRecord(IamAdminCommands.UpdateRole.class,
                "name:String", "description:String", "status:IamRecordStatus",
                "version:Long", "reason:String");
        assertRecord(IamAdminCommands.PermissionAssignment.class,
                "permissionIds:Set", "version:Long", "reason:String");
        assertRecord(IamAdminCommands.Reason.class, "reason:String");
        assertRecord(IamAdminCommands.ClientStatusChange.class,
                "status:IamRecordStatus", "version:Long", "reason:String");
    }

    private static Set<String> routes() {
        return CONTROLLERS.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(IamAdminControllerContractTest::route)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static String route(Method method) {
        if (method.isAnnotationPresent(GetMapping.class))
            return "GET " + path(method.getAnnotation(GetMapping.class).value());
        if (method.isAnnotationPresent(PostMapping.class))
            return "POST " + path(method.getAnnotation(PostMapping.class).value());
        if (method.isAnnotationPresent(PutMapping.class))
            return "PUT " + path(method.getAnnotation(PutMapping.class).value());
        if (method.isAnnotationPresent(DeleteMapping.class))
            return "DELETE " + path(method.getAnnotation(DeleteMapping.class).value());
        return null;
    }

    private static String path(String[] values) {
        assertThat(values).hasSize(1);
        return values[0];
    }

    private static ResponseStatus status(String methodName) {
        return CONTROLLERS.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.getName().equals(methodName))
                .findFirst().orElseThrow()
                .getAnnotation(ResponseStatus.class);
    }

    private static Map<String, String> requestParamDefaults(String methodName) {
        Method method = CONTROLLERS.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        Map<String, String> result = new LinkedHashMap<>();
        Arrays.stream(method.getParameters()).forEach(parameter -> {
            RequestParam annotation = parameter.getAnnotation(RequestParam.class);
            if (annotation != null && !annotation.defaultValue().contains("\ue000")) {
                result.put(parameter.getName(), annotation.defaultValue());
            }
        });
        return result;
    }

    private static void assertRecord(Class<?> type, String... expected) {
        assertThat(Arrays.stream(type.getRecordComponents())
                .map(IamAdminControllerContractTest::component)
                .toList()).containsExactly(expected);
    }

    private static String component(RecordComponent component) {
        return component.getName() + ":" + component.getType().getSimpleName();
    }
}
