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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maven 模块依赖方向门禁。
 *
 * <p>该测试解析 Reactor POM 的 XML 语义，不扫描 Java import，也不依赖目录名推测传递依赖。它覆盖
 * api/client/server 与 Gateway 的直接 Maven 边界；代码级包依赖由同模块的 ArchUnit 测试负责。测试只读
 * 工作区，不修改构建文件；POM 不可解析时直接失败，不采用降级或忽略策略。</p>
 */
class MavenModuleDependencyArchitectureTest {

    private static final String MOM_GROUP = "io.github.chrisshi.mom";

    /** 验证所有已声明 API 模块不引入提供方或 Web/数据实现。 */
    @Test
    void apiModulesMustRemainTransportAndPersistenceIndependent() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Module module : reactorModules()) {
            if (!module.artifactId().endsWith("-api")) {
                continue;
            }
            for (Dependency dependency : module.dependencies()) {
                if (dependency.artifactId().endsWith("-server")
                        || containsAny(dependency.artifactId(),
                        "webmvc", "webflux", "gateway", "mybatis", "jdbc", "postgresql",
                        "autoconfigure")) {
                    violations.add(module.artifactId() + " -> " + dependency.coordinates());
                }
            }
        }
        assertTrue(violations.isEmpty(), "API 模块存在禁止依赖：\n" + String.join("\n", violations));
    }

    /** 验证 Client 依赖对应 API，且不依赖任何提供方 Server。 */
    @Test
    void clientModulesMustDependOnTheirApiAndNeverOnServers() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Module module : reactorModules()) {
            if (!module.artifactId().endsWith("-client")) {
                continue;
            }
            String expectedApi = module.artifactId().replaceFirst("-client$", "-api");
            boolean hasExpectedApi = module.dependencies().stream()
                    .anyMatch(dependency -> MOM_GROUP.equals(dependency.groupId())
                            && expectedApi.equals(dependency.artifactId()));
            if (!hasExpectedApi) {
                violations.add(module.artifactId() + " 缺少 " + expectedApi);
            }
            module.dependencies().stream()
                    .filter(dependency -> dependency.artifactId().endsWith("-server"))
                    .map(dependency -> module.artifactId() + " -> " + dependency.coordinates())
                    .forEach(violations::add);
        }
        assertTrue(violations.isEmpty(), "Client 模块依赖方向错误：\n" + String.join("\n", violations));
    }

    /** 验证领域 Server 不直接依赖其他领域 Server。 */
    @Test
    void domainServersMustNotDependOnOtherDomainServers() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Module module : reactorModules()) {
            if (!module.artifactId().endsWith("-server")) {
                continue;
            }
            module.dependencies().stream()
                    .filter(dependency -> MOM_GROUP.equals(dependency.groupId()))
                    .filter(dependency -> dependency.artifactId().endsWith("-server"))
                    .filter(dependency -> !dependency.artifactId().equals(module.artifactId()))
                    .map(dependency -> module.artifactId() + " -> " + dependency.coordinates())
                    .forEach(violations::add);
        }
        assertTrue(violations.isEmpty(), "领域 Server 存在跨 Server 直接依赖：\n"
                + String.join("\n", violations));
    }

    /** 验证 Gateway POM 不直接引入 Servlet/WebMVC 运行时。 */
    @Test
    void gatewayMustNotDeclareServletOrWebMvcDependencies() throws Exception {
        Module gateway = reactorModules().stream()
                .filter(module -> "mom-gateway".equals(module.artifactId()))
                .findFirst()
                .orElseThrow();
        List<String> violations = gateway.dependencies().stream()
                .filter(dependency -> containsAny(dependency.artifactId(),
                        "spring-boot-starter-web", "spring-webmvc", "servlet-api", "mom-webmvc"))
                .map(Dependency::coordinates)
                .toList();
        assertTrue(violations.isEmpty(), "Gateway 存在 Servlet/WebMVC 直接依赖：\n"
                + String.join("\n", violations));
    }

    private static boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static List<Module> reactorModules() throws Exception {
        Path root = reactorRoot();
        List<Module> modules = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path pom : paths.filter(path -> path.getFileName().toString().equals("pom.xml")).toList()) {
                Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
                Element project = document.getDocumentElement();
                String artifactId = directText(project, "artifactId");
                if (artifactId == null || artifactId.isBlank()) {
                    continue;
                }
                modules.add(new Module(artifactId, dependencies(project)));
            }
        }
        return List.copyOf(modules);
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

    private static List<Dependency> dependencies(Element project) {
        List<Dependency> result = new ArrayList<>();
        Element dependencies = directChild(project, "dependencies");
        if (dependencies == null) {
            return result;
        }
        for (Element dependency : directChildren(dependencies, "dependency")) {
            result.add(new Dependency(
                    directText(dependency, "groupId"), directText(dependency, "artifactId")));
        }
        return result;
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
        return result;
    }

    private record Module(String artifactId, List<Dependency> dependencies) {
    }

    private record Dependency(String groupId, String artifactId) {
        private String coordinates() {
            return groupId + ":" + artifactId;
        }
    }
}
