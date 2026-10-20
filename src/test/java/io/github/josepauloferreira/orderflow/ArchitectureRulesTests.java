package io.github.josepauloferreira.orderflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTests {

  private static final String ROOT = "io.github.josepauloferreira.orderflow";

  private static com.tngtech.archunit.core.domain.JavaClasses productionClasses() {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(ROOT);
  }

  @Test
  void domainsDoNotDependOnDeliveryOrPersistenceAdapters() {
    var model = productionClasses();
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(ROOT + "..api..", ROOT + "..application..", ROOT + "..infrastructure..")
        .check(model);
  }

  @Test
  void controllersNeverReachIntoRepositoriesDirectly() {
    var model = productionClasses();
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(ROOT + "..infrastructure..")
        .check(model);
  }

  @Test
  void domainEntitiesExposeNoPublicSetters() {
    var model = productionClasses();
    noMethods()
        .that()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(Entity.class)
        .and()
        .haveNameMatching("set[A-Z].*")
        .should()
        .bePublic()
        .allowEmptyShould(true)
        .check(model);
  }

  @Test
  void domainDoesNotDependOnSpringApplicationInfrastructure() {
    var model = productionClasses();
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..")
        .check(model);
  }
}
