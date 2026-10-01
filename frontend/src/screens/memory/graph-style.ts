import type { GraphNode } from '@/api/knowledge-graph';
import { gaugeLevel } from '@/components/gauge';
import { components } from '@/theme';

// 지식 그래프 점의 크기·색 (DESIGN.md "지식 그래프"). 웹(Pixi)과 네이티브(SVG) 그리기가 같이 쓴다.

type Kind = GraphNode['kind'];

const baseSize: Record<Kind, number> = {
  FIELD: components.graphNodeField.size,
  SUBFIELD: components.graphNodeSubfield.size,
  SESSION: components.graphNodeSession.size,
  UNIT: components.graphNodeUnit.size,
};

export const levelFill = {
  good: components.gaugeFill.backgroundColor,
  review: components.gaugeReviewFill.backgroundColor,
  low: components.gaugeLowFill.backgroundColor,
} as const;

export const kindLabel: Record<Kind, string> = { FIELD: '분야', SUBFIELD: '세부 분야', SESSION: '학습', UNIT: '주제' };

/** 점 반지름: 단계별 기본 지름 + 기억할 내용 수의 제곱근 × 성장값, 의 절반. */
export const radiusOf = (node: GraphNode) => (baseSize[node.kind] + Math.sqrt(node.totalItems) * components.graphNodeGrowth.size) / 2;

/** 기억 게이지 세 단계의 채움색. 확인된 항목이 없으면 회색. */
export const colorOf = (node: GraphNode) =>
  node.retrievability === null
    ? components.graphNodeUnchecked.backgroundColor
    : levelFill[gaugeLevel(node.retrievability, node.targetRetention)];

/** 분야·세부 분야 이름은 늘 보인다. 세션·주제 이름은 확대하거나 골랐을 때 보인다. */
export const alwaysLabeled = (node: GraphNode) => node.kind === 'FIELD' || node.kind === 'SUBFIELD';

export const nodeA11yLabel = (node: GraphNode) =>
  `${kindLabel[node.kind]} ${node.label}, ${
    node.retrievability === null ? '아직 확인 전' : `지금 기억할 확률 ${Math.round(node.retrievability * 100)}%`
  }`;

export type GraphCanvasProps = {
  nodes: GraphNode[];
  selectedId: string | null;
  onSelect: (id: string) => void;
};
