package io.github.chrisshi.mom.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** System Platform S18 的 POM、API、Migration、事务与零 Mapper XML 精确门禁。 */
class SystemPlatformPomArchitectureTest {
    private static final String MOM_GROUP = "io.github.chrisshi.mom";
    private static final Set<String> CLIENT_DEPENDENCIES = Set.of(
            MOM_GROUP + ":mom-system-api", MOM_GROUP + ":mom-openfeign");
    private static final Set<String> SERVER_DEPENDENCIES = Set.of(
            MOM_GROUP + ":mom-system-api", MOM_GROUP + ":mom-iam-client",
            MOM_GROUP + ":mom-webmvc", MOM_GROUP + ":mom-security",
            MOM_GROUP + ":mom-data", MOM_GROUP + ":mom-messaging",
            MOM_GROUP + ":mom-outbox", MOM_GROUP + ":mom-tracing",
            MOM_GROUP + ":mom-metrics",
            "org.springframework.boot:spring-boot-starter-data-redis",
            "org.springframework.boot:spring-boot-starter-oauth2-client",
            "com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery",
            "com.alibaba.cloud:spring-cloud-starter-stream-rocketmq",
            "org.projectlombok:lombok", "org.springframework.security:spring-security-test",
            MOM_GROUP + ":mom-test");
    private static final Pattern FORBIDDEN_JAVA_TYPE = Pattern.compile(
            ".*(AuditProjection|Session|Refresh|Credential|FactoryScope|PartyBinding|Factory|Warehouse|"
                    + "Equipment|Person|Party).*\\.java");
    private static final Set<String> API_TYPES = Set.of(
            "package-info.java", "ParameterScopeType.java", "ParameterValueType.java",
            "ResolvedSystemParameter.java", "SystemDictionaryItemOption.java",
            "ResolvedSystemDictionaryItem.java", "SupportedUserLocale.java", "UserThemeMode.java",
            "UserDensity.java", "ResolvedUserPreference.java", "UserViewSetting.java",
            "SystemCatalogContracts.java");

    @Test
    void rootReactorMustRegisterSystemPlatformExactlyOnce() throws Exception {
        Path root = reactorRoot();
        assertThat(modules(parse(root.resolve("pom.xml")))).containsOnlyOnce("mom-system-platform");
        assertThat(root.resolve("mom-system-platform")).isDirectory();
    }

    @Test
    void aggregateMustContainOnlyApprovedModulesAndNoDependencies() throws Exception {
        Document document = parse(systemRoot().resolve("pom.xml"));
        Element project = document.getDocumentElement();
        assertThat(directText(project, "packaging")).isEqualTo("pom");
        assertThat(modules(document)).containsExactly(
                "mom-system-api", "mom-system-client", "mom-system-server");
        assertThat(dependencies(project)).isEmpty();
    }

    @Test
    void apiAndClientMustExposeOnlyApprovedContractsAndRemainTransportBounded() throws Exception {
        Element api = parse(systemRoot().resolve("mom-system-api/pom.xml")).getDocumentElement();
        Element client = parse(systemRoot().resolve("mom-system-client/pom.xml")).getDocumentElement();
        assertThat(dependencies(api)).isEmpty();
        assertThat(coordinates(dependencies(client))).containsExactlyInAnyOrderElementsOf(CLIENT_DEPENDENCIES);
        assertThat(javaFiles(systemRoot().resolve("mom-system-api/src/main/java")))
                .extracting(path -> path.getFileName().toString())
                .containsExactlyInAnyOrderElementsOf(API_TYPES);
        assertThat(javaFiles(systemRoot().resolve("mom-system-client/src/main/java")))
                .allMatch(path -> path.getFileName().toString().equals("package-info.java"));
    }

    @Test
    void serverMustUseOnlyApprovedSystemDependenciesAndNoSeata() throws Exception {
        Element server = parse(systemRoot().resolve("mom-system-server/pom.xml")).getDocumentElement();
        List<Dependency> dependencies = dependencies(server);
        assertThat(coordinates(dependencies)).containsExactlyInAnyOrderElementsOf(SERVER_DEPENDENCIES);
        assertThat(validateServerDependencies(dependencies)).isEmpty();
        assertThat(coordinates(dependencies)).doesNotContain(MOM_GROUP + ":mom-seata");
    }

    @Test
    void invalidFixturesMustRemainRejected() throws Exception {
        assertThat(validateServerDependencies(dependencies(parse(reactorRoot().resolve(
                "mom-architecture-tests/src/test/resources/fixtures/invalid-system-server.xml"))
                .getDocumentElement())))
                .containsExactly("System Server 禁止依赖 io.github.chrisshi.mom:mom-iam-server");
        assertThat(validateServerDependencies(dependencies(parse(reactorRoot().resolve(
                "mom-architecture-tests/src/test/resources/fixtures/invalid-system-business-dependency.xml"))
                .getDocumentElement())))
                .containsExactly("System Server 禁止依赖 io.github.chrisshi.mom:mom-mdm-api");
    }

    @Test
    void s18MustUseApprovedMigrationsWithoutMapperXmlOrGlobalTransaction() throws Exception {
        Path server = systemRoot().resolve("mom-system-server");
        try (var paths = Files.walk(systemRoot())) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            assertThat(files).noneMatch(path -> FORBIDDEN_JAVA_TYPE
                    .matcher(path.getFileName().toString()).matches());
            assertThat(files)
                    .filteredOn(path -> normalized(path).contains("/src/"))
                    .filteredOn(path -> path.getFileName().toString().endsWith(".sql"))
                    .extracting(SystemPlatformPomArchitectureTest::normalized)
                    .containsExactlyInAnyOrder(
                            migration(server, "V1__create_system_parameter.sql"),
                            migration(server, "V2__create_system_dictionary.sql"),
                            migration(server, "V3__create_system_i18n.sql"),
                            migration(server, "V4__align_system_entities_with_base_entity.sql"),
                            migration(server, "V5__remove_business_foreign_keys.sql"),
                            migration(server, "V6__clarify_i18n_release_snapshot_columns.sql"),
                            migration(server, "V7__create_system_user_preference.sql"),
                            migration(server, "V8__create_system_application_catalog.sql"),
                            migration(server, "V9__create_system_runtime_change_outbox.sql"));
            assertThat(files)
                    .filteredOn(path -> normalized(path).contains("/src/main/resources/mapper/"))
                    .isEmpty();
            for (Path javaFile : files.stream()
                    .filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(javaFile)).doesNotContain("@GlobalTransactional");
            }
        }
    }

    private static String migration(Path server, String name) {
        return normalized(server.resolve("src/main/resources/db/migration/system/" + name));
    }

    private static List<String> validateServerDependencies(List<Dependency> dependencies) {
        List<String> violations = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            String coordinates = dependency.coordinates();
            if (!SERVER_DEPENDENCIES.contains(coordinates)) {
                violations.add("System Server 禁止依赖 " + coordinates);
            }
            if (dependency.artifactId().endsWith("-server")
                    && !"mom-system-server".equals(dependency.artifactId())) {
                String message = "System Server 禁止依赖 " + coordinates;
                if (!violations.contains(message)) {
                    violations.add(message);
                }
            }
        }
        return List.copyOf(violations);
    }

    private static Set<String> coordinates(List<Dependency> dependencies) {
        return dependencies.stream().map(Dependency::coordinates)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static List<Path> javaFiles(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
        }
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Path systemRoot() {
        return reactorRoot().resolve("mom-system-platform");
    }

    private static Path reactorRoot() {
        String configured = System.getProperty("maven.multiModuleProjectDirectory");
        Path candidate = configured == null ? Path.of("").toAbsolutePath() : Path.of(configured);
        while (candidate != null) {
            Path pom = candidate.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                try {
                    String content = Files.readString(pom);
                    if (content.contains("<artifactId>mom-platform</artifactId>")
                            && content.contains("<modules>")) {
                        return candidate;
                    }
                } catch (Exception exception) {
                    throw new IllegalStateException("无法读取 Reactor 根 POM", exception);
                }
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("找不到 mom-platform Reactor 根目录");
    }

    private static Document parse(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(pom.toFile());
    }

    private static List<String> modules(Document document) {
        Element modules = directChild(document.getDocumentElement(), "modules");
        return modules == null ? List.of() : directChildren(modules, "module").stream()
                .map(element -> element.getTextContent().trim()).toList();
    }

    private static List<Dependency> dependencies(Element project) {
        Element dependencies = directChild(project, "dependencies");
        return dependencies == null ? List.of() : directChildren(dependencies, "dependency").stream()
                .map(element -> new Dependency(directText(element, "groupId"),
                        directText(element, "artifactId"), directText(element, "scope")))
                .toList();
    }

    private static String directText(Element parent, String tagName) {
        Element child = directChild(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element directChild(Element parent, String tagName) {
        return directChildren(parent, tagName).stream().findFirst().orElse(null);
    }

    private static List<Element> directChildren(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element
                    && tagName.equals(element.getLocalName() == null
                    ? element.getTagName() : element.getLocalName())) {
                result.add(element);
            }
        }
        return List.copyOf(result);
    }

    private record Dependency(String groupId, String artifactId, String scope) {
        private String coordinates() {
            return groupId + ":" + artifactId;
        }
    }
}
