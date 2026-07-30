package com.footballmanager.application.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.lang.reflect.Modifier;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.footballmanager", importOptions = ImportOption.DoNotIncludeTests.class)
class SimulationArchitectureBoundaryTest {

    @ArchTest
    static final ArchRule simulation_core_uses_domain_and_application_collaborators_only = noClasses()
            .that().resideInAPackage("..application.service.simulation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapters..",
                    "..infrastructure..",
                    "org.springframework.data.redis..",
                    "org.springframework.jdbc..",
                    "org.springframework.r2dbc..",
                    "io.r2dbc..",
                    "java.sql..");

    @ArchTest
    static final ArchRule detailed_match_flow_components_do_not_depend_on_web_or_persistence = noClasses()
            .that().resideInAPackage("..application.service.simulation.detailed..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapters.in.web..",
                    "..adapters.out..",
                    "..infrastructure..",
                    "org.springframework.data..",
                    "org.springframework.web..",
                    "org.springframework.security..",
                    "java.sql..");

    @ArchTest
    static final ArchRule simulation_orchestrators_are_application_layer_components = classes()
            .that().resideInAPackage("..application.service.simulation..")
            .and().haveSimpleNameEndingWith("Orchestrator")
            .should().resideInAPackage("..application.service.simulation..");

    @ArchTest
    static final ArchRule simulation_orchestrators_do_not_depend_on_adapter_or_infrastructure_details = noClasses()
            .that().resideInAPackage("..application.service.simulation..")
            .and().haveSimpleNameEndingWith("Orchestrator")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapters..",
                    "..infrastructure..");

    @ArchTest
    static final ArchRule detailed_match_engine_facade_stays_small_and_stateless = classes()
            .that().haveFullyQualifiedName(
                    "com.footballmanager.application.service.simulation.detailed.DetailedMatchEngine")
            .should(haveAtMostTwoInstanceFields());

    @ArchTest
    static final ArchRule simulation_package_does_not_reintroduce_large_stateful_flow_objects = classes()
            .that().resideInAPackage("..application.service.simulation..")
            .and().haveSimpleNameEndingWith("Flow")
            .should(haveAtMostFiveInstanceFields());

    private static ArchCondition<JavaClass> haveAtMostTwoInstanceFields() {
        return haveAtMostInstanceFields(2);
    }

    private static ArchCondition<JavaClass> haveAtMostFiveInstanceFields() {
        return haveAtMostInstanceFields(5);
    }

    private static ArchCondition<JavaClass> haveAtMostInstanceFields(int maxFields) {
        return new ArchCondition<>("have at most " + maxFields + " instance fields") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                long fieldCount = item.getFields().stream()
                        .filter(field -> !Modifier.isStatic(field.reflect().getModifiers()))
                        .count();
                if (fieldCount > maxFields) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " has " + fieldCount + " instance fields"));
                }
            }
        };
    }
}
