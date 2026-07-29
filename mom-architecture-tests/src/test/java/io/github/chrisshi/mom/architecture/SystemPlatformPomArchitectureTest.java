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

/**
 * System Platform S12 的 POM 语义与空骨架门禁。
 *
 * <p>测试通过 XML DOM 读取实际 POM，不使用字符串 grep 推断依赖。它同时检查 Reactor 注册、聚合模块、
 * API/Client/Server 直接依赖白名单和 S12 禁止资源。测试只读工作区，无网络、数据库或中间件副作用；
 * XML 不可解析、目录缺失或白名单外依赖均直接失败。</p>
 */
class SystemPlatformPomArchitectureTest {

    private static final String MOM_GROUP = "io.github.chrisshi.mom";
    private static final Set<String> CLIENT_DEPENDENCIES = Set.of(
            MOM_GROUP + ":mom-system-api",
            MOM_GROUP + ":mom-openfeign");
    private static final Set<String> SERVER_DEPENDENCIES = Set.of(
            MOM_GROUP + ":mom-system-api",
            MOM_GROUP + ":mom-webmvc",
            MOM_GROUP + ":mom-tracing",
            MOM_GROUP + ":mom-metrics",
            "com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery",
            MOM_GROUP + ":mom-test");
    private static final Pattern FORBIDDEN_JAVA_TYPE = Pattern.compile(
            ".*(Parameter|Dictionary|Preference|Catalog|Menu|Navigation|Permission|Role|Session|"
                    + "Refresh|Credential|Secret|Entity|Mapper|Repository|Controller).*\\.java");

    /**
     * 验证根 Reactor 精确注册一次 System 聚合模块。
     *
     * @throws Exception POM 读取或解析失败
     */
    @Test
    void rootReactorMustRegisterSystemPlatformExactlyOnce() throws Exception {
        Path root = reactorRoot();
        List<String> modules = modules(parse(root.resolve("pom.xml")));

        assertThat(modules).containsOnlyOnce("mom-system-platform");
        assertThat(root.resolve("mom-system-platform")).isDirectory();
    }

    /**
     * 验证聚合 POM 只包含 api/client/server，且不声明业务依赖。
     *
     * @throws Exception POM 读取或解析失败
     */
    @Test
    void aggregateMustContainOnlyApprovedModulesAndNoDependencies() throws Exception {
        Document document = parse(systemRoot().resolve("pom.xml"));
        Element project = document.getDocumentElement();

        assertThat(directText(project, "packaging")).isEqualTo("pom");
        assertThat(modules(document))
                .containsExactly("mom-system-api", "mom-system-client", "mom-system-server");
        assertThat(dependencies(project)).isEmpty();
    }

    /**
     * 验证 API 为空契约骨架，Client 仅依赖自身 API 与统一调用基础设施。
     *
     * @throws Exception POM 或文件读取失败
     */
    @Test
    void apiAndClientMustRemainEmptyAndTransportBounded() throws Exception {
        Element api = parse(systemRoot().resolve("mom-system-api/pom.xml")).getDocumentElement();
        Element client = parse(systemRoot().resolve("mom-system-client/pom.xml")).getDocumentElement();

        assertThat(dependencies(api)).isEmpty();
        assertThat(coordinates(dependencies(client))).containsExactlyInAnyOrderElementsOf(CLIENT_DEPENDENCIES);
        assertThat(javaFiles(systemRoot().resolve("mom-system-api/src/main/java")))
                .allMatch(path -> path.getFileName().toString().equals("package-info.java"));
        assertThat(javaFiles(systemRoot().resolve("mom-system-client/src/main/java")))
                .allMatch(path -> path.getFileName().toString().equals("package-info.java"));
    }

    /**
     * 验证 Server 仅使用 S12 最小运行依赖，并要求测试依赖保持 test scope。
     *
     * @throws Exception POM 读取或解析失败
     */
    @Test
    void serverMustUseOnlyApprovedSkeletonDependencies() throws Exception {
        Element server = parse(systemRoot().resolve("mom-system-server/pom.xml")).getDocumentElement();
        List<Dependency> dependencies = dependencies(server);

        assertThat(coordinates(dependencies)).containsExactlyInAnyOrderElementsOf(SERVER_DEPENDENCIES);
        assertThat(dependencies)
                .filteredOn(dependency -> "mom-test".equals(dependency.artifactId()))
                .singleElement()
                .extracting(Dependency::scope)
                .isEqualTo("test");
        assertThat(validateServerDependencies(dependencies)).isEmpty();
    }

    /**
     * 负例 Fixture 必须证明 IAM Server 依赖会被同一语义校验拒绝。
     *
     * @throws Exception Fixture 读取或解析失败
     */
    @Test
    void invalidServerDependencyFixtureMustBeRejected() throws Exception {
        Path fixture = reactorRoot().resolve(
                "mom-architecture-tests/src/test/resources/fixtures/invalid-system-server.xml");
        List<String> violations = validateServerDependencies(
                dependencies(parse(fixture).getDocumentElement()));

        assertThat(violations)
                .containsExactly("System Server 禁止依赖 io.github.chrisshi.mom:mom-iam-server");
    }

    /**
     * 验证分层包均已登记，且 S12 没有业务类型、Schema、Flyway 或 SQL。
     *
     * @throws Exception 文件遍历失败
     */
    @Test
    void skeletonMustContainLayersButNoBusinessOrPersistenceResources() throws Exception {
        Path server = systemRoot().resolve("mom-system-server");
        Path packageRoot = server.resolve("src/main/java/io/github/chrisshi/mom/system");

        assertThat(packageRoot.resolve("MomSystemApplication.java")).isRegularFile();
        assertThat(List.of("web", "application", "domain", "infrastructure"))
                .allSatisfy(layer ->
                        assertThat(packageRoot.resolve(layer).resolve("package-info.java")).isRegularFile());

        try (var paths = Files.walk(systemRoot())) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            assertThat(files)
                    .noneMatch(path -> FORBIDDEN_JAVA_TYPE.matcher(path.getFileName().toString()).matches())
                    .noneMatch(path -> path.getFileName().toString().endsWith(".sql"))
                    .noneMatch(path -> normalized(path).contains("/db/migration/"))
                    .noneMatch(path -> normalized(path).contains("/mapper/"));
        }
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
        return dependencies.stream().map(Dependency::coordinates).collect(java.util.stream.Collectors.toSet());
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
        if (modules == null) {
            return List.of();
        }
        return directChildren(modules, "module").stream()
                .map(element -> element.getTextContent().trim())
                .toList();
    }

    private static List<Dependency> dependencies(Element project) {
        Element dependencies = directChild(project, "dependencies");
        if (dependencies == null) {
            return List.of();
        }
        return directChildren(dependencies, "dependency").stream()
                .map(element -> new Dependency(
                        directText(element, "groupId"),
                        directText(element, "artifactId"),
                        directText(element, "scope")))
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
