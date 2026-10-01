import { forceCollide, forceLink, forceManyBody, forceSimulation, forceX, forceY, type SimulationNodeDatum } from 'd3-force';
import { useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Svg, { Circle, G, Line, Text as SvgText } from 'react-native-svg';

import type { GraphNode } from '@/api/knowledge-graph';
import { colors, components, spacing } from '@/theme';

import { alwaysLabeled, colorOf, nodeA11yLabel, radiusOf, type GraphCanvasProps } from './graph-style';

const graph = components.graph;

/** 배치 계산용 상수(화면 디자인 값이 아니다). 웹(Quartz 이식)의 기본값과 맞춘다. 한 번에 계산해 고정한다. */
const physics = { ticks: 300, charge: -50, linkDistance: 30, centerPull: 0.12, maxScale: 1.4 };
/** 세션·주제 이름이 길면 이만큼에서 자른다(글자 수). */
const LABEL_MAX = 10;

type LaidOut = GraphNode & SimulationNodeDatum & { x: number; y: number; r: number };

/**
 * 네이티브용 지식 그래프. 웹은 Quartz 그래프를 옮긴 `graph-canvas.web.tsx`(Pixi)를 쓴다.
 * 여기서는 같은 힘으로 배치를 한 번 계산해 SVG로 그리고, 점을 누르면 고른다.
 */
export function GraphCanvas({ nodes, selectedId, onSelect }: GraphCanvasProps) {
  const [width, setWidth] = useState(0);
  const laidOut = useMemo(() => layout(nodes), [nodes]);
  const byId = new Map(laidOut.map((n) => [n.id, n]));
  const view = fit(laidOut, width, graph.height);
  const focus = new Set(
    selectedId ? [selectedId, ...laidOut.filter((n) => n.parentId === selectedId).map((n) => n.id), byId.get(selectedId)?.parentId ?? ''] : [],
  );
  const short = (text: string) => (text.length > LABEL_MAX ? `${text.slice(0, LABEL_MAX)}…` : text);

  return (
    <View style={styles.canvas} onLayout={(e) => setWidth(e.nativeEvent.layout.width)}>
      {width > 0 && (
        <Svg width={width} height={graph.height}>
          <G transform={`translate(${view.x} ${view.y}) scale(${view.scale})`}>
            {laidOut.map((n) => {
              const parent = n.parentId ? byId.get(n.parentId) : undefined;
              return parent ? (
                <Line
                  key={`edge-${n.id}`}
                  x1={parent.x}
                  y1={parent.y}
                  x2={n.x}
                  y2={n.y}
                  stroke={components.graphEdge.backgroundColor}
                  strokeWidth={components.graphEdge.width / view.scale}
                />
              ) : null;
            })}
            {laidOut.map((n) => (
              <G key={n.id} onPress={() => onSelect(n.id)} accessibilityLabel={nodeA11yLabel(n)}>
                {/* 작은 점도 누르기 쉽게 투명한 넓은 영역을 깐다 */}
                <Circle cx={n.x} cy={n.y} r={Math.max(n.r, spacing.lg / view.scale)} fill="transparent" />
                <Circle
                  cx={n.x}
                  cy={n.y}
                  r={n.r}
                  fill={colorOf(n)}
                  stroke={n.id === selectedId ? components.graphNodeSelected.backgroundColor : colors.surface}
                  strokeWidth={(n.id === selectedId ? components.graphNodeSelected.width : components.graphEdge.width) / view.scale}
                />
                {(alwaysLabeled(n) || focus.has(n.id)) && (
                  <SvgText
                    x={n.x}
                    y={n.y + n.r + (graph.typography.fontSize + spacing.xs) / view.scale}
                    textAnchor="middle"
                    fill={graph.textColor}
                    fontFamily={graph.typography.fontFamily}
                    fontSize={graph.typography.fontSize / view.scale}>
                    {alwaysLabeled(n) ? n.label : short(n.label)}
                  </SvgText>
                )}
              </G>
            ))}
          </G>
        </Svg>
      )}
    </View>
  );
}

function layout(nodes: GraphNode[]): LaidOut[] {
  const laidOut: LaidOut[] = nodes.map((n, i) => {
    // 원 둘레에 고르게 놓고 시작한다(같은 입력이면 같은 배치).
    const angle = (i / Math.max(1, nodes.length)) * 2 * Math.PI;
    return { ...n, r: radiusOf(n), x: Math.cos(angle) * graph.height * 0.3, y: Math.sin(angle) * graph.height * 0.3 };
  });
  const ids = new Set(laidOut.map((n) => n.id));
  const links = laidOut.filter((n) => n.parentId && ids.has(n.parentId)).map((n) => ({ source: n.parentId as string, target: n.id }));
  forceSimulation<LaidOut>(laidOut)
    .force('charge', forceManyBody<LaidOut>().strength(physics.charge))
    .force(
      'link',
      forceLink<LaidOut, { source: string | LaidOut; target: string | LaidOut }>(links)
        .id((n) => n.id)
        .distance((l) => physics.linkDistance + (l.source as LaidOut).r + (l.target as LaidOut).r),
    )
    .force('x', forceX<LaidOut>(0).strength(physics.centerPull))
    .force('y', forceY<LaidOut>(0).strength(physics.centerPull))
    .force('collide', forceCollide<LaidOut>((n) => n.r + spacing.xs))
    .stop()
    .tick(physics.ticks);
  return laidOut;
}

/** 모든 점(이름 글자 자리 포함)이 캔버스 안에 들어오도록 확대·이동한다. */
function fit(nodes: LaidOut[], width: number, height: number) {
  const pad = spacing['2xl'];
  const xs = nodes.flatMap((n) => [n.x - n.r, n.x + n.r]);
  const ys = nodes.flatMap((n) => [n.y - n.r, n.y + n.r + graph.typography.lineHeight]);
  const minX = Math.min(...xs);
  const minY = Math.min(...ys);
  const w = Math.max(1, Math.max(...xs) - minX);
  const h = Math.max(1, Math.max(...ys) - minY);
  const scale = Math.min((width - pad * 2) / w, (height - pad * 2) / h, physics.maxScale);
  return { scale, x: (width - w * scale) / 2 - minX * scale, y: (height - h * scale) / 2 - minY * scale };
}

const styles = StyleSheet.create({
  canvas: {
    height: graph.height,
    backgroundColor: graph.backgroundColor,
    borderRadius: graph.rounded,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
});
