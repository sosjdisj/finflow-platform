package org.example.finflowbackend.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.tngtech.archunit.library.freeze.TextFileBasedViolationStore;
import com.tngtech.archunit.library.freeze.ViolationStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界体检：五条依赖规则（M02 三条 + M03 facade 一条 + M04 contract 叶子一条）。
 *
 * <p>存量违规用 {@link FreezingArchRule} 冻结在 {@code src/test/resources/archunit_store}，
 * 因此当前构建仍为绿；清单之外新增的跨模块引用会让规则失败，防止边界继续恶化。</p>
 *
 * <p>规则口径：</p>
 * <ol>
 *   <li>模块不得引用其他模块的 {@code mapper} / {@code entity}；</li>
 *   <li>{@code *Controller} 不得调用其他模块的 {@code service}；</li>
 *   <li>{@code common} 不得依赖任何业务模块；</li>
 *   <li>（M03）模块间依赖只允许指向对方的 {@code api} 包，实现细节不得跨模块泄漏；</li>
 *   <li>（M04）{@code contract} 是叶子模块，只能依赖 JDK 与自身，保证事件契约可独立发布。</li>
 * </ol>
 */
@AnalyzeClasses(packages = ModuleBoundaryTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    static final String BASE = "org.example.finflowbackend";

    /** 顶层业务模块，与 README「模块说明」一致；common 不在其中，由规则三单独约束 */
    private static final String[] MODULES = {
            "auth", "system", "merchant", "payment", "channel", "refund", "account", "ledger",
            "clearing", "settlement", "reconciliation", "risk", "webhook", "notification", "audit",
            "openapi", "notify"
    };

    /** 冻结基线仓库：文件名取自规则描述，便于在 git 中直接读出是哪三条规则 */
    private static final ViolationStore STORE = new TextFileBasedViolationStore(
            description -> description.replaceAll("[^A-Za-z0-9._-]+", "-"));

    @ArchTest
    static final ArchRule RULE_1_NO_FOREIGN_MAPPER_OR_ENTITY =
            FreezingArchRule.freeze(noForeignMapperOrEntity()).persistIn(STORE);

    @ArchTest
    static final ArchRule RULE_2_CONTROLLER_NO_FOREIGN_SERVICE =
            FreezingArchRule.freeze(controllerMustNotCallForeignService()).persistIn(STORE);

    @ArchTest
    static final ArchRule RULE_3_COMMON_NO_BUSINESS_DEPENDENCY =
            FreezingArchRule.freeze(commonMustNotDependOnBusinessModules()).persistIn(STORE);

    @ArchTest
    static final ArchRule RULE_4_CROSS_MODULE_ONLY_VIA_API =
            FreezingArchRule.freeze(crossModuleOnlyViaApi()).persistIn(STORE);

    @ArchTest
    static final ArchRule RULE_5_CONTRACT_IS_LEAF =
            noClasses()
                    .that().resideInAPackage(BASE + ".contract..")
                    .should().dependOnClassesThat().resideOutsideOfPackages("java..", BASE + ".contract..")
                    .allowEmptyShould(true)
                    .as("rule-5-contract-is-leaf");

    private static ArchRule noForeignMapperOrEntity() {
        List<ArchRule> rules = new ArrayList<>();
        for (String self : MODULES) {
            rules.add(noClasses()
                    .that().resideInAPackage(BASE + "." + self + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(foreignLayerPackages(self, "mapper", "entity"))
                    .allowEmptyShould(true));
        }
        return CompositeArchRule.of(rules).as("rule-1-no-foreign-mapper-or-entity");
    }

    private static ArchRule controllerMustNotCallForeignService() {
        List<ArchRule> rules = new ArrayList<>();
        for (String self : MODULES) {
            rules.add(noClasses()
                    .that().resideInAPackage(BASE + "." + self + "..")
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().resideInAnyPackage(foreignLayerPackages(self, "service"))
                    .allowEmptyShould(true));
        }
        return CompositeArchRule.of(rules).as("rule-2-controller-no-foreign-service");
    }

    private static ArchRule commonMustNotDependOnBusinessModules() {
        String[] businessPackages = Arrays.stream(MODULES)
                .map(module -> BASE + "." + module + "..")
                .toArray(String[]::new);
        return noClasses()
                .that().resideInAPackage(BASE + ".common..")
                .should().dependOnClassesThat().resideInAnyPackage(businessPackages)
                .allowEmptyShould(true)
                .as("rule-3-common-no-business-dependency");
    }

    private static ArchRule crossModuleOnlyViaApi() {
        List<ArchRule> rules = new ArrayList<>();
        for (String self : MODULES) {
            rules.add(noClasses()
                    .that().resideInAPackage(BASE + "." + self + "..")
                    .should().dependOnClassesThat(foreignNonApiClasses(self))
                    .allowEmptyShould(true));
        }
        return CompositeArchRule.of(rules).as("rule-4-cross-module-only-via-api");
    }

    /** 判定：其他模块的非 api 包（含模块根包）中的类，跨模块引用即违规。 */
    private static DescribedPredicate<JavaClass> foreignNonApiClasses(String self) {
        return new DescribedPredicate<>("其他模块的非 api 类") {
            @Override
            public boolean test(JavaClass javaClass) {
                String pkg = javaClass.getPackageName();
                for (String module : MODULES) {
                    if (module.equals(self)) {
                        continue;
                    }
                    String prefix = BASE + "." + module;
                    if (!pkg.equals(prefix) && !pkg.startsWith(prefix + ".")) {
                        continue;
                    }
                    boolean isApi = pkg.equals(prefix + ".api") || pkg.startsWith(prefix + ".api.");
                    if (!isApi) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private static String[] foreignLayerPackages(String self, String... layers) {
        List<String> packages = new ArrayList<>();
        for (String module : MODULES) {
            if (module.equals(self)) {
                continue;
            }
            for (String layer : layers) {
                packages.add(BASE + "." + module + "." + layer + "..");
            }
        }
        return packages.toArray(String[]::new);
    }
}
