import { useCallback, useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import type { Schemas } from '@/api/client';
import { useKnowledgeGraph, type GraphNode } from '@/api/knowledge-graph';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Gauge } from '@/components/gauge';
import { Skeleton } from '@/components/skeleton';
import { ThemedText } from '@/components/themed-text';
import { openSession } from '@/navigation';
import { components, spacing } from '@/theme';

import { GraphCanvas } from './graph-canvas';
import { kindLabel, levelFill } from './graph-style';

type Status = Schemas['LearningSessionSummary']['status'];

/**
 * 지식 그래프 (스펙 §7.10, DESIGN.md "지식 그래프"). 분야 → 세부 분야 → 학습 → 주제를 한 그래프로 보여준다.
 * 웹은 Quartz 그래프(끌기·확대·이웃 강조)를, 네이티브는 고정 배치 SVG를 그린다. 점 색은 기억 게이지 세 단계, 크기는 기억할 내용 수다.
 * 색만으로 뜻을 전하지 않도록 범례와, 고른 점의 이름·경로·게이지를 아래 카드에 글자로 보여준다.
 */
export function KnowledgeGraph({ statusOf }: { statusOf: (sessionId: number) => Status | undefined }) {
  const { data, isPending, isError, refetch } = useKnowledgeGraph();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const byId = useMemo(() => new Map((data?.nodes ?? []).map((n) => [n.id, n])), [data]);
  const select = useCallback((id: string) => setSelectedId(id), []);

  if (isPending) return <Skeleton shape="block" height={components.graph.height} />;
  if (isError) {
    return (
      <Card style={styles.center}>
        <ThemedText tone="dangerInk">그래프를 불러오지 못했어요.</ThemedText>
        <Button variant="secondary" title="다시 시도" onPress={() => refetch()} />
      </Card>
    );
  }
  if (data.nodes.length === 0) {
    return (
      <Card style={styles.center}>
        <ThemedText variant="subhead" tone="inkMuted">
          아직 그래프로 볼 학습이 없어요.
        </ThemedText>
      </Card>
    );
  }

  const selected = selectedId ? byId.get(selectedId) : undefined;
  return (
    <View style={styles.wrap}>
      <GraphCanvas nodes={data.nodes} selectedId={selectedId} onSelect={select} />
      <Legend />
      <Card style={styles.detail}>
        {selected ? (
          <>
            <ThemedText variant="caption" tone="inkMuted">
              {pathOf(selected, byId)}
            </ThemedText>
            <ThemedText variant="headline">{selected.label}</ThemedText>
            <Gauge value={selected.retrievability} target={selected.targetRetention} />
            <ThemedText variant="caption" tone="inkSecondary">
              기억할 내용 {selected.totalItems}개 중 {selected.checkedItems}개 확인
            </ThemedText>
            {selected.sessionId !== null && (
              <Button
                variant="secondary"
                title="학습 열기"
                onPress={() => openSession(selected.sessionId as number, statusOf(selected.sessionId as number))}
              />
            )}
          </>
        ) : (
          <ThemedText variant="subhead" tone="inkSecondary">
            점을 누르면 여기에서 기억 상태를 볼 수 있어요. 끌어서 옮기고, 확대하면 이름이 더 보여요.
          </ThemedText>
        )}
      </Card>
    </View>
  );
}

/** 점 색의 뜻. 색만으로 뜻을 전하지 않도록 글자를 함께 쓴다. */
function Legend() {
  const items = [
    { color: levelFill.good, label: '잘 기억' },
    { color: levelFill.review, label: '복습할 때' },
    { color: levelFill.low, label: '많이 잊음' },
    { color: components.graphNodeUnchecked.backgroundColor, label: '확인 전' },
  ];
  return (
    <View style={styles.legend} accessibilityLabel="점 색: 파랑 잘 기억, 노랑 복습할 때, 빨강 많이 잊음, 회색 확인 전">
      {items.map((item) => (
        <View key={item.label} style={styles.legendItem}>
          <View style={[styles.dot, { backgroundColor: item.color }]} />
          <ThemedText variant="caption" tone="inkSecondary">
            {item.label}
          </ThemedText>
        </View>
      ))}
    </View>
  );
}

function pathOf(node: GraphNode, byId: Map<string, GraphNode>): string {
  const names: string[] = [];
  for (let p = node.parentId ? byId.get(node.parentId) : undefined; p; p = p.parentId ? byId.get(p.parentId) : undefined) {
    names.unshift(p.label);
  }
  return names.length > 0 ? names.join(' › ') : kindLabel[node.kind];
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.md },
  center: { alignItems: 'center', gap: spacing.md },
  detail: { gap: spacing.sm },
  legend: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md, paddingHorizontal: spacing.xs },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  dot: { width: spacing.sm, height: spacing.sm, borderRadius: spacing.xs },
});
