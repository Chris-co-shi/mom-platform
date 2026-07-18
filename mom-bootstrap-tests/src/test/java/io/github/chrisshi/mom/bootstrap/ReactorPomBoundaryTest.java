package io.github.chrisshi.mom.bootstrap;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactorPomBoundaryTest {

    private static final Set<String> API_FORBIDDEN_DEPENDENCIES = Set.of(
            "mom-data",
            "mom-data-permission",
            "mom-webmvc",
            "spring-boot-starter-web",
            "mybatis-plus-spring-boot3-starter",
            "mybatis-plus-spring-boot4-starter");

    private final Path reactorRoot = locateReactorRoot();

    @Test
    void gatewayMustNotDependOnServletModules() throws Exception {
        Set<String> dependencies = dependencyArtifactIds(reactorRoot.resolve("mom-gateway/pom.xml"));

        assertFalse(dependencies.contains("mom-webmvc"));
        assertFalse(dependencies.contains("spring-boot-starter-web"));
        assertFalse(dependencies.contains("spring-boot-starter-webmvc"));
    }

    @Test
    void apiModulesMustRemainInfrastructureFree() throws Exception {
        try (Stream<Path> paths = Files.walk(reactorRoot, 3)) {
            for (Path pom : paths
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> path.getParent().getFileName().toString().endsWith("-api"))
                    .toList()) {
                Set<String> forbidden = new HashSet<>(dependencyArtifactIds(pom));
                forbidden.retainAll(API_FORBIDDEN_DEPENDENCIES);
                assertTrue(forbidden.isEmpty(), () -> pom + " contains forbidden API dependencies: " + forbidden);
            }
        }
    }

    @Test
    void serverModulesMustNotDependOnOtherServerModules() throws Exception {
        try (Stream<Path> paths = Files.walk(reactorRoot, 3)) {
            for (Path pom : paths
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> path.getParent().getFileName().toString().endsWith("-server"))
                    .toList()) {
                Set<String> serverDependencies = dependencyArtifactIds(pom).stream()
                        .filter(artifactId -> artifactId.startsWith("mom-") && artifactId.endsWith("-server"))
                        .collect(java.util.stream.Collectors.toSet());
                assertTrue(serverDependencies.isEmpty(),
                        () -> pom + " directly depends on another domain server: " + serverDependencies);
            }
        }
    }

    private static Set<String> dependencyArtifactIds(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Document document = factory.newDocumentBuilder().parse(pom.toFile());
        NodeList dependencies = document.getElementsByTagName("dependency");
        Set<String> artifactIds = new HashSet<>();
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            NodeList children = dependency.getChildNodes();
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                Node child = children.item(childIndex);
                if (child.getNodeType() == Node.ELEMENT_NODE && "artifactId".equals(child.getNodeName())) {
                    artifactIds.add(child.getTextContent().trim());
                }
            }
        }
        return artifactIds;
    }

    private static Path locateReactorRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("mom-framework"))
                    && Files.isDirectory(current.resolve("mom-bootstrap-tests"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate MOM Platform reactor root");
    }
}
