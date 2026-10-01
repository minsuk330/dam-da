/*
 * 지식 그래프 웹 그리기. Quartz v4의 그래프 컴포넌트(quartz/components/scripts/graph.inline.ts)를 옮겨 왔다.
 * d3-force 시뮬레이션 + Pixi.js 그리기 + tween 전환, 점 끌기·확대/이동, 고른(가리킨) 점과 이웃만 또렷하게, 확대할수록 이름이 보이는 동작이 원본에서 왔다.
 * 바꾼 것: 데이터(분야 → 세부 분야 → 세션 → 주제 트리), 점 색(기억 게이지 세 단계)·크기(기억할 내용 수), 누르면 이동 대신 고르기,
 * 터치에서도 끌 수 있게 포인터 위치로 점 찾기, 디자인 토큰, 움직임 줄이기 설정, 정리(destroy).
 *
 * Original work: Quartz — https://github.com/jackyzha0/quartz
 * MIT License
 *
 * Copyright (c) 2021 jackyzha0
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
import { drag } from 'd3-drag';
import {
  forceCenter,
  forceCollide,
  forceLink,
  forceManyBody,
  forceSimulation,
  forceX,
  forceY,
  type SimulationLinkDatum,
  type SimulationNodeDatum,
} from 'd3-force';
import { select } from 'd3-selection';
import { zoom, zoomIdentity, type ZoomTransform } from 'd3-zoom';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';

import type { GraphNode } from '@/api/knowledge-graph';
import { colors, components, spacing } from '@/theme';

import { alwaysLabeled, colorOf, nodeA11yLabel, radiusOf, type GraphCanvasProps } from './graph-style';

const graph = components.graph;

/**
 * Quartz localGraph 기본값(repelForce 0.5, centerForce 0.3, linkDistance 30, radial 없음, zoom 0.25~4)을 쓴다.
 * globalGraph의 radial 힘은 연결 없는 점이 많을 때를 위한 것이라, 한 그루 트리인 우리 그래프에서는 점을 가장자리로 밀어내 뺐다.
 * 선 길이에는 양 끝 점 반지름을 더한다(우리 점이 Quartz보다 크다). 화면 디자인 값이 아니라 배치 계산용 상수다.
 */
const cfg = {
  repelForce: 0.5,
  centerForce: 0.3,
  /** 분야마다 따로 떨어진 트리가 화면 밖으로 흩어지지 않게 가운데로 약하게 당긴다(원본에 없음). */
  centerPull: 0.06,
  /** 자동 맞춤 확대 상한. 점이 몇 개 없을 때 지나치게 커지지 않게 한다. */
  maxFitScale: 1.4,
  linkDistance: 30,
  collideIterations: 3,
  scaleExtent: [0.25, 4] as [number, number],
  /** 눌렀다 뗀 시간이 이보다 짧으면 끌기가 아니라 누름이다(ms). */
  clickMs: 500,
  /** 고르거나 가리킨 점 밖의 점·선 투명도. */
  dimAlpha: 0.2,
  /** 전환 시간(ms). 점·선 200, 이름 100. */
  fadeMs: 200,
  labelFadeMs: 100,
  /** 움직임 줄이기가 켜져 있으면 이만큼 미리 계산해 멈춘 배치로 그린다. */
  settleTicks: 300,
};

type NodeData = GraphNode & SimulationNodeDatum & { r: number };
type LinkData = SimulationLinkDatum<NodeData> & { source: NodeData; target: NodeData };

type Controller = { setSelected: (id: string | null) => void; destroy: () => void };

/** Quartz 그래프(Pixi 캔버스). 점 이름은 화면 읽기 프로그램용 숨은 버튼 목록으로도 둔다. */
export function GraphCanvas({ nodes, selectedId, onSelect }: GraphCanvasProps) {
  const host = useRef<View>(null);
  const controller = useRef<Controller | null>(null);
  const latest = useRef({ onSelect, selectedId });
  const [width, setWidth] = useState(0);
  const reduceMotion = useReducedMotion();

  useEffect(() => {
    latest.current = { onSelect, selectedId };
    controller.current?.setSelected(selectedId);
  }, [onSelect, selectedId]);

  useEffect(() => {
    const element = host.current as unknown as HTMLElement | null;
    if (!element || width === 0) return;
    let cancelled = false;
    renderGraph(element, nodes, width, graph.height, reduceMotion, latest.current.selectedId, (id) => latest.current.onSelect(id)).then(
      (c) => {
        if (cancelled) c.destroy();
        else controller.current = c;
      },
    );
    return () => {
      cancelled = true;
      controller.current?.destroy();
      controller.current = null;
    };
  }, [nodes, width, reduceMotion]);

  return (
    <View style={styles.canvas} onLayout={(e) => setWidth(e.nativeEvent.layout.width)}>
      <View ref={host} style={StyleSheet.absoluteFill} />
      <View style={styles.srOnly}>
        {nodes.map((n) => (
          <Pressable key={n.id} accessibilityRole="button" accessibilityLabel={nodeA11yLabel(n)} onPress={() => onSelect(n.id)} />
        ))}
      </View>
    </View>
  );
}

async function renderGraph(
  container: HTMLElement,
  graphNodes: GraphNode[],
  width: number,
  height: number,
  reduceMotion: boolean,
  initialSelected: string | null,
  onSelect: (id: string) => void,
): Promise<Controller> {
  const [{ Application, Circle, Container, Graphics, Text }, { Group: TweenGroup, Tween }] = await Promise.all([
    import('pixi.js'),
    import('@tweenjs/tween.js'),
  ]);

  const nodes: NodeData[] = graphNodes.map((n) => ({ ...n, r: radiusOf(n) }));
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const links: LinkData[] = nodes.flatMap((n) => {
    const parent = n.parentId ? byId.get(n.parentId) : undefined;
    return parent ? [{ source: parent, target: n }] : [];
  });

  // we virtualize the simulation and use pixi to actually render it
  const simulation = forceSimulation<NodeData>(nodes)
    // 우리 점은 Quartz 점(반지름 2~5)보다 커서 반발력을 반지름에 비례해 키운다
    .force('charge', forceManyBody<NodeData>().strength((n) => -100 * cfg.repelForce * (1 + n.r / spacing.sm)))
    .force('center', forceCenter<NodeData>().strength(cfg.centerForce))
    .force('link', forceLink<NodeData, LinkData>(links).distance((l) => cfg.linkDistance + l.source.r + l.target.r))
    .force('x', forceX<NodeData>(0).strength(cfg.centerPull))
    .force('y', forceY<NodeData>(0).strength(cfg.centerPull))
    .force('collide', forceCollide<NodeData>((n) => n.r + spacing.xs).iterations(cfg.collideIterations));
  if (reduceMotion) simulation.stop().tick(cfg.settleTicks);

  type RenderNode = { data: NodeData; gfx: InstanceType<typeof Graphics>; label: InstanceType<typeof Text>; active: boolean };
  type RenderLink = { data: LinkData; gfx: InstanceType<typeof Graphics>; alpha: number; color: string; active: boolean };
  const renderNodes: RenderNode[] = [];
  const renderLinks: RenderLink[] = [];
  const tweens = new Map<string, { update: (time: number) => void; stop: () => void }>();

  let selectedId = initialSelected;
  let hoveredId: string | null = null;
  let zoomOpacity = 0;
  let dragging = false;
  let dragStart = 0;
  let currentTransform: ZoomTransform = zoomIdentity;

  const focusId = () => hoveredId ?? selectedId;

  function updateFocus() {
    const focus = focusId();
    const neighbours = new Set<string>();
    for (const l of renderLinks) {
      l.active = focus !== null && (l.data.source.id === focus || l.data.target.id === focus);
      if (l.active) {
        neighbours.add(l.data.source.id);
        neighbours.add(l.data.target.id);
      }
    }
    for (const n of renderNodes) n.active = n.data.id === focus || neighbours.has(n.data.id);
  }

  const labelAlpha = (n: RenderNode) => (alwaysLabeled(n.data) || n.active ? 1 : zoomOpacity);

  function drawNode(n: RenderNode) {
    const selected = n.data.id === selectedId;
    n.gfx
      .clear()
      .circle(0, 0, n.data.r)
      .fill({ color: colorOf(n.data) })
      .stroke({
        width: selected ? components.graphNodeSelected.width : components.graphEdge.width,
        color: selected ? components.graphNodeSelected.backgroundColor : colors.surface,
      });
  }

  function animateTo(key: string, build: (group: InstanceType<typeof TweenGroup>) => void) {
    tweens.get(key)?.stop();
    const group = new TweenGroup();
    build(group);
    group.getAll().forEach((tw) => tw.start());
    tweens.set(key, { update: group.update.bind(group), stop: () => group.getAll().forEach((tw) => tw.stop()) });
  }

  function renderFocus() {
    const focus = focusId();
    animateTo('nodes', (group) => {
      for (const n of renderNodes) group.add(new Tween(n.gfx).to({ alpha: focus === null || n.active ? 1 : cfg.dimAlpha }, cfg.fadeMs));
    });
    animateTo('links', (group) => {
      for (const l of renderLinks) {
        l.color = l.active ? colors.inkMuted : components.graphEdge.backgroundColor;
        group.add(new Tween(l).to({ alpha: focus === null || l.active ? 1 : cfg.dimAlpha }, cfg.fadeMs));
      }
    });
    animateTo('labels', (group) => {
      for (const n of renderNodes) group.add(new Tween(n.label).to({ alpha: labelAlpha(n) }, cfg.labelFadeMs));
    });
  }

  const app = new Application();
  await app.init({
    width,
    height,
    antialias: true,
    autoStart: false,
    autoDensity: true,
    backgroundAlpha: 0,
    preference: 'webgl',
    resolution: window.devicePixelRatio,
    eventMode: 'static',
  });
  container.appendChild(app.canvas);

  const stage = app.stage;
  stage.interactive = false;
  const labelsContainer = new Container({ zIndex: 3, isRenderGroup: true });
  const nodesContainer = new Container({ zIndex: 2, isRenderGroup: true });
  const linkContainer = new Container({ zIndex: 1, isRenderGroup: true });
  stage.addChild(nodesContainer, labelsContainer, linkContainer);

  for (const data of nodes) {
    const label = new Text({
      interactive: false,
      eventMode: 'none',
      text: data.label,
      anchor: { x: 0.5, y: 0 },
      style: { fontSize: graph.typography.fontSize, fill: graph.textColor, fontFamily: graph.typography.fontFamily },
      resolution: window.devicePixelRatio * 4,
    });
    const gfx = new Graphics({
      interactive: true,
      label: data.id,
      eventMode: 'static',
      hitArea: new Circle(0, 0, Math.max(data.r, spacing.md)),
      cursor: 'pointer',
    });
    const node: RenderNode = { data, gfx, label, active: false };
    gfx
      .on('pointerover', () => {
        hoveredId = data.id;
        updateFocus();
        if (!dragging) renderFocus();
      })
      .on('pointerleave', () => {
        hoveredId = null;
        updateFocus();
        if (!dragging) renderFocus();
      });
    drawNode(node);
    nodesContainer.addChild(gfx);
    labelsContainer.addChild(label);
    renderNodes.push(node);
  }
  for (const data of links) {
    const gfx = new Graphics({ interactive: false, eventMode: 'none' });
    linkContainer.addChild(gfx);
    renderLinks.push({ data, gfx, alpha: 1, color: components.graphEdge.backgroundColor, active: false });
  }
  updateFocus();
  for (const n of renderNodes) n.label.alpha = labelAlpha(n);

  // 포인터 아래의 점. Quartz는 가리킨 점(hover)을 끌 대상으로 쓰지만 터치에는 hover가 없어 위치로 찾는다.
  const nodeAt = (px: number, py: number) => {
    const [x, y] = currentTransform.invert([px, py]);
    return nodes.find((n) => Math.hypot((n.x ?? 0) + width / 2 - x, (n.y ?? 0) + height / 2 - y) <= Math.max(n.r, spacing.md));
  };

  const canvas = select<HTMLCanvasElement, unknown>(app.canvas);
  canvas.call(
    drag<HTMLCanvasElement, unknown, NodeData | undefined>()
      .container(() => app.canvas)
      .subject((event) => nodeAt(event.x, event.y))
      .on('start', (event) => {
        const subject = event.subject as NodeData & { __initial?: { x: number; y: number } };
        if (!event.active && !reduceMotion) simulation.alphaTarget(1).restart();
        subject.fx = subject.x;
        subject.fy = subject.y;
        subject.__initial = { x: subject.x ?? 0, y: subject.y ?? 0 };
        dragStart = Date.now();
        dragging = true;
        userMoved = true;
      })
      .on('drag', (event) => {
        const subject = event.subject as NodeData & { __initial: { x: number; y: number } };
        if (reduceMotion) return;
        subject.fx = subject.__initial.x + (event.x - subject.__initial.x) / currentTransform.k;
        subject.fy = subject.__initial.y + (event.y - subject.__initial.y) / currentTransform.k;
      })
      .on('end', (event) => {
        const subject = event.subject as NodeData;
        if (!event.active) simulation.alphaTarget(0);
        subject.fx = null;
        subject.fy = null;
        dragging = false;
        // if the time between mousedown and mouseup is short, we consider it a click
        if (Date.now() - dragStart < cfg.clickMs) onSelect(subject.id);
      }),
  );
  // 사용자가 끌거나 확대하기 전까지는 배치가 자리 잡는 동안 모든 점이 보이게 맞춘다(원본에 없음)
  let userMoved = false;
  const zoomBehavior = zoom<HTMLCanvasElement, unknown>();
  canvas.call(
    zoomBehavior
      .extent([
        [0, 0],
        [width, height],
      ])
      .scaleExtent(cfg.scaleExtent)
      .on('zoom', ({ transform, sourceEvent }: { transform: ZoomTransform; sourceEvent: unknown }) => {
        if (sourceEvent) userMoved = true;
        currentTransform = transform;
        stage.scale.set(transform.k, transform.k);
        stage.position.set(transform.x, transform.y);
        // zoom adjusts opacity of labels too
        zoomOpacity = Math.max((transform.k - 1) / 3.75, 0);
        for (const n of renderNodes) if (!alwaysLabeled(n.data) && !n.active) n.label.alpha = zoomOpacity;
      }),
  );

  function fitView() {
    const pad = spacing['2xl'];
    // 점과 그 아래 이름 글자(가운데 정렬)까지 포함한다
    const xs = renderNodes.flatMap(({ data: n, label }) => {
      const half = Math.max(n.r, label.width / 2);
      return [(n.x ?? 0) - half, (n.x ?? 0) + half];
    });
    const ys = renderNodes.flatMap(({ data: n }) => [(n.y ?? 0) - n.r, (n.y ?? 0) + n.r + spacing.xs + graph.typography.lineHeight]);
    const [minX, maxX, minY, maxY] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
    const k = Math.max(
      cfg.scaleExtent[0],
      Math.min((width - pad * 2) / Math.max(1, maxX - minX), (height - pad * 2) / Math.max(1, maxY - minY), cfg.maxFitScale),
    );
    // 점은 (x + width/2, y + height/2)에 그리므로 그 좌표계의 가운데를 캔버스 가운데로 옮긴다
    const cx = (minX + maxX) / 2 + width / 2;
    const cy = (minY + maxY) / 2 + height / 2;
    canvas.call(zoomBehavior.transform, zoomIdentity.translate(width / 2 - k * cx, height / 2 - k * cy).scale(k));
  }
  fitView();
  simulation.on('tick.fit', () => {
    if (!userMoved) fitView();
  });

  let stopped = false;
  function animate(time: number) {
    if (stopped) return;
    for (const n of renderNodes) {
      const x = (n.data.x ?? 0) + width / 2;
      const y = (n.data.y ?? 0) + height / 2;
      n.gfx.position.set(x, y);
      n.label.position.set(x, y + n.data.r + spacing.xs);
    }
    for (const l of renderLinks) {
      l.gfx
        .clear()
        .moveTo((l.data.source.x ?? 0) + width / 2, (l.data.source.y ?? 0) + height / 2)
        .lineTo((l.data.target.x ?? 0) + width / 2, (l.data.target.y ?? 0) + height / 2)
        .stroke({ alpha: l.alpha, width: components.graphEdge.width, color: l.color });
    }
    tweens.forEach((t) => t.update(time));
    app.renderer.render(stage);
    requestAnimationFrame(animate);
  }
  requestAnimationFrame(animate);

  return {
    setSelected(id) {
      selectedId = id;
      updateFocus();
      renderNodes.forEach(drawNode);
      renderFocus();
    },
    destroy() {
      stopped = true;
      simulation.stop().on('tick.fit', null);
      tweens.forEach((t) => t.stop());
      canvas.on('.drag', null).on('.zoom', null);
      app.destroy(true, { children: true });
    },
  };
}

const styles = StyleSheet.create({
  canvas: {
    height: graph.height,
    backgroundColor: graph.backgroundColor,
    borderRadius: graph.rounded,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  // 화면에는 보이지 않고 화면 읽기 프로그램만 읽는 점 목록
  srOnly: { position: 'absolute', width: 1, height: 1, overflow: 'hidden', opacity: 0 },
});
