package com.example.frontapi.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * AS-01: 도메인 경계 분리를 빌드 타임에 강제한다(설계 채택안: 선별적 도메인 모듈 분리).
 *  - 설계원칙 1(경계 기준): domain.entry·domain.auth·domain.meeting / integration.*
 *  - 설계원칙 2(참조 규칙): 도메인 간·도메인→연계 참조는 인터페이스(포트)만 허용, 직접 구현체 참조 금지
 *  - 설계원칙 4(외부 연계 캡슐화): 외부 연계는 integration.*에 격리, 도메인에 역의존 금지
 * 위반 시 빌드가 실패한다(AS-01 위험요인 R1·R3의 상시 검증 수단).
 */
@AnalyzeClasses(packages = "com.example.frontapi", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainBoundaryArchTest {

    /** 도메인 슬라이스(entry·auth·meeting) 간 순환 의존이 없어야 한다. */
    @ArchTest
    static final ArchRule domainSlicesAreFreeOfCycles =
        slices().matching("com.example.frontapi.domain.(*)..")
            .should().beFreeOfCycles();

    /** 도메인이 외부 연계(integration)를 참조할 때 인터페이스(Gateway 포트)로만 참조한다(구현체 직접 참조 금지). */
    @ArchTest
    static final ArchRule domainDependsOnIntegrationOnlyViaInterfaces =
        noClasses().that().resideInAPackage("..frontapi.domain..")
            .should().dependOnClassesThat(
                resideInAPackage("..frontapi.integration..").and(not(INTERFACES)))
            .as("domain must depend on integration only via interfaces (ports), not concrete adapters");

    /** 외부 연계(integration)는 도메인에 역의존하지 않는다(연계 로직의 도메인 격리). */
    @ArchTest
    static final ArchRule integrationDoesNotDependOnDomain =
        noClasses().that().resideInAPackage("..frontapi.integration..")
            .should().dependOnClassesThat().resideInAPackage("..frontapi.domain..");

    /** 서비스는 컨트롤러를 참조하지 않는다(계층 역참조 금지). */
    @ArchTest
    static final ArchRule servicesDoNotDependOnControllers =
        noClasses().that().haveSimpleNameEndingWith("Service")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller");
}
