package org.example.finflowbackend.risk;

import org.example.finflowbackend.audit.api.AuditApi;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.risk.entity.RiskRuleVersion;
import org.example.finflowbackend.risk.evaluate.AmountLimitEvaluator;
import org.example.finflowbackend.risk.mapper.RiskRuleMapper;
import org.example.finflowbackend.risk.mapper.RiskRuleVersionMapper;
import org.example.finflowbackend.risk.service.RiskRuleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 规则版本号单测（M10）：create 从 1 起、update / toggle / delete 每次 +1，且在同一事务内写完整快照。
 */
class RiskRuleServiceVersionTest {

    private final RiskRuleMapper ruleMapper = mock(RiskRuleMapper.class);
    private final RiskRuleVersionMapper versionMapper = mock(RiskRuleVersionMapper.class);
    private final AuditApi auditApi = mock(AuditApi.class);

    private final RiskRuleService service = new RiskRuleService(ruleMapper, versionMapper,
            List.of(new AmountLimitEvaluator()), new ObjectMapper(), auditApi);

    @Test
    void createStartsAtVersionOneAndWritesCreateSnapshot() {
        when(ruleMapper.insert(any(RiskRule.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, RiskRule.class).setId(7L);
            return 1;
        });

        RiskRule created = service.create(rule("大额", 30));

        assertEquals(1, created.getVersion());
        RiskRuleVersion snapshot = onlySnapshot();
        assertEquals(7L, snapshot.getRuleId());
        assertEquals(1, snapshot.getVersion());
        assertEquals(RiskRuleVersion.CHANGE_CREATE, snapshot.getChangeType());
        assertEquals("大额", snapshot.getRuleName());
        assertEquals(30, snapshot.getScore());
    }

    @Test
    void updateIncrementsVersionAndWritesUpdateSnapshot() {
        when(ruleMapper.selectById(7L)).thenReturn(existing(3));

        service.update(7L, rule("大额改", 40));

        ArgumentCaptor<RiskRule> ruleCaptor = ArgumentCaptor.forClass(RiskRule.class);
        verify(ruleMapper).updateById(ruleCaptor.capture());
        assertEquals(4, ruleCaptor.getValue().getVersion());
        RiskRuleVersion snapshot = onlySnapshot();
        assertEquals(4, snapshot.getVersion());
        assertEquals(RiskRuleVersion.CHANGE_UPDATE, snapshot.getChangeType());
        assertEquals("大额改", snapshot.getRuleName());
        assertEquals(40, snapshot.getScore());
        verify(auditApi).submit(any(), any());
    }

    @Test
    void toggleIncrementsVersionAndWritesToggleSnapshot() {
        when(ruleMapper.selectById(7L)).thenReturn(existing(1));

        service.toggle(7L, false);

        RiskRuleVersion snapshot = onlySnapshot();
        assertEquals(2, snapshot.getVersion());
        assertEquals(RiskRuleVersion.CHANGE_TOGGLE, snapshot.getChangeType());
        assertEquals(false, snapshot.getEnabled());
    }

    /** 删除即一次变更：快照版本号 +1 后删除活动规则，历史不随之消失。 */
    @Test
    void deleteWritesDeleteSnapshotThenRemovesRule() {
        when(ruleMapper.selectById(7L)).thenReturn(existing(5));

        service.delete(7L);

        RiskRuleVersion snapshot = onlySnapshot();
        assertEquals(6, snapshot.getVersion());
        assertEquals(RiskRuleVersion.CHANGE_DELETE, snapshot.getChangeType());
        verify(ruleMapper).deleteById(7L);
    }

    private RiskRuleVersion onlySnapshot() {
        ArgumentCaptor<RiskRuleVersion> captor = ArgumentCaptor.forClass(RiskRuleVersion.class);
        verify(versionMapper).insert(captor.capture());
        return captor.getValue();
    }

    private RiskRule rule(String name, int score) {
        RiskRule rule = new RiskRule();
        rule.setRuleName(name);
        rule.setRuleType("AMOUNT_LIMIT");
        rule.setParams("{\"amountLimit\": 1000000}");
        rule.setScore(score);
        rule.setPriority(10);
        rule.setEnabled(true);
        return rule;
    }

    private RiskRule existing(int version) {
        RiskRule rule = rule("旧规则", 30);
        rule.setId(7L);
        rule.setVersion(version);
        return rule;
    }
}
