package io.github.josepauloferreira.orderflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTests {

  private static final String ROOT = "io.github.josepauloferreira.orderflow";

  @Test
  void domainsDoNotDependOnDeliveryOrPersistenceAdapters() {
    var model = new ClassFileImporter().importPackages(ROOT);
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..api..", "..application..", "..infrastructure..")
        .check(model);
  }

  @Test
  void controllersNeverReachIntoRepositoriesDirectly() {
    var model = new ClassFileImporter().importPackages(ROOT);
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..infrastructure..")
        .check(model);
  }

  @Test
  void domainEntitiesExposeNoPublicSetters() {
    var model = new ClassFileImporter().importPackages(ROOT);
    noMethods()
        .that()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(Entity.class)
        .and()
        .haveNameMatching("set[A-Z].*")
        .should()
        .bePublic()
        .check(model);
  }

  @Test
  void domainDoesNotDependOnSpringApplicationInfrastructure() {
    var model = new ClassFileImporter().importPackages(ROOT);
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..")
        .check(model);
  }
}
