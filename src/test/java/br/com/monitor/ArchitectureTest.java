package br.com.monitor;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test
    void productionMustNotDependOnTestInfrastructure() {
        // MON-02-AC04: production cannot depend on the isolated test infrastructure.
        var production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.monitor");
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("org.junit..", "org.testcontainers..", "com.tngtech.archunit..")
                .check(production);
    }
}
