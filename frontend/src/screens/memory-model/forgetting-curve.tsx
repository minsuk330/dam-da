import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Svg, { Circle, Line, Path, Text as SvgText } from 'react-native-svg';

import type { CurvePoint } from '@/api/memory-model';
import { percent } from '@/components/gauge';
import { ThemedText } from '@/components/themed-text';
import { colors, components, spacing } from '@/theme';

const chart = components.chart;
const TARGET = 0.9;
const X_TICKS = [0, 7, 14, 30];
const Y_TICKS = [0, 0.5, 1];

/** 차트 안 여백. 왼쪽은 y축 글자, 오른쪽은 끝 값 글자, 아래는 x축 글자 자리다. */
const pad = { left: spacing['3xl'] + spacing.md, right: spacing['3xl'] + spacing.md, top: spacing.sm, bottom: spacing['2xl'] };

type Series = { key: string; label: string; points: CurvePoint[]; color: string; width: number };

/**
 * 망각 곡선 (DESIGN.md "망각 곡선 차트"). 오늘 처음 맞힌 지식을 며칠 뒤 기억할 확률.
 * 비교 곡선(기본 모델)이 있으면 회색으로 함께 그리고, 범례와 끝 값 글자로 구분한다.
 */
export function ForgettingCurve({ curve, defaultCurve }: { curve: CurvePoint[]; defaultCurve: CurvePoint[] | null }) {
  const [width, setWidth] = useState(0);
  const series: Series[] = [
    { key: 'mine', label: defaultCurve ? '내 기억 모델' : '기억 모델', points: curve, color: components.chartLine.backgroundColor, width: components.chartLine.width },
  ];
  if (defaultCurve) {
    series.push({
      key: 'default',
      label: '기본 모델',
      points: defaultCurve,
      color: components.chartLineContext.backgroundColor,
      width: components.chartLineContext.width,
    });
  }

  const lastDay = curve[curve.length - 1]?.day ?? 30;
  const plotWidth = Math.max(0, width - pad.left - pad.right);
  const plotHeight = chart.height - pad.top - pad.bottom;
  const x = (day: number) => pad.left + (day / lastDay) * plotWidth;
  const y = (r: number) => pad.top + (1 - r) * plotHeight;
  const path = (points: CurvePoint[]) =>
    points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.day).toFixed(1)},${y(p.retrievability).toFixed(1)}`).join(' ');

  // 끝 값 글자가 겹치지 않도록 한 줄 높이만큼 벌린다. 강조 곡선 글자는 제자리에 둔다.
  const ends = series.map((s) => ({ ...s, end: s.points[s.points.length - 1] }));
  const labelY = ends.map((e) => y(e.end.retrievability));
  if (labelY.length === 2 && Math.abs(labelY[0] - labelY[1]) < chart.typography.lineHeight) {
    labelY[1] = labelY[0] + Math.sign(labelY[1] - labelY[0] || 1) * chart.typography.lineHeight;
  }

  const font = { fontFamily: chart.typography.fontFamily, fontSize: chart.typography.fontSize };
  const summary = ends.map((e) => `${e.label} ${lastDay}일 뒤 ${percent(e.end.retrievability)}%`).join(', ');

  return (
    <View style={styles.wrap}>
      {defaultCurve && (
        <View style={styles.legend}>
          {series.map((s) => (
            <View key={s.key} style={styles.legendItem}>
              <View style={[styles.legendKey, { backgroundColor: s.color, height: s.width }]} />
              <ThemedText variant="caption" tone="inkSecondary">
                {s.label}
              </ThemedText>
            </View>
          ))}
        </View>
      )}
      <View
        accessible
        accessibilityRole="image"
        accessibilityLabel={`망각 곡선. 오늘 맞힌 지식을 기억할 확률. ${summary}`}
        style={{ height: chart.height }}
        onLayout={(e) => setWidth(e.nativeEvent.layout.width)}>
        {width > 0 && (
          <Svg width={width} height={chart.height}>
            {Y_TICKS.map((r) => (
              <Line
                key={r}
                x1={pad.left}
                x2={pad.left + plotWidth}
                y1={y(r)}
                y2={y(r)}
                stroke={components.chartGrid.backgroundColor}
                strokeWidth={components.chartGrid.width}
              />
            ))}
            {Y_TICKS.map((r) => (
              <SvgText key={r} x={pad.left - spacing.sm} y={y(r) + font.fontSize / 3} textAnchor="end" fill={chart.textColor} {...font}>
                {`${percent(r)}%`}
              </SvgText>
            ))}
            <Line
              x1={pad.left}
              x2={pad.left + plotWidth}
              y1={y(TARGET)}
              y2={y(TARGET)}
              stroke={components.chartGrid.backgroundColor}
              strokeWidth={components.chartGrid.width}
            />
            <SvgText
              x={pad.left + plotWidth}
              y={y(TARGET) + font.fontSize + spacing.xs}
              textAnchor="end"
              fill={colors.inkSecondary}
              {...font}>
              목표 90%
            </SvgText>
            {X_TICKS.map((day) => (
              <SvgText key={day} x={x(day)} y={chart.height - spacing.xs} textAnchor="middle" fill={chart.textColor} {...font}>
                {day === 0 ? '오늘' : `${day}일`}
              </SvgText>
            ))}
            {[...series].reverse().map((s) => (
              <Path
                key={s.key}
                d={path(s.points)}
                stroke={s.color}
                strokeWidth={s.width}
                strokeLinejoin="round"
                strokeLinecap="round"
                fill="none"
              />
            ))}
            {ends.map((e) => (
              <Circle
                key={e.key}
                cx={x(e.end.day)}
                cy={y(e.end.retrievability)}
                r={components.chartMarker.size / 2}
                fill={e.color}
                stroke={chart.backgroundColor}
                strokeWidth={components.chartLine.width}
              />
            ))}
            {ends.map((e, i) => (
              <SvgText
                key={e.key}
                x={x(e.end.day) + spacing.sm}
                y={labelY[i] + font.fontSize / 3}
                fill={i === 0 ? colors.ink : chart.textColor}
                {...font}>
                {`${percent(e.end.retrievability)}%`}
              </SvgText>
            ))}
          </Svg>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.sm },
  legend: { flexDirection: 'row', gap: spacing.lg },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  legendKey: { width: spacing.lg, borderRadius: components.chartLine.width },
});
