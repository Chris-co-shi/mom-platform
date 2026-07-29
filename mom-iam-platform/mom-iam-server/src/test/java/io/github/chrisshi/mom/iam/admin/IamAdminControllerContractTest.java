package io.github.chrisshi.mom.iam.admin;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IAM Admin 已发布 HTTP 路由、查询默认值和请求字段特征测试。
 *
 * <p>该测试不启动 Spring Context 或数据库，只锁定 Controller 的 Java 元数据。S09 可以
 * 整理内部 Facade 与 Application Service，但任何 Path、Method、分页默认值或 JSON 请求
 * 字段变化都会使本测试失败。</p>
 */
class IamAdminControllerContractTest {

    /** 全部 22 个 Admin 路由必须保持原 HTTP Method 与 Path。 */
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
        assertThat(IamAdminController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/iam/admin");
    }

    /** 创建、删除与其余端点的显式状态语义不得在重构中漂移。 */
    @Test
    void explicitSuccessStatusesMustRemainStable() throws Exception {
        assertThat(status("createUser").value()).isEqualTo(org.springframework.http.HttpStatus.CREATED);
        assertThat(status("createRole").value()).isEqualTo(org.springframework.http.HttpStatus.CREATED);
        assertThat(status("deleteUser").value()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
        assertThat(status("revokeSession").value()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);
    }

    /** limit/offset 属于已发布查询契约，默认值不得因服务拆分而改变。 */
    @Test
    void paginationDefaultsMustRemainStable() {
        assertThat(requestParamDefaults("users")).containsEntry("limit", "50").containsEntry("offset", "0");
        assertThat(requestParamDefaults("roles")).containsEntry("limit", "50").containsEntry("offset", "0");
        assertThat(requestParamDefaults("permissions")).containsEntry("limit", "100").containsEntry("offset", "0");
        assertThat(requestParamDefaults("sessions")).containsEntry("limit", "50").containsEntry("offset", "0");
        assertThat(requestParamDefaults("securityAudit")).containsEntry("limit", "100").containsEntry("offset", "0");
    }

    /** 管理写入命令的 JSON 字段名和 Java 类型必须继续保持。 */
    @Test
    void requestRecordFieldsMustRemainStable() {
        assertRecord(IamAdminService.CreateUser.class,
                "username:String", "displayName:String", "userType:UserType",
                "initialPassword:String", "partyType:PartyType", "partyId:String");
        assertRecord(IamAdminService.UpdateUser.class, "displayName:String", "version:Long");
        assertRecord(IamAdminService.StatusChange.class, "status:IamRecordStatus", "version:Long", "reason:String");
        assertRecord(IamAdminService.VersionedReason.class, "version:Long", "reason:String");
        assertRecord(IamAdminService.PasswordReset.class, "temporaryPassword:String", "version:Long", "reason:String");
        assertRecord(IamAdminService.RoleAssignment.class, "roleIds:Set", "version:Long", "reason:String");
        assertRecord(IamAdminService.FactoryScopeChange.class, "factoryIds:Set", "version:Long", "reason:String");
        assertRecord(IamAdminService.MobileAccessChange.class, "enabled:boolean", "version:Long", "reason:String");
        assertRecord(IamAdminService.PartyRebind.class, "partyType:PartyType", "partyId:String", "version:Long", "reason:String");
        assertRecord(IamAdminService.CreateRole.class,
                "code:String", "name:String", "applicableUserType:UserType", "description:String");
        assertRecord(IamAdminService.UpdateRole.class,
                "name:String", "description:String", "status:IamRecordStatus", "version:Long", "reason:String");
        assertRecord(IamAdminService.PermissionAssignment.class, "permissionIds:Set", "version:Long", "reason:String");
        assertRecord(IamAdminService.Reason.class, "reason:String");
        assertRecord(IamAdminService.ClientStatusChange.class, "status:IamRecordStatus", "version:Long", "reason:String");
    }

    private static Set<String> routes() {
        return Arrays.stream(IamAdminController.class.getDeclaredMethods())
                .map(IamAdminControllerContractTest::route)
                .filter(value -> value != null)
                .collect(Collectors.toSet());
    }

    private static String route(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) return "GET " + path(method.getAnnotation(GetMapping.class).value());
        if (method.isAnnotationPresent(PostMapping.class)) return "POST " + path(method.getAnnotation(PostMapping.class).value());
        if (method.isAnnotationPresent(PutMapping.class)) return "PUT " + path(method.getAnnotation(PutMapping.class).value());
        if (method.isAnnotationPresent(DeleteMapping.class)) return "DELETE " + path(method.getAnnotation(DeleteMapping.class).value());
        return null;
    }

    private static String path(String[] values) {
        assertThat(values).hasSize(1);
        return values[0];
    }

    private static ResponseStatus status(String methodName) throws Exception {
        Method method = Arrays.stream(IamAdminController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        return method.getAnnotation(ResponseStatus.class);
    }

    private static Map<String, String> requestParamDefaults(String methodName) {
        Method method = Arrays.stream(IamAdminController.class.getDeclaredMethods())
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
