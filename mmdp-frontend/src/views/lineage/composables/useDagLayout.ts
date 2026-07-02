import dagre from "dagre";
import type { ExecutionGraphNode, ExecutionGraphEdge, LayoutedNode } from "@/types/processing";

/** 节点尺寸映射（width x height） */
const NODE_SIZES: Record<string, { w: number; h: number }> = {
  task: { w: 200, h: 56 },
  session: { w: 180, h: 48 },
  asset: { w: 170, h: 44 },
  job: { w: 190, h: 52 },
  qc: { w: 150, h: 40 },
};

const DEFAULT_SIZE = { w: 160, h: 44 };

/**
 * 使用 dagre 对 DAG 节点做拓扑布局，返回带坐标的节点列表。
 * 边保持不变（用于渲染连线）。
 */
export function useDagLayout(
  nodes: ExecutionGraphNode[],
  edges: ExecutionGraphEdge[]
): { layoutedNodes: LayoutedNode[]; svgWidth: number; svgHeight: number } {
  if (nodes.length === 0) {
    return { layoutedNodes: [], svgWidth: 0, svgHeight: 0 };
  }

  const g = new dagre.graphlib.Graph();
  g.setGraph({
    rankdir: "TB",
    nodesep: 50,
    ranksep: 80,
    marginx: 60,
    marginy: 60,
  });
  g.setDefaultEdgeLabel(() => ({}));

  // 添加节点到 dagre 图
  for (const node of nodes) {
    const size = NODE_SIZES[node.type] || DEFAULT_SIZE;
    g.setNode(node.id, { width: size.w, height: size.h });
  }

  // 添加边到 dagre 图
  for (const edge of edges) {
    g.setEdge(edge.source, edge.target);
  }

  // 执行布局计算
  dagre.layout(g);

  // 提取带坐标的节点
  const layoutedNodes: LayoutedNode[] = nodes.map((node) => {
    const pos = g.node(node.id);
    const size = NODE_SIZES[node.type] || DEFAULT_SIZE;
    if (pos) {
      return {
        ...node,
        x: pos.x - size.w / 2,
        y: pos.y - size.h / 2,
        width: size.w,
        height: size.h,
      };
    }
    return { ...node, x: 0, y: 0, width: size.w, height: size.h };
  });

  // 计算 SVG 总尺寸
  const graphInfo = g.graph();
  const svgWidth = (graphInfo.width || 800) + 80;
  const svgHeight = (graphInfo.height || 600) + 80;

  return { layoutedNodes, svgWidth, svgHeight };
}

/** 生成边的 SVG path（三次贝塞尔曲线） */
export function buildEdgePath(
  sourceNode: LayoutedNode,
  targetNode: LayoutedNode
): string {
  const x1 = sourceNode.x + sourceNode.width / 2;
  const y1 = sourceNode.y + sourceNode.height;
  const x2 = targetNode.x + targetNode.width / 2;
  const y2 = targetNode.y;
  const dy = Math.abs(y2 - y1) * 0.4;
  return `M ${x1} ${y1} C ${x1} ${y1 + dy}, ${x2} ${y2 - dy}, ${x2} ${y2}`;
}
