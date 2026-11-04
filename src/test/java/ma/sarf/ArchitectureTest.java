package ma.sarf;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ArchitectureTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(SarfApplication.class);
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ma.sarf");

    @Test
    @DisplayName("Chaque module n'utilise que l'interface publique des autres, sans cycle")
    void modulesBienSepares() {
        MODULES.verify();
    }

    @Test
    @DisplayName("Les règles métier sont écrites en Java pur, sans Spring ni base de données")
    void domaineIndependant() {
        noClasses().that().resideInAPackage("..domaine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "java.sql..", "javax.sql..")
                .because("le domaine doit se lire comme les textes et se tester sans infrastructure")
                .check(CLASSES);
    }

    @Test
    @DisplayName("Les contrôleurs web ne parlent pas directement à la base")
    void controleursSansAccesDirectALaBase() {
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAnyPackage("..persistance..")
                .orShould().dependOnClassesThat().haveSimpleName("JdbcClient")
                .check(CLASSES);
    }

    @Test
    @DisplayName("Documentation des modules (diagrammes C4 générés dans target/spring-modulith-docs)")
    void documentationDesModules() {
        new Documenter(MODULES).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
